
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PlayerRegistry — thread-safe registry of all currently connected players.
 *
 * Lifecycle:
 *   register()          — called on a player's very first UDP packet
 *   touch()             — called on HEARTBEAT packets (refresh lastSeen only)
 *   update()            — called on all other game-state packets
 *   sweepDeadPlayers()  — called periodically; marks/removes silent players
 *
 * All map operations use ConcurrentHashMap so reads from the game loop
 * never block writers on the network thread.
 */
public class PlayerRegistry {

    // keyed by playerId
    private final ConcurrentHashMap<Integer, PlayerInfo> players = new ConcurrentHashMap<>();

    // -----------------------------------------------------------------------
    // Registration
    // -----------------------------------------------------------------------

    /**
     * Registers a new player if not already known.
     * Returns the PlayerInfo (new or pre-existing) — never null.
     */
    public PlayerInfo register(int playerId, String ip, int tcpPort) {
        return players.computeIfAbsent(playerId, id -> {
            PlayerInfo info = new PlayerInfo(id, ip, tcpPort);
            System.out.printf("[PlayerRegistry] Player %d registered  ip=%s  tcp=%d%n",
                    id, ip, tcpPort);
            return info;
        });
    }

    // -----------------------------------------------------------------------
    // State updates
    // -----------------------------------------------------------------------

    /**
     * Heartbeat — only refreshes lastSeen; game state is untouched.
     */
    public void touch(int playerId, String ip, int tcpPort) {
        register(playerId, ip, tcpPort).touch();
    }

    /**
     * Full game-state update — position, action, and extra data.
     * Auto-registers the player if somehow unseen before.
     */
    public void update(int playerId, String ip, int tcpPort,
                       PlayerActionEnum action, float x, float y, String extra) {
        register(playerId, ip, tcpPort).update(action, x, y, extra);
    }

    // -----------------------------------------------------------------------
    // Sweep
    // -----------------------------------------------------------------------

    /**
     * Marks players as dead if they have been silent longer than
     * PlayerInfo.DEAD_THRESHOLD_MS, then removes them from the map.
     *
     * Called every SWEEP_INTERVAL ms by PlayerMonitor's watcher thread.
     */
    public void sweepDeadPlayers() {
        long now = System.currentTimeMillis();
        players.entrySet().removeIf(entry -> {
            PlayerInfo info = entry.getValue();
            boolean dead = (now - toEpochMillis(info)) > PlayerInfo.DEAD_THRESHOLD_MS;
            if (dead) {
                System.out.printf("[PlayerRegistry] Player %d timed out (silent >%ds) — removed.%n",
                        info.getPlayerId(),
                        PlayerInfo.DEAD_THRESHOLD_MS / 1000);
            }
            return dead;
        });
    }

    /** Converts Instant stored inside PlayerInfo to epoch millis for comparison. */
    private long toEpochMillis(PlayerInfo info) {
        // getSecondsSinceLastContact() returns seconds elapsed; derive epoch millis.
        return System.currentTimeMillis() - (info.getSecondsSinceLastContact() * 1000);
    }

    // -----------------------------------------------------------------------
    // Queries
    // -----------------------------------------------------------------------

    public PlayerInfo get(int playerId) {
        return players.get(playerId);
    }

    public Collection<PlayerInfo> getAll() {
        return Collections.unmodifiableCollection(players.values());
    }

    public List<PlayerInfo> getAllAsList() {
        return new ArrayList<>(players.values());
    }

    public int count() {
        return players.size();
    }
}