package core;

import java.util.function.BiConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import client.SimpleJson;
import command.CommandAction;

public class ServerEventHandler implements BiConsumer<String, String> {
    private static final Logger LOGGER = Logger.getLogger(ServerEventHandler.class.getName());

    private final AgentController agentController;
    private final WhitelistSyncService whitelistSyncService;

    public ServerEventHandler(AgentController agentController, WhitelistSyncService whitelistSyncService) {
        this.agentController = agentController;
        this.whitelistSyncService = whitelistSyncService;
    }

    @Override
    public void accept(String event, String data) {
        if ("PING".equals(event)) {
            return; // ignore noisy heartbeat events
        }
        if ("WHITELIST_CHANGED".equals(event)) {
            String filename = SimpleJson.extractField(data, "filename");
            if (filename != null) {
                whitelistSyncService.sync(filename);
            }
            return;
        }

        LOGGER.log(Level.INFO, "Received event: {0}, data: {1}", new Object[] { event, data });

        CommandAction action = null;
        if ("START_PROGRAM".equals(event)) {
            action = CommandAction.START;
        } else if ("STOP_PROGRAM".equals(event)) {
            action = CommandAction.STOP;
        } else if ("SHUTDOWN".equals(event)) {
            agentController.handleShutdown();
            return;
        }

        if (action != null) {
            handleCommandEvent(data, action);
        } else {
            LOGGER.log(Level.WARNING, "Unknown event: {0}", event);
        }
    }

    private void handleCommandEvent(String data, CommandAction action) {
        String filename = SimpleJson.extractField(data, "filename");
        String idStr = SimpleJson.extractField(data, "id");
        long programId = idStr != null ? Long.parseLong(idStr) : -1;

        if (filename != null && programId != -1) {
            agentController.handleProgramCommand(programId, filename, action);
        } else {
            LOGGER.log(Level.WARNING, "Invalid event data: {0}", data);
        }
    }
}
