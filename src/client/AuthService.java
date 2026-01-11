package client;

import java.awt.Desktop;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.sun.net.httpserver.HttpServer;

public class AuthService {
    private final HttpClient client;
    private String cachedAccessToken;

    public AuthService() {
        this.client = HttpClient.newHttpClient();
    }

    public synchronized String getAccessToken() throws IOException, InterruptedException {
        // 1. Try to load refresh token and refresh
        String refreshToken = loadRefreshToken();
        if (refreshToken != null) {
            try {
                return refreshAccessToken(refreshToken);
            } catch (IOException e) {
                System.out.println("Failed to refresh token, initiating new login...");
            }
        }

        // 2. If failed, perform full login
        return performLogin();
    }

    public String getUserId() throws IOException, InterruptedException {
        String token = getAccessToken();
        if (token == null)
            return null;

        String[] parts = token.split("\\.");
        if (parts.length < 2)
            return null;

        try {
            String payload = new String(java.util.Base64.getUrlDecoder().decode(parts[1]));
            return SimpleJson.extractField(payload, "sub");
        } catch (IllegalArgumentException e) {
            System.err.println("Failed to decode JWT payload: " + e.getMessage());
            return null;
        }
    }

    private String performLogin() throws IOException, InterruptedException {
        String state = "state-" + System.currentTimeMillis();
        CountDownLatch latch = new CountDownLatch(1);
        final String[] authCode = { null };

        HttpServer server = null;
        try {
            server = HttpServer.create(new InetSocketAddress(8888), 0);
        } catch (IOException e) {
            System.err.println("Port 8888 is already in use. Please ensure no other instance of the agent is running.");
            throw e;
        }

        server.createContext("/callback", exchange -> {
            String query = exchange.getRequestURI().getQuery();
            String responseText = "Login successful! You can close this window.";
            if (query != null && query.contains("code=")) {
                for (String param : query.split("&")) {
                    String[] pair = param.split("=");
                    if (pair.length == 2 && pair[0].equals("code")) {
                        authCode[0] = pair[1];
                    }
                }
            } else {
                responseText = "Login failed! No code found.";
            }

            exchange.sendResponseHeaders(200, responseText.length());
            OutputStream os = exchange.getResponseBody();
            os.write(responseText.getBytes());
            os.close();
            latch.countDown();
        });
        server.start();

        String loginUrl = AgentConfig.AUTH_URL +
                "?response_type=code" +
                "&client_id=" + AgentConfig.CLIENT_ID +
                "&redirect_uri=" + URLEncoder.encode(AgentConfig.REDIRECT_URI, StandardCharsets.UTF_8) +
                "&scope=" + URLEncoder.encode(AgentConfig.SCOPE, StandardCharsets.UTF_8) +
                "&state=" + state +
                "&prompt=login";

        System.out.println("Opening browser for login: " + loginUrl);
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(URI.create(loginUrl));
        } else {
            System.out.println("Please open the URL above in your browser.");
        }

        // Wait for callback (timeout 5 mins)
        if (!latch.await(5, TimeUnit.MINUTES)) {
            server.stop(0);
            throw new IOException("Login timed out.");
        }
        server.stop(0);

        if (authCode[0] == null) {
            throw new IOException("Failed to obtain authorization code.");
        }

        return exchangeCodeForTokens(authCode[0]);
    }

    private String exchangeCodeForTokens(String code) throws IOException, InterruptedException {
        String data = "grant_type=authorization_code" +
                "&client_id=" + AgentConfig.CLIENT_ID +
                "&code=" + code +
                "&redirect_uri=" + AgentConfig.REDIRECT_URI;

        return requestTokens(data);
    }

    private String refreshAccessToken(String refreshToken) throws IOException, InterruptedException {
        String data = "grant_type=refresh_token" +
                "&client_id=" + AgentConfig.CLIENT_ID +
                "&refresh_token=" + refreshToken;

        return requestTokens(data);
    }

    private String requestTokens(String formData) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(AgentConfig.TOKEN_URL))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(formData))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            String json = response.body();
            String accessToken = extractToken(json, "access_token");
            String newRefreshToken = extractToken(json, "refresh_token");

            if (newRefreshToken != null) {
                saveRefreshToken(newRefreshToken);
            }

            this.cachedAccessToken = accessToken;
            return accessToken;
        } else {
            throw new IOException("Token request failed: " + response.statusCode() + " " + response.body());
        }
    }

    private String extractToken(String json, String tokenName) {
        String key = "\"" + tokenName + "\":\"";
        int start = json.indexOf(key);
        if (start == -1)
            return null;
        start += key.length();
        int end = json.indexOf("\"", start);
        if (end == -1)
            return null;
        return json.substring(start, end);
    }

    private void saveRefreshToken(String token) {
        try {
            Files.writeString(Paths.get(AgentConfig.TOKEN_FILE), token);
        } catch (IOException e) {
            System.err.println("Failed to save refresh token: " + e.getMessage());
        }
    }

    private String loadRefreshToken() {
        Path path = Paths.get(AgentConfig.TOKEN_FILE);
        if (Files.exists(path)) {
            try {
                return Files.readString(path).trim();
            } catch (IOException e) {
                System.err.println("Failed to read refresh token: " + e.getMessage());
            }
        }
        return null;
    }

    public void logout() {
        try {
            Files.deleteIfExists(Paths.get(AgentConfig.TOKEN_FILE));
            cachedAccessToken = null;
            System.out.println("Refresh token deleted.");
        } catch (IOException e) {
            System.err.println("Failed to delete refresh token: " + e.getMessage());
        }
    }
}
