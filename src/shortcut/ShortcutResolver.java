package shortcut;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;

public class ShortcutResolver {

    /**
     * Leser innholdet i en Windows .lnk snarvei ved å bruke PowerShell COM WScript.
     * Returnerer ShortcutInfo eller null ved feil.
     */
    public ShortcutInfo resolveShortcut(File shortcutFile) {
        try {
            String quoted = shortcutFile.getAbsolutePath().replace("\\", "\\\\");
            String ps = "$s=(New-Object -ComObject WScript.Shell).CreateShortcut('" +
                    quoted
                    + "'); Write-Output $s.TargetPath; Write-Output $s.Arguments; Write-Output $s.WorkingDirectory";

            ProcessBuilder builder = new ProcessBuilder("powershell", "-NoProfile", "-Command", ps);
            Process proc = builder.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                String target = reader.readLine();
                String args = reader.readLine();
                String workdir = reader.readLine();
                return new ShortcutInfo(target, args == null ? "" : args.trim(), workdir == null ? "" : workdir.trim());
            }
        } catch (Exception e) {
            System.out.println("Feil ved lesing av snarvei: " + e);
            return null;
        }
    }
}
