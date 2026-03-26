public enum PlayerActionEnum {
    MOVE_UP, MOVE_DOWN, MOVE_LEFT, MOVE_RIGHT,
    PICKUP_POWERUP, USE_ABILITY,
    SHOOT, INTERACT,
    IDLE;

    public static PlayerActionEnum fromString(String s) {
        try {
            return valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}