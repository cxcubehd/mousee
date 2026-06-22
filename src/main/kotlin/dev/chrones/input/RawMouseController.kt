package dev.chrones.input

import dev.chrones.Mousee
import dev.chrones.config.MouseeConfig
import dev.chrones.platform.MacosRawMouseNative
import net.minecraft.client.Minecraft
import net.minecraft.util.Util
import java.util.Locale
import java.util.concurrent.TimeUnit

object RawMouseController {
    private const val DELTA_X = 0
    private const val DELTA_Y = 1
    private const val POLL_BUFFER_SIZE = 4

    private val drainBuffer = DoubleArray(POLL_BUFFER_SIZE)
    private val isRunningOnMacos = Util.getPlatform() == Util.OS.OSX
    private var nativeLibraryLoaded = false
    private var backendInitialized = false
    private var backendAvailable = false
    private var relativeModeActive = false
    private var lastMotionLogNanos = 0L
    private var lastCaptureState =
        CaptureState(
            requested = false,
            mouseConnected = false,
            rawMouseOption = false,
            mouseGrabbed = false,
        )

    @JvmStatic
    fun initialize() {
        if (!isRunningOnMacos) {
            return
        }

        val diagnostics = diagnosticsEnabled()
        nativeLibraryLoaded = MacosRawMouseNative.load(Mousee.LOGGER)
        if (!nativeLibraryLoaded) {
            return
        }

        backendInitialized =
            runCatching { MacosRawMouseNative.init(diagnostics) }
                .getOrElse { throwable ->
                    Mousee.LOGGER.warn("Mousee could not initialize its native backend", throwable)
                    false
                }
        backendAvailable =
            backendInitialized &&
            runCatching { MacosRawMouseNative.isSupported() }
                .getOrElse { throwable ->
                    Mousee.LOGGER.warn("Mousee could not query native backend support", throwable)
                    false
                }
        runCatching {
            MacosRawMouseNative.setDiagnosticLogging(diagnostics)
        }.onFailure { throwable ->
            Mousee.LOGGER.warn("Mousee could not update native diagnostics", throwable)
        }

        if (diagnostics) {
            Mousee.LOGGER.info(
                "Mousee native backend initialized: supported={}, mouseConnected={}, summary={}",
                backendAvailable,
                MacosRawMouseNative.hasMouse(),
                safeDiagnosticSummary(),
            )
        }
    }

    @JvmStatic
    fun shutdown() {
        if (!nativeLibraryLoaded) {
            return
        }

        setRelativeMode(false)
        runCatching {
            MacosRawMouseNative.shutdown()
        }.onFailure { throwable ->
            Mousee.LOGGER.warn("Mousee could not shut down its native backend cleanly", throwable)
        }

        backendAvailable = false
        backendInitialized = false
        relativeModeActive = false
    }

    @JvmStatic
    fun isMacos(): Boolean = isRunningOnMacos

    @JvmStatic
    fun inactivePlatformName(): String? {
        if (isRunningOnMacos) {
            return null
        }

        return Util.getPlatform().name.lowercase(Locale.ROOT)
    }

    @JvmStatic
    fun isNativeRawMouseSupported(): Boolean = isRunningOnMacos && nativeLibraryLoaded && backendInitialized && backendAvailable

    @JvmStatic
    fun shouldSuppressVanillaCursorDelta(
        minecraft: Minecraft,
        mouseGrabbed: Boolean,
    ): Boolean = synchronizeCaptureState(minecraft, mouseGrabbed).active

    @JvmStatic
    fun pollCameraDeltas(
        minecraft: Minecraft,
        mouseGrabbed: Boolean,
        output: DoubleArray,
    ): Boolean {
        if (output.size < POLL_BUFFER_SIZE) {
            return false
        }

        if (!synchronizeCaptureState(minecraft, mouseGrabbed).active) {
            drainPendingDeltas()
            return false
        }

        val events = MacosRawMouseNative.poll(output)
        if (events <= 0 && output[DELTA_X] == 0.0 && output[DELTA_Y] == 0.0) {
            return false
        }

        logMotionSampleIfNeeded(events, output[DELTA_X], output[DELTA_Y])
        return true
    }

    @JvmStatic
    fun syncCaptureState(
        minecraft: Minecraft,
        mouseGrabbed: Boolean,
    ) {
        synchronizeCaptureState(minecraft, mouseGrabbed)
    }

    @JvmStatic
    fun refreshDiagnostics() {
        if (isNativeRawMouseSupported()) {
            runCatching {
                MacosRawMouseNative.setDiagnosticLogging(diagnosticsEnabled())
            }.onFailure { throwable ->
                Mousee.LOGGER.warn("Mousee could not update native diagnostics", throwable)
            }
        }
    }

    @JvmStatic
    fun diagnosticSummary(): String =
        if (isNativeRawMouseSupported()) {
            safeDiagnosticSummary()
        } else {
            val reason =
                when {
                    !isRunningOnMacos -> "not macOS"
                    !nativeLibraryLoaded ->
                        MacosRawMouseNative.loadFailure()?.javaClass?.simpleName ?: "native library not loaded"
                    !backendInitialized -> "backend not initialized"
                    else -> "unsupported macOS backend"
                }
            "supported=false reason=$reason"
        }

    private fun synchronizeCaptureState(
        minecraft: Minecraft,
        mouseGrabbed: Boolean,
    ): CaptureState {
        val state = evaluateCaptureState(minecraft, mouseGrabbed)
        setRelativeMode(state.active)
        logCaptureStateIfNeeded(state)
        return state
    }

    private fun evaluateCaptureState(
        minecraft: Minecraft,
        mouseGrabbed: Boolean,
    ): CaptureState {
        val rawMouseOption = minecraft.options.rawMouseInput().get()
        val requested =
            isNativeRawMouseSupported() &&
                mouseGrabbed &&
                minecraft.isWindowActive &&
                minecraft.player != null &&
                minecraft.gui.screen() == null &&
                minecraft.gui.overlay() == null &&
                rawMouseOption

        return CaptureState(
            requested = requested,
            mouseConnected = nativeHasMouse(),
            rawMouseOption = rawMouseOption,
            mouseGrabbed = mouseGrabbed,
        )
    }

    private fun nativeHasMouse(): Boolean = isNativeRawMouseSupported() && MacosRawMouseNative.hasMouse()

    private fun setRelativeMode(enabled: Boolean) {
        if (!isNativeRawMouseSupported() || relativeModeActive == enabled) {
            return
        }

        relativeModeActive = enabled
        MacosRawMouseNative.setRelativeMode(enabled)
        if (!enabled) {
            drainPendingDeltas()
        }
    }

    private fun drainPendingDeltas() {
        if (isNativeRawMouseSupported()) {
            MacosRawMouseNative.poll(drainBuffer)
        }
    }

    private fun logCaptureStateIfNeeded(state: CaptureState) {
        if (!MouseeConfig.current.captureStateLogging) {
            lastCaptureState = state
            return
        }

        if (state != lastCaptureState) {
            lastCaptureState = state
            Mousee.LOGGER.info(
                "Mousee capture state changed: active={}, requested={}, mouseConnected={}, rawMouseOption={}, grabbed={}, relativeMode={}",
                state.active,
                state.requested,
                state.mouseConnected,
                state.rawMouseOption,
                state.mouseGrabbed,
                relativeModeActive,
            )
        }
    }

    private fun logMotionSampleIfNeeded(
        events: Int,
        dx: Double,
        dy: Double,
    ) {
        if (!MouseeConfig.current.sampleMotionLogging) {
            return
        }

        val now = System.nanoTime()
        if (now - lastMotionLogNanos < TimeUnit.SECONDS.toNanos(2)) {
            return
        }

        lastMotionLogNanos = now
        Mousee.LOGGER.info(
            "Mousee motion sample: events={}, delta=({}, {}), {}",
            events,
            String.format(Locale.ROOT, "%.3f", dx),
            String.format(Locale.ROOT, "%.3f", dy),
            safeDiagnosticSummary(),
        )
    }

    private fun diagnosticsEnabled(): Boolean =
        MouseeConfig.current.backendDiagnostics ||
            java.lang.Boolean.getBoolean("mousee.diagnostics")

    private fun safeDiagnosticSummary(): String =
        runCatching { MacosRawMouseNative.diagnosticSummary() }
            .getOrElse { throwable -> "diagnosticsUnavailable=${throwable.javaClass.simpleName}" }

    private data class CaptureState(
        val requested: Boolean,
        val mouseConnected: Boolean,
        val rawMouseOption: Boolean,
        val mouseGrabbed: Boolean,
    ) {
        val active: Boolean
            get() = requested && mouseConnected
    }
}
