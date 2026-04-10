/**
 * Smoke tests for PlayerAction, Player, Zone, and Item.
 * Run with: javac TestModels.java && java TestModels
 */

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

public class TestModels {

    static int passed = 0;
    static int failed = 0;

    public static void main(String[] args) {
        testPlayerAction();
        testPlayerBasics();
        testAcceptSeq();
        testApplyFreeze();
        testFreezeScoreFloor();
        testApplySpeedBoost();
        testTickEffectsFreeze();
        testTickEffectsSpeedBoost();
        testShieldField();
        testZoneBasics();
        testZoneContainsPosition();
        testZoneDefaultZones();
        testContestedResolvesToLowestScoreCapture();
        testCapturedZoneRemainsControlledWhenChallengerStillPresent();
        testItemBasics();

        System.out.println("\n==============================");
        System.out.println("PASSED: " + passed + " | FAILED: " + failed);
        System.out.println("==============================");
    }

    // ---- PlayerAction ----

    static void testPlayerAction() {
        PlayerAction a = new PlayerAction(1, "MOVE_UP", 0.0f, 0.0f, 12345L, 7);
        check("PlayerAction stores playerId",  a.playerId == 1);
        check("PlayerAction stores action",    a.action.equals("MOVE_UP"));
        check("PlayerAction stores x",         a.x == 0.0f);
        check("PlayerAction stores y",         a.y == 0.0f);
        check("PlayerAction stores timestamp", a.timestamp == 12345L);
        check("PlayerAction stores seq",       a.seq == 7);
        check("PlayerAction toString has id",  a.toString().contains("1"));
    }

    // ---- Player basics ----

    static void testPlayerBasics() {
        Player p = new Player(2, "Alice", 3, 5);
        check("Player id set",         p.id == 2);
        check("Player name set",       p.name.equals("Alice"));
        check("Player x set",          p.x == 3);
        check("Player y set",          p.y == 5);
        check("Player score starts 0", p.score == 0);
        check("Player connected",      p.connected);
        check("Player not frozen",     !p.frozen);
        check("Player no weapon",      !p.hasWeapon);
        check("Player no shield",      !p.hasShield);
        check("Player no speed boost", !p.speedBoost);
        check("Player not killed",     !p.killed);
    }

    // ---- acceptSeq ----

    static void testAcceptSeq() {
        Player p = new Player(3, "Bob", 1, 1);
        check("seq=0 always accepted",       p.acceptSeq(0));
        check("seq=1 accepted first time",   p.acceptSeq(1));
        check("seq=1 duplicate rejected",    !p.acceptSeq(1));
        check("seq=2 accepted newer",        p.acceptSeq(2));
        check("seq=2 out-of-order rejected", !p.acceptSeq(2));
        check("negative seq accepted",       p.acceptSeq(-1));
    }

    // ---- applyFreeze ----

    static void testApplyFreeze() {
        Player p = new Player(4, "Carol", 1, 1);
        p.score = 50;
        p.applyFreeze();
        check("applyFreeze sets frozen",           p.frozen);
        check("applyFreeze sets frozenTicksLeft",  p.frozenTicksLeft == Player.FREEZE_DURATION_TICKS);
        check("applyFreeze deducts 10 points",     p.score == 40);
    }

    static void testFreezeScoreFloor() {
        Player p = new Player(5, "Dan", 1, 1);
        p.score = 5;
        p.applyFreeze();
        check("Freeze score cannot go below 0", p.score == 0);
    }

    // ---- applySpeedBoost ----

    static void testApplySpeedBoost() {
        Player p = new Player(6, "Eve", 1, 1);
        p.applySpeedBoost();
        check("speedBoost flag set",              p.speedBoost);
        check("speedBoostTicksLeft set to 100",   p.speedBoostTicksLeft == Player.SPEED_BOOST_DURATION_TICKS);
    }

    // ---- tickEffects ----

    static void testTickEffectsFreeze() {
        Player p = new Player(7, "Frank", 1, 1);
        p.applyFreeze();
        for (int i = 0; i < Player.FREEZE_DURATION_TICKS - 1; i++) p.tickEffects();
        check("Still frozen with 1 tick left", p.frozen);
        p.tickEffects();
        check("Freeze cleared after duration",  !p.frozen);
        check("frozenTicksLeft reset to 0",     p.frozenTicksLeft == 0);
    }

    static void testTickEffectsSpeedBoost() {
        Player p = new Player(8, "Grace", 1, 1);
        p.applySpeedBoost();
        for (int i = 0; i < Player.SPEED_BOOST_DURATION_TICKS - 1; i++) p.tickEffects();
        check("Still boosted with 1 tick left", p.speedBoost);
        p.tickEffects();
        check("Speed boost cleared after duration", !p.speedBoost);
        check("speedBoostTicksLeft reset to 0",     p.speedBoostTicksLeft == 0);
    }

    // ---- Shield ----

    static void testShieldField() {
        Player p = new Player(9, "Hank", 1, 1);
        check("Shield starts false", !p.hasShield);
        p.hasShield = true;
        check("Shield can be set true", p.hasShield);
        p.hasShield = false;
        check("Shield can be cleared", !p.hasShield);
    }

    // ---- Zone basics ----

    static void testZoneBasics() {
        Zone z = new Zone("zone_a", "ZONE A", 2, 5, 4, 3);
        check("Zone id set",              z.id.equals("zone_a"));
        check("Zone name set",            z.name.equals("ZONE A"));
        check("Zone starts UNCLAIMED",    z.state == Zone.State.UNCLAIMED);
        check("Zone ownerId starts -1",   z.ownerId == -1);
        check("Zone capturingId starts -1", z.capturingId == -1);
        check("Zone captureProgress is 0",  z.captureProgress == 0);
    }

    static void testZoneContainsPosition() {
        Zone z = new Zone("zone_b", "ZONE B", 7, 1, 4, 3);
        check("Inside zone (7,1)",   z.containsPosition(7, 1));
        check("Inside zone (10,3)",  z.containsPosition(10, 3));
        check("Outside zone (6,1)",  !z.containsPosition(6, 1));
        check("Outside zone (11,1)", !z.containsPosition(11, 1));
        check("Outside zone (7,0)",  !z.containsPosition(7, 0));
        check("Outside zone (7,4)",  !z.containsPosition(7, 4));
    }

    static void testZoneDefaultZones() {
        Zone[] zones = Zone.createDefaultZones();
        check("3 default zones created",       zones.length == 3);
        check("Zone A id correct",             zones[0].id.equals("zone_a"));
        check("Zone B col correct",            zones[1].col == 7);
        check("Zone C starts UNCLAIMED",       zones[2].state == Zone.State.UNCLAIMED);
    }

    static void testContestedResolvesToLowestScoreCapture() {
        try {
            GameLogic logic = new GameLogic(30);
            logic.addPlayer(1, "Alice");
            logic.addPlayer(2, "Bob");

            Field playersField = GameLogic.class.getDeclaredField("players");
            playersField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<Integer, Player> players = (Map<Integer, Player>) playersField.get(logic);

            Player alice = players.get(1);
            Player bob = players.get(2);
            Zone zone = logic.getZones()[0];

            alice.x = zone.col;
            alice.y = zone.row;
            alice.score = 10;
            bob.x = zone.col;
            bob.y = zone.row;
            bob.score = 5;

            Method updateZones = GameLogic.class.getDeclaredMethod("updateZones");
            updateZones.setAccessible(true);

            // begin contested state
            updateZones.invoke(logic);
            for (int i = 0; i < Zone.CONTEST_TICKS; i++) {
                updateZones.invoke(logic);
            }

            check("Contested resolves to CAPTURING after delay", zone.state == Zone.State.CAPTURING);
            check("Lowest-score player starts capture", zone.capturingId == 2);

            updateZones.invoke(logic);
            check("Capture remains active after contested resolution", zone.state == Zone.State.CAPTURING);
        } catch (Exception ex) {
            ex.printStackTrace();
            check("Contested resolution test ran without exception", false);
        }
    }

    static void testCapturedZoneRemainsControlledWhenChallengerStillPresent() {
        try {
            GameLogic logic = new GameLogic(30);
            logic.addPlayer(1, "Alice");
            logic.addPlayer(2, "Bob");

            Field playersField = GameLogic.class.getDeclaredField("players");
            playersField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<Integer, Player> players = (Map<Integer, Player>) playersField.get(logic);

            Player alice = players.get(1);
            Player bob = players.get(2);
            Zone zone = logic.getZones()[0];

            alice.x = zone.col;
            alice.y = zone.row;
            alice.score = 5;
            bob.x = zone.col;
            bob.y = zone.row;
            bob.score = 10;

            Method updateZones = GameLogic.class.getDeclaredMethod("updateZones");
            updateZones.setAccessible(true);

            updateZones.invoke(logic);
            for (int i = 0; i < Zone.CONTEST_TICKS; i++) updateZones.invoke(logic);
            for (int i = 0; i < Zone.CAPTURE_TICKS; i++) updateZones.invoke(logic);

            check("Zone becomes CONTROLLED after contested capture", zone.state == Zone.State.CONTROLLED);
            updateZones.invoke(logic);
            check("Captured zone remains CONTROLLED when challenger still present", zone.state == Zone.State.CONTROLLED);
        } catch (Exception ex) {
            ex.printStackTrace();
            check("Captured zone control test ran without exception", false);
        }
    }

    // ---- Item basics ----

    static void testItemBasics() {
        Item energy = new Item("i1", Item.Type.ENERGY, 3, 4, 20);
        check("Item id set",        energy.id.equals("i1"));
        check("Item type ENERGY",   energy.type == Item.Type.ENERGY);
        check("Item x set",         energy.x == 3);
        check("Item y set",         energy.y == 4);
        check("Item value set",     energy.value == 20);
        check("Item starts active", energy.active);

        Item gun = new Item("i2", Item.Type.GUN, 0, 0, 0);
        check("GUN value is 0",     gun.value == 0);
        check("GUN active",         gun.active);

        Item shield = new Item("i3", Item.Type.SHIELD, 1, 1, 0);
        check("SHIELD type correct", shield.type == Item.Type.SHIELD);

        Item speed = new Item("i4", Item.Type.SPEED_BOOST, 2, 2, 0);
        check("SPEED_BOOST type correct", speed.type == Item.Type.SPEED_BOOST);
    }

    // ---- Helper ----

    static void check(String label, boolean condition) {
        if (condition) {
            System.out.println("  PASS: " + label);
            passed++;
        } else {
            System.out.println("  FAIL: " + label);
            failed++;
        }
    }
}
