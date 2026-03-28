import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class Server {

    private static final int TCP_PORT = PropertyFileReader.getTCPPort();

    public static void main(String[] args) {

        // PlayerRegistry instantiated here. It will keep track of the players.
        System.out.println("Game starting. Awaiting players.");
        PlayerRegistry playerRegistry = new PlayerRegistry();

        ServerUDPQueue packetQueue  = new ServerUDPQueue();
        PlayerListener playerListener = new PlayerListener(playerRegistry, packetQueue);
        Thread playerThread = new Thread(playerListener, "PlayerListener");
        playerThread.setDaemon(false);
        playerThread.start();
        System.out.println("[Server] PlayerListener started on UDP:" + PlayerListener.UDP_PORT);

        //Starts sending GamePanels to the Clients
        GamePanel gamePanel = new GamePanel();

        Thread gameThread = new Thread(gamePanel, "GameLoop");
        gameThread.setDaemon(false);
        gameThread.start();
        System.out.println("Game loop started.");

        // accept TCP clients — each gets its own thread so they don't block each other
        try (ServerSocket serverSocket = new ServerSocket(TCP_PORT)) {
            System.out.println("[Server] Listening for TCP on port " + TCP_PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("[Server] Client connected: " + clientSocket.getInetAddress());

                //creates a new handler thread per client so accept() is never blocked
                Thread clientThread = new Thread(() -> {
                    try {
                        ClientHandler handler = new ClientHandler(clientSocket, gamePanel);
                        gamePanel.addClient(handler);
                    } catch (IOException e) {
                        System.err.println("[Server] Failed to create ClientHandler: " + e.getMessage());
                    }
                }, "ClientHandler-" + clientSocket.getInetAddress());

                clientThread.setDaemon(true);
                clientThread.start();
            }
        } catch (IOException e) {
            System.err.println("[Server] ServerSocket error: " + e.getMessage());
            e.printStackTrace();
        }
    

    }
}
