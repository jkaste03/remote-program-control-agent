package client;

import java.util.ArrayList;
import java.util.List;
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

    public static List<String> extractArray(String json, String arrayName, String fieldName) {
        List<String> results = new ArrayList<>();

        // Find the array content: "arrayName": [ ... ]. Use a greedy match to avoid stopping on nested [] (e.g., allowedUsers arrays)
        Pattern arrayPattern = Pattern.compile("\"" + arrayName + "\"\\s*:\\s*\\[(.*)\\]", Pattern.DOTALL);
        Matcher arrayMatcher = arrayPattern.matcher(json);

        if (arrayMatcher.find()) {
            String arrayContent = arrayMatcher.group(1);

            // Find objects in the array: { ... }
            Pattern objectPattern = Pattern.compile("\\{.*?\\}", Pattern.DOTALL);
            Matcher objectMatcher = objectPattern.matcher(arrayContent);

            while (objectMatcher.find()) {
                String objectJson = objectMatcher.group();
                String value = extractField(objectJson, fieldName);
                if (value != null) {
                    results.add(value);
                }
            }
        }

        return results;
    }

    public static String createDeviceRequest(String name, String publicIp, int wakeOnLanPort, String macAddress) {
        return String.format("{\"name\": \"%s\", \"publicIp\": \"%s\", \"wakeOnLanPort\": %d, \"macAddress\": \"%s\"}",
                name, publicIp, wakeOnLanPort, macAddress);
    }

    public static String createProgramSyncRequest(List<String> filenames) {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (int i = 0; i < filenames.size(); i++) {
            sb.append(String.format("{\"filename\": \"%s\"}", filenames.get(i)));
            if (i < filenames.size() - 1) {
                sb.append(",");
            }
        }
        sb.append("]");
        return sb.toString();
    }
}
