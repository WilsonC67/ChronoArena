// PlayerRegistry.java
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe registry of all currently connected players.
 *
 * Lifecycle:
 *   register() called when a player's first UDP packet arrives
 *   update() called on every subsequent packet to refresh state
 *   sweep() remote players whose last sent packet is older than 15 seconds
 */
public class PlayerRegistry {
    // 15 s of silence -> presumed dead -> removed
    private static final long TIMEOUT_MS = 15_000; 

    // keyed by playerId
    private final ConcurrentHashMap<Integer, PlayerState> players = new ConcurrentHashMap<>();

    /**
     * Register a brand-new player, or silently ignore if already present.
     * Returns the PlayerState (new or existing).
     */
    public PlayerState register(int playerId, String ipAddress, int tcpPort) {
        return players.computeIfAbsent(playerId, id -> {
            System.out.printf("[PlayerRegistry] Player %d registered  ip=%s tcp=%d%n",
                    id, ipAddress, tcpPort);
            return new PlayerState(id, ipAddress, tcpPort);
        });
    }

    /**
     * Update an existing player's action + position.
     * Auto-registers if unseen before (e.g. first packet is an action).
     */
    public void update(int playerId, String ipAddress, int tcpPort,
                       PlayerActionEnum action, float x, float y, String extra) {
        PlayerState state = register(playerId, ipAddress, tcpPort);
        state.lastAction = action;
        state.x          = x;
        state.y          = y;
        state.extra      = extra;
        state.lastSeen   = System.currentTimeMillis();
    }

    /** Returns an unmodifiable live view of all registered players. */
    public Collection<PlayerState> getAll() {
        return Collections.unmodifiableCollection(players.values());
    }

    public PlayerState get(int playerId) {
        return players.get(playerId);
    }

    public int count() {
        return players.size();
    }

    /**
     * Remove players who haven't sent a packet within TIMEOUT_MS.
     * Called periodically by the watcher thread inside PlayerListener.
     */
    public void sweepDeadPlayers() {
        // gets current runtime
        long now = System.currentTimeMillis();

        // if the player's last seen packet is more than 15 seconds old, remove their entry
        players.entrySet().removeIf(entry -> {
            boolean dead = (now - entry.getValue().lastSeen) > TIMEOUT_MS;
            if (dead) {
                System.out.printf("[PlayerRegistry] Player %d timed out — removed.%n",
                        entry.getKey());
            }
            return dead;
        });
    }
}