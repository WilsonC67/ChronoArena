import java.net.DatagramPacket;
import java.net.DatagramSocket;

/**
 * Listens on UDP port 6002 for player state packets.
 *
 * Expected payload (comma-separated, 6 fields minimum):
 *
 *   playerId , tcpPort , action , x , y , extra
 *   --------   -------   ------   -   -   -----
 *   int        int        enum   flt flt  string (optional, "" if absent)
 *
 * Examples:
 *   "1,5200,MOVE_RIGHT,320.5,112.0,"
 *   "2,5201,PICKUP_POWERUP,88.0,200.0,SHIELD"
 *   "3,5202,SHOOT,150.0,300.0,BULLET_ID:42"
 *
 * Internally mirrors HeartbeatMonitor:
 *   - receiverLoop() — blocking UDP receive, parses every packet
 *   - watcherLoop()  — daemon thread, sweeps silent players every 10 s
 */
public class PlayerListener implements Runnable {

    public static final  int UDP_PORT        = PropertyFileReader.getUDPPort();
    private static final int BUFFER_SIZE     = 512;
    private static final int SWEEP_INTERVAL  = 10_000;

    private final ServerUDPQueue packetQueue;
    private volatile int currentTick = 0; // incremented by GameLogic each tick

    private final PlayerRegistry registry;

    public PlayerListener(PlayerRegistry registry, ServerUDPQueue packetQueue) {
        this.registry    = registry;
        this.packetQueue = packetQueue;
    }

    public void setCurrentTick(int tick) { 
        this.currentTick = tick; 
    }

    @Override
    public void run() {
        // Watcher runs as a daemon so it dies automatically if the main
        // listener thread stops.
        Thread watcher = new Thread(this::watcherLoop, "PlayerWatcher");
        watcher.setDaemon(true);
        watcher.start();

        receiverLoop();
    }


    private void receiverLoop() {
        try (DatagramSocket socket = new DatagramSocket(UDP_PORT)) {
            System.out.printf("[PlayerListener] Listening on UDP port %d%n", UDP_PORT);
            byte[] buffer = new byte[BUFFER_SIZE];

            while (!Thread.currentThread().isInterrupted()) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                String sourceIp = packet.getAddress().getHostAddress();
                String payload  = new String(packet.getData(), 0, packet.getLength()).trim();
                parseAndUpdate(sourceIp, payload);
            }
        } catch (Exception e) {
            System.err.println("[PlayerListener] Receiver error: " + e.getMessage());
        }
    }

    // -------------------------------------------------------- parseAndUpdate

    /**
     * Parses a raw UDP payload and pushes the result into the registry.
     *
     * Format: playerId,tcpPort,action,x,y[,extra]
     *   - fields 0-4 are required
     *   - field 5 (extra) is optional; defaults to ""
     */
    private void parseAndUpdate(String sourceIp, String payload) {
        try {
            String[] parts = payload.split(",", -1);
            if (parts.length < 5) {
                System.err.println("Malformed packet: " + payload);
                return;
            }

            int              playerId = Integer.parseInt(parts[0].trim());
            int              tcpPort  = Integer.parseInt(parts[1].trim());
            PlayerActionEnum action   = PlayerActionEnum.fromString(parts[2].trim());
            float            x        = Float.parseFloat(parts[3].trim());
            float            y        = Float.parseFloat(parts[4].trim());
            String           extra    = (parts.length >= 6) ? parts[5].trim() : "";
            // seq is parts[6] if present, else 0
            int seq = (parts.length >= 7) ? Integer.parseInt(parts[6].trim()) : 0;

            if (action == null) {
                System.err.printf("Unknown action '%s' from player %d%n",
                        parts[2], playerId);
                return;
            }

            // Register the player on first contact so the registry always has them
            registry.register(playerId, sourceIp, tcpPort);

            PlayerAction pa = new PlayerAction(playerId, action.name(),
                    System.currentTimeMillis(), seq);
            packetQueue.enqueue(pa, currentTick);

        } catch (NumberFormatException e) {
            System.err.println("Parse error: " + e.getMessage());
        }
    }

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