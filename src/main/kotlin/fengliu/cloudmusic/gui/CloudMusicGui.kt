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

import fengliu.cloudmusic.config.Configs
import net.ccbluex.liquidbounce.render.FontManager
import net.ccbluex.liquidbounce.render.engine.font.HorizontalAnchor
import net.ccbluex.liquidbounce.render.engine.font.VerticalAnchor
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.integration.theme.ThemeManager
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.ChatFormatting
import net.ccbluex.liquidbounce.utils.text.asPlainText

/**
 * LiquidBounce styled palette and text helpers shared by the merged CloudMusic
 * screens. Colors follow `src-theme/src/colors.scss`.
 */
object CloudMusicGui {

    /**
     * RikkaMusic has a small local palette switch in addition to the global
     * ClickGUI theme. Keep this helper here so every CloudMusic screen uses the
     * same foreground/background contrast when the switch is changed.
     */
    val isLightPalette: Boolean
        get() = Configs.GUI.GUI_THEME.getStringValue().equals("Light", ignoreCase = true)

    /**
     * The native ClickGUI exposes Accent and Tint through theme metadata. Keep
     * this palette derived from those live values so a theme switch recolors
     * RikkaMusic on the next frame instead of leaving it blue and black.
     */
    private fun themeColor(name: String, fallback: Color4b): Color4b =
        ThemeManager.theme?.colors?.inner
            ?.firstOrNull { it.name.equals(name, ignoreCase = true) }
            ?.get() as? Color4b ?: fallback

    private val tint get() = themeColor("Tint", Color4b.BLACK)
    private val accent get() = themeColor("Accent", Color4b(0x46, 0x77, 0xFF))

    val BACKGROUND
        get() = if (isLightPalette) Color4b(248, 249, 251, 255) else tint.with(a = 244)
    val SIDEBAR
        get() = if (isLightPalette) Color4b(241, 244, 248, 255) else tint.with(a = 232)
    val PLAYER_BG
        get() = if (isLightPalette) Color4b(255, 255, 255, 245) else tint.with(a = 224)
    val ACCENT
        get() = if (isLightPalette) Color4b(255, 61, 88, 255) else accent
    val ACCENT_HOVER
        get() = ACCENT.with(a = 210)
    val ACCENT_SUBTLE
        get() = ACCENT.with(a = if (isLightPalette) 28 else 42)
    val TEXT
        get() = if (isLightPalette) Color4b(28, 43, 66, 255) else Color4b.WHITE
    val TEXT_DIM
        get() = if (isLightPalette) Color4b(93, 105, 124, 255) else Color4b(0xD3, 0xD3, 0xD3, 0xFF)
    val TEXT_FAINT
        get() = if (isLightPalette) Color4b(93, 105, 124, 150) else Color4b(0xFF, 0xFF, 0xFF, 0x59)
    val HOVER
        get() = if (isLightPalette) Color4b(28, 43, 66, 16) else Color4b(0xFF, 0xFF, 0xFF, 0x0F)
    val ACTIVE
        get() = if (isLightPalette) Color4b(28, 43, 66, 20) else Color4b(0xFF, 0xFF, 0xFF, 0x14)
    val BORDER
        get() = if (isLightPalette) Color4b(28, 43, 66, 32) else Color4b(0xFF, 0xFF, 0xFF, 0x1A)
    val PROGRESS_BG
        get() = if (isLightPalette) Color4b(28, 43, 66, 38) else Color4b(0xFF, 0xFF, 0xFF, 0x26)
    val ERROR = Color4b(0xFC, 0x41, 0x30, 0xFF)
    val SUCCESS = Color4b(0x4D, 0xAC, 0x68, 0xFF)

    val fontRenderer
        get() = FontManager.FONT_RENDERER

    /**
     * Scale that maps the custom font to the vanilla 9px line height.
     */
    val fontScale
        get() = fontRenderer.scaleToVanillaFont

    val titleScale get() = fontScale * 2.1f
    val headerScale get() = fontScale * 1.4f
    val bodyScale get() = fontScale * 1.25f
    val smallScale get() = fontScale * 1.05f

    fun textWidth(text: String, scale: Float = bodyScale, shadow: Boolean = false): Float =
        fontRenderer.getStringWidth(fontRenderer.process(text), shadow) * scale

    fun truncate(text: String, maxWidth: Float, scale: Float = bodyScale): String {
        if (text.isEmpty()) {
            return text
        }
        if (textWidth(text, scale) <= maxWidth) {
            return text
        }

        val ellipsis = "…"
        var low = 0
        var high = text.length
        while (low < high) {
            val mid = (low + high + 1) / 2
            if (textWidth(text.substring(0, mid), scale) <= maxWidth - textWidth(ellipsis, scale)) {
                low = mid
            } else {
                high = mid - 1
            }
        }
        return text.substring(0, low) + ellipsis
    }
}

/**
 * Draws text with the LiquidBounce font renderer. Returns the drawn width.
 */
context(ctx: GuiGraphicsExtractor)
fun drawCloudMusicText(
    text: String,
    x: Float,
    y: Float,
    scale: Float = CloudMusicGui.bodyScale,
    color: Color4b = CloudMusicGui.TEXT,
    shadow: Boolean = true,
    horizontalAnchor: HorizontalAnchor = HorizontalAnchor.START,
    verticalAnchor: VerticalAnchor = VerticalAnchor.TOP,
): Float {
    val processed = CloudMusicGui.fontRenderer.process(text, color)
    CloudMusicGui.fontRenderer.draw(processed) {
        this.x = x
        this.y = y
        this.scale = scale
        this.shadow = shadow
        this.horizontalAnchor = horizontalAnchor
        this.verticalAnchor = verticalAnchor
    }
    return CloudMusicGui.textWidth(text, scale)
}

/** Draws a ClickGUI-style medium/semibold label using the registered bold face. */
context(ctx: GuiGraphicsExtractor)
fun drawCloudMusicTextBold(
    text: String,
    x: Float,
    y: Float,
    scale: Float = CloudMusicGui.bodyScale,
    color: Color4b = CloudMusicGui.TEXT,
    shadow: Boolean = false,
    horizontalAnchor: HorizontalAnchor = HorizontalAnchor.START,
    verticalAnchor: VerticalAnchor = VerticalAnchor.TOP,
): Float {
    val component = text.asPlainText(ChatFormatting.BOLD)
    val processed = CloudMusicGui.fontRenderer.process(component, color)
    CloudMusicGui.fontRenderer.draw(processed) {
        this.x = x
        this.y = y
        this.scale = scale
        this.shadow = shadow
        this.horizontalAnchor = horizontalAnchor
        this.verticalAnchor = verticalAnchor
    }
    return CloudMusicGui.textWidth(text, scale)
}
