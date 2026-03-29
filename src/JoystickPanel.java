import javax.swing.*;
import java.awt.*;
import java.util.function.BooleanSupplier;

// visual joystick
public class JoystickPanel extends JPanel {

    private static final int BTN = 40;
    private static final int GAP = 8;

    private final BooleanSupplier up, down, left, right;

    public JoystickPanel(BooleanSupplier up, BooleanSupplier down, BooleanSupplier left, BooleanSupplier right) {
        this.up = up;
        this.down = down;
        this.left = left;
        this.right = right;
        setOpaque(false);
    }

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int cx = getWidth() / 2;
        int cy = getHeight() / 2;

        drawArrow(g, cx - BTN / 2, cy - BTN - GAP - BTN / 2, BTN, BTN, "UP", up.getAsBoolean());
        drawArrow(g, cx - BTN / 2, cy + GAP + BTN / 2, BTN, BTN, "DOWN", down.getAsBoolean());
        drawArrow(g, cx - BTN - GAP - BTN / 2, cy - BTN / 2, BTN, BTN, "LEFT", left.getAsBoolean());
        drawArrow(g, cx + GAP + BTN / 2, cy - BTN / 2, BTN, BTN, "RIGHT", right.getAsBoolean());

        // center nub
        g.setColor(new Color(50, 55, 70));
        g.fillRoundRect(cx - BTN / 2, cy - BTN / 2, BTN, BTN, 8, 8);
        g.setColor(new Color(80, 85, 100));
        g.setStroke(new BasicStroke(1.5f));
        g.drawRoundRect(cx - BTN / 2, cy - BTN / 2, BTN, BTN, 8, 8);
        g.setColor(new Color(100, 105, 120));
        g.fillOval(cx - 6, cy - 6, 12, 12);
    }

    private void drawArrow(Graphics2D g, int x, int y, int w, int h, String dir, boolean pressed) {
        Color bg = pressed ? new Color(80, 140, 255) : new Color(45, 48, 62);
        Color rim = pressed ? new Color(120, 170, 255) : new Color(70, 75, 95);

        g.setColor(bg);
        g.fillRoundRect(x, y, w, h, 8, 8);
        g.setColor(rim);
        g.setStroke(new BasicStroke(1.5f));
        g.drawRoundRect(x, y, w, h, 8, 8);

        Color arrowCol = pressed ? Color.WHITE : new Color(140, 150, 170);
        g.setColor(arrowCol);

        int cx = x + w / 2;
        int cy = y + h / 2;
        int s = 8;

        int[] px, py;
        switch (dir) {
            case "UP":
                px = new int[]{cx, cx - s, cx + s};
                py = new int[]{cy - s, cy + s, cy + s};
                break;
            case "DOWN":
                px = new int[]{cx, cx - s, cx + s};
                py = new int[]{cy + s, cy - s, cy - s};
                break;
            case "LEFT":
                px = new int[]{cx - s, cx + s, cx + s};
                py = new int[]{cy, cy - s, cy + s};
                break;
            default: // RIGHT
                px = new int[]{cx + s, cx - s, cx - s};
                py = new int[]{cy, cy - s, cy + s};
                break;
        }
        g.fillPolygon(px, py, 3);
    }

    @Override
    public Dimension getPreferredSize() {
        int total = BTN * 3 + GAP * 2;
        return new Dimension(total, total);
    }
}