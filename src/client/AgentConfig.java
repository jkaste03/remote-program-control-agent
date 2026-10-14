package client;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

public class AgentConfig {
    private static final String DEVELOPMENT_BASE_URL = "http://localhost:8090";
    private static final String PRODUCTION_BASE_URL = "https://www.fotcalc.com/remote-program-control";

    public static final String BASE_URL = getBaseUrl();
    public static final String LOGIN_URL = BASE_URL + "/auth/login";
    public static final String REFRESH_URL = BASE_URL + "/auth/refresh";
    public static final String API_BASE_URL = BASE_URL + "/api/v1";

    public static long DEVICE_ID = -1;
    public static String DEVICE_NAME = null;
    public static int WOL_PORT = -1;

    public static final String TOKEN_FILE = "refresh_token.txt";
    private static final String CONFIG_FILE = "agent.properties";

    // Store properties in memory to support multiple keys
    private static Properties properties = new Properties();

    private static String getBaseUrl() {
        String configuredUrl = System.getenv("AGENT_BASE_URL");
        if (configuredUrl != null && !configuredUrl.isBlank()) {
            return normalizeBaseUrl(configuredUrl);
        }

        String profile = System.getenv().getOrDefault("AGENT_PROFILE", "dev").trim().toLowerCase();
        return switch (profile) {
            case "prod", "production" -> PRODUCTION_BASE_URL;
            case "dev", "development", "" -> DEVELOPMENT_BASE_URL;
            default -> throw new IllegalStateException(
                    "Unsupported AGENT_PROFILE '" + profile + "'. Use 'dev' or 'prod'.");
        };
    }

    private static String normalizeBaseUrl(String url) {
        String normalized = url.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.isBlank()) {
            throw new IllegalStateException("AGENT_BASE_URL must not be blank.");
        }
        return normalized;
    }

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
