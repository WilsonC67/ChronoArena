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

            } else {
                // Display-only connection (no JOIN line sent)
                sendTextLine("WELCOME 0");
                System.out.println("[ClientHandler] Display-only client connected.");
            }

            // Register for frame broadcast first
            gamePanel.addClient(this);

            // Then add to lobby list and broadcast — client is now in streamFrames()
            if (assignedPlayerId > 0) {
                allClients.add(this);
                try { Thread.sleep(200); } catch (InterruptedException ignored) {}
                broadcastLobbyUpdate();
            }

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
            synchronized (out) {
                out.writeInt(data.length);
                out.write(data);
                out.flush();
            }
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

    /** Sends any text message to this client using the -1 frame flag. */
    public void sendTextMessage(String text) {
        if (!running) return;
        try {
            byte[] payload = text.getBytes();
            synchronized (out) {
                out.writeInt(-1);
                out.writeInt(payload.length);
                out.write(payload);
                out.flush();
            }
        } catch (IOException e) {
            disconnect();
        }
    }

    /** Sends a LOBBY_UPDATE line to this specific client. */
    private void sendLobbyUpdate(boolean[] connected) {
        StringBuilder sb = new StringBuilder("LOBBY_UPDATE");
        for (boolean c : connected) sb.append(c ? ",1" : ",0");
        sendTextMessage(sb.toString());
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