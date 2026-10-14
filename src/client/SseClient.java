package client;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.function.BiConsumer;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public class SseClient {
    private static final long BASE_RETRY_MS = 5000;
    private static final long MAX_RETRY_MS = 60000;

    private final HttpClient client;
    private final long deviceId;
    private final AuthService authService;
    private final BiConsumer<String, String> eventHandler;
    private final ScheduledExecutorService retryExecutor = Executors.newSingleThreadScheduledExecutor(
            daemonThreadFactory());
    private long retryDelayMs = BASE_RETRY_MS;

    public SseClient(long deviceId, AuthService authService, BiConsumer<String, String> eventHandler) {
        this.client = HttpClient.newHttpClient();
        this.deviceId = deviceId;
        this.authService = authService;
        this.eventHandler = eventHandler;
    }

    public void start() {
        connect();
    }

    private void connect() {
        try {
            String token = authService.getAccessToken();
            String url = AgentConfig.API_BASE_URL + "/devices/" + deviceId + "/events";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Accept", "text/event-stream")
                    .header("Authorization", "Bearer " + token)
                    .build();

            System.out.println("Connecting to SSE endpoint: " + url);

            client.sendAsync(request, HttpResponse.BodyHandlers.ofLines())
                    .thenAccept(this::processResponse)
                    .exceptionally(e -> {
                        System.err.println("Connection failed: " + e.getMessage());
                        retry();
                        return null;
                    });

        } catch (Exception e) {
            System.err.println("Error getting token or connecting: " + e.getMessage());
            retry();
        }
    }

    private void retry() {
        long waitMs = retryDelayMs + ThreadLocalRandom.current().nextLong(0, 500);
        retryDelayMs = Math.min(MAX_RETRY_MS, retryDelayMs * 2);
        retryExecutor.schedule(this::connect, waitMs, TimeUnit.MILLISECONDS);
    }

    private static ThreadFactory daemonThreadFactory() {
        return runnable -> {
            Thread thread = new Thread(runnable, "SseClient-Reconnect");
            thread.setDaemon(true);
            return thread;
        };
    }

    private void processResponse(HttpResponse<Stream<String>> response) {
        if (response.statusCode() != 200) {
            System.err.println("Server returned status: " + response.statusCode());
            retry();
            return;
        }

        System.out.println("Connected to SSE stream.");
        retryDelayMs = BASE_RETRY_MS; // reset backoff on successful connect

        final StringBuilder dataBuffer = new StringBuilder();
        final String[] eventName = { null };

        response.body().forEach(line -> {
            if (line.startsWith("event:")) {
                eventName[0] = line.substring(6).trim();
            } else if (line.startsWith("data:")) {
                dataBuffer.append(line.substring(5).trim());
            } else if (line.isEmpty()) {
                if (eventName[0] != null && dataBuffer.length() > 0) {
                    eventHandler.accept(eventName[0], dataBuffer.toString());
                }
                eventName[0] = null;
                dataBuffer.setLength(0);
            }
        });

        System.out.println("Stream closed. Reconnecting...");
        retry();
    }
}
