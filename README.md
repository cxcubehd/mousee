# Mousee

Mousee is a Fabric client mod that gives Minecraft on macOS a raw relative mouse path for gameplay camera movement.

Minecraft's menus, inventories, pause screen, and normal cursor behavior stay vanilla. Mousee only takes over while the game has captured the cursor for first-person camera control and the vanilla `Raw Mouse Input` option is enabled.

## Features

- Raw relative mouse motion on supported macOS systems.
- Vanilla `Raw Mouse Input` toggle in Options > Controls > Mouse Settings.
- No custom sensitivity curve, smoothing, or camera pipeline.
- Universal macOS native library for Apple Silicon and Intel Macs.
- Quiet by default, with optional diagnostics through Mod Menu.
- Inert fallback on unsupported platforms or when the native backend is unavailable.

## Requirements

- Minecraft Java Edition 26.2
- Fabric Loader 0.19.3 or newer
- Java 25
- macOS 14 or newer

Mousee is a client-side mod. It is not required on servers.

## Install

1. Install Fabric Loader for Minecraft 26.2.
2. Install Fabric API, Fabric Language Kotlin, and Cloth Config.
3. Place the Mousee jar in the client's `mods` directory.
4. Launch Minecraft on macOS.
5. Open Options > Controls > Mouse Settings and enable `Raw Mouse Input`.

Mod Menu is optional. When installed, it exposes Mousee's diagnostics screen.

## Build From Source

Release jars must be built on macOS because the native backend links against Apple frameworks.

```bash
./gradlew build
```

The runtime jar is written to `build/libs/` and contains:

```text
natives/macos/libmousee_macos.dylib
```

## Diagnostics

Mousee does not log during normal gameplay.

Diagnostics can be enabled in the Mod Menu config screen or with:

```text
-Dmousee.diagnostics=true
```

The config file is stored at `config/mousee.json`.

## Documentation

- [Development](docs/development.md)
- [Releasing](docs/releasing.md)

## License

Mousee is available under the CC0-1.0 license.
