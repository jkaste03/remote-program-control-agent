package stop;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class LinuxShutdownStrategy implements ShutdownStrategy {

    private static final Logger LOGGER = Logger.getLogger(LinuxShutdownStrategy.class.getName());

    @Override
    public void shutdown() {
        try {
            Process process = new ProcessBuilder(
                    "sudo",
                    "-n",
                    "/usr/sbin/shutdown",
                    "-h",
                    "now")
                    .inheritIO()
                    .start();

            if (!process.waitFor(10, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                LOGGER.severe("Shutdown command timed out after 10 seconds.");
                return;
            }

            int exitCode = process.exitValue();

            if (exitCode == 0) {
                LOGGER.info("Shutdown command completed successfully.");
            } else {
                LOGGER.severe(
                        "Shutdown command failed with exit code: " + exitCode);
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.warning("Waiting for shutdown command was interrupted.");
        } catch (IOException e) {
            LOGGER.severe(
                    "Could not execute shutdown command: " + e.getMessage());
        }
    }
}