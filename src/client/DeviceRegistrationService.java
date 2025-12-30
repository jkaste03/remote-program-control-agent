package client;

import java.net.InetAddress;
import java.net.UnknownHostException;

public class DeviceRegistrationService {
    private final DeviceClient deviceClient;
    private final ConsoleInputHandler inputHandler;

    public DeviceRegistrationService(DeviceClient deviceClient, ConsoleInputHandler inputHandler) {
        this.deviceClient = deviceClient;
        this.inputHandler = inputHandler;
    }

    public void initializeDevice() throws Exception {
        String macAddress = NetworkUtils.getMacAddress();
        String publicIp = NetworkUtils.getPublicIp();

        if (macAddress == null || publicIp == null) {
            throw new RuntimeException("Could not determine MAC address or Public IP.");
        }

        // 1. Ensure configuration is complete (Name and Port)
        ensureConfiguration();

        // 2. Register or Update device on server
        if (AgentConfig.DEVICE_ID != -1) {
            handleExistingDevice(macAddress, publicIp);
        } else {
            handleNewDevice(macAddress, publicIp);
        }
    }

    private void ensureConfiguration() {
        boolean configChanged = false;

        if (AgentConfig.DEVICE_NAME == null) {
            String defaultName = getHostname();
            AgentConfig.DEVICE_NAME = inputHandler.askForDeviceName(defaultName);
            configChanged = true;
        }

        if (AgentConfig.WOL_PORT == -1) {
            AgentConfig.WOL_PORT = inputHandler.askForWolPort(9);
            configChanged = true;
        }

        if (configChanged) {
            AgentConfig.saveConfig();
        }
    }

    private void handleExistingDevice(String macAddress, String publicIp) throws Exception {
        System.out.println("Checking existing device with ID: " + AgentConfig.DEVICE_ID);
        DeviceResponse device = deviceClient.getDeviceDetails(AgentConfig.DEVICE_ID);

        if (device == null) {
            System.out.println("Device not found on server. Re-registering...");
            registerDevice(macAddress, publicIp);
        } else {
            System.out.println("Device found: " + device.getName());

            // Check if local config differs from server or if network details changed
            boolean needsUpdate = !macAddress.equals(device.getMacAddress()) ||
                    !publicIp.equals(device.getPublicIp()) ||
                    !AgentConfig.DEVICE_NAME.equals(device.getName()) ||
                    AgentConfig.WOL_PORT != device.getWakeOnLanPort();

            if (needsUpdate) {
                System.out.println("Device details changed. Updating server...");
                deviceClient.updateDevice(AgentConfig.DEVICE_ID, AgentConfig.DEVICE_NAME, publicIp,
                        AgentConfig.WOL_PORT, macAddress);
                System.out.println("Device updated.");
            } else {
                System.out.println("Device details are up to date.");
            }
        }
    }

    private void handleNewDevice(String macAddress, String publicIp) throws Exception {
        System.out.println("No device ID configured. Registering new device...");
        registerDevice(macAddress, publicIp);
    }

    private void registerDevice(String macAddress, String publicIp) throws Exception {
        DeviceResponse newDevice = deviceClient.createDevice(AgentConfig.DEVICE_NAME, publicIp, AgentConfig.WOL_PORT,
                macAddress);
        AgentConfig.DEVICE_ID = newDevice.getId();
        AgentConfig.saveConfig();
        System.out.println("Registered new device with ID: " + AgentConfig.DEVICE_ID);
    }

    private String getHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return "Unknown Device";
        }
    }
}
