import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;

public class GamePanel extends JPanel implements Runnable {

    // general grid layout (grid, color, etc)
    private static final int TILE = PropertyFileReader.getTileSize();
    private static final int COLS = PropertyFileReader.getColNum();
    private static final int ROWS = PropertyFileReader.getRowNum();

    private static final Color TILE_A = new Color(210, 213, 220);
    private static final Color TILE_B = new Color(195, 198, 207);
    private static final Color GRID_LINE = new Color(180, 183, 192);

    private static final Color COL_CONTESTED = new Color(230, 150, 20);
    private static final Color COL_CONTROLLED = new Color(40, 160, 60);
    private static final Color COL_UNCLAIMED = new Color(160, 140, 60);

    private static final int TICK_RATE = 20;

    // animation states
    private int tick = 0;
    private float tagAlpha = 1f;
    private boolean showTag = true;

    public GamePanel() {
        setPreferredSize(new Dimension(COLS * TILE, ROWS * TILE));
        setSize(COLS * TILE, ROWS * TILE);
        setFocusable(true);
    }

    // list of clients to broadcast frames to
    private final List<ClientHandler> clients = new CopyOnWriteArrayList<>();

    // register/remove clients
    public void addClient(ClientHandler client) { clients.add(client); }
    public void removeClient(ClientHandler client) { clients.remove(client); }

    // game loop thread
    @Override
    public void run() {
        long tickDuration = 1000 / TICK_RATE;
        while (!Thread.currentThread().isInterrupted()) {
            long start = System.currentTimeMillis();

            tick++;
            if (showTag) tagAlpha = 0.6f + 0.4f * (float) Math.abs(Math.sin(tick * 0.1));

            // render and broadcast
            BufferedImage frame = renderToImage();
            broadcastFrame(frame);

            long elapsed = System.currentTimeMillis() - start;
            long sleep = tickDuration - elapsed;
            if (sleep > 0) {
                try { Thread.sleep(sleep); }
                catch (InterruptedException e) { break; }
            }
        }
    }

    // renders the panel to a BufferedImage without needing it displayed on screen
    public BufferedImage renderToImage() {
        BufferedImage image = new BufferedImage(COLS * TILE, ROWS * TILE, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        drawGrid(g);
        drawZones(g);
        if (showTag) drawTagPopup(g);
        g.dispose();
        return image;
    }

    //encodes and sends to all clients
    private void broadcastFrame(BufferedImage frame) {
        try {
            ByteArrayOutputStream arrayOutputStream = new ByteArrayOutputStream();
            ImageIO.write(frame, "jpg", arrayOutputStream);
            byte[] data = arrayOutputStream.toByteArray();
            for (ClientHandler client : clients) {
                client.sendFrame(data);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        drawGrid(g);
        drawZones(g);
        if (showTag) drawTagPopup(g);
    }

    // grid development
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
    }

    // zone development 
    private void drawZones(Graphics2D g) {
        drawZone(g, 2, 5, 4, 3, "ZONE A\nCONTESTED", COL_CONTESTED, new Color(255, 200, 80, 55), false);
        drawZone(g, 7, 1, 4, 3, "ZONE B\nCONTROLLED", COL_CONTROLLED, new Color(60, 200, 80, 50), false);
        drawZone(g, 9, 7, 4, 3, "ZONE C\nUNCLAIMED", COL_UNCLAIMED, new Color(180, 160, 60, 35), true);
    }

    private void drawZone(Graphics2D g, int col, int row, int wTiles, int hTiles, String label, Color border, Color fill, boolean dashed) {
        int x = col * TILE, y = row * TILE;
        int w = wTiles * TILE, h = hTiles * TILE;
        g.setColor(fill);
        g.fillRect(x, y, w, h);
        if (dashed) {
            g.setColor(border);
            g.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[]{8, 5}, tick * 0.3f));
        } else {
            g.setColor(border);
            g.setStroke(new BasicStroke(3f));
        }
        g.drawRect(x + 1, y + 1, w - 2, h - 2);
        g.setStroke(new BasicStroke(1));

        String[] lines = label.split("\n");
        g.setFont(new Font("SansSerif", Font.BOLD, 13));
        FontMetrics fm = g.getFontMetrics();
        int totalH = lines.length * (fm.getHeight() + 2);
        int startY = y + (h - totalH) / 2 + fm.getAscent();

        for (String line : lines) {
            int tw = fm.stringWidth(line);
            g.setColor(new Color(0, 0, 0, 80));
            g.drawString(line, x + (w - tw) / 2 + 1, startY + 1);
            g.setColor(border.darker());
            g.drawString(line, x + (w - tw) / 2, startY);
            startY += fm.getHeight() + 2;
        }
    }

    // tag popup 
    private void drawTagPopup(Graphics2D g) {
        String line1 = "TAGGED!";
        String line2 = "-10 POINTS";

        int cx = getWidth() / 2;
        int cy = getHeight() / 2 + 40;

        g.setFont(new Font("SansSerif", Font.BOLD, 26));
        FontMetrics fm1 = g.getFontMetrics();
        Color tagRed = new Color(220, 40, 40, (int)(tagAlpha * 255));
        g.setColor(new Color(0, 0, 0, (int)(tagAlpha * 120)));
        g.drawString(line1, cx - fm1.stringWidth(line1) / 2 + 2, cy + 2);
        g.setColor(tagRed);
        g.drawString(line1, cx - fm1.stringWidth(line1) / 2, cy);

        g.setFont(new Font("SansSerif", Font.BOLD, 15));
        FontMetrics fm2 = g.getFontMetrics();
        Color subRed = new Color(220, 60, 60, (int)(tagAlpha * 220));
        g.setColor(subRed);
        g.drawString(line2, cx - fm2.stringWidth(line2) / 2, cy + 22);
    }
}