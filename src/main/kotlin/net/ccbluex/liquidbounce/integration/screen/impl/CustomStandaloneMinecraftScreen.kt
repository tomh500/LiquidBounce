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

package net.ccbluex.liquidbounce.integration.screen.impl

import net.ccbluex.liquidbounce.additions.setPosition
import net.ccbluex.liquidbounce.features.module.modules.render.ModuleHud
import net.ccbluex.liquidbounce.features.module.modules.movement.inventorymove.ModuleInventoryMove
import net.ccbluex.liquidbounce.integration.screen.CustomScreenType
import net.ccbluex.liquidbounce.integration.screen.ScreenManager
import net.ccbluex.liquidbounce.integration.theme.ThemeManager
import net.ccbluex.liquidbounce.utils.text.asPlainText
import net.ccbluex.liquidbounce.utils.client.mc
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.KeyMapping
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import org.lwjgl.glfw.GLFW

class CustomStandaloneMinecraftScreen(
    val screenType: CustomScreenType
) : Screen("VS-${screenType.routeName.uppercase()}".asPlainText()), AutoCloseable {

    val browser = ThemeManager.openInputAwareImmediate(
        screenType,
        true,
        priority = 20,
        settings = ScreenManager.browserSettings
    ) {
        mc.gui.screen() == this@CustomStandaloneMinecraftScreen
    }

    init {
        browser.visible = false
    }

    private var mouseX = 0.0
    private var mouseY = 0.0

    override fun init() {
        browser.visible = true
        mc.mouseHandler.setPosition(mouseX, mouseY)
    }

    fun sync() {
        browser.reload()
    }

    override fun onClose() {
        browser.visible = false

        mouseX = mc.mouseHandler.xpos()
        mouseY = mc.mouseHandler.ypos()
        mc.mouseHandler.grabMouse()
        super.onClose()
    }

    override fun removed() {
        // Gui#setScreen invokes removed() when this screen is replaced. Keep
        // the browser overlay lifecycle tied to the Minecraft screen lifecycle
        // instead of relying only on Escape/onClose paths.
        browser.visible = false
        super.removed()
    }

    /**
     * Disable [Screen.extractBlurredBackground]
     */
    override fun isInGameUi(): Boolean {
        return screenType == CustomScreenType.CLICK_GUI && ModuleHud.hudEditorSelected
    }

    override fun extractTransparentBackground(graphics: GuiGraphicsExtractor) {
        // NOOP because we want no background for HUD editor
    }

    override fun isPauseScreen() = false

    // The browser's InputListener receives these events first. Consuming them
    // here keeps Minecraft's screen/keybinding handling from stealing IME
    // composition while a web input (such as the music search field) is focused.
    override fun keyPressed(event: KeyEvent): Boolean {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            mc.gui.setScreen(null)
        }
        syncInventoryMoveKey(event, true)
        return true
    }

    override fun keyReleased(event: KeyEvent): Boolean {
        syncInventoryMoveKey(event, false)
        return true
    }

    override fun charTyped(event: CharacterEvent) = true

    override fun close() {
        browser.close()
    }

    private fun syncInventoryMoveKey(event: KeyEvent, pressed: Boolean) {
        if (screenType != CustomScreenType.RIKKAMUSIC || !ModuleInventoryMove.shouldHandleInputs(event)) {
            return
        }

        KeyMapping.set(InputConstants.getKey(event), pressed)
    }

}
