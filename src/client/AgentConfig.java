package client;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

public class AgentConfig {
    public static final String BASE_URL = "http://localhost:8090";
    public static final String KEYCLOAK_URL = "http://localhost:8080/realms/DAT152";
    public static final String AUTH_URL = KEYCLOAK_URL + "/protocol/openid-connect/auth";
    public static final String TOKEN_URL = KEYCLOAK_URL + "/protocol/openid-connect/token";
    public static final String CLIENT_ID = "dat152oblig2";
    public static final String REDIRECT_URI = "http://localhost:8888/callback";
    public static final String SCOPE = "openid offline_access";

    public static long DEVICE_ID = -1;
    public static String DEVICE_NAME = null;
    public static int WOL_PORT = -1;

    public static final String TOKEN_FILE = "refresh_token.txt";
    private static final String CONFIG_FILE = "agent.properties";

    public static void loadConfig() {
        Properties props = new Properties();
        try (FileInputStream in = new FileInputStream(CONFIG_FILE)) {
            props.load(in);
            String deviceIdStr = props.getProperty("device.id");
            if (deviceIdStr != null) {
                DEVICE_ID = Long.parseLong(deviceIdStr);
            }
        } catch (IOException e) {
            System.out.println("No config file found, using defaults.");
        }
    }

    public static void saveConfig() {
        Properties props = new Properties();
        props.setProperty("device.id", String.valueOf(DEVICE_ID));
        try (FileOutputStream out = new FileOutputStream(CONFIG_FILE)) {
            props.store(out, "Agent Configuration");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
