import java.io.Serializable;


public class Player implements Serializable {
    private static final long serialVersionUID = 1L;

    // Effect durations in ticks (20 ticks/sec)
    public static final int FREEZE_DURATION_TICKS = 60;        // 3 seconds
    public static final int SPEED_BOOST_DURATION_TICKS = 100;  // 5 seconds
    public static final int FREEZE_POINT_PENALTY = 10;

    public final String id;
    public String name;

    // Position on the tile grid
    public int x, y;

    // Score
    public int score;

    // Status effects
    public boolean frozen;
    public int frozenTicksLeft;

    public boolean hasWeapon;   // holds the freeze-ray
    public boolean speedBoost;
    public int speedBoostTicksLeft;

    // Connection state
    public boolean connected;
    public boolean killed;   

    // Last accepted UDP sequence number — rejects duplicates and stale out-of-order packets
    private int lastAcceptedSeq;

    public Player(String id, String name, int startX, int startY) {
        this.id = id;
        this.name = name;
        this.x = startX;
        this.y = startY;
        this.score = 0;
        this.connected = true;
        this.killed = false;
        this.lastAcceptedSeq = 0;
    }

    
    public boolean acceptSeq(int seq) {
        if (seq <= 0) return true;
        if (seq > lastAcceptedSeq) {
            lastAcceptedSeq = seq;
            return true;
        }
        return false;
    }

    /** Called once per tick to count down active status effects. */
    public void tickEffects() {
        if (frozen) {
            if (--frozenTicksLeft <= 0) {
                frozen = false;
                frozenTicksLeft = 0;
            }
        }
        if (speedBoost) {
            if (--speedBoostTicksLeft <= 0) {
                speedBoost = false;
                speedBoostTicksLeft = 0;
            }
        }
    }

    /** Apply freeze effect and deduct points. */
    public void applyFreeze() {
        frozen = true;
        frozenTicksLeft = FREEZE_DURATION_TICKS;
        score = Math.max(0, score - FREEZE_POINT_PENALTY);
    }

    /** Apply speed boost effect. */
    public void applySpeedBoost() {
        speedBoost = true;
        speedBoostTicksLeft = SPEED_BOOST_DURATION_TICKS;
    }
}
