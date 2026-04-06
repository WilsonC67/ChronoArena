/**
 * GameServer — main entry point for the game server.
 *
 * Responsibilities:
 *   - Instantiate the shared PlayerRegistry.
 *   - Start the PlayerMonitor (UDP port 6001) on a non-daemon thread.
 *   - Start the TCPServer on a non-daemon thread so display clients can connect.
 *   - Start the GamePanel render/broadcast loop on a daemon thread.
 *
 * First compile the Java files:
 *   javac *.java
 *
 * Run on the host laptop:
 *   java GameServer
 *
 * Players on other machines then launch Player.java with:
 *   java -Dserver.host=<IP_OF_GAMESERVER_DEVICE> -Dplayer.id=<N> -Dservice.port=<PORT> Player2
 */
public class GameServer {
 
    private static final int ROUND_DURATION_SECONDS = 120;
 
    public static void main(String[] args) {
        System.out.println("=== Game Server starting ===");
 
        // ── Shared state ──────────────────────────────────────────────────────
        PlayerRegistry registry   = new PlayerRegistry();
        ServerUDPQueue packetQueue = new ServerUDPQueue();
 
        // ── Game logic ────────────────────────────────────────────────────────
        // GameLogic owns its own zones, items, and player map.
        // PacketQueue is wired in so GameLogic can clean up on disconnect.
        GameLogic gameLogic = new GameLogic(ROUND_DURATION_SECONDS);
        gameLogic.setPacketQueue(packetQueue);
 
        // ── PlayerMonitor (UDP) ───────────────────────────────────────────────
        PlayerMonitor monitor       = new PlayerMonitor(registry);
        Thread        monitorThread = new Thread(monitor, "PlayerMonitor");
        monitorThread.setDaemon(false);   // keeps JVM alive
        monitorThread.start();
        System.out.println("[GameServer] PlayerMonitor started on UDP:" + PlayerMonitor.UDP_PORT);
 
        // ── PlayerListener ────────────────────────────────────────────────────
        PlayerListener playerListener = new PlayerListener(registry, packetQueue);
 
        // ── GamePanel (headless render + broadcast) ───────────────────────────
        GamePanel gamePanel = new GamePanel();
        gamePanel.setPacketQueue(packetQueue);
        gamePanel.setPlayerListener(playerListener);
        gamePanel.setGameLogic(gameLogic);
 
        // Game-loop on a daemon thread — dies when the JVM exits.
        Thread gameLoopThread = new Thread(gamePanel, "GameLoop");
        gameLoopThread.setDaemon(true);
        gameLoopThread.start();
        System.out.println("[GameServer] GamePanel render loop started.");
 
        // ── TCPServer (display broadcasting) ─────────────────────────────────
        int       tcpPort        = PropertyFileReader.getTCPPort();
        TCPMonitor tcpServer      = new TCPMonitor(tcpPort, gamePanel);
        Thread    tcpServerThread = new Thread(tcpServer, "TCPServer");
        tcpServerThread.setDaemon(false);  // keeps JVM alive alongside PlayerMonitor
        tcpServerThread.start();
        System.out.println("[GameServer] TCPServer started on TCP:" + tcpPort);
 
        // ── Start the round ───────────────────────────────────────────────────
        gameLogic.startGame();
        System.out.println("[GameServer] Ready — waiting for players and display clients.");
    }
}