package client;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SimpleJson {

    private SimpleJson() {
    }

    public static String extractField(String json, String fieldName) {
        // Matches "fieldName" : "value" or "fieldName": 123
        // This is a simplified regex and might fail on complex nested JSON or escaped
        // quotes
        Pattern pattern = Pattern.compile("\"" + fieldName + "\"\\s*:\\s*(\"[^\"]*\"|\\d+|null|true|false)");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            String value = matcher.group(1);
            if (value.startsWith("\"") && value.endsWith("\"")) {
                return value.substring(1, value.length() - 1);
            }
            return value;
        }
        return null;
    }

    public static String createDeviceRequest(String name, String publicIp, int wakeOnLanPort, String macAddress) {
        return String.format("{\"name\": \"%s\", \"publicIp\": \"%s\", \"wakeOnLanPort\": %d, \"macAddress\": \"%s\"}",
                name, publicIp, wakeOnLanPort, macAddress);
    }
}
