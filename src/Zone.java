import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

/**
 * Holds the server-side state of a single capture zone.
 * Positions are in tile coordinates — must match GamePanel layout.
 * Logic (capture rules, grace timer, scoring) is handled in GameLogic.
 */
public class Zone implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum State { UNCLAIMED, CAPTURING, CONTROLLED, CONTESTED }

    // Tuning constants (at 20 ticks/sec)
    public static final int CAPTURE_TICKS               = 60;   // 3 seconds to fully capture
    public static final int CONTEST_TICKS               = 60;   // 3 seconds to stay contested before capture begins
    public static final int CAPTURE_PROTECTION_TICKS    = 5;    // short immunity when contested capture begins
    public static final int POST_CAPTURE_CONTROL_TICKS  = 0;    // challenge should recontest immediately after capture
    public static final int GRACE_TICKS                 = 100;  // 5 second grace period when owner leaves
    public static final int POINTS_INTERVAL             = 20;   // grant points every 20 ticks (once per second)
    public static final int POINTS_PER_INTERVAL         = 5;    // points awarded to owner each interval

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
    public int    captureProgress;        // 0 to CAPTURE_TICKS
    public int    graceTicksLeft;         // counts down when owner leaves zone
    public int    pointsTimer;            // counts up to POINTS_INTERVAL, then resets
    public int    contestedTicksLeft;     // counts down while the zone remains contested
    public int    protectionTicksLeft;    // short protection when contested capture begins or just after capture
    public Set<Integer> presentPlayerIds = new HashSet<>(); // player IDs currently in the zone
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
        this.contestedTicksLeft = 0;
        this.protectionTicksLeft = 0;
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


     // returns 3 zones placed at random positions each match
    public static Zone[] createRandomZones() {
        int cols = PropertyFileReader.getColNum();
        int rows = PropertyFileReader.getRowNum();

        int zW = 4, zH = 3;

        // this is so zones don't sit on the edge
        int minCol = 1, maxCol = cols - zW - 1;
        int minRow = 1, maxRow = rows - zH - 1;

        java.util.Random rng = new java.util.Random();
        java.util.List<int[]> placed = new java.util.ArrayList<>();
        String[] ids = {"zone_a", "zone_b", "zone_c"};
        String[] names = {"ZONE A", "ZONE B", "ZONE C"};

        for (int i = 0; i < 3; i++) {
            int col, row;
            int attempts = 0;

            // keep trying until we find a spot that doesn't overlap any placed zone
            do {
                col = minCol + rng.nextInt(maxCol - minCol + 1);
                row = minRow + rng.nextInt(maxRow - minRow + 1);
                attempts++;
            } while (overlapsAny(col, row, zW, zH, placed) && attempts < 200);

            placed.add(new int[]{col, row, zW, zH});
        }

        Zone[] zones = new Zone[3];
        for (int i = 0; i < 3; i++) {
            zones[i] = new Zone(ids[i], names[i], placed.get(i)[0], placed.get(i)[1], zW, zH);
        }
        return zones;
    }

    /**
     * Creates 3 zones with randomised sizes (width 2–4, height 1–3) and positions.
     * Each zone gets a distinct size so they feel meaningfully different.
     * Sizes used: small (2×1), medium (3×2), large (4×3) — assigned randomly per round.
     */
    public static Zone[] createRandomVariedZones() {
        int cols = PropertyFileReader.getColNum();
        int rows = PropertyFileReader.getRowNum();

        String[] ids   = {"zone_a", "zone_b", "zone_c"};
        String[] names = {"ZONE A", "ZONE B", "ZONE C"};

        // Three distinct sizes: small, medium, large
        int[][] sizes = {{2, 1}, {3, 2}, {4, 3}};

        // Shuffle sizes so each game has them in a random zone slot
        java.util.Random rng = new java.util.Random();
        for (int i = sizes.length - 1; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            int[] tmp = sizes[i]; sizes[i] = sizes[j]; sizes[j] = tmp;
        }

        java.util.List<int[]> placed = new java.util.ArrayList<>();
        Zone[] zones = new Zone[3];

        for (int i = 0; i < 3; i++) {
            int zW = sizes[i][0];
            int zH = sizes[i][1];

            int minCol = 1, maxCol = cols - zW - 1;
            int minRow = 1, maxRow = rows - zH - 1;

            int col = minCol, row = minRow;
            int attempts = 0;
            do {
                col = minCol + rng.nextInt(Math.max(1, maxCol - minCol + 1));
                row = minRow + rng.nextInt(Math.max(1, maxRow - minRow + 1));
                attempts++;
            } while (overlapsAny(col, row, zW, zH, placed) && attempts < 300);

            placed.add(new int[]{col, row, zW, zH});
            zones[i] = new Zone(ids[i], names[i], col, row, zW, zH);
        }
        return zones;
    }

    // returns true if the zone (col, row, w, h) overlaps
    private static boolean overlapsAny(int col, int row, int w, int h, java.util.List<int[]> placed) {
        int gap = 1; // minimum tile gap between zones
        for (int[] p : placed) {
            boolean xOverlap = col < p[0] + p[2] + gap && col + w + gap > p[0];
            boolean yOverlap = row < p[1] + p[3] + gap && row + h + gap > p[1];
            if (xOverlap && yOverlap) return true;
        }
        return false;
    }

    @Override
    public String toString() {
        return "Zone[" + name + " state=" + state
             + " owner=" + ownerId
             + " progress=" + captureProgress + "/" + CAPTURE_TICKS + "]";
    }
}