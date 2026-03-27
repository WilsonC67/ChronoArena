import java.io.Serializable;

/**
 * Represents a spawnable item on the arena map.
 * Items appear at random tile positions and are collected by stepping on them.
 * Logic for spawning and collection is handled in GameLogic.
 */
public class Item implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum Type {
        ENERGY,      // collectible — awards points on pickup
        GUN,         // weapon — lets player freeze nearby opponents once
        SHIELD,      // defense — absorbs one incoming freeze attack
        SPEED_BOOST  // movement — player moves 2 tiles per keypress temporarily
    }

    // Point range for ENERGY items
    public static final int ENERGY_MIN = 10;
    public static final int ENERGY_MAX = 30;

    // Identity
    public final String id;
    public final Type type;

    // Tile position on the grid
    public int x;
    public int y;

    // Whether this item is still on the map (false = already collected)
    public boolean active;

    // Points awarded on pickup — only meaningful for ENERGY, 0 for all others
    public final int value;

    public Item(String id, Type type, int x, int y, int value) {
        this.id     = id;
        this.type   = type;
        this.x      = x;
        this.y      = y;
        this.value  = value;
        this.active = true;
    }

    @Override
    public String toString() {
        return "Item[" + id + " type=" + type + " pos=(" + x + "," + y + ")"
             + (type == Type.ENERGY ? " value=" + value : "")
             + (active ? " ACTIVE" : " COLLECTED") + "]";
    }
}
