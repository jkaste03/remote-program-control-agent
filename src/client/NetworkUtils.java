package client;

import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

public class NetworkUtils {

    public static Map<String, String> getAvailableMacAddresses() {
        Map<String, String> result = new HashMap<>();
        try {
            Enumeration<NetworkInterface> networks = NetworkInterface.getNetworkInterfaces();
            while (networks.hasMoreElements()) {
                NetworkInterface network = networks.nextElement();
                byte[] mac = network.getHardwareAddress();

                if (mac != null && !network.isLoopback() && network.isUp()) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < mac.length; i++) {
                        sb.append(String.format("%02X%s", mac[i], (i < mac.length - 1) ? ":" : ""));
                    }
                    result.put(network.getDisplayName(), sb.toString());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    public static String getMacAddress() {
        // Fallback or simple retrieval: returns the first available one
        Map<String, String> macs = getAvailableMacAddresses();
        if (macs.isEmpty())
            return null;
        return macs.values().iterator().next();
    }

    public static String getPublicIp() {
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.ipify.org"))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return response.body();
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            return null;
        }
    }
}
