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

    // constructor called by gameclient
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

        JButton exitBtn = makeStyledButton("EXIT", new Color(180, 50, 50));
        exitBtn.setBounds(38, 10, 80, 30);
        sidebar.add(exitBtn);

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
        add(hudBar,    BorderLayout.NORTH);
        add(sidebar,   BorderLayout.WEST);
        add(gamePanel, BorderLayout.CENTER);
        add(actionBar, BorderLayout.SOUTH);

        setupKeyListeners();

        pack();
        setLocationRelativeTo(null);
        setVisible(true);
    }

    private void setupKeyListeners() {
        JComponent root = getRootPane();
        InputMap  im = root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = root.getActionMap();

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_UP,    0), "UP");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN,  0), "DOWN");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT,  0), "LEFT");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), "RIGHT");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_W,     0), "UP");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_S,     0), "DOWN");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_A,     0), "LEFT");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_D,     0), "RIGHT");

        am.put("UP",    new AbstractAction() { public void actionPerformed(ActionEvent e) { sendUDP("MOVE " + localPlayerId + " 0 -1 " + udpSeq++); } });
        am.put("DOWN",  new AbstractAction() { public void actionPerformed(ActionEvent e) { sendUDP("MOVE " + localPlayerId + " 0 1 "  + udpSeq++); } });
        am.put("LEFT",  new AbstractAction() { public void actionPerformed(ActionEvent e) { sendUDP("MOVE " + localPlayerId + " -1 0 " + udpSeq++); } });
        am.put("RIGHT", new AbstractAction() { public void actionPerformed(ActionEvent e) { sendUDP("MOVE " + localPlayerId + " 1 0 "  + udpSeq++); } });
    }

    private void sendUDP(String message) {
        if (udpSocket == null) return;
        try {
            byte[] data = message.getBytes();
            DatagramPacket pkt = new DatagramPacket(
                    data, data.length, InetAddress.getByName(serverIp), udpPort);
            udpSocket.send(pkt);
        } catch (IOException e) {
            System.err.println("[UDP] Send failed: " + e.getMessage());
        }
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

    public void recieveTCPMessages(){
        new Thread(new Runnable(){
            @Override
            public void run(){
                String msgFromServer;

                try {
                    while(tcpSocket.isConnected()){
                    msgFromServer = dataInputStream.readUTF();

                    //used for testing what TCP messages are recieved
                    //can delete later
                    System.out.println(msgFromServer);
                }
                } catch (IOException e) {
                    System.out.println("ERROR IN RECIEVING TCP MESSAGE FROM SERVER");
                    e.printStackTrace();
                }
            }
        }).start();
    }

    public static void main(String[] args) {
        ChronoArenaClient client = new ChronoArenaClient();
        new Thread(client).start();
    }
}