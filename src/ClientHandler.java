import java.io.*;
import java.net.Socket;


public class ClientHandler {
    private final Socket socket;
    private final DataOutputStream out;
    private final GamePanel gamePanel;
    private volatile boolean running = true;

    //handles sending updated GamePanels to the client
    public ClientHandler(Socket socket, GamePanel gamePanel) throws IOException {
        this.socket = socket;
        this.gamePanel = gamePanel;
        this.out = new DataOutputStream(socket.getOutputStream());
    }

    public void sendFrame(byte[] data) {
        if (!running) return;
        try {
            out.writeInt(data.length);
            out.write(data);
            out.flush();
        } catch (IOException e) {
            System.out.println("[ClientHandler] Client disconnected.");
            disconnect();
        }
    }

    public void disconnect() {
        running = false;
        gamePanel.removeClient(this);
        try { socket.close(); } catch (IOException ignored) {}
    }
}
