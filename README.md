# Aero Switch

Aero Switch is a client-side Fabric mod for Minecraft 26.2 that runs multiple Minecraft client sessions inside one game process. It is built for split-screen and tabbed play, with each session keeping its own world, player, connection, screen state, camera, controls, and render target. This mod does not provide co-op functionality.

## Features

- Up to four simultaneous client sessions.
- Layout modes for tabs, side-by-side split, grid, and three-pane arrangements.
- Per-session focus, input routing, and local server ownership.
- Optional pane borders, compact status display, inactive-session dimming, and pause or keep-running controls.
- Client keybindings for opening the session manager, switching sessions, and focusing panes.

## Requirements

- Java 25
- Minecraft 26.2
- Fabric Loader 0.19.3 or newer
- Fabric API for Minecraft 26.2

## Building

Build the mod jar with Gradle:

```powershell
.\gradlew.bat build
```

The built jars are written to:

```text
build/libs/
```

The main mod artifact uses the `aero-switch` archive name and the version from `gradle.properties`.

## Running From The Workspace

Start the development client with:

```powershell
.\gradlew.bat runClient
```

For a normal Minecraft launcher profile, no special JVM argument is required. To explicitly disable the session engine, add:

```text
-DaeroSwitch.experimental=false
```

## Controls

Default controls can be changed in Minecraft's keybind menu under the Aero Switch category.

- Open the session manager: F8
- Switch world session (next occupied session with wrap around): F7
- Focus session by direction: Alt + Arrow keys
- Focus session by slot: Alt + number keys

## Configuration

Aero Switch stores client configuration in:

```text
config/aero_switch.json
```

The session manager writes changes immediately when settings are changed in the in-game UI.

## Test And Research Tasks

Useful Gradle tasks include:

```powershell
.\gradlew.bat compileGametestJava
.\gradlew.bat runSessionTest -PaeroScenario=local-local
.\gradlew.bat extractResearchSources
```

`runSessionTest` uses a dedicated session scenario run configuration and enables diagnostic output automatically.