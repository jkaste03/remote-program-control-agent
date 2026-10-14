# Remote Program Control agent

Agenten kobler programmer til Remote Program Control. Den synkroniserer
programfiler fra program-shortcuts, starter og stopper dem og rapporterer
status over SSE.

## Authentication

Authentication is handled by the application's internal user store. No
external identity provider or browser-based OAuth flow is required.

Set the server URL before starting the agent (optional):

~~~text
AGENT_BASE_URL=<your-development-server-url>
~~~

On the first start, the agent asks for the application username and password in the console.
The password is hidden when the agent runs in a real terminal. The agent does not store the password or read it from environment variables.

The agent stores the refresh token locally in refresh_token.txt. Use
logout in the agent console to remove it.

## Supported programs

The agent can launch:

- JAR files with java -jar
- BAT and CMD files through cmd
- Python files with python
- PowerShell scripts
- Shell scripts through bash
- Executables and other files supported by the operating system

Windows shortcut files in program-shortcuts may point to any supported launch
file. Minecraft servers receive their server-specific readiness and graceful
stop handling; other programs use the generic process strategies.
