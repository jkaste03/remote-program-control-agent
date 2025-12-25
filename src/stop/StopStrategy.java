package stop;

import manager.ManagedProcess;
import process.ProcessKiller;

public interface StopStrategy {
    /**
     * Stopper prosessen på en definert måte. Kan kaste exceptions ved feil.
     */
    void stop(ManagedProcess mp, ProcessKiller killer) throws Exception;
}
