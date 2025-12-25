package process;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import manager.ManagedProcess;

public class ProcessOutputLogger implements Runnable {
    private final ManagedProcess mp;
    private final String name;

    public ProcessOutputLogger(ManagedProcess mp, String name) {
        this.mp = mp;
        this.name = name;
    }

    @Override
    public void run() {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(mp.getProcess().getInputStream()))) {
            String line;
            while ((line = r.readLine()) != null) {
                System.out.println("[" + name + "] " + line);
            }
        } catch (IOException ignored) {
        }
    }
}
