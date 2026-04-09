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
    private final java.util.List<ClientHandler> allClients; // shared across all handlers

    private DataOutputStream out;
    private volatile boolean running = true;
    private int assignedPlayerId = -1;

    public ClientHandler(Socket socket, GamePanel gamePanel, GameLogic gameLogic,
                         java.util.List<ClientHandler> allClients) throws IOException {
        this.socket     = socket;
        this.gamePanel  = gamePanel;
        this.gameLogic  = gameLogic;
        this.allClients = allClients;
        this.out        = new DataOutputStream(socket.getOutputStream());
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

                // Register in shared list then tell everyone who is now connected
                allClients.add(this);
                broadcastLobbyUpdate();
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

    /** Sends a LOBBY_UPDATE line to this specific client. */
    private void sendLobbyUpdate(boolean[] connected) {
        if (!running) return;
        try {
            StringBuilder sb = new StringBuilder("LOBBY_UPDATE");
            for (boolean c : connected) sb.append(c ? ",1" : ",0");
            sb.append("\n");
            out.write(0x00); // sentinel so DisplayPanel knows this is a text line
            out.write(sb.toString().getBytes());
            out.flush();
        } catch (IOException e) {
            disconnect();
        }
    }

    /** Builds the current connected-player array and pushes it to all clients. */
    private void broadcastLobbyUpdate() {
        boolean[] connected = new boolean[4];
        synchronized (allClients) {
            for (ClientHandler h : allClients) {
                int pid = h.getAssignedPlayerId();
                if (pid >= 1 && pid <= 4) connected[pid - 1] = true;
            }
            for (ClientHandler h : allClients) h.sendLobbyUpdate(connected);
        }
    }

    public void disconnect() {
        running = false;
        gamePanel.removeClient(this);
        allClients.remove(this);
        if (assignedPlayerId > 0) {
            gameLogic.removePlayer(assignedPlayerId);
            System.out.printf("[ClientHandler] Player %d removed.%n", assignedPlayerId);
        }
        broadcastLobbyUpdate();
        try { socket.close(); } catch (IOException ignored) {}
    }

    public int getAssignedPlayerId() { return assignedPlayerId; }
}