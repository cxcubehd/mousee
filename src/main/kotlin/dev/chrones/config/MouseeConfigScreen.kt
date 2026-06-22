package dev.chrones.config

import dev.chrones.Mousee
import dev.chrones.input.RawMouseController
import me.shedaniel.clothconfig2.api.ConfigBuilder
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

object MouseeConfigScreen {
    fun create(parent: Screen?): Screen {
        if (!RawMouseController.isMacos()) {
            return createInactiveScreen(parent)
        }

        val snapshot = MouseeConfig.current
        val builder =
            ConfigBuilder
                .create()
                .setParentScreen(parent)
                .setTitle(Component.translatable("mousee.config.title"))
                .setSavingRunnable {
                    MouseeConfig.save()
                    RawMouseController.refreshDiagnostics()
                }

        val category = builder.getOrCreateCategory(Component.translatable("mousee.config.category.diagnostics"))
        val entries = builder.entryBuilder()

        fun addToggle(
            key: String,
            initialValue: Boolean,
            save: (Boolean) -> Unit,
        ) {
            val translationKey = "mousee.config.option.$key"
            category.addEntry(
                entries
                    .startBooleanToggle(Component.translatable(translationKey), initialValue)
                    .setDefaultValue(false)
                    .setTooltip(Component.translatable("$translationKey.tooltip"))
                    .setSaveConsumer(save)
                    .build(),
            )
        }

        addToggle("backend_diagnostics", snapshot.backendDiagnostics) { value ->
            MouseeConfig.update { backendDiagnostics = value }
        }
        addToggle("capture_state_logging", snapshot.captureStateLogging) { value ->
            MouseeConfig.update { captureStateLogging = value }
        }
        addToggle("sample_motion_logging", snapshot.sampleMotionLogging) { value ->
            MouseeConfig.update { sampleMotionLogging = value }
        }

        category.setDescription(
            arrayOf(
                Component.translatable("mousee.config.category.diagnostics.description"),
                Component.literal(RawMouseController.diagnosticSummary()),
            ),
        )

        return builder.build()
    }

    private fun createInactiveScreen(parent: Screen?): Screen {
        val builder =
            ConfigBuilder
                .create()
                .setParentScreen(parent)
                .setTitle(Component.translatable("mousee.config.title"))

        val category = builder.getOrCreateCategory(Component.translatable("mousee.config.category.status"))
        val entries = builder.entryBuilder()
        val platformName = RawMouseController.inactivePlatformName() ?: "unknown"

        category.addEntry(
            entries
                .startTextDescription(
                    Component.translatable("mousee.config.inactive.not_macos", platformName),
                ).build(),
        )

        return builder.build()
    }
}
