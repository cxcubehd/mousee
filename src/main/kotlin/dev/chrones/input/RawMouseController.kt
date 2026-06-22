package dev.chrones.input

import dev.chrones.Mousee
import dev.chrones.config.MouseeConfig
import dev.chrones.platform.MacosRawMouseNative
import net.minecraft.client.Minecraft
import net.minecraft.util.Util
import java.util.Locale
import java.util.concurrent.TimeUnit

object RawMouseController {
    private val drainBuffer = DoubleArray(4)
    private val isMacos = Util.getPlatform() == Util.OS.OSX
    private var initialized = false
    private var nativeSupported = false
    private var relativeMode = false
    private var lastDiagnosticsLogNanos = 0L
    private var lastState = State(false, false, false, false)

    @JvmStatic
    fun initialize() {
        if (!isMacos) {
            return
        }

        val diagnostics = diagnosticsEnabled()
        if (!MacosRawMouseNative.load(Mousee.LOGGER)) {
            return
        }

        nativeSupported = MacosRawMouseNative.init(diagnostics) && MacosRawMouseNative.isSupported()
        initialized = true
        MacosRawMouseNative.setDiagnosticLogging(diagnostics)

        if (diagnostics) {
            Mousee.LOGGER.info(
                "Mousee native raw mouse backend initialized: supported={}, hasMouse={}, summary={}",
                nativeSupported,
                MacosRawMouseNative.hasMouse(),
                safeDiagnosticSummary(),
            )
        }
    }

    @JvmStatic
    fun isMacos(): Boolean = isMacos

    @JvmStatic
    fun inactiveReason(): String? {
        if (isMacos) {
            return null
        }

        return Util.getPlatform().name.lowercase(Locale.ROOT)
    }

    @JvmStatic
    fun isNativeRawMouseSupported(): Boolean = isMacos && initialized && nativeSupported

    @JvmStatic
    fun shouldReplaceVanillaDeltas(
        minecraft: Minecraft,
        mouseGrabbed: Boolean,
    ): Boolean {
        val desired = shouldUseNativeDeltas(minecraft, mouseGrabbed)
        val hasMouse = nativeHasMouse()
        setRelativeMode(desired && hasMouse)
        logStateIfNeeded(desired, hasMouse, minecraft.options.rawMouseInput().get(), mouseGrabbed)
        return desired && hasMouse
    }

    @JvmStatic
    fun pollMinecraftDeltas(
        minecraft: Minecraft,
        mouseGrabbed: Boolean,
        output: DoubleArray,
    ): Boolean {
        if (output.size < 4) {
            return false
        }

        val desired = shouldUseNativeDeltas(minecraft, mouseGrabbed)
        val hasMouse = nativeHasMouse()
        setRelativeMode(desired && hasMouse)
        logStateIfNeeded(desired, hasMouse, minecraft.options.rawMouseInput().get(), mouseGrabbed)

        if (!desired || !hasMouse) {
            drainPendingDeltas()
            return false
        }

        val events = MacosRawMouseNative.poll(output)
        if (events <= 0 && output[0] == 0.0 && output[1] == 0.0) {
            return false
        }

        logMotionSampleIfNeeded(events, output[0], output[1])
        return true
    }

    @JvmStatic
    fun updateCaptureState(
        minecraft: Minecraft,
        mouseGrabbed: Boolean,
    ) {
        setRelativeMode(shouldUseNativeDeltas(minecraft, mouseGrabbed) && nativeHasMouse())
    }

    @JvmStatic
    fun refreshDiagnostics() {
        if (isNativeRawMouseSupported()) {
            MacosRawMouseNative.setDiagnosticLogging(diagnosticsEnabled())
        }
    }

    @JvmStatic
    fun diagnosticSummary(): String =
        if (isNativeRawMouseSupported()) {
            safeDiagnosticSummary()
        } else {
            val reason = MacosRawMouseNative.loadFailure()?.javaClass?.simpleName ?: "not initialized"
            "supported=false reason=$reason"
        }

    private fun shouldUseNativeDeltas(
        minecraft: Minecraft,
        mouseGrabbed: Boolean,
    ): Boolean =
        isNativeRawMouseSupported() &&
            mouseGrabbed &&
            minecraft.isWindowActive &&
            minecraft.player != null &&
            minecraft.gui.screen() == null &&
            minecraft.gui.overlay() == null &&
            minecraft.options.rawMouseInput().get()

    private fun nativeHasMouse(): Boolean = isNativeRawMouseSupported() && MacosRawMouseNative.hasMouse()

    private fun setRelativeMode(enabled: Boolean) {
        if (!isNativeRawMouseSupported() || relativeMode == enabled) {
            return
        }

        relativeMode = enabled
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

    private fun logStateIfNeeded(
        desired: Boolean,
        hasMouse: Boolean,
        optionEnabled: Boolean,
        mouseGrabbed: Boolean,
    ) {
        if (!MouseeConfig.current.logStateTransitions) {
            lastState = State(desired, hasMouse, optionEnabled, mouseGrabbed)
            return
        }

        val state = State(desired, hasMouse, optionEnabled, mouseGrabbed)
        if (state != lastState) {
            lastState = state
            Mousee.LOGGER.info(
                "Mousee raw mouse state changed: desired={}, hasMouse={}, option={}, grabbed={}, relative={}",
                desired,
                hasMouse,
                optionEnabled,
                mouseGrabbed,
                relativeMode,
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
        if (now - lastDiagnosticsLogNanos < TimeUnit.SECONDS.toNanos(2)) {
            return
        }

        lastDiagnosticsLogNanos = now
        Mousee.LOGGER.info(
            "Mousee raw motion sample: events={}, delta=({}, {}), {}",
            events,
            String.format(Locale.ROOT, "%.3f", dx),
            String.format(Locale.ROOT, "%.3f", dy),
            safeDiagnosticSummary(),
        )
    }

    private fun diagnosticsEnabled(): Boolean =
        MouseeConfig.current.diagnosticsEnabled || java.lang.Boolean.getBoolean("mousee.diagnostics")

    private fun safeDiagnosticSummary(): String =
        runCatching { MacosRawMouseNative.diagnosticSummary() }
            .getOrElse { throwable -> "diagnosticsUnavailable=${throwable.javaClass.simpleName}" }

    private data class State(
        val desired: Boolean,
        val hasMouse: Boolean,
        val optionEnabled: Boolean,
        val mouseGrabbed: Boolean,
    )
}
