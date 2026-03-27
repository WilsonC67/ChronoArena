import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.KeyAdapter;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import javax.imageio.ImageIO;

//Displays the updated GamePanel to the Client
public class DisplayPanel extends JPanel {
    private static final int TILE = PropertyFileReader.getTileSize();
    private static final int COLS = PropertyFileReader.getColNum();
    private static final int ROWS = PropertyFileReader.getRowNum();
    private static final String IP_STRING = PropertyFileReader.getIP();
    private static final int TCP_PORT = PropertyFileReader.getTCPPort();

    private BufferedImage currentFrame;
    private final Object frameLock = new Object();

    public DisplayPanel() {
        setPreferredSize(new Dimension(COLS * TILE, ROWS * TILE));
        setBackground(Color.BLACK);
        connectToServer(IP_STRING, TCP_PORT);
    }

    private void connectToServer(String IP, int port) {
        Thread receiver = new Thread(() -> {
            try (Socket socket = new Socket(InetAddress.getByName(IP), port);
                 DataInputStream in = new DataInputStream(socket.getInputStream());
                 DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {

                // Forward key input to server
                attachKeyListener(out);

                while (!Thread.currentThread().isInterrupted()) {
                    int length = in.readInt();
                    byte[] data = new byte[length];
                    in.readFully(data);

                    BufferedImage frame = ImageIO.read(new ByteArrayInputStream(data));
                    synchronized (frameLock) {
                        currentFrame = frame;
                    }
                    SwingUtilities.invokeLater(this::repaint);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        receiver.setDaemon(true);
        receiver.start();
    }

    private void attachKeyListener(DataOutputStream out) {
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                try {
                    out.writeInt(e.getKeyCode());
                    out.flush();
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        });
        setFocusable(true);
        requestFocusInWindow();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        synchronized (frameLock) {
            if (currentFrame != null) {
                g.drawImage(currentFrame, 0, 0, getWidth(), getHeight(), null);
            } else {
                g.setColor(Color.WHITE);
                g.drawString("Connecting to server...", 20, 30);
            }
        }
    }

}
