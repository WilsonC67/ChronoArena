
/**
 * GameServer — main entry point for the game server.
 * 
 * Responsibilities:
 *   - Instantiate the shared PlayerRegistry.
 *   - Start the PlayerMonitor (UDP port 6001) on a non-daemon thread.
 *
 * First compile the Java files:
 * javac *.java
 * 
 * Run on the host laptop:
 *   java Game.GameServer
 *
 * Players on other machines then launch Player.java with:
 *   java -Dserver.host=<IP_OF_GAMESERVER_DEVICE> -Dplayer.id=<N> -Dservice.port=<PORT> Player2
 */
public class GameServer {

    public static void main(String[] args) {
        System.out.println("=== Game Server starting ===");

        // Shared state — all threads read/write through this registry.
        PlayerRegistry registry = new PlayerRegistry();

        // --- PlayerMonitor (UDP 6001) -------------------------------------
        PlayerMonitor monitor = new PlayerMonitor(registry);
        Thread monitorThread  = new Thread(monitor, "PlayerMonitor");
        monitorThread.setDaemon(false);   // keeps JVM alive
        monitorThread.start();
        System.out.println("[GameServer] PlayerMonitor started.");

        System.out.println("[GameServer] Ready — listening on UDP:" + PlayerMonitor.UDP_PORT);
        System.out.println("[GameServer] Waiting for players to connect...");

        // The main thread is done; PlayerMonitor's non-daemon thread keeps the JVM alive.
    }
}