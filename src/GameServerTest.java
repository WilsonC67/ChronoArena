import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;


/**
 * GameServerTest — standalone test harness for the game server.
 */
public class GameServerTest {

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.out.println("Usage:");
            System.out.println("  java -cp . GameServerTest player <id> <port> [serverIp]");
            System.out.println("  java -cp . GameServerTest malformed [serverIp]");
            return;
        }

        switch (args[0].toLowerCase()) {
            case "player"    -> FakePlayer.run(args);
            case "malformed" -> MalformedSender.run(args);
            default          -> System.out.println("Unknown argument. Use 'player' or 'malformed'.");
        }
    }

    // ========================================================================
    // FakePlayer
    //
    // Simulates a UDP-only player client (no TCP/display connection) by:
    //   1. Sending a heartbeat every 5 seconds to PlayerMonitor.
    //   2. Sending a burst of game-action packets once at startup.
    //
    // Usage: java -cp . GameServerTest player <id> [udpPort] [serverIp]
    //   id       — player ID (1-4)
    //   udpPort  — defaults to udp.port in config.properties (1235)
    //   serverIp — defaults to 127.0.0.1
    // ========================================================================
    static class FakePlayer {

        static void run(String[] args) throws Exception {
            int    playerId  = args.length > 1 ? Integer.parseInt(args[1]) : 1;
            int    udpPort   = args.length > 2 ? Integer.parseInt(args[2]) : PlayerMonitor.UDP_PORT;
            String serverIp  = args.length > 3 ? args[3] : "127.0.0.1";

            System.out.printf("[FakePlayer %d] Targeting server at %s:%d%n",
                    playerId, serverIp, udpPort);

            DatagramSocket socket     = new DatagramSocket();
            InetAddress    serverAddr = InetAddress.getByName(serverIp);

            // --- Start heartbeat thread ---
            Thread heartbeat = new Thread(() -> {
                String hb = String.format("%d,%d,HEARTBEAT,0.0,0.0,", playerId, udpPort);
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        send(socket, serverAddr, udpPort, hb);
                        System.out.printf("[FakePlayer %d] Heartbeat sent.%n", playerId);
                        Thread.sleep(5_000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } catch (Exception e) {
                        System.err.printf("[FakePlayer %d] Heartbeat error: %s%n",
                                playerId, e.getMessage());
                    }
                }
            }, "FakeHeartbeat-" + playerId);
            heartbeat.setDaemon(true);
            heartbeat.start();

            // --- Burst of game-action packets ---
            String[][] actions = {
                { String.format("%d,%d,MOVE_RIGHT,0.0,0.0,",    playerId, udpPort) },
                { String.format("%d,%d,MOVE_RIGHT,0.0,0.0,",    playerId, udpPort) },
                { String.format("%d,%d,PICKUP_POWERUP,0.0,0.0,SHIELD", playerId, udpPort) },
                { String.format("%d,%d,SHOOT,0.0,0.0,",         playerId, udpPort) },
                { String.format("%d,%d,IDLE,0.0,0.0,",          playerId, udpPort) },
            };

            for (String[] action : actions) {
                send(socket, serverAddr, udpPort, action[0]);
                System.out.printf("[FakePlayer %d] Sent: %s%n", playerId, action[0]);
                Thread.sleep(600);
            }

            System.out.printf("[FakePlayer %d] Burst done. Heartbeat running. Ctrl+C to stop.%n",
                    playerId);

            // Keep alive so heartbeats keep firing
            Thread.currentThread().join();
        }

        static void send(DatagramSocket socket, InetAddress addr, int port, String msg)
                throws Exception {
            byte[]         data   = msg.getBytes();
            DatagramPacket packet = new DatagramPacket(data, data.length, addr, port);
            socket.send(packet);
        }
    }

    // ========================================================================
    // MalformedSender
    //
    // Sends intentionally broken packets to verify the server's error handling
    // does NOT crash PlayerMonitor.
    //
    // Usage: java -cp . GameServerTest malformed [serverIp]
    //   serverIp — defaults to 127.0.0.1
    // ========================================================================
    static class MalformedSender {

        static void run(String[] args) throws Exception {
            String serverIp = args.length > 1 ? args[1] : "127.0.0.1";
            int    udpPort  = PlayerMonitor.UDP_PORT;

            DatagramSocket socket     = new DatagramSocket();
            InetAddress    serverAddr = InetAddress.getByName(serverIp);

            String[] badPackets = {
                "",                                    // empty
                "notanumber,5102,MOVE_RIGHT,0,0,",     // bad player ID
                "1,5102,FLY,100.0,200.0,",             // unknown action
                "1,5102",                              // too few fields
                "1,5102,MOVE_RIGHT,notafloat,200.0,"   // bad x coordinate
            };

            System.out.printf("[MalformedSender] Firing %d bad packets at %s:%d%n",
                    badPackets.length, serverIp, udpPort);

            for (String pkt : badPackets) {
                byte[]         data   = pkt.getBytes();
                DatagramPacket packet = new DatagramPacket(data, data.length, serverAddr, udpPort);
                socket.send(packet);
                System.out.println("[MalformedSender] Sent: '" + pkt + "'");
                Thread.sleep(300);
            }

            socket.close();
            System.out.println("[MalformedSender] Done. Server should still be running.");
        }
    }
}