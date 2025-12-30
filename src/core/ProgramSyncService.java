package core;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import client.AgentConfig;
import client.DeviceClient;
import client.DeviceResponse;

public class ProgramSyncService {
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
            System.out.println("Checking for program synchronization...");

            // 1. Get local programs (Source of Truth)
            List<String> localPrograms = getLocalPrograms();

            // 2. Compare with cached server state
            if (!areProgramListsEqual(localPrograms, lastKnownServerPrograms)) {
                System.out.println("Program lists differ. Syncing with server...");
                System.out.println("Local: " + localPrograms);
                System.out.println("Server (Cached): " + lastKnownServerPrograms);

                deviceClient.syncPrograms(AgentConfig.DEVICE_ID, localPrograms);

                // Update cache after successful sync
                this.lastKnownServerPrograms = new ArrayList<>(localPrograms);
                System.out.println("Programs synced successfully.");
            } else {
                System.out.println("Programs are in sync.");
            }

        } catch (Exception e) {
            System.err.println("Failed to sync programs: " + e.getMessage());
            e.printStackTrace();
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
        List<String> sorted1 = list1.stream().sorted().collect(Collectors.toList());
        List<String> sorted2 = list2.stream().sorted().collect(Collectors.toList());
        return sorted1.equals(sorted2);
    }
}
