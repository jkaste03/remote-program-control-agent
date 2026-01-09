package manager;

import stop.LinuxShutdownStrategy;
import stop.ShutdownStrategy;
import stop.WindowsShutdownStrategy;

public class SystemPowerManager {

    private final ShutdownStrategy shutdownStrategy;

    public SystemPowerManager() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            this.shutdownStrategy = new WindowsShutdownStrategy();
        } else if (os.contains("nix") || os.contains("nux") || os.contains("aix")) {
            this.shutdownStrategy = new LinuxShutdownStrategy();
        } else {
            // Fallback or explicit mac support support if needed.
            // For now defaults to no-op or maybe log warning.
            // Usually generic *nix command works on MacOS too but path might differ.
            // Creating a dummy for now to avoid null pointers.
            this.shutdownStrategy = () -> System.err.println("Shutdown not supported on this OS: " + os);
        }
    }

    public void shutdownSystem() {
        System.out.println("Initiating system shutdown...");
        shutdownStrategy.shutdown();
    }
}
