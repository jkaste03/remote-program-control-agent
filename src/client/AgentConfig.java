package client;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

public class AgentConfig {
    public static final String BASE_URL = "http://localhost:8090";
    public static final String KEYCLOAK_URL = "http://localhost:8080/realms/remote-control";
    public static final String AUTH_URL = KEYCLOAK_URL + "/protocol/openid-connect/auth";
    public static final String TOKEN_URL = KEYCLOAK_URL + "/protocol/openid-connect/token";
    public static final String CLIENT_ID = "remote-control-api";
    public static final String REDIRECT_URI = "http://localhost:8888/callback";
    public static final String SCOPE = "openid profile email offline_access";

    public static long DEVICE_ID = -1;
    public static String DEVICE_NAME = null;
    public static int WOL_PORT = -1;

    public static final String TOKEN_FILE = "refresh_token.txt";
    private static final String CONFIG_FILE = "agent.properties";

    // Store properties in memory to support multiple keys
    private static Properties properties = new Properties();

    public static void loadConfig() {
        try (FileInputStream in = new FileInputStream(CONFIG_FILE)) {
            properties.load(in);
            // We don't set DEVICE_ID here anymore, as it depends on the user
            String deviceIdStr = properties.getProperty("device.id");
            if (deviceIdStr != null) {
                // Optional: Keep legacy behavior for default device ID if needed,
                // but typically we want to wait for user context.
                // DEVICE_ID = Long.parseLong(deviceIdStr);
            }
        } catch (IOException e) {
            System.out.println("No config file found, using defaults.");
        }
    }

    public static long getDeviceIdForUser(String userId) {
        String val = properties.getProperty("device.id." + userId);
        if (val != null) {
            return Long.parseLong(val);
        }
        // Fallback to global device.id if user specific not found?
        // Maybe not, to avoid confusion. But the user asked for mapping.
        return -1;
    }

    public static void saveDeviceIdForUser(String userId, long deviceId) {
        properties.setProperty("device.id." + userId, String.valueOf(deviceId));
        saveProperties();
    }

    public static void saveConfig() {
        // Legacy save, might not be needed if we only use saveDeviceIdForUser
        if (DEVICE_ID != -1) {
            properties.setProperty("device.id", String.valueOf(DEVICE_ID));
        }
        saveProperties();
    }

    private static void saveProperties() {
        try (FileOutputStream out = new FileOutputStream(CONFIG_FILE)) {
            properties.store(out, "Agent Configuration");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
