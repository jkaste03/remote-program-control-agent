package process;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class ProcessKiller {

    /**
     * Kjører Windows taskkill for å lukke prosess-treet. Hvis force==false så
     * sendes kun /T (WM_CLOSE)
     */
    public void killProcessTree(long pid, boolean force) {
        try {
            List<String> cmd = new ArrayList<>();
            cmd.add("taskkill");
            cmd.add("/PID");
            cmd.add(String.valueOf(pid));
            if (force) {
                cmd.add("/F");
                cmd.add("/T");
            } else {
                cmd.add("/T");
            }

            ProcessBuilder pb = new ProcessBuilder(cmd);
            Process taskkill = pb.start();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(taskkill.getInputStream()))) {
                String l;
                while ((l = r.readLine()) != null)
                    System.out.println(l);
            }
            taskkill.waitFor();
        } catch (Exception e) {
            System.out.println("Kunne ikke kjøre taskkill: " + e);
        }
    }
}
