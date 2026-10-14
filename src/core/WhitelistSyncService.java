package core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import client.AgentConfig;
import client.DeviceClient;
import detect.MinecraftDetector;
import shortcut.ShortcutInfo;
import shortcut.ShortcutResolver;

public class WhitelistSyncService {
    private static final Logger LOGGER = Logger.getLogger(WhitelistSyncService.class.getName());

    private final DeviceClient deviceClient;
    private final Path shortcutFolder;
    private final ShortcutResolver shortcutResolver = new ShortcutResolver();

    public WhitelistSyncService(DeviceClient deviceClient, Path shortcutFolder) {
        this.deviceClient = deviceClient;
        this.shortcutFolder = shortcutFolder;
    }

    public void syncAll(List<String> filenames) {
        if (filenames == null) {
            return;
        }
        filenames.forEach(filename -> sync(filename));
    }

    public void sync(String filename) {
        try {
            Path shortcutPath = shortcutFolder.resolve(filename);
            if (!Files.isRegularFile(shortcutPath)) {
                return;
            }

            ShortcutInfo shortcut = shortcutResolver.resolveShortcut(shortcutPath.toFile());
            if (shortcut == null || !MinecraftDetector.isMinecraftServer(shortcut.getTargetFile())) {
                return;
            }

            List<String> usernames = deviceClient.getWhitelist(AgentConfig.DEVICE_ID, filename);
            Path serverDirectory = resolveServerDirectory(shortcut);
            Path whitelist = serverDirectory.resolve("whitelist.json");
            if (!Files.exists(whitelist)) {
                return;
            }

            String json = usernames.stream()
                    .map(name -> "{\"name\":\"" + client.SimpleJson.escapeJson(name) + "\"}")
                    .collect(java.util.stream.Collectors.joining(",", "[", "]"));
            Files.writeString(whitelist, json + System.lineSeparator(), StandardCharsets.UTF_8);
            LOGGER.log(Level.INFO, "Synchronized Minecraft whitelist for {0}", filename);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.log(Level.WARNING, "Whitelist synchronization interrupted for " + filename, e);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Could not synchronize whitelist for " + filename, e);
        }
    }

    private Path resolveServerDirectory(ShortcutInfo shortcut) {
        if (shortcut.getWorkingDirectory() != null && !shortcut.getWorkingDirectory().isBlank()) {
            return Path.of(shortcut.getWorkingDirectory()).toAbsolutePath().normalize();
        }
        Path target = shortcut.getTargetFile().toPath().toAbsolutePath().normalize();
        return target.getParent();
    }
}
