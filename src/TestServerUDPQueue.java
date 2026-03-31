
import java.util.List;

public class TestServerUDPQueue {

    public static void main(String[] args) {
        ServerUDPQueue queue = new ServerUDPQueue();

        System.out.println("=== TEST 1: In-order packets ===");
        queue.enqueue(makeAction(1, "MOVE_UP",   1), 0);
        queue.enqueue(makeAction(1, "MOVE_DOWN", 2), 0);
        queue.enqueue(makeAction(1, "MOVE_LEFT", 3), 0);
        printDrained(queue.drainReady(0));
        queue.reset(); // ★

        System.out.println("\n=== TEST 2: Out-of-order packets ===");
        queue.enqueue(makeAction(1, "MOVE_RIGHT", 2), 1);
        queue.enqueue(makeAction(1, "MOVE_UP",    1), 1);
        printDrained(queue.drainReady(1));
        queue.reset(); // ★

        System.out.println("\n=== TEST 3: Gap with timeout (seq 6 lost) ===");
        queue.enqueue(makeAction(1, "MOVE_DOWN", 2), 2); // seq 1 never arrives
        System.out.println("  tick 2 (gap not timed out yet):");
        printDrained(queue.drainReady(2));
        System.out.println("  tick 22 (gap timed out):");
        printDrained(queue.drainReady(22));
        queue.reset(); // ★

        System.out.println("\n=== TEST 4: Stale duplicate rejected ===");
        queue.enqueue(makeAction(1, "MOVE_UP", 1), 0);
        printDrained(queue.drainReady(0));             // deliver seq=1 first
        queue.enqueue(makeAction(1, "MOVE_LEFT", 1), 5); // now re-enqueue seq=1 as duplicate
        printDrained(queue.drainReady(5));
        queue.reset(); // ★

        System.out.println("\n=== TEST 5: Multiple players independent ===");
        queue.enqueue(makeAction(2, "MOVE_UP",    1), 6);
        queue.enqueue(makeAction(1, "MOVE_RIGHT", 1), 6);
        printDrained(queue.drainReady(6));
    }

    private static PlayerAction makeAction(int playerId, String action, int seq) {
        return new PlayerAction(playerId, action, System.currentTimeMillis(), seq);
    }

    private static void printDrained(List<PlayerAction> actions) {
        if (actions.isEmpty()) {
            System.out.println("  (nothing drained)");
        }
        for (PlayerAction a : actions) {
            System.out.printf("  player=%d  action=%-15s  seq=%d%n",
                    a.playerId, a.action, a.seq);
        }
    }
}