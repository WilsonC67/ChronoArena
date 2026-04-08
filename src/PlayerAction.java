/**
 * Represents a single player action received via UDP.
 * Enqueued by the networking layer and processed by GameLogic each tick.
 */
public class PlayerAction implements java.io.Serializable {
    private static final long serialVersionUID = 1L;

    public final int playerId;
    public final String action;   // "W", "S", "A", "D", "SPACE"
    public final float x;
    public final float y;
    public final long timestamp;  // System.currentTimeMillis() when received
    public final int seq;         // sequence number for duplicate/out-of-order detection (0 = no tracking)

    public PlayerAction(int playerId, String action, float x, float y, long timestamp, int seq) {
        this.playerId = playerId;
        this.action = action;
        this.x = x;
        this.y = y;
        this.timestamp = timestamp;
        this.seq = seq;
    }

    @Override
    public String toString() {
        return "PlayerAction{player=" + playerId + ", action=" + action + ", x=" + x + ", y=" + y + ", seq=" + seq + "}";
    }
}
