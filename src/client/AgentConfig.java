
package client;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Properties;

public class AgentConfig {

    private static final String DEVELOPMENT_BASE_URL =
            "http://localhost:8090";

    private static final String PRODUCTION_BASE_URL =
            "https://www.fotcalc.com/remote-program-control";

    public static final String BASE_URL = getBaseUrl();
    public static final String LOGIN_URL = BASE_URL + "/auth/login";
    public static final String REFRESH_URL = BASE_URL + "/auth/refresh";
    public static final String API_BASE_URL = BASE_URL + "/api/v1";

    public static long DEVICE_ID = -1;
    public static String DEVICE_NAME = null;
    public static int WOL_PORT = -1;

    // All persistent application data lives in one predictable location.
    public static final Path DATA_DIR = resolveDataDirectory();
    public static final Path TOKEN_FILE =
            DATA_DIR.resolve("refresh_token.txt");

    private static final Path CONFIG_FILE =
            DATA_DIR.resolve("agent.properties");

    private static final Properties properties = new Properties();

    private static Path resolveDataDirectory() {
        // Explicit override, useful for testing and custom deployments.
        String override = System.getenv("AGENT_DATA_DIR");

        if (override != null && !override.isBlank()) {
            return Paths.get(override.trim())
                    .toAbsolutePath()
                    .normalize();
        }

        String os = System.getProperty("os.name", "")
                .toLowerCase(Locale.ROOT);

        String userHome = System.getProperty("user.home");

        if (os.contains("win")) {
            String localAppData = System.getenv("LOCALAPPDATA");

            if (localAppData != null && !localAppData.isBlank()) {
                return Paths.get(localAppData)
                        .resolve("RemoteProgramControlAgent")
                        .toAbsolutePath()
                        .normalize();
            }

            return Paths.get(
                    userHome,
                    "AppData",
                    "Local",
                    "RemoteProgramControlAgent"
            ).toAbsolutePath().normalize();
        }

        if (os.contains("mac") || os.contains("darwin")) {
            return Paths.get(
                    userHome,
                    "Library",
                    "Application Support",
                    "RemoteProgramControlAgent"
            ).toAbsolutePath().normalize();
        }

        // Linux and other Unix-like operating systems.
        String xdgConfigHome = System.getenv("XDG_CONFIG_HOME");

        if (xdgConfigHome != null && !xdgConfigHome.isBlank()) {
            Path xdgPath = Paths.get(xdgConfigHome);

            if (xdgPath.isAbsolute()) {
                return xdgPath.resolve("remote-program-control-agent")
                        .normalize();
            }
        }

        return Paths.get(
                userHome,
                ".config",
                "remote-program-control-agent"
        ).toAbsolutePath().normalize();
    }

    private static String getBaseUrl() {
        String configuredUrl = System.getenv("AGENT_BASE_URL");

        if (configuredUrl != null && !configuredUrl.isBlank()) {
            return normalizeBaseUrl(configuredUrl);
        }

        String profile = System.getenv()
                .getOrDefault("AGENT_PROFILE", "prod")
                .trim()
                .toLowerCase(Locale.ROOT);

        return switch (profile) {
            case "prod", "production" -> PRODUCTION_BASE_URL;
            case "dev", "development", "" -> DEVELOPMENT_BASE_URL;
            default -> throw new IllegalStateException(
                    "Unsupported AGENT_PROFILE '" + profile
                            + "'. Use 'dev' or 'prod'."
            );
        };
    }

    private static String normalizeBaseUrl(String url) {
        String normalized = url.trim();

        while (normalized.endsWith("/")) {
            normalized = normalized.substring(
                    0, normalized.length() - 1
            );
        }

        if (normalized.isBlank()) {
            throw new IllegalStateException(
                    "AGENT_BASE_URL must not be blank."
            );
        }

        return normalized;
    }

    public static void ensureDataDirectory() throws IOException {
        Files.createDirectories(DATA_DIR);
    }

    public static void loadConfig() {
        try {
            ensureDataDirectory();

            if (!Files.exists(CONFIG_FILE)) {
                return;
            }

            try (InputStream in = Files.newInputStream(CONFIG_FILE)) {
                properties.load(in);
            }
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Could not load agent configuration from "
                            + CONFIG_FILE,
                    e
            );
        }
    }

    public static long getDeviceIdForUser(String userId) {
        String value = properties.getProperty("device.id." + userId);

        if (value != null) {
            return Long.parseLong(value);
        }

        return -1;
    }

    public static void saveDeviceIdForUser(
            String userId,
            long deviceId
    ) {
        properties.setProperty(
                "device.id." + userId,
                String.valueOf(deviceId)
        );

        saveProperties();
    }

    public static void saveConfig() {
        if (DEVICE_ID != -1) {
            properties.setProperty(
                    "device.id",
                    String.valueOf(DEVICE_ID)
            );
        }

        saveProperties();
    }

    private static synchronized void saveProperties() {
        try {
            ensureDataDirectory();

            try (OutputStream out =
                         Files.newOutputStream(CONFIG_FILE)) {
                properties.store(out, "Agent Configuration");
            }
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Could not save agent configuration to "
                            + CONFIG_FILE,
                    e
            );
        }
    }
}
