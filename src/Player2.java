import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Player2 — UDP heartbeat sender and action dispatcher, runs on each player's machine.
 *
 * NOTE: In the current architecture, players connect via ChronoArenaClient, which
 * handles both the TCP display connection and UDP movement packets directly.
 * Player2 is retained as a standalone UDP utility/testing class.
 *
 * Responsibilities:
 *   1. HeartbeatPulse  — daemon thread; sends a UDP HEARTBEAT packet to the
 *                        server's PlayerMonitor every HEARTBEAT_INTERVAL_MS.
 *                        Packet: "<playerId>,<udpPort>,HEARTBEAT,0.0,0.0,"
 *
 *   2. sendAction()    — transmits any non-heartbeat game action packet.
 *                        Packet: "<playerId>,<udpPort>,<action>,<x>,<y>,<extra>"
 *
 * Configuration (JVM system properties):
 *   -Dserver.ip=192.168.1.10    IP of the machine running GameServer
 *   -Dplayer.id=1               unique integer ID for this player (1-4)
 *   -Dservice.port=1235         UDP port (should match udp.port in config.properties)
 *
 * Example standalone usage (for testing UDP without the full client):
 *   Machine A (server):   java -cp . GameServer
 *   Machine B (player 1): java -Dserver.ip=192.168.1.10 -Dplayer.id=1 -cp . Player2
 *   Machine C (player 2): java -Dserver.ip=192.168.1.10 -Dplayer.id=2 -cp . Player2
 */
public class Player2 implements Runnable {

    // -----------------------------------------------------------------------
    // Configuration — read from system properties at startup
    // -----------------------------------------------------------------------

    /** IP address of the machine running GameServer. Matches -Dserver.ip used by ChronoArenaClient. */
    public static final String SERVER_HOST =
            System.getProperty("server.ip", "localhost");

    /** Unique integer ID for this player. */
    public static final int PLAYER_ID =
            Integer.parseInt(System.getProperty("player.id", "1"));

    /**
     * TCP port this player reports to the server.
     * Each player on the same LAN must use a different value.
     * Matches the -Dservice.port convention from Node.java.
     */
    public static final int SERVICE_PORT =
            Integer.parseInt(System.getProperty("service.port", "5102"));

    // -----------------------------------------------------------------------
    // Constants
    // -----------------------------------------------------------------------

    /** Must match PlayerMonitor.UDP_PORT on the server. */
    private static final int HEARTBEAT_UDP_PORT   = 6001;

    /** How often this player sends a keepalive to the server. */
    private static final int HEARTBEAT_INTERVAL_MS = 5_000;

    // -----------------------------------------------------------------------
    // State
    // -----------------------------------------------------------------------

    private final AtomicBoolean running = new AtomicBoolean(true);

    /** Reuse a single DatagramSocket for all outbound UDP traffic. */
    private DatagramSocket udpSocket;
    private InetAddress    serverAddr;

    // -----------------------------------------------------------------------
    // Entry point (Runnable — start on a thread, or call run() directly)
    // -----------------------------------------------------------------------

    @Override
    public void run() {
        try {
            udpSocket  = new DatagramSocket();
            serverAddr = InetAddress.getByName(SERVER_HOST);

            System.out.printf("[Player %d] Connected.  server=%s  port=%d  heartbeat every %dms%n",
                    PLAYER_ID, SERVER_HOST, SERVICE_PORT, HEARTBEAT_INTERVAL_MS);

            // Heartbeat sender — daemon so it exits when the game closes
            Thread pulse = new Thread(this::heartbeatLoop, "HeartbeatPulse-P" + PLAYER_ID);
            pulse.setDaemon(true);
            pulse.start();

            // The calling thread can now drive the game loop / input handling.
            // Call sendAction() from anywhere to push state to the server.

        } catch (Exception e) {
            System.err.printf("[Player %d] Startup error: %s%n", PLAYER_ID, e.getMessage());
        }
    }

    // -----------------------------------------------------------------------
    // Public API — call from Swing input listeners / game loop
    // -----------------------------------------------------------------------

    /**
     * Sends a game-state packet to the server.
     *
     * @param action the action the player is performing
     * @param x      current X position
     * @param y      current Y position
     * @param extra  optional payload (powerup id, bullet id, ability name…); pass "" if unused
     */
    public void sendAction(PlayerActionEnum action, float x, float y, String extra) {
        String payload = String.format("%d,%d,%s,%.2f,%.2f,%s",
                PLAYER_ID, SERVICE_PORT, action.name(), x, y, extra);
        sendUdp(payload);
    }

    /** Convenience overload — no extra data. */
    public void sendAction(PlayerActionEnum action, float x, float y) {
        sendAction(action, x, y, "");
    }

    /** Stops the heartbeat and closes the socket. Call on game exit. */
    public void shutdown() {
        running.set(false);
        if (udpSocket != null && !udpSocket.isClosed()) {
            udpSocket.close();
        }
    }

    // -----------------------------------------------------------------------
    // Heartbeat loop (daemon thread)
    // -----------------------------------------------------------------------

    private void heartbeatLoop() {
        // Heartbeat payload — position fields are 0,0 and ignored by the server
        String payload = String.format("%d,%d,HEARTBEAT,0.0,0.0,", PLAYER_ID, SERVICE_PORT);

        while (running.get() && !Thread.currentThread().isInterrupted()) {
            sendUdp(payload);
            System.out.printf("[Player %d] Heartbeat sent → %s:%d%n",
                    PLAYER_ID, SERVER_HOST, HEARTBEAT_UDP_PORT);
            try {
                Thread.sleep(HEARTBEAT_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    // -----------------------------------------------------------------------
    // UDP helper
    // -----------------------------------------------------------------------

    private void sendUdp(String payload) {
        try {
            byte[]         data   = payload.getBytes();
            DatagramPacket packet = new DatagramPacket(
                    data, data.length, serverAddr, HEARTBEAT_UDP_PORT);
            udpSocket.send(packet);
        } catch (Exception e) {
            if (running.get()) {
                System.err.printf("[Player %d] UDP send error: %s%n", PLAYER_ID, e.getMessage());
            }
        }
    }

    // -----------------------------------------------------------------------
    // Standalone entry point for testing
    // -----------------------------------------------------------------------

    public static void main(String[] args) throws InterruptedException {
        Player2 player = new Player2();
        player.run();  // starts heartbeat thread

        System.out.printf("[Player %d] Running. Press Ctrl+C to stop.%n", PLAYER_ID);

        // Simulate some game actions for testing purposes
        Thread.sleep(2000);
        player.sendAction(PlayerActionEnum.MOVE_RIGHT, 100f, 200f);
        Thread.sleep(1000);
        player.sendAction(PlayerActionEnum.SHOOT, 105f, 200f, "BULLET_ID:1");
        Thread.sleep(1000);
        player.sendAction(PlayerActionEnum.PICKUP_POWERUP, 110f, 200f, "SHIELD");

        // Keep alive so heartbeats keep firing — in a real game the Swing loop does this
        Thread.currentThread().join();
    }
}