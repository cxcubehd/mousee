package dev.chrones.config

import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
import dev.chrones.Mousee
import net.fabricmc.loader.api.FabricLoader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists

object MouseeConfig {
    private const val CONFIG_FILE = "mousee.json"

    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val path: Path = FabricLoader.getInstance().configDir.resolve(CONFIG_FILE)

    @Volatile
    var current: Data = Data()
        private set

    data class Data(
        @SerializedName(value = "backendDiagnostics", alternate = ["diagnosticsEnabled"])
        var backendDiagnostics: Boolean = false,
        @SerializedName(value = "captureStateLogging", alternate = ["logStateTransitions"])
        var captureStateLogging: Boolean = false,
        @SerializedName(value = "sampleMotionLogging")
        var sampleMotionLogging: Boolean = false,
    )

    fun load() {
        if (!path.exists()) {
            current = Data()
            save()
            return
        }

        current =
            runCatching {
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
