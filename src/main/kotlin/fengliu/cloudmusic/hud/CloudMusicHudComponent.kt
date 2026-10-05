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
import net.ccbluex.liquidbounce.features.misc.SelfDestruct
import net.ccbluex.liquidbounce.integration.theme.component.components.NativeHudComponent
import net.ccbluex.liquidbounce.integration.theme.component.HudComponentManager
import net.ccbluex.liquidbounce.render.drawQuad
import net.ccbluex.liquidbounce.render.drawCustomElement
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
import net.minecraft.client.renderer.RenderPipelines
import com.mojang.blaze3d.platform.InputConstants
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
        if (!SelfDestruct.isDestructed && enabled) render(event)
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
    private val releaseHandler = handler<MouseButtonEvent> { if (it.button == InputConstants.MOUSE_BUTTON_LEFT && it.action == InputConstants.RELEASE) seeking = false }
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
    private val scale by float("Scale", 1f, 0.25f..4f)
    private val showTranslation by boolean("ShowTranslation", true)
    // These defaults must not access CloudMusicGui during module registration: its font renderer is initialized later.
    private val lyricColor by color("LyricColor", Color4b.WHITE)
    private val colorize by boolean("Colorize", false)
    private val unplayedColor by color("UnplayedColor", Color4b(145, 145, 152, 255))
    private val translationColor by color("TranslationColor", Color4b(255, 255, 255, 89))
    private val background by boolean("Background", false)
    private val font by enumChoice("Font", LyricFont.LIQUID_BOUNCE)
    override val guiScaledWidth get() = widthSetting.toFloat()
    override val guiScaledHeight get() = (if (showTranslation) 34f else 17f) * scale
    init { registerComponentListen(this) }
    private val renderHandler = handler<OverlayRenderEvent> { event ->
        if (SelfDestruct.isDestructed || !enabled) return@handler
        val lyrics = MusicCommand.getPlayer().lyricLines()
        val lyric = lyrics.original ?: return@handler
        val bounds = getGuiScaledBounds()
        with(event.context) {
            if (background) drawRoundedRect(bounds.xMin, bounds.yMin, bounds.xMax, bounds.yMax, 4f, Color4b(0, 0, 0, 155))
            val player = MusicCommand.getPlayer()
            val bodyScale = CloudMusicGui.bodyScale * scale
            val smallScale = CloudMusicGui.smallScale * scale
            if (colorize && font == LyricFont.LIQUID_BOUNCE) {
                drawAnimatedLyric(
                    lyric,
                    bounds.xMin + widthSetting / 2f,
                    bounds.yMin + 3f * scale,
                    bodyScale,
                    lyricColor,
                    unplayedColor,
                    lyrics.progress,
                    widthSetting - 16f * scale,
                )
            } else {
                drawScrollingLyric(lyric, bounds.xMin + 8f * scale, bounds.yMin + 3f * scale, widthSetting - 16f * scale, bodyScale, lyricColor, font, centered = true, scrollProgress = player.scrollProgress())
            }
            if (showTranslation) lyrics.translation?.let {
                drawScrollingLyric(it, bounds.xMin + 8f * scale, bounds.yMin + 20f * scale, widthSetting - 16f * scale, smallScale, translationColor, font, centered = true, scrollProgress = player.scrollProgress())
            }
        }
    }
}

/** Lyrics-first Dynamic Island which contracts and expands around every lyric transition. */
object DynamicIslandHudComponent : NativeHudComponent(
    "MusicDynamicIsland", false,
    Alignment(Alignment.ScreenAxisX.CENTER_TRANSLATED, 0, Alignment.ScreenAxisY.TOP, 0),
    description = "Shows CloudMusic in an expanding Dynamic Island layout.",
) {
    private val minimumWidthSetting by int("MinimumWidth", 104, 80..400)
    private val maximumWidthSetting by int("MaximumWidth", 600, 160..1000)
    private val textScale by float("TextScale", .92f, .5f..2f)
    private val singleLineHeightSetting by int("SingleLineHeight", 22, 22..80)
    private val doubleLineHeightSetting by int("DoubleLineHeight", 31, 31..100)
    private val collapseDurationSetting by int("CollapseDuration", 520, 0..1500)
    private val animationSpeed by float("AnimationSpeed", 16f, 2f..40f)
    private val collapseOnLyricChange by boolean("CollapseOnLyricChange", true)
    private val showLogo by boolean("ShowLogo", true)
    private val showCover by boolean("ShowCover", true)
    private val logoSize by float("LogoSize", 14f, 8f..32f)
    private val coverSize by float("CoverSize", 20f, 8f..40f)
    private val sidePadding by float("SidePadding", 22f, 16f..40f)
    private val colorize by boolean("Colorize", true)
    private val showTranslation by boolean("ShowTranslation", true)
    private val backgroundColor by color("BackgroundColor", Color4b(0, 0, 0, 173))
    private val lyricColor by color("LyricColor", Color4b.WHITE)
    private val unplayedColor by color("UnplayedColor", Color4b(145, 145, 152, 255))
    private val translationColor by color("TranslationColor", Color4b(205, 205, 214, 255))
    private val minimumWidth get() = maxOf(minimumWidthSetting.toFloat(), contentReserve + 1f)
    private val singleLineHeight get() = maxOf(singleLineHeightSetting.toFloat(), textScale * 20f, if (showCover) coverSize else 0f, if (showLogo) logoSize else 0f)
    private val doubleLineHeight get() = maxOf(doubleLineHeightSetting.toFloat(), textScale * 34f, singleLineHeight)
    private val collapseDurationMs get() = collapseDurationSetting.toLong()
    private val contentReserve get() = sidePadding * 2 + (if (showLogo) logoSize + 8f else 0f) + (if (showCover) coverSize + 8f else 0f)
    private var visibility = 0f
    private var displayedWidth = minimumWidth
    private var lastUpdate = 0L
    private var lyricTransitionStarted = 0L
    private var lastLyricKey = ""
    private var lyricContentVisible = true
    private var collapsePending = false
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
        if (SelfDestruct.isDestructed || !enabled) return@handler
        val player = MusicCommand.getPlayer()
        if (player.getPlayingMusic() !== cachedMusic) {
            lastLyricKey = ""
            collapsePending = false
            lyricContentVisible = true
            lyricTransitionStarted = 0L
        }
        val lyrics = player.lyricLines()
        animate(player, player.isPlaying(), player.getPlayingMusic(), lyrics)
        if (cachedMusic == null) return@handler
        if (visibility <= 0.01f) return@handler
        val islandHeight = if (showTranslation && !lyrics.translation.isNullOrBlank()) doubleLineHeight else singleLineHeight
        val bounds = getGuiScaledBounds(displayedWidth, islandHeight)
        val contentAlpha = if (lyricContentVisible) ((visibility - .12f) / .88f).coerceIn(0f, 1f) else 0f
        
        with(event.context) {
            drawDynamicIslandShape(bounds, islandHeight, backgroundColor.fade(visibility))
            // The logo and cover are the collapsed minimum unit; they remain
            // visible while lyric text fades out between timestamped lines.
            val iconY = bounds.yMin + (islandHeight - logoSize) / 2f
            MusicIconTexture.loadLiquidBounceIcon()
            if (showLogo && MusicIconTexture.canUseLiquidBounceIcon()) mc.textureManager.getTexture(MusicIconTexture.LIQUID_BOUNCE_ICON_ID)?.let {
                drawTexQuad(it.textureSetup, bounds.xMin + sidePadding, iconY, bounds.xMin + sidePadding + logoSize, iconY + logoSize)
            }
            if (showCover) CloudMusicHudRender.drawCover(this, bounds.xMax - sidePadding - coverSize, bounds.yMin + (islandHeight - coverSize) / 2f, coverSize, rounded = true)
            val textX = bounds.xMin + sidePadding + (if (showLogo) logoSize + 8f else 0f)
            val textWidth = (bounds.xMax - sidePadding - (if (showCover) coverSize + 8f else 0f) - textX).coerceAtLeast(1f)
            if (contentAlpha > 0f) {
                val lyric = lyrics.original?.takeIf { it.isNotBlank() }
                val translation = lyrics.translation?.takeIf { showTranslation && it.isNotBlank() }
                
                lyric?.let {
                    val primaryY = bounds.yMin + (islandHeight - (if (translation == null) 14f else 28f) * textScale) / 2f
                    val lyricProgress = lyrics.progress
                    scissorStack.withPush(getBounds(textX, bounds.yMin, textX + textWidth, bounds.yMax)) {
                        drawAnimatedLyric(
                            it,
                            textX + textWidth / 2f,
                            primaryY,
                            CloudMusicGui.bodyScale * textScale,
                            lyricColor.fade(contentAlpha),
                            unplayedColor.fade(contentAlpha),
                            if (colorize) lyricProgress else 1f,
                            textWidth,
                            lyricProgress,
                        )
                    }
                }
                translation?.let {
                    drawScrollingLyric(it, textX, bounds.yMin + (islandHeight - 28f * textScale) / 2f + 14f * textScale, textWidth, CloudMusicGui.smallScale * textScale, translationColor.fade(contentAlpha), centered = true, scrollProgress = player.scrollProgress())
                }
            }
            pinBounds = bounds
        }
    }

    private fun animate(player: fengliu.cloudmusic.util.MusicPlayer, isPlaying: Boolean, music: IMusic?, lyrics: CurrentLyrics) {
        val now = System.nanoTime()
        val elapsed = if (lastUpdate == 0L) 0f else ((now - lastUpdate) / 1_000_000_000.0).toFloat().coerceAtMost(.1f)
        lastUpdate = now
        if (music != null) cachedMusic = music
        val target = if (isPlaying && music != null) 1f else 0f
        visibility += (target - visibility) * (1f - kotlin.math.exp((-elapsed * 11f).toDouble()).toFloat())

        val lyricKey = "${lyrics.original}\u0000${lyrics.translation}"
        if (target > 0f && lyricKey != lastLyricKey) {
            if (collapsePending) {
                collapsePending = false
                lyricTransitionStarted = 0L
                lyricContentVisible = true
            } else if (collapseOnLyricChange && collapseDurationMs > 0 && lastLyricKey.isNotEmpty()) {
                lyricTransitionStarted = now
                lyricContentVisible = false
            }
            lastLyricKey = lyricKey
        }
        if (target > 0f && collapseOnLyricChange && collapseDurationMs > 0 && !collapsePending) {
            val times = player.getLyricWindowTimes(0, 1)
            val nextStart = times.getOrNull(1)?.takeIf { it >= player.playingProgress }
            if (nextStart != null && nextStart - player.playingProgress <= collapseDurationMs) {
                collapsePending = true
                lyricTransitionStarted = now
                lyricContentVisible = false
            }
        }
        if (!collapseOnLyricChange || collapseDurationMs == 0L) {
            lyricContentVisible = true
            collapsePending = false
            lyricTransitionStarted = 0L
        }
        if (!lyricContentVisible && lyricTransitionStarted != 0L && now - lyricTransitionStarted >= collapseDurationMs * 1_000_000L) {
            lyricContentVisible = true
            lyricTransitionStarted = 0L
            collapsePending = false
        }
        
        val textWidth = maxOf(
            lyrics.original?.takeIf { it.isNotBlank() }?.let { CloudMusicGui.textWidth(it, CloudMusicGui.bodyScale * textScale) } ?: 0f,
            if (showTranslation) lyrics.translation?.takeIf { it.isNotBlank() }?.let { CloudMusicGui.textWidth(it, CloudMusicGui.smallScale * textScale) } ?: 0f else 0f,
        )
        val expandedTarget = (textWidth + contentReserve).coerceIn(minimumWidth, maxOf(minimumWidth, maximumWidthSetting.toFloat()))
        val widthTarget = if (collapsePending || (lyricTransitionStarted != 0L && !lyricContentVisible)) minimumWidth else expandedTarget
        displayedWidth += (widthTarget - displayedWidth) * (1f - kotlin.math.exp((-elapsed * animationSpeed).toDouble()).toFloat())
        
        if (visibility < .005f && target == 0f) {
            visibility = 0f
            displayedWidth = minimumWidth
            cachedMusic = null
            pinBounds = null
            lyricContentVisible = true
            collapsePending = false
        }
    }
}

/** Top-attached capsule silhouette. The background exactly contains the HUD content. */
private fun net.minecraft.client.gui.GuiGraphicsExtractor.drawDynamicIslandShape(
    bounds: BoundingBox2f,
    height: Float,
    color: Color4b,
) {
    val samplesPerPixel = 4f
    val rows = kotlin.math.ceil(height * samplesPerPixel).toInt().coerceAtLeast(2)
    
    // 控制肩部（顶部反角）与底部圆角的合理尺寸
    val shoulderInset = 12f.coerceAtMost((bounds.xMax - bounds.xMin) / 6f)
    val bottomRadius = 16f.coerceAtMost((bounds.xMax - bounds.xMin) / 4f)

    drawCustomElement(
        pipeline = RenderPipelines.GUI,
        bounds = getBounds(bounds.xMin, bounds.yMin, bounds.xMax, bounds.yMax),
    ) { pose ->
        for (row in 0 until rows) {
            val y = row / samplesPerPixel
            val nextY = ((row + 1f) / samplesPerPixel).coerceAtMost(height)
            
            // 使用当前 y 坐标计算该行的左右内缩量
            val inset = dynamicIslandInset(y, height, shoulderInset, bottomRadius)
            
            addVertexWith2DPose(pose, bounds.xMin + inset, bounds.yMin + y).setColor(color.argb)
            addVertexWith2DPose(pose, bounds.xMin + inset, bounds.yMin + nextY).setColor(color.argb)
            addVertexWith2DPose(pose, bounds.xMax - inset, bounds.yMin + nextY).setColor(color.argb)
            addVertexWith2DPose(pose, bounds.xMax - inset, bounds.yMin + y).setColor(color.argb)
        }
    }
}

private fun dynamicIslandInset(y: Float, height: Float, shoulderInset: Float, bottomRadius: Float): Float {
    val shoulderHeight = 10f.coerceAtMost(height * 0.3f)
    
    // 1. 顶部平滑凹弧
    if (y <= shoulderHeight) {
        val t = (y / shoulderHeight).coerceIn(0f, 1f)
        val concaveArc = kotlin.math.sqrt((1f - (1f - t) * (1f - t)).toDouble()).toFloat()
        // 限制最小内缩量（例如 3.5f），直接切掉 y=0 附近过于向外扩展的尖角
        return (shoulderInset * concaveArc).coerceAtLeast(3.5f)
    }

    // 2. 底部平滑凸圆角
    val realBottomRadius = bottomRadius.coerceAtMost(height - shoulderHeight)
    val bottomStart = height - realBottomRadius
    if (y >= bottomStart) {
        val t = ((y - bottomStart) / realBottomRadius).coerceIn(0f, 1f)
        val convexArc = 1f - kotlin.math.sqrt((1f - t * t).toDouble()).toFloat()
        return shoulderInset + realBottomRadius * convexArc
    }

    // 3. 中间直壁区
    return shoulderInset
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

private data class CurrentLyrics(val original: String?, val translation: String?, val progress: Float = 0f)

private fun fengliu.cloudmusic.util.MusicPlayer.lyricLines(): CurrentLyrics {
    val snapshot = lyricSnapshot
    return CurrentLyrics(snapshot.original().takeIf { it.isNotBlank() }, snapshot.translation().takeIf { it.isNotBlank() }, snapshot.progress())
}

private fun blendColor(from: Color4b, to: Color4b, amount: Float): Color4b {
    val t = amount.coerceIn(0f, 1f)
    return Color4b(
        (from.r + (to.r - from.r) * t).toInt(),
        (from.g + (to.g - from.g) * t).toInt(),
        (from.b + (to.b - from.b) * t).toInt(),
        (from.a + (to.a - from.a) * t).toInt(),
    )
}

private context(ctx: net.minecraft.client.gui.GuiGraphicsExtractor)
fun drawAnimatedLyric(
    text: String,
    centerX: Float,
    y: Float,
    scale: Float,
    playedColor: Color4b,
    pendingColor: Color4b,
    progress: Float,
    availableWidth: Float = Float.POSITIVE_INFINITY,
    scrollProgress: Float = progress,
) {
    if (text.isEmpty() || availableWidth <= 0f) return
    val widths = text.map { CloudMusicGui.textWidth(it.toString(), scale) }
    val totalWidth = widths.sum()
    val visibleWidth = minOf(totalWidth, availableWidth)
    var x = centerX - visibleWidth / 2f - (totalWidth - visibleWidth) * scrollProgress.coerceIn(0f, 1f)
    val boundary = progress.coerceIn(0f, 1f) * text.length
    ctx.scissorStack.withPush(ctx.getBounds(centerX - visibleWidth / 2f, y, centerX + visibleWidth / 2f, y + 20f)) {
        text.forEachIndexed { index, character ->
            val distance = boundary - index
            val amount = distance.coerceIn(0f, 1f)
            val color = blendColor(pendingColor, playedColor, amount)
            drawCloudMusicText(character.toString(), x + widths[index] / 2f, y, scale, color, horizontalAnchor = HorizontalAnchor.CENTER)
            x += widths[index]
        }
    }
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
