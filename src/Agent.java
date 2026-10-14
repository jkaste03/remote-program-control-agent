import java.nio.file.Paths;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import client.AgentConfig;
import client.AuthService;
import client.ConsoleInputHandler;
import client.DeviceClient;
import client.DeviceRegistrationService;
import client.DeviceResponse;
import client.SseClient;
import core.AgentController;
import core.ProgramDirectoryWatcher;
import core.ProgramSyncService;
import core.ServerEventHandler;
import core.WhitelistSyncService;
import manager.ProcessManager;

public class Agent {
    private static final Logger LOGGER = Logger.getLogger(Agent.class.getName());
    private static final long INITIAL_RETRY_MS = 5000;
    private static final long MAX_RETRY_MS = 60000;
    private static final long RECOVERY_INTERVAL_SECONDS = 30;
    // Keep shortcuts beside the agent project, not inside it.
    private static final Path SHORTCUT_FOLDER = resolveShortcutFolder();

    private static Path resolveShortcutFolder() {
        String configured = System.getenv("AGENT_SHORTCUTS_DIR");

        if (configured != null && !configured.isBlank()) {
            return Paths.get(configured)
                    .toAbsolutePath()
                    .normalize();
        }

        return Paths.get(
                System.getProperty("user.home"),
                "RemoteProgramControl",
                "program-shortcuts").toAbsolutePath().normalize();
    }

    public static void main(String[] args) {
        // 1. Initialize Configuration and Services
        AgentConfig.loadConfig();
        System.out.println("Using server: " + AgentConfig.BASE_URL);

        // Infrastructure / IO
        ConsoleInputHandler inputHandler = new ConsoleInputHandler();
        AuthService authService = new AuthService(inputHandler);
        DeviceClient deviceClient = new DeviceClient(authService);
        try {
            Files.createDirectories(SHORTCUT_FOLDER);
        } catch (Exception e) {
            LOGGER.severe("Could not create shortcut folder: " + e.getMessage());
            return;
        }
        ProcessManager processManager = new ProcessManager(
                SHORTCUT_FOLDER,
                AgentConfig.DATA_DIR.resolve("active_processes.txt"));

        // Domain Services
        DeviceRegistrationService registrationService = new DeviceRegistrationService(deviceClient, inputHandler,
                authService);
        AgentController agentController = new AgentController(processManager, deviceClient);
        ProgramSyncService programSyncService = new ProgramSyncService(deviceClient, SHORTCUT_FOLDER);
        WhitelistSyncService whitelistSyncService = new WhitelistSyncService(deviceClient, SHORTCUT_FOLDER);
        ProgramDirectoryWatcher programWatcher = new ProgramDirectoryWatcher(SHORTCUT_FOLDER,
                programSyncService::syncPrograms);
        ServerEventHandler eventHandler = new ServerEventHandler(agentController, whitelistSyncService);

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
        List<String> serverPrograms = initializeWithRetry(registrationService);
        if (serverPrograms == null) {
            return;
        }

        // 3. Sync Programs (Source of Truth: Local Folder)
        programSyncService.syncPrograms(serverPrograms);
        whitelistSyncService.syncAll(serverPrograms);

        // 4. Start Monitoring for Changes
        programWatcher.start();

        // 3. Start SSE Client
        SseClient sseClient = new SseClient(AgentConfig.DEVICE_ID, authService, eventHandler);
        sseClient.start();
        startRecoveryScheduler(deviceClient, programSyncService, whitelistSyncService);

        // 4. Keep the application running. systemd has no interactive console.
        if (Boolean.parseBoolean(System.getenv("AGENT_SERVICE_MODE"))) {
            LOGGER.info("Running in service mode; terminal command listener disabled.");
            try {
                new java.util.concurrent.CountDownLatch(1).await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOGGER.info("Agent main thread interrupted; shutting down.");
            }
        } else {
            // Manual execution: keep the interactive console commands.
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

    private static List<String> initializeWithRetry(DeviceRegistrationService registrationService) {
        long retryDelayMs = INITIAL_RETRY_MS;
        while (true) {
            try {
                return registrationService.initializeDevice();
            } catch (AuthService.InvalidCredentialsException e) {
                LOGGER.severe(e.getMessage() + " Start the agent again and enter the production account credentials.");
                return null;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Agent startup was interrupted.", e);
            } catch (Exception e) {
                LOGGER.warning("Server unavailable during startup. Retrying in " + retryDelayMs + " ms: "
                        + safeMessage(e));
                try {
                    Thread.sleep(retryDelayMs);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Agent startup was interrupted.", interrupted);
                }
                retryDelayMs = Math.min(MAX_RETRY_MS, retryDelayMs * 2);
            }
        }
    }

    private static void startRecoveryScheduler(DeviceClient deviceClient, ProgramSyncService programSyncService,
            WhitelistSyncService whitelistSyncService) {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(daemonThreadFactory());
        scheduler.scheduleWithFixedDelay(() -> {
            try {
                DeviceResponse device = deviceClient.getDeviceDetails(AgentConfig.DEVICE_ID, true);
                if (device == null) {
                    LOGGER.warning("Device is no longer present on the server; keeping local agent alive.");
                    return;
                }
                List<String> programs = device.getProgramFilenames();
                programSyncService.syncPrograms(programs);
                whitelistSyncService.syncAll(programs);
                LOGGER.fine("Server connection recovered and state synchronized.");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOGGER.warning("Recovery synchronization was interrupted.");
            } catch (Exception e) {
                LOGGER.fine("Server unavailable during recovery synchronization: " + safeMessage(e));
            }
        }, RECOVERY_INTERVAL_SECONDS, RECOVERY_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    private static ThreadFactory daemonThreadFactory() {
        return runnable -> {
            Thread thread = new Thread(runnable, "Agent-Recovery");
            thread.setDaemon(true);
            return thread;
        };
    }

    private static String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }
}
