import java.net.*;

public class TestPlayerClient {

    // hard-coded for now.to-do: change server IP
    private static final String SERVER_IP = "127.0.0.1";
    private static final int    UDP_PORT  = 6002;

    public static void main(String[] args) throws Exception {
        try (DatagramSocket socket = new DatagramSocket()) {
            InetAddress    server = InetAddress.getByName(SERVER_IP);
            
            // Simulates 3 players sending a burst of packets
            String[] packets = {
                "1,5200,MOVE_RIGHT,100.0,200.0,",
                "1,5200,MOVE_RIGHT,105.0,200.0,",
                "1,5200,PICKUP_POWERUP,110.0,200.0,SHIELD",
                "2,5201,MOVE_UP,300.0,150.0,",
                "2,5201,SHOOT,300.0,145.0,BULLET_ID:7",
                "3,5202,IDLE,50.0,50.0,",
            };
            
            for (String payload : packets) {
                byte[] data   = payload.getBytes();
                DatagramPacket packet = new DatagramPacket(data, data.length, server, UDP_PORT);
                socket.send(packet);
                System.out.println("[TestClient] Sent: " + payload);
                Thread.sleep(500); // space them out so you can read the server logs
            }
        }
        System.out.println("Testing complete.");
    }
}