/**
 * GameServer — main entry point for the game server.
 *
 * Start order:
 *   1. PlayerRegistry + ServerUDPQueue (shared state)
 *   2. GameLogic
 *   3. PlayerMonitor (UDP 6001) — heartbeats from Player2 clients
 *   4. PlayerListener (UDP 6002) — game-action packets
 *   5. GamePanel render/broadcast loop
 *   6. TCPMonitor — accepts display/player clients, handles JOIN registration
 *   7. gameLogic.startGame()
 *
 * Run on the host machine:
 *   java GameServer
 *
 * Each player machine runs ChronoArenaClient, which connects via TCP,
 * sends "JOIN <id>", and receives the rendered arena frames.
 */
public class GameServer {

    private static final int ROUND_DURATION_SECONDS = 120;

    public static void main(String[] args) {
        System.out.println("=== ChronoArena Server starting ===");

        // ── Shared state ──────────────────────────────────────────────────────
        PlayerRegistry registry    = new PlayerRegistry();
        ServerUDPQueue packetQueue = new ServerUDPQueue();

        // ── Game logic ────────────────────────────────────────────────────────
        GameLogic gameLogic = new GameLogic(ROUND_DURATION_SECONDS);
        gameLogic.setPacketQueue(packetQueue);

        // ── PlayerMonitor (UDP 6001) — heartbeats ─────────────────────────────
        PlayerMonitor monitor       = new PlayerMonitor(registry);
        Thread        monitorThread = new Thread(monitor, "PlayerMonitor");
        monitorThread.setDaemon(false);
        monitorThread.start();
        System.out.println("[GameServer] PlayerMonitor on UDP:" + PlayerMonitor.UDP_PORT);

        // ── PlayerListener (UDP 6002) — game actions ──────────────────────────
        PlayerListener playerListener = new PlayerListener(registry, packetQueue);

        // ── GamePanel (headless render + broadcast) ───────────────────────────
        GamePanel gamePanel = new GamePanel();
        gamePanel.setPacketQueue(packetQueue);
        gamePanel.setPlayerListener(playerListener);
        gamePanel.setGameLogic(gameLogic);

        Thread gameLoopThread = new Thread(gamePanel, "GameLoop");
        gameLoopThread.setDaemon(true);
        gameLoopThread.start();
        System.out.println("[GameServer] Game loop started.");

        // ── TCPMonitor — display + player clients ─────────────────────────────
        int        tcpPort   = PropertyFileReader.getTCPPort();
        TCPMonitor tcpServer = new TCPMonitor(tcpPort, gamePanel, gameLogic);
        Thread     tcpThread = new Thread(tcpServer, "TCPMonitor");
        tcpThread.setDaemon(false);
        tcpThread.start();
        System.out.println("[GameServer] TCPMonitor on TCP:" + tcpPort);

        // ── Start the round ───────────────────────────────────────────────────
        gameLogic.startGame();
        System.out.println("[GameServer] Ready — waiting for players.");
    }
}
