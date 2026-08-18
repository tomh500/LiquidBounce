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
import fengliu.cloudmusic.music163.IMusic
import fengliu.cloudmusic.music163.data.DjMusic
import fengliu.cloudmusic.music163.data.Music
import fengliu.cloudmusic.music163.data.PlayList
import fengliu.cloudmusic.render.MusicIconTexture
import net.ccbluex.liquidbounce.render.drawQuad
import net.ccbluex.liquidbounce.render.drawRoundedRect
import net.ccbluex.liquidbounce.render.drawTexQuad
import net.ccbluex.liquidbounce.render.drawTriangle
import net.ccbluex.liquidbounce.render.getBounds
import net.ccbluex.liquidbounce.render.withPush
import net.ccbluex.liquidbounce.render.engine.font.HorizontalAnchor
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.utils.render.textureSetup
import net.ccbluex.liquidbounce.utils.text.asPlainText
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.resources.Identifier
import org.lwjgl.glfw.GLFW

/**
 * Merged NetEase Cloud Music GUI, drawn with LiquidBounce's renderer and font.
 */
class CloudMusicScreen : Screen("RikkaMusic".asPlainText()) {

    private var windowLeft = 0f
    private var windowTop = 0f
    private var windowWidth = 960f
    private var windowHeight = 640f
    private var draggingWindow = false
    private var dragOffsetX = 0f
    private var dragOffsetY = 0f

    private data class LibraryData(
        val nickname: String,
        val likedId: Long,
        val likedName: String,
        val likedCover: String,
        val playlists: List<PlayList>,
    )

    private sealed interface ContentView {
        data class Tracks(
            val title: String,
            val subtitle: String?,
            val coverId: Long?,
            val coverUrl: String,
            val tracks: List<IMusic>,
        ) : ContentView

        data class Message(val text: String, val isError: Boolean = false) : ContentView
    }

    private enum class PlayerIcon {
        PREV,
        PLAY,
        PAUSE,
        NEXT,
    }

    private sealed interface SidebarRow {
        val height: Float
    }

    private data class NavRow(
        val label: String,
        val icon: String,
        val id: Int,
        val enabled: Boolean,
    ) : SidebarRow {
        override val height = 34f
    }

    private data class SectionRow(val label: String) : SidebarRow {
        override val height = 26f
    }

    private data class LoginRow(val label: String) : SidebarRow {
        override val height = 34f
    }

    private var library: LibraryData? = null
    private var cloudDiskTracks: List<IMusic> = emptyList()
    private var loggedIn = false
    private var loading = true

    private var sidebarRows = mutableListOf<SidebarRow>()
    private var sidebarScroll = 0f

    private var selectedNavId: Int? = null
    private var content: ContentView = ContentView.Message("加载中…")

    private var searchQuery = ""
    private var searchFocused = false
    private var searchRunning = false

    private var currentQueue: List<IMusic> = emptyList()
    private var scrollOffset = 0f
    private var hoveredTrack = -1

    private var draggingProgress = false
    private var draggingVolume = false
    private var showQueue = false

    private var started = false

    override fun init() {
        super.init()
        updateWindowBounds()
        if (!started) {
            started = true
            loadLibrary()
        }
    }

    private fun updateWindowBounds() {
        // Match the compact ClickGUI-like footprint from the reference: the
        // game remains visible around the application instead of being covered.
        windowWidth = minOf(1320f, width * 0.58f).coerceAtLeast(420f)
        windowHeight = minOf(940f, height * 0.72f).coerceAtLeast(300f)
        val savedX = fengliu.cloudmusic.config.Configs.GUI.WINDOW_X.getIntegerValue()
        val savedY = fengliu.cloudmusic.config.Configs.GUI.WINDOW_Y.getIntegerValue()
        windowLeft = if (fengliu.cloudmusic.config.Configs.GUI.DRAGGABLE_WINDOW.getBooleanValue() && savedX >= 0) savedX.toFloat() else (width - windowWidth) / 2f
        windowTop = if (fengliu.cloudmusic.config.Configs.GUI.DRAGGABLE_WINDOW.getBooleanValue() && savedY >= 0) savedY.toFloat() else (height - windowHeight) / 2f
        windowLeft = windowLeft.coerceIn(0f, (width - windowWidth).coerceAtLeast(0f))
        windowTop = windowTop.coerceIn(0f, (height - windowHeight).coerceAtLeast(0f))
    }

    private fun localX(x: Float) = ((x - windowLeft) / windowWidth * width).coerceIn(0f, width.toFloat())
    private fun localY(y: Float) = ((y - windowTop) / windowHeight * height).coerceIn(0f, height.toFloat())
    private fun insideWindow(x: Float, y: Float) = x in windowLeft..(windowLeft + windowWidth) && y in windowTop..(windowTop + windowHeight)

    // ------------------------------------------------------------------
    // Data loading
    // ------------------------------------------------------------------

    private fun loadLibrary() {
        loading = true
        CloudMusicAsync.run(
            job = {
                val my = MusicCommand.getMy(false)
                val liked = my.likeMusicPlayList()
                val likedMusics = liked.getMusics()
                val created = my.playLists(0, 100).filter {
                    it.creator.get("userId")?.asLong == my.id
                }
                LibraryData(my.name, liked.id, liked.name, liked.cover, created) to likedMusics
            },
            onSuccess = { (data, likedMusics) ->
                library = data
                loggedIn = true
                loading = false
                rebuildSidebar()
                selectedNavId = NAV_LIKED
                content = ContentView.Tracks(
                    title = data.likedName,
                    subtitle = "共 ${likedMusics.size} 首",
                    coverId = data.likedId,
                    coverUrl = data.likedCover,
                    tracks = likedMusics,
                )
                loadCloudDisk()
            },
            onError = {
                loading = false
                loggedIn = false
                library = null
                rebuildSidebar()
                content = ContentView.Message("尚未登录或网络异常，请先登录网易云账号", isError = true)
            },
        )
    }

    private fun loadCloudDisk() {
        CloudMusicAsync.run(
            job = { MusicCommand.getMusic163().cloudMusic() },
            onSuccess = { tracks ->
                cloudDiskTracks = tracks
                rebuildSidebar()
            },
            onError = { cloudDiskTracks = emptyList(); rebuildSidebar() },
        )
    }

    private fun rebuildSidebar() {
        val rows = mutableListOf<SidebarRow>()
        if (loggedIn) {
            rows += NavRow("我喜欢的音乐", "♥", NAV_LIKED, true)
            if (cloudDiskTracks.isNotEmpty()) rows += NavRow("我的音乐云盘", "☁", NAV_CLOUD, true)
            rows += SectionRow("创建的歌单")
            library?.playlists?.forEachIndexed { index, playlist ->
                rows += NavRow(playlist.name, "♪", NAV_PLAYLIST + index, true)
            }
            rows += LoginRow("退出登录")
        } else {
            rows += NavRow("我喜欢的音乐", "♥", NAV_LIKED, false)
            rows += SectionRow("创建的歌单")
            rows += LoginRow("扫码登录")
        }
        sidebarRows = rows
    }

    private fun loadLiked() {
        selectedNavId = NAV_LIKED
        scrollOffset = 0f
        content = ContentView.Message("加载中…")
        CloudMusicAsync.run(
            job = {
                val my = MusicCommand.getMy(false)
                val liked = my.likeMusicPlayList()
                liked.getMusics() to liked
            },
            onSuccess = { (tracks, liked) ->
                library = library?.copy(
                    likedId = liked.id,
                    likedName = liked.name,
                    likedCover = liked.cover,
                )
                content = ContentView.Tracks(
                    title = liked.name,
                    subtitle = "共 ${tracks.size} 首",
                    coverId = liked.id,
                    coverUrl = liked.cover,
                    tracks = tracks,
                )
            },
            onError = {
                content = ContentView.Message("加载失败，请检查网络连接", isError = true)
            },
        )
    }

    private fun loadPlaylist(playlist: PlayList) {
        selectedNavId = NAV_PLAYLIST + (library?.playlists?.indexOfFirst { it.id == playlist.id } ?: -1)
        scrollOffset = 0f
        content = ContentView.Message("加载中…")
        CloudMusicAsync.run(
            job = {
                val full = MusicCommand.getMusic163().playlist(playlist.id)
                full.getMusics() to full
            },
            onSuccess = { (tracks, full) ->
                content = ContentView.Tracks(
                    title = full.name,
                    subtitle = "共 ${full.trackCountText()}",
                    coverId = full.id,
                    coverUrl = full.cover,
                    tracks = tracks,
                )
            },
            onError = {
                content = ContentView.Message("歌单加载失败，请检查网络连接", isError = true)
            },
        )
    }

    private fun runSearch(query: String) {
        searchRunning = true
        scrollOffset = 0f
        content = ContentView.Message("搜索中…")
        CloudMusicAsync.run(
            job = { MusicCommand.searchMusics(query) },
            onSuccess = { tracks ->
                searchRunning = false
                content = ContentView.Tracks(
                    title = "搜索: $query",
                    subtitle = "找到 ${tracks.size} 首歌曲",
                    coverId = null,
                    coverUrl = "",
                    tracks = tracks,
                )
            },
            onError = {
                searchRunning = false
                content = ContentView.Message("搜索失败，请检查网络连接", isError = true)
            },
        )
    }

    private fun playTracks(tracks: List<IMusic>, index: Int) {
        if (tracks.isEmpty()) {
            return
        }
        currentQueue = tracks
        MusicCommand.playMusicsFrom(tracks, index)
    }

    // ------------------------------------------------------------------
    // Layout helpers
    // ------------------------------------------------------------------

    private fun contentX() = SIDEBAR_WIDTH + 14f
    private fun contentRight() = width - 14f
    private fun contentWidth() = contentRight() - contentX()
    private fun contentTop() = 64f
    private fun contentBottom() = height - PLAYER_HEIGHT - 10f
    private fun viewportHeight() = (contentBottom() - contentTop()).coerceAtLeast(0f)

    private fun tracksHeaderHeight(): Float =
        if ((content as? ContentView.Tracks)?.coverUrl?.isNotEmpty() == true) 112f else 0f

    private fun maxScroll(): Float {
        val view = content as? ContentView.Tracks ?: return 0f
        val header = tracksHeaderHeight()
        val listHeight = view.tracks.size * ROW_HEIGHT
        return (header + listHeight - viewportHeight()).coerceAtLeast(0f)
    }

    private fun trackRowRect(index: Int): Quad? {
        val view = content as? ContentView.Tracks ?: return null
        val header = tracksHeaderHeight()
        val y = contentTop() + header + index * ROW_HEIGHT - scrollOffset
        return Quad(contentX() + 4f, y, contentRight() - 4f, y + ROW_HEIGHT - 4f)
    }

    private data class Quad(val x1: Float, val y1: Float, val x2: Float, val y2: Float) {
        fun contains(x: Float, y: Float): Boolean =
            x in x1..x2 && y in y1..y2

        fun width() = x2 - x1

        fun height() = y2 - y1
    }

    private fun trackIndexAt(x: Float, y: Float): Int {
        val view = content as? ContentView.Tracks ?: return -1
        for (index in view.tracks.indices) {
            val rect = trackRowRect(index) ?: continue
            if (rect.contains(x, y)) {
                return index
            }
        }
        return -1
    }

    // ------------------------------------------------------------------
    // Input
    // ------------------------------------------------------------------

    override fun mouseClicked(click: MouseButtonEvent, doubled: Boolean): Boolean {
        val rawX = click.x().toFloat()
        val rawY = click.y().toFloat()
        if (!insideWindow(rawX, rawY)) return true
        if (click.button() == 0 && fengliu.cloudmusic.config.Configs.GUI.DRAGGABLE_WINDOW.getBooleanValue() && rawY <= windowTop + 30f) {
            draggingWindow = true
            dragOffsetX = rawX - windowLeft
            dragOffsetY = rawY - windowTop
            return true
        }
        val mouseX = localX(rawX)
        val mouseY = localY(rawY)

        if (gearButtonRect().contains(mouseX, mouseY)) {
            mc.gui.setScreen(CloudMusicSettingsScreen())
            return true
        }
        if (queueButtonRect().contains(mouseX, mouseY)) {
            showQueue = !showQueue
            return true
        }

        if (click.button() != 0) {
            return super.mouseClicked(click, doubled)
        }

        if (mouseX <= SIDEBAR_WIDTH) {
            handleSidebarClick(mouseX, mouseY)
            return true
        }

        if (searchBoxRect().contains(mouseX, mouseY)) {
            searchFocused = true
            return true
        }
        searchFocused = false

        val view = content
        if (view is ContentView.Tracks) {
            val index = trackIndexAt(mouseX, mouseY)
            if (index >= 0) {
                playTracks(view.tracks, index)
                return true
            }
        }

        if (view is ContentView.Message && !view.isError && !loggedIn && !loading) {
            if (loginButtonRect().contains(mouseX, mouseY)) {
                mc.gui.setScreen(CloudMusicLoginScreen())
                return true
            }
        }

        handlePlayerBarClick(mouseX, mouseY)
        return true
    }

    private fun handleSidebarClick(mouseX: Float, mouseY: Float) {
        var y = SIDEBAR_HEADER_HEIGHT - sidebarScroll
        for (row in sidebarRows) {
            if (mouseY in y..(y + row.height)) {
                when (row) {
                    is NavRow -> {
                        when {
                            row.id == NAV_LIKED -> if (row.enabled) loadLiked()
                            row.id == NAV_CLOUD -> {
                                selectedNavId = NAV_CLOUD
                                content = ContentView.Tracks("我的音乐云盘", "共 ${cloudDiskTracks.size} 首", null, "", cloudDiskTracks)
                            }
                            row.id >= NAV_PLAYLIST -> {
                                val index = row.id - NAV_PLAYLIST
                                library?.playlists?.getOrNull(index)?.let { loadPlaylist(it) }
                            }
                        }
                    }
                    is LoginRow -> {
                        if (loggedIn) {
                            logout()
                        } else {
                            mc.gui.setScreen(CloudMusicLoginScreen())
                        }
                    }
                    is SectionRow -> Unit
                }
                return
            }
            y += row.height
        }

        if (settingsButtonRect().contains(mouseX, mouseY)) {
            mc.gui.setScreen(CloudMusicSettingsScreen())
        }
    }

    private fun logout() {
        MusicCommand.setCookie("")
        library = null
        loggedIn = false
        rebuildSidebar()
        selectedNavId = null
        content = ContentView.Message("已退出登录")
    }

    private fun handlePlayerBarClick(mouseX: Float, mouseY: Float) {
        val player = MusicCommand.getPlayer()

        if (prevButtonRect().contains(mouseX, mouseY)) {
            player.prev()
            return
        }
        if (playButtonRect().contains(mouseX, mouseY)) {
            player.switchPlay()
            return
        }
        if (nextButtonRect().contains(mouseX, mouseY)) {
            player.next()
            return
        }

        progressBarRect()?.let {
            if (it.contains(mouseX, mouseY)) {
                draggingProgress = true
                seekTo(mouseX)
                return
            }
        }

        volumeBarRect()?.let {
            if (it.contains(mouseX, mouseY)) {
                draggingVolume = true
                setVolume(mouseX)
                return
            }
        }
    }

    override fun mouseDragged(click: MouseButtonEvent, offsetX: Double, offsetY: Double): Boolean {
        if (draggingWindow) {
            windowLeft = (click.x().toFloat() - dragOffsetX).coerceIn(0f, (width - windowWidth).coerceAtLeast(0f))
            windowTop = (click.y().toFloat() - dragOffsetY).coerceIn(0f, (height - windowHeight).coerceAtLeast(0f))
            fengliu.cloudmusic.config.Configs.GUI.WINDOW_X.setIntegerValue(windowLeft.toInt())
            fengliu.cloudmusic.config.Configs.GUI.WINDOW_Y.setIntegerValue(windowTop.toInt())
            return true
        }
        if (draggingProgress) {
            seekTo(localX(click.x().toFloat()))
            return true
        }
        if (draggingVolume) {
            setVolume(localX(click.x().toFloat()))
            return true
        }
        return super.mouseDragged(click, offsetX, offsetY)
    }

    override fun mouseReleased(click: MouseButtonEvent): Boolean {
        draggingWindow = false
        draggingProgress = false
        draggingVolume = false
        return super.mouseReleased(click)
    }

    override fun mouseScrolled(
        mouseX: Double,
        mouseY: Double,
        horizontalAmount: Double,
        verticalAmount: Double,
    ): Boolean {
        if (!insideWindow(mouseX.toFloat(), mouseY.toFloat())) return true
        val localMouseX = localX(mouseX.toFloat())
        val localMouseY = localY(mouseY.toFloat())
        val scroll = verticalAmount.toFloat()
        if (localMouseX <= SIDEBAR_WIDTH.toDouble()) {
            sidebarScroll = (sidebarScroll - scroll * 14f).coerceIn(0f, sidebarMaxScroll())
            return true
        }
        if (localMouseY in contentTop().toDouble()..contentBottom().toDouble()) {
            scrollOffset = (scrollOffset - scroll * ROW_HEIGHT * 0.6f).coerceIn(0f, maxScroll())
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
    }

    override fun keyPressed(input: KeyEvent): Boolean {
        if (searchFocused) {
            when (input.key()) {
                GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                    val query = searchQuery.trim()
                    if (query.isNotEmpty()) {
                        runSearch(query)
                    }
                }
                GLFW.GLFW_KEY_BACKSPACE -> {
                    if (searchQuery.isNotEmpty()) {
                        searchQuery = searchQuery.dropLast(1)
                    }
                }
                GLFW.GLFW_KEY_ESCAPE -> searchFocused = false
            }
            return true
        }
        return super.keyPressed(input)
    }

    override fun charTyped(event: CharacterEvent): Boolean {
        if (searchFocused) {
            searchQuery += event.codepointAsString()
            return true
        }
        return super.charTyped(event)
    }

    // ------------------------------------------------------------------
    // Player control helpers
    // ------------------------------------------------------------------

    private fun seekTo(mouseX: Float) {
        val player = MusicCommand.getPlayer()
        val music = player.getPlayingMusic() ?: return
        val rect = progressBarRect() ?: return
        val fraction = ((mouseX - rect.x1) / rect.width()).coerceIn(0f, 1f)
        player.seek((music.getDurationSecond() * 1000L * fraction).toLong())
    }

    private fun setVolume(mouseX: Float) {
        val rect = volumeBarRect() ?: return
        val fraction = ((mouseX - rect.x1) / rect.width()).coerceIn(0f, 1f)
        MusicCommand.getPlayer().volumeSet((fraction * 100).toInt())
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    override fun extractRenderState(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        context.drawRoundedRect(windowLeft, windowTop, windowLeft + windowWidth, windowTop + windowHeight, 8f, CloudMusicGui.BACKGROUND, outlineColor = CloudMusicGui.BORDER)
        context.pose().pushMatrix()
        context.pose().translate(windowLeft, windowTop)
        context.pose().scale(windowWidth / width.toFloat(), windowHeight / height.toFloat())
        context.drawQuad(0f, 0f, width.toFloat(), height.toFloat(), CloudMusicGui.BACKGROUND)
        context.drawSidebar(localX(mouseX.toFloat()), localY(mouseY.toFloat()))
        context.drawContent(localX(mouseX.toFloat()), localY(mouseY.toFloat()))
        context.drawPlayerBar(localX(mouseX.toFloat()), localY(mouseY.toFloat()))
        if (showQueue) context.drawQueue()
        context.pose().popMatrix()
    }

    override fun extractTransparentBackground(graphics: GuiGraphicsExtractor) { }
    override fun isPauseScreen() = false

    private fun GuiGraphicsExtractor.drawSidebar(mouseX: Float, mouseY: Float) {
        drawQuad(0f, 0f, SIDEBAR_WIDTH, height.toFloat(), CloudMusicGui.SIDEBAR)

        drawCloudMusicText(
            "RikkaMusic", x = 16f, y = 14f,
            scale = CloudMusicGui.titleScale, color = CloudMusicGui.TEXT,
        )
        drawCloudMusicText(
            "网易云音乐", x = 18f, y = 40f,
            scale = CloudMusicGui.smallScale, color = CloudMusicGui.TEXT_FAINT,
        )
        drawQuad(14f, 64f, SIDEBAR_WIDTH - 14f, 65f, CloudMusicGui.BORDER)

        var y = SIDEBAR_HEADER_HEIGHT - sidebarScroll
        for (row in sidebarRows) {
            val rowBottom = y + row.height
            if (rowBottom < 0f || y > height - BOTTOM_SECTION_HEIGHT) {
                y = rowBottom
                continue
            }

            when (row) {
                is SectionRow -> {
                    drawCloudMusicText(
                        row.label, x = 16f, y = y + 5f,
                        scale = CloudMusicGui.smallScale, color = CloudMusicGui.TEXT_FAINT,
                    )
                }
                is NavRow -> {
                    val hovered = mouseX in 0f..SIDEBAR_WIDTH && mouseY in y..rowBottom
                    val selected = row.id == selectedNavId
                    if (selected) {
                        drawQuad(6f, y, SIDEBAR_WIDTH, rowBottom, CloudMusicGui.ACCENT_SUBTLE)
                        drawQuad(6f, y + 5f, 8f, rowBottom - 5f, CloudMusicGui.ACCENT)
                    } else if (hovered) {
                        drawQuad(6f, y, SIDEBAR_WIDTH - 6f, rowBottom, CloudMusicGui.HOVER)
                    }
                    val color = if (row.enabled) CloudMusicGui.TEXT else CloudMusicGui.TEXT_FAINT
                    drawCloudMusicText(
                        row.icon, x = 18f, y = y + 6f,
                        scale = CloudMusicGui.bodyScale, color = CloudMusicGui.ACCENT,
                    )
                    drawCloudMusicText(
                        CloudMusicGui.truncate(row.label, SIDEBAR_WIDTH - 52f),
                        x = 42f, y = y + 7f,
                        scale = CloudMusicGui.bodyScale, color = color,
                    )
                }
                is LoginRow -> {
                    val hovered = mouseX in 0f..SIDEBAR_WIDTH && mouseY in y..rowBottom
                    if (hovered) {
                        drawQuad(6f, y, SIDEBAR_WIDTH - 6f, rowBottom, CloudMusicGui.HOVER)
                    }
                    drawCloudMusicText(
                        row.label, x = 18f, y = y + 7f,
                        scale = CloudMusicGui.bodyScale,
                        color = if (loggedIn) CloudMusicGui.TEXT_FAINT else CloudMusicGui.ACCENT,
                    )
                }
            }
            y = rowBottom
        }

        library?.let {
            drawCloudMusicText(
                it.nickname, x = 16f, y = height - 58f,
                scale = CloudMusicGui.bodyScale, color = CloudMusicGui.TEXT_DIM,
            )
        }

        drawSettingsButton(mouseX, mouseY)
    }

    private fun GuiGraphicsExtractor.drawSettingsButton(mouseX: Float, mouseY: Float) {
        val rect = settingsButtonRect()
        val hovered = rect.contains(mouseX, mouseY)
        drawRoundedRect(
            rect.x1, rect.y1, rect.x2, rect.y2, 6f,
            fillColor = if (hovered) CloudMusicGui.HOVER else CloudMusicGui.ACTIVE,
            outlineColor = CloudMusicGui.BORDER,
        )
        drawCloudMusicText(
            "设置", x = rect.x1 + 12f, y = rect.y1 + 11f,
            scale = CloudMusicGui.bodyScale,
            color = if (hovered) CloudMusicGui.TEXT else CloudMusicGui.TEXT_DIM,
        )
    }

    private fun GuiGraphicsExtractor.drawContent(mouseX: Float, mouseY: Float) {
        val gear = gearButtonRect()
        drawRoundedRect(gear.x1, gear.y1, gear.x2, gear.y2, 6f, if (gear.contains(mouseX, mouseY)) CloudMusicGui.HOVER else CloudMusicGui.ACTIVE, outlineColor = CloudMusicGui.BORDER)
        drawCloudMusicText("G", gear.x1 + gear.width() / 2f, gear.y1 + 9f, scale = CloudMusicGui.headerScale, color = CloudMusicGui.TEXT_DIM, horizontalAnchor = HorizontalAnchor.CENTER)
        drawSearchBar(mouseX, mouseY)

        when (val view = content) {
            is ContentView.Message -> drawMessage(view, mouseX, mouseY)
            is ContentView.Tracks -> drawTracks(view, mouseX, mouseY)
        }
    }

    private fun GuiGraphicsExtractor.drawSearchBar(mouseX: Float, mouseY: Float) {
        val rect = searchBoxRect()
        val hovered = rect.contains(mouseX, mouseY)
        drawRoundedRect(
            rect.x1, rect.y1, rect.x2, rect.y2, 8f,
            fillColor = if (searchFocused || hovered) CloudMusicGui.ACTIVE else CloudMusicGui.HOVER,
            outlineColor = if (searchFocused) CloudMusicGui.ACCENT else CloudMusicGui.BORDER,
            outlineWidth = 1.5f,
        )
        val textX = rect.x1 + 16f
        if (searchQuery.isEmpty() && !searchFocused) {
            drawCloudMusicText(
                "搜索歌曲", x = textX, y = rect.y1 + 9f,
                scale = CloudMusicGui.bodyScale, color = CloudMusicGui.TEXT_FAINT,
            )
        } else {
            val display = CloudMusicGui.truncate(searchQuery, rect.x2 - textX - 12f)
            drawCloudMusicText(
                display, x = textX, y = rect.y1 + 9f,
                scale = CloudMusicGui.bodyScale, color = CloudMusicGui.TEXT,
            )
            if (searchFocused && (System.currentTimeMillis() / 500) % 2 == 0L) {
                val cursorX = textX + CloudMusicGui.textWidth(display)
                drawQuad(cursorX + 2f, rect.y1 + 10f, cursorX + 3f, rect.y2 - 10f, CloudMusicGui.TEXT)
            }
        }
    }

    private fun GuiGraphicsExtractor.drawMessage(view: ContentView.Message, mouseX: Float, mouseY: Float) {
        val color = if (view.isError) CloudMusicGui.TEXT_DIM else CloudMusicGui.TEXT_FAINT
        drawCloudMusicText(
            view.text, x = contentX(), y = contentTop() + 40f,
            scale = CloudMusicGui.bodyScale, color = color,
        )
        if (!loggedIn && !loading) {
            val button = loginButtonRect()
            val hovered = button.contains(mouseX, mouseY)
            drawRoundedRect(
                button.x1, button.y1, button.x2, button.y2, 6f,
                fillColor = if (hovered) CloudMusicGui.ACCENT_HOVER else CloudMusicGui.ACCENT,
            )
            drawCloudMusicText(
                "登录", x = button.x1 + 26f, y = button.y1 + 9f,
                scale = CloudMusicGui.bodyScale, color = Color4b.BLACK,
            )
        }
    }

    private fun GuiGraphicsExtractor.drawTracks(view: ContentView.Tracks, mouseX: Float, mouseY: Float) {
        val header = tracksHeaderHeight()

        if (header > 0f) {
            drawTrackHeader(view)
        }

        scissorStack.withPush(getBounds(contentX(), contentTop() + header, contentRight(), contentBottom())) {
            for (index in view.tracks.indices) {
                val rect = trackRowRect(index) ?: continue
                if (rect.y2 < contentTop() || rect.y1 > contentBottom()) {
                    continue
                }

                val track = view.tracks[index]
                val playing = MusicCommand.getPlayer().getPlayingMusic()?.getId() == track.getId()
                val hovered = rect.contains(mouseX, mouseY)
                if (hovered) {
                    hoveredTrack = index
                }

                if (hovered) {
                    drawQuad(rect.x1, rect.y1, rect.x2, rect.y2, CloudMusicGui.HOVER)
                }
                if (playing) {
                    drawQuad(rect.x1, rect.y1, rect.x1 + 2f, rect.y2, CloudMusicGui.ACCENT)
                }

                val coverId = CloudMusicCoverCache.get(track.getId())
                if (coverId == null) {
                    CloudMusicCoverCache.load(track)
                }
                drawCover(coverId, rect.x1 + 6f, rect.y1 + 4f, ROW_HEIGHT - 8f)

                val indexText = if (playing) "♪" else (index + 1).toString()
                drawCloudMusicText(
                    indexText, x = rect.x1 + 48f, y = rect.y1 + 12f,
                    scale = CloudMusicGui.smallScale,
                    color = if (playing) CloudMusicGui.ACCENT else CloudMusicGui.TEXT_FAINT,
                    horizontalAnchor = HorizontalAnchor.CENTER,
                )

                drawCloudMusicText(
                    CloudMusicGui.truncate(track.getDisplayName(), rect.width() - 110f),
                    x = rect.x1 + 62f, y = rect.y1 + 6f,
                    scale = CloudMusicGui.bodyScale,
                    color = if (playing) CloudMusicGui.ACCENT else CloudMusicGui.TEXT,
                )
                drawCloudMusicText(
                    CloudMusicGui.truncate(track.getSubtitle(), rect.width() - 110f),
                    x = rect.x1 + 62f, y = rect.y1 + 24f,
                    scale = CloudMusicGui.smallScale, color = CloudMusicGui.TEXT_FAINT,
                )

                drawCloudMusicText(
                    track.getDurationToString(), x = rect.x2 - 6f, y = rect.y1 + 12f,
                    scale = CloudMusicGui.smallScale, color = CloudMusicGui.TEXT_FAINT,
                    horizontalAnchor = HorizontalAnchor.END,
                )
            }
        }
    }

    private fun GuiGraphicsExtractor.drawTrackHeader(view: ContentView.Tracks) {
        val coverSize = 88f
        val coverId = view.coverId ?: -1L
        drawCover(CloudMusicCoverCache.get(coverId), contentX(), contentTop() + 4f, coverSize)
        if (view.coverUrl.isNotEmpty() && CloudMusicCoverCache.get(coverId) == null) {
            CloudMusicCoverCache.load(coverId, view.coverUrl)
        }

        drawCloudMusicText(
            CloudMusicGui.truncate(view.title, contentWidth() - coverSize - 30f),
            x = contentX() + coverSize + 18f, y = contentTop() + 16f,
            scale = CloudMusicGui.titleScale, color = CloudMusicGui.TEXT,
        )
        view.subtitle?.let {
            drawCloudMusicText(
                it, x = contentX() + coverSize + 18f, y = contentTop() + 52f,
                scale = CloudMusicGui.bodyScale, color = CloudMusicGui.TEXT_DIM,
            )
        }
        drawQuad(contentX(), contentTop() + 102f, contentRight(), contentTop() + 103f, CloudMusicGui.BORDER)
    }

    private fun GuiGraphicsExtractor.drawCover(id: Identifier?, x: Float, y: Float, size: Float) {
        drawRoundedRect(x, y, x + size, y + size, 6f, CloudMusicGui.ACTIVE, outlineColor = CloudMusicGui.BORDER)
        val texture = id?.let { mc.textureManager.getTexture(it) }
        if (texture != null) {
            drawTexQuad(texture.textureSetup, x, y, x + size, y + size)
        } else {
            drawCloudMusicText(
                "♪", x = x + size / 2f, y = y + size / 2f - 12f,
                scale = CloudMusicGui.headerScale, color = CloudMusicGui.TEXT_FAINT,
                horizontalAnchor = HorizontalAnchor.CENTER,
            )
        }
    }

    private fun GuiGraphicsExtractor.drawPlayerBar(mouseX: Float, mouseY: Float) {
        val y0 = height - PLAYER_HEIGHT
        drawQuad(0f, y0, width.toFloat(), height.toFloat(), CloudMusicGui.PLAYER_BG)
        drawQuad(0f, y0, width.toFloat(), y0 + 1f, CloudMusicGui.BORDER)

        val player = MusicCommand.getPlayer()
        val music = player.getPlayingMusic()

        if (music != null && MusicIconTexture.canUseIcon()) {
            drawCover(MusicIconTexture.MUSIC_ICON_ID, 14f, y0 + 9f, 44f)
        } else {
            drawCover(null, 14f, y0 + 9f, 44f)
        }

        val textX = 68f
        if (music != null) {
            drawCloudMusicText(
                CloudMusicGui.truncate(music.getDisplayName(), width * 0.22f),
                x = textX, y = y0 + 10f,
                scale = CloudMusicGui.bodyScale, color = CloudMusicGui.TEXT,
            )
            drawCloudMusicText(
                CloudMusicGui.truncate(music.getSubtitle(), width * 0.22f),
                x = textX, y = y0 + 29f,
                scale = CloudMusicGui.smallScale, color = CloudMusicGui.TEXT_FAINT,
            )
        } else {
            drawCloudMusicText(
                "未在播放", x = textX, y = y0 + 20f,
                scale = CloudMusicGui.bodyScale, color = CloudMusicGui.TEXT_FAINT,
            )
        }

        drawPlayerControlButton(prevButtonRect(), PlayerIcon.PREV, mouseX, mouseY)
        drawPlayerControlButton(
            playButtonRect(),
            if (player.isPlaying()) PlayerIcon.PAUSE else PlayerIcon.PLAY,
            mouseX, mouseY,
            accent = true,
        )
        drawPlayerControlButton(nextButtonRect(), PlayerIcon.NEXT, mouseX, mouseY)
        val queueButton = queueButtonRect()
        drawRoundedRect(queueButton.x1, queueButton.y1, queueButton.x2, queueButton.y2, 5f, if (showQueue) CloudMusicGui.ACCENT_SUBTLE else Color4b.TRANSPARENT, outlineColor = CloudMusicGui.BORDER)
        drawCloudMusicText("≡", queueButton.x1 + queueButton.width() / 2f, queueButton.y1 + 7f, scale = CloudMusicGui.headerScale, color = CloudMusicGui.TEXT_DIM, horizontalAnchor = HorizontalAnchor.CENTER)

        val progressRect = progressBarRect()
        if (progressRect != null) {
            val progress = player.getPlayingProgress()
            val duration = music?.getDurationSecond()?.times(1000L)?.coerceAtLeast(1L) ?: 1L
            val fraction = (progress.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
            drawQuad(progressRect.x1, progressRect.y1, progressRect.x2, progressRect.y2, CloudMusicGui.PROGRESS_BG)
            drawQuad(
                progressRect.x1, progressRect.y1,
                progressRect.x1 + progressRect.width() * fraction,
                progressRect.y2, CloudMusicGui.ACCENT,
            )
            drawCloudMusicText(
                player.getPlayingProgressToString(),
                x = progressRect.x1 - 4f, y = progressRect.y1 - 14f,
                scale = CloudMusicGui.smallScale, color = CloudMusicGui.TEXT_FAINT,
                horizontalAnchor = HorizontalAnchor.END,
            )
            drawCloudMusicText(
                music?.getDurationToString() ?: "0:00",
                x = progressRect.x2 + 4f, y = progressRect.y1 - 14f,
                scale = CloudMusicGui.smallScale, color = CloudMusicGui.TEXT_FAINT,
            )
        }

        volumeBarRect()?.let { volumeRect ->
            drawCloudMusicText(
                "音量", x = volumeRect.x1, y = volumeRect.y1 - 14f,
                scale = CloudMusicGui.smallScale, color = CloudMusicGui.TEXT_FAINT,
            )
            val fraction = player.getVolumePercentage() / 100f
            drawQuad(volumeRect.x1, volumeRect.y1, volumeRect.x2, volumeRect.y2, CloudMusicGui.PROGRESS_BG)
            drawQuad(
                volumeRect.x1, volumeRect.y1,
                volumeRect.x1 + volumeRect.width() * fraction,
                volumeRect.y2, CloudMusicGui.TEXT_DIM,
            )
            drawCloudMusicText(
                "${player.getVolumePercentage()}%",
                x = volumeRect.x2 + 4f, y = volumeRect.y1 - 14f,
                scale = CloudMusicGui.smallScale, color = CloudMusicGui.TEXT_DIM,
            )
        }
    }

    private fun GuiGraphicsExtractor.drawQueue() {
        val left = width - 330f
        val top = 58f
        drawRoundedRect(left, top, width - 12f, height - PLAYER_HEIGHT - 12f, 8f, CloudMusicGui.SIDEBAR, outlineColor = CloudMusicGui.BORDER)
        drawCloudMusicText("播放列表", left + 16f, top + 14f, scale = CloudMusicGui.headerScale, color = CloudMusicGui.TEXT)
        val current = MusicCommand.getPlayer().getPlayingMusic()?.getId()
        currentQueue.take(12).forEachIndexed { index, track ->
            val y = top + 52f + index * 32f
            val selected = track.getId() == current
            if (selected) drawQuad(left + 8f, y - 4f, width - 20f, y + 24f, CloudMusicGui.ACCENT_SUBTLE)
            drawCloudMusicText(CloudMusicGui.truncate(track.getDisplayName(), 230f), left + 16f, y, scale = CloudMusicGui.smallScale, color = if (selected) CloudMusicGui.ACCENT else CloudMusicGui.TEXT_DIM)
            drawCloudMusicText(track.getDurationToString(), width - 22f, y, scale = CloudMusicGui.smallScale, color = CloudMusicGui.TEXT_FAINT, horizontalAnchor = HorizontalAnchor.END)
        }
    }

    private fun GuiGraphicsExtractor.drawPlayerControlButton(
        rect: Quad,
        icon: PlayerIcon,
        mouseX: Float,
        mouseY: Float,
        accent: Boolean = false,
    ) {
        val hovered = rect.contains(mouseX, mouseY)
        drawRoundedRect(
            rect.x1, rect.y1, rect.x2, rect.y2, 6f,
            fillColor = if (hovered) CloudMusicGui.HOVER else Color4b.TRANSPARENT,
            outlineColor = if (accent) CloudMusicGui.ACCENT else CloudMusicGui.BORDER,
            outlineWidth = if (accent) 1.5f else 1f,
        )

        val color = if (accent) CloudMusicGui.ACCENT else CloudMusicGui.TEXT_DIM
        val centerX = rect.x1 + rect.width() / 2f
        val centerY = rect.y1 + rect.height() / 2f
        val size = rect.height() * 0.3f
        when (icon) {
            PlayerIcon.PLAY -> drawTriangle(
                centerX - size * 0.6f, centerY - size,
                centerX + size * 0.9f, centerY,
                centerX - size * 0.6f, centerY + size,
                fillColor = color,
            )
            PlayerIcon.PAUSE -> {
                drawQuad(centerX - size * 0.8f, centerY - size, centerX - size * 0.15f, centerY + size, color)
                drawQuad(centerX + size * 0.15f, centerY - size, centerX + size * 0.8f, centerY + size, color)
            }
            PlayerIcon.PREV -> {
                drawQuad(centerX + size * 0.45f, centerY - size, centerX + size * 0.9f, centerY + size, color)
                drawTriangle(
                    centerX + size * 0.45f, centerY - size,
                    centerX - size * 0.7f, centerY,
                    centerX + size * 0.45f, centerY + size,
                    fillColor = color,
                )
            }
            PlayerIcon.NEXT -> {
                drawQuad(centerX - size * 0.9f, centerY - size, centerX - size * 0.45f, centerY + size, color)
                drawTriangle(
                    centerX - size * 0.45f, centerY - size,
                    centerX + size * 0.7f, centerY,
                    centerX - size * 0.45f, centerY + size,
                    fillColor = color,
                )
            }
        }
    }

    // ------------------------------------------------------------------
    // Rect helpers
    // ------------------------------------------------------------------

    private fun searchBoxRect() = Quad(contentX(), 12f, contentRight(), 46f)

    private fun loginButtonRect() = Quad(contentX(), contentTop() + 64f, contentX() + 130f, contentTop() + 96f)

    private fun settingsButtonRect(): Quad =
        Quad(10f, height - 50f, SIDEBAR_WIDTH - 10f, height - 14f)

    private fun gearButtonRect(): Quad = Quad(width - 58f, 12f, width - 18f, 48f)

    private fun prevButtonRect(): Quad {
        val cy = height - PLAYER_HEIGHT / 2f
        return Quad(width / 2f - 74f, cy - 14f, width / 2f - 46f, cy + 14f)
    }

    private fun playButtonRect(): Quad {
        val cy = height - PLAYER_HEIGHT / 2f
        return Quad(width / 2f - 16f, cy - 16f, width / 2f + 16f, cy + 16f)
    }

    private fun nextButtonRect(): Quad {
        val cy = height - PLAYER_HEIGHT / 2f
        return Quad(width / 2f + 46f, cy - 14f, width / 2f + 74f, cy + 14f)
    }

    private fun queueButtonRect(): Quad = Quad(width - 112f, height - PLAYER_HEIGHT + 18f, width - 78f, height - 18f)

    private fun progressBarRect(): Quad? {
        if (width < 640) {
            return null
        }
        val y = height - 16f
        return Quad(width * 0.34f, y, width * 0.68f, y + 4f)
    }

    private fun volumeBarRect(): Quad? {
        if (width < 900) {
            return null
        }
        val y = height - 16f
        return Quad(width - 190f, y, width - 120f, y + 4f)
    }

    private fun sidebarMaxScroll(): Float {
        val rowsHeight = sidebarRows.sumOf { it.height.toDouble() }.toFloat()
        val available = height - SIDEBAR_HEADER_HEIGHT - BOTTOM_SECTION_HEIGHT
        return (rowsHeight - available).coerceAtLeast(0f)
    }

    // ------------------------------------------------------------------
    // Display helpers
    // ------------------------------------------------------------------

    private fun IMusic.getDisplayName(): String = name

    private fun IMusic.getSubtitle(): String = when (this) {
        is Music -> {
            val albumName = album.get("name")
                ?.takeUnless { it.isJsonNull }
                ?.takeIf { it.isJsonPrimitive }
                ?.asString
                .orEmpty()
            val artists = artists
                .filter { it.isJsonObject && it.asJsonObject.get("name")?.let { value -> !value.isJsonNull } == true }
                .joinToString("/") { it.asJsonObject.get("name").asString }
            if (albumName.isEmpty()) artists else "$artists - $albumName"
        }
        is DjMusic -> dj.get("nickname")?.asString ?: ""
        else -> ""
    }

    private fun PlayList.trackCountText(): String = "$count 首"

    private companion object {
        const val SIDEBAR_WIDTH = 210f
        const val PLAYER_HEIGHT = 62f
        const val ROW_HEIGHT = 46f
        const val SIDEBAR_HEADER_HEIGHT = 70f
        const val BOTTOM_SECTION_HEIGHT = 96f
        const val NAV_LIKED = 0
        const val NAV_CLOUD = -1
        const val NAV_PLAYLIST = 1000
    }
}
