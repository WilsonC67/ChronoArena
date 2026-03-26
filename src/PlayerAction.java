/**
 * Represents a single player action received via UDP.
 * Enqueued by the networking layer and processed by GameLogic each tick.
 */
public class PlayerAction {

    public final String playerId;
    public final String action;   // "W", "S", "A", "D", "SPACE"
    public final long timestamp;  // System.currentTimeMillis() when received
    public final int seq;         // sequence number for duplicate/out-of-order detection (0 = no tracking)

    public PlayerAction(String playerId, String action, long timestamp, int seq) {
        this.playerId = playerId;
        this.action = action;
        this.timestamp = timestamp;
        this.seq = seq;
    }

    @Override
    public String toString() {
        return "PlayerAction{player=" + playerId + ", action=" + action + ", seq=" + seq + "}";
    }
}
