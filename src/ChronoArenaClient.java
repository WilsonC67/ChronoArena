import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;

/**
 * ChronoArenaClient — the player-side application.
 *
 * On launch it asks for a player ID (1-4), then:
 *   1. DisplayPanel connects to GameServer via TCP and sends "JOIN <id>".
 *   2. The server registers the player in GameLogic and replies "WELCOME <id>".
 *   3. The server broadcasts rendered frames; DisplayPanel paints them.
 *   4. Arrow / WASD keystrokes send UDP movement packets to the server.
 *
 * Configuration is read from config.properties (server.ip, tcp.port, udp.port).
 */
public class ChronoArenaClient extends JFrame implements GameEventListener {

    // ── Config ────────────────────────────────────────────────────────────────
    private static final String SERVER_IP  = PropertyFileReader.getIP();
    private static final int    UDP_PORT   = PropertyFileReader.getUDPPort();

    // ── Network ───────────────────────────────────────────────────────────────
    private DatagramSocket udpSocket;
    private long           udpSeq = 0;

    // ── Player state ──────────────────────────────────────────────────────────
    private final int localPlayerId;
    private boolean holdUp, holdDown, holdLeft, holdRight;

    // ── UI panels ─────────────────────────────────────────────────────────────
    private HUDPanel      hud;
    private SidebarPanel  sidebar;
    private ActionbarPanel actionbar;
    private GameOverPanel  gameOverPanel;
    private DisplayPanel   displayPanel;

    // ── Constructor ───────────────────────────────────────────────────────────

    public ChronoArenaClient(int playerId) {
        this.localPlayerId = playerId;
    }

    // ── Build & launch ────────────────────────────────────────────────────────

    public void launch() {
        try { udpSocket = new DatagramSocket(); }
        catch (SocketException e) { System.err.println("UDP socket error: " + e.getMessage()); }

        SwingUtilities.invokeLater(this::buildUI);
    }

    private void buildUI() {
        setTitle("ChronoArena — Player " + localPlayerId);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false);
        setBackground(Color.BLACK);

        hud      = new HUDPanel();
        hud.setGameEventListener(this);

        sidebar  = new SidebarPanel(localPlayerId,
                () -> holdUp, () -> holdDown, () -> holdLeft, () -> holdRight,
                () -> System.exit(0));

        actionbar = new ActionbarPanel(action ->
                sendUDP("ACTION " + localPlayerId + " " + action + " " + udpSeq++));

        setLayout(new BorderLayout());
        add(hud,      BorderLayout.NORTH);
        add(sidebar,  BorderLayout.WEST);
        add(actionbar, BorderLayout.SOUTH);

        // DisplayPanel sends JOIN <playerId> to the server on connect
        displayPanel = new DisplayPanel(localPlayerId);
        Dimension dp = displayPanel.getPreferredSize();

        // Layer displayPanel + game-over overlay
        JLayeredPane center = new JLayeredPane();
        center.setPreferredSize(dp);
        displayPanel.setBounds(0, 0, dp.width, dp.height);
        gameOverPanel = new GameOverPanel();
        gameOverPanel.setBounds(0, 0, dp.width, dp.height);
        center.add(displayPanel,  JLayeredPane.DEFAULT_LAYER);
        center.add(gameOverPanel, JLayeredPane.PALETTE_LAYER);
        add(center, BorderLayout.CENTER);

        setupKeyListeners();
        pack();
        setLocationRelativeTo(null);
        setVisible(true);
    }

    // ── Key input → UDP movement packets ─────────────────────────────────────

    private void setupKeyListeners() {
        JComponent root = getRootPane();
        InputMap  im = root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = root.getActionMap();

        bindKey(im, KeyEvent.VK_UP,    "UP");
        bindKey(im, KeyEvent.VK_DOWN,  "DOWN");
        bindKey(im, KeyEvent.VK_LEFT,  "LEFT");
        bindKey(im, KeyEvent.VK_RIGHT, "RIGHT");
        bindKey(im, KeyEvent.VK_W,     "UP");
        bindKey(im, KeyEvent.VK_S,     "DOWN");
        bindKey(im, KeyEvent.VK_A,     "LEFT");
        bindKey(im, KeyEvent.VK_D,     "RIGHT");

        am.put("UP_P",    press(() -> { holdUp    = true;  repaintJoystick(); moveUDP(0, -1); }));
        am.put("DOWN_P",  press(() -> { holdDown  = true;  repaintJoystick(); moveUDP(0,  1); }));
        am.put("LEFT_P",  press(() -> { holdLeft  = true;  repaintJoystick(); moveUDP(-1, 0); }));
        am.put("RIGHT_P", press(() -> { holdRight = true;  repaintJoystick(); moveUDP(1,  0); }));

        am.put("UP_R",    release(() -> { holdUp    = false; repaintJoystick(); }));
        am.put("DOWN_R",  release(() -> { holdDown  = false; repaintJoystick(); }));
        am.put("LEFT_R",  release(() -> { holdLeft  = false; repaintJoystick(); }));
        am.put("RIGHT_R", release(() -> { holdRight = false; repaintJoystick(); }));
    }

    private void bindKey(InputMap im, int keyCode, String dir) {
        im.put(KeyStroke.getKeyStroke(keyCode, 0, false), dir + "_P");
        im.put(KeyStroke.getKeyStroke(keyCode, 0, true),  dir + "_R");
    }

    private AbstractAction press(Runnable r) {
        return new AbstractAction() { public void actionPerformed(ActionEvent e) { r.run(); } };
    }
    private AbstractAction release(Runnable r) {
        return new AbstractAction() { public void actionPerformed(ActionEvent e) { r.run(); } };
    }

    private void moveUDP(int dx, int dy) {
        // Map direction to PlayerActionEnum name expected by PlayerListener
        String action;
        if      (dx == -1) action = "MOVE_LEFT";
        else if (dx ==  1) action = "MOVE_RIGHT";
        else if (dy == -1) action = "MOVE_UP";
        else               action = "MOVE_DOWN";
        // Format: playerId,tcpPort,action,x,y,extra,seq
        sendUDP(localPlayerId + "," + UDP_PORT + "," + action + ",0.0,0.0,," + udpSeq++);
    }

    private void repaintJoystick() { if (sidebar != null) sidebar.repaint(); }

    // ── GameEventListener ─────────────────────────────────────────────────────

    @Override public void onGameStart()    { System.out.println("=== GAME START ==="); }
    @Override public void onPlayerFrozen() { System.out.println("=== PLAYER FROZEN ==="); }

    @Override
    public void onGameEnd() {
        System.out.println("=== GAME OVER ===");
        // TODO: receive real scores from server
        int[]    scores = {0, 0, 0, 0};
        String[] names  = {"Player 1", "Player 2", "Player 3", "Player 4"};
        SwingUtilities.invokeLater(() -> gameOverPanel.show(scores, names));
    }

    // ── UDP sender ────────────────────────────────────────────────────────────

    private void sendUDP(String message) {
        if (udpSocket == null) return;
        try {
            byte[]         data = message.getBytes();
            DatagramPacket pkt  = new DatagramPacket(data, data.length,
                    InetAddress.getByName(SERVER_IP), UDP_PORT);
            udpSocket.send(pkt);
        } catch (IOException e) {
            System.err.println("[UDP] Send failed: " + e.getMessage());
        }
    }

    // ── Entry point ───────────────────────────────────────────────────────────

    /**
     * Prompts for a player ID (1-4) then launches the client.
     * The player ID determines which "seat" the player occupies in the game.
     */
    public static void main(String[] args) {
        // Try to read player ID from system property first, then prompt
        int pid = 0;
        String prop = System.getProperty("player.id");
        if (prop != null) {
            try { pid = Integer.parseInt(prop.trim()); } catch (NumberFormatException ignored) {}
        }

        if (pid < 1 || pid > 4) {
            String input = JOptionPane.showInputDialog(null,
                    "Enter your Player ID (1-4):", "ChronoArena", JOptionPane.QUESTION_MESSAGE);
            if (input == null) System.exit(0);
            try { pid = Integer.parseInt(input.trim()); }
            catch (NumberFormatException e) { pid = 1; }
            pid = Math.max(1, Math.min(4, pid));
        }

        final int finalPid = pid;
        SwingUtilities.invokeLater(() -> new ChronoArenaClient(finalPid).launch());
    }
}
