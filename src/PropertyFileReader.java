import java.io.InputStream;
import java.util.Properties;

/**
 * Loads config.properties once and caches it.
 *
 * server.ip can be overridden at runtime without editing any file:
 *   -Dserver.ip=192.168.1.42
 * All other values are read purely from config.properties.
 */
public class PropertyFileReader {

    private static final Properties PROPS = load();

    private static Properties load() {
        Properties p = new Properties();
        try (InputStream in = PropertyFileReader.class
                .getClassLoader().getResourceAsStream("config.properties")) {
            if (in == null) {
                System.err.println("[Config] config.properties not found on classpath.");
                return p;
            }
            p.load(in);
        } catch (Exception e) {
            System.err.println("[Config] Failed to load config.properties: " + e.getMessage());
        }
        return p;
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    /**
     * Returns the server IP.
     * Priority: -Dserver.ip JVM flag  →  config.properties server.ip  →  "localhost"
     */
    public static String getIP() {
        String sysProp = System.getProperty("server.ip");
        if (sysProp != null && !sysProp.isBlank()) return sysProp.trim();
        String fromFile = PROPS.getProperty("server.ip", "localhost");
        return fromFile.trim();
    }

    public static int getTCPPort() {
        return intProp("tcp.port", 1234);
    }

    public static int getPlayerMonitorPort() {
        return intProp("playerMonitor.port", 6001);
    }

    public static int getPlayerListenerPort() {
        return intProp("playerListener.port", 6002);
    }

    public static int getClientPort() {
        return intProp("client.port", 1234);
    }

    public static int getTileSize() {
        return intProp("tile.size", 50);
    }

    public static int getColNum() {
        return intProp("column.num", 15);
    }

    public static int getRowNum() {
        return intProp("row.num", 12);
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private static int intProp(String key, int defaultValue) {
        try {
            String val = PROPS.getProperty(key);
            if (val != null) return Integer.parseInt(val.trim());
        } catch (NumberFormatException e) {
            System.err.printf("[Config] Bad value for '%s', using default %d%n", key, defaultValue);
        }
        return defaultValue;
    }
}