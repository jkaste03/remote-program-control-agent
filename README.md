# Remote Program Control agent

Agenten kobler programmer til Remote Program Control. Den synkroniserer
programfiler fra program-shortcuts (en mappe rett utenfor agentprosjektet),
starter og stopper dem og rapporterer status over SSE.

## Authentication

Authentication is handled by the application's internal user store. No
external identity provider or browser-based OAuth flow is required.

The agent supports explicit development and production profiles. Production
uses the endpoint configured for the selected deployment; the agent must not
connect directly to the PostgreSQL database.

For production:

~~~text
AGENT_PROFILE=prod
~~~

For local development:

~~~text
AGENT_PROFILE=dev
~~~

An explicit AGENT_BASE_URL overrides the profile, which is useful for a
local test server or another deployment:

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

Shortcut files in program-shortcuts can point to supported launch files on
all operating systems. For Minecraft servers, the agent uses the shortcut
target and working directory to find whitelist.json. If present, whitelist
changes can be synchronized with the owner and explicitly assigned users.
