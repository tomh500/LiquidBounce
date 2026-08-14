/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
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
import net.ccbluex.liquidbounce.config.types.list.Tagged
import net.ccbluex.liquidbounce.event.events.MouseButtonEvent
import net.ccbluex.liquidbounce.event.events.MouseCursorEvent
import net.ccbluex.liquidbounce.event.events.OverlayRenderEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.misc.HideAppearance
import net.ccbluex.liquidbounce.integration.theme.component.components.NativeHudComponent
import net.ccbluex.liquidbounce.render.drawQuad
import net.ccbluex.liquidbounce.render.drawRoundedRect
import net.ccbluex.liquidbounce.render.drawTexQuad
import net.ccbluex.liquidbounce.render.engine.type.BoundingBox2f
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.utils.kotlin.EventPriorityConvention
import net.ccbluex.liquidbounce.utils.render.Alignment
import net.ccbluex.liquidbounce.utils.render.textureSetup
import net.minecraft.client.gui.screens.ChatScreen
import kotlin.math.roundToLong
import org.lwjgl.glfw.GLFW

/** A compact music player with a conventional HUD and an Apple-style Dynamic Island mode. */
object CloudMusicHudComponent : NativeHudComponent(
    "CloudMusic",
    enabled = false,
    alignment = Alignment(
        horizontalAlignment = Alignment.ScreenAxisX.CENTER_TRANSLATED,
        horizontalOffset = 0,
        verticalAlignment = Alignment.ScreenAxisY.TOP,
        verticalOffset = 10,
    ),
    description = "Shows the currently playing NetEase music and its lyrics.",
) {

    private val style by enumChoice("Style", Style.NORMAL)
    private val componentWidth by int("Width", 220, 160..400).visibleWhen { style == Style.NORMAL }
    private val showLyrics by boolean("ShowLyrics", true).visibleWhen { style == Style.NORMAL }
    private val showProgress by boolean("ShowProgress", true).visibleWhen { style == Style.NORMAL }
    private val backgroundAlpha by int("BackgroundAlpha", 205, 0..255).visibleWhen { style == Style.NORMAL }

    private var islandVisibility = 0f
    private var islandLastUpdate = 0L
    private var islandMusic: IMusic? = null
    private var seeking = false

    override val guiScaledWidth: Float
        get() = if (style == Style.NORMAL) componentWidth.toFloat() else ISLAND_EXPANDED_WIDTH

    override val guiScaledHeight: Float
        get() = if (style == Style.NORMAL) 86f else ISLAND_HEIGHT

    init {
        registerComponentListen(this)
    }

    override fun onEnabled() {
        Configs.GUI.MUSIC_INFO.setBooleanValue(true)
        super.onEnabled()
    }

    override fun onDisabled() {
        Configs.GUI.MUSIC_INFO.setBooleanValue(false)
        islandVisibility = 0f
        super.onDisabled()
    }

    /**
     * Called from ChatScreen only. The normal HUD deliberately keeps gameplay clicks untouched.
     */
    @JvmStatic
    fun handleChatPointer(mouseX: Double, mouseY: Double): Boolean {
        if (!enabled || style != Style.NORMAL || !showProgress) {
            return false
        }

        val player = MusicCommand.getPlayer()
        val music = player.getPlayingMusic() ?: return false
        val bounds = normalBounds(player)
        val progressStart = bounds.xMin + 56f
        val progressEnd = bounds.xMax - 10f
        val progressY = bounds.yMin + 46f
        if (mouseX !in progressStart.toDouble()..progressEnd.toDouble() || mouseY !in (progressY - 7f).toDouble()..(progressY + 9f).toDouble()) {
            return false
        }

        seeking = true
        val fraction = ((mouseX.toFloat() - progressStart) / (progressEnd - progressStart)).coerceIn(0f, 1f)
        player.seek((music.getDurationSecond() * 1000L * fraction).roundToLong())
        return true
    }

    @Suppress("unused")
    private val renderHandler = handler<OverlayRenderEvent>(priority = EventPriorityConvention.MODEL_STATE) { event ->
        if (HideAppearance.isHidingNow || !enabled) {
            return@handler
        }

        when (style) {
            Style.NORMAL -> renderNormal(event)
            Style.DYNAMIC_ISLAND -> renderDynamicIsland(event)
        }
    }

    @Suppress("unused")
    private val mouseButtonHandler = handler<MouseButtonEvent> { event ->
        if (event.button == GLFW.GLFW_MOUSE_BUTTON_LEFT && event.action == GLFW.GLFW_RELEASE) {
            seeking = false
        }
    }

    @Suppress("unused")
    private val mouseCursorHandler = handler<MouseCursorEvent> { event ->
        if (!seeking || mc.gui.screen() !is ChatScreen) {
            return@handler
        }

        val guiX = event.x * mc.window.guiScaledWidth / mc.window.screenWidth
        val guiY = event.y * mc.window.guiScaledHeight / mc.window.screenHeight
        handleChatPointer(guiX, guiY)
    }

    private fun renderNormal(event: OverlayRenderEvent) {
        val player = MusicCommand.getPlayer()
        val music = player.getPlayingMusic() ?: return
        val bounds = normalBounds(player)

        with(event.context) {
            drawRoundedRect(
                bounds.xMin, bounds.yMin, bounds.xMax, bounds.yMax, 6f,
                fillColor = Color4b(0x11, 0x11, 0x14, backgroundAlpha),
                outlineColor = CloudMusicGui.BORDER,
            )

            drawCover(bounds.xMin + 9f, bounds.yMin + 9f, 36f)
            val textX = bounds.xMin + 56f
            val maxTextWidth = bounds.xMax - textX - 10f
            drawCloudMusicText(CloudMusicGui.truncate(music.getDisplayName(), maxTextWidth), textX, bounds.yMin + 9f, CloudMusicGui.bodyScale, CloudMusicGui.TEXT)
            drawCloudMusicText(CloudMusicGui.truncate(music.getSubtitle(), maxTextWidth), textX, bounds.yMin + 28f, CloudMusicGui.smallScale, CloudMusicGui.TEXT_FAINT)

            if (showProgress) {
                val progressY = bounds.yMin + 46f
                val fraction = playbackFraction(player, music)
                drawQuad(textX, progressY, bounds.xMax - 10f, progressY + 3f, CloudMusicGui.PROGRESS_BG)
                drawQuad(textX, progressY, textX + maxTextWidth * fraction, progressY + 3f, CloudMusicGui.ACCENT)
            }

            var lyricY = bounds.yMin + 54f
            normalLyrics(player).forEach { line ->
                drawCloudMusicText(CloudMusicGui.truncate(line, componentWidth - 18f), bounds.xMin + 10f, lyricY, CloudMusicGui.smallScale, CloudMusicGui.TEXT_DIM)
                lyricY += 14f
            }
        }
    }

    private fun renderDynamicIsland(event: OverlayRenderEvent) {
        val player = MusicCommand.getPlayer()
        val music = player.getPlayingMusic()
        updateIslandVisibility(player.isPlaying(), music)
        if (islandVisibility <= 0f || islandMusic == null) {
            return
        }

        val width = ISLAND_COLLAPSED_WIDTH + (ISLAND_EXPANDED_WIDTH - ISLAND_COLLAPSED_WIDTH) * islandVisibility
        val bounds = getGuiScaledBounds(width, ISLAND_HEIGHT)
        val alpha = (232 * islandVisibility).toInt()
        val contentAlpha = ((islandVisibility - 0.2f) / 0.8f).coerceIn(0f, 1f)

        with(event.context) {
            drawRoundedRect(bounds.xMin, bounds.yMin, bounds.xMax, bounds.yMax, ISLAND_HEIGHT / 2f, Color4b(8, 8, 10, alpha))
            val coverSize = 36f * contentAlpha
            if (coverSize > 1f) {
                drawCover(bounds.xMin + 8f, bounds.yMin + (ISLAND_HEIGHT - coverSize) / 2f, coverSize)
            }

            if (contentAlpha > 0f) {
                val textX = bounds.xMin + 52f
                val available = (bounds.xMax - textX - 10f).coerceAtLeast(0f)
                val lyrics = player.getLyric().filter { it.isNotBlank() }
                val line = lyrics.firstOrNull() ?: islandMusic!!.getDisplayName()
                val subline = lyrics.getOrNull(1) ?: islandMusic!!.getSubtitle()
                drawCloudMusicText(CloudMusicGui.truncate(line, available), textX, bounds.yMin + 12f, CloudMusicGui.smallScale, Color4b(235, 235, 240, (255 * contentAlpha).toInt()))
                drawCloudMusicText(CloudMusicGui.truncate(subline, available), textX, bounds.yMin + 27f, CloudMusicGui.smallScale, Color4b(164, 164, 174, (255 * contentAlpha).toInt()))
            }
        }
    }

    private fun updateIslandVisibility(isPlaying: Boolean, music: IMusic?) {
        val now = System.nanoTime()
        val elapsed = if (islandLastUpdate == 0L) 0f else ((now - islandLastUpdate) / 1_000_000_000.0).toFloat().coerceAtMost(0.1f)
        islandLastUpdate = now
        if (music != null) islandMusic = music
        val target = if (isPlaying && music != null) 1f else 0f
        islandVisibility = when {
            islandVisibility < target -> (islandVisibility + elapsed * ISLAND_ANIMATION_SPEED).coerceAtMost(target)
            islandVisibility > target -> (islandVisibility - elapsed * ISLAND_ANIMATION_SPEED).coerceAtLeast(target)
            else -> islandVisibility
        }
        if (islandVisibility == 0f && target == 0f) islandMusic = null
    }

    private fun normalBounds(player: fengliu.cloudmusic.util.MusicPlayer): BoundingBox2f =
        getGuiScaledBounds(componentWidth.toFloat(), 58f + normalLyrics(player).size * 14f)

    private fun normalLyrics(player: fengliu.cloudmusic.util.MusicPlayer): List<String> =
        if (showLyrics) player.getLyric().filter { it.isNotBlank() }.take(2) else emptyList()

    private fun playbackFraction(player: fengliu.cloudmusic.util.MusicPlayer, music: IMusic): Float =
        (player.getPlayingProgress().toFloat() / (music.getDurationSecond() * 1000L).coerceAtLeast(1L)).coerceIn(0f, 1f)

    private fun net.minecraft.client.gui.GuiGraphicsExtractor.drawCover(x: Float, y: Float, size: Float) {
        drawRoundedRect(x, y, x + size, y + size, (size / 7f).coerceAtLeast(2f), CloudMusicGui.ACTIVE, CloudMusicGui.BORDER)
        if (MusicIconTexture.canUseIcon()) {
            mc.textureManager.getTexture(MusicIconTexture.MUSIC_ICON_ID)?.let { texture ->
                drawTexQuad(texture.textureSetup, x, y, x + size, y + size)
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

    private enum class Style(override val tag: String) : Tagged {
        NORMAL("Normal"),
        DYNAMIC_ISLAND("DynamicIsland"),
    }

    private const val ISLAND_COLLAPSED_WIDTH = 42f
    private const val ISLAND_EXPANDED_WIDTH = 180f
    private const val ISLAND_HEIGHT = 52f
    private const val ISLAND_ANIMATION_SPEED = 5f
}
