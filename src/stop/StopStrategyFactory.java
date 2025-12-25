package stop;

import manager.ManagedProcess;

public class StopStrategyFactory {

    public StopStrategy forProcess(ManagedProcess mp) {
        if (mp.isMinecraft()) {
            return new MinecraftStopStrategy(300); // vent 300s før tvang
        }
        // Default: prøv grasiøst først
        return new GracefulStopStrategy(30); // vent 30s før tvang
    }

}
