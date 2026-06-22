# Development

Mousee is intentionally narrow: it replaces the source of captured gameplay mouse deltas on macOS and leaves Minecraft's normal camera behavior intact.

## Project Layout

- `src/main/kotlin/dev/chrones/Mousee.kt` initializes the client mod.
- `src/main/kotlin/dev/chrones/input/RawMouseController.kt` owns backend activation, capture-state checks, polling, and diagnostics.
- `src/main/java/dev/chrones/mixin/MouseHandlerMixin.java` suppresses vanilla captured cursor deltas and injects native deltas before Minecraft turns the player.
- `src/main/java/dev/chrones/mixin/InputConstantsMixin.java` exposes the vanilla raw mouse toggle when Mousee's backend is available.
- `src/main/java/dev/chrones/platform/MacosRawMouseNative.java` extracts and loads the packaged JNI library.
- `src/main/native/macos/mousee_macos_raw_mouse.mm` implements the macOS `GameController.framework` backend.

## Native Backend

The backend uses `GCMouse` relative movement from Apple's `GameController.framework`. It is enabled only when all of these are true:

- The game is running on macOS.
- The native library loaded and reported support.
- Minecraft has captured the mouse for gameplay.
- The window is active.
- A player is in-world.
- No screen or overlay is open.
- Vanilla `Raw Mouse Input` is enabled.
- A native mouse is connected.

When any condition fails, Mousee disables native relative mode, drains pending deltas, and lets vanilla input continue.

## Formatting

Run the formatter before submitting changes:

```bash
./gradlew formatCode
```

This formats Kotlin and Gradle files with Spotless/ktlint, and Java/native sources with `clang-format`.

## Build

```bash
./gradlew build
```

On macOS, the build compiles a universal native library and packages it into the runtime jar. On non-macOS hosts, the JVM code can still compile, but the resulting jar is not a complete release artifact.

## Manual Validation

Before publishing a release, test:

- Apple Silicon Mac.
- Intel Mac, when available.
- Windowed, fullscreen, and borderless fullscreen modes.
- Slow aim, rapid flicks, and long continuous turns.
- Menus, inventories, pause screens, and uncaptured cursor behavior.
- A launch on a non-macOS platform to confirm Mousee remains inert.
