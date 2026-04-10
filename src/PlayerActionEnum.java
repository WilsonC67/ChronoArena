/**
 * PlayerAction — all possible payload types a player client can send.
 *
 * Transmitted as the third CSV field in every UDP packet:
 *   "<playerId>,<tcpPort>,<action>[,x,y,extra]"
 *
 * HEARTBEAT is special — it only refreshes lastSeen and never touches game state.
 */
public enum PlayerActionEnum {
    // --- keepalive ---
    HEARTBEAT,

    // --- movement ---
    MOVE_UP,
    MOVE_DOWN,
    MOVE_LEFT,
    MOVE_RIGHT,

    // --- combat ---
    SHOOT,
    USE_ABILITY,

    // --- world interaction ---
    PICKUP_POWERUP,
    INTERACT,

    // --- lobby ---
    GAME_START,
    READY,
    VOTE_START,

    // --- default ---
    IDLE;

    /**
     * Case-insensitive lookup. Returns null on no match (never throws).
     */
    public static PlayerActionEnum fromString(String s) {
        if (s == null) return null;
        try {
            return valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}