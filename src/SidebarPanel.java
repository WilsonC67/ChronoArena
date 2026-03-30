import javax.swing.*;
import java.awt.*;
import java.util.function.BooleanSupplier;

public class SidebarPanel extends JPanel {

    private final JLabel cardNameLabel;
    private final JLabel cardHpLabel;
    private final JPanel cardHpBar;
    private final JLabel cardStatusLabel;
    private final JLabel cardItemLabel;

    // other player card labels
    private final JLabel[] otherNameLabels = new JLabel[3];
    private final JLabel[] otherScoreLabels = new JLabel[3];
    private final JLabel[] otherStatusLabels = new JLabel[3];
    private final JLabel[] otherItemLabels = new JLabel[3];

    public SidebarPanel(int localPlayerId, BooleanSupplier up, BooleanSupplier down, BooleanSupplier left, BooleanSupplier right, Runnable onExit) {
        setLayout(null);
        setPreferredSize(new Dimension(160, 600));
        setBackground(Style.BG_PANEL);

        JButton exitBtn = Style.makeStyledButton("EXIT", new Color(180, 50, 50));
        exitBtn.setBounds(38, 10, 80, 30);
        exitBtn.addActionListener(e -> onExit.run());
        add(exitBtn);

        // own player card
        JPanel card = new JPanel(null);
        card.setBackground(Style.BG_CARD);
        card.setBorder(BorderFactory.createLineBorder(Style.ACCENT_BLUE, 2));
        card.setBounds(8, 50, 144, 180);

        JLabel youLabel = Style.makeLabel("YOU", Style.FONT_XXS_B, Style.TEXT_MUTED, SwingConstants.CENTER);
        youLabel.setBounds(0, 6, 144, 14);
        card.add(youLabel);

        cardNameLabel = Style.makeLabel("Player " + localPlayerId, Style.FONT_MED, Style.TEXT_WHITE, SwingConstants.CENTER);
        cardNameLabel.setBounds(0, 22, 144, 20);
        card.add(cardNameLabel);

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

        JLabel itemTitle = Style.makeLabel("HOLDING", Style.FONT_XXS_B, Style.TEXT_MUTED);
        itemTitle.setBounds(10, 114, 70, 14);
        card.add(itemTitle);

        cardItemLabel = Style.makeLabel("NONE", new Font("SansSerif", Font.BOLD, 12), Style.TEXT_MUTED, SwingConstants.RIGHT);
        cardItemLabel.setBounds(60, 112, 74, 18);
        card.add(cardItemLabel);

        JLabel shieldBadge = Style.makeLabel("NO SHIELD", Style.FONT_XXS, Style.TEXT_DIMMER, SwingConstants.CENTER);
        shieldBadge.setBounds(10, 140, 58, 14);
        card.add(shieldBadge);

        JLabel weaponBadge = Style.makeLabel("NO GUN", Style.FONT_XXS, Style.TEXT_DIMMER, SwingConstants.CENTER);
        weaponBadge.setBounds(76, 140, 58, 14);
        card.add(weaponBadge);

        add(card);

        // other player cards 
        Color[] accents = {Style.PLAYER_ACCENTS[1], Style.PLAYER_ACCENTS[2], Style.PLAYER_ACCENTS[3]};
        String[] tags = {"P2", "P3", "P4"};

        for (int i = 0; i < 3; i++) {
            int cy = 238 + i * 62;

            JPanel oCard = new JPanel(null);
            oCard.setBackground(Style.BG_CARD);
            oCard.setBorder(BorderFactory.createLineBorder(accents[i], 1));
            oCard.setBounds(8, cy, 144, 54);
            add(oCard);

            JLabel pTag = Style.makeLabel(tags[i], Style.FONT_XXS_B, accents[i]);
            pTag.setBounds(6, 4, 20, 12);
            oCard.add(pTag);

            otherNameLabels[i] = Style.makeLabel("---", Style.FONT_SMALL, Style.TEXT_WHITE);
            otherNameLabels[i].setBounds(24, 4, 90, 12);
            oCard.add(otherNameLabels[i]);

            otherScoreLabels[i] = Style.makeLabel("0 pts", Style.FONT_XS, Style.TEXT_GOLD, SwingConstants.RIGHT);
            otherScoreLabels[i].setBounds(100, 4, 38, 12);
            oCard.add(otherScoreLabels[i]);

            otherStatusLabels[i] = Style.makeLabel("NORMAL", Style.FONT_XS_P, Style.ACCENT_GREEN);
            otherStatusLabels[i].setBounds(6, 22, 70, 14);
            oCard.add(otherStatusLabels[i]);

            otherItemLabels[i] = Style.makeLabel("NONE", Style.FONT_XS, Style.TEXT_MUTED, SwingConstants.RIGHT);
            otherItemLabels[i].setBounds(76, 22, 62, 14);
            oCard.add(otherItemLabels[i]);

            JSeparator oDiv = new JSeparator();
            oDiv.setForeground(Style.BORDER_DIM);
            oDiv.setBounds(6, 40, 132, 2);
            oCard.add(oDiv);

            JLabel connDot = Style.makeLabel("● CONNECTED", Style.FONT_XXS, new Color(80, 160, 80));
            connDot.setBounds(6, 44, 132, 10);
            oCard.add(connDot);
        }

        // joystick
        JoystickPanel joystick = new JoystickPanel(up, down, left, right);
        joystick.setBounds(10, 430, 140, 140);
        add(joystick);

        JLabel hint = new JLabel("<html><center>WASD / ARROWS</center></html>", SwingConstants.CENTER);
        hint.setFont(Style.FONT_XS_P);
        hint.setForeground(Style.TEXT_HINT);
        hint.setBounds(10, 575, 140, 20);
        add(hint);
    }

    // update methods called by ChronoArenaClient
    public void updateSelfCard(String name, int hp, boolean frozen, boolean hasWeapon, boolean hasShield, boolean speedBoost, String itemType) {
        SwingUtilities.invokeLater(() -> {
            cardNameLabel.setText(name);

            int clamped = Math.max(0, Math.min(100, hp));
            cardHpLabel.setText(String.valueOf(clamped));
            cardHpBar.setBounds(0, 0, (int)(124 * (clamped / 100.0)), 8);
            Color hpColor = clamped > 60 ? Style.ACCENT_GREEN : clamped > 30 ? new Color(255, 180, 0) : new Color(220, 60, 60);
            cardHpBar.setBackground(hpColor);
            cardHpLabel.setForeground(hpColor);
            cardHpBar.getParent().repaint();

            Style.applyStatusStyle(cardStatusLabel, frozen, speedBoost);
            Style.applyItemStyle(cardItemLabel, itemType);
        });
    }

    public void updateOtherPlayer(int index, String name, int score, boolean frozen, boolean speedBoost, String itemType) {
        if (index < 0 || index > 2) return;
        SwingUtilities.invokeLater(() -> {
            otherNameLabels[index].setText(name);
            otherScoreLabels[index].setText(score + " pts");
            Style.applyStatusStyle(otherStatusLabels[index], frozen, speedBoost);
            Style.applyItemStyle(otherItemLabels[index], itemType);
        });
    }
}