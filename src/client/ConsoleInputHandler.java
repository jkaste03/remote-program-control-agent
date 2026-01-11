package client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

public class ConsoleInputHandler {
    private final Scanner scanner;

    public ConsoleInputHandler() {
        this.scanner = new Scanner(System.in);
    }

    public String askForDeviceName(String defaultName) {
        while (true) {
            System.out.println("Enter device name (default: " + defaultName + "): ");
            String input = scanner.nextLine().trim();

            if (input.isEmpty()) {
                return defaultName;
            }

            if (input.length() > 100) {
                System.out.println("Error: Name must be 100 characters or less.");
                continue;
            }

            return input;
        }
    }

    public int askForWolPort(int defaultPort) {
        while (true) {
            System.out.println("Enter Wake-on-LAN port (default: " + defaultPort + "): ");
            String input = scanner.nextLine().trim();

            if (input.isEmpty()) {
                return defaultPort;
            }

            try {
                int port = Integer.parseInt(input);
                if (port < 1 || port > 65535) {
                    System.out.println("Error: Port must be between 1 and 65535.");
                    continue;
                }
                return port;
            } catch (NumberFormatException e) {
                System.out.println("Error: Invalid number.");
            }
        }
    }

    public String askForMacAddress(Map<String, String> macs) {
        if (macs.isEmpty()) {
            return null;
        }

        // Group interfaces by MAC address to show unique MACs only
        Map<String, List<String>> macToNames = new HashMap<>();
        for (Map.Entry<String, String> entry : macs.entrySet()) {
            macToNames.computeIfAbsent(entry.getValue(), k -> new ArrayList<>()).add(entry.getKey());
        }

        // If only one unique MAC exists, use it automatically
        if (macToNames.size() == 1) {
            return macToNames.keySet().iterator().next();
        }

        System.out.println("Detected multiple MAC addresses. Please select one for identification:");
        List<String> uniqueMacs = new ArrayList<>(macToNames.keySet());

        for (int i = 0; i < uniqueMacs.size(); i++) {
            String mac = uniqueMacs.get(i);
            List<String> names = macToNames.get(mac);

            List<String> types = new ArrayList<>();
            for (String name : names) {
                String lower = name.toLowerCase();
                if (lower.contains("wireless") || lower.contains("wi-fi") || lower.contains("wifi")
                        || lower.contains("wlan")) {
                    if (!types.contains("Wireless"))
                        types.add("Wireless");
                }
                if (lower.contains("ethernet") || lower.contains("eth") || lower.contains("lan")) {
                    if (!types.contains("Ethernet"))
                        types.add("Ethernet");
                }
            }

            String suffix = types.isEmpty() ? "" : " (" + String.join(", ", types) + ")";
            System.out.println((i + 1) + ") " + mac + suffix);
        }

        while (true) {
            System.out.println("Select MAC [1-" + uniqueMacs.size() + "]: ");
            String input = scanner.nextLine().trim();
            try {
                int selection = Integer.parseInt(input);
                if (selection >= 1 && selection <= uniqueMacs.size()) {
                    return uniqueMacs.get(selection - 1);
                }
            } catch (NumberFormatException ignored) {
            }
            System.out.println("Invalid selection. Please try again.");
        }
    }

    public void listenForCommands(Runnable onLogout, Runnable onExit) {
        System.out.println("\n---------------------------------------------------");
        System.out.println("Agent is running.");
        System.out.println("Type 'logout' to clear credentials and exit.");
        System.out.println("Type 'exit' or 'quit' to stop the agent.");
        System.out.println("---------------------------------------------------\n");

        while (scanner.hasNextLine()) {
            String line = scanner.nextLine().trim();
            if ("logout".equalsIgnoreCase(line)) {
                if (onLogout != null)
                    onLogout.run();
                break;
            } else if ("exit".equalsIgnoreCase(line) || "quit".equalsIgnoreCase(line)) {
                if (onExit != null)
                    onExit.run();
                break;
            }
        }
    }
}
