import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Logger;

import client.AgentConfig;
import client.AuthService;
import client.ConsoleInputHandler;
import client.DeviceClient;
import client.DeviceRegistrationService;
import client.SseClient;
import core.AgentController;
import core.ProgramDirectoryWatcher;
import core.ProgramSyncService;
import core.ServerEventHandler;
import manager.ProcessManager;

public class Agent {
    private static final Logger LOGGER = Logger.getLogger(Agent.class.getName());
    // Relative path: "program-shortcuts" in the current working directory
    private static final String SHORTCUT_FOLDER = "program-shortcuts";

    public static void main(String[] args) {
        // 1. Initialize Configuration and Services
        AgentConfig.loadConfig();

        // Infrastructure / IO
        AuthService authService = new AuthService();
        DeviceClient deviceClient = new DeviceClient(authService);
        ConsoleInputHandler inputHandler = new ConsoleInputHandler();
        ProcessManager processManager = new ProcessManager(Paths.get(SHORTCUT_FOLDER));

        // Domain Services
        DeviceRegistrationService registrationService = new DeviceRegistrationService(deviceClient, inputHandler);
        AgentController agentController = new AgentController(processManager, deviceClient);
        ProgramSyncService programSyncService = new ProgramSyncService(deviceClient, Paths.get(SHORTCUT_FOLDER));
        ProgramDirectoryWatcher programWatcher = new ProgramDirectoryWatcher(Paths.get(SHORTCUT_FOLDER),
                programSyncService::syncPrograms);
        ServerEventHandler eventHandler = new ServerEventHandler(agentController);

        processManager.setProcessExitListener((programId, filename) -> {
            try {
                deviceClient.updateProgramStatus(programId, "STOPPED");
                LOGGER.info("Detected unexpected stop for program id=" + programId + " (" + filename + ")");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOGGER.severe("Interrupted while reporting program stop for id=" + programId + ": " + e.getMessage());
            } catch (Exception e) {
                LOGGER.severe("Failed to report program stop for id=" + programId + ": " + e.getMessage());
            }
        });

        // After listener is registered, reconcile persisted processes to detect missing
        // ones
        processManager.reconcilePersistedProcesses();

        // 2. Perform Device Registration / Update
        try {
            List<String> serverPrograms = registrationService.initializeDevice();

            // 3. Sync Programs (Source of Truth: Local Folder)
            programSyncService.syncPrograms(serverPrograms);

            // 4. Start Monitoring for Changes
            programWatcher.start();

        } catch (Exception e) {
            e.printStackTrace();
            LOGGER.severe("Failed to initialize device. Exiting.");
            return;
        }

        // 3. Start SSE Client
        SseClient sseClient = new SseClient(AgentConfig.DEVICE_ID, authService, eventHandler);
        sseClient.start();

        // 4. Keep the application running and listen for user input
        inputHandler.listenForCommands(
                () -> {
                    LOGGER.info("Logging out...");
                    authService.logout();
                    System.exit(0);
                },
                () -> {
                    LOGGER.info("Exiting...");
                    System.exit(0);
                });
    }
}