package manager;

import java.io.BufferedWriter;
import java.lang.Process;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import program.ProgramHandler;

public class ManagedProcess {
    private final Process process;
    private final ProcessHandle processHandle;
    private BufferedWriter stdinWriter; // kan være null
    private boolean minecraft;
    private ProgramHandler handler;

    public ManagedProcess(Process process, BufferedWriter stdinWriter, boolean minecraft, ProgramHandler handler) {
        this.process = process;
        this.processHandle = process.toHandle();
        this.stdinWriter = stdinWriter;
        this.minecraft = minecraft;
        this.handler = handler;
    }

    public ManagedProcess(long pid, boolean minecraft, ProgramHandler handler) {
        this.process = null;
        this.processHandle = ProcessHandle.of(pid).orElse(null);
        this.stdinWriter = null;
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
        return processHandle != null ? processHandle.pid() : -1;
    }

    public boolean isAlive() {
        return processHandle != null && processHandle.isAlive();
    }

    public boolean waitFor(long timeout, TimeUnit unit) throws InterruptedException {
        if (process != null) {
            return process.waitFor(timeout, unit);
        } else if (processHandle != null) {
            try {
                processHandle.onExit().get(timeout, unit);
                return true;
            } catch (ExecutionException e) {
                throw new RuntimeException(e);
            } catch (TimeoutException e) {
                return false;
            }
        }
        return true;
    }
}
