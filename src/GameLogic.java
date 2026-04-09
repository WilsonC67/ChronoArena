import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class GameLogic {

    // Beam class for visual effects
    public static class Beam {
        public int x, y, dx, dy, length, ticksLeft;
        public Beam(int x, int y, int dx, int dy, int length, int ticksLeft) {
            this.x = x;
            this.y = y;
            this.dx = dx;
            this.dy = dy;
            this.length = length;
            this.ticksLeft = ticksLeft;
        }
    }

    public static final int GRID_COLS = PropertyFileReader.getColNum();
    public static final int GRID_ROWS = PropertyFileReader.getRowNum();


    private static final int ITEM_SPAWN_INTERVAL = 100; // every 5 sec at 20 ticks/sec
    private static final int MAX_ITEMS           = 6;
    private static final int RESPAWN_DURATION_TICKS = 60;  // 3 seconds at 20 ticks/sec
    private static final int[][] SPAWN_POINTS    = {{1,1},{13,1},{1,10},{13,10}};

    private final Map<Integer, Player> players = new ConcurrentHashMap<>();
    private final Zone[]               zones;
    private final List<Item>           items   = new ArrayList<>();
    private final List<Beam>           beams   = new ArrayList<>();
    private final Random               random  = new Random();

    private int     itemIdCounter      = 0;
    private int     spawnIndex         = 0;
    private boolean gameActive         = false;
    private boolean roundStarted       = false; // true only after all players ready
    private long    roundEndTimeMs;
    private int     roundDurationSeconds;
    private ServerUDPQueue packetQueue;

    public GameLogic(int roundDurationSeconds) {
        this.zones               = Zone.createDefaultZones();
        this.roundDurationSeconds = roundDurationSeconds;
        this.roundEndTimeMs      = System.currentTimeMillis() + roundDurationSeconds * 1000L;
    }

    public void setPacketQueue(ServerUDPQueue pq) { this.packetQueue = pq; }

    // called by GamePanel every tick
    public synchronized void processTick(int tick, List<PlayerAction> actions) {
        if (!gameActive) return;

        for (PlayerAction pa : actions) {
            if (!players.containsKey(pa.playerId))
                addPlayer(pa.playerId, "Player " + pa.playerId);
            Player player = players.get(pa.playerId);
            if (player == null || !player.connected || player.killed) continue;
            handleAction(player, pa);

        }

        for (Player p : players.values()) {
            if (p.connected && !p.killed) p.tickEffects();
        }

        // Handle respawn countdown
        for (Player p : players.values()) {
            if (p.respawnTicksLeft > 0) {
                p.respawnTicksLeft--;
                if (p.respawnTicksLeft <= 0) {
                    // Respawn the player
                    int[] pos = SPAWN_POINTS[spawnIndex++ % SPAWN_POINTS.length];
                    p.x = pos[0];
                    p.y = pos[1];
                    p.hp = 100;
                    p.killed = false;
                    p.frozen = false;
                    p.frozenTicksLeft = 0;
                    p.hasWeapon = false;
                    p.hasShield = false;
                    p.speedBoost = false;
                    p.speedBoostTicksLeft = 0;
                    System.out.printf("[GameLogic] Player %d respawned at (%d,%d)%n", p.id, p.x, p.y);
                }
            }
        }

        // Check for deaths (health <= 0 and not currently respawning)
        for (Player p : players.values()) {
            if (p.connected && !p.killed && p.respawnTicksLeft == 0 && p.hp <= 0) {
                p.killed = true;
                p.respawnTicksLeft = RESPAWN_DURATION_TICKS;
                p.score = Math.max(0, p.score - 10);
                System.out.printf("[GameLogic] Player %d died and lost 10 points (score now %d)%n", p.id, p.score);
            }
        }

        updateZones();

        if (tick % ITEM_SPAWN_INTERVAL == 0) spawnItem();
        if (tick % Zone.POINTS_INTERVAL  == 0) updateZoneScores();

        // Update beams
        beams.removeIf(beam -> --beam.ticksLeft <= 0);

        if (System.currentTimeMillis() >= roundEndTimeMs && roundStarted) {
            gameActive = false;
            System.out.println("[GameLogic] Round over! Winner: " + getWinner());
        }
    }

    private void handleAction(Player player, PlayerAction pa) {
        switch (pa.action) {
            case "MOVE_UP":        handleMove(player,  0, -1); break;
            case "MOVE_DOWN":      handleMove(player,  0,  1); break;
            case "MOVE_LEFT":      handleMove(player, -1,  0); break;
            case "MOVE_RIGHT":     handleMove(player,  1,  0); break;
            case "SHOOT":          handleShoot(player, pa.x, pa.y);        break;
            case "PICKUP_POWERUP": checkItemCollection(player);break;
            case "GAME_START":     startRound(); break;
            default: break;
        }
        checkItemCollection(player);
    }

    // frozen players can't move; speed boost = 2 tiles per step
    private void handleMove(Player player, int dx, int dy) {
        if (player.frozen) return;
        int steps = player.speedBoost ? 2 : 1;
        for (int s = 0; s < steps; s++) {
            int nx = player.x + dx;
            int ny = player.y + dy;
            if (nx >= 0 && nx < GRID_COLS && ny >= 0 && ny < GRID_ROWS) {
                player.x = nx;
                player.y = ny;
            }
        }
    }

    // freeze nearest player within range; shield blocks the hit
    private void handleShoot(Player attacker, float dx, float dy) {
        if (!attacker.hasWeapon || attacker.frozen) return;

        // Use Gun.shoot
        Gun.shoot(attacker, dx, dy, players, GRID_COLS, GRID_ROWS, beams);
    }

    // lower player ID wins if two players are on the same item tile
    private void checkItemCollection(Player player) {
        for (Item item : items) {
            if (!item.active || item.x != player.x || item.y != player.y) continue;
            boolean wins = true;
            for (Player other : players.values()) {
                if (other.id != player.id && other.connected && !other.killed
                        && other.x == item.x && other.y == item.y && other.id < player.id) {
                    wins = false; break;
                }
            }
            if (!wins) continue;
            item.active = false;
            applyItem(player, item);
        }
    }

    private void applyItem(Player player, Item item) {
        switch (item.type) {
            case ENERGY:      player.score += item.value; break;
            case GUN:         player.hasWeapon = true;    break;
            case SHIELD:      player.hasShield = true;    break;
            case SPEED_BOOST: player.applySpeedBoost();   break;
        }
        System.out.printf("[GameLogic] Player %d picked up %s%n", player.id, item.type);
    }

    private void updateZones() {
        for (Zone zone : zones) {
            List<Player> inZone = getPlayersInZone(zone);
            switch (zone.state) {
                case UNCLAIMED:  handleUnclaimed(zone, inZone);  break;
                case CAPTURING:  handleCapturing(zone, inZone);  break;
                case CONTROLLED: handleControlled(zone, inZone); break;
                case CONTESTED:  handleContested(zone, inZone);  break;
            }
        }
    }

    private List<Player> getPlayersInZone(Zone zone) {
        List<Player> inZone = new ArrayList<>();
        for (Player p : players.values()) {
            if (p.connected && !p.killed && zone.containsPosition(p.x, p.y)) inZone.add(p);
        }
        return inZone;
    }

    private void handleUnclaimed(Zone zone, List<Player> inZone) {
        if (inZone.isEmpty()) return;
        if (inZone.size() == 1) {
            zone.state = Zone.State.CAPTURING;
            zone.capturingId = inZone.get(0).id;
            zone.captureProgress = 1;
        } else {
            zone.state = Zone.State.CONTESTED;
        }
    }

    private void handleCapturing(Zone zone, List<Player> inZone) {
        boolean capturerPresent = inZone.stream().anyMatch(p -> p.id == zone.capturingId);
        List<Player> others = new ArrayList<>();
        for (Player p : inZone) { if (p.id != zone.capturingId) others.add(p); }

        if (!capturerPresent && inZone.isEmpty()) {
            zone.state = Zone.State.UNCLAIMED;
            zone.capturingId = -1;
            zone.captureProgress = 0;
        } else if (!capturerPresent) {
            zone.capturingId = others.get(0).id;
            zone.captureProgress = 0;
        } else if (!others.isEmpty()) {
            zone.state = Zone.State.CONTESTED;
        } else {
            zone.captureProgress++;
            if (zone.captureProgress >= Zone.CAPTURE_TICKS) {
                zone.state = Zone.State.CONTROLLED;
                zone.ownerId = zone.capturingId;
                zone.graceTicksLeft = Zone.GRACE_TICKS;
                System.out.printf("[GameLogic] Zone %s captured by player %d%n", zone.name, zone.ownerId);
            }
        }
    }

    private void handleControlled(Zone zone, List<Player> inZone) {
        boolean ownerPresent = inZone.stream().anyMatch(p -> p.id == zone.ownerId);
        List<Player> challengers = new ArrayList<>();
        for (Player p : inZone) { if (p.id != zone.ownerId) challengers.add(p); }

        if (ownerPresent) {
            zone.graceTicksLeft = Zone.GRACE_TICKS;
            if (!challengers.isEmpty()) zone.state = Zone.State.CONTESTED;
        } else if (!challengers.isEmpty()) {
            zone.state = Zone.State.CAPTURING;
            zone.capturingId = challengers.get(0).id;
            zone.captureProgress = 0;
            zone.ownerId = -1;
        } else {
            zone.graceTicksLeft--;
            if (zone.graceTicksLeft <= 0) {
                System.out.printf("[GameLogic] Zone %s lost by player %d (grace expired)%n", zone.name, zone.ownerId);
                zone.state = Zone.State.UNCLAIMED;
                zone.ownerId = -1;
                zone.captureProgress = 0;
                zone.graceTicksLeft = Zone.GRACE_TICKS;
            }
        }
    }

    private void handleContested(Zone zone, List<Player> inZone) {
        if (inZone.size() > 1) return;
        if (inZone.isEmpty()) {
            zone.state = Zone.State.UNCLAIMED;
            zone.ownerId = -1; zone.capturingId = -1; zone.captureProgress = 0;
        } else {
            zone.state = Zone.State.CAPTURING;
            zone.capturingId = inZone.get(0).id;
            zone.captureProgress = 0;
            zone.ownerId = -1;
        }
    }

    private void spawnItem() {
        if (items.stream().filter(i -> i.active).count() >= MAX_ITEMS) return;
        for (int attempts = 0; attempts < 20; attempts++) {
            int x = 1 + random.nextInt(GRID_COLS - 2);
            int y = 1 + random.nextInt(GRID_ROWS - 2);
            if (isTileOccupiedByItem(x, y)) continue;

            int roll = random.nextInt(6);
            Item.Type type = roll < 2 ? Item.Type.ENERGY : roll < 4 ? Item.Type.GUN
                           : roll == 4 ? Item.Type.SHIELD : Item.Type.SPEED_BOOST;
            int value = type == Item.Type.ENERGY
                    ? Item.ENERGY_MIN + random.nextInt(Item.ENERGY_MAX - Item.ENERGY_MIN + 1) : 0;

            items.add(new Item("item_" + (++itemIdCounter), type, x, y, value));
            System.out.printf("[GameLogic] Spawned %s at (%d,%d)%n", type, x, y);
            break;
        }
    }

    private boolean isTileOccupiedByItem(int x, int y) {
        for (Item item : items) { if (item.active && item.x == x && item.y == y) return true; }
        return false;
    }

    private void updateZoneScores() {
        for (Zone zone : zones) {
            if (zone.state != Zone.State.CONTROLLED || zone.ownerId == -1) continue;
            Player owner = players.get(zone.ownerId);
            if (owner != null && owner.connected && !owner.killed) owner.score += Zone.POINTS_PER_INTERVAL;
        }
    }

    public synchronized void addPlayer(int playerId, String name) {
        if (players.containsKey(playerId)) {
            players.get(playerId).connected = true;
            return;
        }
        int[] pos = SPAWN_POINTS[spawnIndex++ % SPAWN_POINTS.length];
        players.put(playerId, new Player(playerId, name, pos[0], pos[1]));
        System.out.printf("[GameLogic] Player %d (%s) joined at (%d,%d)%n", playerId, name, pos[0], pos[1]);
    }

    public synchronized void removePlayer(int playerId) {
        Player p = players.get(playerId);
        if (p == null) return;
        p.connected = false;
        releaseZones(playerId);
        if (packetQueue != null) packetQueue.removePlayer(playerId);
        System.out.printf("[GameLogic] Player %d disconnected.%n", playerId);
    }

    // permanently boots a misbehaving client
    public synchronized void killPlayer(int playerId) {
        Player p = players.get(playerId);
        if (p == null) return;
        p.connected = false;
        p.killed = true;
        releaseZones(playerId);
        System.out.printf("[GameLogic] KILL_SWITCH: Player %d removed.%n", playerId);
    }

    private void releaseZones(int playerId) {
        for (Zone zone : zones) {
            if (zone.ownerId == playerId) {
                zone.state = Zone.State.UNCLAIMED;
                zone.ownerId = -1; zone.captureProgress = 0; zone.graceTicksLeft = Zone.GRACE_TICKS;
            }
            if (zone.capturingId == playerId) {
                zone.capturingId = -1; zone.captureProgress = 0;
                if (zone.state == Zone.State.CAPTURING) zone.state = Zone.State.UNCLAIMED;
            }
        }
    }

    public void startGame() {
        gameActive = true;
        // roundEndTimeMs is set in startRound() when the client GAME_START packet arrives
        System.out.println("[GameLogic] Game active, waiting for round start signal.");
    }

    /** Called when all players are ready and the lobby countdown finishes. */
    public synchronized void startRound() {
        if (roundStarted) return; // only trigger once
        roundStarted   = true;
        roundEndTimeMs = System.currentTimeMillis() + roundDurationSeconds * 1000L;
        System.out.println("[GameLogic] Round timer started!");
    }

    public long getTimeRemainingMs() {
        if (!roundStarted) return roundDurationSeconds * 1000L;
        return Math.max(0, roundEndTimeMs - System.currentTimeMillis());
    }

    public String getWinner() {
        return players.values().stream()
                .filter(p -> !p.killed)
                .max(Comparator.comparingInt(p -> p.score))
                .map(p -> String.format("Player %d (%s) with %d pts", p.id, p.name, p.score))
                .orElse("No winner");
    }

    public Map<Integer, Player> getPlayers()         { return players; }
    public Zone[]               getZones()           { return zones;   }
    public List<Item>           getItems()           { return items;   }
    public List<Beam>           getBeams()           { return beams;   }
    public boolean              isActive()           { return gameActive; }
}