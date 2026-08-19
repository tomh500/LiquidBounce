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
import net.ccbluex.liquidbounce.LiquidBounce
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.features.module.ModuleOrigin
import net.ccbluex.liquidbounce.utils.client.inGame
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.integration.screen.CustomScreenType
import net.ccbluex.liquidbounce.integration.screen.impl.CustomStandaloneMinecraftScreen
import org.lwjgl.glfw.GLFW

/**
 * RikkaMusic module
 *
 * Merged NetEase Cloud Music player. Toggling the module opens the in-client
 * music GUI. The original mod is not exposed as a standalone Fabric mod; the
 * `.rikkamusic` (or `.music`) command tree and the HUD widgets are part of the client.
 */
object ModuleCloudMusic : ClientModule(
    "RikkaMusic",
    ModuleCategories.FUN,
    bind = GLFW.GLFW_KEY_UNKNOWN,
    disableActivation = true,
    origin = ModuleOrigin.XUAN_RIKKA,
) {

    private var standaloneScreen: CustomStandaloneMinecraftScreen? = null

    override fun onRegistration() {
        CloudMusicCommands.register()
    }

    override fun onEnabled() {
        if (!LiquidBounce.isInitialized || !inGame) {
            return
        }

        openGui()
        super.onEnabled()
    }

    @JvmStatic
    fun openGui() {
        mc.execute {
            if (standaloneScreen == null) {
                standaloneScreen = CustomStandaloneMinecraftScreen(CustomScreenType.RIKKAMUSIC)
            }
            mc.gui.setScreen(standaloneScreen)
        }
    }

}
