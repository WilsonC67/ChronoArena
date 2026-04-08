import java.util.List;
import java.util.Map;

public class Gun {
    private static final int FREEZE_RANGE = 3;

    public static void shoot(Player attacker, float dx, float dy, Map<Integer, Player> players, int GRID_COLS, int GRID_ROWS, List<GameLogic.Beam> beams) {
        if (!attacker.hasWeapon || attacker.frozen) return;

        int dirX = (int) dx;
        int dirY = (int) dy;

        if (dirX == 0 && dirY == 0) return; // No direction, don't shoot

         // Always draw the beam regardless of hit/miss
         beams.add(new GameLogic.Beam(attacker.x, attacker.y, dirX, dirY, FREEZE_RANGE, 5));

        // Shoot a red beam in the direction
        for (int dist = 1; dist <= FREEZE_RANGE; dist++) {
            int nx = attacker.x + dirX * dist;
            int ny = attacker.y + dirY * dist;

            if (nx < 0 || nx >= GRID_COLS || ny < 0 || ny >= GRID_ROWS) break;

            for (Player p : players.values()) {
                if (p.id == attacker.id || !p.connected || p.killed) continue;
                if (p.x == nx && p.y == ny) {
                    // Hit player, apply freeze
                    if (p.hasShield) {
                        p.hasShield = false;
                        System.out.printf("[Gun] Player %d's shield blocked freeze%n", p.id);
                    } else {
                        p.applyFreeze();
                        int pointsLost = p.score / 2;
                        p.score -= pointsLost;
                        attacker.score += pointsLost;
                        System.out.printf("[Gun] Player %d froze player %d with red beam, transferring %d points%n", attacker.id, p.id, pointsLost);
                    }
                    attacker.hasWeapon = false;
                    return;
                }
            }
        }
        attacker.hasWeapon = false;
    }
}
