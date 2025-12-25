package program;

import java.io.BufferedWriter;
import java.io.File;
import java.io.OutputStreamWriter;
import detect.MinecraftDetector;
import manager.ManagedProcess;
import process.ProcessKiller;
import stop.MinecraftStopStrategy;

public class MinecraftProgramHandler implements ProgramHandler {
    @Override
    public boolean canHandle(String filename, File targetFile) {
        return MinecraftDetector.isMinecraftServer(targetFile);
    }

    @Override
    public void onStart(Process process, ManagedProcess managedProcess) {
        // Attach writer for Minecraft commands
        managedProcess.setStdinWriter(new BufferedWriter(new OutputStreamWriter(process.getOutputStream())));
        managedProcess.setMinecraft(true);
    }

    @Override
    public void stop(ManagedProcess managedProcess, ProcessKiller processKiller) {
        try {
            new MinecraftStopStrategy(300).stop(managedProcess, processKiller);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
