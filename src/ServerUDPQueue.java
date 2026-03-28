import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;


 //A packet is "ready" when its seq == nextExpected, OR when it has
//waited longer than MAX_WAIT_TICKS (stale-gap recovery).
public class ServerUDPQueue {

    // Drop gaps older than this many ticks without filling
    private static final int MAX_WAIT_TICKS = 20;

    /** Wraps a PlayerAction with arrival metadata for the priority queue. */
    public static class PendingPacket implements Comparable<PendingPacket> {
        public final PlayerAction action;
        public final int          tickArrived;

        public PendingPacket(PlayerAction action, int tickArrived) {
            this.action      = action;
            this.tickArrived = tickArrived;
        }

        @Override
        public int compareTo(PendingPacket other) {
            return Integer.compare(this.action.seq, other.action.seq);
        }
    }

    // One queue per player, keyed by playerId
    private final Map<Integer, PriorityQueue<PendingPacket>> queues      = new HashMap<>();
    private final Map<Integer, Integer>                       nextExpected = new HashMap<>();

    /** Called by PlayerListener immediately after parsing a UDP packet. */
    public synchronized void enqueue(PlayerAction action, int currentTick) {
        int pid = action.playerId;
        queues.computeIfAbsent(pid, k -> new PriorityQueue<>());
        nextExpected.putIfAbsent(pid, 1);

        // Discard packets older than what we've already delivered
        if (action.seq > 0 && action.seq < nextExpected.get(pid)) {
            System.out.printf("[PacketQueue] Discarding stale seq=%d for player %d%n",
                    action.seq, pid);
            return;
        }

        queues.get(pid).add(new PendingPacket(action, currentTick));
    }

    /**
     * Called once per game tick by GameLogic.
     * Drains every packet that is ready (in-order or gap-timed-out)
     * and returns them for processing.
     */
    public synchronized java.util.List<PlayerAction> drainReady(int currentTick) {
        java.util.List<PlayerAction> ready = new java.util.ArrayList<>();

        for (int pid : queues.keySet()) {
            PriorityQueue<PendingPacket> q = queues.get(pid);

            while (!q.isEmpty()) {
                PendingPacket head = q.peek();
                int expected = nextExpected.getOrDefault(pid, 1);

                boolean inOrder   = head.action.seq == expected;
                boolean gapTimedOut = (currentTick - head.tickArrived) >= MAX_WAIT_TICKS;
                boolean unsequenced = head.action.seq <= 0; // seq=0 means no tracking

                if (inOrder || gapTimedOut || unsequenced) {
                    q.poll();
                    ready.add(head.action);

                    // Advance expected pointer — skip over the gap if we timed out
                    if (head.action.seq > 0) {
                        nextExpected.put(pid, head.action.seq + 1);
                    }
                } else {
                    break; // head isn't ready yet — nothing behind it will be either
                }
            }
        }

        return ready;
    }

    /** Call when a player disconnects to free memory. */
    public synchronized void removePlayer(int playerId) {
        queues.remove(playerId);
        nextExpected.remove(playerId);
    }

    public synchronized void reset() {
        queues.clear();
        nextExpected.clear();
    }
}