# Cutre-Marcianos (libGDX migration)

Migration of an old Java applet to a multi-module libGDX Gradle project. The original `.class` files are kept apart as a reference; the new game does not depend on AWT, Applet, or those binaries.

## What has been ported

- Ship, acceleration, rotation, firing, shield, lives, player collisions, and explosions.
- The game state is managed by `GameScreen` and each player's simulation by `PlayerManager`; this separation makes it easier to add a network layer later.
- No images, fonts, or sounds are required to run this first version.

## Controls

| Player | Rotate | Thrust | Fire | Shield | Hyperspace |
|---|---|---|---|---|---|
| 1 (white) | Left / right arrows | Up arrow | Enter | Right Shift | Right Ctrl |
| 2 (yellow) | A / D | W | Space | Left Shift | Left Ctrl |

Each ship has three hyperspace uses per life. When its key is pressed, the
ship appears at a random position and loses one attempt. The counter is reset
when the ship respawns after losing a life.

The game is restarted by closing and running the application again.

## Requirements without administrator privileges

1. Java 8 or later. Java 21 was detected on the machine during the migration. If Gradle requests a newer version, a JDK can be downloaded to a user folder and `JAVA_HOME` can be configured only for that terminal; it does not need to be installed system-wide on Windows.
2. Gradle 8.10 (compatible with java 21) or later. Administrator privileges are not required if the ZIP is downloaded from [gradle.org](https://gradle.org/releases/) and extracted, for example, to `%USERPROFILE%\\tools\\gradle`.
3. VS Code. No plugin is required for compilation: Gradle can be run from the integrated terminal.

Recommended, but optional, extensions:

- Extension Pack for Java (`vscjava.vscode-java-pack`) for autocomplete, errors, and debugging.
- Gradle for Java (`vscjava.vscode-gradle`) to view Gradle tasks in the side panel.

## Build and run

From the project root folder (`.\\Workspace\\marcianos`), run the following commands in a VS Code terminal:

```text
gradle build
gradle :desktop:run
```

The first run downloads Gradle dependencies from Maven Central and may take some time. The desktop executable opens in a 1920 x 1080 window.

To view the available tasks:

```text
gradle tasks
```

If a Gradle wrapper is configured on the machine, the equivalent commands are `gradlew.bat build` and `gradlew.bat :desktop:run`; using the wrapper is recommended so that the whole team uses exactly the same version.

## Architecture for Internet multiplayer

This version uses a local simulation. For Internet multiplayer, it is better not to send drawn positions; the following components should be separated in the future:

1. `GameState`: serializable positions, velocities, angles, lives, and projectiles.
2. `GameSimulation`: deterministic rules and collisions.
3. `InputCommand`: player actions with a tick number.
4. libGDX client: renders the latest confirmed state and sends commands.
5. Authoritative server: validates commands, runs the simulation, and distributes snapshots.

For a first online version, I would recommend a separate Java server using TCP/WebSocket or UDP, keeping libGDX as the client only. Networking has not been added yet to avoid mixing it with the visual migration and to allow the local game to be tested first.
