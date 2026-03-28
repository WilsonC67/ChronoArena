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

    // zone owner labels + capture bars
    private JLabel[] zoneOwnerLabels = new JLabel[3];
    private JPanel[] zoneProgressBars = new JPanel[3];

    // item held indicator
    private JLabel itemHeldLabel;

    // player info card labels (updated by server)
    private JLabel cardNameLabel;
    private JLabel cardHpLabel;
    private JPanel cardHpBar;
    private JLabel cardStatusLabel;
    private JLabel cardItemLabel;

    private JLabel[] otherNameLabels = new JLabel[3];
    private JLabel[] otherScoreLabels = new JLabel[3];
    private JLabel[] otherStatusLabels = new JLabel[3];
    private JLabel[] otherItemLabels = new JLabel[3];


    static final class Style {
        static final Color BG_DARK = new Color(25, 25, 35);
        static final Color BG_PANEL = new Color(30, 30, 42);
        static final Color BG_CARD = new Color(38, 40, 55);
        static final Color BG_BAR = new Color(45, 48, 60);
        static final Color BORDER_DIM = new Color(55, 58, 75);

        static final Color TEXT_WHITE = Color.WHITE;
        static final Color TEXT_MUTED = new Color(120, 125, 145);
        static final Color TEXT_DIMMER = new Color(90, 95, 115);
        static final Color TEXT_HINT = new Color(100, 110, 130);
        static final Color TEXT_GOLD = new Color(255, 200, 40);

        static final Color ACCENT_BLUE = new Color(60, 100, 220);
        static final Color ACCENT_GREEN = new Color(100, 220, 100);
        static final Color ACCENT_ORANGE = new Color(255, 200, 40);

        static final Color ITEM_GUN = new Color(255, 100, 100);
        static final Color ITEM_SHIELD = new Color(100, 160, 255);
        static final Color ITEM_SPEED = new Color(100, 220, 100);
        static final Color ITEM_ENERGY = new Color(255, 200, 40);

        static final Color STATUS_FROZEN = new Color(100, 180, 255);
        static final Color STATUS_OK = new Color(100, 220, 100);

        static final Color[] PLAYER_ACCENTS = {
            new Color(60, 100, 220),
            new Color(200, 50, 50),
            new Color(50, 160, 50),
            new Color(160, 140, 60)
        };

        static final Font FONT_TITLE = new Font("SansSerif", Font.BOLD, 18);
        static final Font FONT_LARGE = new Font("SansSerif", Font.BOLD, 16);
        static final Font FONT_MED = new Font("SansSerif", Font.BOLD, 14);
        static final Font FONT_NORM = new Font("SansSerif", Font.BOLD, 13);
        static final Font FONT_SMALL = new Font("SansSerif", Font.BOLD, 11);
        static final Font FONT_XS = new Font("SansSerif", Font.BOLD, 10);
        static final Font FONT_XS_P = new Font("SansSerif", Font.PLAIN, 10);
        static final Font FONT_XXS = new Font("SansSerif", Font.PLAIN, 9);
        static final Font FONT_XXS_B = new Font("SansSerif", Font.BOLD, 9);
    }


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

        setLayout(new BorderLayout());
        add(buildHUD(), BorderLayout.NORTH);
        add(buildLeftSidebar(), BorderLayout.WEST);
        add(new DisplayPanel(), BorderLayout.CENTER);
        add(buildActionBar(), BorderLayout.SOUTH);

        setupKeyListeners();
        pack();
        setLocationRelativeTo(null);
        setVisible(true);
    }


    private JPanel buildHUD() {
        JPanel hud = new JPanel(null);
        hud.setPreferredSize(new Dimension(900, 48));
        hud.setBackground(Style.BG_DARK);

        JLabel title = makeLabel("CHRONOARENA", Style.FONT_TITLE, Style.TEXT_GOLD);
        title.setBounds(12, 10, 230, 28);
        hud.add(title);

        JLabel timer = makeLabel("TIME LEFT: 02:35", Style.FONT_LARGE, Style.TEXT_WHITE);
        timer.setBounds(330, 10, 220, 28);
        hud.add(timer);

        JLabel scoreLbl = makeLabel("SCORE", Style.FONT_XS, Color.LIGHT_GRAY);
        scoreLbl.setBounds(643, 13, 50, 22);
        hud.add(scoreLbl);

        int[] scores = {120, 90, 75, 60};
        for (int i = 0; i < 4; i++) {
            JLabel s = new JLabel(String.valueOf(scores[i]), SwingConstants.CENTER);
            s.setFont(Style.FONT_NORM);
            s.setForeground(Style.TEXT_WHITE);
            s.setOpaque(true);
            s.setBackground(Style.PLAYER_ACCENTS[i]);
            s.setBounds(690 + i * 52, 10, 46, 28);
            hud.add(s);
        }
        return hud;
    }

    // left side bar
    private JPanel buildLeftSidebar() {
        JPanel sidebar = new JPanel(null);
        sidebar.setPreferredSize(new Dimension(160, 600));
        sidebar.setBackground(Style.BG_PANEL);

        JButton exitBtn = makeStyledButton("EXIT", new Color(180, 50, 50));
        exitBtn.setBounds(38, 10, 80, 30);
        exitBtn.addActionListener(e -> System.exit(0));
        sidebar.add(exitBtn);

        sidebar.add(buildSelfCard());
        buildOtherCards(sidebar);

        joystick = new JoystickPanel( () -> holdUp, () -> holdDown, () -> holdLeft, () -> holdRight);
        joystick.setBounds(10, 430, 140, 140);
        sidebar.add(joystick);

        JLabel hint = new JLabel("<html><center>WASD / ARROWS</center></html>", SwingConstants.CENTER);
        hint.setFont(Style.FONT_XS_P);
        hint.setForeground(Style.TEXT_HINT);
        hint.setBounds(10, 575, 140, 20);
        sidebar.add(hint);

        return sidebar;
    }

    // builds player card
    private JPanel buildSelfCard() {
        JPanel card = new JPanel(null);
        card.setBackground(Style.BG_CARD);
        card.setBorder(BorderFactory.createLineBorder(Style.ACCENT_BLUE, 2));
        card.setBounds(8, 50, 144, 180);

        JLabel youLabel = makeLabel("YOU", Style.FONT_XXS_B, Style.TEXT_MUTED, SwingConstants.CENTER);
        youLabel.setBounds(0, 6, 144, 14);
        card.add(youLabel);

        cardNameLabel = makeLabel("Player " + localPlayerId, Style.FONT_MED, Style.TEXT_WHITE, SwingConstants.CENTER);
        cardNameLabel.setBounds(0, 22, 144, 20);
        card.add(cardNameLabel);

        JLabel hpTitle = makeLabel("HP", Style.FONT_XXS_B, Style.TEXT_MUTED);
        hpTitle.setBounds(10, 50, 30, 14);
        card.add(hpTitle);

        cardHpLabel = makeLabel("100", Style.FONT_XXS_B, Style.ACCENT_GREEN, SwingConstants.RIGHT);
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

        JLabel statusTitle = makeLabel("STATUS", Style.FONT_XXS_B, Style.TEXT_MUTED);
        statusTitle.setBounds(10, 84, 60, 14);
        card.add(statusTitle);

        cardStatusLabel = makeLabel("NORMAL", new Font("SansSerif", Font.BOLD, 11), Style.ACCENT_GREEN, SwingConstants.RIGHT);
        cardStatusLabel.setBounds(60, 84, 74, 14);
        card.add(cardStatusLabel);

        JSeparator divider = new JSeparator();
        divider.setForeground(Style.BORDER_DIM);
        divider.setBounds(10, 106, 124, 2);
        card.add(divider);

        JLabel itemTitle = makeLabel("HOLDING", Style.FONT_XXS_B, Style.TEXT_MUTED);
        itemTitle.setBounds(10, 114, 70, 14);
        card.add(itemTitle);

        cardItemLabel = makeLabel("NONE", new Font("SansSerif", Font.BOLD, 12), Style.TEXT_MUTED, SwingConstants.RIGHT);
        cardItemLabel.setBounds(60, 112, 74, 18);
        card.add(cardItemLabel);

        JLabel shieldBadge = makeLabel("NO SHIELD", Style.FONT_XXS, Style.TEXT_DIMMER, SwingConstants.CENTER);
        shieldBadge.setBounds(10, 140, 58, 14);
        card.add(shieldBadge);

        JLabel weaponBadge = makeLabel("NO GUN", Style.FONT_XXS, Style.TEXT_DIMMER, SwingConstants.CENTER);
        weaponBadge.setBounds(76, 140, 58, 14);
        card.add(weaponBadge);

        return card;
    }

    // builds opponents player cards
    private void buildOtherCards(JPanel sidebar) {
        Color[] accents = {Style.PLAYER_ACCENTS[1], Style.PLAYER_ACCENTS[2], Style.PLAYER_ACCENTS[3]};
        String[] tags = {"P2", "P3", "P4"};

        for (int i = 0; i < 3; i++) {
            int cy = 238 + i * 62;

            JPanel oCard = new JPanel(null);
            oCard.setBackground(Style.BG_CARD);
            oCard.setBorder(BorderFactory.createLineBorder(accents[i], 1));
            oCard.setBounds(8, cy, 144, 54);
            sidebar.add(oCard);

            JLabel pTag = makeLabel(tags[i], Style.FONT_XXS_B, accents[i]);
            pTag.setBounds(6, 4, 20, 12);
            oCard.add(pTag);

            otherNameLabels[i] = makeLabel("---", Style.FONT_SMALL, Style.TEXT_WHITE);
            otherNameLabels[i].setBounds(24, 4, 90, 12);
            oCard.add(otherNameLabels[i]);

            otherScoreLabels[i] = makeLabel("0 pts", Style.FONT_XS, Style.TEXT_GOLD, SwingConstants.RIGHT);
            otherScoreLabels[i].setBounds(100, 4, 38, 12);
            oCard.add(otherScoreLabels[i]);

            otherStatusLabels[i] = makeLabel("NORMAL", Style.FONT_XS_P, Style.ACCENT_GREEN);
            otherStatusLabels[i].setBounds(6, 22, 70, 14);
            oCard.add(otherStatusLabels[i]);

            otherItemLabels[i] = makeLabel("NONE", Style.FONT_XS, Style.TEXT_MUTED, SwingConstants.RIGHT);
            otherItemLabels[i].setBounds(76, 22, 62, 14);
            oCard.add(otherItemLabels[i]);

            JSeparator oDiv = new JSeparator();
            oDiv.setForeground(Style.BORDER_DIM);
            oDiv.setBounds(6, 40, 132, 2);
            oCard.add(oDiv);

            JLabel connDot = makeLabel("● CONNECTED", Style.FONT_XXS, new Color(80, 160, 80));
            connDot.setBounds(6, 44, 132, 10);
            oCard.add(connDot);
        }
    }

    // builds action bar
    private JPanel buildActionBar() {
        JPanel bar = new JPanel(null);
        bar.setPreferredSize(new Dimension(900, 52));
        bar.setBackground(Style.BG_DARK);

        JButton dashBtn = makeStyledButton("DASH", new Color(60, 160, 80));
        dashBtn.setBounds(170, 8, 100, 36);
        dashBtn.addActionListener(e -> sendUDP("ACTION " + localPlayerId + " DASH " + udpSeq++));
        bar.add(dashBtn);

        JButton tagBtn = makeStyledButton("TAG", new Color(200, 140, 30));
        tagBtn.setBounds(280, 8, 100, 36);
        tagBtn.addActionListener(e -> sendUDP("ACTION " + localPlayerId + " TAG " + udpSeq++));
        bar.add(tagBtn);

        buildZonePanels(bar);

        JLabel holdingTitle = makeLabel("HOLDING", Style.FONT_XXS_B, Style.TEXT_MUTED, SwingConstants.CENTER);
        holdingTitle.setBounds(760, 6, 80, 14);
        bar.add(holdingTitle);

        itemHeldLabel = makeLabel("NONE", Style.FONT_NORM, Style.TEXT_MUTED, SwingConstants.CENTER);
        itemHeldLabel.setFont(new Font("SansSerif", Font.BOLD, 13));
        itemHeldLabel.setBounds(755, 20, 90, 26);
        bar.add(itemHeldLabel);

        return bar;
    }

    // builds zone panels
    private void buildZonePanels(JPanel parent) {
        String[] zoneNames = {"ZONE A", "ZONE B", "ZONE C"};
        for (int i = 0; i < 3; i++) {
            int bx = 395 + i * 120;

            JLabel zName = makeLabel(zoneNames[i], Style.FONT_XS, new Color(150, 155, 170), SwingConstants.CENTER);
            zName.setBounds(bx, 3, 110, 14);
            parent.add(zName);

            zoneOwnerLabels[i] = makeLabel("---", Style.FONT_SMALL, Style.TEXT_MUTED, SwingConstants.CENTER);
            zoneOwnerLabels[i].setBounds(bx, 17, 110, 16);
            parent.add(zoneOwnerLabels[i]);

            JPanel barBg = new JPanel(null);
            barBg.setBackground(Style.BG_BAR);
            barBg.setBounds(bx, 36, 110, 8);
            parent.add(barBg);

            zoneProgressBars[i] = new JPanel();
            zoneProgressBars[i].setBackground(new Color(80, 140, 255));
            zoneProgressBars[i].setBounds(0, 0, 0, 8);
            barBg.add(zoneProgressBars[i]);
        }
    }

    // key listeners
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
            if (!holdUp) { holdUp = true; joystick.repaint(); sendUDP("MOVE " + localPlayerId + " 0 -1 " + udpSeq++); }
        }});
        am.put("DOWN_P", new AbstractAction() { public void actionPerformed(ActionEvent e) {
            if (!holdDown) { holdDown = true; joystick.repaint(); sendUDP("MOVE " + localPlayerId + " 0 1 " + udpSeq++); }
        }});
        am.put("LEFT_P", new AbstractAction() { public void actionPerformed(ActionEvent e) {
            if (!holdLeft) { holdLeft = true; joystick.repaint(); sendUDP("MOVE " + localPlayerId + " -1 0 " + udpSeq++); }
        }});
        am.put("RIGHT_P", new AbstractAction() { public void actionPerformed(ActionEvent e) {
            if (!holdRight) { holdRight = true;  joystick.repaint(); sendUDP("MOVE " + localPlayerId + " 1 0 " + udpSeq++); }
        }});

        am.put("UP_R", new AbstractAction() { public void actionPerformed(ActionEvent e) { holdUp = false; joystick.repaint(); }});
        am.put("DOWN_R", new AbstractAction() { public void actionPerformed(ActionEvent e) { holdDown = false; joystick.repaint(); }});
        am.put("LEFT_R", new AbstractAction() { public void actionPerformed(ActionEvent e) { holdLeft = false; joystick.repaint(); }});
        am.put("RIGHT_R", new AbstractAction() { public void actionPerformed(ActionEvent e) { holdRight = false; joystick.repaint(); }});
    }

    // binds and releases
    private void bindKey(InputMap im, int keyCode, String dir) {
        im.put(KeyStroke.getKeyStroke(keyCode, 0, false), dir + "_P");
        im.put(KeyStroke.getKeyStroke(keyCode, 0, true),  dir + "_R");
    }

    // update zone status
    public void updateZone(int zoneIndex, String ownerName, double captureProgress) {
        SwingUtilities.invokeLater(() -> {
            JLabel lbl = zoneOwnerLabels[zoneIndex];
            JPanel bar = zoneProgressBars[zoneIndex];
            boolean owned = ownerName != null && !ownerName.isEmpty();
            lbl.setText(owned ? ownerName : "---");
            lbl.setForeground(owned ? Style.ACCENT_GREEN : Style.TEXT_MUTED);
            bar.setBackground(owned ? new Color(60, 180, 80) : new Color(80, 140, 255));
            bar.setBounds(0, 0, (int)(110 * Math.min(1.0, Math.max(0.0, captureProgress))), 8);
            bar.getParent().repaint();
        });
    }

    public void updateItemHeld(String itemType) {
        SwingUtilities.invokeLater(() -> applyItemStyle(itemHeldLabel, itemType));
    }

    public void updatePlayerCard(String name, int hp, boolean frozen, boolean hasWeapon, boolean hasShield, boolean speedBoost, String itemType) {
        SwingUtilities.invokeLater(() -> {
            cardNameLabel.setText(name);

            int clamped = Math.max(0, Math.min(100, hp));
            cardHpLabel.setText(String.valueOf(clamped));
            cardHpBar.setBounds(0, 0, (int)(124 * (clamped / 100.0)), 8);
            Color hpColor = clamped > 60 ? Style.ACCENT_GREEN: clamped > 30 ? new Color(255, 180, 0) : new Color(220, 60, 60);
            cardHpBar.setBackground(hpColor);
            cardHpLabel.setForeground(hpColor);
            cardHpBar.getParent().repaint();

            applyStatusStyle(cardStatusLabel, frozen, speedBoost);
            applyItemStyle(cardItemLabel, itemType);
        });
    }

    public void updateOtherPlayer(int index, String name, int score, boolean frozen, boolean speedBoost, String itemType) {
        if (index < 0 || index > 2) return;
        SwingUtilities.invokeLater(() -> {
            otherNameLabels[index].setText(name);
            otherScoreLabels[index].setText(score + " pts");
            applyStatusStyle(otherStatusLabels[index], frozen, speedBoost);
            applyItemStyle(otherItemLabels[index], itemType);
        });
    }


    // labels
    private static void applyItemStyle(JLabel label, String itemType) {
        switch (itemType) {
            case "GUN": label.setText("GUN"); label.setForeground(Style.ITEM_GUN); break;
            case "SHIELD": label.setText("SHIELD"); label.setForeground(Style.ITEM_SHIELD); break;
            case "SPEED_BOOST": label.setText("SPEED BOOST");label.setForeground(Style.ITEM_SPEED); break;
            case "ENERGY": label.setText("ENERGY"); label.setForeground(Style.ITEM_ENERGY); break;
            default: label.setText("NONE"); label.setForeground(Style.TEXT_MUTED); break;
        }
    }

    // labels for status effects
    private static void applyStatusStyle(JLabel label, boolean frozen, boolean speedBoost) {
        if (frozen) {
            label.setText("FROZEN"); label.setForeground(Style.STATUS_FROZEN);
        } else if (speedBoost) {
            label.setText("SPEED BOOST");label.setForeground(Style.STATUS_OK);
        } else {
            label.setText("NORMAL"); label.setForeground(Style.STATUS_OK);
        }
    }


    private static JLabel makeLabel(String text, Font font, Color fg) {
        return makeLabel(text, font, fg, SwingConstants.LEFT);
    }

    private static JLabel makeLabel(String text, Font font, Color fg, int align) {
        JLabel lbl = new JLabel(text, align);
        lbl.setFont(font);
        lbl.setForeground(fg);
        return lbl;
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
        btn.setFont(Style.FONT_NORM);
        btn.setForeground(Style.TEXT_WHITE);
        btn.setOpaque(false);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        return btn;
    }

    // network
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