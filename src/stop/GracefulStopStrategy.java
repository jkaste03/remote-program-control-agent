package stop;

import java.util.concurrent.TimeUnit;

import manager.ManagedProcess;
import process.ProcessKiller;

public class GracefulStopStrategy implements StopStrategy {
    private final long waitSeconds;

    public GracefulStopStrategy(long waitSeconds) {
        this.waitSeconds = waitSeconds;
    }

    @Override
    public void stop(ManagedProcess mp, ProcessKiller killer) throws Exception {
        System.out.println("Prøver å lukke prosess grasiøst (PID=" + mp.pid() + ")");
        killer.killProcessTree(mp.pid(), false);

        if (mp.isAlive()) {
            if (!mp.waitFor(waitSeconds, TimeUnit.SECONDS)) {
                System.out.println("Prosessen responderte ikke innen " + waitSeconds + "s — tvinger avslutning.");
                killer.killProcessTree(mp.pid(), true);
            }
        }
    }
}
