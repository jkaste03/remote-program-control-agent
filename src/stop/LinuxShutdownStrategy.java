package stop;

import java.io.IOException;

public class LinuxShutdownStrategy implements ShutdownStrategy {

    @Override
    public void shutdown() {
        try {
            // sudo shutdown -h now
            Runtime.getRuntime().exec("sudo shutdown -h now");
        } catch (IOException e) {
            System.err.println("Failed to execute Linux shutdown command: " + e.getMessage());
        }
    }
}
