import java.awt.event.KeyEvent;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;

import javax.swing.SwingUtilities;


public class GameClient {
    private static final int UDP_PORT = PropertyFileReader.getUDPPort();
    private static final int TCP_PORT = PropertyFileReader.getTCPPort();
    private static final String SERVER_IP = PropertyFileReader.getIP();
    private DataOutputStream dataOutputStream;
    private DataInputStream dataInputStream;
    private byte[] outgoingData = new byte[1024];
    private Socket socket;
    private DatagramSocket datagramSocket;

    public GameClient(Socket socket, DatagramSocket datagramSocket){
        try {
            this.socket = socket;
            this.datagramSocket = datagramSocket;
            this.dataInputStream = new DataInputStream(socket.getInputStream());
            this.dataOutputStream = new DataOutputStream(socket.getOutputStream());
        } catch (Exception e) {
            System.out.println("ERROR INITIALIZING CLIENT");
            e.printStackTrace();
        }
    }

    //sends a UDP message to the server
    public void sendUDP(String message, InetAddress IP, int port) throws IOException{
        outgoingData = message.getBytes();
        DatagramPacket outgoingPacket = new DatagramPacket(outgoingData, outgoingData.length, IP, port);
        datagramSocket.send(outgoingPacket);
    }

    public void recieveTCPMessages(){
        new Thread(new Runnable(){
            @Override
            public void run(){
                String msgFromServer;

                try {
                    while(socket.isConnected()){
                    msgFromServer = dataInputStream.readUTF();

                    //used for testing what TCP messages are recieved
                    //can delete later
                    System.out.println(msgFromServer);
                }
                } catch (IOException e) {
                    System.out.println("ERROR IN RECIEVING TCP MESSAGE FROM SERVER");
                    e.printStackTrace();
                }
            }
        }).start();
    }

    public static void main(String[] args) {
        try {
            Socket socket = new Socket(SERVER_IP, TCP_PORT);
            DatagramSocket datagramSocket = new DatagramSocket();
        
            GameClient gameClient = new GameClient(socket, datagramSocket);
            
            //sends a message to the server that a new player has connected
            gameClient.dataOutputStream.writeUTF("PLAYER_CONNECT");
            gameClient.dataOutputStream.flush();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
