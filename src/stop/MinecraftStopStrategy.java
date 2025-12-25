package stop;

import java.io.BufferedWriter;
import java.util.concurrent.TimeUnit;

import manager.ManagedProcess;
import process.ProcessKiller;

public class MinecraftStopStrategy implements StopStrategy {
    private final long gracefulTimeoutSeconds;

    public MinecraftStopStrategy(long gracefulTimeoutSeconds) {
        this.gracefulTimeoutSeconds = gracefulTimeoutSeconds;
    }

    @Override
    public void stop(ManagedProcess mp, ProcessKiller killer) throws Exception {
        if (mp.getStdinWriter() != null) {
            System.out.println("Sender 'stop' til Minecraft (PID=" + mp.pid() + ")");
            BufferedWriter w = mp.getStdinWriter();
            w.write("stop\n");
            w.flush();
            if (!mp.getProcess().waitFor(gracefulTimeoutSeconds, TimeUnit.SECONDS)) {
                System.out.println("Minecraft stoppet ikke innen " + gracefulTimeoutSeconds + "s — tvangskiller.");
                killer.killProcessTree(mp.pid(), true);
            }
        } else {
            // Hvis vi ikke har writer, prøv å lukke via taskkill først
            System.out.println("Ingen stdin tilgjengelig for Minecraft; forsøker taskkill.");
            killer.killProcessTree(mp.pid(), false);
            if (mp.isAlive()) {
                killer.killProcessTree(mp.pid(), true);
            }
        }
    }
}
