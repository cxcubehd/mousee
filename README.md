# Mousee

Mousee is a Fabric client-side mod for Minecraft Java Edition 26.2 that replaces macOS gameplay camera deltas with raw relative mouse motion sourced from Apple's modern game-input stack.

On supported macOS systems, Mousee keeps vanilla menus and cursor behavior intact, but when Minecraft has captured the cursor for gameplay it swaps GLFW cursor deltas for `GCMouse` relative motion delivered through `GameController.framework`.

## Highlights

- Targets Minecraft Java Edition 26.2 with Fabric and Yarn mappings.
- Active only on macOS. Other operating systems return immediately and keep vanilla behavior.
- Preserves vanilla GUI, inventory, menu, and uncaptured cursor behavior.
- Integrates into Minecraft's existing camera pipeline by feeding deltas into `MouseHandler.accumulatedDX` and `MouseHandler.accumulatedDY`.
- Uses the vanilla `Raw Mouse Input` toggle in Options > Controls > Mouse Settings.
- Packages a universal macOS native library containing both `arm64` and `x86_64` slices.
- Provides optional diagnostics through a Cloth Config screen exposed in Mod Menu when Mod Menu is installed.

## Architecture

### High-level flow

1. [src/main/java/dev/chrones/platform/MacosRawMouseNative.java](src/main/java/dev/chrones/platform/MacosRawMouseNative.java) extracts and loads the packaged JNI library.
2. [src/main/native/macos/mousee_macos_raw_mouse.mm](src/main/native/macos/mousee_macos_raw_mouse.mm) initializes `GameController.framework`, subscribes to `GCMouse` connect and disconnect notifications, and accumulates relative deltas from `mouseMovedHandler`.
3. [src/main/kotlin/dev/chrones/input/RawMouseController.kt](src/main/kotlin/dev/chrones/input/RawMouseController.kt) decides when the backend should be active, based on OS, backend support, cursor capture, focus, player presence, overlay state, and the vanilla `rawMouseInput` option.
4. [src/main/java/dev/chrones/mixin/MouseHandlerMixin.java](src/main/java/dev/chrones/mixin/MouseHandlerMixin.java) suppresses vanilla cursor-delta accumulation during captured gameplay and injects the native deltas into Minecraft's normal camera turn path.
5. [src/main/java/dev/chrones/mixin/InputConstantsMixin.java](src/main/java/dev/chrones/mixin/InputConstantsMixin.java) reports raw mouse support to vanilla when the native backend is available, which makes Minecraft expose its built-in `Raw Mouse Input` option in Mouse Settings.

### Why this integration point

Mousee does not replace Minecraft's sensitivity curve, smoothing path, or turn-player logic. It only replaces the source of relative deltas when gameplay camera input is active. That keeps the mod small and minimizes conflicts with other client-side mods that expect vanilla camera processing to remain in place.

### Why GameController and `GCMouse`

The chosen backend follows Apple's current game-input direction and aligns with the approach modern SDL3 has taken on macOS: prefer the `GameController` ecosystem and `GCMouse` relative input over older legacy cursor or event-tap paths.

Compared with alternatives:

- GLFW raw mouse input on macOS is not a reliable answer for this problem in the Minecraft stack.
- Legacy `NSEvent`, Quartz event taps, or cursor-warpping strategies are more likely to inherit acceleration behavior, mode quirks, or maintenance risk.
- `GameController.framework` provides relative deltas through an API intended for modern games and works cleanly with captured gameplay input.

## User-visible behavior

- On supported macOS versions, open Options > Controls > Mouse Settings and use the vanilla `Raw Mouse Input` toggle.
- Default vanilla behavior is preserved outside captured gameplay.
- If Mod Menu is installed, Mousee exposes a Cloth Config screen for diagnostics toggles.

## Diagnostics

Mousee remains quiet by default.

Diagnostics can be enabled in one of two ways:

- Set the JVM property `-Dmousee.diagnostics=true`.
- Open Mousee's optional Mod Menu config screen and enable one or more diagnostics toggles.

The diagnostics config is stored at `config/mousee.json` and currently contains:

- backend diagnostics logging
- capture state transition logging
- sampled motion logging

## Build

### Requirements

- macOS build host
- Java 25
- Xcode Command Line Tools with `clang++`

### Command

```bash
./gradlew build
```

The build packages the native library into the mod jar at `natives/macos/libmousee_macos.dylib`.

## Installation

1. Install Fabric Loader for Minecraft 26.2.
2. Place the built `mousee-1.0.0.jar` in the client's `mods` directory.
3. Launch Minecraft on macOS.
4. Open Options > Controls > Mouse Settings and ensure `Raw Mouse Input` is enabled.

## Testing checklist

- Confirm the game launches on macOS with the mod installed.
- Confirm the `Raw Mouse Input` option appears in Mouse Settings on supported macOS systems.
- Confirm slow aiming, fast flicks, and continuous turning feel consistent.
- Confirm GUI screens, inventories, pause menus, and uncaptured cursor states remain vanilla.
- Confirm windowed, borderless fullscreen, and fullscreen gameplay all preserve stable camera motion.
- Confirm the mod remains inert on non-macOS systems.
- Confirm the built jar contains `natives/macos/libmousee_macos.dylib`.

## Limitations and edge cases

- The native backend currently requires macOS 14 or newer because it relies on the `GCMouse` path used here.
- Building the packaged native library must happen on macOS because the project compiles against Apple frameworks.
- If the native library cannot be loaded or the backend is unsupported, Mousee falls back to vanilla behavior.
- If Mod Menu is not installed, the Cloth Config diagnostics screen is simply not exposed; gameplay features still work.

## Additional report

See [TECHNICAL_REPORT.md](TECHNICAL_REPORT.md) for the SDL3 and Apple game-input investigation summary, design tradeoffs, and validation notes.

## License

This project is available under the CC0-1.0 license.
