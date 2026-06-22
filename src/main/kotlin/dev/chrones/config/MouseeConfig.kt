package dev.chrones.config

import com.google.gson.GsonBuilder
import dev.chrones.Mousee
import net.fabricmc.loader.api.FabricLoader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists

object MouseeConfig {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val path: Path = FabricLoader.getInstance().configDir.resolve("${Mousee.MOD_ID}.json")

    @Volatile
    var current: Data = Data()
        private set

    data class Data(
        var diagnosticsEnabled: Boolean = false,
        var sampleMotionLogging: Boolean = false,
        var logStateTransitions: Boolean = false,
    )

    fun load() {
        current =
            runCatching {
                if (!path.exists()) {
                    save()
                    return
                }

                Files.newBufferedReader(path, StandardCharsets.UTF_8).use { reader ->
                    gson.fromJson(reader, Data::class.java) ?: Data()
                }
            }.getOrElse { throwable ->
                Mousee.LOGGER.warn("Failed to load Mousee config; using defaults", throwable)
                Data()
            }
    }

    fun update(mutator: Data.() -> Unit) {
        current = current.copy().apply(mutator)
    }

    fun save() {
        runCatching {
            path.parent.createDirectories()
            Files.newBufferedWriter(path, StandardCharsets.UTF_8).use { writer ->
                gson.toJson(current, writer)
            }
        }.onFailure { throwable ->
            Mousee.LOGGER.warn("Failed to save Mousee config", throwable)
        }
    }
}
