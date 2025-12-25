package process;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class CommandBuilder {

    /**
     * Bygger en ProcessBuilder for å starte målfilen (exe, bat, cmd, etc).
     * For .bat/.cmd: bruker "cmd /c start "" /wait <path> <args>" for å la
     * kommandoen holde seg i eget vindu.
     */
    public ProcessBuilder buildProcessBuilder(File realFile, String arguments, String workingDirectory) {
        String lc = realFile.getName().toLowerCase(Locale.ROOT);
        List<String> cmd = new ArrayList<>();

        if (lc.endsWith(".bat") || lc.endsWith(".cmd")) {
            cmd.add("cmd");
            cmd.add("/c");
            cmd.add("start");
            cmd.add("\"\"");
            cmd.add("/wait");
            cmd.add(realFile.getAbsolutePath());
            if (arguments != null && !arguments.isBlank()) {
                String[] parts = splitArguments(arguments);
                cmd.addAll(Arrays.asList(parts));
            }
        } else {
            cmd.add(realFile.getAbsolutePath());
            if (arguments != null && !arguments.isBlank()) {
                String[] parts = splitArguments(arguments);
                cmd.addAll(Arrays.asList(parts));
            }
        }

        ProcessBuilder pb = new ProcessBuilder(cmd);
        if (workingDirectory != null && !workingDirectory.isBlank()) {
            pb.directory(new File(workingDirectory));
        } else {
            pb.directory(realFile.getParentFile());
        }
        return pb;
    }

    // Enkel splitting; kan byttes ut med en robust parser ved behov
    private String[] splitArguments(String args) {
        return args.trim().split(" ");
    }
}
