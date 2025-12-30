package client;

import java.util.List;

public class DeviceResponse {
    private long id;
    private String name;
    private String publicIp;
    private String macAddress;
    private int wakeOnLanPort;
    private List<String> programFilenames;

    public DeviceResponse(long id, String name, String publicIp, String macAddress, int wakeOnLanPort,
            List<String> programFilenames) {
        this.id = id;
        this.name = name;
        this.publicIp = publicIp;
        this.macAddress = macAddress;
        this.wakeOnLanPort = wakeOnLanPort;
        this.programFilenames = programFilenames;
    }

    public long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getPublicIp() {
        return publicIp;
    }

    public String getMacAddress() {
        return macAddress;
    }

    public int getWakeOnLanPort() {
        return wakeOnLanPort;
    }

    public List<String> getProgramFilenames() {
        return programFilenames;
    }
}
