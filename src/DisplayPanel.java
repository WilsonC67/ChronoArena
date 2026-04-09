import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.*;
import javax.imageio.ImageIO;
import javax.swing.*;

/**
 * DisplayPanel — connects to GameServer via TCP, performs the JOIN handshake,
 * then streams rendered arena frames and paints them.
 *
 * Protocol:
 *   C→S  "JOIN <playerId>\n"
 *   S→C  "WELCOME <assignedId>\n"   (or "REJECT <reason>\n")
 *   S→C  repeated binary frames: [int length][byte[] jpeg]
 */
public class DisplayPanel extends JPanel {

    private static final int TILE     = PropertyFileReader.getTileSize();
    private static final int COLS     = PropertyFileReader.getColNum();
    private static final int ROWS     = PropertyFileReader.getRowNum();
    private static final int TCP_PORT = PropertyFileReader.getTCPPort();

    private final String serverIp;
    private final int    playerId;

    private BufferedImage currentFrame;
    private final Object  frameLock = new Object();
    private String        statusMsg = "Connecting...";

    // optional callback fired when the server sends a LOBBY_UPDATE line
    private java.util.function.Consumer<boolean[]> lobbyCallback;
    // optional callback fired when the server sends a SCORE_UPDATE line
    private java.util.function.BiConsumer<Integer, int[]> scoreCallback;
    // optional callback fired with zone updates: (zoneIndex, stateName, ownerId, progress)
    private ZoneUpdateCallback zoneCallback;
    // optional callback fired with player state updates
    private PlayerUpdateCallback playerCallback;
    // optional callback fired when the server assigns the connected player ID
    private java.util.function.IntConsumer assignedIdCallback;

    @FunctionalInterface
    public interface PlayerUpdateCallback {
        void onPlayerUpdate(int id, int score, int hp, boolean frozen, boolean hasWeapon, boolean hasShield, boolean speedBoost);
    }

    @FunctionalInterface
    public interface ZoneUpdateCallback {
        void onZoneUpdate(int zoneIndex, String state, int ownerId, int progress);
    }

    public DisplayPanel(String serverIp, int playerId) {
        this.serverIp = serverIp;
        this.playerId = playerId;
        setPreferredSize(new Dimension(COLS * TILE, ROWS * TILE));
        setBackground(Color.BLACK);
        connectToServer();
    }

    /** Optional — set before the connection is established. */
    public void setLobbyCallback(java.util.function.Consumer<boolean[]> cb) {
        this.lobbyCallback = cb;
    }

    /** Optional — fired with (secondsLeft, int[4] scores) each second. */
    public void setScoreCallback(java.util.function.BiConsumer<Integer, int[]> cb) {
        this.scoreCallback = cb;
    }

    /** Optional — fired each second with zone state updates. */
    public void setZoneCallback(ZoneUpdateCallback cb) {
        this.zoneCallback = cb;
    }

    /** Optional — fired each second with each player's state. */
    public void setPlayerCallback(PlayerUpdateCallback cb) {
        this.playerCallback = cb;
    }

    /** Optional — fired when the server assigns the connected player ID. */
    public void setAssignedIdCallback(java.util.function.IntConsumer cb) {
        this.assignedIdCallback = cb;
    }

    // ── Connection loop ───────────────────────────────────────────────────────

    private void connectToServer() {
        Thread t = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    setStatus("Connecting to " + serverIp + ":" + TCP_PORT + " ...");

                    Socket socket = new Socket();
                    socket.connect(
                            new InetSocketAddress(InetAddress.getByName(serverIp), TCP_PORT),
                            5_000);

                    OutputStream rawOut = socket.getOutputStream();
                    DataInputStream in  = new DataInputStream(socket.getInputStream());

                    // 1. Send JOIN
                    rawOut.write(("JOIN " + playerId + "\n").getBytes());
                    rawOut.flush();

                    // 2. Read text response (one line)
                    StringBuilder sb = new StringBuilder();
                    int ch;
                    while ((ch = in.read()) != -1 && ch != '\n') sb.append((char) ch);
                    String response = sb.toString().trim();

                    if (response.startsWith("REJECT")) {
                        setStatus("Rejected: " + response.substring(7).trim());
                        socket.close();
                        Thread.sleep(5_000);
                        continue;
                    }

                    if (response.startsWith("WELCOME")) {
                        int assignedId = Integer.parseInt(response.split(" ")[1]);
                        setStatus("Connected — Player " + assignedId);
                        if (assignedIdCallback != null) assignedIdCallback.accept(assignedId);
                        System.out.println("[DisplayPanel] Server says: " + response);
                    }

                    // 3. Stream frames until disconnect
                    streamFrames(in);
                    socket.close();

                } catch (Exception e) {
                    setStatus("Connection lost — retrying in 3 s...");
                    System.err.println("[DisplayPanel] " + e.getMessage());
                }
                try { Thread.sleep(3_000); } catch (InterruptedException ex) { break; }
            }
        }, "FrameReceiver");
        t.setDaemon(true);
        t.start();
    }

    private void streamFrames(DataInputStream in) throws IOException {
        while (!Thread.currentThread().isInterrupted()) {
            int length = in.readInt();
            if (length == -1) {
                // Text message — read the actual payload length then the bytes
                int textLength = in.readInt();
                byte[] textData = new byte[textLength];
                in.readFully(textData);
                handleTextLine(new String(textData).trim());
            } else {
                // Binary JPEG frame
                byte[] data = new byte[length];
                in.readFully(data);
                BufferedImage frame = ImageIO.read(new ByteArrayInputStream(data));
                if (frame != null) {
                    synchronized (frameLock) { currentFrame = frame; }
                    SwingUtilities.invokeLater(this::repaint);
                }
            }
        }
    }

    private void handleTextLine(String line) {
        if (line.startsWith("LOBBY_UPDATE,") && lobbyCallback != null) {
            String[] parts = line.split(",");
            if (parts.length == 5) {
                boolean[] connected = new boolean[4];
                for (int i = 0; i < 4; i++) connected[i] = parts[i + 1].equals("1");
                lobbyCallback.accept(connected);
            }
        } else if (line.startsWith("PLAYER_UPDATE,") && playerCallback != null) {
            String[] parts = line.split(",");
            // each player block is 7 fields: id,score,hp,frozen,hasWeapon,hasShield,speedBoost
            int i = 1;
            while (i + 6 < parts.length) {
                int     id         = Integer.parseInt(parts[i]);
                int     score      = Integer.parseInt(parts[i+1]);
                int     hp         = Integer.parseInt(parts[i+2]);
                boolean frozen     = parts[i+3].equals("1");
                boolean hasWeapon  = parts[i+4].equals("1");
                boolean hasShield  = parts[i+5].equals("1");
                boolean speedBoost = parts[i+6].equals("1");
                playerCallback.onPlayerUpdate(id, score, hp, frozen, hasWeapon, hasShield, speedBoost);
                i += 7;
            }
        } else if (line.startsWith("ZONE_UPDATE,") && zoneCallback != null) {
            String[] parts = line.split(",");
            if (parts.length >= 1 + 9) {
                for (int z = 0; z < 3; z++) {
                    int base = 1 + z * 3;
                    String state    = parts[base];
                    int    ownerId  = Integer.parseInt(parts[base + 1]);
                    int    progress = Integer.parseInt(parts[base + 2]);
                    zoneCallback.onZoneUpdate(z, state, ownerId, progress);
                }
            }
        } else if (line.startsWith("SCORE_UPDATE,")) {
            String[] parts = line.split(",");
            if (parts.length >= 6) {
                int secondsLeft = Integer.parseInt(parts[1]);
                int[] scores = new int[4];
                for (int i = 0; i < 4; i++) scores[i] = Integer.parseInt(parts[i + 2]);
                if (scoreCallback != null) scoreCallback.accept(secondsLeft, scores);
                // Zone data starts at index 6: state,ownerId,progress per zone
                if (zoneCallback != null && parts.length >= 6 + 9) {
                    for (int z = 0; z < 3; z++) {
                        int base = 6 + z * 3;
                        String state    = parts[base];
                        int    ownerId  = Integer.parseInt(parts[base + 1]);
                        int    progress = Integer.parseInt(parts[base + 2]);
                        zoneCallback.onZoneUpdate(z, state, ownerId, progress);
                    }
                }
            }
        }
    }

    private void setStatus(String msg) {
        statusMsg = msg;
        SwingUtilities.invokeLater(this::repaint);
    }

    // ── Paint ─────────────────────────────────────────────────────────────────

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        synchronized (frameLock) {
            if (currentFrame != null) {
                g.drawImage(currentFrame, 0, 0, getWidth(), getHeight(), null);
            } else {
                g.setColor(new Color(30, 30, 42));
                g.fillRect(0, 0, getWidth(), getHeight());
                g.setColor(new Color(255, 200, 40));
                g.setFont(new Font("SansSerif", Font.BOLD, 18));
                FontMetrics fm = g.getFontMetrics();
                g.drawString(statusMsg,
                        (getWidth()  - fm.stringWidth(statusMsg)) / 2,
                        getHeight() / 2);
            }
        }
    }
}