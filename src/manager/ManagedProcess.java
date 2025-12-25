package manager;

import java.io.BufferedWriter;
import java.lang.Process;

public class ManagedProcess {
    private final Process process;
    private final BufferedWriter stdinWriter; // kan være null
    private final boolean minecraft;

    public ManagedProcess(Process process, BufferedWriter stdinWriter, boolean minecraft) {
        this.process = process;
        this.stdinWriter = stdinWriter;
        this.minecraft = minecraft;
    }

    public Process getProcess() {
        return process;
    }

    public BufferedWriter getStdinWriter() {
        return stdinWriter;
    }

    public boolean isMinecraft() {
        return minecraft;
    }

    public long pid() {
        return process.pid();
    }

    public boolean isAlive() {
        return process.isAlive();
    }
}
