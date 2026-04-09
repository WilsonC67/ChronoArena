import javax.swing.*;
import java.awt.*;
import java.util.function.BooleanSupplier;

public class SidebarPanel extends JPanel {

    private final JLabel cardNameLabel;
    private final JLabel cardHpLabel;
    private final JPanel cardHpBar;
    private final JLabel cardStatusLabel;
    private final JLabel cardItemLabel;
    private final JLabel cardScoreLabel;

    // other player cards (always 3 slots for the non-local players)
    private final JLabel[] otherNameLabels  = new JLabel[3];
    private final JLabel[] otherScoreLabels = new JLabel[3];
    private final JLabel[] otherStatusLabels = new JLabel[3];
    private final JLabel[] otherItemLabels  = new JLabel[3];
    private final JLabel[] otherConnLabels  = new JLabel[3];

    // maps absolute player id (1-4) → slot index (0-2), skipping localPlayerId
    private final int[] playerIdToSlot = new int[5]; // index 1-4 used

    public SidebarPanel(int localPlayerId, BooleanSupplier up, BooleanSupplier down,
                        BooleanSupplier left, BooleanSupplier right, Runnable onExit) {
        setLayout(null);
        setPreferredSize(new Dimension(160, 600));
        setBackground(Style.BG_PANEL);

        // --- EXIT button ---
        JButton exitBtn = Style.makeStyledButton("EXIT", new Color(180, 50, 50));
        exitBtn.setBounds(38, 10, 80, 30);
        exitBtn.addActionListener(e -> onExit.run());
        add(exitBtn);

        // --- Own player card ---
        JPanel card = new JPanel(null);
        card.setBackground(Style.BG_CARD);
        card.setBorder(BorderFactory.createLineBorder(Style.ACCENT_BLUE, 2));
        card.setBounds(8, 50, 144, 180);

        JLabel youLabel = Style.makeLabel("YOU", Style.FONT_XXS_B, Style.TEXT_MUTED, SwingConstants.CENTER);
        youLabel.setBounds(0, 6, 144, 14);
        card.add(youLabel);

        cardNameLabel = Style.makeLabel("Player " + localPlayerId, Style.FONT_MED, Style.TEXT_WHITE, SwingConstants.CENTER);
        cardNameLabel.setBounds(0, 22, 110, 20);
        card.add(cardNameLabel);

        // Score on own card — same gold color as the top bar
        cardScoreLabel = Style.makeLabel("0", Style.FONT_XS, Style.TEXT_GOLD, SwingConstants.RIGHT);
        cardScoreLabel.setBounds(110, 24, 28, 14);
        card.add(cardScoreLabel);

        JLabel hpTitle = Style.makeLabel("HP", Style.FONT_XXS_B, Style.TEXT_MUTED);
        hpTitle.setBounds(10, 50, 30, 14);
        card.add(hpTitle);

        cardHpLabel = Style.makeLabel("100", Style.FONT_XXS_B, Style.ACCENT_GREEN, SwingConstants.RIGHT);
        cardHpLabel.setBounds(100, 50, 34, 14);
        card.add(cardHpLabel);

        JPanel hpBarBg = new JPanel(null);
        hpBarBg.setBackground(Style.BG_BAR);
        hpBarBg.setBounds(10, 66, 124, 8);
        card.add(hpBarBg);

        cardHpBar = new JPanel();
        cardHpBar.setBackground(Style.ACCENT_GREEN);
        cardHpBar.setBounds(0, 0, 124, 8);
        hpBarBg.add(cardHpBar);

        JLabel statusTitle = Style.makeLabel("STATUS", Style.FONT_XXS_B, Style.TEXT_MUTED);
        statusTitle.setBounds(10, 84, 60, 14);
        card.add(statusTitle);

        cardStatusLabel = Style.makeLabel("NORMAL", new Font("SansSerif", Font.BOLD, 11), Style.ACCENT_GREEN, SwingConstants.RIGHT);
        cardStatusLabel.setBounds(60, 84, 74, 14);
        card.add(cardStatusLabel);

        JSeparator divider = new JSeparator();
        divider.setForeground(Style.BORDER_DIM);
        divider.setBounds(10, 106, 124, 2);
        card.add(divider);

        cardItemLabel = Style.makeLabel("NONE", new Font("SansSerif", Font.BOLD, 12), Style.TEXT_MUTED, SwingConstants.RIGHT);
        cardItemLabel.setBounds(60, 112, 74, 18);
        card.add(cardItemLabel);

        add(card);

        // --- Other player cards ---
        // Build ordered list of player IDs excluding self, always in order 1→4
        int slot = 0;
        for (int pid = 1; pid <= 4; pid++) {
            if (pid == localPlayerId) continue;
            playerIdToSlot[pid] = slot;

            Color accent = Style.PLAYER_ACCENTS[pid - 1]; // PLAYER_ACCENTS is 0-indexed
            String tag = "P" + pid;
            int cy = 238 + slot * 62;

            JPanel oCard = new JPanel(null);
            oCard.setBackground(Style.BG_CARD);
            oCard.setBorder(BorderFactory.createLineBorder(accent, 1));
            oCard.setBounds(8, cy, 144, 62);
            add(oCard);

            JLabel pTag = Style.makeLabel(tag, Style.FONT_XXS_B, accent);
            pTag.setBounds(6, 4, 20, 12);
            oCard.add(pTag);

            otherNameLabels[slot] = Style.makeLabel("---", Style.FONT_SMALL, Style.TEXT_WHITE);
            otherNameLabels[slot].setBounds(24, 4, 80, 12);
            oCard.add(otherNameLabels[slot]);

            // Score color matches the top-bar score box for this player (accent color)
            otherScoreLabels[slot] = Style.makeLabel("0 pts", Style.FONT_XS, accent, SwingConstants.RIGHT);
            otherScoreLabels[slot].setBounds(100, 4, 38, 12);
            oCard.add(otherScoreLabels[slot]);

            otherStatusLabels[slot] = Style.makeLabel("NORMAL", Style.FONT_XS_P, Style.ACCENT_GREEN);
            otherStatusLabels[slot].setBounds(6, 22, 70, 14);
            oCard.add(otherStatusLabels[slot]);

            otherItemLabels[slot] = Style.makeLabel("NONE", Style.FONT_XS, Style.TEXT_MUTED, SwingConstants.RIGHT);
            otherItemLabels[slot].setBounds(76, 22, 62, 14);
            oCard.add(otherItemLabels[slot]);

            JSeparator oDiv = new JSeparator();
            oDiv.setForeground(Style.BORDER_DIM);
            oDiv.setBounds(6, 40, 132, 2);
            oCard.add(oDiv);

            // Connection dot — kept as a label so we can update it later
            otherConnLabels[slot] = Style.makeLabel("○ WAITING", Style.FONT_XXS, new Color(120, 120, 120));
            otherConnLabels[slot].setBounds(6, 46, 132, 10);
            oCard.add(otherConnLabels[slot]);

            slot++;
        }

        // --- Joystick ---
        // Place it below the 3 other-player cards (3 × 72 = 216px, starting at 242 → ends ~458)
        JoystickPanel joystick = new JoystickPanel(up, down, left, right);
        joystick.setBounds(10, 430, 140, 140);
        add(joystick);

        JLabel hint = new JLabel("<html><center>WASD / ARROWS</center></html>", SwingConstants.CENTER);
        hint.setFont(Style.FONT_XS_P);
        hint.setForeground(Style.TEXT_HINT);
        hint.setBounds(10, 575, 140, 20);
        add(hint);
    }

    // --- Update methods called by ChronoArenaClient ---

    public void updateSelfCard(String name, int score, int hp, boolean frozen, boolean hasWeapon,
                               boolean hasShield, boolean speedBoost, String itemType) {
        SwingUtilities.invokeLater(() -> {
            cardNameLabel.setText(name);
            cardScoreLabel.setText(score + " pts");

            int clamped = Math.max(0, Math.min(100, hp));
            cardHpLabel.setText(String.valueOf(clamped));
            cardHpBar.setBounds(0, 0, (int)(124 * (clamped / 100.0)), 8);
            Color hpColor = clamped > 60 ? Style.ACCENT_GREEN
                          : clamped > 30 ? new Color(255, 180, 0)
                          : new Color(220, 60, 60);
            cardHpBar.setBackground(hpColor);
            cardHpLabel.setForeground(hpColor);
            cardHpBar.getParent().repaint();

            Style.applyStatusStyle(cardStatusLabel, frozen, speedBoost);
            Style.applyItemStyle(cardItemLabel, itemType);
        });
    }

    /**
     * @param playerId  absolute player id (1-4), NOT a slot index
     */
    public void updateOtherPlayer(int playerId, String name, int score,
                                  boolean frozen, boolean speedBoost, String itemType) {
        if (playerId < 1 || playerId > 4) return;
        int index = playerIdToSlot[playerId];
        SwingUtilities.invokeLater(() -> {
            otherNameLabels[index].setText(name);
            // Only update text — color stays as the player accent set at construction
            otherScoreLabels[index].setText(score + " pts");
            Style.applyStatusStyle(otherStatusLabels[index], frozen, speedBoost);
            Style.applyItemStyle(otherItemLabels[index], itemType);
        });
    }

    /**
     * @param playerId  absolute player id (1-4)
     * @param connected true = green dot CONNECTED, false = grey dot WAITING
     */
    public void updateOtherConnection(int playerId, boolean connected) {
        if (playerId < 1 || playerId > 4) return;
        int index = playerIdToSlot[playerId];
        SwingUtilities.invokeLater(() -> {
            if (connected) {
                otherConnLabels[index].setText("● CONNECTED");
                otherConnLabels[index].setForeground(new Color(80, 160, 80));
            } else {
                otherConnLabels[index].setText("○ WAITING");
                otherConnLabels[index].setForeground(new Color(120, 120, 120));
            }
        });
    }
}