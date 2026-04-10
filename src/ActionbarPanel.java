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

    public ActionbarPanel(Consumer<String> onAction) {
        setLayout(null);
        setPreferredSize(new Dimension(900, 52));
        setBackground(Style.BG_DARK);

        // zone panels
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
        }
    }

    // ── doLayout — runs every resize, positions zone panels to the left ────────
    @Override
    public void doLayout() {
        super.doLayout();
        int W = getWidth();
        if (W == 0) return;

        int left = 50;  // positioned to the left

        for (int i = 0; i < 3; i++) {
            int zx = left + i * (ZONE_W + ZONE_GAP);

            zoneNameLabels[i].setBounds(zx, 3, ZONE_W, 14);
            zoneOwnerLabels[i].setBounds(zx, 17, ZONE_W, 16);
            zoneBarBackgrounds[i].setBounds(zx, 36, ZONE_W, 8);
        }
    }

    // update methods called by ChronoArenaClient
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
}