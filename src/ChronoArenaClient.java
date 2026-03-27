import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;

public class ChronoArenaClient extends JFrame implements Runnable {

    private Socket tcpSocket;
    private DatagramSocket udpSocket;
    private String serverIp;
    private int udpPort;
    private int localPlayerId;
    private long udpSeq = 0;
    private DataOutputStream dataOutputStream;
    private DataInputStream dataInputStream;

    // tracks which directions are held
    private boolean holdUp, holdDown, holdLeft, holdRight;

    // the joystick panel (for updates)
    private JoystickPanel joystick;

    public ChronoArenaClient(Socket tcpSocket, DatagramSocket udpSocket, String serverIp, int udpPort, int playerId) {
        this.tcpSocket = tcpSocket;
        this.udpSocket = udpSocket;
        this.serverIp = serverIp;
        this.udpPort = udpPort;
        this.localPlayerId = playerId;
    }

    public ChronoArenaClient() {}

    @Override
    public void run() {
        SwingUtilities.invokeLater(this::buildUI);
    }

    private void buildUI() {
        setTitle("ChronoArena");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setFocusable(true);
        setResizable(false);
        setBackground(Color.BLACK);

        DisplayPanel displayPanel = new DisplayPanel();

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
        Color[] scoreColors = {
            new Color(60, 100, 220), new Color(200, 50, 50),
            new Color(50, 160, 50), new Color(160, 140, 60)
        };
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

        JButton exitBtn = makeStyledButton("EXIT", new Color(180, 50, 50));
        exitBtn.setBounds(38, 10, 80, 30);
        exitBtn.addActionListener(e -> System.exit(0));
        sidebar.add(exitBtn);

        // joystick
        joystick = new JoystickPanel();
        joystick.setBounds(10, 430, 140, 140);
        sidebar.add(joystick);

        // label below joystick (wasd/arrow keys)
        JLabel hint = new JLabel("<html><center>WASD / ARROWS</center></html>", SwingConstants.CENTER);
        hint.setFont(new Font("SansSerif", Font.PLAIN, 10));
        hint.setForeground(new Color(100, 110, 130));
        hint.setBounds(10, 575, 140, 20);
        sidebar.add(hint);

        // bottom action bar 
        JPanel actionBar = new JPanel(null);
        actionBar.setPreferredSize(new Dimension(900, 52));
        actionBar.setBackground(new Color(25, 25, 35));

        JButton dashBtn = makeStyledButton("DASH", new Color(60, 160, 80));
        dashBtn.setBounds(170, 8, 100, 36);
        dashBtn.addActionListener(e -> sendUDP("ACTION " + localPlayerId + " DASH " + udpSeq++));
        actionBar.add(dashBtn);

        JButton tagBtn = makeStyledButton("TAG", new Color(200, 140, 30));
        tagBtn.setBounds(280, 8, 100, 36);
        tagBtn.addActionListener(e -> sendUDP("ACTION " + localPlayerId + " TAG " + udpSeq++));
        actionBar.add(tagBtn);

        // general layout
        setLayout(new BorderLayout());
        add(hudBar, BorderLayout.NORTH);
        add(sidebar, BorderLayout.WEST);
        add(displayPanel, BorderLayout.CENTER);
        add(actionBar, BorderLayout.SOUTH);

        setupKeyListeners();

        pack();
        setLocationRelativeTo(null);
        setVisible(true);
    }

    // key listeners to joystick visual
    private void setupKeyListeners() {
        JComponent root = getRootPane();
        InputMap im = root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = root.getActionMap();

        // pressed bindings
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0, false), "UP_P");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0, false), "DOWN_P");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0, false), "LEFT_P");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0, false), "RIGHT_P");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_W, 0, false), "UP_P");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_S, 0, false), "DOWN_P");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_A, 0, false), "LEFT_P");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_D, 0, false), "RIGHT_P");

        // released bindings
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0, true), "UP_R");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0, true), "DOWN_R");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0, true), "LEFT_R");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0, true), "RIGHT_R");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_W, 0, true), "UP_R");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_S, 0, true), "DOWN_R");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_A, 0, true), "LEFT_R");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_D, 0, true), "RIGHT_R");

        // press actions send UDP and light up joystick
        am.put("UP_P", new AbstractAction() { public void actionPerformed(ActionEvent e) {
            if (!holdUp) { holdUp = true; joystick.repaint(); sendUDP("MOVE " + localPlayerId + " 0 -1 " + udpSeq++); }
        }});
        am.put("DOWN_P", new AbstractAction() { public void actionPerformed(ActionEvent e) {
            if (!holdDown) { holdDown = true; joystick.repaint(); sendUDP("MOVE " + localPlayerId + " 0 1 " + udpSeq++); }
        }});
        am.put("LEFT_P", new AbstractAction() { public void actionPerformed(ActionEvent e) {
            if (!holdLeft) { holdLeft = true; joystick.repaint(); sendUDP("MOVE " + localPlayerId + " -1 0 " + udpSeq++); }
        }});
        am.put("RIGHT_P", new AbstractAction() { public void actionPerformed(ActionEvent e) {
            if (!holdRight) { holdRight = true; joystick.repaint(); sendUDP("MOVE " + localPlayerId + " 1 0 " + udpSeq++); }
        }});

        // release actions to unlight
        am.put("UP_R", new AbstractAction() { public void actionPerformed(ActionEvent e) { holdUp = false; joystick.repaint(); }});
        am.put("DOWN_R", new AbstractAction() { public void actionPerformed(ActionEvent e) { holdDown = false; joystick.repaint(); }});
        am.put("LEFT_R", new AbstractAction() { public void actionPerformed(ActionEvent e) { holdLeft = false; joystick.repaint(); }});
        am.put("RIGHT_R", new AbstractAction() { public void actionPerformed(ActionEvent e) { holdRight = false; joystick.repaint(); }});
    }

    private void sendUDP(String message) {
        if (udpSocket == null) return;
        try {
            byte[] data = message.getBytes();
            DatagramPacket pkt = new DatagramPacket(data, data.length, InetAddress.getByName(serverIp), udpPort);
            udpSocket.send(pkt);
        } catch (IOException e) {
            System.err.println("[UDP] Send failed: " + e.getMessage());
        }
    }

    public void receiveTCPMessages() {
        new Thread(() -> {
            try {
                while (tcpSocket.isConnected()) {
                    String msg = dataInputStream.readUTF();
                    System.out.println(msg);
                }
            } catch (IOException e) {
                System.out.println("ERROR IN RECEIVING TCP MESSAGE FROM SERVER");
                e.printStackTrace();
            }
        }).start();
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

    // visual joystick
    private class JoystickPanel extends JPanel {

        private static final int BTN = 40;
        private static final int GAP = 8;

        JoystickPanel() {
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            Graphics2D g = (Graphics2D) g0;
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int cx = getWidth() / 2;
            int cy = getHeight() / 2;

            drawArrow(g, cx - BTN/2, cy - BTN - GAP - BTN/2, BTN, BTN, "UP", holdUp);
            drawArrow(g, cx - BTN/2, cy + GAP + BTN/2, BTN, BTN, "DOWN", holdDown);
            drawArrow(g, cx - BTN - GAP - BTN/2, cy - BTN/2, BTN, BTN, "LEFT", holdLeft);
            drawArrow(g, cx + GAP + BTN/2, cy - BTN/2, BTN, BTN, "RIGHT", holdRight);

            g.setColor(new Color(50, 55, 70));
            g.fillRoundRect(cx - BTN/2, cy - BTN/2, BTN, BTN, 8, 8);
            g.setColor(new Color(80, 85, 100));
            g.setStroke(new BasicStroke(1.5f));
            g.drawRoundRect(cx - BTN/2, cy - BTN/2, BTN, BTN, 8, 8);

            g.setColor(new Color(100, 105, 120));
            g.fillOval(cx - 6, cy - 6, 12, 12);
        }

        private void drawArrow(Graphics2D g, int x, int y, int w, int h, String dir, boolean pressed) {
            // Button background
            Color bg = pressed ? new Color(80, 140, 255) : new Color(45, 48, 62);
            Color rim = pressed ? new Color(120, 170, 255) : new Color(70, 75, 95);

            g.setColor(bg);
            g.fillRoundRect(x, y, w, h, 8, 8);
            g.setColor(rim);
            g.setStroke(new BasicStroke(1.5f));
            g.drawRoundRect(x, y, w, h, 8, 8);

            // arrows
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

    public static void main(String[] args) {
        ChronoArenaClient client = new ChronoArenaClient();
        new Thread(client).start();
    }
}