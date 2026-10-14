package client;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import java.util.List;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class DeviceClient {
    private final AuthService authService;
    private final HttpClient httpClient;
    private final String baseUrl;

    public DeviceClient(AuthService authService) {
        this.authService = authService;
        this.httpClient = HttpClient.newHttpClient();
        this.baseUrl = AgentConfig.API_BASE_URL + "/devices";
    }

    public DeviceResponse getDeviceDetails(long id, boolean includePrograms) throws IOException, InterruptedException {
        String token = authService.getAccessToken();
        String url = baseUrl + "/" + id + "?includePrograms=" + includePrograms;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            return parseDeviceResponse(response.body());
        } else if (response.statusCode() == 404) {
            return null;
        } else {
            throw new IOException("Failed to get device details: " + response.statusCode() + " " + response.body());
        }
    }

    public DeviceResponse createDevice(String name, String publicIp, int wolPort, String macAddress)
            throws IOException, InterruptedException {
        String token = authService.getAccessToken();
        String json = SimpleJson.createDeviceRequest(name, publicIp, wolPort, macAddress);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 201) {
                return parseDeviceResponse(response.body());
            } else {
                throw new IOException("Failed to create device: " + response.statusCode() + " " + response.body());
            }
        } catch (IOException e) {
            System.err.println("Connection error when creating device at " + baseUrl);
            System.err.println("Please ensure the server is running and accessible.");
            throw e;
        }
    }

    public DeviceResponse updateDevice(long id, String name, String publicIp, int wolPort, String macAddress)
            throws IOException, InterruptedException {
        String token = authService.getAccessToken();
        String json = SimpleJson.createDeviceRequest(name, publicIp, wolPort, macAddress);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/" + id))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            return parseDeviceResponse(response.body());
        } else {
            throw new IOException("Failed to update device: " + response.statusCode() + " " + response.body());
        }
    }

    public void updateProgramStatus(long programId, String status) throws IOException, InterruptedException {
        String token = authService.getAccessToken();
        String json = "{\"status\": \"" + status + "\"}";
        String url = AgentConfig.API_BASE_URL + "/programs/" + programId + "/status";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("Failed to update program status: " + response.statusCode() + " " + response.body());
        }
    }

    public void syncPrograms(long deviceId, List<String> filenames) throws IOException, InterruptedException {
        String token = authService.getAccessToken();
        String json = SimpleJson.createProgramSyncRequest(filenames);
        String url = baseUrl + "/" + deviceId + "/programs/sync";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("Failed to sync programs: " + response.statusCode() + " " + response.body());
        }
    }

    public List<String> getWhitelist(long deviceId, String filename) throws IOException, InterruptedException {
            String token = authService.getAccessToken();
            String url = baseUrl + "/" + deviceId + "/whitelist?filename="
                    + URLEncoder.encode(filename, StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + token)
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IOException("Failed to get whitelist: " + response.statusCode() + " " + response.body());
            }
            return SimpleJson.extractStringArray(response.body());
    }

    private DeviceResponse parseDeviceResponse(String json) {
        String idStr = SimpleJson.extractField(json, "id");
        String name = SimpleJson.extractField(json, "name");
        String publicIp = SimpleJson.extractField(json, "publicIp");
        String macAddress = SimpleJson.extractField(json, "macAddress");
        String wolPortStr = SimpleJson.extractField(json, "wakeOnLanPort");

        List<String> programFilenames = SimpleJson.extractArray(json, "programs", "filename");

        long id = idStr != null ? Long.parseLong(idStr) : 0;
        int wolPort = wolPortStr != null ? Integer.parseInt(wolPortStr) : 9; // Default to 9 if missing

        return new DeviceResponse(id, name, publicIp, macAddress, wolPort, programFilenames);
    }
}
