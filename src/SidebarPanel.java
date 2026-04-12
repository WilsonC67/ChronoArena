import java.awt.*;
import java.util.function.BooleanSupplier;
import javax.swing.*;

public class SidebarPanel extends JPanel {

    private final JLabel cardNameLabel;
    private final JLabel cardHpLabel;
    private final JPanel cardHpBar;
    private final JLabel cardStatusLabel;
    private final JLabel cardItemLabel;
    private final JLabel cardScoreLabel;
    private       JLabel selfDeadLabel;      // DEAD overlay for own card

    // other player cards (always 3 slots for the non-local players)
    private final JLabel[] otherNameLabels   = new JLabel[3];
    private final JLabel[] otherScoreLabels  = new JLabel[3];
    private final JLabel[] otherStatusLabels = new JLabel[3];
    private final JLabel[] otherItemLabels   = new JLabel[3];
    private final JLabel[] otherConnLabels   = new JLabel[3];
    private final JLabel[] otherTagLabels    = new JLabel[3];
    private final JLabel[] otherHpLabels     = new JLabel[3];  // NEW: numeric HP
    private final JPanel[] otherHpBars       = new JPanel[3];  // NEW: HP bar fill
    private final JLabel[] otherDeadLabels   = new JLabel[3];  // NEW: "DEAD / RESPAWN" overlay

    // maps absolute player id (1-4) → slot index (0-2), skipping localPlayerId
    private final int[] playerIdToSlot = new int[5]; // index 1-4 used

    // Each other-player card is now taller to fit the HP row (62 → 80px).
    // The vertical stride is also 80 to avoid overlap.
    private static final int OTHER_CARD_H      = 62;
    private static final int OTHER_CARD_STRIDE = 66;

    public SidebarPanel(int localPlayerId, BooleanSupplier up, BooleanSupplier down,
                        BooleanSupplier left, BooleanSupplier right, Runnable onExit) {
        setLayout(null);
        setPreferredSize(new Dimension(160, 600));
        setBackground(Style.BG_PANEL);

        // --- EXIT button ---
        JButton exitBtn = Style.makeStyledButton("EXIT", new Color(180, 50, 50));
        exitBtn.setBounds(38, 10, 80, 28);
        exitBtn.addActionListener(e -> onExit.run());
        add(exitBtn);

        // --- Own player card ---
        JPanel card = new JPanel(null);
        card.setBackground(Style.BG_CARD);
        card.setBorder(BorderFactory.createLineBorder(new Color(200, 200, 200), 2));
        card.setBounds(8, 42, 144, 136);

        JLabel youLabel = Style.makeLabel("YOU", Style.FONT_XXS_B, Style.TEXT_MUTED, SwingConstants.CENTER);
        youLabel.setBounds(0, 4, 144, 12);
        card.add(youLabel);

        cardNameLabel = Style.makeLabel(
                localPlayerId >= 1 && localPlayerId <= 4 ? "Player " + localPlayerId : "Player ?",
                Style.FONT_MED, Style.TEXT_WHITE, SwingConstants.CENTER);
        cardNameLabel.setBounds(0, 16, 110, 16);
        card.add(cardNameLabel);

        cardScoreLabel = Style.makeLabel("0", Style.FONT_XS, Style.TEXT_GOLD, SwingConstants.RIGHT);
        cardScoreLabel.setBounds(110, 18, 28, 12);
        card.add(cardScoreLabel);

        JLabel hpTitle = Style.makeLabel("HP", Style.FONT_XXS_B, Style.TEXT_MUTED);
        hpTitle.setBounds(10, 36, 30, 12);
        card.add(hpTitle);

        cardHpLabel = Style.makeLabel("100", Style.FONT_XXS_B, Style.ACCENT_GREEN, SwingConstants.RIGHT);
        cardHpLabel.setBounds(100, 36, 34, 12);
        card.add(cardHpLabel);

        JPanel hpBarBg = new JPanel(null);
        hpBarBg.setBackground(Style.BG_BAR);
        hpBarBg.setBounds(10, 50, 124, 7);
        card.add(hpBarBg);

        cardHpBar = new JPanel();
        cardHpBar.setBackground(Style.ACCENT_GREEN);
        cardHpBar.setBounds(0, 0, 124, 7);
        hpBarBg.add(cardHpBar);

        JLabel statusTitle = Style.makeLabel("STATUS", Style.FONT_XXS_B, Style.TEXT_MUTED);
        statusTitle.setBounds(10, 62, 60, 12);
        card.add(statusTitle);

        cardStatusLabel = Style.makeLabel("NORMAL", new Font("SansSerif", Font.BOLD, 11), Style.ACCENT_GREEN, SwingConstants.RIGHT);
        cardStatusLabel.setBounds(60, 62, 74, 12);
        card.add(cardStatusLabel);

        JSeparator divider = new JSeparator();
        divider.setForeground(Style.BORDER_DIM);
        divider.setBounds(10, 78, 124, 2);
        card.add(divider);

        cardItemLabel = Style.makeLabel("NONE", new Font("SansSerif", Font.BOLD, 12), Style.TEXT_MUTED, SwingConstants.RIGHT);
        cardItemLabel.setBounds(60, 82, 74, 16);
        card.add(cardItemLabel);

        selfDeadLabel = new JLabel("✦ DEAD", SwingConstants.CENTER);
        selfDeadLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
        selfDeadLabel.setForeground(new Color(220, 60, 60));
        selfDeadLabel.setOpaque(true);
        selfDeadLabel.setBackground(Style.BG_PANEL);
        selfDeadLabel.setBounds(-2, -2, 148, 140);
        selfDeadLabel.setVisible(false);
        card.add(selfDeadLabel);
        card.setComponentZOrder(selfDeadLabel, 0);

        add(card);

        // --- Other player cards ---
        java.util.Arrays.fill(playerIdToSlot, -1);
        int slot = 0;
        for (int pid = 1; pid <= 4 && slot < 3; pid++) {
            if (localPlayerId >= 1 && localPlayerId <= 4 && pid == localPlayerId) continue;
            if (localPlayerId >= 1 && localPlayerId <= 4) playerIdToSlot[pid] = slot;

            Color accent = Style.PLAYER_ACCENTS[pid - 1];
            String tag = localPlayerId >= 1 && localPlayerId <= 4 ? "P" + pid : "P?";
            int cy = 184 + slot * OTHER_CARD_STRIDE;

            JPanel oCard = new JPanel(null);
            oCard.setBackground(Style.BG_CARD);
            oCard.setBorder(BorderFactory.createLineBorder(accent, 2));
            oCard.setBounds(8, cy, 144, OTHER_CARD_H);
            add(oCard);

            // --- Row 1: tag | name | score ---
            JLabel pTag = Style.makeLabel(tag, Style.FONT_XXS_B, accent);
            pTag.setBounds(6, 4, 20, 12);
            oCard.add(pTag);
            otherTagLabels[slot] = pTag;

            otherNameLabels[slot] = Style.makeLabel("---", Style.FONT_SMALL, Style.TEXT_WHITE);
            otherNameLabels[slot].setBounds(24, 4, 80, 12);
            oCard.add(otherNameLabels[slot]);

            otherScoreLabels[slot] = Style.makeLabel("0 pts", Style.FONT_XS, accent, SwingConstants.RIGHT);
            otherScoreLabels[slot].setBounds(100, 4, 38, 12);
            oCard.add(otherScoreLabels[slot]);

            // --- Row 2: status | item ---
            otherStatusLabels[slot] = Style.makeLabel("NORMAL", Style.FONT_XS_P, Style.ACCENT_GREEN);
            otherStatusLabels[slot].setBounds(6, 18, 70, 12);
            oCard.add(otherStatusLabels[slot]);

            otherItemLabels[slot] = Style.makeLabel("NONE", Style.FONT_XS, Style.TEXT_MUTED, SwingConstants.RIGHT);
            otherItemLabels[slot].setBounds(76, 18, 62, 12);
            oCard.add(otherItemLabels[slot]);

            // --- Divider ---
            JSeparator oDiv = new JSeparator();
            oDiv.setForeground(Style.BORDER_DIM);
            oDiv.setBounds(6, 33, 132, 2);
            oCard.add(oDiv);

            // --- Row 3: HP label + HP bar (NEW) ---
            JLabel hpLbl = Style.makeLabel("HP", Style.FONT_XXS_B, Style.TEXT_MUTED);
            hpLbl.setBounds(6, 37, 20, 10);
            oCard.add(hpLbl);

            otherHpLabels[slot] = Style.makeLabel("100", Style.FONT_XXS_B, Style.ACCENT_GREEN, SwingConstants.RIGHT);
            otherHpLabels[slot].setBounds(110, 37, 28, 10);
            oCard.add(otherHpLabels[slot]);

            JPanel oHpBarBg = new JPanel(null);
            oHpBarBg.setBackground(Style.BG_BAR);
            oHpBarBg.setBounds(6, 50, 132, 6);
            oCard.add(oHpBarBg);

            otherHpBars[slot] = new JPanel();
            otherHpBars[slot].setBackground(Style.ACCENT_GREEN);
            otherHpBars[slot].setBounds(0, 0, 132, 6);
            oHpBarBg.add(otherHpBars[slot]);

            // --- Dead / Respawning overlay (hidden by default) ---
            otherDeadLabels[slot] = new JLabel("✦ DEAD", SwingConstants.CENTER);
            otherDeadLabels[slot].setFont(new Font("SansSerif", Font.BOLD, 11));
            otherDeadLabels[slot].setForeground(new Color(220, 60, 60));
            otherDeadLabels[slot].setOpaque(true);
            otherDeadLabels[slot].setBackground(Style.BG_PANEL);
            otherDeadLabels[slot].setBounds(-2, -2, 148, OTHER_CARD_H + 4);
            otherDeadLabels[slot].setVisible(false);
            oCard.add(otherDeadLabels[slot]);
            // Bring overlay to front so it paints over the other rows
            oCard.setComponentZOrder(otherDeadLabels[slot], 0);

            slot++;
        }

        // --- Joystick ---
        // Cards end at 238 + 3*84 = 490; joystick fits below with 8px gap
        JoystickPanel joystick = new JoystickPanel(up, down, left, right);
        joystick.setBounds(10, 386, 140, 148);
        add(joystick);

        JLabel hint = new JLabel("<html><center>WASD / ARROWS</center></html>", SwingConstants.CENTER);
        hint.setFont(Style.FONT_XS_P);
        hint.setForeground(Style.TEXT_HINT);
        hint.setBounds(10, 538, 140, 14);
        add(hint);
    }

    public void setLocalPlayerId(int localPlayerId) {
        if (localPlayerId < 1 || localPlayerId > 4) return;
        cardNameLabel.setText("Player " + localPlayerId);
        java.util.Arrays.fill(playerIdToSlot, -1);
        int slot = 0;
        for (int pid = 1; pid <= 4; pid++) {
            if (pid == localPlayerId) continue;
            playerIdToSlot[pid] = slot;
            otherTagLabels[slot].setText("P" + pid);
            slot++;
        }
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
            Color hpColor = hpColor(clamped);
            cardHpBar.setBackground(hpColor);
            cardHpLabel.setForeground(hpColor);
            cardHpBar.getParent().repaint();

            Style.applyStatusStyle(cardStatusLabel, frozen, speedBoost);
            Style.applyItemStyle(cardItemLabel, itemType);

            // Show/hide dead overlay on own card
            selfDeadLabel.setVisible(clamped == 0);
        });
    }

    /**
     * @param playerId absolute player id (1-4), NOT a slot index
     * @param hp       current HP (0-100); 0 triggers the dead overlay
     */
    public void updateOtherPlayer(int playerId, String name, int score, int hp,
                                  boolean frozen, boolean speedBoost, String itemType) {
        if (playerId < 1 || playerId > 4) return;
        int index = playerIdToSlot[playerId];
        if (index < 0) return;

        SwingUtilities.invokeLater(() -> {
            otherNameLabels[index].setText(name);
            otherScoreLabels[index].setText(score + "pts");
            Style.applyStatusStyle(otherStatusLabels[index], frozen, speedBoost);
            Style.applyItemStyle(otherItemLabels[index], itemType);

            // --- HP bar update ---
            int clamped = Math.max(0, Math.min(100, hp));
            otherHpLabels[index].setText(String.valueOf(clamped));
            Color hpColor = hpColor(clamped);
            otherHpBars[index].setBackground(hpColor);
            otherHpBars[index].setBounds(0, 0, (int)(132 * (clamped / 100.0)), 6);
            otherHpLabels[index].setForeground(hpColor);
            otherHpBars[index].getParent().repaint();

            // --- Death / respawn overlay ---
            boolean isDead = (clamped == 0);
            otherDeadLabels[index].setVisible(isDead);
        });
    }

    /**
     * Call this when a previously-dead player respawns (HP > 0 again).
     * The overlay is also cleared automatically by updateOtherPlayer when hp > 0,
     * but this explicit method lets the server send a dedicated respawn event.
     *
     * @param playerId absolute player id (1-4)
     */
    public void onOtherPlayerRespawn(int playerId) {
        if (playerId < 1 || playerId > 4) return;
        int index = playerIdToSlot[playerId];
        if (index < 0) return;
        SwingUtilities.invokeLater(() -> {
            // Reset HP bar to full until the next proper update arrives
            otherHpBars[index].setBounds(0, 0, 132, 6);
            otherHpBars[index].setBackground(Style.ACCENT_GREEN);
            otherHpBars[index].getParent().repaint();
            otherHpLabels[index].setText("100");
            otherHpLabels[index].setForeground(Style.ACCENT_GREEN);
            otherDeadLabels[index].setVisible(false);
        });
    }

    /**
     * Snaps the local player's own card HP to 0 and shows the dead overlay.
     * Called immediately when the respawn countdown begins.
     */
    public void forceSelfDead() {
        SwingUtilities.invokeLater(() -> {
            cardHpBar.setBounds(0, 0, 0, 8);
            cardHpBar.setBackground(new Color(220, 60, 60));
            cardHpBar.getParent().repaint();
            cardHpLabel.setText("0");
            cardHpLabel.setForeground(new Color(220, 60, 60));
            selfDeadLabel.setVisible(true);
        });
    }

    /**
     * Called when another player dies — snaps their HP bar to 0 and shows the dead overlay.
     * @param playerId absolute player id (1-4)
     */
    public void forceOtherPlayerDead(int playerId) {
        if (playerId < 1 || playerId > 4) return;
        int index = playerIdToSlot[playerId];
        if (index < 0) return;
        SwingUtilities.invokeLater(() -> {
            otherHpBars[index].setBounds(0, 0, 0, 6);
            otherHpBars[index].setBackground(new Color(220, 60, 60));
            otherHpBars[index].getParent().repaint();
            otherHpLabels[index].setText("0");
            otherHpLabels[index].setForeground(new Color(220, 60, 60));
            otherDeadLabels[index].setVisible(true);
        });
    }

    /**
     * @param playerId  absolute player id (1-4)
     * @param connected true = green dot CONNECTED, false = grey dot WAITING
     */
    public void updateOtherConnection(int playerId, boolean connected) {
        if (playerId < 1 || playerId > 4) return;
        int index = playerIdToSlot[playerId];
        if (index < 0) return;
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

    // --- Helpers ---

    /** Returns green / yellow / red based on HP percentage. */
    private static Color hpColor(int clamped) {
        if (clamped > 60) return Style.ACCENT_GREEN;
        if (clamped > 30) return new Color(255, 180, 0);
        return new Color(220, 60, 60);
    }
}