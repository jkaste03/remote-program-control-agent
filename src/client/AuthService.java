package client;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;

/**
 * Authenticates directly against the application's local user store.
 */
public class AuthService {
    private static final long TOKEN_EXPIRY_SKEW_SECONDS = 30;

    private final HttpClient client = HttpClient.newHttpClient();
    private final ConsoleInputHandler inputHandler;
    private String cachedAccessToken;
    private String username;
    private char[] password;

    public AuthService(ConsoleInputHandler inputHandler) {
        this.inputHandler = inputHandler;
    }

    public synchronized String getAccessToken() throws IOException, InterruptedException {
        if (cachedAccessToken != null && !isExpired(cachedAccessToken)) {
            return cachedAccessToken;
        }

        String refreshToken = loadRefreshToken();
        if (refreshToken != null) {
            try {
                return refreshAccessToken(refreshToken);
            } catch (InvalidRefreshTokenException e) {
                System.out.println("Stored credentials are no longer valid; logging in again.");
                deleteRefreshToken();
            }
        }
        return login();
    }

    private boolean isExpired(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length < 2) {
                return true;
            }
            String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            String expiresAt = SimpleJson.extractField(payload, "exp");
            return expiresAt == null
                    || Long.parseLong(expiresAt) <= (System.currentTimeMillis() / 1000) + TOKEN_EXPIRY_SKEW_SECONDS;
        } catch (IllegalArgumentException e) {
            return true;
        }
    }

    public String getUserId() throws IOException, InterruptedException {
        String token = getAccessToken();
        String[] parts = token.split("\\.");
        if (parts.length < 2) {
            throw new IOException("Authentication response contained an invalid access token.");
        }
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        String userId = SimpleJson.extractField(payload, "sub");
        if (userId == null || userId.isBlank()) {
            throw new IOException("Access token does not contain a user id.");
        }
        return userId;
    }

    private String login() throws IOException, InterruptedException {
        if (username == null) {
            username = inputHandler.askForUsername();
        }
        if (password == null) {
            password = inputHandler.askForPassword();
        }
        String passwordText = new String(password);
        try {
            return requestToken(AgentConfig.LOGIN_URL,
                    "{\"username\":\"" + SimpleJson.escapeJson(username) + "\",\"password\":\""
                            + SimpleJson.escapeJson(passwordText) + "\"}");
        } finally {
            passwordText = null;
        }
    }

    private String refreshAccessToken(String refreshToken) throws IOException, InterruptedException {
        return requestToken(AgentConfig.REFRESH_URL,
                "{\"refreshToken\":\"" + SimpleJson.escapeJson(refreshToken) + "\"}");
    }

    private String requestToken(String url, String body) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200 && response.statusCode() != 201) {
            throw authenticationError(url, response.statusCode(), response.body());
        }

        String accessToken = SimpleJson.extractField(response.body(), "accessToken");
        String newRefreshToken = SimpleJson.extractField(response.body(), "refreshToken");
        if (accessToken == null || newRefreshToken == null) {
            throw new IOException("Authentication response did not contain both tokens.");
        }
        saveRefreshToken(newRefreshToken);
        cachedAccessToken = accessToken;
        return accessToken;
    }

    private IOException authenticationError(String url, int statusCode, String responseBody) {
        if (statusCode == 401 && url.equals(AgentConfig.LOGIN_URL)) {
            return new InvalidCredentialsException("Login failed: username or password is incorrect.");
        }
        if (statusCode == 401 && url.equals(AgentConfig.REFRESH_URL)) {
            return new InvalidRefreshTokenException("The saved refresh token has expired or was revoked.");
        }

        if (statusCode >= 500) {
            return new IOException("The authentication server returned HTTP " + statusCode
                    + " while processing the login. Check the server log.");
        }
        return new IOException("Authentication request failed with HTTP " + statusCode + ".");
    }

    private static final class InvalidRefreshTokenException extends IOException {
        private InvalidRefreshTokenException(String message) {
            super(message);
        }
    }

    public static final class InvalidCredentialsException extends IOException {
        private InvalidCredentialsException(String message) {
            super(message);
        }
    }

    private void saveRefreshToken(String token) throws IOException {
        AgentConfig.ensureDataDirectory();

        Files.writeString(
                AgentConfig.TOKEN_FILE,
                token,
                StandardCharsets.UTF_8
        );
    }

    private String loadRefreshToken() {
        Path path = AgentConfig.TOKEN_FILE;

        try {
            return Files.exists(path)
                    ? Files.readString(path, StandardCharsets.UTF_8).trim()
                    : null;
        } catch (IOException e) {
            System.err.println(
                    "Failed to read refresh token: " + e.getMessage()
            );
            return null;
        }
    }

    private void deleteRefreshToken() throws IOException {
        Files.deleteIfExists(AgentConfig.TOKEN_FILE);
    }

    public synchronized void logout() {
        try {
            deleteRefreshToken();
            cachedAccessToken = null;
            username = null;
            if (password != null) {
                java.util.Arrays.fill(password, '\0');
                password = null;
            }
            System.out.println("Credentials deleted. The next start will require a new login.");
        } catch (IOException e) {
            System.err.println("Failed to delete credentials: " + e.getMessage());
        }
    }
}
