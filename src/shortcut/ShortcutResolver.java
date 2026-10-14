package shortcut;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;

public class ShortcutResolver {

    /**
     * Resolves the target of a shortcut file.
     * - Windows .lnk: Uses PowerShell to extract target.
     * - Symlinks: Resolves the symbolic link.
     * - Others: Returns the file itself as the target.
     */
    public ShortcutInfo resolveShortcut(File shortcutFile) {
        String fileName = shortcutFile.getName().toLowerCase();

        // 1. Handle Windows Shortcuts (.lnk)
        if (fileName.endsWith(".lnk") && isWindows()) {
            return resolveWindowsShortcut(shortcutFile);
        }

        // 2. Handle Symbolic Links (Linux/Mac/Windows)
        if (Files.isSymbolicLink(shortcutFile.toPath())) {
            try {
                Path target = Files.readSymbolicLink(shortcutFile.toPath());
                if (!target.isAbsolute()) {
                    target = shortcutFile.toPath().getParent().resolve(target);
                }
                target = target.toAbsolutePath().normalize();
                // For symlinks, we assume no extra args/workdir unless we parse them from
                // somewhere else
                return new ShortcutInfo(target.toString(), "", target.getParent().toString());
            } catch (Exception e) {
                System.err.println("Failed to resolve symlink: " + e.getMessage());
                return null;
            }
        }

        // 3. Default: Treat the file itself as the program (Script, Binary, etc.)
        // This allows putting .sh, .py, or binaries directly in the folder.
        return new ShortcutInfo(shortcutFile.getAbsolutePath(), "", shortcutFile.getParent());
    }

    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }

    private ShortcutInfo resolveWindowsShortcut(File shortcutFile) {
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
