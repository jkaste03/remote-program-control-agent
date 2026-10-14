# Remote Program Control Agent

Remote Program Control Agent is a Java application that registers a computer with the Remote Program Control service, synchronizes its program list, starts and stops supported programs, reports program status, and listens for server commands over Server-Sent Events (SSE).

This README documents the current minecraft-server branch. The agent uses the application's own username/password authentication; it does not use Keycloak.

## Requirements

- Java Development Kit (JDK) 21 or later. A JDK is required to compile; a compatible Java runtime is required to run the JAR.
- Network access to the configured Remote Program Control service.
- A valid Remote Program Control account.
- Git, if cloning the repository.

There is no Maven or Gradle build file in this branch. The commands below compile the Java sources directly using the JDK tools.

## Configuration

The default profile is production. Set AGENT_PROFILE to dev for local development.

| Setting | Purpose |
| --- | --- |
| AGENT_PROFILE=prod | Production profile; this is the default. |
| AGENT_PROFILE=dev | Development profile. |
| AGENT_BASE_URL | Optional override for the base URL used for login and REST API requests. |
| AGENT_SSE_BASE_URL | Optional override for the SSE API base URL. |
| AGENT_DATA_DIR | Optional absolute path for local configuration, refresh token, and process tracking data. |
| AGENT_SHORTCUTS_DIR | Optional absolute path for the program-shortcuts directory. |

The production service endpoints are determined by the application configuration. For a custom deployment, set AGENT_BASE_URL to the base URL expected by that server. If its SSE endpoint is hosted separately, set AGENT_SSE_BASE_URL as well. Do not point the agent directly at a database.

PowerShell:

~~~powershell
$env:AGENT_PROFILE = "prod"
java -jar .\dist\remote-program-control-agent.jar
~~~

Linux/macOS:

~~~bash
AGENT_PROFILE=prod java -jar ./dist/remote-program-control-agent.jar
~~~

For example, set AGENT_BASE_URL to the URL supplied by your service administrator. Set environment variables in the same shell or service environment that starts the agent.

## Build the executable JAR

Clone the branch that contains the current implementation:

~~~bash
git clone --branch minecraft-server --single-branch https://github.com/jkaste03/remote-program-control-agent.git
cd remote-program-control-agent
~~~

### Build on Windows using PowerShell

Run these commands from the repository root. They remove stale compiled classes before recompiling, which avoids old classes accidentally being packaged into the JAR.

~~~powershell
New-Item -ItemType Directory -Force .\bin, .\dist | Out-Null
Remove-Item .\bin\* -Recurse -Force -ErrorAction SilentlyContinue

$javaFiles = Get-ChildItem .\src -Recurse -Filter *.java |
    Select-Object -ExpandProperty FullName

javac -d .\bin $javaFiles
if ($LASTEXITCODE -ne 0) {
    throw "Java compilation failed. The JAR was not created."
}

jar --create --file .\dist\remote-program-control-agent.jar --main-class Agent -C .\bin .
if ($LASTEXITCODE -ne 0) {
    throw "JAR creation failed."
}
~~~

The output is dist/remote-program-control-agent.jar.

### Build on Linux/macOS

Run from the repository root:

~~~bash
mkdir -p bin dist
find bin -mindepth 1 -delete
find src -type f -name '*.java' -print0 | xargs -0 javac -d bin
jar --create --file dist/remote-program-control-agent.jar --main-class Agent -C bin .
~~~

If compilation fails, do not deploy the JAR. Fix the reported compiler errors first.

## First startup and login

Run the agent interactively the first time, as the same operating-system user who will run it later. First-time authentication and device registration require console input.

~~~bash
java -jar dist/remote-program-control-agent.jar
~~~

On first startup, the agent will:

1. Ask for the Remote Program Control username/email and password.
2. Authenticate and save a refresh token locally.
3. If no device is registered for that account on this machine, ask for a device name and, when required, Wake-on-LAN details such as the relevant network adapter or MAC address.
4. Register the device and save its ID locally.
5. Synchronize supported programs found in the program-shortcuts folder and connect to SSE.

The password is not saved by the agent. The refresh token is saved so future starts can refresh the login without asking for the password again. If the token expires or is revoked, run the agent interactively to sign in again.

When running in a terminal, enter exit or quit to stop the agent without deliberately deleting the saved refresh token. Enter logout to remove the saved refresh token, then exit.

Do not run an interactive instance and a systemd instance at the same time for the same device/account.

## Program shortcuts and supported files

By default, program files are stored outside the repository in:

~~~text
~/RemoteProgramControl/program-shortcuts/
~~~

The folder is created automatically if missing. On Windows it is under the current user's home directory; on Linux and macOS it is under the user's home directory.

To use another location, set AGENT_SHORTCUTS_DIR to an absolute path before starting the agent.

The agent supports launch files including:

- JAR files, started with Java
- BAT and CMD files
- Python files
- PowerShell scripts
- Shell scripts
- Executables and other files supported by the operating system
- Windows shortcut files where applicable

Place each launch file or shortcut directly in the program-shortcuts folder and ensure its target exists and is accessible to the account running the agent. Scripts should work when started by the agent, not only from an interactive shell; avoid relying on shell aliases or environment variables defined only in an interactive profile. Use absolute paths inside scripts when practical.

For Minecraft server launch files, the agent uses the shortcut target and working directory when locating whitelist.json. When found, whitelist changes can be synchronized with the owner and explicitly assigned users.

## Where local data is stored

The agent keeps local configuration, refresh token, and active-process tracking data in its data directory.

Default locations:

- Windows: %LOCALAPPDATA%\RemoteProgramControlAgent\
- Linux/Unix: ~/.config/remote-program-control-agent/
- Linux/Unix with an absolute XDG_CONFIG_HOME: $XDG_CONFIG_HOME/remote-program-control-agent/
- macOS: ~/Library/Application Support/RemoteProgramControlAgent/

Important files include:

- agent.properties - locally stored device ID/configuration.
- refresh_token.txt - refresh token used to authenticate again.
- active_processes.txt - persisted process tracking information.

Use AGENT_DATA_DIR to specify another absolute data directory. The service account must have permission to read and write this directory. Changing the operating-system user can make the agent appear to be a new installation because each user has a separate home/data directory.

## Run on Linux with systemd

A systemd service has no interactive terminal, so the agent must be started in service mode rather than waiting for commands from standard input. Set `AGENT_SERVICE_MODE=true` in the service unit as shown below.

### 1. Log in interactively as the service user

Install the JAR under a stable directory readable by the service user. Ensure Java is installed and determine which account will run the service.

Before starting systemd, launch the JAR interactively as that same account, using the same home directory, and complete login/device registration. This creates the refresh token and device configuration in that account's data directory. Stop the interactive instance with exit before continuing.

### 2. Create the service unit

Replace YOUR_LINUX_USER and the example installation paths with the account and paths for your machine.

~~~ini
[Unit]
Description=Remote Program Control Agent
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
User=YOUR_LINUX_USER
WorkingDirectory=/opt/your-agent
Environment=HOME=/home/YOUR_LINUX_USER
Environment=AGENT_PROFILE=prod
Environment=AGENT_SERVICE_MODE=true
ExecStart=/usr/bin/java -jar /opt/your-agent/remote-program-control-agent.jar
Restart=on-failure
RestartSec=10

[Install]
WantedBy=multi-user.target
~~~

Save the unit in the systemd system-service directory. Check that ExecStart points to the actual Java executable and JAR file on the machine. The HOME value must match the selected service user's real home directory so the agent finds the refresh token created during interactive login.

Enable and start it:

~~~bash
sudo systemctl daemon-reload
sudo systemctl enable --now remote-program-control-agent
sudo systemctl status remote-program-control-agent --no-pager
~~~

Useful operations:

~~~bash
sudo systemctl restart remote-program-control-agent
sudo systemctl stop remote-program-control-agent
sudo journalctl -u remote-program-control-agent -n 100 --no-pager
sudo journalctl -u remote-program-control-agent -f
~~~

Do not launch a second manual instance while the service is running. When systemd manages the agent, use systemctl stop instead of relying on the terminal's exit command.

## Update an installed agent

1. Build a new JAR and confirm the build succeeded.
2. Transfer it securely to the host using your normal deployment method.
3. Stop the service before replacing the JAR.
4. Back up the current JAR, copy in the new one, and start the service.
5. Check the service status and logs before considering the update complete.

On the Linux host, replace the example paths with your chosen installation and transfer locations:

~~~bash
sudo systemctl stop remote-program-control-agent
sudo cp /opt/your-agent/remote-program-control-agent.jar /opt/your-agent/remote-program-control-agent.jar.bak
sudo cp /path/to/remote-program-control-agent.jar /opt/your-agent/remote-program-control-agent.jar
sudo systemctl start remote-program-control-agent
sudo systemctl status remote-program-control-agent --no-pager
sudo journalctl -u remote-program-control-agent -n 60 --no-pager
~~~

Never overwrite the installed JAR while the service is running.

## Troubleshooting

### The service immediately becomes inactive

A Java process that waits for terminal input can encounter end-of-file when a service manager provides no interactive input. Confirm that AGENT_SERVICE_MODE=true is set in the service unit and that the installed JAR supports service mode. Check that the deployed JAR is the build you intended to install.

~~~bash
sudo systemctl status remote-program-control-agent --no-pager
sudo journalctl -u remote-program-control-agent -n 100 --no-pager
~~~

### The agent asks for a login every time, or cannot authenticate under systemd

Run the agent once interactively as the same Linux user as the service. Verify that the service's HOME environment points to that user's real home directory and that the user can read/write the data directory. If the refresh token is expired or revoked, stop the service and sign in interactively again.

### The agent cannot connect or SSE disconnects

- Confirm the selected profile and effective base URL.
- Confirm that the machine can reach the configured service endpoints.
- Check the agent logs for HTTP status codes, authentication failures, TLS errors, or connection errors.
- Check whether a proxy or firewall interrupts long-lived SSE connections.

### A program does not start

- Confirm the file is in the configured program-shortcuts folder and that its target exists.
- Check that the operating-system account running the agent can read and execute the target.
- Use absolute paths in scripts, and verify required runtimes such as Java, Python, or PowerShell are installed.
- Check the agent output and the program's own logs.

### Compilation fails or old behavior persists

Remove stale class files from bin, compile every Java source file again, and recreate the JAR. Confirm the newly built JAR is the one copied to the host and restart the service after replacement.

## Security notes

- Keep the refresh token and configuration directory private to the operating-system user running the agent. Do not publish or commit local data files.
- Never commit passwords, refresh tokens, or other credentials.
- The systemd service should run as an unprivileged dedicated user wherever practical.
- Configure access to the Remote Program Control service and device permissions according to your deployment's security requirements.
- Use HTTPS for production communication.

---

Java compatibility: 21 or later.
