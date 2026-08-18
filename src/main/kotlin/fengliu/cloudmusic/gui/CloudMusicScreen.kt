package fengliu.cloudmusic.gui

import fengliu.cloudmusic.command.MusicCommand
import fengliu.cloudmusic.config.Configs
import fengliu.cloudmusic.music163.IMusic
import fengliu.cloudmusic.music163.data.PlayList
import net.ccbluex.liquidbounce.render.drawQuad
import net.ccbluex.liquidbounce.render.drawRoundedRect
import net.ccbluex.liquidbounce.render.drawTriangle
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
 * RikkaMusic is intentionally a self-contained application window.  It does
 * not scale a full-screen screen into a rectangle: drawing and input share the
 * same window-local coordinate system.
 */
class CloudMusicScreen : Screen("RikkaMusic".asPlainText()) {
    private data class Rect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
        fun contains(x: Float, y: Float) = x in left..right && y in top..bottom
        val width get() = right - left
    }
    private sealed interface Page {
        data class Tracks(val title: String, val detail: String, val tracks: List<IMusic>) : Page
        data class Notice(val text: String, val error: Boolean = false) : Page
    }
    private data class Library(val name: String, val liked: PlayList, val playlists: List<PlayList>)

    private var left = 0f
    private var top = 0f
    private var windowWidth = 1040f
    private var windowHeight = 680f
    private var draggingWindow = false
    private var dragX = 0f
    private var dragY = 0f
    private var library: Library? = null
    private var cloudTracks: List<IMusic> = emptyList()
    private var cloudLoaded = false
    private var page: Page = Page.Notice("正在连接网易云音乐…")
    private var selectedId: Long? = null
    private var queue: List<IMusic> = emptyList()
    private var requestId = 0L
    private var trackScroll = 0f
    private var sidebarScroll = 0f
    private var search = ""
    private var searchFocused = false
    private var showQueue = false
    private var showSettings = false
    private var draggingProgress = false
    private var draggingVolume = false

    private val right get() = left + windowWidth
    private val bottom get() = top + windowHeight
    private fun x(raw: Float) = raw - left
    private fun y(raw: Float) = raw - top
    private fun inside(rawX: Float, rawY: Float) = rawX in left..right && rawY in top..bottom

    override fun init() {
        super.init()
        windowWidth = minOf(1120f, width * 0.68f).coerceAtLeast(720f)
        windowHeight = minOf(720f, height * 0.76f).coerceAtLeast(460f)
        val storedX = Configs.GUI.WINDOW_X.integerValue
        val storedY = Configs.GUI.WINDOW_Y.integerValue
        left = if (Configs.GUI.DRAGGABLE_WINDOW.booleanValue && storedX >= 0) storedX.toFloat() else (width - windowWidth) / 2f
        top = if (Configs.GUI.DRAGGABLE_WINDOW.booleanValue && storedY >= 0) storedY.toFloat() else (height - windowHeight) / 2f
        left = left.coerceIn(0f, (width - windowWidth).coerceAtLeast(0f))
        top = top.coerceIn(0f, (height - windowHeight).coerceAtLeast(0f))
        loadLibrary()
    }

    private fun loadLibrary() {
        val token = ++requestId
        CloudMusicAsync.run(
            job = {
                val my = MusicCommand.getMy(false)
                val liked = my.likeMusicPlayList()
                Triple(Library(my.name, liked, my.playLists(0, 100).filter { it.creator.get("userId")?.asLong == my.id }), liked.getMusics(), Unit)
            },
            onSuccess = { (data, tracks, _) ->
                if (token != requestId) return@run
                library = data
                page = Page.Tracks(data.liked.name, "喜欢的音乐 · ${tracks.size} 首", tracks)
                selectedId = data.liked.id
                loadCloudDisk(token)
            },
            onError = {
                if (token == requestId) page = Page.Notice("尚未登录或无法连接网易云音乐", true)
            },
        )
    }

    private fun loadCloudDisk(owner: Long) = CloudMusicAsync.run(
        job = { MusicCommand.getMusic163().cloudMusic() },
        onSuccess = { tracks -> if (owner == requestId) { cloudTracks = tracks; cloudLoaded = true } },
        onError = { if (owner == requestId) cloudLoaded = true },
    )

    private fun openPlaylist(playlist: PlayList) {
        selectedId = playlist.id; trackScroll = 0f
        page = Page.Notice("正在加载 ${playlist.name}…")
        val token = ++requestId
        CloudMusicAsync.run(
            job = { MusicCommand.getMusic163().playlist(playlist.id).let { it to it.getMusics() } },
            onSuccess = { (full, tracks) -> if (token == requestId) page = Page.Tracks(full.name, "歌单 · ${tracks.size} 首", tracks) },
            onError = { if (token == requestId) page = Page.Notice("歌单加载失败", true) },
        )
    }

    private fun searchMusic() {
        val query = search.trim(); if (query.isEmpty()) return
        trackScroll = 0f; page = Page.Notice("正在搜索 “$query”…")
        val token = ++requestId
        CloudMusicAsync.run(
            job = { MusicCommand.searchMusics(query) },
            onSuccess = { tracks -> if (token == requestId) page = Page.Tracks("搜索结果", "“$query” · ${tracks.size} 首", tracks) },
            onError = { if (token == requestId) page = Page.Notice("搜索失败，请检查网络连接", true) },
        )
    }

    private fun play(tracks: List<IMusic>, index: Int) {
        if (tracks.isEmpty()) return
        queue = tracks
        MusicCommand.playMusicsFrom(tracks, index)
    }

    private fun titleBar() = Rect(0f, 0f, windowWidth, 42f)
    private fun searchBox() = Rect(SIDEBAR + 22f, 13f, windowWidth - 154f, 43f)
    private fun settingsButton() = Rect(windowWidth - 128f, 13f, windowWidth - 88f, 43f)
    private fun closeButton() = Rect(windowWidth - 76f, 13f, windowWidth - 36f, 43f)
    private fun contentLeft() = SIDEBAR + 22f
    private fun contentRight() = windowWidth - 22f
    private fun contentTop() = 72f
    private fun playerTop() = windowHeight - PLAYER_HEIGHT
    private fun listTop() = contentTop() + 82f
    private fun listBottom() = playerTop() - 12f
    private fun trackRect(index: Int) = Rect(contentLeft(), listTop() + index * ROW_HEIGHT - trackScroll, contentRight(), listTop() + (index + 1) * ROW_HEIGHT - 4f - trackScroll)
    private fun progressRect() = Rect(windowWidth * .36f, playerTop() + 47f, windowWidth * .66f, playerTop() + 51f)
    private fun volumeRect() = Rect(windowWidth - 168f, playerTop() + 47f, windowWidth - 98f, playerTop() + 51f)
    private fun loginButton() = Rect(contentLeft(), contentTop() + 70f, contentLeft() + 118f, contentTop() + 102f)
    private fun maxTrackScroll(): Float = ((page as? Page.Tracks)?.tracks?.size ?: 0) * ROW_HEIGHT.toFloat() - (listBottom() - listTop()).coerceAtLeast(0f)

    override fun mouseClicked(click: MouseButtonEvent, doubled: Boolean): Boolean {
        val rawX = click.x().toFloat(); val rawY = click.y().toFloat()
        if (!inside(rawX, rawY)) return true
        val mx = x(rawX); val my = y(rawY)
        if (click.button() != 0) return true
        if (Configs.GUI.DRAGGABLE_WINDOW.booleanValue && titleBar().contains(mx, my)) {
            draggingWindow = true; dragX = mx; dragY = my; return true
        }
        if (closeButton().contains(mx, my)) { mc.gui.setScreen(null); return true }
        if (settingsButton().contains(mx, my)) { showSettings = !showSettings; showQueue = false; return true }
        if (library == null && loginButton().contains(mx, my)) { mc.gui.setScreen(CloudMusicLoginScreen()); return true }
        if (showSettings) { if (handleSettingsClick(mx, my)) return true }
        if (showQueue) { if (handleQueueClick(mx, my)) return true }
        if (searchBox().contains(mx, my)) { searchFocused = true; return true }
        searchFocused = false
        if (my in playerTop()..windowHeight) return handlePlayerClick(mx, my)
        if (mx < SIDEBAR) return handleSidebarClick(mx, my)
        val tracks = (page as? Page.Tracks)?.tracks ?: return true
        tracks.indices.firstOrNull { trackRect(it).contains(mx, my) }?.let { play(tracks, it) }
        return true
    }

    private fun handleSidebarClick(mx: Float, my: Float): Boolean {
        var row = 88f - sidebarScroll
        fun hit(height: Float): Boolean { val result = my in row..row + height; row += height; return result }
        val current = library
        if (hit(34f) && current != null) { openPlaylist(current.liked); return true }
        if (hit(34f) && current != null) { selectedId = CLOUD_ID; page = Page.Tracks("我的音乐云盘", if (cloudLoaded) "${cloudTracks.size} 首" else "正在加载…", cloudTracks); return true }
        row += 24f
        current?.playlists?.forEach { playlist -> if (hit(32f)) { openPlaylist(playlist); return true } }
        return true
    }

    private fun handlePlayerClick(mx: Float, my: Float): Boolean {
        val player = MusicCommand.getPlayer()
        when {
            Rect(windowWidth / 2f - 72f, playerTop() + 18f, windowWidth / 2f - 42f, playerTop() + 48f).contains(mx, my) -> player.prev()
            Rect(windowWidth / 2f - 18f, playerTop() + 13f, windowWidth / 2f + 18f, playerTop() + 49f).contains(mx, my) -> player.switchPlay()
            Rect(windowWidth / 2f + 42f, playerTop() + 18f, windowWidth / 2f + 72f, playerTop() + 48f).contains(mx, my) -> player.next()
            progressRect().contains(mx, my) -> { draggingProgress = true; seek(mx) }
            volumeRect().contains(mx, my) -> { draggingVolume = true; volume(mx) }
            Rect(windowWidth - 72f, playerTop() + 15f, windowWidth - 34f, playerTop() + 49f).contains(mx, my) -> { showQueue = !showQueue; showSettings = false }
        }
        return true
    }

    private fun seek(mx: Float) {
        val music = MusicCommand.getPlayer().playingMusic ?: return
        val fraction = ((mx - progressRect().left) / progressRect().width).coerceIn(0f, 1f)
        MusicCommand.getPlayer().seek((music.durationSecond * 1000L * fraction).toLong())
    }
    private fun volume(mx: Float) {
        val fraction = ((mx - volumeRect().left) / volumeRect().width).coerceIn(0f, 1f)
        MusicCommand.getPlayer().volumeSet((fraction * 100).toInt())
    }

    override fun mouseDragged(click: MouseButtonEvent, offsetX: Double, offsetY: Double): Boolean {
        val mx = x(click.x().toFloat()); val my = y(click.y().toFloat())
        when {
            draggingWindow -> { left = (click.x().toFloat() - dragX).coerceIn(0f, (width - windowWidth).coerceAtLeast(0f)); top = (click.y().toFloat() - dragY).coerceIn(0f, (height - windowHeight).coerceAtLeast(0f)); Configs.GUI.WINDOW_X.setIntegerValue(left.toInt()); Configs.GUI.WINDOW_Y.setIntegerValue(top.toInt()) }
            draggingProgress -> seek(mx)
            draggingVolume -> volume(mx)
        }
        return true
    }
    override fun mouseReleased(click: MouseButtonEvent): Boolean { draggingWindow = false; draggingProgress = false; draggingVolume = false; return true }
    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        if (!inside(mouseX.toFloat(), mouseY.toFloat())) return true
        if (x(mouseX.toFloat()) < SIDEBAR) sidebarScroll = (sidebarScroll - verticalAmount.toFloat() * 20f).coerceAtLeast(0f)
        else trackScroll = (trackScroll - verticalAmount.toFloat() * ROW_HEIGHT).coerceIn(0f, maxTrackScroll().coerceAtLeast(0f))
        return true
    }
    override fun keyPressed(input: KeyEvent): Boolean {
        if (!searchFocused) return super.keyPressed(input)
        when (input.key()) { GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> searchMusic(); GLFW.GLFW_KEY_BACKSPACE -> search = search.dropLast(1); GLFW.GLFW_KEY_ESCAPE -> searchFocused = false }
        return true
    }
    override fun charTyped(event: CharacterEvent): Boolean { if (searchFocused) { search += event.codepointAsString(); return true }; return super.charTyped(event) }

    override fun extractRenderState(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        with(context) {
            val mx = x(mouseX.toFloat()); val my = y(mouseY.toFloat())
            drawRoundedRect(left, top, right, bottom, 6f, CloudMusicGui.BACKGROUND, outlineColor = CloudMusicGui.BORDER)
            pose().pushMatrix(); pose().translate(left, top)
            drawQuad(0f, 0f, windowWidth, windowHeight, CloudMusicGui.BACKGROUND)
            drawSidebar(mx, my); drawHeader(mx, my); drawPage(mx, my); drawPlayer(mx, my)
            if (showQueue) drawQueue(mx, my)
            if (showSettings) drawSettings(mx, my)
            pose().popMatrix()
        }
    }
    override fun extractTransparentBackground(graphics: GuiGraphicsExtractor) { }
    override fun isPauseScreen() = false

    private fun GuiGraphicsExtractor.drawSidebar(mx: Float, my: Float) {
        drawQuad(0f, 0f, SIDEBAR, windowHeight, CloudMusicGui.SIDEBAR)
        drawCloudMusicText("RikkaMusic", 20f, 18f, CloudMusicGui.headerScale, CloudMusicGui.TEXT)
        drawCloudMusicText("MUSIC LIBRARY", 20f, 39f, CloudMusicGui.smallScale, CloudMusicGui.TEXT_FAINT)
        var row = 88f - sidebarScroll
        navRow("喜欢的音乐", "♥", library?.liked?.id, row, mx, my); row += 34f
        navRow("我的音乐云盘", "☁", CLOUD_ID, row, mx, my, !cloudLoaded); row += 48f
        drawCloudMusicText("创建的歌单", 20f, row, CloudMusicGui.smallScale, CloudMusicGui.TEXT_FAINT); row += 24f
        library?.playlists?.forEach { playlist -> navRow(CloudMusicGui.truncate(playlist.name, SIDEBAR - 56f), "♪", playlist.id, row, mx, my); row += 32f }
        drawQuad(16f, windowHeight - 57f, SIDEBAR - 16f, windowHeight - 56f, CloudMusicGui.BORDER)
        drawCloudMusicText(library?.name ?: "未登录", 20f, windowHeight - 39f, CloudMusicGui.bodyScale, CloudMusicGui.TEXT_DIM)
    }
    private fun GuiGraphicsExtractor.navRow(label: String, icon: String, id: Long?, row: Float, mx: Float, my: Float, loading: Boolean = false) {
        val selected = id == selectedId; val hovered = mx in 8f..SIDEBAR - 8f && my in row..row + 29f
        if (selected) { drawQuad(8f, row, SIDEBAR, row + 29f, CloudMusicGui.ACCENT_SUBTLE); drawQuad(8f, row + 4f, 10f, row + 25f, CloudMusicGui.ACCENT) } else if (hovered) drawQuad(8f, row, SIDEBAR - 8f, row + 29f, CloudMusicGui.HOVER)
        drawCloudMusicText(icon, 20f, row + 7f, CloudMusicGui.bodyScale, if (selected) CloudMusicGui.ACCENT else CloudMusicGui.TEXT_DIM)
        drawCloudMusicText(label, 45f, row + 7f, CloudMusicGui.bodyScale, if (loading) CloudMusicGui.TEXT_FAINT else CloudMusicGui.TEXT_DIM)
    }
    private fun GuiGraphicsExtractor.drawHeader(mx: Float, my: Float) {
        val box = searchBox(); drawRoundedRect(box.left, box.top, box.right, box.bottom, 5f, CloudMusicGui.ACTIVE, outlineColor = if (searchFocused) CloudMusicGui.ACCENT else CloudMusicGui.BORDER)
        drawCloudMusicText(if (search.isEmpty()) "搜索歌曲、歌手或专辑" else CloudMusicGui.truncate(search, box.width - 35f), box.left + 14f, box.top + 9f, CloudMusicGui.bodyScale, if (search.isEmpty()) CloudMusicGui.TEXT_FAINT else CloudMusicGui.TEXT)
        drawCloudMusicText("⌕", box.right - 19f, box.top + 8f, CloudMusicGui.headerScale, CloudMusicGui.TEXT_DIM)
        iconButton(settingsButton(), "⚙", mx, my); iconButton(closeButton(), "×", mx, my)
    }
    private fun GuiGraphicsExtractor.drawPage(mx: Float, my: Float) {
        when (val active = page) {
        is Page.Notice -> {
            drawCloudMusicText(active.text, contentLeft(), contentTop() + 36f, CloudMusicGui.bodyScale, if (active.error) CloudMusicGui.ERROR else CloudMusicGui.TEXT_DIM)
            if (library == null) {
                val button = loginButton()
                drawRoundedRect(button.left, button.top, button.right, button.bottom, 5f, CloudMusicGui.ACCENT)
                drawCloudMusicText("扫码登录", button.left + button.width / 2f, button.top + 9f, CloudMusicGui.bodyScale, Color4b.BLACK, horizontalAnchor = HorizontalAnchor.CENTER)
                drawCloudMusicText("登录后可查看歌单、云盘并播放音乐", contentLeft(), button.bottom + 18f, CloudMusicGui.smallScale, CloudMusicGui.TEXT_FAINT)
            }
        }
        is Page.Tracks -> {
            drawCloudMusicText(active.title, contentLeft(), contentTop(), CloudMusicGui.titleScale, CloudMusicGui.TEXT)
            drawCloudMusicText(active.detail, contentLeft(), contentTop() + 31f, CloudMusicGui.bodyScale, CloudMusicGui.TEXT_DIM)
            drawRoundedRect(contentRight() - 96f, contentTop() - 3f, contentRight(), contentTop() + 28f, 5f, CloudMusicGui.ACCENT)
            drawTriangle(contentRight() - 79f, contentTop() + 6f, contentRight() - 79f, contentTop() + 19f, contentRight() - 67f, contentTop() + 12.5f, CloudMusicGui.BACKGROUND)
            drawCloudMusicText("播放全部", contentRight() - 58f, contentTop() + 7f, CloudMusicGui.smallScale, Color4b.BLACK)
            drawQuad(contentLeft(), listTop() - 10f, contentRight(), listTop() - 9f, CloudMusicGui.BORDER)
            active.tracks.forEachIndexed { index, track -> drawTrack(track, index, mx, my) }
        }
        }
    }
    private fun GuiGraphicsExtractor.drawTrack(track: IMusic, index: Int, mx: Float, my: Float) {
        val rect = trackRect(index); if (rect.bottom < listTop() || rect.top > listBottom()) return
        val playing = MusicCommand.getPlayer().playingMusic?.id == track.id
        if (rect.contains(mx, my)) drawQuad(rect.left, rect.top, rect.right, rect.bottom, CloudMusicGui.HOVER)
        if (playing) drawQuad(rect.left, rect.top, rect.left + 2f, rect.bottom, CloudMusicGui.ACCENT)
        drawCloudMusicText(if (playing) "▶" else "%02d".format(index + 1), rect.left + 14f, rect.top + 12f, CloudMusicGui.smallScale, if (playing) CloudMusicGui.ACCENT else CloudMusicGui.TEXT_FAINT)
        drawCloudMusicText(CloudMusicGui.truncate(track.name, rect.width - 180f), rect.left + 54f, rect.top + 7f, CloudMusicGui.bodyScale, if (playing) CloudMusicGui.ACCENT else CloudMusicGui.TEXT)
        drawCloudMusicText(track.durationToString, rect.right - 12f, rect.top + 12f, CloudMusicGui.smallScale, CloudMusicGui.TEXT_FAINT, horizontalAnchor = HorizontalAnchor.END)
    }
    private fun GuiGraphicsExtractor.drawPlayer(mx: Float, my: Float) {
        drawQuad(0f, playerTop(), windowWidth, windowHeight, CloudMusicGui.PLAYER_BG); drawQuad(0f, playerTop(), windowWidth, playerTop() + 1f, CloudMusicGui.BORDER)
        val player = MusicCommand.getPlayer(); val music = player.playingMusic
        drawRoundedRect(18f, playerTop() + 14f, 52f, playerTop() + 48f, 4f, CloudMusicGui.ACTIVE, outlineColor = CloudMusicGui.BORDER)
        drawCloudMusicText("♪", 35f, playerTop() + 22f, CloudMusicGui.headerScale, CloudMusicGui.ACCENT, horizontalAnchor = HorizontalAnchor.CENTER)
        drawCloudMusicText(CloudMusicGui.truncate(music?.name ?: "未在播放", windowWidth * .22f), 64f, playerTop() + 16f, CloudMusicGui.bodyScale, CloudMusicGui.TEXT)
        drawCloudMusicText(music?.durationToString ?: "--:--", 64f, playerTop() + 36f, CloudMusicGui.smallScale, CloudMusicGui.TEXT_FAINT)
        control(windowWidth / 2f - 72f, playerTop() + 18f, "‹", mx, my); control(windowWidth / 2f - 18f, playerTop() + 13f, if (player.isPlaying) "Ⅱ" else "▶", mx, my, true); control(windowWidth / 2f + 42f, playerTop() + 18f, "›", mx, my)
        val fraction = if (music == null || music.durationSecond == 0) 0f else (player.playingProgress.toFloat() / (music.durationSecond * 1000f)).coerceIn(0f, 1f)
        slider(progressRect(), fraction, CloudMusicGui.ACCENT); drawCloudMusicText(formatTime(player.playingProgress), progressRect().left - 8f, playerTop() + 39f, CloudMusicGui.smallScale, CloudMusicGui.TEXT_FAINT, horizontalAnchor = HorizontalAnchor.END)
        drawCloudMusicText("♬", windowWidth - 184f, playerTop() + 38f, CloudMusicGui.bodyScale, CloudMusicGui.TEXT_DIM); slider(volumeRect(), player.volumePercentage / 100f, CloudMusicGui.TEXT_DIM); iconButton(Rect(windowWidth - 72f, playerTop() + 15f, windowWidth - 34f, playerTop() + 49f), "≡", mx, my)
    }
    private fun GuiGraphicsExtractor.drawQueue(mx: Float, my: Float) { val panel = Rect(windowWidth - 355f, 58f, windowWidth - 14f, playerTop() - 10f); drawRoundedRect(panel.left, panel.top, panel.right, panel.bottom, 6f, CloudMusicGui.SIDEBAR, outlineColor = CloudMusicGui.BORDER); drawCloudMusicText("播放列表 · ${queue.size}", panel.left + 16f, panel.top + 15f, CloudMusicGui.headerScale, CloudMusicGui.TEXT); queue.take(14).forEachIndexed { i, track -> val yy = panel.top + 52f + i * 30f; drawCloudMusicText(CloudMusicGui.truncate(track.name, 220f), panel.left + 16f, yy, CloudMusicGui.smallScale, if (track.id == MusicCommand.getPlayer().playingMusic?.id) CloudMusicGui.ACCENT else CloudMusicGui.TEXT_DIM); drawCloudMusicText(track.durationToString, panel.right - 14f, yy, CloudMusicGui.smallScale, CloudMusicGui.TEXT_FAINT, horizontalAnchor = HorizontalAnchor.END) } }
    private fun handleQueueClick(mx: Float, my: Float): Boolean { val panel = Rect(windowWidth - 355f, 58f, windowWidth - 14f, playerTop() - 10f); queue.indices.firstOrNull { my in panel.top + 45f + it * 30f..panel.top + 75f + it * 30f && mx in panel.left..panel.right }?.let { play(queue, it) }; return true }
    private fun GuiGraphicsExtractor.drawSettings(mx: Float, my: Float) { val panel = Rect(windowWidth - 390f, 58f, windowWidth - 14f, playerTop() - 10f); drawRoundedRect(panel.left, panel.top, panel.right, panel.bottom, 6f, CloudMusicGui.SIDEBAR, outlineColor = CloudMusicGui.BORDER); drawCloudMusicText("RikkaMusic 设置", panel.left + 18f, panel.top + 16f, CloudMusicGui.headerScale, CloudMusicGui.TEXT); settingRow(panel, 0, "循环播放", Configs.PLAY.PLAY_LOOP.booleanValue, mx, my); settingRow(panel, 1, "随机播放", Configs.PLAY.PLAY_AUTO_RANDOM.booleanValue, mx, my); drawCloudMusicText("音质", panel.left + 18f, panel.top + 142f, CloudMusicGui.bodyScale, CloudMusicGui.TEXT_DIM); drawCloudMusicText(Configs.PLAY.PLAY_QUALITY.stringValue, panel.right - 18f, panel.top + 142f, CloudMusicGui.bodyScale, CloudMusicGui.ACCENT, horizontalAnchor = HorizontalAnchor.END); drawCloudMusicText("完整模块设置", panel.left + 18f, panel.bottom - 34f, CloudMusicGui.bodyScale, CloudMusicGui.ACCENT) }
    private fun GuiGraphicsExtractor.settingRow(panel: Rect, index: Int, label: String, enabled: Boolean, mx: Float, my: Float) { val yy = panel.top + 58f + index * 42f; drawCloudMusicText(label, panel.left + 18f, yy + 7f, CloudMusicGui.bodyScale, CloudMusicGui.TEXT_DIM); drawRoundedRect(panel.right - 48f, yy, panel.right - 18f, yy + 20f, 10f, if (enabled) CloudMusicGui.ACCENT else CloudMusicGui.ACTIVE); drawQuad(if (enabled) panel.right - 31f else panel.right - 45f, yy + 4f, if (enabled) panel.right - 22f else panel.right - 36f, yy + 16f, CloudMusicGui.TEXT) }
    private fun handleSettingsClick(mx: Float, my: Float): Boolean { val panel = Rect(windowWidth - 390f, 58f, windowWidth - 14f, playerTop() - 10f); when { my in panel.top + 58f..panel.top + 78f -> Configs.PLAY.PLAY_LOOP.setBooleanValue(!Configs.PLAY.PLAY_LOOP.booleanValue); my in panel.top + 100f..panel.top + 120f -> Configs.PLAY.PLAY_AUTO_RANDOM.setBooleanValue(!Configs.PLAY.PLAY_AUTO_RANDOM.booleanValue); my in panel.bottom - 52f..panel.bottom - 16f -> mc.gui.setScreen(CloudMusicSettingsScreen()) }; return true }
    private fun GuiGraphicsExtractor.iconButton(rect: Rect, icon: String, mx: Float, my: Float) { drawRoundedRect(rect.left, rect.top, rect.right, rect.bottom, 5f, if (rect.contains(mx, my)) CloudMusicGui.HOVER else CloudMusicGui.ACTIVE, outlineColor = CloudMusicGui.BORDER); drawCloudMusicText(icon, rect.left + rect.width / 2f, rect.top + 8f, CloudMusicGui.headerScale, CloudMusicGui.TEXT_DIM, horizontalAnchor = HorizontalAnchor.CENTER) }
    private fun GuiGraphicsExtractor.control(x: Float, y: Float, icon: String, mx: Float, my: Float, accent: Boolean = false) { val r = Rect(x, y, x + if (accent) 36f else 30f, y + if (accent) 36f else 30f); drawRoundedRect(r.left, r.top, r.right, r.bottom, 5f, if (r.contains(mx, my)) CloudMusicGui.HOVER else Color4b.TRANSPARENT, outlineColor = if (accent) CloudMusicGui.ACCENT else CloudMusicGui.BORDER); drawCloudMusicText(icon, r.left + r.width / 2f, r.top + 7f, CloudMusicGui.headerScale, if (accent) CloudMusicGui.ACCENT else CloudMusicGui.TEXT_DIM, horizontalAnchor = HorizontalAnchor.CENTER) }
    private fun GuiGraphicsExtractor.slider(rect: Rect, value: Float, color: Color4b) { drawQuad(rect.left, rect.top, rect.right, rect.bottom, CloudMusicGui.PROGRESS_BG); drawQuad(rect.left, rect.top, rect.left + rect.width * value, rect.bottom, color) }
    private fun formatTime(ms: Long) = "%d:%02d".format(ms / 60000, (ms / 1000) % 60)
    private companion object { const val SIDEBAR = 230f; const val PLAYER_HEIGHT = 78f; const val ROW_HEIGHT = 42f; const val CLOUD_ID = Long.MIN_VALUE }
}
