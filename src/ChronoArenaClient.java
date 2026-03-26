import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

public class ChronoArenaClient extends JFrame {

    private GameClient gameClient;

    public ChronoArenaClient(GameClient gameClient) {
        setTitle("ChronoArena");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setFocusable(true);
        setResizable(false);
        setBackground(Color.BLACK);

        //key listeners to read user input
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                int key = e.getKeyCode();
                gameClient.sendPressed(key);
            }
        });

        GamePanel gamePanel = new GamePanel();

        // HUD bar
        JPanel hudBar = new JPanel(null);
        hudBar.setPreferredSize(new Dimension(900, 48));
        hudBar.setBackground(new Color(25, 25, 35));

        JLabel title = new JLabel("CHRONOARENA");
        title.setFont(new Font("SansSerif", Font.BOLD, 18));
        title.setForeground(new Color(255, 200, 40));
        title.setBounds(12, 10, 230, 28);
        hudBar.add(title);

        JLabel timer = new JLabel("TIME LEFT: 02:35");
        timer.setFont(new Font("SansSerif", Font.BOLD, 16));
        timer.setForeground(Color.WHITE);
        timer.setBounds(330, 10, 220, 28);
        hudBar.add(timer);

        // score boxes
        int[] scores = {120, 90, 75, 60};
        Color[] scoreColors = {new Color(60, 100, 220), new Color(200, 50, 50), new Color(50, 160, 50), new Color(160, 140, 60)};
        for (int i = 0; i < 4; i++) {
            JLabel scoreLbl = new JLabel(String.valueOf(scores[i]), SwingConstants.CENTER);
            scoreLbl.setFont(new Font("SansSerif", Font.BOLD, 14));
            scoreLbl.setForeground(Color.WHITE);
            scoreLbl.setOpaque(true);
            scoreLbl.setBackground(scoreColors[i]);
            scoreLbl.setBounds(690 + i * 52, 10, 46, 28);
            hudBar.add(scoreLbl);
        }
        
        JLabel scoreLbl = new JLabel("SCORE");
        scoreLbl.setFont(new Font("SansSerif", Font.BOLD, 12));
        scoreLbl.setForeground(Color.LIGHT_GRAY);
        scoreLbl.setBounds(643, 13, 50, 22);
        hudBar.add(scoreLbl);

        // left sidebar
        JPanel sidebar = new JPanel(null);
        sidebar.setPreferredSize(new Dimension(160, 600));
        sidebar.setBackground(new Color(30, 30, 42));

        // exit
        JButton exitBtn = makeStyledButton("EXIT", new Color(180, 50, 50));
        exitBtn.setBounds(38, 10, 80, 30);
        sidebar.add(exitBtn);

        // bottom action bar
        JPanel actionBar = new JPanel(null);
        actionBar.setPreferredSize(new Dimension(900, 52));
        actionBar.setBackground(new Color(25, 25, 35));

        JButton dashBtn = makeStyledButton("DASH", new Color(60, 160, 80));
        dashBtn.setBounds(170, 8, 100, 36);
        actionBar.add(dashBtn);

        JButton tagBtn = makeStyledButton("TAG", new Color(200, 140, 30));
        tagBtn.setBounds(280, 8, 100, 36);
        actionBar.add(tagBtn);

        // general layout
        setLayout(new BorderLayout());
        add(hudBar, BorderLayout.NORTH);
        add(sidebar, BorderLayout.WEST);
        add(gamePanel, BorderLayout.CENTER);
        add(actionBar, BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(null);
        setVisible(true);
    }

    private JButton makeStyledButton(String text, Color bg) {
        JButton btn = new JButton(text) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getModel().isPressed() ? bg.darker() : bg);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                super.paintComponent(g);
            }
        };
        btn.setFont(new Font("SansSerif", Font.BOLD, 13));
        btn.setForeground(Color.WHITE);
        btn.setOpaque(false);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        return btn;
    }
}