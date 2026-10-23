package net.ccbluex.liquidbounce.integration;

import fengliu.cloudmusic.CloudMusicClient;
import net.fabricmc.api.ClientModInitializer;

/**
 * Boots CloudMusic after Fabric has initialized the selected MaLiLib dependency.
 * MaLiLib owns its initialization and mixins, whether installed or bundled.
 */
public final class MergedModulesInitializer implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        CloudMusicClient.init();
    }
}
