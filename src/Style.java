import java.awt.*;
import javax.swing.*;

public final class Style {
    private Style() {}

    public static final Color BG_DARK = new Color(25, 25, 35);
    public static final Color BG_PANEL = new Color(30, 30, 42);
    public static final Color BG_CARD = new Color(38, 40, 55);
    public static final Color BG_BAR = new Color(45, 48, 60);
    public static final Color BORDER_DIM = new Color(55, 58, 75);

    public static final Color TEXT_WHITE = Color.WHITE;
    public static final Color TEXT_MUTED = new Color(120, 125, 145);
    public static final Color TEXT_DIMMER = new Color(90, 95, 115);
    public static final Color TEXT_HINT = new Color(100, 110, 130);
    public static final Color TEXT_GOLD = new Color(255, 200, 40);

    public static final Color ACCENT_BLUE = new Color(60, 100, 220);
    public static final Color ACCENT_GREEN = new Color(100, 220, 100);
    public static final Color ACCENT_ORANGE = new Color(255, 200, 40);

    public static final Color ITEM_GUN = new Color(255, 100, 100);
    public static final Color ITEM_SHIELD = new Color(100, 160, 255);
    public static final Color ITEM_SPEED = new Color(100, 220, 100);
    public static final Color ITEM_ENERGY = new Color(255, 200, 40);

    public static final Color STATUS_FROZEN = new Color(100, 180, 255);
    public static final Color STATUS_OK     = new Color(100, 220, 100);
    public static final Color STATUS_SPEED  = new Color(255, 200,  40);  // gold/yellow for speed boost

    public static final Color[] PLAYER_ACCENTS = {
        new Color(80, 120, 255),   // P1 — bright blue  (matches top bar)
        new Color(220,  60,  60),  // P2 — bright red
        new Color(60,  200,  60),  // P3 — bright green
        new Color(200, 170,  50)   // P4 — gold/olive
    };

    public static final Font FONT_TITLE = new Font("SansSerif", Font.BOLD, 18);
    public static final Font FONT_LARGE = new Font("SansSerif", Font.BOLD, 16);
    public static final Font FONT_MED = new Font("SansSerif", Font.BOLD, 14);
    public static final Font FONT_NORM = new Font("SansSerif", Font.BOLD, 13);
    public static final Font FONT_SMALL = new Font("SansSerif", Font.BOLD, 11);
    public static final Font FONT_XS = new Font("SansSerif", Font.BOLD, 10);
    public static final Font FONT_XS_P = new Font("SansSerif", Font.PLAIN, 10);
    public static final Font FONT_XXS = new Font("SansSerif", Font.PLAIN, 9);
    public static final Font FONT_XXS_B = new Font("SansSerif", Font.BOLD, 9);

    // shared helpers for all panels
    public static JLabel makeLabel(String text, Font font, Color fg) {
        return makeLabel(text, font, fg, javax.swing.SwingConstants.LEFT);
    }

    public static JLabel makeLabel(String text, Font font, Color fg, int align) {
        JLabel lbl = new JLabel(text, align);
        lbl.setFont(font);
        lbl.setForeground(fg);
        return lbl;
    }

    public static void applyItemStyle(JLabel label, String itemType) {
        switch (itemType) {
            case "GUN": label.setText("GUN"); label.setForeground(ITEM_GUN); break;
            case "SHIELD": label.setText("SHIELD"); label.setForeground(ITEM_SHIELD); break;
            case "SPEED_BOOST": label.setText("SPEED BOOST");label.setForeground(ITEM_SPEED); break;
            case "ENERGY": label.setText("ENERGY"); label.setForeground(ITEM_ENERGY); break;
            default: label.setText("NONE"); label.setForeground(TEXT_MUTED); break;
        }
    }

    public static void applyStatusStyle(JLabel label, boolean frozen, boolean speedBoost) {
        if (frozen) {
            label.setText("FROZEN");     label.setForeground(STATUS_FROZEN);
        } else if (speedBoost) {
            label.setText("SPEED BOOST"); label.setForeground(STATUS_SPEED);
        } else {
            label.setText("NORMAL");     label.setForeground(STATUS_OK);
        }
    }

    public static JButton makeStyledButton(String text, Color bg) {
        JButton btn = new JButton(text) {
            @Override protected void paintComponent(java.awt.Graphics g) {
                java.awt.Graphics2D g2 = (java.awt.Graphics2D) g;
                g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getModel().isPressed() ? bg.darker() : bg);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                super.paintComponent(g);
            }
        };
        btn.setFont(FONT_NORM);
        btn.setForeground(TEXT_WHITE);
        btn.setOpaque(false);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        return btn;
    }
}