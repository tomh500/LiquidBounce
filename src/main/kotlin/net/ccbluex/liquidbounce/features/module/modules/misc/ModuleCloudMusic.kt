/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LiquidBounce is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LiquidBounce. If not, see <https://www.gnu.org/licenses/>.
 */
package net.ccbluex.liquidbounce.features.module.modules.misc

import fengliu.cloudmusic.command.CloudMusicCommands
import fengliu.cloudmusic.config.Configs
import fengliu.cloudmusic.gui.CloudMusicScreen
import net.ccbluex.liquidbounce.LiquidBounce
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.features.module.ModuleOrigin
import net.ccbluex.liquidbounce.utils.client.inGame
import net.ccbluex.liquidbounce.utils.client.mc
import org.lwjgl.glfw.GLFW

/**
 * CloudMusic module
 *
 * Merged NetEase Cloud Music player. Toggling the module opens the in-client
 * music GUI. The original mod is not exposed as a standalone Fabric mod; the
 * `.rikkamusic` (or `.music`) command tree and the HUD widgets are part of the client.
 */
object ModuleCloudMusic : ClientModule(
    "CloudMusic",
    ModuleCategories.MISC,
    bind = GLFW.GLFW_KEY_UNKNOWN,
    disableActivation = true,
    origin = ModuleOrigin.XUAN_RIKKA,
) {

    override fun onRegistration() {
        CloudMusicCommands.register()
    }

    /**
     * Volume slider bridges into the original malilib configuration so the
     * settings screen and the module stay in sync.
     */
    @Suppress("UnusedPrivateProperty")
    private val volume by int("Volume", Configs.PLAY.VOLUME.getIntegerValue(), 0..100).onChange {
        Configs.PLAY.VOLUME.setIntegerValue(it)
        it
    }

    override fun onEnabled() {
        if (!LiquidBounce.isInitialized || !inGame) {
            return
        }

        mc.execute {
            mc.gui.setScreen(CloudMusicScreen())
        }
        super.onEnabled()
    }

}
