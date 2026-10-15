package core;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.*;
import java.util.regex.*;
import client.AgentConfig;
import client.DeviceClient;
import manager.ProcessManager;

/** Reconciles the website's access list with a running Minecraft console. */
public class WhitelistSyncService {
    private static final Logger LOG = Logger.getLogger(WhitelistSyncService.class.getName());
    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_]{3,16}");
    private static final Pattern LIST = Pattern.compile("(?i)There are (\\d+) whitelisted players?(?: out of .*?)?:\\s*(.*)");
    private final DeviceClient client;
    private final ProcessManager processes;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Minecraft-Whitelist-Sync");
        t.setDaemon(true);
        return t;
    });
    private final Map<String, CompletableFuture<Set<String>>> pending = new ConcurrentHashMap<>();

    public WhitelistSyncService(DeviceClient client, ProcessManager processes) {
        this.client = client;
        this.processes = processes;
    }

    public void syncAll(List<String> filenames) {
        if (filenames != null) filenames.forEach(this::sync);
    }

    public void sync(String filename) {
        if (filename == null || !processes.isMinecraftRunning(filename)) return;
        worker.execute(() -> reconcile(filename));
    }

    /** Called on Minecraft's stdout thread, never blocks that thread. */
    public void onOutputLine(String filename, String line) {
        CompletableFuture<Set<String>> future = pending.get(filename);
        if (future == null) return;
        Matcher matcher = LIST.matcher(line);
        if (!matcher.find()) return;
        int count = Integer.parseInt(matcher.group(1));
        String names = matcher.group(2).trim();
        Set<String> actual = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        if (!names.isEmpty()) {
            for (String name : names.split(",\\s*")) actual.add(name.trim());
        }
        if (actual.size() == count) future.complete(actual);
    }

    private void reconcile(String filename) {
        if (!processes.isMinecraftRunning(filename)) return;
        CompletableFuture<Set<String>> response = new CompletableFuture<>();
        try {
            List<String> desiredNames = client.getWhitelist(AgentConfig.DEVICE_ID, filename);
            if (desiredNames == null) return;
            Set<String> desired = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            for (String name : desiredNames) {
                if (name == null || !USERNAME.matcher(name).matches()) {
                    LOG.warning("Invalid Minecraft username ignored for " + filename + ": " + name);
                    return; // Never remove entries when source data is invalid.
                }
                desired.add(name);
            }
            pending.put(filename, response);
            if (!processes.sendMinecraftCommand(filename, "whitelist list")) return;
            Set<String> actual = response.get(10, TimeUnit.SECONDS);
            for (String name : actual) {
                if (!desired.contains(name)) {
                    if (!processes.sendMinecraftCommand(filename, "whitelist remove " + name)) return;
                }
            }
            for (String name : desired) {
                if (!actual.contains(name)) {
                    if (!processes.sendMinecraftCommand(filename, "whitelist add " + name)) return;
                }
            }
            LOG.info("Minecraft whitelist commands synchronized for " + filename);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException | ExecutionException | TimeoutException e) {
            LOG.log(Level.WARNING, "Minecraft whitelist sync failed for " + filename, e);
        } finally {
            pending.remove(filename, response);
        }
    }
}
