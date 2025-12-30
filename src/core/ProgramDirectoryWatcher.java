package core;

import java.io.IOException;
import java.nio.file.*;

public class ProgramDirectoryWatcher {
    private final Path folder;
    private final Runnable onChange;
    private WatchService watchService;
    private Thread watcherThread;
    private volatile boolean running = false;

    public ProgramDirectoryWatcher(Path folder, Runnable onChange) {
        this.folder = folder;
        this.onChange = onChange;
    }

    public void start() {
        if (running)
            return;

        try {
            watchService = FileSystems.getDefault().newWatchService();
            folder.register(watchService,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_DELETE,
                    StandardWatchEventKinds.ENTRY_MODIFY);
        } catch (IOException e) {
            System.err.println("Failed to initialize file watcher: " + e.getMessage());
            return;
        }

        running = true;
        watcherThread = new Thread(this::watchLoop, "ProgramDirectoryWatcher");
        watcherThread.setDaemon(true);
        watcherThread.start();
        System.out.println("Started monitoring " + folder + " for changes.");
    }

    public void stop() {
        running = false;
        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException e) {
                // ignore
            }
        }
        if (watcherThread != null) {
            watcherThread.interrupt();
        }
    }

    private void watchLoop() {
        while (running) {
            try {
                // Wait for key
                WatchKey key = watchService.take();

                // Debounce: Sleep for a short duration to allow subsequent events (like file
                // copy/move) to settle
                Thread.sleep(1000);

                // Poll and discard any other events that occurred during the sleep
                // This prevents multiple syncs for a single batch of file operations
                for (WatchEvent<?> event : key.pollEvents()) {
                    // Just consuming events
                }

                boolean valid = key.reset();
                if (!valid) {
                    System.err.println("Directory watcher key is no longer valid. Stopping watcher.");
                    running = false;
                    break;
                }

                // Trigger the callback
                System.out.println("Detected changes in shortcut folder. Triggering sync...");
                try {
                    onChange.run();
                } catch (Exception e) {
                    System.err.println("Error executing sync callback: " + e.getMessage());
                }

            } catch (InterruptedException e) {
                running = false;
                Thread.currentThread().interrupt();
            } catch (ClosedWatchServiceException e) {
                running = false;
            } catch (Exception e) {
                System.err.println("Unexpected error in watcher loop: " + e.getMessage());
            }
        }
    }
}
