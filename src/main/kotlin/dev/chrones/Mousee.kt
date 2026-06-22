package dev.chrones

import dev.chrones.config.MouseeConfig
import dev.chrones.input.RawMouseController
import net.fabricmc.api.ClientModInitializer
import net.minecraft.resources.Identifier
import org.slf4j.LoggerFactory

object Mousee : ClientModInitializer {
    const val MOD_ID: String = "mousee"

    val LOGGER = LoggerFactory.getLogger(MOD_ID)

    override fun onInitializeClient() {
        MouseeConfig.load()
        RawMouseController.initialize()
    }

    fun id(path: String): Identifier = Identifier.fromNamespaceAndPath(MOD_ID, path)
}
