package dev.chrones

import dev.chrones.config.MouseeConfig
import dev.chrones.input.RawMouseController
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.minecraft.resources.Identifier
import org.slf4j.LoggerFactory

object Mousee : ClientModInitializer {
    const val MOD_ID: String = "mousee"

    val LOGGER = LoggerFactory.getLogger(MOD_ID)

    override fun onInitializeClient() {
        MouseeConfig.load()
        RawMouseController.initialize()
        ClientLifecycleEvents.CLIENT_STOPPING.register { RawMouseController.shutdown() }
    }

    fun id(path: String): Identifier = Identifier.fromNamespaceAndPath(MOD_ID, path)
}
