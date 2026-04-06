import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * TCPServer — listens for incoming client TCP connections.
 *
 * For each new connection it creates a ClientHandler, registers it with
 * GamePanel so it receives broadcast frames, then moves on to accept the
 * next client. Disconnection and cleanup are handled by ClientHandler itself.
 *
 * Implements Runnable so GameServer can run it on a dedicated non-daemon
 * thread, keeping the JVM alive alongside PlayerMonitor.
 */
public class TCPMonitor implements Runnable {
 
    private final int       port;
    private final GamePanel gamePanel;
 
    public TCPMonitor(int port, GamePanel gamePanel) {
        this.port      = port;
        this.gamePanel = gamePanel;
    }
 
    @Override
    public void run() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("[TCPServer] Listening for display clients on TCP:" + port);
 
            while (!Thread.currentThread().isInterrupted()) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("[TCPServer] Client connected: " + clientSocket.getRemoteSocketAddress());
 
                ClientHandler handler = new ClientHandler(clientSocket, gamePanel);
                gamePanel.addClient(handler);
            }
 
        } catch (IOException e) {
            System.err.println("[TCPServer] Server socket error: " + e.getMessage());
        }
    }
}
 