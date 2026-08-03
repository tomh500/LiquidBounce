package net.ccbluex.liquidbounce.features.module.modules.render

import net.ccbluex.liquidbounce.integration.RikkaDynamicLightsControl
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.features.module.ModuleOrigin

/** Integrated dynamic lighting, exposed as a normal LiquidBounce module. */
object ModuleRikkaDynamicLights : ClientModule(
    "DynamicLights",
    ModuleCategories.RENDER,
    state = true,
    origin = ModuleOrigin.XUAN_RIKKA,
) {
    override val baseKey = "liquidbounce.module.rikkaDynamicLights"

    override fun onRegistration() {
        setEngineEnabled(enabled)
    }

    override fun onEnabled() {
        setEngineEnabled(true)
    }

    override fun onDisabled() {
        setEngineEnabled(false)
    }

    private fun setEngineEnabled(enabled: Boolean) {
        RikkaDynamicLightsControl.setEnabled(enabled)
    }
}
