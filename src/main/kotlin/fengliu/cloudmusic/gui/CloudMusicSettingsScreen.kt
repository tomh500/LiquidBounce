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
package fengliu.cloudmusic.gui

import fengliu.cloudmusic.command.MusicCommand
import fengliu.cloudmusic.config.Configs
import fengliu.cloudmusic.hud.CloudMusicHudComponent
import fi.dy.masa.malilib.config.IConfigBase
import fi.dy.masa.malilib.config.options.ConfigBoolean
import fi.dy.masa.malilib.config.options.ConfigBooleanHotkeyed
import fi.dy.masa.malilib.config.options.ConfigColor
import fi.dy.masa.malilib.config.options.ConfigHotkey
import fi.dy.masa.malilib.config.options.ConfigInteger
import fi.dy.masa.malilib.config.options.ConfigOptionList
import fi.dy.masa.malilib.config.options.ConfigString
import fi.dy.masa.malilib.hotkeys.IKeybind
import net.ccbluex.liquidbounce.render.drawQuad
import net.ccbluex.liquidbounce.render.getBounds
import net.ccbluex.liquidbounce.render.withPush
import net.ccbluex.liquidbounce.render.drawRoundedRect
import net.ccbluex.liquidbounce.render.engine.font.HorizontalAnchor
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.utils.text.asPlainText
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import org.lwjgl.glfw.GLFW

/**
 * LiquidBounce styled settings screen for the merged CloudMusic module. Shows the
 * original malilib configuration options redrawn with the client's renderer.
 */
class CloudMusicSettingsScreen : Screen("RikkaMusic Settings".asPlainText()) {

    private var windowLeft = 0f
    private var windowTop = 0f
    private var windowWidth = 960f
    private var windowHeight = 640f

    private enum class SettingsTab(val label: String) {
        ALL("全部"),
        PLAY("播放"),
        GUI("界面"),
        COMMAND("命令"),
        LOGIN("登录"),
        HTTP("HTTP"),
        ENABLE("功能"),
        HOTKEY("按键"),
    }

    private data class EditingState(val config: IConfigBase, val buffer: String)

    private data class Quad(val x1: Float, val y1: Float, val x2: Float, val y2: Float) {
        fun contains(x: Float, y: Float): Boolean =
            x in x1..x2 && y in y1..y2

        fun width() = x2 - x1
    }

    private var tab = SettingsTab.ALL
    private var scrollOffset = 0f
    private var editing: EditingState? = null
    private var capturingHotkey: IKeybind? = null
    private var draggingInteger: ConfigInteger? = null

    private fun options(): List<IConfigBase> = when (tab) {
        SettingsTab.ALL -> Configs.ALL.OPTIONS
        SettingsTab.PLAY -> Configs.PLAY.OPTIONS
        SettingsTab.GUI -> Configs.GUI.OPTIONS
        SettingsTab.COMMAND -> Configs.COMMAND.OPTIONS
        SettingsTab.LOGIN -> Configs.LOGIN.OPTIONS
        SettingsTab.HTTP -> Configs.HTTP.OPTIONS
        SettingsTab.ENABLE -> Configs.ENABLE.HOTKEY_LIST.map { it as IConfigBase }
        SettingsTab.HOTKEY -> Configs.HOTKEY.HOTKEY_LIST.map { it as IConfigBase }
    }

    private fun displayName(config: IConfigBase): String =
        config.getCleanName()
            .split('_')
            .filter { it.isNotEmpty() }
            .joinToString(" ") { part ->
                part.replaceFirstChar { it.uppercaseChar() }
            }

    // ------------------------------------------------------------------
    // Layout
    // ------------------------------------------------------------------

    private fun contentX() = 16f
    private fun contentRight() = width - 16f
    private fun contentWidth() = contentRight() - contentX()
    private fun tabBarY() = 52f
    private fun listTop() = tabBarY() + 46f
    private fun listBottom() = height - 16f
    private val rowHeight = 36f

    private fun maxScroll(): Float {
        val rows = options().size
        val contentHeight = rows * rowHeight
        return (contentHeight - (listBottom() - listTop())).coerceAtLeast(0f)
    }

    private fun rowRect(index: Int): Quad {
        val y = listTop() + index * rowHeight - scrollOffset
        return Quad(contentX(), y, contentRight(), y + rowHeight - 2f)
    }

    private fun rowIndexAt(x: Float, y: Float): Int {
        if (x < contentX() || x > contentRight() || y < listTop() || y > listBottom()) {
            return -1
        }
        for (index in options().indices) {
            if (rowRect(index).contains(x, y)) {
                return index
            }
        }
        return -1
    }

    // ------------------------------------------------------------------
    // Input
    // ------------------------------------------------------------------

    override fun mouseClicked(click: MouseButtonEvent, doubled: Boolean): Boolean {
        val mouseX = localX(click.x().toFloat())
        val mouseY = localY(click.y().toFloat())
        if (click.button() != 0) {
            return super.mouseClicked(click, doubled)
        }

        // Back button
        if (backButtonRect().contains(mouseX, mouseY)) {
            close()
            return true
        }

        // Tabs
        for (tab in SettingsTab.entries) {
            if (tabRect(tab).contains(mouseX, mouseY)) {
                this.tab = tab
                scrollOffset = 0f
                editing = null
                capturingHotkey = null
                return true
            }
        }

        val index = rowIndexAt(mouseX, mouseY)
        if (index < 0) {
            return true
        }
        val config = options()[index]
        handleRowClick(config, index, mouseX)

        return true
    }

    private fun handleRowClick(config: IConfigBase, index: Int, mouseX: Float) {
        when (config) {
            is ConfigBoolean -> {
                config.setBooleanValue(!config.getBooleanValue())
                if (config.name.endsWith("music.info")) {
                    CloudMusicHudComponent.enabled = config.getBooleanValue()
                }
            }
            is ConfigOptionList -> {
                config.setOptionListValue(config.getOptionListValue().cycle(true))
            }
            is ConfigColor -> {
                editing = EditingState(config, String.format("#%08X", config.getIntegerValue()))
            }
            is ConfigInteger -> {
                val slider = sliderRect(index)
                if (slider != null && mouseX in slider.x1..slider.x2) {
                    draggingInteger = config
                    updateInteger(config, mouseX, slider)
                } else {
                    // Step with the mouse button side
                    val step = 1
                    val value = if (mouseX > sliderRect(index)?.x2 ?: 0f) {
                        config.getIntegerValue() + step
                    } else {
                        config.getIntegerValue() - step
                    }
                    config.setIntegerValue(value.coerceIn(config.getMinIntegerValue(), config.getMaxIntegerValue()))
                }
            }
            is ConfigString -> {
                editing = EditingState(config, config.getStringValue())
            }
            is ConfigHotkey -> {
                capturingHotkey = config.getKeybind()
            }
        }
    }

    override fun mouseDragged(click: MouseButtonEvent, offsetX: Double, offsetY: Double): Boolean {
        val config = draggingInteger
        if (config != null) {
            val index = options().indexOf(config)
            val slider = sliderRect(index)
            if (slider != null) {
                updateInteger(config, localX(click.x().toFloat()), slider)
            }
            return true
        }
        return super.mouseDragged(click, offsetX, offsetY)
    }

    override fun mouseReleased(click: MouseButtonEvent): Boolean {
        draggingInteger = null
        return super.mouseReleased(click)
    }

    override fun mouseScrolled(
        mouseX: Double,
        mouseY: Double,
        horizontalAmount: Double,
        verticalAmount: Double,
    ): Boolean {
        if (localY(mouseY.toFloat()) in listTop().toDouble()..listBottom().toDouble()) {
            scrollOffset = (scrollOffset - verticalAmount.toFloat() * rowHeight * 0.5f).coerceIn(0f, maxScroll())
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
    }

    override fun keyPressed(input: KeyEvent): Boolean {
        val capture = capturingHotkey
        if (capture != null) {
            if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
                capturingHotkey = null
            } else {
                capture.clearKeys()
                capture.addKey(input.key())
                capturingHotkey = null
            }
            return true
        }

        val edit = editing
        if (edit != null) {
            when (input.key()) {
                GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> commitEdit()
                GLFW.GLFW_KEY_ESCAPE -> editing = null
                GLFW.GLFW_KEY_BACKSPACE -> editing = edit.copy(buffer = edit.buffer.dropLast(1))
            }
            return true
        }

        return super.keyPressed(input)
    }

    override fun charTyped(event: CharacterEvent): Boolean {
        if (editing != null) {
            editing = editing?.copy(buffer = editing!!.buffer + event.codepointAsString())
            return true
        }
        return super.charTyped(event)
    }

    private fun commitEdit() {
        val edit = editing ?: return
        when (val config = edit.config) {
            is ConfigString -> config.setStringValue(edit.buffer)
            is ConfigColor -> {
                val hex = edit.buffer.removePrefix("#")
                hex.toLongOrNull(16)?.let { config.setIntegerValue(it.toInt()) }
            }
            else -> Unit
        }
        editing = null
    }

    private fun updateInteger(config: ConfigInteger, mouseX: Float, slider: Quad) {
        val fraction = ((mouseX - slider.x1) / slider.width()).coerceIn(0f, 1f)
        val min = config.getMinIntegerValue()
        val max = config.getMaxIntegerValue()
        val value = (min + (max - min) * fraction).toInt()
        config.setIntegerValue(value.coerceIn(min, max))
        applyIntegerSideEffects(config)
    }

    private fun applyIntegerSideEffects(config: ConfigInteger) {
        if (config.name.endsWith("volume")) {
            MusicCommand.getPlayer().volumeSet(config.getIntegerValue())
        }
    }

    override fun removed() {
        Configs.INSTANCE.save()
        MusicCommand.getPlayer().volumeSet(Configs.PLAY.VOLUME.getIntegerValue())
        super.removed()
    }

    private fun close() {
        mc.gui.setScreen(CloudMusicScreen())
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    override fun extractRenderState(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        updateWindowBounds()
        with(context) {
            drawRoundedRect(windowLeft, windowTop, windowLeft + windowWidth, windowTop + windowHeight, 8f, CloudMusicGui.BACKGROUND, outlineColor = CloudMusicGui.BORDER)
            pose().pushMatrix()
            pose().translate(windowLeft, windowTop)
            pose().scale(windowWidth / width.toFloat(), windowHeight / height.toFloat())
            drawQuad(0f, 0f, width.toFloat(), height.toFloat(), CloudMusicGui.BACKGROUND)

            // Header
            drawCloudMusicText(
                "RikkaMusic 设置", x = 16f, y = 14f,
                scale = CloudMusicGui.titleScale, color = CloudMusicGui.TEXT,
            )

            val back = backButtonRect()
            val backHovered = back.contains(mouseX.toFloat(), mouseY.toFloat())
            drawRoundedRect(
                back.x1, back.y1, back.x2, back.y2, 6f,
                fillColor = if (backHovered) CloudMusicGui.HOVER else CloudMusicGui.ACTIVE,
                outlineColor = CloudMusicGui.BORDER,
            )
            drawCloudMusicText(
                "返回", x = back.x1 + 12f, y = back.y1 + 11f,
                scale = CloudMusicGui.bodyScale, color = CloudMusicGui.TEXT_DIM,
            )

            // Tabs
            drawTabs(mouseX.toFloat(), mouseY.toFloat())

            // Options
            scissorStack.withPush(getBounds(contentX(), listTop(), contentRight(), listBottom())) {
                for (index in options().indices) {
                    val rect = rowRect(index)
                    if (rect.y2 < listTop() || rect.y1 > listBottom()) {
                        continue
                    }
                    drawRow(options()[index], index, rect, mouseX.toFloat(), mouseY.toFloat())
                }
            }
            pose().popMatrix()
        }
    }

    override fun extractTransparentBackground(graphics: GuiGraphicsExtractor) { }
    override fun isPauseScreen() = false

    private fun updateWindowBounds() {
        windowWidth = minOf(960f, width * 0.92f).coerceAtLeast(520f)
        windowHeight = minOf(640f, height * 0.88f).coerceAtLeast(380f)
        windowLeft = (width - windowWidth) / 2f
        windowTop = (height - windowHeight) / 2f
    }

    private fun localX(x: Float) = ((x - windowLeft) / windowWidth * width).coerceIn(0f, width.toFloat())
    private fun localY(y: Float) = ((y - windowTop) / windowHeight * height).coerceIn(0f, height.toFloat())

    private fun GuiGraphicsExtractor.drawTabs(mouseX: Float, mouseY: Float) {
        var x = contentX()
        for (tab in SettingsTab.entries) {
            val label = tab.label
            val width = CloudMusicGui.textWidth(label) + 24f
            val rect = Quad(x, tabBarY(), x + width, tabBarY() + 34f)
            val selected = tab == this@CloudMusicSettingsScreen.tab
            val hovered = rect.contains(mouseX, mouseY)
            drawRoundedRect(
                rect.x1, rect.y1, rect.x2, rect.y2, 6f,
                fillColor = if (selected) CloudMusicGui.ACCENT_SUBTLE else if (hovered) CloudMusicGui.HOVER else CloudMusicGui.ACTIVE,
                outlineColor = if (selected) CloudMusicGui.ACCENT else CloudMusicGui.BORDER,
                outlineWidth = if (selected) 1.5f else 1f,
            )
            drawCloudMusicText(
                label, x = rect.x1 + 12f, y = rect.y1 + 8f,
                scale = CloudMusicGui.bodyScale,
                color = if (selected) CloudMusicGui.ACCENT else CloudMusicGui.TEXT_DIM,
            )
            x += width + 8f
        }
    }

    private fun GuiGraphicsExtractor.drawRow(
        config: IConfigBase,
        index: Int,
        rect: Quad,
        mouseX: Float,
        mouseY: Float,
    ) {
        val hovered = rect.contains(mouseX, mouseY)
        if (hovered) {
            drawQuad(rect.x1, rect.y1, rect.x2, rect.y2, CloudMusicGui.HOVER)
        }

        drawCloudMusicText(
            CloudMusicGui.truncate(displayName(config), contentWidth() * 0.45f),
            x = rect.x1 + 6f, y = rect.y1 + 10f,
            scale = CloudMusicGui.bodyScale, color = CloudMusicGui.TEXT,
        )

        val controlX = contentRight() - 170f
        when (config) {
            is ConfigBoolean -> drawBooleanControl(config, rect)
            is ConfigOptionList -> drawOptionControl(config, rect)
            is ConfigColor -> drawColorControl(config, rect)
            is ConfigInteger -> drawIntegerControl(config, index, rect)
            is ConfigString -> drawStringControl(config, rect)
            is ConfigHotkey -> drawHotkeyControl(config, rect)
            else -> Unit
        }
    }

    private fun GuiGraphicsExtractor.drawBooleanControl(config: ConfigBoolean, rect: Quad) {
        val enabled = config.getBooleanValue()
        val size = 22f
        val x = contentRight() - size
        val y = rect.y1 + (rect.height() - size) / 2f
        drawRoundedRect(
            x, y, x + size, y + size, size / 2f,
            fillColor = if (enabled) CloudMusicGui.ACCENT else CloudMusicGui.PROGRESS_BG,
            outlineColor = CloudMusicGui.BORDER,
        )
        val knob = 16f
        val knobX = if (enabled) x + size - knob - 3f else x + 3f
        drawRoundedRect(knobX, y + 3f, knobX + knob, y + 3f + knob, knob / 2f, Color4b.WHITE)

        if (config is ConfigBooleanHotkeyed) {
            drawCloudMusicText(
                "按键: ${config.getKeybind().getKeysDisplayString().ifEmpty { "未绑定" }}",
                x = contentRight() - 330f, y = rect.y1 + 10f,
                scale = CloudMusicGui.smallScale, color = CloudMusicGui.TEXT_FAINT,
            )
        }
    }

    private fun GuiGraphicsExtractor.drawOptionControl(config: ConfigOptionList, rect: Quad) {
        val label = config.getOptionListValue().getDisplayName()
        drawCloudMusicText(
            CloudMusicGui.truncate(label, 150f),
            x = contentRight() - 160f, y = rect.y1 + 10f,
            scale = CloudMusicGui.bodyScale, color = CloudMusicGui.ACCENT,
            horizontalAnchor = HorizontalAnchor.END,
        )
    }

    private fun GuiGraphicsExtractor.drawColorControl(config: ConfigColor, rect: Quad) {
        val swatch = 18f
        val y = rect.y1 + (rect.height() - swatch) / 2f
        val x = contentRight() - 120f
        drawRoundedRect(x, y, x + swatch, y + swatch, 4f, Color4b(config.getIntegerValue()), outlineColor = CloudMusicGui.BORDER)
        val text = editing?.takeIf { it.config === config }?.buffer ?: String.format("#%08X", config.getIntegerValue())
        drawCloudMusicText(
            text, x = contentRight() - 4f, y = rect.y1 + 10f,
            scale = CloudMusicGui.smallScale, color = CloudMusicGui.TEXT_DIM,
            horizontalAnchor = HorizontalAnchor.END,
        )
    }

    private fun GuiGraphicsExtractor.drawIntegerControl(config: ConfigInteger, index: Int, rect: Quad) {
        val slider = sliderRect(index)
        if (slider == null) {
            drawCloudMusicText(
                config.getIntegerValue().toString(), x = contentRight() - 4f, y = rect.y1 + 10f,
                scale = CloudMusicGui.bodyScale, color = CloudMusicGui.ACCENT,
                horizontalAnchor = HorizontalAnchor.END,
            )
            return
        }
        val min = config.getMinIntegerValue()
        val max = config.getMaxIntegerValue()
        val value = config.getIntegerValue()
        val fraction = ((value - min).toFloat() / (max - min).toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
        drawQuad(slider.x1, slider.y1, slider.x2, slider.y2, CloudMusicGui.PROGRESS_BG)
        drawQuad(slider.x1, slider.y1, slider.x1 + slider.width() * fraction, slider.y2, CloudMusicGui.ACCENT)
        drawCloudMusicText(
            value.toString(), x = slider.x2 + 6f, y = rect.y1 + 9f,
            scale = CloudMusicGui.smallScale, color = CloudMusicGui.TEXT_DIM,
        )
    }

    private fun GuiGraphicsExtractor.drawStringControl(config: ConfigString, rect: Quad) {
        val editingThis = editing?.takeIf { it.config === config }
        val text = editingThis?.buffer ?: config.getStringValue()
        val display = if (config.name.endsWith("cookie") && editingThis == null && text.length > 12) {
            text.take(6) + "****" + text.takeLast(4)
        } else {
            text
        }
        drawCloudMusicText(
            CloudMusicGui.truncate(display, 160f),
            x = contentRight() - 4f, y = rect.y1 + 10f,
            scale = CloudMusicGui.smallScale, color = CloudMusicGui.TEXT_DIM,
            horizontalAnchor = HorizontalAnchor.END,
        )
    }

    private fun GuiGraphicsExtractor.drawHotkeyControl(config: ConfigHotkey, rect: Quad) {
        val capturing = capturingHotkey === config.getKeybind()
        val label = when {
            capturing -> "正在捕获按键 (Esc 取消)"
            else -> config.getKeybind().getKeysDisplayString().ifEmpty { "未设置" }
        }
        drawCloudMusicText(
            label, x = contentRight() - 4f, y = rect.y1 + 10f,
            scale = CloudMusicGui.smallScale,
            color = if (capturing) CloudMusicGui.ACCENT else CloudMusicGui.TEXT_DIM,
            horizontalAnchor = HorizontalAnchor.END,
        )
    }

    // ------------------------------------------------------------------
    // Rect helpers
    // ------------------------------------------------------------------

    private fun backButtonRect() = Quad(14f, 14f, 100f, 50f)

    private fun tabRect(tab: SettingsTab): Quad {
        var x = contentX()
        for (candidate in SettingsTab.entries) {
            val width = CloudMusicGui.textWidth(candidate.label) + 24f
            if (candidate == tab) {
                return Quad(x, tabBarY(), x + width, tabBarY() + 34f)
            }
            x += width + 8f
        }
        return Quad(0f, 0f, 0f, 0f)
    }

    private fun sliderRect(index: Int): Quad? {
        if (width < 520) {
            return null
        }
        val rect = rowRect(index)
        val y = rect.y1 + (rect.height() - 4f) / 2f
        return Quad(contentRight() - 150f, y, contentRight() - 30f, y + 4f)
    }

    private fun Quad.height() = y2 - y1
}
