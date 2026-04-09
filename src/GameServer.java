/**
 * GameServer — main entry point for the game server.
 */
public class GameServer {

    private static final int  ROUND_DURATION_SECONDS = 120;
    private static final int  REQUIRED_PLAYERS       = 4;
    private static final long LOBBY_COUNTDOWN_MS     = 5_600; // 5s countdown + 0.6s "GO!" display

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

        // ── Start game, then wait for all players and start round timer ───────
        gameLogic.startGame();
        System.out.println("[GameServer] Ready — waiting for players.");

        // Watch for all players to join, then mirror the client lobby countdown
        Thread roundStarter = new Thread(() -> {
            System.out.println("[GameServer] Waiting for " + REQUIRED_PLAYERS + " players...");
            while (gameLogic.getConnectedPlayerCount() < REQUIRED_PLAYERS) {
                try { Thread.sleep(200); } catch (InterruptedException e) { return; }
            }
            System.out.println("[GameServer] All players connected — starting lobby countdown.");
            try { Thread.sleep(LOBBY_COUNTDOWN_MS); } catch (InterruptedException e) { return; }
            gameLogic.startRound();
        }, "RoundStarter");
        roundStarter.setDaemon(true);
        roundStarter.start();
    }
}