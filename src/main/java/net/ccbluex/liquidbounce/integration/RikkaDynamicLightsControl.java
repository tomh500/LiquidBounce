package net.ccbluex.liquidbounce.integration;

import dev.lambdaurora.lambdynlights.DynamicLightsMode;
import dev.lambdaurora.lambdynlights.LambDynLights;

/** Java-side bridge for Kotlin module code and the integrated engine. */
public final class RikkaDynamicLightsControl {
    private RikkaDynamicLightsControl() {
    }

    public static void setEnabled(boolean enabled) {
        var config = LambDynLights.INSTANCE.config;
        if (enabled && !config.getDynamicLightsMode().isEnabled()) {
            config.setDynamicLightsMode(DynamicLightsMode.FANCY);
        } else if (!enabled && config.getDynamicLightsMode().isEnabled()) {
            config.setDynamicLightsMode(DynamicLightsMode.OFF);
        }
    }

    public static boolean isEnabled() {
        return LambDynLights.INSTANCE.config.getDynamicLightsMode().isEnabled();
    }
}
