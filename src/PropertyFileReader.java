
import java.io.InputStream;
import java.util.Properties;

public class PropertyFileReader {
    public static String getIP() {
        Properties prop = new Properties();
        String ip = "";
        try (InputStream input = PropertyFileReader.class.getClassLoader().getResourceAsStream("config.properties")) {
            if (input == null) {
                System.out.println("Sorry, unable to find config.properties");
                return ip;
            }

            prop.load(input);
            ip = prop.getProperty("server.ip");

            if (ip != null) {
                ip = ip.trim();
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return ip;
    }
    public static int getClientPort() {
        Properties prop = new Properties();
        String port = "";
        try (InputStream input = PropertyFileReader.class.getClassLoader().getResourceAsStream("config.properties")) {
            if (input == null) {
                System.out.println("Sorry, unable to find config.properties");
                return Integer.parseInt(port);
            }

            prop.load(input);
            port = prop.getProperty("client.port");

            if (port != null) {
                port = port.trim();
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return Integer.parseInt(port);
    }
        public static int getUDPPort() {
        Properties prop = new Properties();
        String port = "";
        try (InputStream input = PropertyFileReader.class.getClassLoader().getResourceAsStream("config.properties")) {
            if (input == null) {
                System.out.println("Sorry, unable to find config.properties");
                return Integer.parseInt(port);
            }

            prop.load(input);
            port = prop.getProperty("udp.port");

            if (port != null) {
                port = port.trim();
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return Integer.parseInt(port);
    }
            public static int getTCPPort() {
        Properties prop = new Properties();
        String port = "";
        try (InputStream input = PropertyFileReader.class.getClassLoader().getResourceAsStream("config.properties")) {
            if (input == null) {
                System.out.println("Sorry, unable to find config.properties");
                return Integer.parseInt(port);
            }

            prop.load(input);
            port = prop.getProperty("tcp.port");

            if (port != null) {
                port = port.trim();
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return Integer.parseInt(port);
    }
    
}