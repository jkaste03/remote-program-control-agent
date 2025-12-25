package stop;

import manager.ManagedProcess;
import process.ProcessKiller;

public class ForceStopStrategy implements StopStrategy {
    @Override
    public void stop(ManagedProcess mp, ProcessKiller killer) {
        System.out.println("Tvinger avslutning (PID=" + mp.pid() + ")");
        killer.killProcessTree(mp.pid(), true);
    }
}
