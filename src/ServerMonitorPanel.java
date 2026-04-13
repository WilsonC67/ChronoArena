import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Map;
import javax.swing.*;

/**
 * ServerMonitorPanel — the server-side admin GUI.
 *
 * Launched by GameServer on the EDT after all components are wired.
 * Reads game state directly from GameLogic and renders frames directly
 * from GamePanel — no TCP, no JPEG round-trip.
 *
 * Layout (BorderLayout):
 *   NORTH  — status strip  (server tag, round timer, game state)
 *   WEST   — player cards  (one per slot 1-4, each with an ✕ kill button)
 *   CENTER — live arena    (GamePanel.renderToImage() painted each tick)
 *   SOUTH  — zone bar      (mirrors ActionbarPanel, read-only)
 */
public class ServerMonitorPanel extends JFrame {

    // ── Palette (slightly different accent from client to feel "admin") ────────
    private static final Color BG_WIN    = new Color(18, 18, 26);
    private static final Color BG_STRIP  = new Color(22, 22, 32);
    private static final Color BG_SIDE   = new Color(24, 26, 36);
    private static final Color BG_CARD   = new Color(32, 34, 48);
    private static final Color BG_BAR    = new Color(40, 42, 56);
    private static final Color BORDER    = new Color(50, 52, 70);
    private static final Color TEXT_HEAD = new Color(200, 205, 220);
    private static final Color TEXT_DIM  = new Color(110, 115, 135);
    private static final Color GOLD      = new Color(255, 200, 40);
    private static final Color RED_KILL  = new Color(200, 50, 50);
    private static final Color GREEN     = new Color(80, 200, 100);
    private static final Color INACTIVE  = new Color(80, 85, 105);

    private static final Color[] PLAYER_ACCENTS = {
        new Color(80,  120, 255),
        new Color(220,  60,  60),
        new Color(60,  200,  60),
        new Color(200, 170,  50),
    };

    // ── Wiring ────────────────────────────────────────────────────────────────
    private final GameLogic  gameLogic;
    private final GamePanel  gamePanel;

    // ── Status strip ──────────────────────────────────────────────────────────
    private final JLabel statusGameLabel;   // "LOBBY" / "ROUND ACTIVE" / "ROUND OVER"
    private final JLabel statusTimerLabel;  // "TIME LEFT  01:47"
    private final JLabel statusPlayersLabel;// "3 / 4 CONNECTED"

    // ── Player cards (4 slots) ────────────────────────────────────────────────
    private final JLabel[]  cardNameLabels   = new JLabel[4];
    private final JLabel[]  cardStateLabels  = new JLabel[4];  // CONNECTED / DEAD / WAITING
    private final JLabel[]  cardScoreLabels  = new JLabel[4];
    private final JLabel[]  cardHpLabels     = new JLabel[4];
    private final JPanel[]  cardHpBars       = new JPanel[4];
    private final JButton[] killButtons      = new JButton[4];
    private final JPanel[]  cards            = new JPanel[4];

    // ── Arena view ────────────────────────────────────────────────────────────
    private final ArenaView arenaView;

    // ── Zone bar (bottom) ─────────────────────────────────────────────────────
    private static final int ZONE_W   = 120;
    private static final int ZONE_GAP = 10;
    private final JLabel[] zoneNameLabels  = new JLabel[3];
    private final JLabel[] zoneStateLabels = new JLabel[3];
    private final JPanel[] zoneBarBgs      = new JPanel[3];
    private final JPanel[] zoneBarFills    = new JPanel[3];

    // ── Refresh timer ─────────────────────────────────────────────────────────
    private static final int REFRESH_MS = 50; // 20 fps

    // ─────────────────────────────────────────────────────────────────────────

    public ServerMonitorPanel(GameLogic gameLogic, GamePanel gamePanel) {
        super("ChronoArena — Server Monitor");
        this.gameLogic = gameLogic;
        this.gamePanel = gamePanel;

        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                int choice = JOptionPane.showConfirmDialog(
                        ServerMonitorPanel.this,
                        "Shut down the ChronoArena server?\nAll connected players will be disconnected.",
                        "Confirm Server Shutdown",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE);
                if (choice == JOptionPane.YES_OPTION) {
                    System.out.println("[ServerMonitor] Shutdown confirmed — exiting.");
                    System.exit(0);
                }
            }
        });
        getContentPane().setBackground(BG_WIN);
        setLayout(new BorderLayout(0, 0));

        // ── Status strip (NORTH) ──────────────────────────────────────────────
        JPanel strip = buildStatusStrip();
        statusGameLabel   = (JLabel) strip.getClientProperty("gameLabel");
        statusTimerLabel  = (JLabel) strip.getClientProperty("timerLabel");
        statusPlayersLabel= (JLabel) strip.getClientProperty("playersLabel");
        add(strip, BorderLayout.NORTH);

        // ── Player sidebar (WEST) ─────────────────────────────────────────────
        add(buildSidebar(), BorderLayout.WEST);

        // ── Arena view (CENTER) ───────────────────────────────────────────────
        arenaView = new ArenaView();
        add(arenaView, BorderLayout.CENTER);

        // ── Zone bar (SOUTH) ──────────────────────────────────────────────────
        add(buildZoneBar(), BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(null);
        setVisible(true);

        // ── Poll timer ────────────────────────────────────────────────────────
        new Timer(REFRESH_MS, e -> refresh()).start();
    }

    // ── Status strip ──────────────────────────────────────────────────────────

    private JPanel buildStatusStrip() {
        JPanel p = new JPanel(null);
        p.setBackground(BG_STRIP);
        p.setPreferredSize(new Dimension(0, 46));
        p.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER));

        // "SERVER MONITOR" badge on the left
        JLabel badge = new JLabel("⬡  SERVER MONITOR");
        badge.setFont(new Font("SansSerif", Font.BOLD, 13));
        badge.setForeground(new Color(120, 180, 255));
        badge.setBounds(14, 14, 200, 18);
        p.add(badge);

        // Game state centre-left
        JLabel gameLabel = new JLabel("LOBBY");
        gameLabel.setFont(new Font("SansSerif", Font.BOLD, 15));
        gameLabel.setForeground(GOLD);
        gameLabel.setBounds(240, 14, 180, 18);
        p.add(gameLabel);
        p.putClientProperty("gameLabel", gameLabel);

        // Timer
        JLabel timerLabel = new JLabel("TIME LEFT  --:--");
        timerLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
        timerLabel.setForeground(TEXT_HEAD);
        timerLabel.setBounds(440, 14, 200, 18);
        p.add(timerLabel);
        p.putClientProperty("timerLabel", timerLabel);

        // Player count (right)
        JLabel playersLabel = new JLabel("0 / 4 CONNECTED");
        playersLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
        playersLabel.setForeground(TEXT_DIM);
        playersLabel.setBounds(660, 14, 180, 18);
        p.add(playersLabel);
        p.putClientProperty("playersLabel", playersLabel);

        return p;
    }

    // ── Sidebar ───────────────────────────────────────────────────────────────

    private JPanel buildSidebar() {
        JPanel side = new JPanel(null);
        side.setBackground(BG_SIDE);
        side.setPreferredSize(new Dimension(172, 0));
        side.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, BORDER));

        JLabel heading = new JLabel("PLAYERS", SwingConstants.CENTER);
        heading.setFont(new Font("SansSerif", Font.BOLD, 10));
        heading.setForeground(TEXT_DIM);
        heading.setBounds(0, 10, 172, 14);
        side.add(heading);

        for (int i = 0; i < 4; i++) {
            JPanel card = buildPlayerCard(i);
            card.setBounds(8, 30 + i * 110, 156, 100);
            side.add(card);
            cards[i] = card;
        }

        return side;
    }

    private JPanel buildPlayerCard(int slot) {
        int pid = slot + 1;
        Color accent = PLAYER_ACCENTS[slot];

        JPanel card = new JPanel(null);
        card.setBackground(BG_CARD);
        card.setBorder(BorderFactory.createLineBorder(BORDER, 1));

        // Player tag top-left
        JLabel tag = new JLabel("P" + pid);
        tag.setFont(new Font("SansSerif", Font.BOLD, 11));
        tag.setForeground(accent);
        tag.setBounds(8, 6, 28, 14);
        card.add(tag);

        // Player name
        cardNameLabels[slot] = new JLabel("Player " + pid);
        cardNameLabels[slot].setFont(new Font("SansSerif", Font.BOLD, 12));
        cardNameLabels[slot].setForeground(TEXT_HEAD);
        cardNameLabels[slot].setBounds(34, 6, 82, 14);
        card.add(cardNameLabels[slot]);

        // Kill button — top-right ✕
        killButtons[slot] = buildKillButton(pid);
        killButtons[slot].setBounds(122, 4, 26, 18);
        card.add(killButtons[slot]);

        // State label (CONNECTED / DEAD / WAITING)
        cardStateLabels[slot] = new JLabel("WAITING");
        cardStateLabels[slot].setFont(new Font("SansSerif", Font.BOLD, 9));
        cardStateLabels[slot].setForeground(INACTIVE);
        cardStateLabels[slot].setBounds(8, 22, 140, 12);
        card.add(cardStateLabels[slot]);

        // Score
        cardScoreLabels[slot] = new JLabel("0 pts", SwingConstants.RIGHT);
        cardScoreLabels[slot].setFont(new Font("SansSerif", Font.BOLD, 10));
        cardScoreLabels[slot].setForeground(GOLD);
        cardScoreLabels[slot].setBounds(90, 22, 58, 12);
        card.add(cardScoreLabels[slot]);

        // HP label
        JLabel hpLbl = new JLabel("HP");
        hpLbl.setFont(new Font("SansSerif", Font.BOLD, 9));
        hpLbl.setForeground(TEXT_DIM);
        hpLbl.setBounds(8, 38, 20, 10);
        card.add(hpLbl);

        cardHpLabels[slot] = new JLabel("---", SwingConstants.RIGHT);
        cardHpLabels[slot].setFont(new Font("SansSerif", Font.BOLD, 9));
        cardHpLabels[slot].setForeground(GREEN);
        cardHpLabels[slot].setBounds(110, 38, 38, 10);
        card.add(cardHpLabels[slot]);

        // HP bar background
        JPanel hpBarBg = new JPanel(null);
        hpBarBg.setBackground(BG_BAR);
        hpBarBg.setBounds(8, 51, 140, 6);
        card.add(hpBarBg);

        cardHpBars[slot] = new JPanel();
        cardHpBars[slot].setBackground(GREEN);
        cardHpBars[slot].setBounds(0, 0, 0, 6);
        hpBarBg.add(cardHpBars[slot]);

        // Thin accent line at bottom of card
        JPanel accentLine = new JPanel();
        accentLine.setBackground(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 80));
        accentLine.setBounds(0, 93, 156, 3);
        card.add(accentLine);

        return card;
    }

    private JButton buildKillButton(int pid) {
        JButton btn = new JButton("✕") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getModel().isPressed()
                        ? RED_KILL.darker()
                        : getModel().isRollover()
                        ? RED_KILL
                        : new Color(80, 30, 30));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 4, 4);
                super.paintComponent(g);
            }
        };
        btn.setFont(new Font("SansSerif", Font.BOLD, 10));
        btn.setForeground(new Color(255, 120, 120));
        btn.setOpaque(false);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setToolTipText("Kill Player " + pid);
        btn.addActionListener(e -> confirmAndKill(pid));
        return btn;
    }

    // ── Arena view ────────────────────────────────────────────────────────────

    /** Thin panel that just paints the latest rendered frame from GamePanel. */
    private class ArenaView extends JPanel {
        ArenaView() {
            int tile = PropertyFileReader.getTileSize();
            int cols = PropertyFileReader.getColNum();
            int rows = PropertyFileReader.getRowNum();
            setPreferredSize(new Dimension(cols * tile, rows * tile));
            setBackground(Color.BLACK);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            BufferedImage frame = gamePanel.renderToImage();
            if (frame != null) {
                g.drawImage(frame, 0, 0, getWidth(), getHeight(), null);
            }
        }
    }

    // ── Zone bar ──────────────────────────────────────────────────────────────

    private JPanel buildZoneBar() {
        JPanel bar = new JPanel(null) {
            @Override
            public Dimension getPreferredSize() { return new Dimension(0, 52); }
        };
        bar.setBackground(Style.BG_DARK);
        bar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER));

        String[] names = {"ZONE A", "ZONE B", "ZONE C"};
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            int zx = 50 + i * (ZONE_W + ZONE_GAP);

            zoneNameLabels[i] = new JLabel(names[i], SwingConstants.CENTER);
            zoneNameLabels[i].setFont(Style.FONT_XS);
            zoneNameLabels[i].setForeground(new Color(150, 155, 170));
            zoneNameLabels[i].setBounds(zx, 3, ZONE_W, 14);
            bar.add(zoneNameLabels[i]);

            zoneStateLabels[i] = new JLabel("---", SwingConstants.CENTER);
            zoneStateLabels[i].setFont(Style.FONT_SMALL);
            zoneStateLabels[i].setForeground(Style.TEXT_MUTED);
            zoneStateLabels[i].setBounds(zx, 17, ZONE_W, 16);
            bar.add(zoneStateLabels[i]);

            JPanel bg = new JPanel(null);
            bg.setBackground(Style.BG_BAR);
            bg.setBounds(zx, 36, ZONE_W, 8);
            bar.add(bg);
            zoneBarBgs[i] = bg;

            zoneBarFills[i] = new JPanel();
            zoneBarFills[i].setBackground(new Color(80, 140, 255));
            zoneBarFills[i].setBounds(0, 0, 0, 8);
            bg.add(zoneBarFills[i]);
        }

        // "ADMIN VIEW" watermark on the right
        JLabel adminTag = new JLabel("ADMIN VIEW", SwingConstants.RIGHT);
        adminTag.setFont(new Font("SansSerif", Font.BOLD, 9));
        adminTag.setForeground(new Color(70, 75, 95));
        // Position is resolved in doLayout equivalent — use component listener or just anchor far right
        adminTag.setBounds(700, 18, 110, 14);
        bar.add(adminTag);

        return bar;
    }

    // ── Kill confirmation ─────────────────────────────────────────────────────

    private void confirmAndKill(int pid) {
        Player p = gameLogic.getPlayers().get(pid);
        if (p == null || !p.connected) {
            JOptionPane.showMessageDialog(this,
                    "Player " + pid + " is not currently connected.",
                    "Kill Switch", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        int choice = JOptionPane.showConfirmDialog(this,
                "Force-disconnect Player " + pid + " (" + p.name + ")?\n"
              + "This will remove them from the game immediately.",
                "Confirm Kill — Player " + pid,
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);

        if (choice == JOptionPane.YES_OPTION) {
            gameLogic.killPlayer(pid);
            System.out.printf("[ServerMonitor] Kill switch fired for Player %d.%n", pid);
        }
    }

    // ── Refresh ───────────────────────────────────────────────────────────────

    private void refresh() {
        refreshStatusStrip();
        refreshPlayerCards();
        refreshZoneBar();
        arenaView.repaint();
    }

    private void refreshStatusStrip() {
        boolean active  = gameLogic.isActive();
        boolean started = gameLogic.isRoundStarted();

        // Game state label
        if (!active && !started) {
            statusGameLabel.setText("⬤  LOBBY");
            statusGameLabel.setForeground(new Color(100, 160, 255));
        } else if (active) {
            statusGameLabel.setText("⬤  ROUND ACTIVE");
            statusGameLabel.setForeground(GREEN);
        } else {
            statusGameLabel.setText("⬤  ROUND OVER");
            statusGameLabel.setForeground(new Color(220, 100, 60));
        }

        // Round timer
        long msLeft = gameLogic.getTimeRemainingMs();
        int  secLeft = (int)(msLeft / 1000);
        int  m = secLeft / 60, s = secLeft % 60;
        statusTimerLabel.setText(String.format("TIME LEFT  %02d:%02d", m, s));
        statusTimerLabel.setForeground(secLeft < 30 && active
                ? new Color(220, 60, 60) : TEXT_HEAD);

        // Connected player count
        long connected = gameLogic.getPlayers().values().stream()
                .filter(p -> p.connected).count();
        statusPlayersLabel.setText(connected + " / 4 CONNECTED");
        statusPlayersLabel.setForeground(connected > 0 ? TEXT_DIM : INACTIVE);
    }

    private void refreshPlayerCards() {
        Map<Integer, Player> players = gameLogic.getPlayers();

        for (int slot = 0; slot < 4; slot++) {
            int pid = slot + 1;
            Player p = players.get(pid);

            if (p == null || !p.connected) {
                // Slot empty
                cardNameLabels[slot].setText("Player " + pid);
                cardNameLabels[slot].setForeground(INACTIVE);
                cardStateLabels[slot].setText("WAITING");
                cardStateLabels[slot].setForeground(INACTIVE);
                cardScoreLabels[slot].setText("---");
                cardScoreLabels[slot].setForeground(INACTIVE);
                cardHpLabels[slot].setText("---");
                cardHpLabels[slot].setForeground(INACTIVE);
                cardHpBars[slot].setBounds(0, 0, 0, 6);
                cardHpBars[slot].getParent().repaint();
                killButtons[slot].setEnabled(false);
                cards[slot].setBorder(BorderFactory.createLineBorder(BORDER, 1));
                continue;
            }

            // Slot occupied
            cardNameLabels[slot].setText(p.name != null ? p.name : "Player " + pid);
            cardNameLabels[slot].setForeground(TEXT_HEAD);
            killButtons[slot].setEnabled(true);
            cards[slot].setBorder(BorderFactory.createLineBorder(
                    new Color(PLAYER_ACCENTS[slot].getRed(),
                              PLAYER_ACCENTS[slot].getGreen(),
                              PLAYER_ACCENTS[slot].getBlue(), 160), 1));

            // State
            if (p.killed || p.respawnTicksLeft > 0) {
                cardStateLabels[slot].setText(p.respawnTicksLeft > 0
                        ? "RESPAWNING (" + ((p.respawnTicksLeft + 19) / 20) + "s)" : "DEAD");
                cardStateLabels[slot].setForeground(new Color(220, 80, 80));
            } else if (p.frozen) {
                cardStateLabels[slot].setText("FROZEN");
                cardStateLabels[slot].setForeground(new Color(100, 180, 255));
            } else if (p.speedBoost) {
                cardStateLabels[slot].setText("SPEED BOOST");
                cardStateLabels[slot].setForeground(new Color(100, 220, 100));
            } else {
                cardStateLabels[slot].setText("CONNECTED");
                cardStateLabels[slot].setForeground(GREEN);
            }

            // Score
            cardScoreLabels[slot].setText(p.score + " pts");
            cardScoreLabels[slot].setForeground(GOLD);

            // HP bar
            int hp = Math.max(0, Math.min(100, p.hp));
            cardHpLabels[slot].setText(String.valueOf(hp));
            Color hpCol = hp > 60 ? GREEN : hp > 30 ? new Color(255, 180, 0) : new Color(220, 60, 60);
            cardHpLabels[slot].setForeground(hpCol);
            cardHpBars[slot].setBackground(hpCol);
            cardHpBars[slot].setBounds(0, 0, (int)(140 * (hp / 100.0)), 6);
            cardHpBars[slot].getParent().repaint();
        }
    }

    private void refreshZoneBar() {
        Zone[] zones = gameLogic.getZones();
        for (int i = 0; i < 3 && i < zones.length; i++) {
            Zone z = zones[i];
            zoneNameLabels[i].setText(z.name);

            String stateText;
            Color  stateCol;
            Color  fillCol;
            double fillPct;

            switch (z.state) {
                case CONTROLLED:
                    stateText = z.ownerId > 0 ? "P" + z.ownerId + " ✔" : "CONTROLLED";
                    stateCol  = GREEN;
                    fillCol   = new Color(60, 180, 80);
                    fillPct   = 1.0;
                    break;
                case CAPTURING:
                    stateText = z.capturingId > 0 ? "P" + z.capturingId + " capturing…" : "CAPTURING";
                    stateCol  = new Color(100, 160, 255);
                    fillCol   = new Color(80, 140, 255);
                    fillPct   = Math.min(1.0, (double) z.captureProgress / Zone.CAPTURE_TICKS);
                    break;
                case CONTESTED:
                    stateText = "CONTESTED";
                    stateCol  = new Color(230, 150, 20);
                    fillCol   = new Color(230, 150, 20);
                    fillPct   = 1.0;
                    break;
                default: // UNCLAIMED
                    stateText = "---";
                    stateCol  = Style.TEXT_MUTED;
                    fillCol   = new Color(80, 140, 255);
                    fillPct   = 0.0;
                    break;
            }

            zoneStateLabels[i].setText(stateText);
            zoneStateLabels[i].setForeground(stateCol);
            zoneBarFills[i].setBackground(fillCol);
            zoneBarFills[i].setBounds(0, 0, (int)(ZONE_W * fillPct), 8);
            zoneBarBgs[i].repaint();
        }
    }
}