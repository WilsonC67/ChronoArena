import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;

/**
 * ChronoArenaClient — the player-side application.
 *
 * Usage:
 *   java -cp . ChronoArenaClient <serverIp> [playerId]
 *
 * Examples:
 *   java -cp . ChronoArenaClient 192.168.1.42          (prompts for player ID)
 *   java -cp . ChronoArenaClient 192.168.1.42 2        (player 2, no prompt)
 *   java -cp . ChronoArenaClient localhost              (local testing)
 *
 * Alternatively use system properties:
 *   java -Dserver.ip=192.168.1.42 -Dplayer.id=3 -cp . ChronoArenaClient
 *
 * The server IP is passed directly to DisplayPanel so config.properties
 * does not need to be edited on the client machine.
 */
public class ChronoArenaClient extends JFrame implements GameEventListener {

    // ── Ports (from config.properties — same on all machines) ─────────────────
    private static final int UDP_PORT = PropertyFileReader.getPlayerListenerPort();

    // ── Network ───────────────────────────────────────────────────────────────
    private final String       serverIp;
    private final int          localPlayerId;
    private       DatagramSocket udpSocket;
    private       long           udpSeq = 0;

    // ── Movement flags ────────────────────────────────────────────────────────
    private boolean holdUp, holdDown, holdLeft, holdRight;
    private int lastDx = 0, lastDy = 0;

    // ── UI ────────────────────────────────────────────────────────────────────
    private HUDPanel       hud;
    private SidebarPanel   sidebar;
    private ActionbarPanel actionbar;
    private GameOverPanel  gameOverPanel;
    private LobbyPanel     lobbyPanel;

    // ── Constructor ───────────────────────────────────────────────────────────

    public ChronoArenaClient(String serverIp, int playerId) {
        this.serverIp      = serverIp;
        this.localPlayerId = playerId;
    }

    // ── Launch ────────────────────────────────────────────────────────────────

    public void launch() {
        try { udpSocket = new DatagramSocket(); }
        catch (SocketException e) {
            System.err.println("[Client] Could not open UDP socket: " + e.getMessage());
        }
        SwingUtilities.invokeLater(this::buildUI);
    }

    private void buildUI() {
        setTitle("ChronoArena — Player " + localPlayerId + "  [server: " + serverIp + "]");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false);
        setBackground(Color.BLACK);

        hud = new HUDPanel();
        hud.setGameEventListener(this);

        sidebar = new SidebarPanel(localPlayerId,
                () -> holdUp, () -> holdDown, () -> holdLeft, () -> holdRight,
                () -> System.exit(0));

        actionbar = new ActionbarPanel(action ->
                sendUDP(localPlayerId + "," + UDP_PORT + ",ACTION,0.0,0.0," + action + "," + udpSeq++));

        // DisplayPanel receives the server IP directly — no config.properties needed on client
        DisplayPanel displayPanel = new DisplayPanel(serverIp, localPlayerId);
        displayPanel.setLobbyCallback(connected -> updateLobby(connected));
        Dimension dp = displayPanel.getPreferredSize();

        // Layer displayPanel + game-over overlay + lobby overlay
        JLayeredPane center = new JLayeredPane();
        center.setPreferredSize(dp);
        displayPanel.setBounds(0, 0, dp.width, dp.height);
        gameOverPanel = new GameOverPanel();
        gameOverPanel.setBounds(0, 0, dp.width, dp.height);
        lobbyPanel = new LobbyPanel();
        lobbyPanel.setBounds(0, 0, dp.width, dp.height);
        lobbyPanel.setOnCountdownEnd(this::onGameStart);
        center.add(displayPanel,  JLayeredPane.DEFAULT_LAYER);
        center.add(gameOverPanel, JLayeredPane.PALETTE_LAYER);
        center.add(lobbyPanel,    JLayeredPane.MODAL_LAYER);

        setLayout(new BorderLayout());
        add(hud,      BorderLayout.NORTH);
        add(sidebar,  BorderLayout.WEST);
        add(center,   BorderLayout.CENTER);
        add(actionbar, BorderLayout.SOUTH);

        setupKeyListeners();
        pack();
        setLocationRelativeTo(null);
        setVisible(true);
        disableSpaceOnButtons(this);

        // Add global key event dispatcher for space to shoot
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(new KeyEventDispatcher() {
            @Override
            public boolean dispatchKeyEvent(KeyEvent e) {
                if (e.getID() == KeyEvent.KEY_PRESSED && e.getKeyCode() == KeyEvent.VK_SPACE) {
                    System.out.println("[Client] Shooting in direction " + lastDx + "," + lastDy);
                    sendUDP(localPlayerId + "," + UDP_PORT + ",SHOOT," + lastDx + ".0," + lastDy + ".0,," + udpSeq++);
                    // Update UI to remove gun icon after shooting
                    actionbar.updateItemHeld("NONE");
                    return true; // consume the event
                }
                return false;
            }
        });

    }

    // ── Key input → UDP movement ──────────────────────────────────────────────

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
        bindKey(im, KeyEvent.VK_SPACE, "SHOOT");

        am.put("UP_P",    press(() -> { if (!holdUp)    { holdUp    = true;  repaintJoystick(); moveUDP(0, -1); } }));
        am.put("DOWN_P",  press(() -> { if (!holdDown)  { holdDown  = true;  repaintJoystick(); moveUDP(0,  1); } }));
        am.put("LEFT_P",  press(() -> { if (!holdLeft)  { holdLeft  = true;  repaintJoystick(); moveUDP(-1, 0); } }));
        am.put("RIGHT_P", press(() -> { if (!holdRight) { holdRight = true;  repaintJoystick(); moveUDP(1,  0); } }));

        am.put("UP_R",    release(() -> { holdUp    = false; repaintJoystick(); }));
        am.put("DOWN_R",  release(() -> { holdDown  = false; repaintJoystick(); }));
        am.put("LEFT_R",  release(() -> { holdLeft  = false; repaintJoystick(); }));
        am.put("RIGHT_R", release(() -> { holdRight = false; repaintJoystick(); }));

        am.put("SHOOT_P", press(() -> sendUDP(localPlayerId + "," + UDP_PORT + ",SHOOT," + lastDx + ".0," + lastDy + ".0,," + udpSeq++)));
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
        String action;
        if      (dx == -1) action = "MOVE_LEFT";
        else if (dx ==  1) action = "MOVE_RIGHT";
        else if (dy == -1) action = "MOVE_UP";
        else               action = "MOVE_DOWN";
        // Format matches PlayerListener: playerId,tcpPort,action,x,y,extra,seq
        sendUDP(localPlayerId + "," + UDP_PORT + "," + action + ",0.0,0.0,," + udpSeq++);
        lastDx = dx;
        lastDy = dy;
    }

    private void repaintJoystick() { if (sidebar != null) sidebar.repaint(); }

    // ── GameEventListener ─────────────────────────────────────────────────────

    /**
     * Called by server integration when a player connects or disconnects.
     * connected[i] = true means player slot i+1 is in the lobby.
     * Example: connected = {true, true, false, false} → 2/4 players connected.
     */
    public void updateLobby(boolean[] connected) {
        SwingUtilities.invokeLater(() -> lobbyPanel.updatePlayers(connected));
    }

    @Override public void onGameStart()    { System.out.println("=== GAME START ==="); }
    @Override public void onPlayerFrozen() { System.out.println("=== PLAYER FROZEN ==="); }

    @Override
    public void onGameEnd() {
        System.out.println("=== GAME OVER ===");
        int[]    scores = {0, 0, 0, 0};
        String[] names  = {"Player 1", "Player 2", "Player 3", "Player 4"};
        SwingUtilities.invokeLater(() -> gameOverPanel.show(scores, names));
    }

    // ── UDP sender ────────────────────────────────────────────────────────────

    private void sendUDP(String message) {
        if (udpSocket == null) return;
        try {
            byte[]         data = message.getBytes();
            DatagramPacket pkt  = new DatagramPacket(
                    data, data.length, InetAddress.getByName(serverIp), UDP_PORT);
            udpSocket.send(pkt);
        } catch (IOException e) {
            System.err.println("[UDP] Send failed: " + e.getMessage());
        }
    }

    private void disableSpaceOnButtons(Container container) {
        for (Component c : container.getComponents()) {
            if (c instanceof AbstractButton btn) {
                btn.getInputMap(JComponent.WHEN_FOCUSED)
                   .put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0, false), "none");
                btn.getInputMap(JComponent.WHEN_FOCUSED)
                   .put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0, true), "none");
            }
            if (c instanceof Container child) {
                disableSpaceOnButtons(child);
            }
        }
    }

    // ── Entry point ───────────────────────────────────────────────────────────

    public static void main(String[] args) {
        // ── Resolve server IP ─────────────────────────────────────────────────
        // Priority: args[0]  →  -Dserver.ip  →  prompt
        String serverIp = null;

        if (args.length >= 1) {
            serverIp = args[0].trim();
        }
        if (serverIp == null || serverIp.isEmpty()) {
            serverIp = System.getProperty("server.ip", "").trim();
        }
        if (serverIp.isEmpty()) {
            serverIp = JOptionPane.showInputDialog(null,
                    "Enter server IP address:", "ChronoArena — Connect",
                    JOptionPane.QUESTION_MESSAGE);
            if (serverIp == null || serverIp.isBlank()) System.exit(0);
            serverIp = serverIp.trim();
        }

        // ── Resolve player ID ─────────────────────────────────────────────────
        // Priority: args[1]  →  -Dplayer.id  →  prompt
        int pid = 0;

        if (args.length >= 2) {
            try { pid = Integer.parseInt(args[1].trim()); } catch (NumberFormatException ignored) {}
        }
        if (pid < 1 || pid > 4) {
            String sysPid = System.getProperty("player.id", "").trim();
            if (!sysPid.isEmpty()) {
                try { pid = Integer.parseInt(sysPid); } catch (NumberFormatException ignored) {}
            }
        }
        if (pid < 1 || pid > 4) {
            String input = JOptionPane.showInputDialog(null,
                    "Enter your Player ID (1–4):", "ChronoArena — Player ID",
                    JOptionPane.QUESTION_MESSAGE);
            if (input == null) System.exit(0);
            try { pid = Integer.parseInt(input.trim()); } catch (NumberFormatException ignored) {}
            pid = Math.max(1, Math.min(4, pid));
        }

        final String ip    = serverIp;
        final int    finalPid = pid;

        System.out.printf("[Client] Connecting to %s  (TCP:%d  UDP:%d)  as Player %d%n",
                ip, PropertyFileReader.getTCPPort(), PropertyFileReader.getPlayerMonitorPort(), finalPid);

        SwingUtilities.invokeLater(() -> new ChronoArenaClient(ip, finalPid).launch());
    }
}