public class Server {
    public static void main(String[] args) {

        // PlayerRegistry instantiated here. It will keep track of the players.
        System.out.println("Game starting. Awaiting players.");
        PlayerRegistry playerRegistry = new PlayerRegistry();



        PlayerListener playerListener = new PlayerListener(playerRegistry);
        Thread playerThread = new Thread(playerListener, "PlayerListener");
        playerThread.setDaemon(false);
        playerThread.start();
        System.out.println("[Server] PlayerListener started on UDP:" + PlayerListener.UDP_PORT);
    }
}
