package net.ccbluex.liquidbounce.features.module.modules.xuanrikka;

import net.ccbluex.liquidbounce.features.global.GlobalSettingsClientChat;
import net.ccbluex.liquidbounce.features.module.ClientModule;
import net.ccbluex.liquidbounce.features.module.modules.combat.ModuleNoItemRelease;
import net.ccbluex.liquidbounce.features.module.modules.combat.ModuleRightClicker;
import net.ccbluex.liquidbounce.features.module.modules.combat.ModuleRikkaKAHelper;
import net.ccbluex.liquidbounce.features.module.modules.combat.ModuleTriggerbot;
import net.ccbluex.liquidbounce.features.module.modules.misc.ModuleCloudMusic;
import net.ccbluex.liquidbounce.features.module.modules.render.ModuleRikkaDynamicLights;
import net.ccbluex.liquidbounce.features.module.modules.world.ModuleClutch;
import net.ccbluex.liquidbounce.features.module.modules.world.ModuleRikkaMine;

import java.util.List;

/** The single registration list for client-owned modules; keep upstream's built-in list intact. */
public final class XuanRikkaModules {
    private XuanRikkaModules() { }

    public static List<ClientModule> builtins() {
        return List.of(
            ModuleTriggerbot.INSTANCE,
            ModuleNoItemRelease.INSTANCE,
            ModuleRightClicker.INSTANCE,
            ModuleCloudMusic.INSTANCE,
            GlobalSettingsClientChat.INSTANCE,
            ModuleRikkaDynamicLights.INSTANCE,
            ModuleClutch.INSTANCE,
            ModuleRikkaMine.INSTANCE,
            ModuleRikkaBuild.INSTANCE,
            ModuleRikkaExplore.INSTANCE,
            ModuleRikkaFollow.INSTANCE,
            ModuleRikkaGoto.INSTANCE,
            ModuleRikkaPause.INSTANCE,
            ModuleRikkaResume.INSTANCE,
            ModuleRikkaStop.INSTANCE,
            ModuleRikkaDeathReturn.INSTANCE,
            ModuleRikkaKAHelper.INSTANCE
        );
    }
}
