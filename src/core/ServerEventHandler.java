package core;

import java.util.function.BiConsumer;

import client.SimpleJson;
import command.CommandAction;

public class ServerEventHandler implements BiConsumer<String, String> {
    private final AgentController agentController;

    public ServerEventHandler(AgentController agentController) {
        this.agentController = agentController;
    }

    @Override
    public void accept(String event, String data) {
        System.out.println("Received event: " + event + ", data: " + data);

        CommandAction action = null;
        if ("START_PROGRAM".equals(event)) {
            action = CommandAction.START;
        } else if ("STOP_PROGRAM".equals(event)) {
            action = CommandAction.STOP;
        }

        if (action != null) {
            handleCommandEvent(data, action);
        } else {
            System.out.println("Unknown event: " + event);
        }
    }

    private void handleCommandEvent(String data, CommandAction action) {
        String filename = SimpleJson.extractField(data, "filename");
        String idStr = SimpleJson.extractField(data, "id");
        long programId = idStr != null ? Long.parseLong(idStr) : -1;

        if (filename != null && programId != -1) {
            agentController.handleProgramCommand(programId, filename, action);
        } else {
            System.err.println("Invalid event data: " + data);
        }
    }
}
