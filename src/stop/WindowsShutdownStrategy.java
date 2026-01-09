package stop;

import java.io.IOException;

public class WindowsShutdownStrategy implements ShutdownStrategy {

    @Override
    public void shutdown() {
        try {
            // shutdown /s /t 0 -> Shutdown, timestamp 0 (immediately)
            Runtime.getRuntime().exec("shutdown /s /t 0");
        } catch (IOException e) {
            System.err.println("Failed to execute Windows shutdown command: " + e.getMessage());
        }
    }
}
