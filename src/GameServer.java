/**
 * GameServer — main entry point for the game server.
 */
public class GameServer {

    private static final int  ROUND_DURATION_SECONDS = 120; // default; players can change via lobby

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
        Thread         listenerThread = new Thread(playerListener, "PlayerListener");
        listenerThread.setDaemon(false);
        listenerThread.start();

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

        // ── Start game and wait for players to vote to start ──────────────────
        gameLogic.startGame();
        System.out.println("[GameServer] Ready — waiting for players to connect and vote.");
    }
}