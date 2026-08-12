# Cutre-Marcianos

Desktop libGDX version of the original game, now with local multiplayer and a built-in online TCP mode.

Current app version: 2.0.6.

## Features

- Classic Asteroids-style gameplay with lives, shield, hyperspace, bullets, collisions, and explosions.
- Local play from the presentation screen:
	- 1 player mode
	- 2 players mode (same keyboard)
- Online play from the presentation screen:
	- CREATE (host)
	- JOIN (client)
- Up to 16 players in the current built-in TCP server.

## Controls

### Local 2-player mode

| Player | Rotate | Thrust | Fire | Shield | Hyperspace |
|---|---|---|---|---|---|
| 1 (white) | Left / Right | Up | Enter | Right Shift | Right Ctrl |
| 2 (yellow) | A / D | W | Space | Left Shift | Left Ctrl |

### Online mode

The local online player uses Player 1 controls:

- Rotate: Left / Right
- Thrust: Up
- Fire: Enter
- Shield: Right Shift
- Hyperspace: Right Ctrl

## Requirements

1. Java 8 or later (Java 21 is known to work).
2. Gradle installed on PATH.

Note: this repository does not include gradlew/gradlew.bat, so use system Gradle commands.

## Build and run

From project root:

```text
gradle build
gradle :desktop:run
```

Useful command:

```text
gradle tasks
```

## Run two game windows at the same time

Use two terminals in the same project folder and run this in both:

```text
gradle :desktop:run
```

You can also spawn new terminals from PowerShell:

```powershell
Start-Process powershell -ArgumentList '-NoExit','-Command','cd "c:\Desarrollo\Workspace\marcianos-tcp"; gradle :desktop:run'
```

Run it twice to launch two instances.

## Online quick guide (CREATE/JOIN)

From the presentation screen:

- Press C to open CREATE.
- Press J to open JOIN.

In CREATE:

- H edits bind host (default 0.0.0.0).
- P edits port (default 7777).
- N edits player name.
- Enter starts the host session.

In JOIN:

- H edits target host.
- P edits port.
- N edits player name.
- Enter connects.

Important host rule:

- Do not use localhost on a remote machine.
- Use the host machine LAN IP (same network) or public IP (different networks).
- Host machine must allow the selected TCP port in firewall/router when playing over the Internet.

## Current networking notes

- The built-in server is intended for the integrated online mode and testing.
- In CREATE mode, server bind address and client connection host are handled separately:
	- Server bind host defaults to 0.0.0.0.
	- Host local client connects to 127.0.0.1.
- Player editing keys H, P, and N open text input dialogs in desktop mode.
