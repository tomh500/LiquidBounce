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
package fengliu.cloudmusic.hud

import fengliu.cloudmusic.command.MusicCommand
import fengliu.cloudmusic.config.Configs
import fengliu.cloudmusic.gui.CloudMusicGui
import fengliu.cloudmusic.gui.drawCloudMusicText
import fengliu.cloudmusic.music163.IMusic
import fengliu.cloudmusic.music163.data.DjMusic
import fengliu.cloudmusic.music163.data.Music
import fengliu.cloudmusic.render.MusicIconTexture
import net.ccbluex.liquidbounce.event.events.OverlayRenderEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.misc.HideAppearance
import net.ccbluex.liquidbounce.integration.theme.component.components.NativeHudComponent
import net.ccbluex.liquidbounce.render.drawQuad
import net.ccbluex.liquidbounce.render.drawRoundedRect
import net.ccbluex.liquidbounce.render.drawTexQuad
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.utils.kotlin.EventPriorityConvention
import net.ccbluex.liquidbounce.utils.render.Alignment
import net.ccbluex.liquidbounce.utils.render.textureSetup

/**
 * Native HUD component for the merged CloudMusic module. Position and visibility
 * are managed by the LiquidBounce HUD editor like any other HUD component.
 */
object CloudMusicHudComponent : NativeHudComponent(
    "CloudMusic",
    enabled = false,
    alignment = Alignment(
        horizontalAlignment = Alignment.ScreenAxisX.RIGHT,
        horizontalOffset = 10,
        verticalAlignment = Alignment.ScreenAxisY.BOTTOM,
        verticalOffset = 34,
    ),
    description = "Shows the currently playing NetEase music and its lyrics.",
) {

    private val componentWidth by int("Width", 260, 140..560)
    private val showLyrics by boolean("ShowLyrics", true)
    private val showProgress by boolean("ShowProgress", true)
    private val backgroundAlpha by int("BackgroundAlpha", 205, 0..255)

    override val guiScaledWidth: Float
        get() = componentWidth.toFloat()

    override val guiScaledHeight: Float
        get() = 86f

    override fun onEnabled() {
        Configs.GUI.MUSIC_INFO.setBooleanValue(true)
        super.onEnabled()
    }

    override fun onDisabled() {
        Configs.GUI.MUSIC_INFO.setBooleanValue(false)
        super.onDisabled()
    }

    @Suppress("unused")
    private val renderHandler = handler<OverlayRenderEvent>(priority = EventPriorityConvention.MODEL_STATE) { event ->
        if (HideAppearance.isHidingNow || !enabled) {
            return@handler
        }

        val player = MusicCommand.getPlayer()
        val music = player.getPlayingMusic() ?: return@handler

        val lyricLines = if (showLyrics) player.getLyric() else emptyArray()
        val visibleLyrics = lyricLines.take(2)
        val componentHeight = 58f + visibleLyrics.size * 14f

        val bounds = getGuiScaledBounds(componentWidth.toFloat(), componentHeight)
        with(event.context) {
            drawRoundedRect(
                bounds.xMin, bounds.yMin, bounds.xMax, bounds.yMax, 6f,
                fillColor = Color4b(0x11, 0x11, 0x14, backgroundAlpha),
                outlineColor = CloudMusicGui.BORDER,
            )

            val coverSize = 36f
            val coverX = bounds.xMin + 9f
            val coverY = bounds.yMin + 9f
            drawRoundedRect(coverX, coverY, coverX + coverSize, coverY + coverSize, 5f, CloudMusicGui.ACTIVE, CloudMusicGui.BORDER)
            if (MusicIconTexture.canUseIcon()) {
                val texture = mc.textureManager.getTexture(MusicIconTexture.MUSIC_ICON_ID)
                if (texture != null) {
                    drawTexQuad(texture.textureSetup, coverX, coverY, coverX + coverSize, coverY + coverSize)
                }
            }

            val textX = bounds.xMin + 56f
            val maxTextWidth = bounds.xMax - textX - 10f
            drawCloudMusicText(
                CloudMusicGui.truncate(music.getDisplayName(), maxTextWidth),
                x = textX, y = bounds.yMin + 9f,
                scale = CloudMusicGui.bodyScale, color = CloudMusicGui.TEXT,
            )
            drawCloudMusicText(
                CloudMusicGui.truncate(music.getSubtitle(), maxTextWidth),
                x = textX, y = bounds.yMin + 28f,
                scale = CloudMusicGui.smallScale, color = CloudMusicGui.TEXT_FAINT,
            )

            if (showProgress) {
                val progressWidth = maxTextWidth
                val progressY = bounds.yMin + 46f
                val progress = player.getPlayingProgress()
                val duration = music.getDurationSecond().times(1000L).coerceAtLeast(1L)
                val fraction = (progress.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                drawQuad(textX, progressY, textX + progressWidth, progressY + 3f, CloudMusicGui.PROGRESS_BG)
                drawQuad(textX, progressY, textX + progressWidth * fraction, progressY + 3f, CloudMusicGui.ACCENT)
            }

            var lyricY = bounds.yMin + 54f
            for (line in visibleLyrics) {
                drawCloudMusicText(
                    CloudMusicGui.truncate(line, componentWidth - 18f),
                    x = bounds.xMin + 10f, y = lyricY,
                    scale = CloudMusicGui.smallScale, color = CloudMusicGui.TEXT_DIM,
                )
                lyricY += 14f
            }
        }
    }

    private fun IMusic.getDisplayName(): String = name

    private fun IMusic.getSubtitle(): String = when (this) {
        is Music -> {
            val albumName = album.get("name")?.asString ?: ""
            val artists = Music.getArtistsName(artists)
            if (albumName.isEmpty()) artists else "$artists - $albumName"
        }
        is DjMusic -> dj.get("nickname")?.asString ?: ""
        else -> ""
    }
}
