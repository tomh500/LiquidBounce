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

import net.ccbluex.liquidbounce.render.FontManager
import net.ccbluex.liquidbounce.render.engine.font.HorizontalAnchor
import net.ccbluex.liquidbounce.render.engine.font.VerticalAnchor
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.minecraft.client.gui.GuiGraphicsExtractor

/**
 * LiquidBounce styled palette and text helpers shared by the merged CloudMusic
 * screens. Colors follow `src-theme/src/colors.scss`.
 */
object CloudMusicGui {

    // Theme palette
    val BACKGROUND = Color4b(0x0C, 0x0C, 0x0E, 0xFF)
    val SIDEBAR = Color4b(0x11, 0x11, 0x14, 0xFF)
    val PLAYER_BG = Color4b(0x16, 0x16, 0x1A, 0xFF)
    val ACCENT = Color4b(0x46, 0x77, 0xFF, 0xFF)
    val ACCENT_HOVER = Color4b(0x3B, 0x62, 0xD0, 0xFF)
    val ACCENT_SUBTLE = Color4b(0x46, 0x77, 0xFF, 0x22)
    val TEXT = Color4b.WHITE
    val TEXT_DIM = Color4b(0xD3, 0xD3, 0xD3, 0xFF)
    val TEXT_FAINT = Color4b(0xFF, 0xFF, 0xFF, 0x59)
    val HOVER = Color4b(0xFF, 0xFF, 0xFF, 0x0F)
    val ACTIVE = Color4b(0xFF, 0xFF, 0xFF, 0x14)
    val BORDER = Color4b(0xFF, 0xFF, 0xFF, 0x1A)
    val PROGRESS_BG = Color4b(0xFF, 0xFF, 0xFF, 0x26)
    val ERROR = Color4b(0xFC, 0x41, 0x30, 0xFF)
    val SUCCESS = Color4b(0x4D, 0xAC, 0x68, 0xFF)

    val fontRenderer
        get() = FontManager.FONT_RENDERER

    /**
     * Scale that maps the custom font to the vanilla 9px line height.
     */
    val fontScale
        get() = fontRenderer.scaleToVanillaFont

    val titleScale = fontScale * 2.1f
    val headerScale = fontScale * 1.4f
    val bodyScale = fontScale * 1.25f
    val smallScale = fontScale * 1.05f

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
