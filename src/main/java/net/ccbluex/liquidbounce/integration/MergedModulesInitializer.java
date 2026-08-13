package net.ccbluex.liquidbounce.integration;

import fengliu.cloudmusic.CloudMusicClient;
import fi.dy.masa.malilib.MaLiLib;
import net.fabricmc.api.ClientModInitializer;

/**
 * Boots the integrated malilib library and the merged CloudMusic module as part
 * of the LiquidBounce client. The remaining malilib lifecycle (input, config,
 * tick and render handlers) is driven by malilib's own client mixins.
 */
public final class MergedModulesInitializer implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        new MaLiLib().onInitialize();
        CloudMusicClient.init();
    }
}
