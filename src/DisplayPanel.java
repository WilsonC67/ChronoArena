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
 *   S→C  repeated: [int frameLength][byte[] jpegData]
 */
public class DisplayPanel extends JPanel {

    private static final int    TILE     = PropertyFileReader.getTileSize();
    private static final int    COLS     = PropertyFileReader.getColNum();
    private static final int    ROWS     = PropertyFileReader.getRowNum();
    private static final String IP       = PropertyFileReader.getIP();
    private static final int    TCP_PORT = PropertyFileReader.getTCPPort();

    private BufferedImage currentFrame;
    private final Object  frameLock = new Object();
    private String        statusMsg = "Connecting to server...";

    /** Player ID this panel will send in the JOIN message. 0 = display-only. */
    private final int playerId;

    public DisplayPanel(int playerId) {
        this.playerId = playerId;
        setPreferredSize(new Dimension(COLS * TILE, ROWS * TILE));
        setBackground(Color.BLACK);
        connectToServer(IP, TCP_PORT);
    }

    /** Convenience — display-only mode (no JOIN as a player). */
    public DisplayPanel() { this(0); }

    // ── Connect & stream ──────────────────────────────────────────────────────

    private void connectToServer(String ip, int port) {
        Thread t = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    setStatus("Connecting to " + ip + ":" + port + " ...");
                    Socket socket = new Socket();
                    socket.connect(new InetSocketAddress(InetAddress.getByName(ip), port), 5_000);

                    OutputStream rawOut = socket.getOutputStream();
                    DataInputStream in  = new DataInputStream(socket.getInputStream());

                    // 1. Send JOIN
                    String joinMsg = "JOIN " + playerId + "\n";
                    rawOut.write(joinMsg.getBytes());
                    rawOut.flush();

                    // 2. Read the text response line (WELCOME or REJECT)
                    StringBuilder sb = new StringBuilder();
                    int ch;
                    while ((ch = in.read()) != -1 && ch != '\n') sb.append((char) ch);
                    String response = sb.toString().trim();

                    if (response.startsWith("REJECT")) {
                        setStatus("Server rejected: " + response.substring(7));
                        socket.close();
                        Thread.sleep(5_000);
                        continue;
                    }

                    // WELCOME <id>
                    if (response.startsWith("WELCOME")) {
                        int assignedId = Integer.parseInt(response.split(" ")[1]);
                        setStatus("Connected as player " + assignedId);
                        System.out.println("[DisplayPanel] " + response);
                    }

                    // 3. Stream binary frames
                    streamFrames(in);
                    socket.close();

                } catch (Exception e) {
                    setStatus("Lost connection — retrying...");
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
            byte[] data = new byte[length];
            in.readFully(data);
            BufferedImage frame = ImageIO.read(new ByteArrayInputStream(data));
            if (frame != null) {
                synchronized (frameLock) { currentFrame = frame; }
                SwingUtilities.invokeLater(this::repaint);
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
                g.drawString(statusMsg, (getWidth() - fm.stringWidth(statusMsg)) / 2,
                        getHeight() / 2);
            }
        }
    }
}
