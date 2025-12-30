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

    public ProgramSyncService(DeviceClient deviceClient, Path shortcutFolder) {
        this.deviceClient = deviceClient;
        this.shortcutFolder = shortcutFolder;
    }

    public void syncPrograms() {
        try {
            System.out.println("Checking for program synchronization...");

            // 1. Get local programs (Source of Truth)
            List<String> localPrograms = getLocalPrograms();

            // 2. Get server programs
            DeviceResponse device = deviceClient.getDeviceDetails(AgentConfig.DEVICE_ID, true);
            if (device == null) {
                System.err.println("Could not fetch device details for sync.");
                return;
            }

            List<String> serverPrograms = device.getProgramFilenames();
            if (serverPrograms == null) {
                serverPrograms = Collections.emptyList();
            }

            // 3. Compare
            if (!areProgramListsEqual(localPrograms, serverPrograms)) {
                System.out.println("Program lists differ. Syncing with server...");
                System.out.println("Local: " + localPrograms);
                System.out.println("Server: " + serverPrograms);

                deviceClient.syncPrograms(AgentConfig.DEVICE_ID, localPrograms);
                System.out.println("Programs synced successfully.");
            } else {
                System.out.println("Programs are in sync.");
            }

        } catch (Exception e) {
            System.err.println("Failed to sync programs: " + e.getMessage());
            e.printStackTrace();
        }
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
