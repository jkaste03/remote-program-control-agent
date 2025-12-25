package program;

import java.io.File;
import manager.ManagedProcess;
import process.ProcessKiller;
import stop.GracefulStopStrategy;

public class DefaultProgramHandler implements ProgramHandler {
    @Override
    public boolean canHandle(String filename, File targetFile) {
        return true; // Fallback
    }

    @Override
    public void onStart(Process process, ManagedProcess managedProcess) {
        // Do nothing special
    }

    @Override
    public void stop(ManagedProcess managedProcess, ProcessKiller processKiller) {
        try {
            new GracefulStopStrategy(30).stop(managedProcess, processKiller);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
