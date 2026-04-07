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

    public DisplayPanel(String serverIp, int playerId) {
        this.serverIp = serverIp;
        this.playerId = playerId;
        setPreferredSize(new Dimension(COLS * TILE, ROWS * TILE));
        setBackground(Color.BLACK);
        connectToServer();
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
                g.drawString(statusMsg,
                        (getWidth()  - fm.stringWidth(statusMsg)) / 2,
                        getHeight() / 2);
            }
        }
    }
}