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
    "CloudMusic",
    enabled = true,
    alignment = Alignment(Alignment.ScreenAxisX.CENTER_TRANSLATED, 0, Alignment.ScreenAxisY.TOP, 10),
    description = "Shows current music information.",
) {
    private val componentWidth by int("Width", 220, 160..400)
    private val showLyrics by boolean("ShowLyrics", true)
    private val showTranslation by boolean("ShowTranslation", true)
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
                drawScrollingLyric(lyric, textX, lyricY, textWidth, CloudMusicGui.smallScale, CloudMusicGui.TEXT_DIM)
                translation?.let {
                    drawScrollingLyric(it, textX, bounds.yMin + 36f, textWidth, CloudMusicGui.smallScale, CloudMusicGui.TEXT_FAINT)
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
            drawScrollingLyric(lyric, bounds.xMin + 8f, bounds.yMin + 3f, widthSetting - 16f, CloudMusicGui.bodyScale, lyricColor, font, centered = true)
            if (showTranslation) lyrics.translation?.let {
                drawScrollingLyric(it, bounds.xMin + 8f, bounds.yMin + 20f, widthSetting - 16f, CloudMusicGui.smallScale, translationColor, font, centered = true)
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

/** Music-focused Dynamic Island: small while paused and fluidly expands with the active lyric. */
object DynamicIslandHudComponent : NativeHudComponent(
    "MusicDynamicIsland", false,
    Alignment(Alignment.ScreenAxisX.CENTER_TRANSLATED, 0, Alignment.ScreenAxisY.TOP, 10),
    description = "Shows CloudMusic in an expanding Dynamic Island layout.",
) {
    private val compactWidth = 58f
    private val expandedWidth = 268f
    private val islandHeight = 64f
    private val backgroundAlpha by int("BackgroundAlpha", 205, 0..255)
    private var openness = 0f
    private var lastUpdate = 0L
    private var cachedMusic: IMusic? = null
    private var pinBounds: BoundingBox2f? = null
    override val guiScaledWidth get() = expandedWidth
    override val guiScaledHeight get() = islandHeight
    init { registerComponentListen(this) }

    fun handleChatPointer(mouseX: Double, mouseY: Double): Boolean {
        val pin = pinBounds ?: return false
        if (!enabled || mouseX !in pin.xMin.toDouble()..pin.xMax.toDouble() || mouseY !in pin.yMin.toDouble()..pin.yMax.toDouble()) return false
        // The HUD editor stores offsets in its 2x base coordinate system; this puts the island at the true top centre on any GUI scale.
        alignment.setFrom(Alignment(Alignment.ScreenAxisX.CENTER_TRANSLATED, 0, Alignment.ScreenAxisY.TOP, 10))
        return true
    }

    private val renderHandler = handler<OverlayRenderEvent>(priority = EventPriorityConvention.MODEL_STATE) { event ->
        if (HideAppearance.isHidingNow || !enabled) return@handler
        val player = MusicCommand.getPlayer()
        animate(player.isPlaying(), player.getPlayingMusic())
        val music = cachedMusic ?: return@handler
        if (openness <= 0.01f) return@handler
        val width = compactWidth + (expandedWidth - compactWidth) * openness
        val bounds = getGuiScaledBounds(width, islandHeight)
        val contentAlpha = ((openness - .12f) / .88f).coerceIn(0f, 1f)
        with(event.context) {
            drawRoundedRect(bounds.xMin, bounds.yMin, bounds.xMax, bounds.yMax, islandHeight / 2f, Color4b(17, 17, 20, (backgroundAlpha * openness).toInt()), CloudMusicGui.BORDER)
            val cover = 44f
            CloudMusicHudRender.drawCover(this, bounds.xMin + 10f, bounds.yMin + 10f, cover, rounded = true)
            if (contentAlpha > 0f) {
                val textX = bounds.xMin + 64f
                val textWidth = bounds.xMax - textX - 29f
                val lyrics = player.lyricLines()
                val title = lyrics.original ?: music.name
                val sub = lyrics.translation ?: CloudMusicHudRender.subtitle(music)
                drawCloudMusicText(CloudMusicGui.truncate(title, textWidth), textX, bounds.yMin + 14f, CloudMusicGui.bodyScale, Color4b(245, 245, 248, (255 * contentAlpha).toInt()))
                drawCloudMusicText(CloudMusicGui.truncate(sub, textWidth), textX, bounds.yMin + 35f, CloudMusicGui.smallScale, Color4b(184, 184, 194, (255 * contentAlpha).toInt()))
                pinBounds = BoundingBox2f(bounds.xMax - 22f, bounds.yMin + 22f, bounds.xMax - 8f, bounds.yMin + 42f)
                drawCloudMusicText("+", bounds.xMax - 15f, bounds.yMin + 24f, CloudMusicGui.bodyScale, CloudMusicGui.TEXT, horizontalAnchor = HorizontalAnchor.CENTER)
            } else pinBounds = null
        }
    }

    private fun animate(isPlaying: Boolean, music: IMusic?) {
        val now = System.nanoTime()
        val elapsed = if (lastUpdate == 0L) 0f else ((now - lastUpdate) / 1_000_000_000.0).toFloat().coerceAtMost(.1f)
        lastUpdate = now
        if (music != null) cachedMusic = music
        val target = if (isPlaying && music != null) 1f else 0f
        openness += (target - openness) * (1f - kotlin.math.exp((-elapsed * 11f).toDouble()).toFloat())
        if (openness < .005f && target == 0f) { openness = 0f; cachedMusic = null; pinBounds = null }
    }
}

private object CloudMusicHudRender {
    fun fraction(player: fengliu.cloudmusic.util.MusicPlayer, music: IMusic) = (player.playingProgress.toFloat() / (music.durationSecond * 1000L).coerceAtLeast(1L)).coerceIn(0f, 1f)
    fun subtitle(music: IMusic): String = when (music) {
        is Music -> Music.getArtistsName(music.artists).ifBlank { music.album.get("name")?.asString.orEmpty() }
        is DjMusic -> music.dj.get("nickname")?.asString.orEmpty()
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
    return CurrentLyrics(lines.getOrNull(0), lines.getOrNull(1))
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
    val offset = ((System.currentTimeMillis() % ((textWidth + gap) / 30f * 1000f).toLong()) / 1000f) * 30f
    scissorStack.withPush(getBounds(x, y, x + width, y + lineHeight)) {
        val drawX = x - offset
        if (font == LyricFont.LIQUID_BOUNCE) {
            drawCloudMusicText(text, drawX, y, scale, color)
            drawCloudMusicText(text, drawX + textWidth + gap, y, scale, color)
        } else {
            text(mc.font, text, drawX.toInt(), y.toInt(), color.argb, true)
            text(mc.font, text, (drawX + textWidth + gap).toInt(), y.toInt(), color.argb, true)
        }
    }
}
