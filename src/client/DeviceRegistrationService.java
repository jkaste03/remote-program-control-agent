package client;

import java.net.InetAddress;
import java.net.UnknownHostException;

import java.util.Collections;
import java.util.List;

public class DeviceRegistrationService {
    private final DeviceClient deviceClient;
    private final ConsoleInputHandler inputHandler;

    public DeviceRegistrationService(DeviceClient deviceClient, ConsoleInputHandler inputHandler) {
        this.deviceClient = deviceClient;
        this.inputHandler = inputHandler;
    }

    public List<String> initializeDevice() throws Exception {
        String macAddress = NetworkUtils.getMacAddress();
        String publicIp = NetworkUtils.getPublicIp();

        if (macAddress == null || publicIp == null) {
            throw new RuntimeException("Could not determine MAC address or Public IP.");
        }

        // 1. Register or Update device on server
        if (AgentConfig.DEVICE_ID != -1) {
            return handleExistingDevice(macAddress, publicIp);
        } else {
            return handleNewDevice(macAddress, publicIp);
        }
    }

    private List<String> handleExistingDevice(String macAddress, String publicIp) throws Exception {
        System.out.println("Checking existing device with ID: " + AgentConfig.DEVICE_ID);
        // Fetch device details INCLUDING programs
        DeviceResponse device = deviceClient.getDeviceDetails(AgentConfig.DEVICE_ID, true);

        if (device == null) {
            System.out.println("Device not found on server. Re-registering...");
            return handleNewDevice(macAddress, publicIp);
        } else {
            System.out.println("Device found: " + device.getName());

            // Sync local config from server (Source of Truth for Name and Port)
            AgentConfig.DEVICE_NAME = device.getName();
            AgentConfig.WOL_PORT = device.getWakeOnLanPort();

            // Check if network details changed (Source of Truth is Local Agent)
            boolean networkChanged = !macAddress.equals(device.getMacAddress()) ||
                    !publicIp.equals(device.getPublicIp());

            if (networkChanged) {
                System.out.println("Network details changed. Updating server...");
                // We send back the name and port we just got from the server, ensuring they
                // don't change
                deviceClient.updateDevice(AgentConfig.DEVICE_ID, AgentConfig.DEVICE_NAME, publicIp,
                        AgentConfig.WOL_PORT, macAddress);
                System.out.println("Device updated.");
            } else {
                System.out.println("Device details are up to date.");
            }

            return device.getProgramFilenames() != null ? device.getProgramFilenames() : Collections.emptyList();
        }
    }

    private List<String> handleNewDevice(String macAddress, String publicIp) throws Exception {
        System.out.println("No device ID configured. Registering new device...");

        // For new devices, we need to ask for initial configuration
        String defaultName = getHostname();
        AgentConfig.DEVICE_NAME = inputHandler.askForDeviceName(defaultName);
        AgentConfig.WOL_PORT = inputHandler.askForWolPort(9);

        return registerDevice(macAddress, publicIp);
    }

    private List<String> registerDevice(String macAddress, String publicIp) throws Exception {
        DeviceResponse newDevice = deviceClient.createDevice(AgentConfig.DEVICE_NAME, publicIp, AgentConfig.WOL_PORT,
                macAddress);
        AgentConfig.DEVICE_ID = newDevice.getId();
        AgentConfig.saveConfig(); // Only saves ID now
        System.out.println("Registered new device with ID: " + AgentConfig.DEVICE_ID);
        return Collections.emptyList(); // New device has no programs
    }

    private String getHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return "Unknown Device";
        }
    }
}
