import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * TCPMonitor — listens for incoming client TCP connections.
 *
 * For each new connection it creates a ClientHandler (which handles the JOIN
 * handshake and player registration in GameLogic), runs it on a short thread,
 * then loops back to accept the next client.
 */
public class TCPMonitor implements Runnable {

    private final int       port;
    private final GamePanel gamePanel;
    private final GameLogic gameLogic;
    private final java.util.List<ClientHandler> allClients =
            java.util.Collections.synchronizedList(new java.util.ArrayList<>());

    public TCPMonitor(int port, GamePanel gamePanel, GameLogic gameLogic) {
        this.port      = port;
        this.gamePanel = gamePanel;
        this.gameLogic = gameLogic;
    }

    @Override
    public void run() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("[TCPMonitor] Listening for clients on TCP:" + port);

            while (!Thread.currentThread().isInterrupted()) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("[TCPMonitor] Incoming connection from "
                        + clientSocket.getRemoteSocketAddress());

                ClientHandler handler = new ClientHandler(clientSocket, gamePanel, gameLogic, allClients);
                // Handshake runs on its own thread so accept() isn't blocked
                Thread t = new Thread(handler, "ClientHandshake-" + clientSocket.getPort());
                t.setDaemon(true);
                t.start();
            }

        } catch (IOException e) {
            System.err.println("[TCPMonitor] Server socket error: " + e.getMessage());
        }
    }
}