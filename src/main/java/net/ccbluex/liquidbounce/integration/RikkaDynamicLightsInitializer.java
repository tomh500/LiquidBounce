package net.ccbluex.liquidbounce.integration;

import dev.lambdaurora.lambdynlights.LambDynLights;
import net.ccbluex.liquidbounce.features.module.ModuleManager;
import net.fabricmc.api.ClientModInitializer;

/** Initializes the integrated dynamic-light engine with the client. */
public final class RikkaDynamicLightsInitializer implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        LambDynLights.INSTANCE.initialize();
        var module = ModuleManager.INSTANCE.get("Rikka DynamicLights");
        if (module != null) {
            module.setEnabled(RikkaDynamicLightsControl.isEnabled());
        }
    }
}
