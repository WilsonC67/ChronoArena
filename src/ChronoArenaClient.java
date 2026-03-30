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

    private boolean holdUp, holdDown, holdLeft, holdRight;

    private HUDPanel hud;
    private SidebarPanel sidebar;
    private ActionbarPanel actionbar;

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

    // build UI
    private void buildUI() {
        setTitle("ChronoArena");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setFocusable(true);
        setResizable(false);
        setBackground(Color.BLACK);

        hud = new HUDPanel();
        sidebar = new SidebarPanel(localPlayerId, () -> holdUp, () -> holdDown, () -> holdLeft, () -> holdRight, () -> System.exit(0));
        actionbar = new ActionbarPanel(action -> sendUDP("ACTION " + localPlayerId + " " + action + " " + udpSeq++));

        setLayout(new BorderLayout());
        add(hud, BorderLayout.NORTH);
        add(sidebar, BorderLayout.WEST);
        add(new DisplayPanel(), BorderLayout.CENTER);
        add(actionbar, BorderLayout.SOUTH);

        setupKeyListeners();
        pack();
        setLocationRelativeTo(null);
        setVisible(true);
    }

    // key listeners for movement
    private void setupKeyListeners() {
        JComponent root = getRootPane();
        InputMap im = root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = root.getActionMap();

        bindKey(im, KeyEvent.VK_UP, "UP");
        bindKey(im, KeyEvent.VK_DOWN, "DOWN");
        bindKey(im, KeyEvent.VK_LEFT, "LEFT");
        bindKey(im, KeyEvent.VK_RIGHT, "RIGHT");
        bindKey(im, KeyEvent.VK_W, "UP");
        bindKey(im, KeyEvent.VK_S, "DOWN");
        bindKey(im, KeyEvent.VK_A, "LEFT");
        bindKey(im, KeyEvent.VK_D, "RIGHT");

        am.put("UP_P", new AbstractAction() { public void actionPerformed(ActionEvent e) {
            if (!holdUp) { holdUp = true; repaintJoystick(); sendUDP("MOVE " + localPlayerId + " 0 -1 " + udpSeq++); }
        }});
        am.put("DOWN_P", new AbstractAction() { public void actionPerformed(ActionEvent e) {
            if (!holdDown) { holdDown = true; repaintJoystick(); sendUDP("MOVE " + localPlayerId + " 0 1 " + udpSeq++); }
        }});
        am.put("LEFT_P", new AbstractAction() { public void actionPerformed(ActionEvent e) {
            if (!holdLeft) { holdLeft = true; repaintJoystick(); sendUDP("MOVE " + localPlayerId + " -1 0 " + udpSeq++); }
        }});
        am.put("RIGHT_P", new AbstractAction() { public void actionPerformed(ActionEvent e) {
            if (!holdRight) { holdRight = true; repaintJoystick(); sendUDP("MOVE " + localPlayerId + " 1 0 " + udpSeq++); }
        }});

        am.put("UP_R", new AbstractAction() { public void actionPerformed(ActionEvent e) { holdUp = false; repaintJoystick(); }});
        am.put("DOWN_R", new AbstractAction() { public void actionPerformed(ActionEvent e) { holdDown = false; repaintJoystick(); }});
        am.put("LEFT_R", new AbstractAction() { public void actionPerformed(ActionEvent e) { holdLeft = false; repaintJoystick(); }});
        am.put("RIGHT_R", new AbstractAction() { public void actionPerformed(ActionEvent e) { holdRight = false; repaintJoystick(); }});
    }

    private void bindKey(InputMap im, int keyCode, String dir) {
        im.put(KeyStroke.getKeyStroke(keyCode, 0, false), dir + "_P");
        im.put(KeyStroke.getKeyStroke(keyCode, 0, true), dir + "_R");
    }

    private void repaintJoystick() {
        if (sidebar != null) sidebar.repaint();
    }


    // updates timer and scores
    public void updateHUD(int secondsLeft, int[] scores) {
        SwingUtilities.invokeLater(() -> hud.update(secondsLeft, scores));
    }

    // updates own player card
    public void updatePlayerCard(String name, int hp, boolean frozen, boolean hasWeapon, boolean hasShield, boolean speedBoost, String itemType) {
        sidebar.updateSelfCard(name, hp, frozen, hasWeapon, hasShield, speedBoost, itemType);
    }

    // updates other player cards
    public void updateOtherPlayer(int index, String name, int score, boolean frozen, boolean speedBoost, String itemType) {
        sidebar.updateOtherPlayer(index, name, score, frozen, speedBoost, itemType);
    }

    // updates zone and capture
    public void updateZone(int zoneIndex, String ownerName, double captureProgress) {
        actionbar.updateZone(zoneIndex, ownerName, captureProgress);
    }

    // updates item held
    public void updateItemHeld(String itemType) {
        actionbar.updateItemHeld(itemType);
    }

    // networking
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

    public static void main(String[] args) {
        new Thread(new ChronoArenaClient()).start();
    }
}