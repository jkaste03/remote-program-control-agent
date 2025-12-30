package client;

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
}
