# Mousee

Mousee is a Fabric client mod for Minecraft Java Edition 26.2 that provides raw macOS mouse motion for gameplay camera movement.

When Minecraft captures the cursor in-game, Mousee replaces GLFW cursor deltas with relative motion from Apple's `GameController.framework` and `GCMouse`. Menus, inventories, pause screens, and uncaptured cursor behavior stay on Minecraft's vanilla path.

## Features

- Uses the vanilla `Raw Mouse Input` option in Options > Controls > Mouse Settings.
- Runs only on macOS; other platforms keep vanilla behavior.
- Preserves Minecraft's existing sensitivity, smoothing, and camera turn pipeline.
- Packages a universal native library for Apple Silicon and Intel Macs.
- Adds optional diagnostics through Mod Menu when Mod Menu is installed.

## Requirements

- Minecraft Java Edition 26.2
- Fabric Loader 0.19.3 or newer
- Java 25
- macOS 14 or newer for the native raw-mouse backend

## Build

Native packaging requires a macOS build host with Xcode Command Line Tools:

```bash
./gradlew build
```

The release jar is written to `build/libs/` and includes the native library at `natives/macos/libmousee_macos.dylib`.

## Install

1. Install Fabric Loader for Minecraft 26.2.
2. Place the built Mousee jar in the client's `mods` directory.
3. Launch Minecraft on macOS.
4. Open Options > Controls > Mouse Settings and enable `Raw Mouse Input`.

## Diagnostics

Mousee is quiet by default. Diagnostic logging can be enabled with either:

- The optional Mod Menu config screen.
- The JVM property `-Dmousee.diagnostics=true`.

The config file is stored at `config/mousee.json`.

## Architecture

1. `MacosRawMouseNative` extracts and loads the packaged JNI library.
2. `mousee_macos_raw_mouse.mm` subscribes to `GCMouse` connect/disconnect notifications and accumulates relative motion.
3. `RawMouseController` enables the backend only while gameplay input is captured and vanilla raw mouse input is enabled.
4. `MouseHandlerMixin` suppresses vanilla captured cursor deltas and injects native deltas into Minecraft's normal camera path.
5. `InputConstantsMixin` exposes vanilla's raw mouse option when Mousee's backend is available.

Mousee deliberately replaces only the source of relative mouse deltas. Minecraft still owns sensitivity, smoothing, and player rotation, which keeps the mod small and limits compatibility risk.

## Validation Checklist

- Launch the client on macOS with the mod installed.
- Confirm `Raw Mouse Input` appears in Mouse Settings.
- Test slow aiming, fast flicks, and continuous turning.
- Check windowed, fullscreen, and borderless fullscreen modes.
- Confirm menus, inventories, pause screens, and uncaptured cursor states behave like vanilla.
- Confirm the mod remains inert on non-macOS systems.
- Confirm the release jar contains `natives/macos/libmousee_macos.dylib`.

## License

Mousee is available under the CC0-1.0 license.
