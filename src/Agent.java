import java.nio.file.Paths;
import java.util.concurrent.CountDownLatch;

import client.AgentConfig;
import client.AuthService;
import client.ConsoleInputHandler;
import client.DeviceClient;
import client.DeviceRegistrationService;
import client.SseClient;
import command.CommandAction;
import manager.ProcessManager;

public class Agent {
    private static final String SHORTCUT_FOLDER = "C:\\RemoteServerControlAgent\\program-shortcuts";

    public static void main(String[] args) {
        // 1. Initialize Configuration and Services
        AgentConfig.loadConfig();
        AuthService authService = new AuthService();
        DeviceClient deviceClient = new DeviceClient(authService);
        ConsoleInputHandler inputHandler = new ConsoleInputHandler();
        DeviceRegistrationService registrationService = new DeviceRegistrationService(deviceClient, inputHandler);
        ProcessManager processManager = new ProcessManager(Paths.get(SHORTCUT_FOLDER));

        // 2. Perform Device Registration / Update
        try {
            registrationService.initializeDevice();
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Failed to initialize device. Exiting.");
            return;
        }

        // 3. Start SSE Client
        SseClient sseClient = new SseClient(AgentConfig.DEVICE_ID, authService, (event, data) -> {
            System.out.println("Received event: " + event + ", data: " + data);
            CommandAction action = null;
            if ("START_PROGRAM".equals(event)) {
                action = CommandAction.START;
            } else if ("STOP_PROGRAM".equals(event)) {
                action = CommandAction.STOP;
            }

            if (action != null) {
                processManager.handleCommand(data, action);
            } else {
                System.out.println("Unknown event: " + event);
            }
        });

        sseClient.start();

        // 4. Keep the application running
        try {
            new CountDownLatch(1).await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}