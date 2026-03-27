import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class Server {

    private static final int TCP_PORT = PropertyFileReader.getTCPPort();

    public static void main(String[] args) {

        // PlayerRegistry instantiated here. It will keep track of the players.
        System.out.println("Game starting. Awaiting players.");
        PlayerRegistry playerRegistry = new PlayerRegistry();

        PlayerListener playerListener = new PlayerListener(playerRegistry);
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

        // accept TCP clients and register them with GamePanel
        try {
            ServerSocket serverSocket = new ServerSocket(TCP_PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Client connected: " + clientSocket.getInetAddress());
                ClientHandler handler = new ClientHandler(clientSocket, gamePanel);
                gamePanel.addClient(handler);
            }
        } catch (IOException e) {
            System.out.println("Server Socket Error");
            e.printStackTrace();
        }

    }
}
