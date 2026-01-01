package manager;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.concurrent.CompletableFuture;

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
    private final Path stateFilePath;
    private ProcessExitListener exitListener;
    private static final String STATE_FILE_NAME = "active_processes.txt";
    private static final boolean WINDOWS = System.getProperty("os.name").toLowerCase().contains("win");
    private static final Logger LOGGER = Logger.getLogger(ProcessManager.class.getName());

    public ProcessManager(Path shortcutFolder) {
        this.shortcutFolder = shortcutFolder;
        this.shortcutResolver = new ShortcutResolver();
        this.commandBuilder = new CommandBuilder();
        this.processKiller = new ProcessKiller();
        this.programHandlerFactory = new ProgramHandlerFactory();
        this.stateFilePath = shortcutFolder.resolve(STATE_FILE_NAME);
        loadProcessList();
    }

    public boolean handleCommand(long programId, String filename, CommandAction action) {
        if (action == CommandAction.START) {
            return startProgram(programId, filename);
        } else if (action == CommandAction.STOP) {
            stopProgram(filename);
            return true;
        }
        return false;
    }

    public boolean startProgram(long programId, String filename) {
        ProgramTarget target = resolveProgramTarget(filename);
        if (target == null || !target.realFile.exists()) {
            return false;
        }

        try {
            ProgramHandler handler = programHandlerFactory.getHandler(filename, target.realFile);

            ProcessBuilder pb = commandBuilder.buildProcessBuilder(target.realFile, target.arguments,
                    target.workingDirectory);

            pb.redirectErrorStream(true);
            Process p = pb.start();

            ManagedProcess mp = new ManagedProcess(p, null, false, handler, programId);
            handler.onStart(p, mp);

            registerActiveProcess(filename, mp);

            LOGGER.log(Level.INFO, () -> "Started: " + target.realFile.getAbsolutePath() + " PID=" + p.pid());

            // Start logger-tråd for stdout/err
            new Thread(new ProcessOutputLogger(mp, filename)).start();
            return true;

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to start program", e);
            return false;
        }
    }

    public void stopProgram(String filename) {
        ManagedProcess mp = activeProcesses.get(filename);
        if (mp == null) {
            LOGGER.log(Level.INFO, () -> "No active process found for: " + filename);
            return; // Allerede stoppet
        }

        if (!mp.isAlive()) {
            activeProcesses.remove(filename);
            LOGGER.log(Level.INFO, () -> "Process is already terminated: " + filename);
            return; // Allerede stoppet
        }

        LOGGER.log(Level.INFO, () -> "Stopping " + filename + "...");

        // Start en ny tråd for å stoppe prosessen, slik at vi ikke blokkerer
        // hovedtråden
        // hvis prosessen henger (f.eks. pga "pause" i batch-script).
        new Thread(() -> {
            try {
                mp.markStoppedByAgent();
                mp.getHandler().stop(mp, processKiller);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error while stopping " + filename, e);
                processKiller.killProcessTree(mp.pid(), true);
            } finally {
                // Fjern fra listen over aktive prosesser når den faktisk er stoppet (eller
                // forsøkt stoppet)
                removeActiveProcess(filename, mp);
            }
        }).start();
    }

    // For eksternt bruk: sjekk om en prosess er aktiv
    public boolean isActive(String filename) {
        ManagedProcess mp = activeProcesses.get(filename);
        return mp != null && mp.isAlive();
    }

    private synchronized void saveProcessList() {
        try (PrintWriter writer = new PrintWriter(
                new BufferedWriter(Files.newBufferedWriter(stateFilePath, StandardCharsets.UTF_8)))) {
            for (Map.Entry<String, ManagedProcess> entry : activeProcesses.entrySet()) {
                ManagedProcess mp = entry.getValue();
                writer.println(entry.getKey() + "=" + mp.pid() + ":" + mp.getProgramId());
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Could not save process list", e);
        }
    }

    private void loadProcessList() {
        if (!Files.exists(stateFilePath)) {
            return;
        }

        try (BufferedReader reader = Files.newBufferedReader(stateFilePath, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                handleProcessLine(line, false);
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Could not load process list", e);
        }
    }

    public void reconcilePersistedProcesses() {
        if (!Files.exists(stateFilePath)) {
            return;
        }

        try (BufferedReader reader = Files.newBufferedReader(stateFilePath, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                handleProcessLine(line, true);
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Could not reconcile process list", e);
        }
    }

    private void handleProcessLine(String line, boolean notifyOnMissing) {
        String[] parts = line.split("=", 2);
        if (parts.length != 2) {
            return;
        }
        String filename = parts[0];
        try {
            String[] pidAndProgram = parts[1].split(":", 2);
            long pid = Long.parseLong(pidAndProgram[0]);
            long programId = pidAndProgram.length == 2 ? parseLongOrDefault(pidAndProgram[1], -1) : -1;
            restoreProcess(filename, pid, programId, notifyOnMissing);
        } catch (NumberFormatException e) {
            // Ignore invalid lines
        }
    }

    private void restoreProcess(String filename, long pid, long programId, boolean notifyOnMissing) {
        ProcessHandle handle = ProcessHandle.of(pid).orElse(null);
        if (handle == null || !handle.isAlive()) {
            if (notifyOnMissing && exitListener != null && programId > 0) {
                exitListener.onProcessExit(programId, filename);
            }
            return;
        }

        ProgramTarget target = resolveProgramTarget(filename);
        File targetFile = target != null ? target.realFile : shortcutFolder.resolve(filename).toFile();

        ProgramHandler handler = programHandlerFactory.getHandler(filename, targetFile);
        ManagedProcess mp = new ManagedProcess(pid, false, handler, programId);

        if (handler instanceof program.MinecraftProgramHandler) {
            mp.setMinecraft(true);
        }

        registerActiveProcess(filename, mp);
        LOGGER.log(Level.INFO, () -> "Restored process: " + filename + " (PID=" + pid + ")");
    }

    private void registerActiveProcess(String filename, ManagedProcess mp) {
        activeProcesses.put(filename, mp);
        watchProcess(filename, mp);
        saveProcessList();
    }

    private void removeActiveProcess(String filename, ManagedProcess mp) {
        boolean removed = activeProcesses.remove(filename, mp);
        if (removed) {
            saveProcessList();
        }
    }

    private void watchProcess(String filename, ManagedProcess mp) {
        ProcessHandle handle = mp.getProcessHandle();
        if (handle == null) {
            return;
        }
        CompletableFuture<ProcessHandle> onExit = handle.onExit();
        onExit.thenRun(() -> {
            removeActiveProcess(filename, mp);
            if (exitListener != null && !mp.isStoppedByAgent() && mp.getProgramId() > 0) {
                exitListener.onProcessExit(mp.getProgramId(), filename);
            }
        });
    }

    private ProgramTarget resolveProgramTarget(String filename) {
        Path path = shortcutFolder.resolve(filename);
        File file = path.toFile();

        if (!file.exists()) {
            LOGGER.log(Level.WARNING, () -> "File does not exist: " + path);
            return null;
        }

        if (WINDOWS && filename.toLowerCase().endsWith(".lnk")) {
            ShortcutInfo info = shortcutResolver.resolveShortcut(file);
            if (info == null || info.getTargetPath() == null || info.getTargetPath().isEmpty()) {
                LOGGER.log(Level.WARNING, () -> "Could not read shortcut: " + path);
                return null;
            }
            return new ProgramTarget(info.getTargetFile(), info.getArguments(), info.getWorkingDirectory());
        }

        String workingDirectory = file.getParent();
        return new ProgramTarget(file, "", workingDirectory == null ? "" : workingDirectory);
    }

    private static final class ProgramTarget {
        private final File realFile;
        private final String arguments;
        private final String workingDirectory;

        private ProgramTarget(File realFile, String arguments, String workingDirectory) {
            this.realFile = realFile;
            this.arguments = arguments;
            this.workingDirectory = workingDirectory;
        }
    }

    public void setProcessExitListener(ProcessExitListener listener) {
        this.exitListener = listener;
    }

    @FunctionalInterface
    public interface ProcessExitListener {
        void onProcessExit(long programId, String filename);
    }

    private long parseLongOrDefault(String value, long defaultValue) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
