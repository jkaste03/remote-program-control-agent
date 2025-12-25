package manager;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import command.CommandAction;
import process.CommandBuilder;
import process.ProcessKiller;
import process.ProcessOutputLogger;
import program.ProgramHandler;
import program.ProgramHandlerFactory;
import shortcut.ShortcutInfo;
import shortcut.ShortcutResolver;

public class ProcessManager {
    private final Path shortcutFolder;
    private final ShortcutResolver shortcutResolver;
    private final CommandBuilder commandBuilder;
    private final ProcessKiller processKiller;
    private final Map<String, ManagedProcess> activeProcesses = new ConcurrentHashMap<>();
    private final ProgramHandlerFactory programHandlerFactory;

    public ProcessManager(Path shortcutFolder) {
        this.shortcutFolder = shortcutFolder;
        this.shortcutResolver = new ShortcutResolver();
        this.commandBuilder = new CommandBuilder();
        this.processKiller = new ProcessKiller();
        this.programHandlerFactory = new ProgramHandlerFactory();
    }

    public void handleCommand(String filename, CommandAction action) {
        if (action == CommandAction.START) {
            startProgram(filename);
        } else if (action == CommandAction.STOP) {
            stopProgram(filename);
        }
    }

    public void startProgram(String filename) {
        Path shortcutPath = shortcutFolder.resolve(filename);
        if (!shortcutPath.toFile().exists()) {
            System.out.println("Snarvei finnes ikke: " + shortcutPath);
            return;
        }

        ShortcutInfo info = shortcutResolver.resolveShortcut(shortcutPath.toFile());
        if (info == null || info.getTargetPath() == null || info.getTargetPath().isEmpty()) {
            System.out.println("Kunne ikke lese snarveien: " + shortcutPath);
            return;
        }

        var realFile = info.getTargetFile();
        if (!realFile.exists()) {
            System.out.println("Målfil finnes ikke: " + realFile.getAbsolutePath());
            return;
        }

        try {
            ProgramHandler handler = programHandlerFactory.getHandler(filename, realFile);

            ProcessBuilder pb = commandBuilder.buildProcessBuilder(realFile, info.getArguments(),
                    info.getWorkingDirectory());

            pb.redirectErrorStream(true);
            Process p = pb.start();

            ManagedProcess mp = new ManagedProcess(p, null, false, handler);
            handler.onStart(p, mp);
            
            activeProcesses.put(filename, mp);

            System.out.println(
                    "Startet: " + realFile.getAbsolutePath() + " PID=" + p.pid());

            // Start logger-tråd for stdout/err
            new Thread(new ProcessOutputLogger(mp, filename)).start();

        } catch (IOException e) {
            System.out.println("Feil ved start: " + e);
            e.printStackTrace();
        }
    }

    public void stopProgram(String filename) {
        ManagedProcess mp = activeProcesses.get(filename);
        if (mp == null) {
            System.out.println("Ingen aktiv prosess funnet for: " + filename);
            return;
        }

        Process p = mp.getProcess();
        if (!p.isAlive()) {
            activeProcesses.remove(filename);
            System.out.println("Prosessen er allerede avsluttet: " + filename);
            return;
        }

        System.out.println("Stopper " + filename + "...");
        try {
            mp.getHandler().stop(mp, processKiller);
        } catch (Exception e) {
            System.out.println("Feil ved stopp: " + e);
            processKiller.killProcessTree(p.pid(), true);
        } finally {
            activeProcesses.remove(filename);
        }
    }

    // For eksternt bruk: sjekk om en prosess er aktiv
    public boolean isActive(String filename) {
        ManagedProcess mp = activeProcesses.get(filename);
        return mp != null && mp.getProcess().isAlive();
    }
}
