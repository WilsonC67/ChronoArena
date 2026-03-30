import javax.swing.*;
import java.awt.*;
import java.util.function.Consumer;

public class ActionbarPanel extends JPanel {

    private final JLabel[] zoneOwnerLabels = new JLabel[3];
    private final JPanel[] zoneProgressBars = new JPanel[3];
    private final JLabel itemHeldLabel;

    public ActionbarPanel(Consumer<String> onAction) {
        setLayout(null);
        setPreferredSize(new Dimension(900, 52));
        setBackground(Style.BG_DARK);

        // action buttons
        JButton dashBtn = Style.makeStyledButton("DASH", new Color(60, 160, 80));
        dashBtn.setBounds(170, 8, 100, 36);
        dashBtn.addActionListener(e -> onAction.accept("DASH"));
        add(dashBtn);

        JButton tagBtn = Style.makeStyledButton("TAG", new Color(200, 140, 30));
        tagBtn.setBounds(280, 8, 100, 36);
        tagBtn.addActionListener(e -> onAction.accept("TAG"));
        add(tagBtn);

        // zone panels
        String[] zoneNames = {"ZONE A", "ZONE B", "ZONE C"};
        for (int i = 0; i < 3; i++) {
            int bx = 395 + i * 120;

            JLabel zName = Style.makeLabel(zoneNames[i], Style.FONT_XS, new Color(150, 155, 170), SwingConstants.CENTER);
            zName.setBounds(bx, 3, 110, 14);
            add(zName);

            zoneOwnerLabels[i] = Style.makeLabel("---", Style.FONT_SMALL, Style.TEXT_MUTED, SwingConstants.CENTER);
            zoneOwnerLabels[i].setBounds(bx, 17, 110, 16);
            add(zoneOwnerLabels[i]);

            JPanel barBg = new JPanel(null);
            barBg.setBackground(Style.BG_BAR);
            barBg.setBounds(bx, 36, 110, 8);
            add(barBg);

            zoneProgressBars[i] = new JPanel();
            zoneProgressBars[i].setBackground(new Color(80, 140, 255));
            zoneProgressBars[i].setBounds(0, 0, 0, 8);
            barBg.add(zoneProgressBars[i]);
        }

        // item held indicator
        JLabel itemTitle = Style.makeLabel("HOLDING", Style.FONT_XXS_B, Style.TEXT_MUTED, SwingConstants.CENTER);
        itemTitle.setBounds(760, 6, 80, 14);
        add(itemTitle);

        itemHeldLabel = Style.makeLabel("NONE", Style.FONT_NORM, Style.TEXT_MUTED, SwingConstants.CENTER);
        itemHeldLabel.setBounds(755, 20, 90, 26);
        add(itemHeldLabel);
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
        SwingUtilities.invokeLater(() -> Style.applyItemStyle(itemHeldLabel, itemType));
    }
}