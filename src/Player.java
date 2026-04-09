import java.io.Serializable;


public class Player implements Serializable {
    private static final long serialVersionUID = 1L;

    // Effect durations in ticks (20 ticks/sec)
    public static final int FREEZE_DURATION_TICKS = 60;        // 3 seconds
    public static final int SPEED_BOOST_DURATION_TICKS = 100;  // 5 seconds
    public static final int FREEZE_POINT_PENALTY = 10;

    public final int id;
    public String name;

    // Position on the tile grid
    public int x, y;

    // Score
    public int score;

    // Health
    public int hp;  // max 100

    // Status effects
    public boolean frozen;
    public int frozenTicksLeft;

    public boolean hasWeapon;   // holds the GUN — can freeze nearby opponents once
    public boolean hasShield;   // holds the SHIELD — absorbs one incoming freeze attack
    public boolean speedBoost;
    public int speedBoostTicksLeft;

    // Connection state and respawn
    public boolean connected;
    public boolean killed;   
    public int respawnTicksLeft;  // Countdown until respawn (0 = not respawning)

    // Last accepted UDP sequence number — rejects duplicates and stale out-of-order packets
    private int lastAcceptedSeq;

    public Player(int id, String name, int startX, int startY) {
        this.id = id;
        this.name = name;
        this.x = startX;
        this.y = startY;
        this.score = 0;
        this.hp = 100;  // Start at full health
        this.connected = true;
        this.killed = false;
        this.respawnTicksLeft = 0;  // Not respawning initially
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
            // Lose 1 HP per tick while frozen
            hp = Math.max(0, hp - 1);
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

    /** Apply freeze effect and deduct points. Shield absorbs it if active. */
    public void applyFreeze() {
        if (hasShield) {
            hasShield = false; // shield consumed, freeze blocked
            return;
        }
        frozen = true;
        frozenTicksLeft = FREEZE_DURATION_TICKS;
        score = Math.max(0, score - FREEZE_POINT_PENALTY);
    }

    /** Apply shield — absorbs the next incoming freeze attack. */
    public void applyShield() {
        hasShield = true;
    }

    /** Apply speed boost effect. */
    public void applySpeedBoost() {
        speedBoost = true;
        speedBoostTicksLeft = SPEED_BOOST_DURATION_TICKS;
    }
}
