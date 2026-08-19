package fengliu.cloudmusic.hud

import fengliu.cloudmusic.command.MusicCommand
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
import net.ccbluex.liquidbounce.integration.theme.component.HudComponentManager
import net.ccbluex.liquidbounce.render.drawQuad
import net.ccbluex.liquidbounce.render.drawRoundedRect
import net.ccbluex.liquidbounce.render.drawTexQuad
import net.ccbluex.liquidbounce.render.getBounds
import net.ccbluex.liquidbounce.render.withPush
import net.ccbluex.liquidbounce.render.engine.font.HorizontalAnchor
import net.ccbluex.liquidbounce.render.engine.type.BoundingBox2f
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.utils.kotlin.EventPriorityConvention
import net.ccbluex.liquidbounce.utils.render.Alignment
import net.ccbluex.liquidbounce.utils.render.textureSetup
import net.minecraft.client.gui.screens.ChatScreen
import org.lwjgl.glfw.GLFW
import kotlin.math.roundToLong

/** Original CloudMusic information panel, adapted to the LiquidBounce font. */
object CloudMusicHudComponent : NativeHudComponent(
    "RikkaMusic",
    enabled = true,
    alignment = Alignment(Alignment.ScreenAxisX.CENTER_TRANSLATED, 0, Alignment.ScreenAxisY.TOP, 10),
    description = "Shows current music information.",
) {
    private val componentWidth by int("Width", 220, 160..400)
    private val showLyrics by boolean("ShowLyrics", true)
    private val showTranslationValue = boolean("ShowTranslation", true).onChanged {
        HudComponentManager.updateComponents()
    }
    private val showTranslation get() = showTranslationValue.get()
    private val showProgress by boolean("ShowProgress", true)
    private val backgroundAlpha by int("BackgroundAlpha", 205, 0..255)
    private var seeking = false

    override val guiScaledWidth get() = componentWidth.toFloat()
    override val guiScaledHeight get() = 58f

    init { registerComponentListen(this) }

    @JvmStatic
    fun handleChatPointer(mouseX: Double, mouseY: Double): Boolean {
        if (DynamicIslandHudComponent.handleChatPointer(mouseX, mouseY)) return true
        if (!enabled || !showProgress) return false
        val player = MusicCommand.getPlayer()
        val music = player.getPlayingMusic() ?: return false
        val bounds = getGuiScaledBounds()
        val left = bounds.xMin + 56f
        val right = bounds.xMax - 10f
        val y = bounds.yMin + 46f
        if (mouseX !in left.toDouble()..right.toDouble() || mouseY !in (y - 7).toDouble()..(y + 9).toDouble()) return false
        seeking = true
        player.seek((music.getDurationSecond() * 1000L * ((mouseX.toFloat() - left) / (right - left)).coerceIn(0f, 1f)).roundToLong())
        return true
    }

    private val renderHandler = handler<OverlayRenderEvent>(priority = EventPriorityConvention.MODEL_STATE) { event ->
        if (!HideAppearance.isHidingNow && enabled) render(event)
    }
    private val qrRenderHandler = handler<OverlayRenderEvent> { event ->
        if (!MusicCommand.loadQRCode) return@handler
        val texture = mc.textureManager.getTexture(MusicIconTexture.QR_CODE_ID) ?: return@handler
        val size = 128f
        val x = mc.window.guiScaledWidth - size - 12f
        val y = mc.window.guiScaledHeight - size - 28f
        with(event.context) {
            drawRoundedRect(x - 4f, y - 4f, x + size + 4f, y + size + 24f, 4f, Color4b(255, 255, 255, 235), CloudMusicGui.BORDER)
            drawTexQuad(texture.textureSetup, x, y, x + size, y + size)
            drawCloudMusicText("RikkaMusic QR", x, y + size + 6f, CloudMusicGui.smallScale, CloudMusicGui.TEXT)
        }
    }
    private val releaseHandler = handler<MouseButtonEvent> { if (it.button == GLFW.GLFW_MOUSE_BUTTON_LEFT && it.action == GLFW.GLFW_RELEASE) seeking = false }
    private val dragHandler = handler<MouseCursorEvent> { event ->
        if (seeking && mc.gui.screen() is ChatScreen) handleChatPointer(event.x * mc.window.guiScaledWidth / mc.window.screenWidth, event.y * mc.window.guiScaledHeight / mc.window.screenHeight)
    }

    private fun render(event: OverlayRenderEvent) {
        val player = MusicCommand.getPlayer()
        val music = player.getPlayingMusic() ?: return
        val bounds = getGuiScaledBounds()
        with(event.context) {
            drawRoundedRect(bounds.xMin, bounds.yMin, bounds.xMax, bounds.yMax, 6f, Color4b(17, 17, 20, backgroundAlpha), CloudMusicGui.BORDER)
            CloudMusicHudRender.drawCover(this, bounds.xMin + 9f, bounds.yMin + 9f, 36f, rounded = false)
            val textX = bounds.xMin + 56f
            val textWidth = bounds.xMax - textX - 10f
            drawCloudMusicText(CloudMusicGui.truncate(music.name, textWidth), textX, bounds.yMin + 9f, CloudMusicGui.bodyScale, CloudMusicGui.TEXT)
            val lyrics = player.lyricLines()
            val lyric = lyrics.original.takeIf { showLyrics }
            val translation = lyrics.translation.takeIf { showLyrics && showTranslation }
            if (lyric == null) {
                drawScrollingLyric(CloudMusicHudRender.subtitle(music), textX, bounds.yMin + 28f, textWidth, CloudMusicGui.smallScale, CloudMusicGui.TEXT_FAINT)
            } else {
                val lyricY = if (translation == null) bounds.yMin + 28f else bounds.yMin + 23f
                drawScrollingLyric(lyric, textX, lyricY, textWidth, CloudMusicGui.smallScale, CloudMusicGui.TEXT_DIM, scrollProgress = player.scrollProgress())
                translation?.let {
                    drawScrollingLyric(it, textX, bounds.yMin + 36f, textWidth, CloudMusicGui.smallScale, CloudMusicGui.TEXT_FAINT, scrollProgress = player.scrollProgress())
                }
            }
            if (showProgress) {
                val y = if (translation == null) bounds.yMin + 46f else bounds.yMin + 50f
                val fraction = CloudMusicHudRender.fraction(player, music)
                drawQuad(textX, y, bounds.xMax - 10f, y + 3f, CloudMusicGui.PROGRESS_BG)
                drawQuad(textX, y, textX + textWidth * fraction, y + 3f, CloudMusicGui.ACCENT)
            }
        }
    }
}


/** The original "default" lyric mode as a movable, standalone HUD component. */
object MusicLyricsHudComponent : NativeHudComponent(
    "MusicLyrics", false,
    Alignment(Alignment.ScreenAxisX.CENTER_TRANSLATED, 0, Alignment.ScreenAxisY.BOTTOM, 52),
    description = "Shows the current and following CloudMusic lyrics.",
) {
    private val widthSetting by int("Width", 300, 160..600)
    private val showTranslation by boolean("ShowTranslation", true)
    // These defaults must not access CloudMusicGui during module registration: its font renderer is initialized later.
    private val lyricColor by color("LyricColor", Color4b.WHITE)
    private val translationColor by color("TranslationColor", Color4b(255, 255, 255, 89))
    private val background by boolean("Background", false)
    private val font by enumChoice("Font", LyricFont.LIQUID_BOUNCE)
    override val guiScaledWidth get() = widthSetting.toFloat()
    override val guiScaledHeight get() = if (showTranslation) 34f else 17f
    init { registerComponentListen(this) }
    private val renderHandler = handler<OverlayRenderEvent> { event ->
        if (HideAppearance.isHidingNow || !enabled) return@handler
        val lyrics = MusicCommand.getPlayer().lyricLines()
        val lyric = lyrics.original ?: return@handler
        val bounds = getGuiScaledBounds()
        with(event.context) {
            if (background) drawRoundedRect(bounds.xMin, bounds.yMin, bounds.xMax, bounds.yMax, 4f, Color4b(0, 0, 0, 155))
            drawScrollingLyric(lyric, bounds.xMin + 8f, bounds.yMin + 3f, widthSetting - 16f, CloudMusicGui.bodyScale, lyricColor, font, centered = true, scrollProgress = MusicCommand.getPlayer().scrollProgress())
            if (showTranslation) lyrics.translation?.let {
                drawScrollingLyric(it, bounds.xMin + 8f, bounds.yMin + 20f, widthSetting - 16f, CloudMusicGui.smallScale, translationColor, font, centered = true, scrollProgress = MusicCommand.getPlayer().scrollProgress())
            }
        }
    }
}

/** ActionBar-inspired lyric overlay using LiquidBounce's font renderer. */
object MusicActionbarLyricsHudComponent : NativeHudComponent(
    "MusicActionbarLyrics", false,
    Alignment(Alignment.ScreenAxisX.CENTER_TRANSLATED, 0, Alignment.ScreenAxisY.BOTTOM, 35),
    description = "Shows the current lyric in an ActionBar-style overlay.",
) {
    private val widthSetting by int("Width", 360, 180..700)
    override val guiScaledWidth get() = widthSetting.toFloat()
    override val guiScaledHeight get() = 21f
    init { registerComponentListen(this) }
    private val renderHandler = handler<OverlayRenderEvent> { event ->
        if (HideAppearance.isHidingNow || !enabled) return@handler
        val lyric = MusicCommand.getPlayer().lyricLines().original ?: return@handler
        val bounds = getGuiScaledBounds()
        with(event.context) {
            drawRoundedRect(bounds.xMin, bounds.yMin, bounds.xMax, bounds.yMax, 4f, Color4b(0, 0, 0, 155))
            drawCloudMusicText(CloudMusicGui.truncate(lyric, widthSetting - 16f), (bounds.xMin + bounds.xMax) / 2f, bounds.yMin + 4f, CloudMusicGui.bodyScale, CloudMusicGui.TEXT, horizontalAnchor = HorizontalAnchor.CENTER)
        }
    }
}

/** Lyrics-first Dynamic Island which contracts and expands around every lyric transition. */
object DynamicIslandHudComponent : NativeHudComponent(
    "MusicDynamicIsland", false,
    Alignment(Alignment.ScreenAxisX.CENTER_TRANSLATED, 0, Alignment.ScreenAxisY.TOP, 0),
    description = "Shows CloudMusic in an expanding Dynamic Island layout.",
) {
    private val minimumWidth = 180f
    private val maximumWidth by int("MaximumWidth", 400, 260..520)
    private val singleLineHeight = 44f
    private val doubleLineHeight = 66f
    private val showTranslation by boolean("ShowTranslation", true)
    private val backgroundColor by color("BackgroundColor", Color4b(0, 0, 0, 255))
    private val lyricColor by color("LyricColor", Color4b.WHITE)
    private val translationColor by color("TranslationColor", Color4b(205, 205, 214, 255))
    private var visibility = 0f
    private var displayedWidth = minimumWidth
    private var lastUpdate = 0L
    private var lyricTransitionStarted = 0L
    private var lastLyricKey = ""
    private var cachedMusic: IMusic? = null
    private var pinBounds: BoundingBox2f? = null
    override val guiScaledWidth get() = minimumWidth
    override val guiScaledHeight get() = doubleLineHeight
    init { registerComponentListen(this) }

    fun handleChatPointer(mouseX: Double, mouseY: Double): Boolean {
        val pin = pinBounds ?: return false
        if (!enabled || mouseX !in pin.xMin.toDouble()..pin.xMax.toDouble() || mouseY !in pin.yMin.toDouble()..pin.yMax.toDouble()) return false
        // The HUD editor stores offsets in its 2x base coordinate system; this puts the island at the true top centre on any GUI scale.
        alignment.setFrom(Alignment(Alignment.ScreenAxisX.CENTER_TRANSLATED, 0, Alignment.ScreenAxisY.TOP, 0))
        return true
    }

    private val renderHandler = handler<OverlayRenderEvent>(priority = EventPriorityConvention.MODEL_STATE) { event ->
        if (HideAppearance.isHidingNow || !enabled) return@handler
        val player = MusicCommand.getPlayer()
        val lyrics = player.lyricLines()
        animate(player.isPlaying(), player.getPlayingMusic(), lyrics)
        val music = cachedMusic ?: return@handler
        if (visibility <= 0.01f) return@handler
        val islandHeight = if (showTranslation && !lyrics.translation.isNullOrBlank()) doubleLineHeight else singleLineHeight
        val bounds = getGuiScaledBounds(displayedWidth, islandHeight)
        val contentAlpha = ((visibility - .12f) / .88f).coerceIn(0f, 1f)
        with(event.context) {
            drawDynamicIslandShape(bounds, islandHeight, backgroundColor.fade(visibility))
            if (contentAlpha > 0f) {
                val logoSize = 16f
                val iconY = bounds.yMin + (islandHeight - logoSize) / 2f
                MusicIconTexture.loadLiquidBounceIcon()
                if (MusicIconTexture.canUseLiquidBounceIcon()) mc.textureManager.getTexture(MusicIconTexture.LIQUID_BOUNCE_ICON_ID)?.let {
                    drawTexQuad(it.textureSetup, bounds.xMin + 10f, iconY, bounds.xMin + 10f + logoSize, iconY + logoSize)
                }
                val coverSize = 22f
                CloudMusicHudRender.drawCover(this, bounds.xMax - coverSize - 10f, bounds.yMin + (islandHeight - coverSize) / 2f, coverSize, rounded = true)
                val textX = bounds.xMin + 32f
                val textWidth = (bounds.xMax - coverSize - 16f - textX).coerceAtLeast(1f)
                val lyric = lyrics.original ?: lyrics.translation ?: music.name
                val translation = lyrics.translation.takeIf { showTranslation }
                val primaryY = if (translation == null) bounds.yMin + (islandHeight - 12f) / 2f else bounds.yMin + 8f
                drawScrollingLyric(lyric, textX, primaryY, textWidth, CloudMusicGui.bodyScale, lyricColor.fade(contentAlpha), centered = true, scrollProgress = player.scrollProgress())
                translation?.let {
                    drawScrollingLyric(it, textX, bounds.yMin + 27f, textWidth, CloudMusicGui.smallScale, translationColor.fade(contentAlpha), centered = true, scrollProgress = player.scrollProgress())
                }
                pinBounds = BoundingBox2f(bounds.xMax - 10f, bounds.yMin + 19f, bounds.xMax - 4f, bounds.yMin + 29f)
            } else pinBounds = null
        }
    }

    private fun animate(isPlaying: Boolean, music: IMusic?, lyrics: CurrentLyrics) {
        val now = System.nanoTime()
        val elapsed = if (lastUpdate == 0L) 0f else ((now - lastUpdate) / 1_000_000_000.0).toFloat().coerceAtMost(.1f)
        lastUpdate = now
        if (music != null) cachedMusic = music
        val target = if (isPlaying && music != null) 1f else 0f
        visibility += (target - visibility) * (1f - kotlin.math.exp((-elapsed * 11f).toDouble()).toFloat())

        val lyricKey = "${lyrics.original}\u0000${lyrics.translation}"
        if (target > 0f && lyricKey != lastLyricKey) {
            if (lastLyricKey.isNotEmpty()) lyricTransitionStarted = now
            lastLyricKey = lyricKey
        }
        val textWidth = maxOf(
            CloudMusicGui.textWidth(lyrics.original ?: music?.name.orEmpty(), CloudMusicGui.bodyScale),
            if (showTranslation) CloudMusicGui.textWidth(lyrics.translation.orEmpty(), CloudMusicGui.smallScale) else 0f,
        )
        val expandedTarget = (textWidth + 92f).coerceIn(minimumWidth, maximumWidth.toFloat())
        val collapseFor = if (lyricTransitionStarted == 0L) 0L else (now - lyricTransitionStarted) / 1_000_000
        val widthTarget = if (collapseFor in 0..115) minimumWidth else expandedTarget
        displayedWidth += (widthTarget - displayedWidth) * (1f - kotlin.math.exp((-elapsed * 16f).toDouble()).toFloat())
        if (visibility < .005f && target == 0f) {
            visibility = 0f
            displayedWidth = minimumWidth
            cachedMusic = null
            pinBounds = null
        }
    }
}

/** Standard top-attached Dynamic Island geometry: a pill body clipped into the top edge. */
private fun net.minecraft.client.gui.GuiGraphicsExtractor.drawDynamicIslandShape(
    bounds: BoundingBox2f,
    height: Float,
    color: Color4b,
) {
    val radius = (height * .5f).coerceAtLeast(1f)
    drawRoundedRect(bounds.xMin, bounds.yMin, bounds.xMax, bounds.yMax, radius, color, null)
    // The top edge is attached to the screen. Fill the center of the top cap so
    // the visible silhouette reads as a clipped island instead of a floating pill.
    drawQuad(bounds.xMin + radius, bounds.yMin, bounds.xMax - radius, bounds.yMin + radius, color)
}

private object CloudMusicHudRender {
    fun fraction(player: fengliu.cloudmusic.util.MusicPlayer, music: IMusic) = (player.playingProgress.toFloat() / (music.durationSecond * 1000L).coerceAtLeast(1L)).coerceIn(0f, 1f)
    fun subtitle(music: IMusic): String = when (music) {
        is Music -> Music.getArtistsName(music.artists).ifBlank { music.album.get("name")?.takeUnless { it.isJsonNull }?.asString.orEmpty() }
        is DjMusic -> music.dj.get("nickname")?.takeUnless { it.isJsonNull }?.asString.orEmpty()
        else -> ""
    }
    fun drawCover(context: net.minecraft.client.gui.GuiGraphicsExtractor, x: Float, y: Float, size: Float, rounded: Boolean) {
        if (!rounded) {
            // Keep the original normal-panel look: an uncropped square cover fills its 36px slot.
            context.drawRoundedRect(x, y, x + size, y + size, 5f, CloudMusicGui.ACTIVE, CloudMusicGui.BORDER)
            if (MusicIconTexture.canUseIcon()) mc.textureManager.getTexture(MusicIconTexture.MUSIC_ICON_ID)?.let { context.drawTexQuad(it.textureSetup, x, y, x + size, y + size) }
            return
        }

        context.drawRoundedRect(x, y, x + size, y + size, size / 2f, CloudMusicGui.ACTIVE, CloudMusicGui.BORDER)
        if (MusicIconTexture.canUseIcon()) mc.textureManager.getTexture(MusicIconTexture.MUSIC_CIRCLE_ICON_ID)?.let { context.drawTexQuad(it.textureSetup, x + 1f, y + 1f, x + size - 1f, y + size - 1f) }
        if (rounded) context.drawRoundedRect(x, y, x + size, y + size, size / 2f, null, CloudMusicGui.BORDER)
    }
}

private enum class LyricFont(override val tag: String) : Tagged {
    LIQUID_BOUNCE("LiquidBounce"),
    MINECRAFT("Minecraft"),
}

private data class CurrentLyrics(val original: String?, val translation: String?)

private fun fengliu.cloudmusic.util.MusicPlayer.lyricLines(): CurrentLyrics {
    val lines = getLyric().filter { it.isNotBlank() }
    if (lines.isNotEmpty()) return CurrentLyrics(lines.getOrNull(0), lines.getOrNull(1))
    // During a timestamp gap the lyric worker can briefly expose an empty snapshot.
    // Keep the next timed line visible instead of leaving the HUD blank.
    val upcoming = getLyricWindow(1, 0, 0).filter { it.isNotBlank() }
    val upcomingTranslation = getLyricTranslationWindow(1, 0, 0).filter { it.isNotBlank() }
    return CurrentLyrics(upcoming.getOrNull(0), upcomingTranslation.getOrNull(0))
}

private fun fengliu.cloudmusic.util.MusicPlayer.scrollProgress(): Float? {
    val times = getLyricWindowTimes(0, 1)
    val start = times.getOrNull(0)?.takeIf { it >= 0L } ?: return null
    val end = times.getOrNull(1)?.takeIf { it > start } ?: (start + 3000L)
    return ((playingProgress - start).toFloat() / (end - start).toFloat()).coerceIn(0f, 1f)
}

private fun net.minecraft.client.gui.GuiGraphicsExtractor.drawScrollingLyric(
    text: String,
    x: Float,
    y: Float,
    width: Float,
    scale: Float,
    color: Color4b,
    font: LyricFont = LyricFont.LIQUID_BOUNCE,
    centered: Boolean = false,
    scrollProgress: Float? = null,
) {
    if (text.isEmpty() || width <= 0f) return
    val textWidth = when (font) {
        LyricFont.LIQUID_BOUNCE -> CloudMusicGui.textWidth(text, scale)
        LyricFont.MINECRAFT -> mc.font.width(text).toFloat()
    }
    val lineHeight = if (font == LyricFont.LIQUID_BOUNCE) 14f else 10f
    if (textWidth <= width) {
        if (font == LyricFont.LIQUID_BOUNCE) {
            drawCloudMusicText(text, if (centered) x + width / 2f else x, y, scale, color, horizontalAnchor = if (centered) HorizontalAnchor.CENTER else HorizontalAnchor.START)
        } else if (centered) {
            centeredText(mc.font, text, (x + width / 2f).toInt(), y.toInt(), color.argb)
        } else {
            text(mc.font, text, x.toInt(), y.toInt(), color.argb, true)
        }
        return
    }

    val gap = 24f
    val maxOffset = (textWidth - width + gap).coerceAtLeast(0f)
    val offset = if (scrollProgress == null) 0f else maxOffset * scrollProgress
    scissorStack.withPush(getBounds(x, y, x + width, y + lineHeight)) {
        val drawX = x - offset
        if (font == LyricFont.LIQUID_BOUNCE) {
            drawCloudMusicText(text, drawX, y, scale, color)
            if (scrollProgress == null) drawCloudMusicText(text, drawX + textWidth + gap, y, scale, color)
        } else {
            text(mc.font, text, drawX.toInt(), y.toInt(), color.argb, true)
            if (scrollProgress == null) text(mc.font, text, (drawX + textWidth + gap).toInt(), y.toInt(), color.argb, true)
        }
    }
}
