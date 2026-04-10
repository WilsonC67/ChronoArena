import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.imageio.ImageIO;
import javax.swing.*;

/**
 * GamePanel — game loop, renderer, and frame broadcaster.
 *
 * Each tick it:
 *   1. Drains the UDP packet queue and hands actions to GameLogic.
 *   2. Renders the full arena (grid → zones → items → players → overlays)
 *      into a BufferedImage.
 *   3. JPEG-compresses that image and broadcasts it to all connected
 *      ClientHandlers.
 */
public class GamePanel extends JPanel implements Runnable {

    // ── Config ────────────────────────────────────────────────────────────────
    private static final int TILE      = PropertyFileReader.getTileSize();
    private static final int COLS      = PropertyFileReader.getColNum();
    private static final int ROWS      = PropertyFileReader.getRowNum();
    private static final int TICK_RATE = 20;   // ticks per second

    // ── Grid colours ─────────────────────────────────────────────────────────
    private static final Color TILE_A     = new Color(210, 213, 220);
    private static final Color TILE_B     = new Color(195, 198, 207);
    private static final Color GRID_LINE  = new Color(180, 183, 192);

    // ── Zone colours ──────────────────────────────────────────────────────────
    private static final Color COL_CONTESTED  = new Color(230, 150,  20);

    private static final Color COL_CAPTURING  = new Color( 60, 100, 220);
    private static final Color COL_UNCLAIMED  = new Color(160, 140,  60);

    // ── Player colours (index 0-3) ────────────────────────────────────────────
    private static final Color[] PLAYER_COLORS = {
        new Color( 60, 100, 220),   // P1 — blue
        new Color(200,  50,  50),   // P2 — red
        new Color( 50, 160,  50),   // P3 — green
        new Color(200, 160,  30),   // P4 — gold
    };

    // ── Item colours ──────────────────────────────────────────────────────────
    private static final Color COL_ENERGY = new Color(255, 200,  40);
    private static final Color COL_GUN    = new Color(255, 100, 100);
    private static final Color COL_SHIELD = new Color(100, 160, 255);
    private static final Color COL_SPEED  = new Color(100, 220, 100);

    // ── Wiring ────────────────────────────────────────────────────────────────
    private ServerUDPQueue  packetQueue;
    private PlayerListener  playerListener;
    private GameLogic       gameLogic;

    private final List<ClientHandler> clients = new CopyOnWriteArrayList<>();

    // ── Animation state ───────────────────────────────────────────────────────
    private int   tick     = 0;
    private float pulse    = 0f;   // 0-1 sine wave used for frozen shimmer / item bounce

    // ── Constructor ───────────────────────────────────────────────────────────

    public GamePanel() {
        setPreferredSize(new Dimension(COLS * TILE, ROWS * TILE));
        setSize(COLS * TILE, ROWS * TILE);
        setFocusable(true);
    }

    public void setPacketQueue(ServerUDPQueue pq)    { this.packetQueue    = pq; }
    public void setPlayerListener(PlayerListener pl) { this.playerListener = pl; }
    public void setGameLogic(GameLogic gl)           { this.gameLogic      = gl; }

    public void addClient(ClientHandler c)    { clients.add(c); }
    public void removeClient(ClientHandler c) { clients.remove(c); }

    // ── Game loop ─────────────────────────────────────────────────────────────

    @Override
    public void run() {
        long tickMs = 1000 / TICK_RATE;
        while (!Thread.currentThread().isInterrupted()) {
            long start = System.currentTimeMillis();

            tick++;
            pulse = (float) Math.abs(Math.sin(tick * 0.15));

            if (playerListener != null) playerListener.setCurrentTick(tick);

            if (packetQueue != null && gameLogic != null) {
                List<PlayerAction> actions = packetQueue.drainReady(tick);
                // Filter out players not locked into this game session (if game has started)
                java.util.Set<Integer> gamePlayers = gameLogic.getGamePlayers();
                if (!gamePlayers.isEmpty()) {
                    actions.removeIf(a -> !gamePlayers.contains(a.playerId));
                }
                gameLogic.processTick(tick, actions);
            }

            BufferedImage frame = renderToImage();
            broadcastFrame(frame);

            // Broadcast score + timer once per second
            if (tick % TICK_RATE == 0 && gameLogic != null) broadcastScoreUpdate();
            // Broadcast zone progress every tick for smooth animation
            if (gameLogic != null) broadcastZoneUpdate();
            // Broadcast player state once per second
            if (tick % TICK_RATE == 0 && gameLogic != null) broadcastPlayerUpdate();
            // Broadcast death messages when players die
            if (gameLogic != null) broadcastDeathMessages();

            long sleep = tickMs - (System.currentTimeMillis() - start);
            if (sleep > 0) {
                try { Thread.sleep(sleep); } catch (InterruptedException e) { break; }
            }
        }
    }

    // ── Rendering ─────────────────────────────────────────────────────────────

    public BufferedImage renderToImage() {
        BufferedImage img = new BufferedImage(COLS * TILE, ROWS * TILE, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        drawGrid(g);
        if (gameLogic != null) {
            drawZones(g);
            drawItems(g);
            drawPlayers(g);
            drawBeams(g);
        } else {
            drawWaitingScreen(g);
        }

        g.dispose();
        return img;
    }

    // ── Grid ──────────────────────────────────────────────────────────────────

    private void drawGrid(Graphics2D g) {
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                g.setColor((r + c) % 2 == 0 ? TILE_A : TILE_B);
                g.fillRect(c * TILE, r * TILE, TILE, TILE);
            }
        }
        g.setColor(GRID_LINE);
        g.setStroke(new BasicStroke(0.5f));
        for (int c = 0; c <= COLS; c++) g.drawLine(c * TILE, 0, c * TILE, ROWS * TILE);
        for (int r = 0; r <= ROWS; r++) g.drawLine(0, r * TILE, COLS * TILE, r * TILE);
        g.setStroke(new BasicStroke(1f));
    }

    // ── Zones — live state from GameLogic ─────────────────────────────────────

    private void drawZones(Graphics2D g) {
        Zone[] zones = gameLogic.getZones();
        for (Zone zone : zones) {
            Color border, fill;
            boolean dashed = false;

            switch (zone.state) {
                case CONTESTED:
                    border = COL_CONTESTED;
                    fill   = new Color(230, 150, 20, 55);
                    break;
                case CONTROLLED:
                    // Tint to the owning player's colour
                    Color ownerCol = playerColor(zone.ownerId);
                    border = ownerCol.darker();
                    fill   = new Color(ownerCol.getRed(), ownerCol.getGreen(), ownerCol.getBlue(), 50);
                    break;
                case CAPTURING:
                    border = COL_CAPTURING;
                    fill   = new Color(60, 100, 220, 45);
                    break;
                default: // UNCLAIMED
                    border = COL_UNCLAIMED;
                    fill   = new Color(160, 140, 60, 35);
                    dashed = true;
                    break;
            }

            int x = zone.col   * TILE;
            int y = zone.row   * TILE;
            int w = zone.width * TILE;
            int h = zone.height * TILE;

            g.setColor(fill);
            g.fillRect(x, y, w, h);

            if (dashed) {
                g.setColor(border);
                g.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                        10, new float[]{8, 5}, tick * 0.3f));
            } else {
                g.setColor(border);
                g.setStroke(new BasicStroke(3f));
            }
            g.drawRect(x + 1, y + 1, w - 2, h - 2);
            g.setStroke(new BasicStroke(1f));

            // Capture progress bar at the bottom of the zone
            if (zone.state == Zone.State.CAPTURING && zone.captureProgress > 0) {
                int barW = (int)((double) w * zone.captureProgress / Zone.CAPTURE_TICKS);
                g.setColor(new Color(60, 100, 220, 140));
                g.fillRect(x, y + h - 6, barW, 6);
            }

            // Zone label
            String[] lines = buildZoneLabel(zone);
            g.setFont(new Font("SansSerif", Font.BOLD, 12));
            FontMetrics fm = g.getFontMetrics();
            int totalH  = lines.length * (fm.getHeight() + 1);
            int startY  = y + (h - totalH) / 2 + fm.getAscent();
            for (String line : lines) {
                int tw = fm.stringWidth(line);
                g.setColor(new Color(0, 0, 0, 80));
                g.drawString(line, x + (w - tw) / 2 + 1, startY + 1);
                g.setColor(border.darker());
                g.drawString(line, x + (w - tw) / 2, startY);
                startY += fm.getHeight() + 1;
            }
        }
    }

    private String[] buildZoneLabel(Zone zone) {
        String stateLine;
        switch (zone.state) {
            case CONTESTED:  stateLine = "CONTESTED";  break;
            case CONTROLLED: stateLine = "CONTROLLED"; break;
            case CAPTURING:  stateLine = "CAPTURING";  break;
            default:         stateLine = "UNCLAIMED";  break;
        }
        if (zone.state == Zone.State.CONTROLLED && zone.ownerId > 0) {
            return new String[]{ zone.name, "P" + zone.ownerId };
        }
        return new String[]{ zone.name, stateLine };
    }

    // ── Items ─────────────────────────────────────────────────────────────────

    private void drawItems(Graphics2D g) {
        int bounce = (int)(pulse * 4);
        for (Item item : gameLogic.getItems()) {
            if (!item.active) continue;

            int cx = item.x * TILE + TILE / 2;
            int cy = item.y * TILE + TILE / 2 - bounce;
            int r  = TILE / 3;

            Color col = itemColor(item.type);

            // Glow
            g.setColor(new Color(col.getRed(), col.getGreen(), col.getBlue(), 60));
            g.fillOval(cx - r - 4, cy - r - 4, (r + 4) * 2, (r + 4) * 2);

            // Body
            g.setColor(col);
            g.fillOval(cx - r, cy - r, r * 2, r * 2);
            g.setColor(col.darker());
            g.setStroke(new BasicStroke(1.5f));
            g.drawOval(cx - r, cy - r, r * 2, r * 2);
            g.setStroke(new BasicStroke(1f));

            // Letter inside
            g.setColor(Color.WHITE);
            g.setFont(new Font("SansSerif", Font.BOLD, 10));
            FontMetrics fm = g.getFontMetrics();
            String lbl = itemLabel(item.type);
            g.drawString(lbl, cx - fm.stringWidth(lbl) / 2, cy + fm.getAscent() / 2);
        }
    }

    private Color itemColor(Item.Type t) {
        switch (t) {
            case GUN:         return COL_GUN;
            case SHIELD:      return COL_SHIELD;
            case SPEED_BOOST: return COL_SPEED;
            default:          return COL_ENERGY;
        }
    }

    private String itemLabel(Item.Type t) {
        switch (t) {
            case GUN:         return "G";
            case SHIELD:      return "S";
            case SPEED_BOOST: return "⚡";
            default:          return "E";
        }
    }

    // ── Players ───────────────────────────────────────────────────────────────

    private void drawPlayers(Graphics2D g) {
        Map<Integer, Player> players = gameLogic.getPlayers();
        if (players.isEmpty()) {
            drawWaitingScreen(g);
            return;
        }

        java.util.Set<Integer> gamePlayers = gameLogic.getGamePlayers();
        for (Player p : players.values()) {
            if (!p.connected || p.killed) continue;
            // Once game is locked in, only render players in the session
            if (!gamePlayers.isEmpty() && !gamePlayers.contains(p.id)) continue;

            int cx = p.x * TILE + TILE / 2;
            int cy = p.y * TILE + TILE / 2;
            int r  = TILE / 2 - 4;

            Color col = playerColor(p.id);

            // Frozen shimmer
            if (p.frozen) {
                float alpha = 0.3f + 0.4f * pulse;
                g.setColor(new Color(100, 180, 255, (int)(alpha * 255)));
                g.fillOval(cx - r - 5, cy - r - 5, (r + 5) * 2, (r + 5) * 2);
            }

            // Speed boost aura
            if (p.speedBoost) {
                g.setColor(new Color(100, 220, 100, 80));
                g.fillOval(cx - r - 6, cy - r - 6, (r + 6) * 2, (r + 6) * 2);
            }

            // Shadow
            g.setColor(new Color(0, 0, 0, 60));
            g.fillOval(cx - r + 2, cy - r + 2, r * 2, r * 2);

            // Body
            g.setColor(col);
            g.fillOval(cx - r, cy - r, r * 2, r * 2);

            // Rim
            g.setColor(col.brighter());
            g.setStroke(new BasicStroke(2f));
            g.drawOval(cx - r, cy - r, r * 2, r * 2);
            g.setStroke(new BasicStroke(1f));

            // Player number
            g.setColor(Color.WHITE);
            g.setFont(new Font("SansSerif", Font.BOLD, 13));
            FontMetrics fm = g.getFontMetrics();
            String idStr = String.valueOf(p.id);
            g.drawString(idStr, cx - fm.stringWidth(idStr) / 2, cy + fm.getAscent() / 2 - 1);

            // Item badge (top-right of circle)
            drawItemBadge(g, p, cx + r - 6, cy - r + 2);

            // Name tag above player
            drawNameTag(g, p, cx, cy - r - 4);
        }
    }

    private void drawItemBadge(Graphics2D g, Player p, int bx, int by) {
        Color col = null;
        String lbl = null;
        if (p.hasWeapon)  { col = COL_GUN;    lbl = "G"; }
        else if (p.hasShield) { col = COL_SHIELD; lbl = "S"; }
        if (col == null) return;

        g.setColor(col);
        g.fillOval(bx - 7, by - 7, 14, 14);
        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.BOLD, 8));
        FontMetrics fm = g.getFontMetrics();
        g.drawString(lbl, bx - fm.stringWidth(lbl) / 2, by + fm.getAscent() / 2 - 1);
    }

    private void drawNameTag(Graphics2D g, Player p, int cx, int topY) {
        String name  = p.name != null ? p.name : ("P" + p.id);
        String score = p.score + "pt";

        g.setFont(new Font("SansSerif", Font.BOLD, 9));
        FontMetrics fm  = g.getFontMetrics();
        int nameW = fm.stringWidth(name);
        int tagW  = Math.max(nameW, fm.stringWidth(score)) + 8;
        int tagH  = fm.getHeight() * 2 + 4;
        int tx    = cx - tagW / 2;
        int ty    = topY - tagH;

        // Background pill
        g.setColor(new Color(20, 20, 30, 180));
        g.fillRoundRect(tx, ty, tagW, tagH, 6, 6);

        // Name
        g.setColor(playerColor(p.id).brighter());
        g.drawString(name,  cx - nameW / 2,             ty + fm.getAscent() + 1);

        // Score
        g.setColor(new Color(255, 200, 40));
        String scoreStr = score;
        g.drawString(scoreStr, cx - fm.stringWidth(scoreStr) / 2, ty + fm.getAscent() * 2 + 2);
    }

    // ── Beams ─────────────────────────────────────────────────────────────────

    private void drawBeams(Graphics2D g) {
        List<GameLogic.Beam> beams = gameLogic.getBeams();
        g.setColor(Color.RED);
        g.setStroke(new BasicStroke(3f));
        for (GameLogic.Beam beam : beams) {
            int x1 = beam.x * TILE + TILE / 2;
            int y1 = beam.y * TILE + TILE / 2;
            int x2 = (beam.x + beam.dx * beam.length) * TILE + TILE / 2;
            int y2 = (beam.y + beam.dy * beam.length) * TILE + TILE / 2;
            g.drawLine(x1, y1, x2, y2);
        }
        g.setStroke(new BasicStroke(1f));
    }

    // ── HUD overlay in top-left corner of the arena frame ────────────────────

    private void drawHudOverlay(Graphics2D g) throws Exception {
        if (gameLogic == null) return;
        int secondsLeft = (int)(gameLogic.getTimeRemainingMs() / 1000);
        int connected   = (int) gameLogic.getPlayers().values().stream()
                .filter(p -> p.connected && !p.killed).count();

        String timeStr = String.format("%02d:%02d", secondsLeft / 60, secondsLeft % 60);
        String connStr = connected + "/4 players";

        g.setColor(new Color(0, 0, 0, 120));
        g.fillRoundRect(6, 6, 130, 40, 8, 8);

        g.setFont(new Font("SansSerif", Font.BOLD, 15));
        g.setColor(secondsLeft < 30 ? new Color(220, 60, 60) : Color.WHITE);
        g.drawString(timeStr, 12, 24);

        g.setFont(new Font("SansSerif", Font.PLAIN, 10));
        g.setColor(new Color(180, 185, 200));
        g.drawString(connStr, 12, 40);
    }

    // ── Waiting screen (no players yet) ──────────────────────────────────────

    private void drawWaitingScreen(Graphics2D g) {
        g.setColor(new Color(0, 0, 0, 130));
        int pw = COLS * TILE, ph = ROWS * TILE;
        g.fillRect(0, 0, pw, ph);

        g.setFont(new Font("SansSerif", Font.BOLD, 28));
        g.setColor(new Color(255, 200, 40));
        String title = "Waiting for players...";
        FontMetrics fm = g.getFontMetrics();
        g.drawString(title, (pw - fm.stringWidth(title)) / 2, ph / 2 - 10);

        g.setFont(new Font("SansSerif", Font.PLAIN, 14));
        g.setColor(new Color(150, 155, 170));
        String sub = "Up to 4 players can join";
        fm = g.getFontMetrics();
        g.drawString(sub, (pw - fm.stringWidth(sub)) / 2, ph / 2 + 20);
    }

    // ── Broadcast ─────────────────────────────────────────────────────────────

    private void broadcastScoreUpdate() {
        int secondsLeft = (int)(gameLogic.getTimeRemainingMs() / 1000);
        // Build "SCORE_UPDATE,secondsLeft,s1,s2,s3,s4,zState,zOwner,zProgress,..."
        StringBuilder sb = new StringBuilder("SCORE_UPDATE,").append(secondsLeft);
        java.util.Map<Integer, Player> players = gameLogic.getPlayers();
        for (int i = 1; i <= 4; i++) {
            Player p = players.get(i);
            sb.append(",").append(p != null ? p.score : 0);
        }
        // Append zone data: state,ownerId,captureProgress for each zone
        for (Zone zone : gameLogic.getZones()) {
            sb.append(",").append(zone.state.name());
            sb.append(",").append(zone.ownerId);
            sb.append(",").append(zone.captureProgress);
        }
        for (ClientHandler client : clients) client.sendTextMessage(sb.toString());
    }

    private void broadcastPlayerUpdate() {
        // Format: PLAYER_UPDATE,id,score,hp,frozen,hasWeapon,hasShield,speedBoost per player
        java.util.Map<Integer, Player> players = gameLogic.getPlayers();
        java.util.Set<Integer> gamePlayers = gameLogic.getGamePlayers();
        StringBuilder sb = new StringBuilder("PLAYER_UPDATE");
        for (int i = 1; i <= 4; i++) {
            Player p = players.get(i);
            // Only include players locked into this game session
            if (p != null && p.connected && !p.killed
                    && (gamePlayers.isEmpty() || gamePlayers.contains(p.id))) {
                sb.append(",").append(p.id);
                sb.append(",").append(p.score);
                sb.append(",").append(p.hp);
                sb.append(",").append(p.frozen   ? 1 : 0);
                sb.append(",").append(p.hasWeapon ? 1 : 0);
                sb.append(",").append(p.hasShield ? 1 : 0);
                sb.append(",").append(p.speedBoost? 1 : 0);
            }
        }
        for (ClientHandler client : clients) client.sendTextMessage(sb.toString());
    }

    private void broadcastZoneUpdate() {
        StringBuilder sb = new StringBuilder("ZONE_UPDATE");
        for (Zone zone : gameLogic.getZones()) {
            sb.append(",").append(zone.state.name());
            sb.append(",").append(zone.ownerId);
            sb.append(",").append(zone.captureProgress);
        }
        for (ClientHandler client : clients) client.sendTextMessage(sb.toString());
    }

    private void broadcastDeathMessages() {
        java.util.Map<Integer, Player> players = gameLogic.getPlayers();
        for (Player p : players.values()) {
            // Broadcast respawn countdown for players currently respawning
            if (p.respawnTicksLeft > 0) {
                int secondsLeft = (p.respawnTicksLeft + 19) / 20;  // Round up to whole seconds
                String respawnMsg = "RESPAWN_COUNTDOWN," + p.id + "," + secondsLeft;
                for (ClientHandler client : clients) client.sendTextMessage(respawnMsg);
            }
        }
    }

    private void broadcastFrame(BufferedImage frame) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(frame, "jpg", baos);
            byte[] data = baos.toByteArray();
            for (ClientHandler client : clients) client.sendFrame(data);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ── Swing on-screen paint (local preview if shown as a window) ────────────

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        if (gameLogic != null) {
            Graphics2D g = (Graphics2D) g0;
            g.drawImage(renderToImage(), 0, 0, null);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Color playerColor(int playerId) {
        if (playerId < 1 || playerId > 4) return Color.LIGHT_GRAY;
        return PLAYER_COLORS[playerId - 1];
    }
}