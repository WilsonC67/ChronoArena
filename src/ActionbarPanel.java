import java.awt.*;
import java.util.function.Consumer;
import javax.swing.*;

public class ActionbarPanel extends JPanel {

    private static final int ZONE_PANEL_W = 350;  // 3 zones × 110 + 2 gaps × 10
    private static final int ZONE_W = 110;
    private static final int ZONE_GAP = 10;

    private final JLabel[] zoneNameLabels = new JLabel[3];
    private final JLabel[] zoneOwnerLabels = new JLabel[3];
    private final JPanel[] zoneBarBackgrounds = new JPanel[3];
    private final JPanel[] zoneProgressBars = new JPanel[3];

    // ── Next zone change timer (right side) ───────────────────────────────────
    private static final int TIMER_AREA_W = 160;

    private final JLabel nextChangeHeaderLabel;
    private final JLabel nextChangeTimerLabel;
    private final JLabel nextChangeZoneLabel;

    // Track the most recent progress per zone so we can recompute each update
    private final int[]    zoneProgress = new int[3];
    private final String[] zoneStates   = new String[3];

    public ActionbarPanel(Consumer<String> onAction) {
        setLayout(null);
        setPreferredSize(new Dimension(900, 52));
        setBackground(Style.BG_DARK);

        // ── Zone panels ───────────────────────────────────────────────────────
        String[] zoneNames = {"ZONE A", "ZONE B", "ZONE C"};
        for (int i = 0; i < 3; i++) {
            zoneNameLabels[i] = Style.makeLabel(zoneNames[i], Style.FONT_XS, new Color(150, 155, 170), SwingConstants.CENTER);
            add(zoneNameLabels[i]);

            zoneOwnerLabels[i] = Style.makeLabel("---", Style.FONT_SMALL, Style.TEXT_MUTED, SwingConstants.CENTER);
            add(zoneOwnerLabels[i]);

            zoneBarBackgrounds[i] = new JPanel(null);
            zoneBarBackgrounds[i].setBackground(Style.BG_BAR);
            add(zoneBarBackgrounds[i]);

            zoneProgressBars[i] = new JPanel();
            zoneProgressBars[i].setBackground(new Color(80, 140, 255));
            zoneProgressBars[i].setBounds(0, 0, 0, 8);
            zoneBarBackgrounds[i].add(zoneProgressBars[i]);

            zoneStates[i]   = "UNCLAIMED";
            zoneProgress[i] = 0;
        }

        // ── Next zone change timer block ───────────────────────────────────────
        nextChangeHeaderLabel = Style.makeLabel("NEXT CAPTURE", Style.FONT_XS,
                new Color(150, 155, 170), SwingConstants.CENTER);
        add(nextChangeHeaderLabel);

        nextChangeTimerLabel = Style.makeLabel("--:--", Style.FONT_LARGE,
                Style.TEXT_GOLD, SwingConstants.CENTER);
        add(nextChangeTimerLabel);

        nextChangeZoneLabel = Style.makeLabel("", Style.FONT_XS,
                Style.TEXT_MUTED, SwingConstants.CENTER);
        add(nextChangeZoneLabel);
    }

    // ── doLayout — runs every resize ──────────────────────────────────────────
    @Override
    public void doLayout() {
        super.doLayout();
        int W = getWidth();
        if (W == 0) return;

        int left = 50;

        for (int i = 0; i < 3; i++) {
            int zx = left + i * (ZONE_W + ZONE_GAP);

            zoneNameLabels[i].setBounds(zx, 3,  ZONE_W, 14);
            zoneOwnerLabels[i].setBounds(zx, 17, ZONE_W, 16);
            zoneBarBackgrounds[i].setBounds(zx, 36, ZONE_W, 8);
        }

        // Timer block pinned to the right edge with a small margin
        int tx = W - TIMER_AREA_W - 16;
        nextChangeHeaderLabel.setBounds(tx, 2,  TIMER_AREA_W, 13);
        nextChangeTimerLabel .setBounds(tx, 14, TIMER_AREA_W, 22);
        nextChangeZoneLabel  .setBounds(tx, 36, TIMER_AREA_W, 13);
    }

    // ── Public update API ─────────────────────────────────────────────────────

    /**
     * Called by ChronoArenaClient's zone callback each time a zone state arrives.
     * Stores the raw state + progress so we can recompute the capture countdown.
     *
     * @param zoneIndex      0-based zone index (0 = A, 1 = B, 2 = C)
     * @param ownerName      display label for the owner ("P1", "Contested", "" …)
     * @param captureProgress fraction 0.0–1.0 already used for the progress bar
     * @param state          raw state string from the server ("CAPTURING", "CONTROLLED", etc.)
     * @param rawProgress    raw tick progress (0 to Zone.CAPTURE_TICKS)
     */
    public void updateZone(int zoneIndex, String ownerName, double captureProgress,
                           String state, int rawProgress) {
        // Update the existing progress bar / label
        SwingUtilities.invokeLater(() -> {
            JLabel lbl = zoneOwnerLabels[zoneIndex];
            JPanel bar = zoneProgressBars[zoneIndex];
            boolean owned = ownerName != null && !ownerName.isEmpty();
            lbl.setText(owned ? ownerName : "---");
            lbl.setForeground(owned ? Style.ACCENT_GREEN : Style.TEXT_MUTED);
            bar.setBackground(owned ? new Color(60, 180, 80) : new Color(80, 140, 255));
            bar.setBounds(0, 0, (int)(110 * Math.min(1.0, Math.max(0.0, captureProgress))), 8);
            bar.getParent().repaint();
        });

        // Store state for the timer calculation
        zoneStates[zoneIndex]   = (state != null) ? state : "UNCLAIMED";
        zoneProgress[zoneIndex] = rawProgress;
        refreshChangeTimer();
    }

    /**
     * Backward-compatible overload — used when raw state/progress aren't available.
     * The timer will show "--:--" when called through this path.
     */
    public void updateZone(int zoneIndex, String ownerName, double captureProgress) {
        SwingUtilities.invokeLater(() -> {
            JLabel lbl = zoneOwnerLabels[zoneIndex];
            JPanel bar = zoneProgressBars[zoneIndex];
            boolean owned = ownerName != null && !ownerName.isEmpty();
            lbl.setText(owned ? ownerName : "---");
            lbl.setForeground(owned ? Style.ACCENT_GREEN : Style.TEXT_MUTED);
            bar.setBackground(owned ? new Color(60, 180, 80) : new Color(80, 140, 255));
            bar.setBounds(0, 0, (int)(110 * Math.min(1.0, Math.max(0.0, captureProgress))), 8);
            bar.getParent().repaint();
        });
    }

    public void updateItemHeld(String itemType) {
    }

    // ── Timer calculation ─────────────────────────────────────────────────────

    /**
     * Finds the furthest-along CAPTURING zone and displays how many seconds
     * remain until it completes (i.e. becomes CONTROLLED).
     * If no zone is currently capturing, shows "--:--".
     */
    private void refreshChangeTimer() {
        // Game runs at 20 ticks/sec; capture completes at Zone.CAPTURE_TICKS (60)
        final int CAPTURE_TICKS = Zone.CAPTURE_TICKS;
        final int TICKS_PER_SEC = 20;

        int   bestProgress  = -1;
        int   bestZone      = -1;
        String[] zoneLabels = {"ZONE A", "ZONE B", "ZONE C"};

        for (int i = 0; i < 3; i++) {
            if ("CAPTURING".equals(zoneStates[i]) && zoneProgress[i] > bestProgress) {
                bestProgress = zoneProgress[i];
                bestZone     = i;
            }
        }

        if (bestZone == -1) {
            // No zone is being captured right now
            SwingUtilities.invokeLater(() -> {
                nextChangeTimerLabel.setText("--:--");
                nextChangeTimerLabel.setForeground(Style.TEXT_MUTED);
                nextChangeZoneLabel.setText("");
            });
            return;
        }

        int ticksLeft   = Math.max(0, CAPTURE_TICKS - bestProgress);
        int totalSecs   = (int) Math.ceil((double) ticksLeft / TICKS_PER_SEC);
        int mins        = totalSecs / 60;
        int secs        = totalSecs % 60;
        String timeStr  = String.format("%02d:%02d", mins, secs);
        String zoneStr  = zoneLabels[bestZone];

        // Colour: green when >10 s, gold when ≤10 s, red when ≤5 s
        Color timerColor = totalSecs > 10
                ? Style.TEXT_GOLD
                : (totalSecs > 5 ? new Color(255, 160, 40) : new Color(220, 60, 60));

        final Color col    = timerColor;
        final String tStr  = timeStr;
        final String zStr  = zoneStr;
        SwingUtilities.invokeLater(() -> {
            nextChangeTimerLabel.setText(tStr);
            nextChangeTimerLabel.setForeground(col);
            nextChangeZoneLabel.setText(zStr);
            nextChangeZoneLabel.setForeground(Style.TEXT_MUTED);
        });
    }
}