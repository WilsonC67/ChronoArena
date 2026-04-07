import java.io.*;
import java.net.Socket;

/**
 * ClientHandler — one instance per connected display client (ChronoArenaClient).
 *
 * Protocol (TCP):
 *   C→S  "JOIN <playerId>\n"
 *   S→C  "WELCOME <assignedId>\n"   OR   "REJECT <reason>\n"
 *   S→C  repeated binary frames: [int length][byte[] jpeg]
 *
 * The handler registers the player in GameLogic on JOIN and removes them on disconnect.
 */
public class ClientHandler implements Runnable {
    private static final int MAX_PLAYERS = 4;

    private final Socket    socket;
    private final GamePanel gamePanel;
    private final GameLogic gameLogic;

    private DataOutputStream out;
    private volatile boolean running = true;
    private int assignedPlayerId = -1;

    public ClientHandler(Socket socket, GamePanel gamePanel, GameLogic gameLogic) throws IOException {
        this.socket    = socket;
        this.gamePanel = gamePanel;
        this.gameLogic = gameLogic;
        this.out       = new DataOutputStream(socket.getOutputStream());
    }

    // -----------------------------------------------------------------------
    // Runnable — short-lived thread just for the handshake, then stays alive
    // for frame delivery via sendFrame()
    // -----------------------------------------------------------------------

    @Override
    public void run() {
        try {
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream()));

            // Wait for JOIN <playerId>
            String line = reader.readLine();
            if (line != null && line.startsWith("JOIN ")) {
                int requestedId = Integer.parseInt(line.substring(5).trim());

                // Enforce 4-player cap
                if (gameLogic.getPlayers().size() >= MAX_PLAYERS) {
                    sendTextLine("REJECT Server full (" + MAX_PLAYERS + " players max)");
                    disconnect();
                    return;
                }

                assignedPlayerId = requestedId;
                gameLogic.addPlayer(assignedPlayerId, "Player " + assignedPlayerId);
                sendTextLine("WELCOME " + assignedPlayerId);
                System.out.printf("[ClientHandler] Player %d joined from %s%n",
                        assignedPlayerId, socket.getRemoteSocketAddress());
            } else {
                // Display-only connection (no JOIN line sent)
                sendTextLine("WELCOME 0");
                System.out.println("[ClientHandler] Display-only client connected.");
            }

            // Now register for frame broadcast — this is all we do from here on
            gamePanel.addClient(this);

        } catch (IOException | NumberFormatException e) {
            System.out.println("[ClientHandler] Handshake error: " + e.getMessage());
            disconnect();
        }
        // Thread exits here; frames are pushed later via sendFrame()
    }

    // -----------------------------------------------------------------------
    // Frame broadcast — called from the game-loop thread each tick
    // -----------------------------------------------------------------------

    public void sendFrame(byte[] data) {
        if (!running) return;
        try {
            out.writeInt(data.length);
            out.write(data);
            out.flush();
        } catch (IOException e) {
            System.out.println("[ClientHandler] Client disconnected during frame send.");
            disconnect();
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private void sendTextLine(String text) throws IOException {
        out.write((text + "\n").getBytes());
        out.flush();
    }

    public void disconnect() {
        running = false;
        gamePanel.removeClient(this);
        if (assignedPlayerId > 0) {
            gameLogic.removePlayer(assignedPlayerId);
            System.out.printf("[ClientHandler] Player %d removed.%n", assignedPlayerId);
        }
        try { socket.close(); } catch (IOException ignored) {}
    }

    public int getAssignedPlayerId() { return assignedPlayerId; }
}
