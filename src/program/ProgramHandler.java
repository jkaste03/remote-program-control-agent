package program;

import java.io.File;
import manager.ManagedProcess;
import process.ProcessKiller;

public interface ProgramHandler {
    boolean canHandle(String filename, File targetFile);

    void onStart(Process process, ManagedProcess managedProcess);

    void stop(ManagedProcess managedProcess, ProcessKiller processKiller);
}
