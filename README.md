# Remote Program Control Agent

A Java-based agent for remote program management running on local machines. The agent connects to the Remote Program Control central server to receive commands and manage local programs.

## Overview

The **Remote Program Control Agent** is a lightweight service that:

- Registers with the central control server
- Receives real-time commands via Server-Sent Events (SSE)
- Starts and stops programs based on server commands
- Monitors program status and synchronizes with server
- Manages system shutdown and restart operations
- Watches local program folder for changes
- Provides secure OAuth2 authentication

## Architecture

The agent communicates with a central Spring Boot server via REST API and SSE for real-time command delivery.

```
Central Server (Spring Boot)
         |
         | REST API / SSE
         |
    Remote Agent
    ┌────────────────────────┐
    │ - AuthService          │
    │ - DeviceClient         │
    │ - SseClient            │
    │ - AgentController      │
    │ - ProcessManager       │
    │ - ProgramSyncService   │
    │ - EventHandler         │
    └────────────────────────┘
         |
    Local Programs
    ├── minecraft.exe.lnk
    ├── paint.exe
    └── ...
```

## Prerequisites

- Java 21 or higher
- Access to the Remote Program Control server
- Valid Keycloak/OAuth2 credentials
- Program shortcuts in the `program-shortcuts` folder

## Installation

1. Clone or download the agent project
2. Compile the Java source files (using VS Code Java extension or command line)
3. Create an `agent.properties` file in the project root

## Configuration

Create or edit `agent.properties` in the agent project root:

```properties
# Keycloak/OAuth2 Configuration
auth.server.url=http://localhost:8080/realms/remote-control
auth.client.id=remote-control-agent
auth.client.secret=YOUR_CLIENT_SECRET
auth.username=agent-user
auth.password=agent-password

# Server API Configuration
api.base.url=http://localhost:8080
api.timeout.seconds=30

# Device Information
device.id=1
device.name=Gaming PC
device.public.ip=192.168.1.100
device.mac.address=AA:BB:CC:DD:EE:FF
device.wol.port=9
```

### Configuration Options

- `auth.server.url` - Keycloak realm URL for authentication
- `auth.client.id` - OAuth2 client ID
- `auth.client.secret` - OAuth2 client secret
- `api.base.url` - Remote Program Control server URL
- `device.id` - Unique device identifier on server
- `device.mac.address` - MAC address for Wake-on-LAN support

## Building

### Using VS Code (Recommended)

The project is configured as a VS Code Java project. Compilation is handled automatically by the Java Extension Pack.

### Using Command Line

```bash
javac -d bin src/**/*.java
```

## Running

```bash
# Navigate to the project directory
cd remote-program-control-agent

# Run the agent
java -cp "bin:lib/*" Agent
```

The agent will:

1. Load configuration from `agent.properties`
2. Authenticate with Keycloak
3. Register/update device on the server
4. Synchronize programs from `program-shortcuts` folder
5. Start monitoring for server-sent events
6. Wait for commands from the server

## Folder Structure

```
.
├── README.md                      # This file
├── agent.properties              # Configuration file
├── active_processes.txt          # Active process tracking
├── bin/                          # Compiled class files
│   ├── Agent.class
│   ├── client/
│   ├── command/
│   ├── core/
│   ├── detect/
│   ├── manager/
│   ├── process/
│   ├── program/
│   ├── shortcut/
│   └── stop/
├── src/                          # Java source files
│   ├── Agent.java               # Main entry point
│   ├── client/                  # Server communication
│   │   ├── AgentConfig.java
│   │   ├── AuthService.java
│   │   ├── DeviceClient.java
│   │   ├── DeviceResponse.java
│   │   ├── SseClient.java
│   │   └── ...
│   ├── core/                    # Core agent logic
│   │   ├── AgentController.java
│   │   ├── ServerEventHandler.java
│   │   ├── ProgramSyncService.java
│   │   └── ProgramDirectoryWatcher.java
│   ├── command/                 # Command definitions
│   │   └── CommandAction.java
│   ├── manager/                 # Process and system management
│   │   ├── ProcessManager.java
│   │   └── SystemPowerManager.java
│   ├── program/                 # Program handling
│   │   ├── ProgramHandler.java
│   │   ├── DefaultProgramHandler.java
│   │   └── MinecraftProgramHandler.java
│   ├── process/                 # Process utilities
│   │   ├── CommandBuilder.java
│   │   ├── ProcessKiller.java
│   │   └── ProcessOutputLogger.java
│   ├── detect/                  # Program detection
│   │   └── MinecraftDetector.java
│   ├── shortcut/                # Windows shortcut handling
│   │   └── ShortcutResolver.java
│   └── stop/                    # Program stop strategies
│       ├── StopStrategy.java
│       ├── GracefulStopStrategy.java
│       └── WindowsShutdownStrategy.java
├── lib/                         # External dependencies
└── program-shortcuts/           # Program shortcuts (required)
    ├── minecraft.exe.lnk
    ├── paint.exe.lnk
    └── ...
```

## Program Shortcuts

The agent discovers and manages programs from the `program-shortcuts` folder. Each file in this folder becomes a manageable program.

Supported formats:

- Windows shortcuts (`.lnk`)
- Batch files (`.bat`)
- Executable files (`.exe`)
- Any executable with valid path

Example setup:

```
program-shortcuts/
├── minecraft.exe.lnk
├── notepad.bat
├── game-launcher.exe
└── server-start.bat
```

## Component Overview

### Agent.java

Main entry point. Initializes all services and starts the agent:

- Loads configuration
- Creates authentication service
- Registers device with server
- Synchronizes programs
- Starts event listener
- Keeps application running

### client/ - Server Communication

- `AuthService.java` - OAuth2 authentication with Keycloak
- `DeviceClient.java` - REST API calls to server
- `SseClient.java` - Server-Sent Events listener for commands
- `DeviceRegistrationService.java` - Device registration workflow

### core/ - Core Functionality

- `AgentController.java` - Command orchestration and execution
- `ServerEventHandler.java` - Processes incoming server events
- `ProgramSyncService.java` - Synchronizes programs with server
- `ProgramDirectoryWatcher.java` - Monitors changes in program folder

### manager/ - System Management

- `ProcessManager.java` - Creates, tracks, and kills processes
- `SystemPowerManager.java` - Handles shutdown and restart commands

### program/ - Program Handling

- `ProgramHandler.java` - Interface for program-specific handling
- `DefaultProgramHandler.java` - Standard program start/stop
- `MinecraftProgramHandler.java` - Special Minecraft handling
- `ProgramHandlerFactory.java` - Creates appropriate handlers

### stop/ - Shutdown Strategies

- `StopStrategy.java` - Interface for stop strategies
- `GracefulStopStrategy.java` - SIGTERM-based termination
- `ForceStopStrategy.java` - Forceful process termination
- `WindowsShutdownStrategy.java` - Windows system shutdown
- `LinuxShutdownStrategy.java` - Linux system shutdown

## Server Commands

The agent listens for these command types via SSE:

### Program Commands

- `PROGRAM_START_COMMAND` - Start a specific program
- `PROGRAM_STOP_COMMAND` - Stop a specific program

### System Commands

- `SYSTEM_SHUTDOWN_COMMAND` - Shutdown the system
- `SYSTEM_RESTART_COMMAND` - Restart the system

## Security

### Authentication

- OAuth2 with Keycloak
- Client credentials flow
- Credentials stored in `agent.properties` (keep secure)
- Session-based token management

### Best Practices

- Never commit `agent.properties` with real credentials
- Use environment variables for secrets in production:
    ```bash
    export AUTH_CLIENT_SECRET=your-secret
    ```
- Enable HTTPS for server communication
- Restrict access to `agent.properties` file

## Troubleshooting

### Agent Cannot Connect to Server

**Symptoms:** Connection timeout or refused errors

**Solutions:**

1. Verify server is running on configured URL
2. Check network connectivity to server
3. Validate `agent.properties` settings
4. Check client credentials are correct
5. Verify Keycloak is accessible and running

### Authentication Failures

**Symptoms:** 401/403 errors in logs

**Solutions:**

1. Verify OAuth2 credentials in `agent.properties`
2. Check Keycloak realm and client configuration
3. Ensure client has required permissions
4. Verify user account is active in Keycloak

### Programs Won't Start

**Symptoms:** Command received but program doesn't launch

**Solutions:**

1. Check shortcuts exist in `program-shortcuts` folder
2. Verify shortcut paths point to valid executables
3. Check file permissions on program files
4. Review agent logs for specific error messages
5. Test manual execution of the program

### SSE Connection Drops

**Symptoms:** Commands not received intermittently

**Solutions:**

1. Check network stability
2. Verify token hasn't expired
3. Check firewall rules allow SSE connections
4. Enable connection retry in logs
5. Restart agent if needed

### Compiled Classes Not Found

**Symptoms:** ClassNotFoundException at runtime

**Solutions:**

1. Ensure all .java files are compiled to `bin/` folder
2. Check classpath includes both `bin/` and `lib/`
3. Recompile with: `javac -d bin src/**/*.java`
4. Verify VS Code Java extension is active

## Logging

The agent uses `java.util.logging`. Control log levels via command line:

```bash
# Debug level (verbose)
java -Djava.util.logging.level=FINE -cp "bin:lib/*" Agent

# Info level (default)
java -Djava.util.logging.level=INFO -cp "bin:lib/*" Agent

# Warning level (minimal)
java -Djava.util.logging.level=WARNING -cp "bin:lib/*" Agent
```

## Active Processes

The agent maintains `active_processes.txt` which tracks:

- Currently running programs
- Program IDs
- Process information
- Last status update

This file is automatically managed by the agent and used for:

- Detecting unexpected program termination
- Reconciling state on restart
- Debugging process management

## Performance Considerations

- **Memory:** Minimal footprint, typically <100MB
- **CPU:** Low usage when idle
- **Network:** Only connects on startup and for SSE events
- **Disk:** No persistent storage except configuration and process tracking

## Development

### Code Style

- Java naming conventions
- Package organization by responsibility
- Clear separation of concerns

### Adding New Program Handlers

1. Implement `ProgramHandler` interface
2. Add to `ProgramHandlerFactory`
3. Implement start/stop logic
4. Handle program-specific quirks

Example:

```java
public class CustomProgramHandler implements ProgramHandler {
    @Override
    public void start(ManagedProcess process) {
        // Custom start logic
    }

    @Override
    public void stop(ManagedProcess process) {
        // Custom stop logic
    }
}
```

### Adding New Stop Strategies

1. Implement `StopStrategy` interface
2. Register in strategy selection logic
3. Handle OS-specific behavior

---

**Last Updated:** April 2026
**Version:** 0.0.1-SNAPSHOT
**Java Compatibility:** Java 21+
