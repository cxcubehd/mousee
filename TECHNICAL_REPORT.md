# Mousee Technical Report

## Objective

Deliver macOS gameplay camera motion for Minecraft 26.2 that behaves like a modern native macOS game using raw, unaccelerated relative mouse deltas, while preserving vanilla menus and the existing camera sensitivity pipeline.

## Input-path investigation summary

### Minecraft

Minecraft's camera path ultimately consumes relative deltas through `MouseHandler`, then applies vanilla sensitivity and turn logic. That makes `MouseHandler` the correct ownership boundary for replacing the source of deltas without rewriting the rest of the camera stack.

### Vanilla and GLFW on macOS

Vanilla exposes `Raw Mouse Input` only when `InputConstants.isRawMouseInputSupported()` returns true. On macOS, relying on the default GLFW path does not provide the desired modern raw-input behavior for this mod's goal, so Mousee reports support only when its native backend is actually available.

### SDL3 and Apple's modern game-input stack

The relevant architectural takeaway from modern SDL3 on macOS is the preference for Apple's current game-input ecosystem instead of legacy cursor-delta techniques. The important direction is not a line-for-line port of SDL3 internals, but the use of modern Apple input APIs that deliver proper relative mouse motion suitable for games.

Mousee follows that same direction by using `GameController.framework` and `GCMouse` as the native backend.

## Chosen design

### Native layer

The native Objective-C++ layer:

- initializes `GameController.framework`
- watches `GCMouse` device connect and disconnect notifications
- installs a `mouseMovedHandler` on each connected mouse
- accumulates raw relative deltas behind a lock
- exposes a narrow JNI surface for initialization, polling, relative-mode control, and diagnostics

### Java and Kotlin layer

The JVM side:

- loads the packaged `.dylib` from the mod jar
- activates the backend only on macOS
- leaves the mod inert on unsupported platforms or unsupported macOS versions
- enables relative mode only while gameplay has captured the mouse and the player is actively in-world
- injects native deltas into vanilla `MouseHandler` accumulated movement

## Why this approach was selected

This approach was chosen because it best satisfies the prompt's priorities:

- modern Apple-supported input path
- minimal interference with Minecraft's existing camera behavior
- clean macOS-only isolation
- lower conflict risk with other client-side mods
- universal native packaging for Apple Silicon and Intel Macs

## Alternatives considered

### Keep vanilla GLFW handling only

Rejected because the mod's purpose is specifically to improve macOS relative mouse behavior beyond the default stack.

### Legacy event taps, `NSEvent`, or cursor-warping strategies

Rejected because they are less aligned with Apple's current game-input direction and are more likely to carry acceleration, cursor-mode, or maintenance issues.

### Replace more of Minecraft's camera pipeline

Rejected because it would create more compatibility risk and was unnecessary once the correct delta source was identified.

## Cloth Config usage

The gameplay toggle remains the vanilla `Raw Mouse Input` option in Mouse Settings, because that is where users already expect it and it integrates naturally with Minecraft's options system.

Cloth Config is used for optional diagnostics configuration, exposed through Mod Menu when available. This keeps the user-facing gameplay toggle in the vanilla menu while still providing a structured troubleshooting surface for the mod's extra logging features.

## Packaging and runtime notes

- The build produces a universal `libmousee_macos.dylib` with both `arm64` and `x86_64` architectures.
- The final jar packages the native library under `natives/macos/`.
- The backend is silent by default and falls back to vanilla behavior if the native component cannot be loaded.

## Validation status

Completed in this workspace:

- project compiles successfully
- full Gradle build succeeds
- production jar contains `natives/macos/libmousee_macos.dylib`
- raw mouse support is wired through vanilla's built-in Mouse Settings option path
- optional Cloth Config diagnostics screen is implemented for Mod Menu

Manual runtime validation still recommended on target hardware:

- Apple Silicon Mac
- Intel Mac
- fullscreen, borderless fullscreen, and windowed modes
- at least one high-polling-rate mouse
- rapid flick, slow precision, and long continuous rotation tests

## Known constraints

- The current backend path requires macOS 14 or newer.
- Native compilation requires a macOS build host with Apple toolchains.
- The workspace provided here is not a Git repository, so Git-based change review could not be used.