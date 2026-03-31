
import java.time.Duration;
import java.time.Instant;

/**
 * PlayerInfo — live snapshot of one connected player.
 *
 * Immutable identity fields (id, ip, tcpPort) are set once at registration.
 * All game-state fields are volatile so the GamePanel thread can read them
 * without synchronisation overhead.
 *
 * (position, last action, extra) instead of service metadata.
 */
public class PlayerInfo {

    /** A player is considered disconnected after 15 seconds. */
    public static final long DEAD_THRESHOLD_MS = 15_000; 

    // -----------------------------------------------------------------------
    // Identity — set once, never changed
    // -----------------------------------------------------------------------

    private final int    playerId;
    private final String ip;
    private final int    tcpPort;

    // -----------------------------------------------------------------------
    // Game state — updated on every non-heartbeat UDP packet
    // -----------------------------------------------------------------------

    public volatile PlayerActionEnum lastAction = PlayerActionEnum.IDLE;
    public volatile float        x          = 0f;
    public volatile float        y          = 0f;
    public volatile String       extra      = "";   // powerup id, ability name, bullet id…

    // -----------------------------------------------------------------------
    // Liveness
    // -----------------------------------------------------------------------

    private volatile Instant lastSeen = Instant.now();
    private volatile boolean alive    = true;

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------

    public PlayerInfo(int playerId, String ip, int tcpPort) {
        this.playerId = playerId;
        this.ip       = ip;
        this.tcpPort  = tcpPort;
    }

    // -----------------------------------------------------------------------
    // Update methods (called by PlayerMonitor)
    // -----------------------------------------------------------------------

    /**
     * Refresh timestamp only — used for HEARTBEAT packets.
     */
    public synchronized void touch() {
        this.lastSeen = Instant.now();
        this.alive    = true;
    }

    /**
     * Full update — used for every non-heartbeat packet.
     */
    public synchronized void update(PlayerActionEnum action, float x, float y, String extra) {
        this.lastAction = action;
        this.x          = x;
        this.y          = y;
        this.extra      = extra;
        this.lastSeen   = Instant.now();
        this.alive      = true;
    }

    public synchronized void markDead() {
        this.alive = false;
    }

    // -----------------------------------------------------------------------
    // Queries
    // -----------------------------------------------------------------------

    public long getSecondsSinceLastContact() {
        return Duration.between(lastSeen, Instant.now()).getSeconds();
    }

    public int     getPlayerId() { return playerId; }
    public String  getIp()       { return ip; }
    public int     getTcpPort()  { return tcpPort; }
    public boolean isAlive()     { return alive; }

    @Override
    public String toString() {
        return String.format(
            "PlayerInfo[id=%d, ip=%s, tcp=%d, action=%s, pos=(%.1f,%.1f), extra='%s', alive=%b]",
            playerId, ip, tcpPort, lastAction, x, y, extra, alive);
    }
}