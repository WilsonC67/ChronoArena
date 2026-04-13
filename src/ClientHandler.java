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
                int requestedId;
                try {
                    requestedId = Integer.parseInt(line.substring(5).trim());
                } catch (NumberFormatException e) {
                    sendTextLine("REJECT Invalid player ID — expected a number (1-4)");
                    disconnect();
                    return;
                }

                // Atomically check the cap, resolve the ID, and register the player
                // so that two simultaneous JOIN requests cannot both slip past the limit.
                int assignedId = gameLogic.tryAddPlayer(requestedId, "Player " + requestedId);

                if (assignedId == -1) {
                    sendTextLine("REJECT Server full (" + MAX_PLAYERS + " players max)");
                    System.out.printf("[ClientHandler] Rejected connection from %s — server full.%n",
                            socket.getRemoteSocketAddress());
                    disconnect();
                    return;
                }

                assignedPlayerId = assignedId;
                gameLogic.setOnReadyCallback(this::broadcastReadyUpdate);
                gameLogic.setOnVoteCallback(this::broadcastVoteUpdate);
                gameLogic.setOnAllReadyCallback(this::broadcastCountdownStart);
                gameLogic.setOnRestartVoteCallback(this::broadcastRestartVoteUpdate);
                gameLogic.setOnAllRestartCallback(this::broadcastRestartResult_Yes);
                gameLogic.setOnRestartDeclinedCallback(this::broadcastRestartResult_No);
                gameLogic.setOnTimerChangeCallback(this::broadcastTimerUpdate);
                gameLogic.setOnPlayerKilledCallback(this::broadcastPlayerKilled);
                sendTextLine("WELCOME " + assignedPlayerId);
                System.out.printf("[ClientHandler] Player %d joined from %s%n",
                        assignedPlayerId, socket.getRemoteSocketAddress());

            } else {
                // Connection arrived without a valid JOIN line — reject immediately.
                // Previously this fell through as a "display-only" client with id 0,
                // which caused a phantom Player 0 to appear and block game-start voting.
                sendTextLine("REJECT Missing JOIN — send: JOIN <playerId> (1-4)");
                System.out.printf("[ClientHandler] Rejected display-only / bad handshake from %s%n",
                        socket.getRemoteSocketAddress());
                disconnect();
                return;
            }

            // Register for frame broadcast first
            gamePanel.addClient(this);

            // Then add to lobby list and broadcast — client is now in streamFrames()
            if (assignedPlayerId > 0) {
                allClients.add(this);
                try { Thread.sleep(200); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
                broadcastLobbyUpdate();
                // Inform the newly joined client of the current round duration
                sendTextMessage("TIMER_UPDATE," + gameLogic.getRoundDurationSeconds());
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

    /** Broadcasts current ready state to all clients. */
    public void broadcastReadyUpdate() {
        boolean[] ready = gameLogic.getReadyState();
        StringBuilder sb = new StringBuilder("READY_UPDATE");
        for (boolean r : ready) sb.append(r ? ",1" : ",0");
        String msg = sb.toString();
        synchronized (allClients) {
            for (ClientHandler h : allClients) h.sendTextMessage(msg);
        }
    }

    /** Broadcasts current vote tally to all clients as VOTE_UPDATE,votes,total */
    public void broadcastVoteUpdate() {
        boolean[] votes = gameLogic.getVoteState();
        int voteCount = 0, total = 0;
        for (int i = 0; i < 4; i++) {
            // count only connected players
        }
        // recount properly from allClients
        synchronized (allClients) {
            total = allClients.size();
            for (boolean v : votes) if (v) voteCount++;
            String msg = "VOTE_UPDATE," + voteCount + "," + total;
            for (ClientHandler h : allClients) h.sendTextMessage(msg);
        }
    }

    /** Tells all clients that a player was forcibly removed via the kill switch. */
    public void broadcastPlayerKilled(int playerId) {
        String msg = "PLAYER_KILLED," + playerId;
        synchronized (allClients) {
            for (ClientHandler h : allClients) h.sendTextMessage(msg);
        }
        System.out.printf("[ClientHandler] Broadcast PLAYER_KILLED for player %d.%n", playerId);
    }

    /** Broadcasts current round duration to all clients as TIMER_UPDATE,seconds */
    public void broadcastTimerUpdate() {
        int seconds = gameLogic.getRoundDurationSeconds();
        String msg = "TIMER_UPDATE," + seconds;
        synchronized (allClients) {
            for (ClientHandler h : allClients) h.sendTextMessage(msg);
        }
    }

    /** Tells all clients to start the countdown — server is the source of truth. */
    public void broadcastCountdownStart() {
        synchronized (allClients) {
            for (ClientHandler h : allClients) h.sendTextMessage("COUNTDOWN_START");
        }
    }

    /** Broadcasts current restart vote tally to all clients as RESTART_VOTE_UPDATE,votes,total */
    public void broadcastRestartVoteUpdate() {
        boolean[] votes = gameLogic.getRestartVoteState();
        synchronized (allClients) {
            int voteCount = 0;
            int total = allClients.size();
            for (boolean v : votes) if (v) voteCount++;
            String msg = "RESTART_VOTE_UPDATE," + voteCount + "," + total;
            for (ClientHandler h : allClients) h.sendTextMessage(msg);
        }
    }

    /** Tells all clients the restart vote passed — everyone agreed. */
    public void broadcastRestartResult_Yes() {
        synchronized (allClients) {
            for (ClientHandler h : allClients) h.sendTextMessage("RESTART_RESULT,yes");
        }
        gameLogic.resetGame();
        gameLogic.startGame();
        broadcastLobbyUpdate();
        System.out.println("[ClientHandler] Game reset — returning all players to lobby (restart).");
    }

    /** Tells all clients the restart vote failed — return to lobby. */
    public void broadcastRestartResult_No() {
        synchronized (allClients) {
            for (ClientHandler h : allClients) h.sendTextMessage("RESTART_RESULT,no");
        }
        gameLogic.resetGame();
        gameLogic.startGame();
        broadcastLobbyUpdate();
        System.out.println("[ClientHandler] Game reset — returning all players to lobby (declined).");
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