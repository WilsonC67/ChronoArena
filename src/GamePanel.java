import javax.swing.*;
import java.awt.*;

public class GamePanel extends JPanel {
    public GamePanel() {
        setPreferredSize(new Dimension(800, 600));
        setBackground(new Color(230, 230, 230));
        setFocusable(true);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, getWidth(), 40);
        
        g.setColor(Color.WHITE);
        g.drawString("TIME: 02:35", 20, 25);
        g.drawString("SCORE: 120", 700, 25);

        // zone placeholder
        g.setColor(Color.ORANGE);
        g.drawRect(500, 300, 150, 100);
        g.drawString("ZONE C (UNCLAIMED)", 510, 350);
    }
}