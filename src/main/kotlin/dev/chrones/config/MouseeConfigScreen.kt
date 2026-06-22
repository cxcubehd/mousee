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

        category.addEntry(
            entries
                .startBooleanToggle(
                    Component.translatable("mousee.config.option.diagnostics_enabled"),
                    snapshot.diagnosticsEnabled,
                ).setDefaultValue(false)
                .setTooltip(Component.translatable("mousee.config.option.diagnostics_enabled.tooltip"))
                .setSaveConsumer { value ->
                    MouseeConfig.update { diagnosticsEnabled = value }
                }.build(),
        )

        category.addEntry(
            entries
                .startBooleanToggle(
                    Component.translatable("mousee.config.option.log_state_transitions"),
                    snapshot.logStateTransitions,
                ).setDefaultValue(false)
                .setTooltip(Component.translatable("mousee.config.option.log_state_transitions.tooltip"))
                .setSaveConsumer { value ->
                    MouseeConfig.update { logStateTransitions = value }
                }.build(),
        )

        category.addEntry(
            entries
                .startBooleanToggle(
                    Component.translatable("mousee.config.option.sample_motion_logging"),
                    snapshot.sampleMotionLogging,
                ).setDefaultValue(false)
                .setTooltip(Component.translatable("mousee.config.option.sample_motion_logging.tooltip"))
                .setSaveConsumer { value ->
                    MouseeConfig.update { sampleMotionLogging = value }
                }.build(),
        )

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
        val platformName = RawMouseController.inactiveReason() ?: "unknown"

        category.addEntry(
            entries
                .startTextDescription(
                    Component.translatable("mousee.config.inactive.not_macos", platformName),
                ).build(),
        )

        category.addEntry(
            entries
                .startTextDescription(
                    Component.translatable("mousee.config.inactive.no_ingame_options"),
                ).build(),
        )

        return builder.build()
    }
}
