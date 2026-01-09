package core;

import java.util.concurrent.CompletableFuture;

import client.DeviceClient;
import command.CommandAction;
import manager.ProcessManager;
import manager.SystemPowerManager;

public class AgentController {
    private final ProcessManager processManager;
    private final DeviceClient deviceClient;
    private final SystemPowerManager systemPowerManager;

    public AgentController(ProcessManager processManager, DeviceClient deviceClient) {
        this.processManager = processManager;
        this.deviceClient = deviceClient;
        this.systemPowerManager = new SystemPowerManager();
    }

    public void handleShutdown() {
        systemPowerManager.shutdownSystem();
    }

    public void handleProgramCommand(long programId, String filename, CommandAction action) {
        CompletableFuture<Boolean> result = processManager.handleCommand(programId, filename, action);

        result.whenComplete((success, error) -> {
            boolean ok = error == null && Boolean.TRUE.equals(success);
            String status = determineStatus(action, ok);

            try {
                deviceClient.updateProgramStatus(programId, status);
                System.out.println("Updated program status to: " + status);
            } catch (Exception e) {
                System.err.println("Failed to update program status: " + e.getMessage());
            }
        });
    }

    private String determineStatus(CommandAction action, boolean success) {
        if (success) {
            return action == CommandAction.START ? "STARTED" : "STOPPED";
        } else {
            // If failed to start, it's stopped. If failed to stop, it's started.
            return action == CommandAction.START ? "STOPPED" : "STARTED";
        }
    }
}
