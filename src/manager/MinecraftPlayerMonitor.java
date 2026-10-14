package manager;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Periodically queries a Minecraft server through its console and stops it
 * after four consecutive player-count results of zero.
 */
public final class MinecraftPlayerMonitor implements AutoCloseable {
    private static final Logger LOGGER = Logger.getLogger(MinecraftPlayerMonitor.class.getName());

    private static final long CHECK_INTERVAL_MINUTES = 5;
    private static final long RESPONSE_TIMEOUT_SECONDS = 30;
    private static final int EMPTY_CHECKS_BEFORE_STOP = 4;

    /*
     * Supports common vanilla / Paper console formats:
     * "There are 0 of a max of 20 players online:"
     * "There are 0/20 players online:"
     * "There are 0 players online:"
     */
    private static final Pattern PLAYER_COUNT_PATTERN = Pattern.compile(
            "(?i)\\bthere are\\s+(\\d+)(?:\\s*/\\s*\\d+|\\s+of a max of\\s+\\d+)?\\s+players online\\b");

    private final String filename;
    private final ManagedProcess managedProcess;
    private final Runnable stopServer;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Minecraft-player-monitor");
        thread.setDaemon(true);
        return thread;
    });

    private boolean started;
    private boolean awaitingResponse;
    private boolean stopping;
    private boolean closed;
    private int consecutiveEmptyChecks;
    private long requestSequence;
    private ScheduledFuture<?> responseTimeout;

    public MinecraftPlayerMonitor(String filename, ManagedProcess managedProcess, Runnable stopServer) {
        this.filename = filename;
        this.managedProcess = managedProcess;
        this.stopServer = stopServer;
    }

    /**
     * Starts the five-minute schedule once the Minecraft server is ready.
     */
    public synchronized void start() {
        if (started || closed || stopping) {
            return;
        }
        if (!managedProcess.isAlive()) {
            close();
            return;
        }

        started = true;
        LOGGER.log(Level.INFO,
                "Minecraft player monitor started for {0}: checking every {1} minutes; stopping after {2} consecutive empty checks.",
                new Object[] { filename, CHECK_INTERVAL_MINUTES, EMPTY_CHECKS_BEFORE_STOP });

        scheduler.scheduleWithFixedDelay(
                this::checkPlayerCount,
                CHECK_INTERVAL_MINUTES,
                CHECK_INTERVAL_MINUTES,
                TimeUnit.MINUTES);
    }

    /**
     * Receives lines from the process output reader. Only a valid response while
     * a list request is pending is counted.
     */
    public void onOutputLine(String line) {
        Matcher matcher = PLAYER_COUNT_PATTERN.matcher(line);
        if (!matcher.find()) {
            return;
        }

        final int playerCount;
        try {
            playerCount = Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException e) {
            return;
        }

        boolean stopNow = false;
        synchronized (this) {
            if (closed || stopping || managedProcess.isStoppedByAgent() || !awaitingResponse) {
                return;
            }

            awaitingResponse = false;
            cancelResponseTimeout();

            if (playerCount == 0) {
                consecutiveEmptyChecks++;
                LOGGER.log(Level.INFO,
                        "Minecraft player check for {0}: 0 players ({1}/{2} consecutive empty checks).",
                        new Object[] { filename, consecutiveEmptyChecks, EMPTY_CHECKS_BEFORE_STOP });

                if (consecutiveEmptyChecks >= EMPTY_CHECKS_BEFORE_STOP) {
                    stopping = true;
                    stopNow = true;
                }
            } else {
                int previousEmptyChecks = consecutiveEmptyChecks;
                consecutiveEmptyChecks = 0;
                LOGGER.log(Level.INFO,
                        "Minecraft player check for {0}: {1} player(s). Reset consecutive empty checks from {2} to 0.",
                        new Object[] { filename, playerCount, previousEmptyChecks });
            }
        }

        if (stopNow) {
            LOGGER.log(Level.WARNING,
                    "Minecraft server {0} has reported zero players for {1} consecutive checks. Requesting graceful stop.",
                    new Object[] { filename, EMPTY_CHECKS_BEFORE_STOP });
            scheduler.shutdownNow();
            try {
                stopServer.run();
            } catch (RuntimeException e) {
                LOGGER.log(Level.SEVERE, "Could not request automatic Minecraft stop for " + filename, e);
            }
        }
    }

    private void checkPlayerCount() {
        if (!managedProcess.isAlive() || managedProcess.isStoppedByAgent()) {
            close();
            return;
        }

        final long requestId;
        synchronized (this) {
            if (closed || stopping || !started) {
                return;
            }
            if (awaitingResponse) {
                LOGGER.log(Level.WARNING,
                        "Skipping player check for {0}: the previous list command is still awaiting a response.",
                        filename);
                return;
            }

            awaitingResponse = true;
            requestId = ++requestSequence;
            responseTimeout = scheduler.schedule(
                    () -> onResponseTimeout(requestId),
                    RESPONSE_TIMEOUT_SECONDS,
                    TimeUnit.SECONDS);
        }

        try {
            managedProcess.sendMinecraftCommand("list");
            LOGGER.log(Level.INFO, "Requested Minecraft player count for {0}.", filename);
        } catch (IOException e) {
            clearPendingRequest(requestId);
            LOGGER.log(Level.WARNING,
                    "Could not query Minecraft player count for " + filename + "; this check will not be counted.",
                    e);
        }
    }

    private void onResponseTimeout(long requestId) {
        synchronized (this) {
            if (closed || stopping || !awaitingResponse || requestSequence != requestId) {
                return;
            }
            awaitingResponse = false;
            responseTimeout = null;
        }

        LOGGER.log(Level.WARNING,
                "No valid player-count response from Minecraft server {0} within {1} seconds. This check will not count as empty.",
                new Object[] { filename, RESPONSE_TIMEOUT_SECONDS });
    }

    private void clearPendingRequest(long requestId) {
        synchronized (this) {
            if (!awaitingResponse || requestSequence != requestId) {
                return;
            }
            awaitingResponse = false;
            cancelResponseTimeout();
        }
    }

    private void cancelResponseTimeout() {
        if (responseTimeout != null) {
            responseTimeout.cancel(false);
            responseTimeout = null;
        }
    }

    @Override
    public void close() {
        boolean logClose;
        synchronized (this) {
            if (closed) {
                return;
            }
            closed = true;
            cancelResponseTimeout();
            logClose = started;
        }
        scheduler.shutdownNow();
        if (logClose) {
            LOGGER.log(Level.INFO, "Minecraft player monitor stopped for {0}.", filename);
        }
    }
}
