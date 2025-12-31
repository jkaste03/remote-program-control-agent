package process;

import java.util.stream.Stream;

public class ProcessKiller {

    /**
     * Kills the process and its descendants using the Java ProcessHandle API.
     * This is cross-platform (Windows, Linux, macOS).
     */
    public void killProcessTree(long pid, boolean force) {
        ProcessHandle.of(pid).ifPresent(handle -> {
            // 1. Get all descendants (children, grandchildren, etc.)
            Stream<ProcessHandle> descendants = handle.descendants();

            // 2. Kill descendants first
            descendants.forEach(child -> {
                killSingleProcess(child, force);
            });

            // 3. Kill the parent process
            killSingleProcess(handle, force);
        });
    }

    private void killSingleProcess(ProcessHandle handle, boolean force) {
        try {
            if (force) {
                handle.destroyForcibly();
            } else {
                handle.destroy();
            }
        } catch (Exception e) {
            System.err.println("Failed to kill process " + handle.pid() + ": " + e.getMessage());
        }
    }
}
