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

    private float windowX, windowY, windowWidth = 1040, windowHeight = 660;
    private final float sidebarWidth = 238, headerHeight = 58, playerHeight = 88;
    private View view = View.STATUS;
    private Palette palette = Palette.LIQUIDBOUNCE;
    private String status = "正在连接网易云音乐…", searchText = "", pageTitle = "";
    private boolean statusError, searchFocused, settingsOpen, queueOpen;
    private boolean draggingWindow, draggingProgress, draggingVolume;
    private float dragOffsetX, dragOffsetY, songScroll, playlistScroll;
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
        windowWidth = Math.max(800, Math.min(1100, width * 0.68f));
        windowHeight = Math.max(520, Math.min(720, height * 0.76f));
        windowX = Math.max(0, (width - windowWidth) / 2f);
        windowY = Math.max(0, (height - windowHeight) / 2f);
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
            value -> { cloudSongs = new ArrayList<>(value); return Unit.INSTANCE; },
            error -> Unit.INSTANCE
        );
    }

    private void openPlaylist(PlayList playlist) {
        selectedPlaylist = playlist.id;
        pageCoverId = playlist.id;
        pageCoverUrl = playlist.cover;
        showStatus("正在加载 " + playlist.name + "…", false);
        CloudMusicAsync.INSTANCE.run(
            playlist::getMusics,
            value -> { if (likedPlaylist != null && playlist.id == likedPlaylist.id) { likedSongIds.clear(); for (IMusic music : value) likedSongIds.add(music.getId()); } showSongs(playlist.name, value); return Unit.INSTANCE; },
            error -> { showStatus("歌单加载失败", true); return Unit.INSTANCE; }
        );
    }

    private void showSongs(String title, List<IMusic> value) {
        pageTitle = title;
        songs = value;
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
        float x = (float) event.x() - windowX, y = (float) event.y() - windowY;
        if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT || x < 0 || y < 0 || x > windowWidth || y > windowHeight) return true;
        if (gearBox().contains(x, y)) { settingsOpen = !settingsOpen; queueOpen = false; return true; }
        if (closeBox().contains(x, y)) { Minecraft.getInstance().gui.setScreen(null); return true; }
        if (searchBox().contains(x, y)) { searchFocused = true; return true; }
        if (settingsOpen) return clickSettings(x, y);
        if (queueOpen && clickQueue(x, y)) return true;
        if (view == View.LYRICS && new Box(contentX(), headerHeight + 12, 120, 38).contains(x, y)) { view = View.LIBRARY; return true; }
        if (view == View.LIBRARY && new Box(windowWidth - 132, headerHeight + 23, 107, 32).contains(x, y)) { if (!songs.isEmpty()) play(0); return true; }
        if (y >= playerTop()) return clickPlayer(x, y);
        if (x < sidebarWidth) return clickSidebar(y);
        if (view == View.LIBRARY && y >= rowsTop() && y <= rowsBottom()) play((int) ((y - rowsTop() + songScroll) / 49f));
        if (y < headerHeight) { draggingWindow = true; dragOffsetX = x; dragOffsetY = y; }
        return true;
    }

    private boolean clickSidebar(float y) {
        float row = headerHeight + 55 - playlistScroll;
        if (y >= row && y <= row + 36 && likedPlaylist != null) { openPlaylist(likedPlaylist); return true; }
        row += 46;
        if (y >= row && y <= row + 36) { selectedPlaylist = Long.MIN_VALUE; pageCoverUrl = ""; showSongs("我的音乐云盘", cloudSongs); return true; }
        row += 65;
        for (PlayList list : playlists) {
            if (y >= row && y <= row + 38) { openPlaylist(list); return true; }
            row += 42;
        }
        return true;
    }

    private boolean clickPlayer(float x, float y) {
        float top = playerTop();
        if (new Box(22, top + 12, 56, 56).contains(x, y) && MusicCommand.getPlayer().getPlayingMusic() != null) view = View.LYRICS;
        else if (new Box(windowWidth / 2 - 88, top + 22, 36, 36).contains(x, y)) MusicCommand.getPlayer().prev();
        else if (new Box(windowWidth / 2 - 21, top + 15, 42, 42).contains(x, y)) MusicCommand.getPlayer().switchPlay();
        else if (new Box(windowWidth / 2 + 54, top + 22, 36, 36).contains(x, y)) MusicCommand.getPlayer().next();
        else if (new Box(windowWidth - 74, top + 19, 38, 38).contains(x, y)) queueOpen = !queueOpen;
        else if (new Box(286, top + 18, 38, 38).contains(x, y)) toggleLike();
        else if (progressBox().contains(x, y)) { draggingProgress = true; seek(x); }
        else if (volumeBox().contains(x, y)) { draggingVolume = true; setVolume(x); }
        return true;
    }

    private boolean clickSettings(float x, float y) {
        Box panel = settingsBox();
        if (!panel.contains(x, y)) { settingsOpen = false; return true; }
        if (y >= panel.y + 50 && y <= panel.y + 88) { palette = palette == Palette.LIQUIDBOUNCE ? Palette.LIGHT : Palette.LIQUIDBOUNCE; Configs.GUI.GUI_THEME.setStringValue(palette == Palette.LIGHT ? "Light" : "LiquidBounce"); Configs.INSTANCE.save(); }
        else if (y >= panel.y + 98 && y <= panel.y + 136) Configs.PLAY.PLAY_LOOP.setBooleanValue(!Configs.PLAY.PLAY_LOOP.getBooleanValue());
        else if (y >= panel.y + 146 && y <= panel.y + 184) Minecraft.getInstance().gui.setScreen(new CloudMusicSettingsScreen());
        return true;
    }

    private boolean clickQueue(float x, float y) {
        float panelX = windowWidth - 355, panelY = headerHeight + 10;
        if (x < panelX || x > windowWidth - 18 || y < panelY || y > playerTop() - 12) { queueOpen = false; return false; }
        int index = (int) ((y - panelY - 47) / 30f);
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
        float localX = (float) event.x() - windowX;
        if (draggingWindow) {
            windowX = clamp((float) event.x() - dragOffsetX, 0, Math.max(0, width - windowWidth));
            windowY = clamp((float) event.y() - dragOffsetY, 0, Math.max(0, height - windowHeight));
        } else if (draggingProgress) seek(localX); else if (draggingVolume) setVolume(localX);
        return true;
    }

    @Override public boolean mouseReleased(MouseButtonEvent event) { draggingWindow = draggingProgress = draggingVolume = false; return true; }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) { if (x - windowX < sidebarWidth) playlistScroll = Math.max(0, playlistScroll - (float) vertical * 28); else songScroll = Math.max(0, songScroll - (float) vertical * 45); return true; }
    @Override public boolean keyPressed(KeyEvent event) { if (!searchFocused) return super.keyPressed(event); if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) { searchFocused = false; runSearch(); } else if (event.key() == GLFW.GLFW_KEY_BACKSPACE) searchText = searchText.isEmpty() ? "" : searchText.substring(0, searchText.length() - 1); else if (event.key() == GLFW.GLFW_KEY_ESCAPE) searchFocused = false; return true; }
    @Override public boolean charTyped(CharacterEvent event) { if (!searchFocused) return super.charTyped(event); searchText += event.codepointAsString(); return true; }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.pose().pushMatrix();
        graphics.pose().translate(windowX, windowY);
        rounded(graphics, 0, 0, windowWidth, windowHeight, 8, background(), divider());
        quad(graphics, 0, 0, sidebarWidth, windowHeight, surface());
        drawSidebar(graphics);
        drawHeader(graphics);
        if (view == View.LIBRARY) drawLibrary(graphics); else if (view == View.LYRICS) drawLyrics(graphics); else text(graphics, status, contentX(), headerHeight + 105, 1.35f, statusError ? new Color4b(245, 85, 85, 255) : secondary());
        drawPlayer(graphics);
        if (queueOpen) drawQueue(graphics);
        if (settingsOpen) drawSettings(graphics);
        graphics.pose().popMatrix();
    }

    private void drawSidebar(GuiGraphicsExtractor g) {
        text(g, "RikkaMusic", 23, 19, 1.5f, foreground());
        text(g, "我的音乐", 23, 45, 1.05f, secondary());
        float y = headerHeight + 55 - playlistScroll;
        sidebarRow(g, "♥", "收藏的音乐", likedPlaylist == null ? null : likedPlaylist.id, likedPlaylist == null ? null : likedPlaylist.cover, y); y += 46;
        sidebarRow(g, "☁", "我的音乐云盘", Long.MIN_VALUE, y); y += 65;
        text(g, "我的歌单", 23, y, 1.05f, secondary()); y += 30;
        for (PlayList list : playlists) { sidebarRow(g, "♫", truncate(list.name, 19), list.id, list.cover, y); y += 42; }
    }

    private void sidebarRow(GuiGraphicsExtractor g, String icon, String label, Long id, float y) { sidebarRow(g, icon, label, id, null, y); }
    private void sidebarRow(GuiGraphicsExtractor g, String icon, String label, Long id, String coverUrl, float y) {
        boolean selected = id != null && id.equals(selectedPlaylist);
        if (selected) rounded(g, 10, y, sidebarWidth - 10, y + 36, 5, translucentAccent(), Color4b.TRANSPARENT);
        if (coverUrl != null) cover(g, id, coverUrl, 20, y + 4, 30);
        else { rounded(g, 20, y + 5, 48, y + 33, 4, selected ? translucentAccent() : background(), divider()); text(g, icon, 34, y + 11, 1.15f, selected ? accent() : secondary(), HorizontalAnchor.CENTER); }
        text(g, label, 59, y + 11, 1.18f, selected ? foreground() : secondary());
    }

    private void drawHeader(GuiGraphicsExtractor g) {
        rounded(g, searchBox().x, searchBox().y, searchBox().x + searchBox().width, searchBox().y + searchBox().height, 6, surface(), searchFocused ? accent() : divider());
        text(g, searchText.isBlank() ? "搜索歌曲、歌手或专辑" : searchText, searchBox().x + 15, searchBox().y + 10, 1.18f, searchText.isBlank() ? secondary() : foreground());
        text(g, "⌕", searchBox().x + searchBox().width - 19, searchBox().y + 9, 1.35f, secondary());
        text(g, "⚙", gearBox().x + 19, gearBox().y + 9, 1.35f, secondary(), HorizontalAnchor.CENTER);
        text(g, "×", closeBox().x + 18, closeBox().y + 8, 1.45f, secondary(), HorizontalAnchor.CENTER);
    }

    private void drawLibrary(GuiGraphicsExtractor g) {
        if (!pageCoverUrl.isBlank()) cover(g, pageCoverId, pageCoverUrl, contentX(), headerHeight + 16, 76);
        float titleX = pageCoverUrl.isBlank() ? contentX() : contentX() + 96;
        text(g, pageTitle, titleX, headerHeight + 24, 2.0f, foreground());
        text(g, "歌曲 " + songs.size(), titleX, headerHeight + 61, 1.16f, secondary());
        rounded(g, windowWidth - 132, headerHeight + 23, windowWidth - 25, headerHeight + 55, 5, accent(), Color4b.TRANSPARENT);
        text(g, "▶ 播放全部", windowWidth - 78, headerHeight + 33, 1.08f, palette == Palette.LIGHT ? Color4b.WHITE : new Color4b(15, 18, 25, 255), HorizontalAnchor.CENTER);
        quad(g, contentX(), rowsTop() - 10, windowWidth - 25, rowsTop() - 9, divider());
        for (int i = 0; i < songs.size(); i++) {
            float y = rowsTop() + i * 49 - songScroll;
            if (y < rowsTop() - 49 || y > rowsBottom()) continue;
            IMusic song = songs.get(i);
            boolean playing = MusicCommand.getPlayer().getPlayingMusic() != null && MusicCommand.getPlayer().getPlayingMusic().getId() == song.getId();
            if (playing) quad(g, contentX(), y, windowWidth - 25, y + 44, translucentAccent());
            cover(g, song, contentX() + 43, y + 5, 34);
            text(g, playing ? "▶" : String.format("%02d", i + 1), contentX() + 16, y + 15, 1.02f, playing ? accent() : secondary());
            text(g, truncate(song.getName(), 36), contentX() + 88, y + 7, 1.2f, playing ? accent() : foreground());
            text(g, truncate(artistName(song), 28), contentX() + 88, y + 27, 1.0f, secondary());
            text(g, song.getDurationToString(), windowWidth - 40, y + 16, 1.02f, secondary(), HorizontalAnchor.END);
            quad(g, contentX(), y + 44, windowWidth - 25, y + 45, divider());
        }
    }

    private void drawLyrics(GuiGraphicsExtractor g) {
        IMusic song = MusicCommand.getPlayer().getPlayingMusic();
        quad(g, sidebarWidth, headerHeight, windowWidth, playerTop(), palette == Palette.LIGHT ? new Color4b(225, 230, 239, 255) : new Color4b(26, 31, 43, 255));
        text(g, "‹  正在播放", contentX(), headerHeight + 24, 1.35f, foreground());
        if (song == null) { text(g, "暂无歌曲", contentX(), headerHeight + 115, 1.8f, secondary()); return; }
        cover(g, song, contentX() + 18, headerHeight + 70, Math.min(270, rowsBottom() - headerHeight - 90));
        float lyricsX = contentX() + Math.min(310, windowWidth * .3f);
        text(g, song.getName(), lyricsX, headerHeight + 78, 2.0f, foreground());
        text(g, artistName(song), lyricsX, headerHeight + 115, 1.2f, secondary());
        String[] lyric = MusicCommand.getPlayer().getLyric();
        float center = (rowsTop() + rowsBottom()) / 2f;
        if (lyric.length == 0) text(g, "纯音乐，请欣赏", lyricsX, center, 1.5f, secondary());
        for (int i = 0; i < lyric.length && i < 12; i++) text(g, lyric[i], lyricsX, center + (i - 2) * 34, i == 2 ? 1.55f : 1.22f, i == 2 ? foreground() : secondary());
    }

    private void drawPlayer(GuiGraphicsExtractor g) {
        float y = playerTop();
        quad(g, 0, y, windowWidth, windowHeight, surface()); quad(g, 0, y, windowWidth, y + 1, divider());
        IMusic song = MusicCommand.getPlayer().getPlayingMusic();
        if (song == null) { rounded(g, 22, y + 13, 76, y + 67, 6, background(), divider()); text(g, "♫", 49, y + 29, 1.65f, accent(), HorizontalAnchor.CENTER); }
        else cover(g, song, 22, y + 13, 54);
        text(g, truncate(song == null ? "未在播放" : song.getName(), 21), 89, y + 21, 1.2f, foreground());
        text(g, truncate(song == null ? "" : artistName(song), 20), 89, y + 45, 1.02f, secondary());
        text(g, song != null && likedSongIds.contains(song.getId()) ? "♥" : "♡", 305, y + 29, 1.45f, song != null && likedSongIds.contains(song.getId()) ? accent() : secondary(), HorizontalAnchor.CENTER);
        text(g, "↢", windowWidth / 2 - 70, y + 29, 1.45f, secondary(), HorizontalAnchor.CENTER);
        text(g, MusicCommand.getPlayer().isPlaying() ? "Ⅱ" : "▶", windowWidth / 2, y + 29, 1.55f, accent(), HorizontalAnchor.CENTER);
        text(g, "↣", windowWidth / 2 + 70, y + 29, 1.45f, secondary(), HorizontalAnchor.CENTER);
        float fraction = song == null ? 0 : clamp(MusicCommand.getPlayer().getPlayingProgress() / Math.max(1f, song.getDurationSecond() * 1000f));
        quad(g, progressBox().x, progressBox().y, progressBox().x + progressBox().width, progressBox().y + 3, divider());
        quad(g, progressBox().x, progressBox().y, progressBox().x + progressBox().width * fraction, progressBox().y + 3, accent());
        text(g, "音量", volumeBox().x - 43, y + 66, 1.0f, secondary());
        quad(g, volumeBox().x, volumeBox().y, volumeBox().x + volumeBox().width, volumeBox().y + 3, divider());
        quad(g, volumeBox().x, volumeBox().y, volumeBox().x + volumeBox().width * MusicCommand.getPlayer().getVolumePercentage() / 100f, volumeBox().y + 3, accent());
        text(g, "☷", windowWidth - 54, y + 29, 1.4f, secondary(), HorizontalAnchor.CENTER);
    }

    private void drawQueue(GuiGraphicsExtractor g) {
        float x = windowWidth - 355, y = headerHeight + 10;
        rounded(g, x, y, windowWidth - 18, playerTop() - 12, 7, surface(), divider());
        text(g, "播放列表 · " + queue.size(), x + 18, y + 18, 1.35f, foreground());
        for (int i = 0; i < queue.size() && i < 12; i++) text(g, truncate(queue.get(i).getName(), 27), x + 18, y + 55 + i * 30, 1.05f, secondary());
    }

    private void drawSettings(GuiGraphicsExtractor g) {
        Box p = settingsBox();
        rounded(g, p.x, p.y, p.x + p.width, p.y + p.height, 7, surface(), divider());
        text(g, "RikkaMusic 设置", p.x + 18, p.y + 17, 1.35f, foreground());
        text(g, "界面配色", p.x + 18, p.y + 58, 1.15f, secondary());
        text(g, palette == Palette.LIQUIDBOUNCE ? "LiquidBounce" : "浅色", p.x + p.width - 18, p.y + 58, 1.15f, accent(), HorizontalAnchor.END);
        text(g, "循环播放", p.x + 18, p.y + 105, 1.15f, secondary());
        text(g, Configs.PLAY.PLAY_LOOP.getBooleanValue() ? "开启" : "关闭", p.x + p.width - 18, p.y + 105, 1.15f, accent(), HorizontalAnchor.END);
        text(g, "完整 CloudMusic 设置…", p.x + 18, p.y + 153, 1.15f, accent());
    }

    private float contentX() { return sidebarWidth + 26; }
    private float playerTop() { return windowHeight - playerHeight; }
    private float rowsTop() { return headerHeight + 100; }
    private float rowsBottom() { return playerTop() - 16; }
    private Box searchBox() { return new Box(contentX(), 13, Math.min(355, windowWidth - contentX() - 150), 34); }
    private Box gearBox() { return new Box(windowWidth - 92, 12, 38, 34); }
    private Box closeBox() { return new Box(windowWidth - 48, 12, 36, 34); }
    private Box progressBox() { return new Box(windowWidth * .36f, playerTop() + 69, windowWidth * .28f, 7); }
    private Box volumeBox() { return new Box(windowWidth - 192, playerTop() + 69, 92, 7); }
    private Box settingsBox() { return new Box(windowWidth - 318, headerHeight + 8, 300, 194); }
    private Color4b background() { return palette == Palette.LIQUIDBOUNCE ? new Color4b(25, 28, 37, 252) : new Color4b(248, 249, 251, 255); }
    private Color4b surface() { return palette == Palette.LIQUIDBOUNCE ? new Color4b(34, 39, 51, 255) : Color4b.WHITE; }
    private Color4b foreground() { return palette == Palette.LIQUIDBOUNCE ? new Color4b(244, 246, 251, 255) : new Color4b(28, 43, 66, 255); }
    private Color4b secondary() { return palette == Palette.LIQUIDBOUNCE ? new Color4b(163, 171, 191, 255) : new Color4b(124, 135, 152, 255); }
    private Color4b divider() { return palette == Palette.LIQUIDBOUNCE ? new Color4b(58, 66, 83, 255) : new Color4b(229, 233, 240, 255); }
    private Color4b accent() { return palette == Palette.LIQUIDBOUNCE ? CloudMusicGui.INSTANCE.getACCENT() : new Color4b(255, 61, 88, 255); }
    private Color4b translucentAccent() { Color4b a = accent(); return new Color4b(a.r(), a.g(), a.b(), 40); }
    private static float clamp(float value) { return Math.max(0, Math.min(1, value)); }
    private static float clamp(float value, float min, float max) { return Math.max(min, Math.min(max, value)); }
    private static String truncate(String value, int max) { return value.length() <= max ? value : value.substring(0, Math.max(0, max - 1)) + "…"; }
    private static String artistName(IMusic music) { return music instanceof Music song ? Music.getArtistsName(song.artists) : ""; }
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
    private static void text(GuiGraphicsExtractor g, String value, float x, float y, float scale, Color4b color) { text(g, value, x, y, scale, color, HorizontalAnchor.START); }
    private static void text(GuiGraphicsExtractor g, String value, float x, float y, float scale, Color4b color, HorizontalAnchor anchor) { drawCloudMusicText(g, value, x, y, CloudMusicGui.INSTANCE.getFontScale() * scale, color, true, anchor, VerticalAnchor.TOP); }
}
