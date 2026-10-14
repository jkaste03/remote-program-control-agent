package stop;

import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import manager.ManagedProcess;
import process.ProcessKiller;

public class MinecraftStopStrategy implements StopStrategy {
    private static final Logger LOGGER = Logger.getLogger(MinecraftStopStrategy.class.getName());
    private final long gracefulTimeoutSeconds;

    public MinecraftStopStrategy(long gracefulTimeoutSeconds) {
        this.gracefulTimeoutSeconds = gracefulTimeoutSeconds;
    }

    @Override
    public void stop(ManagedProcess mp, ProcessKiller killer) throws Exception {
        if (mp.getStdinWriter() != null) {
            LOGGER.log(Level.INFO, "Sending 'stop' to Minecraft (PID={0})", mp.pid());
            mp.sendMinecraftCommand("stop");
            if (!mp.waitFor(gracefulTimeoutSeconds, TimeUnit.SECONDS)) {
                LOGGER.log(Level.WARNING, "Minecraft did not exit within {0}s — forcing kill.", gracefulTimeoutSeconds);
                killer.killProcessTree(mp.pid(), true);
            }
        } else {
            // No stdin available after agent restart; attempt gentle terminate before force
            LOGGER.log(Level.INFO, "No stdin available for Minecraft; attempting graceful terminate (PID={0})",
                    mp.pid());
            killer.killProcessTree(mp.pid(), false);
            if (mp.isAlive()) {
                killer.killProcessTree(mp.pid(), true);
            }
        }
    }
}
