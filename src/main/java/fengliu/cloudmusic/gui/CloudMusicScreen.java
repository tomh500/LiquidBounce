package fengliu.cloudmusic.gui;

import fengliu.cloudmusic.command.MusicCommand;
import fengliu.cloudmusic.config.Configs;
import fengliu.cloudmusic.music163.IMusic;
import fengliu.cloudmusic.music163.data.My;
import fengliu.cloudmusic.music163.data.Music;
import fengliu.cloudmusic.music163.data.PlayList;
import kotlin.Unit;
import net.ccbluex.liquidbounce.render.Render2DKt;
import net.ccbluex.liquidbounce.utils.render.RenderExtensionsKt;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.ccbluex.liquidbounce.render.engine.font.HorizontalAnchor;
import net.ccbluex.liquidbounce.render.engine.font.VerticalAnchor;
import net.ccbluex.liquidbounce.render.engine.type.Color4b;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static fengliu.cloudmusic.gui.CloudMusicGuiKt.drawCloudMusicText;

/** RikkaMusic's standalone in-game application window. */
public final class CloudMusicScreen extends Screen {
    private enum Palette { LIQUIDBOUNCE, LIGHT }
    private enum View { LIBRARY, LYRICS, STATUS }
    private record Box(float x, float y, float width, float height) {
        boolean contains(float px, float py) { return px >= x && py >= y && px <= x + width && py <= y + height; }
    }

    private static final float DESIGN_WIDTH = 440f;
    private static final float DESIGN_HEIGHT = 313f;
    private static final float WIDTH_RATIO = 1320f / 2560f;
    private static final float HEIGHT_RATIO = 940f / 1440f;
    private float windowX, windowY, windowWidth = DESIGN_WIDTH, windowHeight = DESIGN_HEIGHT, uiScale = 1f;
    private final float sidebarWidth = 116;
    private final float headerHeight = 30, playerHeight = 48, rowHeight = 19;
    private View view = View.STATUS;
    private Palette palette = Palette.LIQUIDBOUNCE;
    private String status = "正在连接网易云音乐…", searchText = "", pageTitle = "";
    private boolean statusError, searchFocused, settingsOpen, queueOpen;
    private boolean showTranslation;
    private boolean draggingWindow, draggingProgress, draggingVolume;
    private float dragOffsetX, dragOffsetY, songScroll, playlistScroll, lyricScroll;
    private int pageIndex;
    private boolean cloudLoading;
    private PlayList likedPlaylist;
    private long pageCoverId = Long.MIN_VALUE;
    private String pageCoverUrl = "";
    private List<PlayList> playlists = List.of();
    private List<IMusic> songs = List.of(), cloudSongs = List.of(), queue = List.of();
    private final Set<Long> likedSongIds = new HashSet<>();
    private Long selectedPlaylist;

    public CloudMusicScreen() { super(Component.literal("RikkaMusic")); }

    @Override
    protected void init() {
        uiScale = Math.min(width * WIDTH_RATIO / DESIGN_WIDTH, height * HEIGHT_RATIO / DESIGN_HEIGHT);
        uiScale = Math.min(uiScale, Math.min((width - 12f) / DESIGN_WIDTH, (height - 12f) / DESIGN_HEIGHT));
        windowX = Math.max(0, (width - windowWidth * uiScale) / 2f);
        windowY = Math.max(0, (height - windowHeight * uiScale) / 2f);
        palette = "Light".equalsIgnoreCase(Configs.GUI.GUI_THEME.getStringValue()) ? Palette.LIGHT : Palette.LIQUIDBOUNCE;
        loadLibrary();
    }

    private void loadLibrary() {
        CloudMusicAsync.INSTANCE.run(
            () -> MusicCommand.getMy(false),
            value -> { applyLibrary(value); return Unit.INSTANCE; },
            error -> { showStatus("未登录或无法连接网易云音乐", true); return Unit.INSTANCE; }
        );
    }

    private void applyLibrary(My my) {
        likedPlaylist = my.likeMusicPlayList();
        List<PlayList> owned = new ArrayList<>();
        for (PlayList list : my.playLists(0, 100)) {
            if (list.creator.has("userId") && list.creator.get("userId").getAsLong() == my.id) owned.add(list);
        }
        playlists = owned;
        openPlaylist(likedPlaylist);
        CloudMusicAsync.INSTANCE.run(
            () -> MusicCommand.getMusic163().cloudMusic(),
            value -> { cloudSongs = new ArrayList<>(value); cloudLoading = false; return Unit.INSTANCE; },
            error -> { cloudLoading = false; return Unit.INSTANCE; }
        );
        cloudLoading = true;
    }

    private void openPlaylist(PlayList playlist) {
        selectedPlaylist = playlist.id;
        pageCoverId = playlist.id;
        pageCoverUrl = playlist.cover;
        showStatus("正在加载 " + playlist.name + "…", false);
        CloudMusicAsync.INSTANCE.run(
            () -> MusicCommand.getMusic163().playlist(playlist.id).getMusics(),
            value -> { if (likedPlaylist != null && playlist.id == likedPlaylist.id) { likedSongIds.clear(); for (IMusic music : value) likedSongIds.add(music.getId()); } showSongs(playlist.name, value); return Unit.INSTANCE; },
            error -> { showStatus("歌单加载失败", true); return Unit.INSTANCE; }
        );
    }

    private void showSongs(String title, List<IMusic> value) {
        pageTitle = title;
        songs = value;
        pageIndex = 0;
        songScroll = 0;
        view = View.LIBRARY;
    }

    private void showStatus(String message, boolean error) {
        status = message;
        statusError = error;
        view = View.STATUS;
    }

    private void runSearch() {
        String query = searchText.trim();
        if (query.isEmpty()) return;
        showStatus("正在搜索 “" + query + "”…", false);
        CloudMusicAsync.INSTANCE.run(
            () -> MusicCommand.searchMusics(query),
            value -> { selectedPlaylist = null; pageCoverUrl = value.isEmpty() ? "" : value.getFirst().getPicUrl(); pageCoverId = value.isEmpty() ? Long.MIN_VALUE : value.getFirst().getId(); showSongs("搜索结果 · " + query, value); return Unit.INSTANCE; },
            error -> { showStatus("搜索失败，请检查网络连接", true); return Unit.INSTANCE; }
        );
    }

    private void play(int index) {
        if (index < 0 || index >= songs.size()) return;
        queue = new ArrayList<>(songs);
        MusicCommand.playMusicsFrom(queue, index);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        float x = ((float) event.x() - windowX) / uiScale, y = ((float) event.y() - windowY) / uiScale;
        if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT || x < 0 || y < 0 || x > windowWidth || y > windowHeight) return true;
        if (gearBox().contains(x, y)) { Minecraft.getInstance().gui.setScreen(new CloudMusicSettingsScreen()); return true; }
        if (closeBox().contains(x, y)) { Minecraft.getInstance().gui.setScreen(null); return true; }
        if (searchBox().contains(x, y)) { searchFocused = true; return true; }
        if (settingsOpen) return clickSettings(x, y);
        if (queueOpen && clickQueue(x, y)) return true;
        if (view == View.LYRICS && new Box(contentX(), headerHeight + 5, 65, 20).contains(x, y)) { view = View.LIBRARY; lyricScroll = 0; return true; }
        if (view == View.LYRICS && lyricToggleBox().contains(x, y)) { showTranslation = !showTranslation; return true; }
        if (view == View.LYRICS && y >= headerHeight + 72 && y < playerTop()) { seekLyricLine(y); return true; }
        if (view == View.LIBRARY && playAllBox().contains(x, y)) { if (!songs.isEmpty()) play(pageIndex * pageSize()); return true; }
        if (y >= playerTop()) return clickPlayer(x, y);
        if (x < sidebarWidth) return clickSidebar(y);
        if (view == View.LIBRARY && y >= rowsTop() && y <= rowsBottom()) {
            if (previousPageBox().contains(x, y)) { previousPage(); return true; }
            if (nextPageBox().contains(x, y)) { nextPage(); return true; }
            if (y < rowsListBottom()) play(pageIndex * pageSize() + (int) ((y - rowsTop() + songScroll) / rowHeight));
        }
        if (y < headerHeight) { draggingWindow = true; dragOffsetX = x; dragOffsetY = y; }
        return true;
    }

    private boolean clickSidebar(float y) {
        float row = 54 - playlistScroll;
        if (y >= row && y <= row + 24 && likedPlaylist != null) { openPlaylist(likedPlaylist); return true; }
        row += 28;
        if (y >= row && y <= row + 24) {
            selectedPlaylist = Long.MIN_VALUE; pageCoverUrl = "";
            if (cloudLoading) showStatus("正在加载我的音乐云盘…", false);
            else if (cloudSongs.isEmpty()) loadCloudDisk();
            else showSongs("我的音乐云盘", cloudSongs);
            return true;
        }
        row += 43;
        for (PlayList list : playlists) {
            if (y >= row && y <= row + 24) { openPlaylist(list); return true; }
            row += 27;
        }
        return true;
    }

    private void loadCloudDisk() {
        cloudLoading = true;
        showStatus("正在加载我的音乐云盘…", false);
        CloudMusicAsync.INSTANCE.run(
            () -> MusicCommand.getMusic163().cloudMusic(),
            value -> { cloudSongs = new ArrayList<>(value); cloudLoading = false; showSongs("我的音乐云盘", cloudSongs); return Unit.INSTANCE; },
            error -> { cloudLoading = false; showStatus("音乐云盘暂时无法访问", true); return Unit.INSTANCE; }
        );
    }

    private int pageSize() {
        int capacity = Math.max(1, (int) ((rowsBottom() - rowsTop() - 18) / rowHeight));
        return Math.max(1, Math.min(Configs.GUI.PAGE_LIMIT.getIntegerValue(), capacity));
    }
    private int pageCount() { return Math.max(1, (songs.size() + pageSize() - 1) / pageSize()); }
    private void previousPage() { if (pageIndex > 0) { pageIndex--; songScroll = 0; } }
    private void nextPage() { if (pageIndex + 1 < pageCount()) { pageIndex++; songScroll = 0; } }

    private boolean clickPlayer(float x, float y) {
        float top = playerTop();
        if (new Box(10, top + 6, 36, 36).contains(x, y) && MusicCommand.getPlayer().getPlayingMusic() != null) view = View.LYRICS;
        else if (new Box(windowWidth / 2 - 48, top + 8, 24, 24).contains(x, y)) MusicCommand.getPlayer().prev();
        else if (new Box(windowWidth / 2 - 12, top + 6, 24, 24).contains(x, y)) MusicCommand.getPlayer().switchPlay();
        else if (new Box(windowWidth / 2 + 24, top + 8, 24, 24).contains(x, y)) MusicCommand.getPlayer().next();
        else if (playModeBox().contains(x, y)) cyclePlayMode();
        else if (new Box(windowWidth - 35, top + 7, 26, 26).contains(x, y)) queueOpen = !queueOpen;
        else if (new Box(100, top + 8, 24, 24).contains(x, y)) toggleLike();
        else if (progressBox().contains(x, y)) { draggingProgress = true; seek(x); }
        else if (volumeBox().contains(x, y)) { draggingVolume = true; setVolume(x); }
        return true;
    }

    private boolean clickSettings(float x, float y) {
        Box panel = settingsBox();
        if (!panel.contains(x, y)) { settingsOpen = false; return true; }
        if (y >= panel.y + 20 && y <= panel.y + 40) { palette = palette == Palette.LIQUIDBOUNCE ? Palette.LIGHT : Palette.LIQUIDBOUNCE; Configs.GUI.GUI_THEME.setStringValue(palette == Palette.LIGHT ? "Light" : "LiquidBounce"); Configs.INSTANCE.save(); }
        else if (y >= panel.y + 41 && y <= panel.y + 61) Configs.PLAY.PLAY_LOOP.setBooleanValue(!Configs.PLAY.PLAY_LOOP.getBooleanValue());
        else if (y >= panel.y + 62 && y <= panel.y + 82) Configs.PLAY.PLAY_AUTO_RANDOM.setBooleanValue(!Configs.PLAY.PLAY_AUTO_RANDOM.getBooleanValue());
        else if (y >= panel.y + 83 && y <= panel.y + 105) Configs.PLAY.PLAY_URL.setBooleanValue(!Configs.PLAY.PLAY_URL.getBooleanValue());
        Configs.INSTANCE.save();
        return true;
    }

    private boolean clickQueue(float x, float y) {
        Box panel = queueBox();
        if (!panel.contains(x, y)) { queueOpen = false; return false; }
        int index = (int) ((y - panel.y - 26) / 18f);
        if (index >= 0 && index < queue.size()) { songs = queue; play(index); }
        return true;
    }

    private void toggleLike() {
        IMusic current = MusicCommand.getPlayer().getPlayingMusic();
        if (!(current instanceof Music music)) return;
        boolean liked = likedSongIds.contains(music.getId());
        CloudMusicAsync.INSTANCE.run(() -> { if (liked) music.unlike(); else music.like(); return !liked; }, value -> { if (value) likedSongIds.add(music.getId()); else likedSongIds.remove(music.getId()); return Unit.INSTANCE; }, error -> Unit.INSTANCE);
    }

    private void seek(float x) {
        IMusic music = MusicCommand.getPlayer().getPlayingMusic();
        if (music == null) return;
        float amount = clamp((x - progressBox().x) / progressBox().width);
        MusicCommand.getPlayer().seek((long) (music.getDurationSecond() * 1000L * amount));
    }

    private void setVolume(float x) {
        MusicCommand.getPlayer().volumeSet((int) (clamp((x - volumeBox().x) / volumeBox().width) * 100));
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double offsetX, double offsetY) {
        float localX = ((float) event.x() - windowX) / uiScale;
        if (draggingWindow) {
            windowX = clamp((float) event.x() - dragOffsetX * uiScale, 0, Math.max(0, width - windowWidth * uiScale));
            windowY = clamp((float) event.y() - dragOffsetY * uiScale, 0, Math.max(0, height - windowHeight * uiScale));
        } else if (draggingProgress) seek(localX); else if (draggingVolume) setVolume(localX);
        return true;
    }

    @Override public boolean mouseReleased(MouseButtonEvent event) { draggingWindow = draggingProgress = draggingVolume = false; return true; }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        float localX = ((float) x - windowX) / uiScale;
        float localY = ((float) y - windowY) / uiScale;
        if (view == View.LYRICS && localX >= contentX() && localY >= headerHeight + 72 && localY < playerTop()) {
            lyricScroll = clamp(lyricScroll - (float) vertical, -50, 50);
        } else if (localX >= 0 && localX < sidebarWidth && localY >= headerHeight && localY < playerTop()) {
            playlistScroll = clamp(playlistScroll - (float) vertical * 12, 0, maxPlaylistScroll());
        } else if (view == View.LIBRARY && localX >= contentX() && localY >= rowsTop() && localY < rowsListBottom()) {
            songScroll = clamp(songScroll - (float) vertical * rowHeight, 0, maxSongScroll());
        }
        return true;
    }
    @Override public boolean keyPressed(KeyEvent event) { if (!searchFocused) return super.keyPressed(event); if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) { searchFocused = false; runSearch(); } else if (event.key() == GLFW.GLFW_KEY_BACKSPACE) searchText = searchText.isEmpty() ? "" : searchText.substring(0, searchText.length() - 1); else if (event.key() == GLFW.GLFW_KEY_ESCAPE) searchFocused = false; return true; }
    @Override public boolean charTyped(CharacterEvent event) { if (!searchFocused) return super.charTyped(event); searchText += event.codepointAsString(); return true; }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.pose().pushMatrix();
        graphics.pose().translate(windowX, windowY);
        graphics.pose().scale(uiScale, uiScale);
        rounded(graphics, 0, 0, windowWidth, windowHeight, 8, background(), divider());
        if (palette == Palette.LIGHT) drawLightGradient(graphics);
        rounded(graphics, 0, 0, sidebarWidth + 8, playerTop() + 4, 8, sidebarSurface(), Color4b.TRANSPARENT);
        quad(graphics, sidebarWidth - 8, 0, sidebarWidth + 8, playerTop(), sidebarSurface());
        quad(graphics, 0, playerTop() - 8, sidebarWidth, playerTop(), sidebarSurface());
        text(graphics, "RikkaMusic", 14, 12, 1.16f, foreground());
        enableClip(graphics, 0, headerHeight, sidebarWidth, playerTop());
        drawSidebar(graphics);
        graphics.disableScissor();
        drawHeader(graphics);
        if (view == View.LIBRARY) drawLibrary(graphics); else if (view == View.LYRICS) drawLyrics(graphics); else text(graphics, status, contentX(), headerHeight + 55, 1.0f, statusError ? new Color4b(245, 85, 85, 255) : secondary());
        drawPlayer(graphics);
        if (queueOpen) drawQueue(graphics);
        if (settingsOpen) drawSettings(graphics);
        graphics.pose().popMatrix();
    }

    private void drawSidebar(GuiGraphicsExtractor g) {
        float y = 44 - playlistScroll;
        sidebarRow(g, "心", "收藏的音乐", likedPlaylist == null ? null : likedPlaylist.id, likedPlaylist == null ? null : likedPlaylist.cover, y); y += 28;
        sidebarRow(g, "云", "我的音乐云盘", Long.MIN_VALUE, y); y += 36;
        text(g, "我的歌单", 14, y, .82f, secondary()); y += 16;
        for (PlayList list : playlists) { sidebarRow(g, "歌", truncate(list.name, 12), list.id, list.cover, y); y += 25; }
    }

    private void sidebarRow(GuiGraphicsExtractor g, String icon, String label, Long id, float y) { sidebarRow(g, icon, label, id, null, y); }
    private void sidebarRow(GuiGraphicsExtractor g, String icon, String label, Long id, String coverUrl, float y) {
        boolean selected = id != null && id.equals(selectedPlaylist);
        if (selected) rounded(g, 7, y, sidebarWidth - 7, y + 23, 5, translucentAccent(), Color4b.TRANSPARENT);
        if (coverUrl != null) cover(g, id, coverUrl, 12, y + 3, 18);
        else { rounded(g, 12, y + 3, 30, y + 21, 4, selected ? translucentAccent() : background(), divider()); text(g, icon, 21, y + 7, .78f, selected ? accent() : secondary(), HorizontalAnchor.CENTER); }
        text(g, label, 36, y + 7, .88f, selected ? foreground() : secondary());
    }

    private void drawHeader(GuiGraphicsExtractor g) {
        rounded(g, searchBox().x, searchBox().y, searchBox().x + searchBox().width, searchBox().y + searchBox().height, 6, surface(), searchFocused ? accent() : divider());
        drawSearchIcon(g, searchBox().x + 10, searchBox().y + 10, secondary());
        text(g, searchText.isBlank() ? "搜索歌曲、歌手或专辑" : truncateToWidth(searchText, searchBox().width - 28, .82f), searchBox().x + 20, searchBox().y + 7, .82f, searchText.isBlank() ? secondary() : foreground());
        drawSettingsIcon(g, gearBox().x + gearBox().width / 2, gearBox().y + gearBox().height / 2, secondary());
        drawCloseIcon(g, closeBox().x + closeBox().width / 2, closeBox().y + closeBox().height / 2, secondary());
    }

    private void drawLibrary(GuiGraphicsExtractor g) {
        if (!pageCoverUrl.isBlank()) cover(g, pageCoverId, pageCoverUrl, contentX(), headerHeight + 10, 48);
        float titleX = pageCoverUrl.isBlank() ? contentX() : contentX() + 60;
        text(g, truncateToWidth(pageTitle, playAllBox().x - titleX - 12, 1.28f), titleX, headerHeight + 14, 1.28f, foreground());
        text(g, "歌曲 " + songs.size(), titleX, headerHeight + 35, .78f, secondary());
        rounded(g, playAllBox().x, playAllBox().y, playAllBox().x + playAllBox().width, playAllBox().y + playAllBox().height, 5, accent(), Color4b.TRANSPARENT);
        text(g, "播放全部", playAllBox().x + playAllBox().width / 2, playAllBox().y + 6, .72f, Color4b.WHITE, HorizontalAnchor.CENTER);
        text(g, "#", contentX() + 9, rowsTop() - 12, .66f, secondary());
        text(g, "歌曲", contentX() + 47, rowsTop() - 12, .66f, secondary());
        text(g, "歌手", contentX() + 166, rowsTop() - 12, .66f, secondary());
        text(g, "时长", windowWidth - 18, rowsTop() - 12, .66f, secondary(), HorizontalAnchor.END);
        quad(g, contentX(), rowsTop() - 6, windowWidth - 12, rowsTop() - 5, divider());
        int start = pageIndex * pageSize();
        int end = Math.min(songs.size(), start + pageSize());
        enableClip(g, contentX(), rowsTop(), windowWidth - 8, rowsListBottom());
        for (int i = start; i < end; i++) {
            int visible = i - start;
            float y = rowsTop() + visible * rowHeight - songScroll;
            if (y < rowsTop() || y + rowHeight > rowsListBottom()) continue;
            IMusic song = songs.get(i);
            boolean playing = MusicCommand.getPlayer().getPlayingMusic() != null && MusicCommand.getPlayer().getPlayingMusic().getId() == song.getId();
            if (playing) rounded(g, contentX(), y, windowWidth - 12, y + rowHeight - 1, 3, translucentAccent(), Color4b.TRANSPARENT);
            cover(g, song, contentX() + 26, y + 2, 15);
            text(g, playing ? "播" : String.format("%02d", i + 1), contentX() + 9, y + 6, .66f, playing ? accent() : secondary());
            text(g, truncateToWidth(song.getName(), 112, .82f), contentX() + 47, y + 3, .82f, playing ? accent() : foreground());
            text(g, truncateToWidth(artistName(song), 82, .72f), contentX() + 166, y + 3, .72f, secondary());
            text(g, song.getDurationToString(), windowWidth - 18, y + 4, .7f, secondary(), HorizontalAnchor.END);
            quad(g, contentX(), y + rowHeight - 1, windowWidth - 12, y + rowHeight, divider());
        }
        g.disableScissor();
        text(g, "上一页", previousPageBox().x, previousPageBox().y + 3, .68f, pageIndex > 0 ? accent() : secondary());
        text(g, (pageIndex + 1) + " / " + pageCount(), windowWidth / 2f, previousPageBox().y + 3, .68f, secondary(), HorizontalAnchor.CENTER);
        text(g, "下一页", nextPageBox().x + nextPageBox().width, nextPageBox().y + 3, .68f, pageIndex + 1 < pageCount() ? accent() : secondary(), HorizontalAnchor.END);
    }

    private void drawLyrics(GuiGraphicsExtractor g) {
        IMusic song = MusicCommand.getPlayer().getPlayingMusic();
        quad(g, sidebarWidth, headerHeight, windowWidth, playerTop(), palette == Palette.LIGHT ? new Color4b(255, 246, 248, 255) : new Color4b(26, 31, 43, 255));
        text(g, "返回歌单", contentX(), headerHeight + 8, .82f, accent());
        rounded(g, windowWidth - 76, headerHeight + 6, windowWidth - 12, headerHeight + 24, 5, showTranslation ? translucentAccent() : surface(), divider());
        text(g, showTranslation ? "显示原文" : "显示翻译", windowWidth - 44, headerHeight + 11, .62f, showTranslation ? accent() : secondary(), HorizontalAnchor.CENTER);
        if (song == null) { text(g, "暂无歌曲", contentX(), headerHeight + 75, 1.1f, secondary()); return; }
        cover(g, song, contentX() + 8, headerHeight + 43, 82);
        float lyricsX = contentX() + 110;
        text(g, truncate(song.getName(), 17), lyricsX, headerHeight + 35, 1.08f, foreground());
        text(g, truncate(artistName(song), 21), lyricsX, headerHeight + 53, .78f, secondary());
        int lyricOffset = Math.round(lyricScroll);
        String[] lyric = MusicCommand.getPlayer().getLyricWindow(lyricOffset, 2, 5);
        String[] translation = MusicCommand.getPlayer().getLyricTranslationWindow(lyricOffset, 2, 5);
        float center = 112;
        if (lyric.length == 0) text(g, "纯音乐，请欣赏", lyricsX, center, .9f, secondary());
        enableClip(g, lyricsX, 72, windowWidth - 12, playerTop() - 8);
        for (int i = 0; i < lyric.length && i < 8; i++) {
            boolean current = i == 2;
            String line = lyric[i];
            if (showTranslation && i < translation.length && !translation[i].isBlank()) line += "  " + translation[i];
            text(g, truncateToWidth(line, windowWidth - lyricsX - 12, current ? .94f : .76f), lyricsX, center + (i - 2) * 18 + lyricScroll, current ? .94f : .76f, current ? foreground() : secondary());
        }
        g.disableScissor();
    }

    private void drawPlayer(GuiGraphicsExtractor g) {
        float y = playerTop();
        quad(g, 0, y, windowWidth, windowHeight, surface()); quad(g, 0, y, windowWidth, y + 1, divider());
        IMusic song = MusicCommand.getPlayer().getPlayingMusic();
        if (song == null) { rounded(g, 8, y + 5, 35, y + 40, 5, background(), divider()); text(g, "音乐", 25, y + 17, .68f, accent(), HorizontalAnchor.CENTER); }
        else cover(g, song, 8, y + 5, 35);
        text(g, truncateToWidth(song == null ? "未在播放" : song.getName(), 55, .82f), 48, y + 8, .82f, foreground());
        text(g, truncateToWidth(song == null ? "" : artistName(song), 55, .68f), 48, y + 24, .68f, secondary());
        drawHeartButton(g, 125, y + 18, song != null && likedSongIds.contains(song.getId()));
        text(g, playModeLabel(), 158, y + 14, .62f, secondary(), HorizontalAnchor.CENTER);
        drawPreviousButton(g, windowWidth / 2 - 38, y + 18, secondary());
        drawPlayButton(g, windowWidth / 2, y + 18, accent());
        drawNextButton(g, windowWidth / 2 + 38, y + 18, secondary());
        float fraction = song == null ? 0 : clamp(MusicCommand.getPlayer().getPlayingProgress() / Math.max(1f, song.getDurationSecond() * 1000f));
        quad(g, progressBox().x, progressBox().y, progressBox().x + progressBox().width, progressBox().y + 3, divider());
        quad(g, progressBox().x, progressBox().y, progressBox().x + progressBox().width * fraction, progressBox().y + 3, accent());
        text(g, "音量", volumeBox().x - 24, y + 35, .72f, secondary());
        quad(g, volumeBox().x, volumeBox().y, volumeBox().x + volumeBox().width, volumeBox().y + 3, divider());
        quad(g, volumeBox().x, volumeBox().y, volumeBox().x + volumeBox().width * MusicCommand.getPlayer().getVolumePercentage() / 100f, volumeBox().y + 3, accent());
        drawQueueButton(g, windowWidth - 20, y + 18, secondary());
    }

    private void drawQueue(GuiGraphicsExtractor g) {
        Box panel = queueBox();
        rounded(g, panel.x, panel.y, panel.x + panel.width, panel.y + panel.height, 6, surface(), divider());
        text(g, "播放列表 · " + queue.size(), panel.x + 10, panel.y + 8, .82f, foreground());
        enableClip(g, panel.x + 5, panel.y + 23, panel.x + panel.width - 5, panel.y + panel.height - 5);
        for (int i = 0; i < queue.size() && i < 10; i++) {
            IMusic item = queue.get(i);
            text(g, truncate(item.getName(), 17), panel.x + 10, panel.y + 28 + i * 18, .72f, secondary());
            text(g, item.getDurationToString(), panel.x + panel.width - 8, panel.y + 28 + i * 18, .66f, secondary(), HorizontalAnchor.END);
        }
        g.disableScissor();
    }

    private void drawSettings(GuiGraphicsExtractor g) {
        Box p = settingsBox();
        rounded(g, p.x, p.y, p.x + p.width, p.y + p.height, 7, surface(), divider());
        text(g, "音乐设置", p.x + 10, p.y + 10, .9f, foreground());
        text(g, "配色", p.x + 10, p.y + 31, .75f, secondary());
        rounded(g, p.x + p.width - 55, p.y + 22, p.x + p.width - 9, p.y + 39, 5, translucentAccent(), Color4b.TRANSPARENT);
        text(g, palette == Palette.LIQUIDBOUNCE ? "LB" : "浅色", p.x + p.width - 32, p.y + 27, .68f, accent(), HorizontalAnchor.CENTER);
        text(g, "循环播放", p.x + 10, p.y + 52, .75f, secondary());
        drawToggle(g, p.x + p.width - 33, p.y + 50, Configs.PLAY.PLAY_LOOP.getBooleanValue());
        text(g, "随机播放", p.x + 10, p.y + 73, .75f, secondary());
        drawToggle(g, p.x + p.width - 33, p.y + 71, Configs.PLAY.PLAY_AUTO_RANDOM.getBooleanValue());
        text(g, "在线播放", p.x + 10, p.y + 94, .75f, secondary());
        drawToggle(g, p.x + p.width - 33, p.y + 92, Configs.PLAY.PLAY_URL.getBooleanValue());
    }

    private float contentX() { return sidebarWidth + 18; }
    private float playerTop() { return windowHeight - playerHeight; }
    private float rowsTop() { return headerHeight + 74; }
    private float rowsListBottom() { return playerTop() - 23; }
    private float rowsBottom() { return playerTop() - 4; }
    private Box searchBox() { return new Box(contentX(), 7, Math.min(160, windowWidth - contentX() - 70), 20); }
    private Box gearBox() { return new Box(windowWidth - 54, 6, 27, 22); }
    private Box closeBox() { return new Box(windowWidth - 28, 6, 22, 22); }
    private Box playAllBox() { return new Box(windowWidth - 72, headerHeight + 12, 62, 20); }
    private Box previousPageBox() { return new Box(contentX(), rowsBottom() - 16, 40, 14); }
    private Box nextPageBox() { return new Box(windowWidth - 52, rowsBottom() - 16, 40, 14); }
    private Box progressBox() { return new Box(windowWidth * .40f, playerTop() + 38, windowWidth * .25f, 4); }
    private Box volumeBox() { return new Box(windowWidth - 74, playerTop() + 38, 48, 4); }
    private Box playModeBox() { return new Box(143, playerTop() + 5, 30, 25); }
    private Box settingsBox() { return new Box(windowWidth - 148, headerHeight + 5, 140, 112); }
    private Box queueBox() { return new Box(windowWidth - 164, headerHeight + 5, 154, playerTop() - headerHeight - 13); }
    private Box lyricToggleBox() { return new Box(windowWidth - 76, headerHeight + 6, 64, 18); }
    private String playModeLabel() {
        if (Configs.PLAY.PLAY_AUTO_RANDOM.getBooleanValue()) return "随机";
        if (Configs.PLAY.PLAY_LOOP.getBooleanValue()) return "循环";
        return "顺序";
    }
    private void cyclePlayMode() {
        boolean loop = Configs.PLAY.PLAY_LOOP.getBooleanValue();
        boolean random = Configs.PLAY.PLAY_AUTO_RANDOM.getBooleanValue();
        if (!loop && !random) { Configs.PLAY.PLAY_LOOP.setBooleanValue(true); Configs.PLAY.PLAY_AUTO_RANDOM.setBooleanValue(false); }
        else if (loop) { Configs.PLAY.PLAY_LOOP.setBooleanValue(false); Configs.PLAY.PLAY_AUTO_RANDOM.setBooleanValue(true); }
        else { Configs.PLAY.PLAY_LOOP.setBooleanValue(false); Configs.PLAY.PLAY_AUTO_RANDOM.setBooleanValue(false); }
        Configs.INSTANCE.save();
    }
    private Color4b sidebarSurface() { return palette == Palette.LIQUIDBOUNCE ? new Color4b(31, 36, 48, 255) : new Color4b(252, 247, 248, 255); }
    private void drawLightGradient(GuiGraphicsExtractor g) {
        Color4b top = new Color4b(255, 239, 242, 255);
        rounded(g, 0, 0, windowWidth, 96, 8, top, Color4b.TRANSPARENT);
        quad(g, 0, 8, windowWidth, 96, top);
    }
    private void enableClip(GuiGraphicsExtractor g, float x1, float y1, float x2, float y2) {
        // GuiGraphicsExtractor transforms scissor coordinates with the active pose itself.
        g.enableScissor((int) x1, (int) y1, (int) x2, (int) y2);
    }
    private float maxPlaylistScroll() {
        float contentBottom = 124 + playlists.size() * 25f;
        return Math.max(0, contentBottom - playerTop());
    }
    private float maxSongScroll() {
        return Math.max(0, (pageSize() * rowHeight) - (rowsListBottom() - rowsTop()));
    }
    private void seekLyricLine(float y) {
        int lineOffset = Math.round(lyricScroll);
        long[] times = MusicCommand.getPlayer().getLyricWindowTimes(lineOffset, 2, 5);
        int index = Math.round((y - 112f - lyricScroll) / 18f) + 2;
        if (index >= 0 && index < times.length && times[index] >= 0) MusicCommand.getPlayer().seek(times[index]);
    }
    private Color4b background() { return palette == Palette.LIQUIDBOUNCE ? new Color4b(25, 28, 37, 252) : new Color4b(248, 249, 251, 255); }
    private Color4b surface() { return palette == Palette.LIQUIDBOUNCE ? new Color4b(34, 39, 51, 255) : Color4b.WHITE; }
    private Color4b foreground() { return palette == Palette.LIQUIDBOUNCE ? new Color4b(244, 246, 251, 255) : new Color4b(28, 43, 66, 255); }
    private Color4b secondary() { return palette == Palette.LIQUIDBOUNCE ? new Color4b(163, 171, 191, 255) : new Color4b(124, 135, 152, 255); }
    private Color4b divider() { return palette == Palette.LIQUIDBOUNCE ? new Color4b(58, 66, 83, 255) : new Color4b(229, 233, 240, 255); }
    private Color4b accent() { return palette == Palette.LIQUIDBOUNCE ? CloudMusicGui.INSTANCE.getACCENT() : new Color4b(255, 61, 88, 255); }
    private Color4b translucentAccent() { Color4b a = accent(); return new Color4b(a.r(), a.g(), a.b(), 40); }
    private static float clamp(float value) { return Math.max(0, Math.min(1, value)); }
    private static float clamp(float value, float min, float max) { return Math.max(min, Math.min(max, value)); }
    private static String truncate(String value, int max) {
        if (value == null || value.isBlank()) return "";
        return value.length() <= max ? value : value.substring(0, Math.max(0, max - 1)) + "…";
    }
    private static String truncateToWidth(String value, float width, float scale) {
        if (value == null || value.isBlank()) return "";
        return CloudMusicGui.INSTANCE.truncate(value, width, CloudMusicGui.INSTANCE.getFontScale() * scale);
    }
    private static String artistName(IMusic music) {
        if (!(music instanceof Music song) || song.artists == null) return "";
        StringBuilder result = new StringBuilder();
        for (var element : song.artists) {
            if (element == null || element.isJsonNull() || !element.isJsonObject()) continue;
            var name = element.getAsJsonObject().get("name");
            if (name != null && !name.isJsonNull() && name.isJsonPrimitive()) {
                if (result.length() > 0) result.append('/');
                result.append(name.getAsString());
            }
        }
        return result.toString();
    }
    private static void cover(GuiGraphicsExtractor g, IMusic music, float x, float y, float size) { cover(g, music.getId(), music.getPicUrl(), x, y, size); }
    private static void cover(GuiGraphicsExtractor g, long id, String url, float x, float y, float size) {
        CloudMusicCoverCache.INSTANCE.load(id, url);
        Identifier textureId = CloudMusicCoverCache.INSTANCE.get(id);
        if (textureId == null) { rounded(g, x, y, x + size, y + size, 5, new Color4b(45, 50, 62, 255), new Color4b(72, 79, 96, 255)); return; }
        var texture = Minecraft.getInstance().getTextureManager().getTexture(textureId);
        Render2DKt.drawTexQuad(g, RenderExtensionsKt.getTextureSetup(texture), x, y, x + size, y + size, 0, 0, 1, 1, -1, RenderPipelines.GUI_TEXTURED);
    }
    private static void quad(GuiGraphicsExtractor g, float x1, float y1, float x2, float y2, Color4b color) { Render2DKt.drawQuad(g, x1, y1, x2, y2, color, Color4b.TRANSPARENT); }
    private static void rounded(GuiGraphicsExtractor g, float x1, float y1, float x2, float y2, float radius, Color4b fill, Color4b outline) { Render2DKt.drawRoundedRect(g, x1, y1, x2, y2, radius, fill, outline, 1); }
    private static void triangle(GuiGraphicsExtractor g, float x0, float y0, float x1, float y1, float x2, float y2, Color4b fill) { Render2DKt.drawTriangle(g, x0, y0, x1, y1, x2, y2, fill, Color4b.TRANSPARENT, true); }
    private static void drawSearchIcon(GuiGraphicsExtractor g, float x, float y, Color4b color) {
        rounded(g, x - 5, y - 5, x + 4, y + 4, 5, Color4b.TRANSPARENT, color);
        quad(g, x + 3, y + 3, x + 7, y + 4, color);
    }
    private static void drawCloseIcon(GuiGraphicsExtractor g, float x, float y, Color4b color) {
        triangle(g, x - 4, y - 5, x - 2, y - 5, x + 4, y + 5, color);
        triangle(g, x - 4, y - 5, x + 4, y + 5, x + 2, y + 5, color);
        triangle(g, x + 4, y - 5, x + 2, y - 5, x - 4, y + 5, color);
        triangle(g, x + 4, y - 5, x - 4, y + 5, x - 2, y + 5, color);
    }
    private static void drawSettingsIcon(GuiGraphicsExtractor g, float x, float y, Color4b color) {
        rounded(g, x - 5, y - 5, x + 5, y + 5, 5, Color4b.TRANSPARENT, color);
        rounded(g, x - 1.5f, y - 1.5f, x + 1.5f, y + 1.5f, 2, color, Color4b.TRANSPARENT);
    }
    private static void drawPlayButton(GuiGraphicsExtractor g, float x, float y, Color4b color) {
        rounded(g, x - 10, y - 10, x + 10, y + 10, 10, color, Color4b.TRANSPARENT);
        triangle(g, x - 2, y - 5, x - 2, y + 5, x + 5, y, Color4b.WHITE);
    }
    private static void drawPreviousButton(GuiGraphicsExtractor g, float x, float y, Color4b color) {
        quad(g, x - 5, y - 6, x - 4, y + 6, color); triangle(g, x + 5, y - 6, x + 5, y + 6, x - 3, y, color);
    }
    private static void drawNextButton(GuiGraphicsExtractor g, float x, float y, Color4b color) {
        triangle(g, x - 5, y - 6, x - 5, y + 6, x + 3, y, color); quad(g, x + 4, y - 6, x + 5, y + 6, color);
    }
    private static void drawQueueButton(GuiGraphicsExtractor g, float x, float y, Color4b color) {
        for (int i = 0; i < 3; i++) { quad(g, x - 7, y - 6 + i * 5, x + 6, y - 5 + i * 5, color); rounded(g, x - 10, y - 6 + i * 5, x - 8, y - 4 + i * 5, 1, color, Color4b.TRANSPARENT); }
    }
    private static void drawHeartButton(GuiGraphicsExtractor g, float x, float y, boolean active) {
        Color4b color = active ? new Color4b(255, 61, 88, 255) : new Color4b(124, 135, 152, 255);
        rounded(g, x - 7, y - 7, x - 1, y, 4, color, Color4b.TRANSPARENT);
        rounded(g, x + 1, y - 7, x + 7, y, 4, color, Color4b.TRANSPARENT);
        triangle(g, x - 7, y - 3, x + 7, y - 3, x, y + 7, color);
    }
    private void drawToggle(GuiGraphicsExtractor g, float x, float y, boolean enabled) {
        rounded(g, x, y, x + 24, y + 11, 6, enabled ? accent() : divider(), Color4b.TRANSPARENT);
        float knob = enabled ? x + 18 : x + 2;
        rounded(g, knob, y + 2, knob + 7, y + 9, 4, Color4b.WHITE, Color4b.TRANSPARENT);
    }
    private static void text(GuiGraphicsExtractor g, String value, float x, float y, float scale, Color4b color) { text(g, value, x, y, scale, color, HorizontalAnchor.START); }
    private static void text(GuiGraphicsExtractor g, String value, float x, float y, float scale, Color4b color, HorizontalAnchor anchor) { drawCloudMusicText(g, value, x, y, CloudMusicGui.INSTANCE.getFontScale() * scale, color, false, anchor, VerticalAnchor.TOP); }
}
