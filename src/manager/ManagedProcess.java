package manager;

import java.io.BufferedWriter;
import java.lang.Process;

import program.ProgramHandler;

public class ManagedProcess {
    private final Process process;
    private BufferedWriter stdinWriter; // kan være null
    private boolean minecraft;
    private ProgramHandler handler;

    public ManagedProcess(Process process, BufferedWriter stdinWriter, boolean minecraft, ProgramHandler handler) {
        this.process = process;
        this.stdinWriter = stdinWriter;
        this.minecraft = minecraft;
        this.handler = handler;
    }

    public ProgramHandler getHandler() {
        return handler;
    }

    public void setStdinWriter(BufferedWriter stdinWriter) {
        this.stdinWriter = stdinWriter;
    }

    public void setMinecraft(boolean minecraft) {
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
