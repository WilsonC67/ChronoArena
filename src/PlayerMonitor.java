import java.net.DatagramPacket;
import java.net.DatagramSocket;

/**
 * PlayerMonitor — listens for UDP heartbeat/action packets from player clients.
 *
 * Port is read from config.properties (udp.port, default 1235).
 *
 * Two internal threads:
 *   receiverLoop() — blocks on DatagramSocket.receive(); runs on THIS thread.
 *   watcherLoop()  — daemon thread; calls sweepDeadPlayers() every SWEEP_INTERVAL ms.
 *
 * -----------------------------------------------------------------------
 * UDP packet format (UTF-8, comma-separated, 6+ fields):
 *
 *   playerId , udpPort , action , x , y [, extra]
 *   --------   -------   ------   -   -    -----
 *   int        int        enum   flt flt   string  (optional, "" if absent)
 *
 * Examples:
 *   "1,1235,HEARTBEAT,0.0,0.0,"        ← keepalive only
 *   "1,1235,MOVE_RIGHT,0.0,0.0,"       ← movement (x/y unused server-side, grid-based)
 *   "2,1235,PICKUP_POWERUP,0.0,0.0,SHIELD"
 *   "3,1235,SHOOT,0.0,0.0,"
 * -----------------------------------------------------------------------
 *
 * HEARTBEAT packets only refresh lastSeen — they never touch game state.
 * All other actions call PlayerRegistry.update() with the parsed data.
 */
public class PlayerMonitor implements Runnable {

    public static final  int UDP_PORT       =  PropertyFileReader.getUDPPort();
    private static final int BUFFER_SIZE    = 512;
    private static final int SWEEP_INTERVAL = 10_000;   // ms between dead-player sweeps

    private final PlayerRegistry registry;

    public PlayerMonitor(PlayerRegistry registry) {
        this.registry = registry;
    }

    // -----------------------------------------------------------------------
    // Runnable entry point
    // -----------------------------------------------------------------------

    @Override
    public void run() {
        // Watcher is a daemon — it automatically dies when the JVM exits.
        Thread watcher = new Thread(this::watcherLoop, "PlayerWatcher");
        watcher.setDaemon(true);
        watcher.start();

        receiverLoop(); // blocks until interrupted or socket error
    }

    // -----------------------------------------------------------------------
    // Receiver loop — one thread, blocking UDP receive
    // -----------------------------------------------------------------------

    private void receiverLoop() {
        try (DatagramSocket socket = new DatagramSocket(UDP_PORT)) {
            System.out.printf("[PlayerMonitor] Listening for player packets on UDP port %d%n",
                    UDP_PORT);
            byte[] buffer = new byte[BUFFER_SIZE];

            while (!Thread.currentThread().isInterrupted()) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);                          // blocks here

                String sourceIp = packet.getAddress().getHostAddress();
                String payload  = new String(packet.getData(), 0, packet.getLength()).trim();
                parseAndDispatch(sourceIp, payload);
            }
        } catch (Exception e) {
            System.err.println("[PlayerMonitor] Receiver error: " + e.getMessage());
        }
    }

    // -----------------------------------------------------------------------
    // Packet parser
    // -----------------------------------------------------------------------

    /**
     * Parses one raw UDP payload string and routes it to the registry.
     *
     * Expected: playerId,tcpPort,action,x,y[,extra]
     *   - Fields 0-4 are required.
     *   - Field 5 (extra) is optional; defaults to "".
     *   - HEARTBEAT packets skip the full update path.
     */
    private void parseAndDispatch(String sourceIp, String payload) {
        try {
            // -1 keeps trailing empty strings so "1,5102,HEARTBEAT,0,0," parses correctly
            String[] parts = payload.split(",", -1);

            if (parts.length < 5) {
                System.err.printf("[PlayerMonitor] Malformed packet from %s: '%s'%n",
                        sourceIp, payload);
                return;
            }

            int          playerId = Integer.parseInt(parts[0].trim());
            int          tcpPort  = Integer.parseInt(parts[1].trim());
            PlayerActionEnum action   = PlayerActionEnum.fromString(parts[2].trim());
            float        x        = Float.parseFloat(parts[3].trim());
            float        y        = Float.parseFloat(parts[4].trim());
            String       extra    = (parts.length >= 6) ? parts[5].trim() : "";

            if (action == null) {
                System.err.printf("[PlayerMonitor] Unknown action '%s' from player %d%n",
                        parts[2].trim(), playerId);
                return;
            }

            // HEARTBEAT — only touch lastSeen, skip game-state update
            if (action == PlayerActionEnum.HEARTBEAT) {
                registry.touch(playerId, sourceIp, tcpPort);
                System.out.printf("[PlayerMonitor] Heartbeat from player %d (ip=%s)%n",
                        playerId, sourceIp);
                return;
            }

            // All other actions — full update
            registry.update(playerId, sourceIp, tcpPort, action, x, y, extra);
            System.out.printf("[PlayerMonitor] Updated player %d  action=%s  pos=(%.1f,%.1f)%n",
                    playerId, action, x, y);

        } catch (NumberFormatException e) {
            System.err.printf("[PlayerMonitor] Parse error for '%s': %s%n",
                    payload, e.getMessage());
        }
    }

    // -----------------------------------------------------------------------
    // Watcher loop — runs on a daemon thread
    // -----------------------------------------------------------------------

    private void watcherLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                Thread.sleep(SWEEP_INTERVAL);
                registry.sweepDeadPlayers();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
}