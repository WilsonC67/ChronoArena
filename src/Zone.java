import java.io.Serializable;

/**
 * Holds the server-side state of a single capture zone.
 * Positions are in tile coordinates — must match GamePanel layout.
 * Logic (capture rules, grace timer, scoring) is handled in GameLogic.
 */
public class Zone implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum State { UNCLAIMED, CAPTURING, CONTROLLED, CONTESTED }

    // Tuning constants (at 20 ticks/sec)
    public static final int CAPTURE_TICKS      = 60;   // 3 seconds to fully capture
    public static final int GRACE_TICKS        = 100;  // 5 second grace period when owner leaves
    public static final int POINTS_INTERVAL    = 20;   // grant points every 20 ticks (once per second)
    public static final int POINTS_PER_INTERVAL = 5;   // points awarded to owner each interval

    // Identity
    public final String id;
    public final String name;

    // Tile-space bounding box (matches GamePanel hardcoded positions)
    public final int col;     // leftmost tile column
    public final int row;     // topmost tile row
    public final int width;   // width in tiles
    public final int height;  // height in tiles

    // Live state — updated each tick by GameLogic
    public State  state;
    public int    ownerId;       // player ID who controls the zone (-1 = no owner)
    public int    capturingId;   // player ID currently progressing capture (-1 = none)
    public int    captureProgress;  // 0 to CAPTURE_TICKS
    public int    graceTicksLeft;   // counts down when owner leaves zone
    public int    pointsTimer;      // counts up to POINTS_INTERVAL, then resets

    public Zone(String id, String name, int col, int row, int width, int height) {
        this.id     = id;
        this.name   = name;
        this.col    = col;
        this.row    = row;
        this.width  = width;
        this.height = height;

        this.state           = State.UNCLAIMED;
        this.ownerId         = -1;
        this.capturingId     = -1;
        this.captureProgress = 0;
        this.graceTicksLeft  = GRACE_TICKS;
        this.pointsTimer     = 0;
    }

    /**
     * Returns true if tile position (px, py) falls inside this zone.
     * Used by GameLogic to check which players are in the zone each tick.
     */
    public boolean containsPosition(int px, int py) {
        return px >= col && px < col + width
            && py >= row && py < row + height;
    }

    /**
     * Returns the 3 default zones matching GamePanel's hardcoded layout.
     * Call this in GameLogic to initialize the zone list.
     */
    public static Zone[] createDefaultZones() {
        return new Zone[] {
            new Zone("zone_a", "ZONE A", 2, 5, 4, 3),
            new Zone("zone_b", "ZONE B", 7, 1, 4, 3),
            new Zone("zone_c", "ZONE C", 9, 7, 4, 3)
        };
    }

    @Override
    public String toString() {
        return "Zone[" + name + " state=" + state
             + " owner=" + ownerId
             + " progress=" + captureProgress + "/" + CAPTURE_TICKS + "]";
    }
}
