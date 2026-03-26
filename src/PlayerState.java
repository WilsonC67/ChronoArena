
/**
 * Snapshot of a single player's last-known state.
 * Immutable fields set at registration; mutable fields updated on each packet.
 */
public class PlayerState {
    // unmodifiable identifiers for a player (values set when they join)
    public final int    playerId;
    public final int    tcpPort;
    public final String ipAddress;

    // updated every packet
    public volatile PlayerActionEnum lastAction = PlayerActionEnum.IDLE;
    public volatile float        x          = 0f;
    public volatile float        y          = 0f;
    public volatile String       extra      = "";   // powerup id, ability name, etc.
    public volatile long         lastSeen   = System.currentTimeMillis();

    public PlayerState(int playerId, String ipAddress, int tcpPort) {
        this.playerId   = playerId;
        this.ipAddress  = ipAddress;
        this.tcpPort    = tcpPort;
    }

    @Override
    public String toString() {
        return String.format("Player[id=%d ip=%s tcp=%d action=%s pos=(%.1f,%.1f) extra='%s']",
                playerId, ipAddress, tcpPort, lastAction, x, y, extra);
    }
}