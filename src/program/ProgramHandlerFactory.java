package program;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ProgramHandlerFactory {
    private final List<ProgramHandler> handlers = new ArrayList<>();

    public ProgramHandlerFactory() {
        // Register handlers. Order matters.
        handlers.add(new MinecraftProgramHandler());
        handlers.add(new DefaultProgramHandler());
    }

    public ProgramHandler getHandler(String filename, File targetFile) {
        for (ProgramHandler handler : handlers) {
            if (handler.canHandle(filename, targetFile)) {
                return handler;
            }
        }
        return new DefaultProgramHandler();
    }
}
