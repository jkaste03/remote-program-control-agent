package core;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import client.AgentConfig;
import client.DeviceClient;

public class ProgramSyncService {
    private static final Logger logger = Logger.getLogger(ProgramSyncService.class.getName());
    private final DeviceClient deviceClient;
    private final Path shortcutFolder;
    private List<String> lastKnownServerPrograms = new ArrayList<>();

    public ProgramSyncService(DeviceClient deviceClient, Path shortcutFolder) {
        this.deviceClient = deviceClient;
        this.shortcutFolder = shortcutFolder;
    }

    /**
     * Called by the directory watcher when changes are detected.
     * Uses the cached server state to avoid unnecessary API calls.
     */
    public void syncPrograms() {
        try {
            logger.info("Checking for program synchronization...");

            // 1. Get local programs (Source of Truth)
            List<String> localPrograms = getLocalPrograms();

            // 2. Compare with cached server state
            if (!areProgramListsEqual(localPrograms, lastKnownServerPrograms)) {
                logger.info("Program lists differ. Syncing with server...");
                logger.log(Level.INFO, "Local: {0}", localPrograms);
                logger.log(Level.INFO, "Server (Cached): {0}", lastKnownServerPrograms);

                deviceClient.syncPrograms(AgentConfig.DEVICE_ID, localPrograms);

                // Update cache after successful sync
                this.lastKnownServerPrograms = new ArrayList<>(localPrograms);
                logger.info("Programs synced successfully.");
            } else {
                logger.info("Programs are in sync.");
            }

        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.log(Level.WARNING, "Program synchronization was interrupted", ie);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to sync programs", e);
        }
    }

    /**
     * Called at startup with the initial state from the server.
     */
    public void syncPrograms(List<String> serverPrograms) {
        if (serverPrograms == null) {
            serverPrograms = Collections.emptyList();
        }
        this.lastKnownServerPrograms = new ArrayList<>(serverPrograms);
        syncPrograms(); // Run the check immediately
    }

    private List<String> getLocalPrograms() {
        List<String> filenames = new ArrayList<>();
        File folder = shortcutFolder.toFile();

        if (folder.exists() && folder.isDirectory()) {
            File[] files = folder.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile()) {
                        filenames.add(file.getName());
                    }
                }
            }
        }
        return filenames;
    }

    private boolean areProgramListsEqual(List<String> list1, List<String> list2) {
        if (list1.size() != list2.size()) {
            return false;
        }
        List<String> sorted1 = list1.stream().sorted().toList();
        List<String> sorted2 = list2.stream().sorted().toList();
        return sorted1.equals(sorted2);
    }
}
