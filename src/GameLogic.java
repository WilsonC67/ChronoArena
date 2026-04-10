import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class GameLogic {

    // Beam class for visual effects
    public static class Beam {
        public int x, y, dx, dy, length, ticksLeft;
        public Beam(int x, int y, int dx, int dy, int length, int ticksLeft) {
            this.x = x; this.y = y; this.dx = dx; this.dy = dy;
            this.length = length; this.ticksLeft = ticksLeft;
        }
    }

    public static final int GRID_COLS = PropertyFileReader.getColNum();
    public static final int GRID_ROWS = PropertyFileReader.getRowNum();

    private static final int ITEM_SPAWN_INTERVAL    = 100;
    private static final int MAX_ITEMS              = 6;
    private static final int RESPAWN_DURATION_TICKS = 60;
    private static final int[][] SPAWN_POINTS       = {{1,1},{13,1},{1,10},{13,10}};

    private final Map<Integer, Player> players = new ConcurrentHashMap<>();
    private final Zone[]               zones;
    private final List<Item>           items   = new ArrayList<>();
    private final List<Beam>           beams   = new ArrayList<>();
    private final Random               random  = new Random();

    private int     itemIdCounter        = 0;
    private int     spawnIndex           = 0;
    private boolean gameActive           = false;
    private boolean roundStarted         = false;
    private long    roundEndTimeMs;
    private int     roundDurationSeconds;
    private ServerUDPQueue packetQueue;

    // ── Ready / vote-to-start state ───────────────────────────────────────────
    private final boolean[]          readyState  = new boolean[4]; // index = playerId-1
    private final boolean[]          voteState   = new boolean[4]; // index = playerId-1
    private final boolean[]          restartVoteState = new boolean[4]; // index = playerId-1
    private final Set<Integer>       gamePlayers = new LinkedHashSet<>();
    private Runnable onReadyCallback;    // fires whenever a player readies up
    private Runnable onVoteCallback;     // fires whenever a player votes
    private Runnable onAllReadyCallback; // fires when all connected players have voted
    private Runnable onRestartVoteCallback;    // fires whenever a player votes to restart
    private Runnable onAllRestartCallback;     // fires when all players vote to restart
    private Runnable onRestartDeclinedCallback; // fires when restart vote fails

    public GameLogic(int roundDurationSeconds) {
        this.zones                = Zone.createDefaultZones();
        this.roundDurationSeconds = roundDurationSeconds;
        this.roundEndTimeMs       = System.currentTimeMillis() + roundDurationSeconds * 1000L;
    }

    public void setPacketQueue(ServerUDPQueue pq)  { this.packetQueue       = pq; }
    public void setOnReadyCallback(Runnable cb)    { this.onReadyCallback    = cb; }
    public void setOnVoteCallback(Runnable cb)     { this.onVoteCallback     = cb; }
    public void setOnAllReadyCallback(Runnable cb) { this.onAllReadyCallback = cb; }
    public void setOnRestartVoteCallback(Runnable cb)    { this.onRestartVoteCallback    = cb; }
    public void setOnAllRestartCallback(Runnable cb)     { this.onAllRestartCallback    = cb; }
    public void setOnRestartDeclinedCallback(Runnable cb) { this.onRestartDeclinedCallback = cb; }

    // ── Game loop tick ────────────────────────────────────────────────────────

    public synchronized void processTick(int tick, List<PlayerAction> actions) {
        for (PlayerAction pa : actions) {
            // READY and VOTE_START are lobby actions — process them even before game starts
            if (pa.action.equals("READY") || pa.action.equals("VOTE_START")) {
                if (!players.containsKey(pa.playerId))
                    addPlayer(pa.playerId, "Player " + pa.playerId);
                Player player = players.get(pa.playerId);
                if (player != null) handleAction(player, pa);
                continue;
            }
            if (!gameActive) continue;
            if (!players.containsKey(pa.playerId))
                addPlayer(pa.playerId, "Player " + pa.playerId);
            Player player = players.get(pa.playerId);
            if (player == null || !player.connected || player.killed) continue;
            handleAction(player, pa);
        }

        if (!gameActive) return;

        for (Player p : players.values()) {
            if (p.connected && !p.killed) p.tickEffects();
        }

        for (Player p : players.values()) {
            if (p.respawnTicksLeft > 0) {
                p.respawnTicksLeft--;
                if (p.respawnTicksLeft <= 0) {
                    int[] pos = SPAWN_POINTS[spawnIndex++ % SPAWN_POINTS.length];
                    p.x = pos[0]; p.y = pos[1];
                    p.hp = 100; p.killed = false; p.frozen = false;
                    p.frozenTicksLeft = 0; p.hasWeapon = false;
                    p.hasShield = false; p.speedBoost = false; p.speedBoostTicksLeft = 0;
                    System.out.printf("[GameLogic] Player %d respawned at (%d,%d)%n", p.id, p.x, p.y);
                }
            }
        }

        for (Player p : players.values()) {
            if (p.connected && !p.killed && p.respawnTicksLeft == 0 && p.hp <= 0) {
                p.killed = true;
                p.respawnTicksLeft = RESPAWN_DURATION_TICKS;
                p.score = Math.max(0, p.score - 10);
                System.out.printf("[GameLogic] Player %d died (score now %d)%n", p.id, p.score);
            }
        }

        updateZones();

        for (Item item : items) {
            if (item.active && --item.ticksLeft <= 0) {
                item.active = false;
                System.out.printf("[GameLogic] Item %s despawned%n", item.id);
            }
        }
        if (tick % ITEM_SPAWN_INTERVAL == 0) spawnItem();
        if (tick % Zone.POINTS_INTERVAL  == 0) updateZoneScores();
        beams.removeIf(beam -> --beam.ticksLeft <= 0);

        if (System.currentTimeMillis() >= roundEndTimeMs && roundStarted) {
            gameActive = false;
            System.out.println("[GameLogic] Round over! Winner: " + getWinner());
        }
    }

    // ── Action dispatch ───────────────────────────────────────────────────────

    private void handleAction(Player player, PlayerAction pa) {
        switch (pa.action) {
            case "MOVE_UP":        handleMove(player,  0, -1); break;
            case "MOVE_DOWN":      handleMove(player,  0,  1); break;
            case "MOVE_LEFT":      handleMove(player, -1,  0); break;
            case "MOVE_RIGHT":     handleMove(player,  1,  0); break;
            case "SHOOT":          handleShoot(player, pa.x, pa.y); break;
            case "PICKUP_POWERUP": checkItemCollection(player); break;
            case "USE_ABILITY":    handleDash(player); break;
            case "GAME_START":     startRound(); break;
            case "READY":          handleReady(player); break;
            case "VOTE_START":     handleVote(player); break;
            case "RESTART_VOTE":   handleRestartVote(player); break;
            default: break;
        }
        if (gameActive) checkItemCollection(player);
    }

    private void handleReady(Player player) {
        if (player.id < 1 || player.id > 4) return;
        if (readyState[player.id - 1]) return;
        readyState[player.id - 1] = true;
        System.out.printf("[GameLogic] Player %d is ready.%n", player.id);
        if (onReadyCallback != null) onReadyCallback.run();
    }

    private void handleVote(Player player) {
        if (player.id < 1 || player.id > 4) return;
        if (voteState[player.id - 1]) return;
        voteState[player.id - 1] = true;
        System.out.printf("[GameLogic] Player %d voted to start.%n", player.id);
        if (onVoteCallback != null) onVoteCallback.run();
        boolean allVoted = true;
        for (Player p : players.values()) {
            if (p.connected && !voteState[p.id - 1]) { allVoted = false; break; }
        }
        if (allVoted && getConnectedPlayerCount() >= 2) {
            gamePlayers.clear();
            for (Player p : players.values()) {
                if (p.connected) gamePlayers.add(p.id);
            }
            System.out.println("[GameLogic] Vote passed — locking in: " + gamePlayers);
            if (onAllReadyCallback != null) onAllReadyCallback.run();
        }
    }

    private void handleRestartVote(Player player) {
        if (player.id < 1 || player.id > 4) return;
        if (restartVoteState[player.id - 1]) return;
        restartVoteState[player.id - 1] = true;
        System.out.printf("[GameLogic] Player %d voted to restart.%n", player.id);
        if (onRestartVoteCallback != null) onRestartVoteCallback.run();
    }

    public synchronized boolean[]    getReadyState()  { return readyState.clone(); }
    public synchronized boolean[]    getVoteState()   { return voteState.clone(); }
    public synchronized boolean[]    getRestartVoteState() { return restartVoteState.clone(); }
    public synchronized Set<Integer> getGamePlayers() { return new LinkedHashSet<>(gamePlayers); }

    public synchronized void resetReadyState() {
        Arrays.fill(readyState, false);
        Arrays.fill(voteState,  false);
        Arrays.fill(restartVoteState, false);
        gamePlayers.clear();
    }


    private void handleMove(Player player, int dx, int dy) {
        if (player.frozen) return;
        player.lastDx = dx;
        player.lastDy = dy;
        int steps = player.speedBoost ? 2 : 1;
        for (int s = 0; s < steps; s++) {
            int nx = player.x + dx, ny = player.y + dy;
            if (nx >= 0 && nx < GRID_COLS && ny >= 0 && ny < GRID_ROWS) {
                player.x = nx; player.y = ny;
                checkItemCollection(player);
            }
        }
    }

    private void handleDash(Player player) {
        if (player.frozen || player.dashCooldownTicks > 0) return;
        if (player.lastDx == 0 && player.lastDy == 0) return;
        for (int i = 0; i < Player.DASH_DISTANCE; i++) {
            int nx = player.x + player.lastDx;
            int ny = player.y + player.lastDy;
            if (nx >= 0 && nx < GRID_COLS && ny >= 0 && ny < GRID_ROWS) {
                player.x = nx;
                player.y = ny;
            }
        }
        player.dashCooldownTicks = Player.DASH_COOLDOWN_TICKS;
        System.out.printf("[GameLogic] Player %d dashed to (%d,%d)%n", player.id, player.x, player.y);
    }


    private void handleShoot(Player attacker, float dx, float dy) {
        if (!attacker.hasWeapon || attacker.frozen) return;
        Gun.shoot(attacker, dx, dy, players, GRID_COLS, GRID_ROWS, beams);
    }

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

    // ── Zones ─────────────────────────────────────────────────────────────────

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
            zone.state = Zone.State.UNCLAIMED; zone.capturingId = -1; zone.captureProgress = 0;
        } else if (!capturerPresent) {
            zone.capturingId = others.get(0).id; zone.captureProgress = 0;
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
            zone.captureProgress = 0; zone.ownerId = -1;
        } else {
            zone.graceTicksLeft--;
            if (zone.graceTicksLeft <= 0) {
                System.out.printf("[GameLogic] Zone %s lost (grace expired)%n", zone.name);
                zone.state = Zone.State.UNCLAIMED;
                zone.ownerId = -1; zone.captureProgress = 0; zone.graceTicksLeft = Zone.GRACE_TICKS;
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
            zone.captureProgress = 0; zone.ownerId = -1;
        }
    }

    // ── Items ─────────────────────────────────────────────────────────────────

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

    // ── Player management ─────────────────────────────────────────────────────

    public synchronized void addPlayer(int playerId, String name) {
        Player existing = players.get(playerId);
        if (existing != null) {
            if (existing.connected) return;
            int[] pos = SPAWN_POINTS[spawnIndex++ % SPAWN_POINTS.length];
            players.put(playerId, new Player(playerId, name, pos[0], pos[1]));
            System.out.printf("[GameLogic] Player %d rejoined at (%d,%d)%n", playerId, pos[0], pos[1]);
            return;
        }
        int[] pos = SPAWN_POINTS[spawnIndex++ % SPAWN_POINTS.length];
        players.put(playerId, new Player(playerId, name, pos[0], pos[1]));
        System.out.printf("[GameLogic] Player %d joined at (%d,%d)%n", playerId, pos[0], pos[1]);
    }

    public synchronized int getConnectedPlayerCount() {
        int count = 0;
        for (Player p : players.values()) { if (p.connected) count++; }
        return count;
    }

    public synchronized int getAvailablePlayerId() {
        for (int id = 1; id <= 4; id++) {
            Player p = players.get(id);
            if (p == null || !p.connected) return id;
        }
        return -1;
    }

    public synchronized boolean isPlayerIdAvailable(int playerId) {
        if (playerId < 1 || playerId > 4) return false;
        Player p = players.get(playerId);
        return p == null || !p.connected;
    }

    public synchronized void removePlayer(int playerId) {
        Player p = players.get(playerId);
        if (p == null) return;
        p.connected = false;
        releaseZones(playerId);
        if (packetQueue != null) packetQueue.removePlayer(playerId);
        System.out.printf("[GameLogic] Player %d disconnected.%n", playerId);
    }

    public synchronized void killPlayer(int playerId) {
        Player p = players.get(playerId);
        if (p == null) return;
        p.connected = false; p.killed = true;
        releaseZones(playerId);
        System.out.printf("[GameLogic] KILL_SWITCH: Player %d removed.%n", playerId);
    }

    private void releaseZones(int playerId) {
        for (Zone zone : zones) {
            if (zone.ownerId == playerId) {
                zone.state = Zone.State.UNCLAIMED; zone.ownerId = -1;
                zone.captureProgress = 0; zone.graceTicksLeft = Zone.GRACE_TICKS;
            }
            if (zone.capturingId == playerId) {
                zone.capturingId = -1; zone.captureProgress = 0;
                if (zone.state == Zone.State.CAPTURING) zone.state = Zone.State.UNCLAIMED;
            }
        }
    }

    // ── Game lifecycle ────────────────────────────────────────────────────────

    public void startGame() {
        gameActive = true;
        System.out.println("[GameLogic] Game active, waiting for round start signal.");
    }

    /**
     * Full reset for a new game — clears all scores, items, zones, beams,
     * ready/vote state, and round timer. Call this when returning to lobby.
     * Connected players are kept so the lobby can re-show them immediately.
     */
    public synchronized void resetGame() {
        // Reset round lifecycle
        gameActive    = false;
        roundStarted  = false;
        roundEndTimeMs = System.currentTimeMillis() + roundDurationSeconds * 1000L;

        // Reset all player state but keep them connected
        for (Player p : players.values()) {
            if (!p.connected) continue;
            int[] pos = SPAWN_POINTS[spawnIndex++ % SPAWN_POINTS.length];
            p.x = pos[0]; p.y = pos[1];
            p.hp = 100; p.score = 0;
            p.killed = false; p.frozen = false;
            p.frozenTicksLeft = 0; p.hasWeapon = false;
            p.hasShield = false; p.speedBoost = false;
            p.speedBoostTicksLeft = 0; p.respawnTicksLeft = 0;
            p.dashCooldownTicks = 0; p.lastDx = 0; p.lastDy = 0;
        }

        // Clear world state
        items.clear();
        beams.clear();
        itemIdCounter = 0;
        spawnIndex    = 0;

        // Reset zones
        for (Zone zone : zones) {
            zone.state           = Zone.State.UNCLAIMED;
            zone.ownerId         = -1;
            zone.capturingId     = -1;
            zone.captureProgress = 0;
            zone.graceTicksLeft  = Zone.GRACE_TICKS;
        }

        // Reset lobby state
        resetReadyState();
        System.out.println("[GameLogic] Game fully reset — ready for new lobby.");
    }

    public synchronized void startRound() {
        if (roundStarted) return;
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

    public Map<Integer, Player> getPlayers() { return players; }
    public Zone[]               getZones()   { return zones;   }
    public List<Item>           getItems()   { return items;   }
    public List<Beam>           getBeams()   { return beams;   }
    public boolean              isActive()   { return gameActive; }

    // ── Restart vote timeout ──────────────────────────────────────────────────

    /**
     * Called when the restart vote timeout (15 seconds) expires.
     * Checks if all connected players voted to restart.
     * If yes, triggers onAllRestartCallback. If no, triggers onRestartDeclinedCallback.
     */
    public synchronized void resolveRestartTimeout() {
        boolean allRestartVoted = true;
        for (Player p : players.values()) {
            if (p.connected && !restartVoteState[p.id - 1]) {
                allRestartVoted = false;
                break;
            }
        }
        if (allRestartVoted && getConnectedPlayerCount() >= 1) {
            System.out.println("[GameLogic] Restart vote passed — all players agreed.");
            if (onAllRestartCallback != null) onAllRestartCallback.run();
            Arrays.fill(restartVoteState, false); // Reset for next round
        } else {
            System.out.println("[GameLogic] Restart vote declined — returning to lobby.");
            if (onRestartDeclinedCallback != null) onRestartDeclinedCallback.run();
            Arrays.fill(restartVoteState, false); // Reset for next round
        }
    }
}