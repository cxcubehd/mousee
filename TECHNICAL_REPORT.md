# Mousee Technical Report

## Goal

Mousee provides macOS gameplay camera motion from raw relative mouse input while preserving Minecraft's vanilla camera pipeline. The mod changes where captured gameplay deltas come from; it does not replace sensitivity handling, smoothing, or player rotation.

## Input Path

Minecraft consumes mouse movement in `MouseHandler`, accumulates relative deltas, and then applies its normal turn logic. That makes `MouseHandler` the narrowest useful integration point: Mousee can swap the delta source without owning the rest of camera behavior.

Vanilla exposes `Raw Mouse Input` only when `InputConstants.isRawMouseInputSupported()` reports support. On macOS, Mousee reports support only after its native backend has loaded and confirmed availability, which keeps the vanilla option hidden when the backend cannot run.

## Native Backend

The native backend uses Apple's `GameController.framework` and `GCMouse`.

The Objective-C++ layer:

- Requires macOS 14 or newer for this backend path.
- Watches `GCMouse` connect and disconnect notifications.
- Installs a `mouseMovedHandler` for connected mice.
- Accumulates relative deltas behind an `os_unfair_lock`.
- Exposes JNI methods for initialization, support checks, mouse presence, relative-mode state, polling, diagnostics, and shutdown.

This path follows Apple's current game-input APIs and avoids older cursor-warping, event-tap, and legacy `NSEvent` strategies that are more likely to inherit acceleration or cursor-mode edge cases.

## JVM Layer

The JVM layer:

- Extracts the packaged `.dylib` from the mod jar into a temporary directory.
- Loads the JNI library only on macOS.
- Enables native relative motion only while Minecraft has captured gameplay input.
- Requires the vanilla `Raw Mouse Input` option to be enabled.
- Drains pending native deltas whenever Mousee leaves active capture.
- Falls back to vanilla behavior when loading, initialization, platform support, or mouse detection fails.

## Mixins

`InputConstantsMixin` makes the vanilla raw mouse option visible when Mousee's backend is available.

`MouseHandlerMixin` has two responsibilities:

- Cancel vanilla captured cursor-delta accumulation while Mousee is active.
- Poll native deltas before Minecraft calls `turnPlayer`, then add those deltas to `MouseHandler`'s accumulated movement.

This keeps Mousee compatible with Minecraft's sensitivity and framerate-limit input notification paths.

## Diagnostics

Diagnostics are intentionally opt-in. They can be enabled from the Mod Menu config screen or with `-Dmousee.diagnostics=true`.

The diagnostics surface is limited to backend startup/support state, capture-state transitions, and sampled motion logs. Normal gameplay logging remains silent by default.

## Packaging

The Gradle build compiles a universal macOS library with `arm64` and `x86_64` slices, then packages it into the mod jar under `natives/macos/libmousee_macos.dylib`.

Release builds should run on macOS so the packaged jar contains the native backend. Non-macOS builds are useful for JVM-side checks but do not produce a complete runtime artifact.

## Runtime Validation

Recommended manual checks before release:

- Apple Silicon Mac.
- Intel Mac.
- Windowed, fullscreen, and borderless fullscreen modes.
- Slow precision aim, rapid flicks, and long continuous rotation.
- Menus, inventories, pause screens, and uncaptured cursor behavior.
- At least one high-polling-rate mouse.
- Non-macOS launch to confirm Mousee remains inert.
