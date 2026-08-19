package fengliu.cloudmusic.gui;

import fengliu.cloudmusic.command.MusicCommand;
import fengliu.cloudmusic.config.Configs;
import fengliu.cloudmusic.music163.IMusic;
import fengliu.cloudmusic.music163.data.My;
import fengliu.cloudmusic.music163.data.Music;
import fengliu.cloudmusic.music163.data.PlayList;
import fengliu.cloudmusic.render.MusicIconTexture;
import kotlin.Unit;
import net.ccbluex.liquidbounce.render.Render2DKt;
import net.ccbluex.liquidbounce.render.engine.font.HorizontalAnchor;
import net.ccbluex.liquidbounce.render.engine.font.VerticalAnchor;
import net.ccbluex.liquidbounce.render.engine.type.Color4b;
import net.ccbluex.liquidbounce.utils.render.RenderExtensionsKt;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

import static fengliu.cloudmusic.gui.CloudMusicGuiKt.drawCloudMusicText;
import static fengliu.cloudmusic.gui.CloudMusicGuiKt.drawCloudMusicTextBold;

public final class CloudMusicScreen extends Screen {

    // --- Design dimensions (match CloudMusicSettingsScreen) ---
    private static final float DESIGN_WIDTH  = 440f;
    private static final float DESIGN_HEIGHT = 313f;
    private static final float WIDTH_RATIO   = 1320f / 2560f;
    private static final float HEIGHT_RATIO  = 940f  / 1440f;
    private static final float SIDEBAR_WIDTH = 84f;
    private static final float HEADER_H      = 30f;
    private static final float PLAYER_H      = 35f;
    private static final float ROW_H         = 22f;
    private static final float COVER_SIZE    = 56f;

    // --- Window bounds (computed in init) ---
    private float winLeft, winTop, winW = DESIGN_WIDTH, winH = DESIGN_HEIGHT;

    // --- State ---
    private enum View { LIKED, FAVORITES, CLOUD, LOCAL, PLAYLIST, SEARCH }
    private View view = View.LIKED;

    private PlayList likedPlaylist;
    private List<PlayList> myPlaylists = List.of();
    private List<IMusic> songs = List.of();
    private Long openPlaylistId;
    private String openPlaylistTitle = "";
    private String openPlaylistCover = "";

    private float sidebarScroll = 0f;
    private float contentScroll = 0f;
    private boolean draggingWin = false;
    private float dragOx, dragOy;
    private boolean draggingProgress = false;
    private boolean draggingVolume   = false;

    private String searchText = "";
    private boolean searchActive = false;
    private List<IMusic> searchResults = List.of();

    public CloudMusicScreen() {
        super(Component.literal("RikkaMusic"));
    }

    // ── helpers ─────────────────────────────────────────────────────────

    private float lx(float sx) { return (sx - winLeft) / winW * DESIGN_WIDTH; }
    private float ly(float sy) { return (sy - winTop)  / winH * DESIGN_HEIGHT; }

    private float fs()  { return CloudMusicGui.INSTANCE.getFontScale(); }
    private float body(){ return fs() * 1.25f; }
    private float small(){ return fs() * 1.05f; }
    private float hdr() { return fs() * 1.4f; }

    private boolean inBox(float mx, float my, float x1, float y1, float x2, float y2) {
        return mx >= x1 && my >= y1 && mx <= x2 && my <= y2;
    }

    private static String fmt(long ms) {
        long s = ms / 1000; return String.format("%02d:%02d", s/60, s%60);
    }

    private static String artistName(IMusic m) {
        if (!(m instanceof Music mu) || mu.artists == null) return "";
        try { return Music.getArtistsName(mu.artists); } catch (Exception e) { return ""; }
    }

    private String trunc(String t, float w) {
        return CloudMusicGui.INSTANCE.truncate(t, w, body());
    }

    // ── init ─────────────────────────────────────────────────────────────

    @Override
    protected void init() {
        float s = Math.min(width * WIDTH_RATIO / DESIGN_WIDTH, height * HEIGHT_RATIO / DESIGN_HEIGHT);
        winW = DESIGN_WIDTH * s; winH = DESIGN_HEIGHT * s;
        winLeft = (width - winW) / 2f; winTop = (height - winH) / 2f;
        loadLibrary();
    }

    private void loadLibrary() {
        CloudMusicAsync.INSTANCE.run(
            () -> MusicCommand.getMy(false),
            my -> { applyLibrary(my); return Unit.INSTANCE; },
            e  -> Unit.INSTANCE
        );
    }

    private void applyLibrary(My my) {
        likedPlaylist = my.likeMusicPlayList();
        List<PlayList> own = new ArrayList<>();
        for (PlayList p : my.playLists(0, 100)) {
            if (p.creator.has("userId") && p.creator.get("userId").getAsLong() == my.id)
                own.add(p);
        }
        myPlaylists = own;
        if (likedPlaylist != null) openPlaylist(likedPlaylist);
    }

    private void openPlaylist(PlayList pl) {
        openPlaylistId    = pl.id;
        openPlaylistTitle = pl.name;
        openPlaylistCover = pl.cover;
        contentScroll     = 0f;
        view              = View.PLAYLIST;
        CloudMusicAsync.INSTANCE.run(
            () -> MusicCommand.getMusic163().playlist(pl.id).getMusics(),
            list -> { songs = list; if (likedPlaylist != null && pl.id == likedPlaylist.id) view = View.LIKED; return Unit.INSTANCE; },
            e    -> Unit.INSTANCE
        );
    }

    // ── render ───────────────────────────────────────────────────────────

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        float lmx = lx(mx), lmy = ly(my);
        g.pose().pushMatrix();
        g.pose().translate(winLeft, winTop);
        g.pose().scale(winW / DESIGN_WIDTH, winH / DESIGN_HEIGHT);

        // background
        quad(g, 0, 0, DESIGN_WIDTH, DESIGN_HEIGHT, CloudMusicGui.INSTANCE.getBACKGROUND());
        rounded(g, 0, 0, DESIGN_WIDTH, DESIGN_HEIGHT, 8f,
            Color4b.TRANSPARENT, CloudMusicGui.INSTANCE.getBORDER());

        drawHeader(g, lmx, lmy);
        drawSidebar(g, lmx, lmy);
        drawContent(g, lmx, lmy);
        drawPlayer(g, lmx, lmy);

        g.pose().popMatrix();
    }

    // ── header ────────────────────────────────────────────────────────────

    private void drawHeader(GuiGraphicsExtractor g, float mx, float my) {
        quad(g, 0, 0, DESIGN_WIDTH, HEADER_H, CloudMusicGui.INSTANCE.getBACKGROUND());
        quad(g, 0, HEADER_H - 1f, DESIGN_WIDTH, HEADER_H, CloudMusicGui.INSTANCE.getBORDER());

        // logo
        float lx = SIDEBAR_WIDTH + 6f;
        drawTextBold(g, "RikkaMusic", lx, 9f, hdr(), CloudMusicGui.INSTANCE.getTEXT());

        // search bar
        float sx = SIDEBAR_WIDTH + 100f, sw = 140f, sh = 18f, sy = 6f;
        boolean sfocus = searchActive || inBox(mx, my, sx, sy, sx+sw, sy+sh);
        rounded(g, sx, sy, sx+sw, sy+sh, 9f,
            sfocus ? CloudMusicGui.INSTANCE.getACTIVE() : CloudMusicGui.INSTANCE.getHOVER(),
            sfocus ? CloudMusicGui.INSTANCE.getACCENT() : CloudMusicGui.INSTANCE.getBORDER());
        String disp = searchText.isEmpty() ? "搜索歌曲、歌手" : searchText;
        Color4b tc = searchText.isEmpty() ? CloudMusicGui.INSTANCE.getTEXT_FAINT() : CloudMusicGui.INSTANCE.getTEXT();
        drawText(g, disp, sx+8f, sy+5f, small(), tc);

        // settings button
        float bx = DESIGN_WIDTH - 24f, by = 7f;
        boolean bh = inBox(mx, my, bx, by, bx+16f, by+16f);
        drawText(g, "⚙", bx, by+1f, body(), bh ? CloudMusicGui.INSTANCE.getACCENT() : CloudMusicGui.INSTANCE.getTEXT_DIM());

        // close
        float cx = DESIGN_WIDTH - 10f, cy = 3f;
        boolean ch = inBox(mx, my, cx-4f, cy, cx+4f, cy+14f);
        drawText(g, "×", cx-3f, cy, body(), ch ? CloudMusicGui.INSTANCE.getERROR() : CloudMusicGui.INSTANCE.getTEXT_DIM());
    }

    // ── sidebar ───────────────────────────────────────────────────────────

    private static final float ITEM_H = 20f;

    private void drawSidebar(GuiGraphicsExtractor g, float mx, float my) {
        quad(g, 0, HEADER_H, SIDEBAR_WIDTH, DESIGN_HEIGHT - PLAYER_H, CloudMusicGui.INSTANCE.getSIDEBAR());
        quad(g, SIDEBAR_WIDTH-1f, HEADER_H, SIDEBAR_WIDTH, DESIGN_HEIGHT-PLAYER_H, CloudMusicGui.INSTANCE.getBORDER());

        float y = HEADER_H + 8f - sidebarScroll;

        drawText(g, "我的", 8f, y, small(), CloudMusicGui.INSTANCE.getTEXT_DIM());
        y += 14f;

        y = sidebarItem(g, mx, my, y, "♥ 喜欢的音乐", view == View.LIKED);
        y = sidebarItem(g, mx, my, y, "★ 我的收藏",   view == View.FAVORITES);
        y = sidebarItem(g, mx, my, y, "☁ 音乐网盘",   view == View.CLOUD);
        y = sidebarItem(g, mx, my, y, "▶ 本地挂载",   view == View.LOCAL);

        y += 6f;
        drawText(g, "我的歌单", 8f, y, small(), CloudMusicGui.INSTANCE.getTEXT_DIM());
        y += 14f;

        for (PlayList pl : myPlaylists) {
            boolean active = view == View.PLAYLIST && openPlaylistId != null && openPlaylistId == pl.id;
            y = sidebarItem(g, mx, my, y, trunc(pl.name, SIDEBAR_WIDTH - 14f), active);
        }
    }

    private float sidebarItem(GuiGraphicsExtractor g, float mx, float my,
                               float y, String label, boolean active) {
        boolean hov = inBox(mx, my, 4f, y, SIDEBAR_WIDTH-4f, y+ITEM_H);
        if (active) {
            rounded(g, 4f, y, SIDEBAR_WIDTH-4f, y+ITEM_H, 4f,
                CloudMusicGui.INSTANCE.getACCENT_SUBTLE(), Color4b.TRANSPARENT);
        } else if (hov) {
            rounded(g, 4f, y, SIDEBAR_WIDTH-4f, y+ITEM_H, 4f,
                CloudMusicGui.INSTANCE.getHOVER(), Color4b.TRANSPARENT);
        }
        Color4b c = active ? CloudMusicGui.INSTANCE.getACCENT() : CloudMusicGui.INSTANCE.getTEXT();
        drawText(g, label, 8f, y+5f, small(), c);
        return y + ITEM_H + 2f;
    }

    // ── content ───────────────────────────────────────────────────────────

    private float contentX() { return SIDEBAR_WIDTH + 8f; }
    private float contentRight() { return DESIGN_WIDTH - 6f; }
    private float contentTop() { return HEADER_H + 4f; }
    private float contentBottom() { return DESIGN_HEIGHT - PLAYER_H - 2f; }

    private void drawContent(GuiGraphicsExtractor g, float mx, float my) {
        g.enableScissor((int)(contentX()), (int)contentTop(), (int)(contentRight()), (int)contentBottom());
        switch (view) {
            case LIKED, PLAYLIST -> drawPlaylistView(g, mx, my);
            case SEARCH    -> drawSearchView(g, mx, my);
            case CLOUD     -> drawSimpleView(g, "我的音乐网盘", "云盘暂无数据");
            case FAVORITES -> drawSimpleView(g, "我的收藏",   "暂无收藏内容");
            case LOCAL     -> drawLocalView(g, mx, my);
        }
        g.disableScissor();
    }

    private void drawPlaylistView(GuiGraphicsExtractor g, float mx, float my) {
        float cx = contentX(), cy = contentTop() + 4f - contentScroll;
        float cw = contentRight() - cx;

        // cover
        drawCoverRect(g, cx, cy, COVER_SIZE);

        // info
        float ix = cx + COVER_SIZE + 8f;
        drawTextBold(g, trunc(openPlaylistTitle, cw - COVER_SIZE - 40f), ix, cy + 2f, hdr(), CloudMusicGui.INSTANCE.getTEXT());
        drawText(g, "Sangatsu_P", ix, cy + 14f, small(), CloudMusicGui.INSTANCE.getTEXT_DIM());

        // play-all button
        float bx = ix, by = cy + 26f, bw = 56f, bh2 = 14f;
        boolean bHov = inBox(mx, my, bx, by, bx+bw, by+bh2);
        rounded(g, bx, by, bx+bw, by+bh2, 7f,
            bHov ? CloudMusicGui.INSTANCE.getACCENT_HOVER() : CloudMusicGui.INSTANCE.getACCENT(),
            Color4b.TRANSPARENT);
        drawText(g, "▶ 播放全部", bx+5f, by+3f, small(), Color4b.WHITE);

        float y = cy + COVER_SIZE + 10f;
        // table header
        drawText(g, "#",   cx+2f,  y, small(), CloudMusicGui.INSTANCE.getTEXT_FAINT());
        drawText(g, "标题", cx+18f, y, small(), CloudMusicGui.INSTANCE.getTEXT_FAINT());
        drawText(g, "歌手", cx+130f,y, small(), CloudMusicGui.INSTANCE.getTEXT_FAINT());
        drawText(g, "时长", contentRight()-28f, y, small(), CloudMusicGui.INSTANCE.getTEXT_FAINT());
        y += 12f;
        quad(g, cx, y, contentRight(), y+0.5f, CloudMusicGui.INSTANCE.getBORDER());
        y += 3f;

        for (int i = 0; i < songs.size(); i++) {
            IMusic s = songs.get(i);
            boolean playing = MusicCommand.getPlayer().getPlayingMusic() != null
                && MusicCommand.getPlayer().getPlayingMusic().getId() == s.getId();
            boolean hov = inBox(mx, my, cx, y, contentRight(), y+ROW_H);

            if (playing) rounded(g, cx, y, contentRight(), y+ROW_H, 3f, CloudMusicGui.INSTANCE.getACCENT_SUBTLE(), Color4b.TRANSPARENT);
            else if (hov) rounded(g, cx, y, contentRight(), y+ROW_H, 3f, CloudMusicGui.INSTANCE.getHOVER(), Color4b.TRANSPARENT);

            Color4b c = playing ? CloudMusicGui.INSTANCE.getACCENT() : CloudMusicGui.INSTANCE.getTEXT();
            drawText(g, String.format("%02d", i+1), cx+2f, y+6f, small(), CloudMusicGui.INSTANCE.getTEXT_FAINT());
            drawText(g, trunc(s.getName(), 105f), cx+18f, y+6f, small(), c);
            drawText(g, trunc(artistName(s), 80f), cx+130f, y+6f, small(), CloudMusicGui.INSTANCE.getTEXT_DIM());
            drawText(g, fmt(s.getDurationSecond()*1000L), contentRight()-28f, y+6f, small(), CloudMusicGui.INSTANCE.getTEXT_DIM());
            y += ROW_H;
        }
    }

    private void drawCoverRect(GuiGraphicsExtractor g, float x, float y, float size) {
        rounded(g, x, y, x+size, y+size, 4f, CloudMusicGui.INSTANCE.getACTIVE(), CloudMusicGui.INSTANCE.getBORDER());
        if (!openPlaylistCover.isEmpty()) {
            CloudMusicCoverCache.INSTANCE.load(openPlaylistId != null ? openPlaylistId : 0L, openPlaylistCover);
            Identifier tid = CloudMusicCoverCache.INSTANCE.get(openPlaylistId != null ? openPlaylistId : 0L);
            if (tid != null) drawTexture(g, tid, x, y, size);
        }
    }

    private void drawSimpleView(GuiGraphicsExtractor g, String title, String sub) {
        float cx = contentX(), cy = contentTop() + 8f;
        drawTextBold(g, title, cx, cy, hdr(), CloudMusicGui.INSTANCE.getTEXT());
        drawText(g, sub, cx, cy+18f, body(), CloudMusicGui.INSTANCE.getTEXT_DIM());
    }

    private void drawLocalView(GuiGraphicsExtractor g, float mx, float my) {
        float cx = contentX(), cy = contentTop() + 8f;
        drawTextBold(g, "本地挂载歌单", cx, cy, hdr(), CloudMusicGui.INSTANCE.getTEXT());
        cy += 20f;
        float bw = 80f, bh = 16f;
        boolean bh2 = inBox(mx, my, cx, cy, cx+bw, cy+bh);
        rounded(g, cx, cy, cx+bw, cy+bh, 8f,
            bh2 ? CloudMusicGui.INSTANCE.getACCENT_HOVER() : CloudMusicGui.INSTANCE.getACCENT(),
            Color4b.TRANSPARENT);
        drawText(g, "选择文件夹", cx+6f, cy+4f, small(), Color4b.WHITE);
    }

    private void drawSearchView(GuiGraphicsExtractor g, float mx, float my) {
        float cx = contentX(), cy = contentTop() + 4f - contentScroll;
        drawTextBold(g, "搜索: " + searchText, cx, cy, hdr(), CloudMusicGui.INSTANCE.getTEXT());
        cy += 18f;
        for (int i = 0; i < searchResults.size(); i++) {
            IMusic s = searchResults.get(i);
            boolean hov = inBox(mx, my, cx, cy, contentRight(), cy+ROW_H);
            if (hov) rounded(g, cx, cy, contentRight(), cy+ROW_H, 3f, CloudMusicGui.INSTANCE.getHOVER(), Color4b.TRANSPARENT);
            drawText(g, trunc(s.getName(), 100f), cx+4f, cy+6f, small(), CloudMusicGui.INSTANCE.getTEXT());
            drawText(g, trunc(artistName(s), 80f), cx+130f, cy+6f, small(), CloudMusicGui.INSTANCE.getTEXT_DIM());
            cy += ROW_H;
        }
    }

    // ── player ────────────────────────────────────────────────────────────

    private float playerTop() { return DESIGN_HEIGHT - PLAYER_H; }

    private void drawPlayer(GuiGraphicsExtractor g, float mx, float my) {
        float pt = playerTop();
        quad(g, 0, pt, DESIGN_WIDTH, DESIGN_HEIGHT, CloudMusicGui.INSTANCE.getPLAYER_BG());
        quad(g, 0, pt, DESIGN_WIDTH, pt+1f, CloudMusicGui.INSTANCE.getBORDER());

        var player = MusicCommand.getPlayer();
        IMusic music = player.getPlayingMusic();

        // cover
        float cx = 8f, cy = pt + 5f, cs = 24f;
        rounded(g, cx, cy, cx+cs, cy+cs, 3f, CloudMusicGui.INSTANCE.getACTIVE(), CloudMusicGui.INSTANCE.getBORDER());
        if (MusicIconTexture.canUseIcon()) drawTexture(g, MusicIconTexture.MUSIC_ICON_ID, cx, cy, cs);

        // track info
        if (music != null) {
            drawText(g, trunc(music.getName(), 60f),    cx+cs+5f, cy+2f,  small(), CloudMusicGui.INSTANCE.getTEXT());
            drawText(g, trunc(artistName(music), 60f),  cx+cs+5f, cy+13f, small(), CloudMusicGui.INSTANCE.getTEXT_DIM());
        } else {
            drawText(g, "暂无播放", cx+cs+5f, cy+9f, small(), CloudMusicGui.INSTANCE.getTEXT_DIM());
        }

        // controls (center)
        float midX = DESIGN_WIDTH / 2f;
        float ctrlY = pt + 5f;

        // prev
        float prevX = midX - 40f;
        boolean prevH = inBox(mx, my, prevX, ctrlY, prevX+16f, ctrlY+14f);
        quad(g, prevX+3f, ctrlY+4f, prevX+5f, ctrlY+10f, prevH ? CloudMusicGui.INSTANCE.getACCENT() : CloudMusicGui.INSTANCE.getTEXT_DIM());
        Render2DKt.drawTriangle(g, prevX+14f, ctrlY+3f, prevX+14f, ctrlY+11f, prevX+5f, ctrlY+7f,
            prevH ? CloudMusicGui.INSTANCE.getACCENT() : CloudMusicGui.INSTANCE.getTEXT_DIM(),
            Color4b.TRANSPARENT, true);

        // play/pause
        float pbX = midX - 10f, pbY = pt + 3f, pbS = 20f;
        boolean pbH = inBox(mx, my, pbX, pbY, pbX+pbS, pbY+pbS);
        boolean playing = player.isPlaying();
        rounded(g, pbX, pbY, pbX+pbS, pbY+pbS, pbS/2f,
            playing ? CloudMusicGui.INSTANCE.getACCENT() : CloudMusicGui.INSTANCE.getACTIVE(),
            playing ? Color4b.TRANSPARENT : CloudMusicGui.INSTANCE.getBORDER());
        if (playing) {
            quad(g, pbX+6f, pbY+5f, pbX+9f, pbY+15f, Color4b.WHITE);
            quad(g, pbX+11f, pbY+5f, pbX+14f, pbY+15f, Color4b.WHITE);
        } else {
            Render2DKt.drawTriangle(g, pbX+7f, pbY+5f, pbX+7f, pbY+15f, pbX+15f, pbY+10f, Color4b.WHITE, Color4b.TRANSPARENT, true);
        }

        // next
        float nextX = midX + 25f;
        boolean nextH = inBox(mx, my, nextX, ctrlY, nextX+16f, ctrlY+14f);
        Render2DKt.drawTriangle(g, nextX+2f, ctrlY+3f, nextX+2f, ctrlY+11f, nextX+11f, ctrlY+7f,
            nextH ? CloudMusicGui.INSTANCE.getACCENT() : CloudMusicGui.INSTANCE.getTEXT_DIM(),
            Color4b.TRANSPARENT, true);
        quad(g, nextX+11f, ctrlY+4f, nextX+13f, ctrlY+10f, nextH ? CloudMusicGui.INSTANCE.getACCENT() : CloudMusicGui.INSTANCE.getTEXT_DIM());

        // progress bar
        float prX = SIDEBAR_WIDTH + 8f, prY = pt + 22f, prW = DESIGN_WIDTH - SIDEBAR_WIDTH - 100f;
        long dur  = music != null ? music.getDurationSecond() * 1000L : 1L;
        long prog = player.getPlayingProgress();
        float frac = dur > 0 ? Math.min(1f, (float) prog / dur) : 0f;
        drawText(g, fmt(prog), prX - 22f, prY - 4f, small(), CloudMusicGui.INSTANCE.getTEXT_FAINT());
        drawText(g, fmt(dur),  prX + prW + 2f, prY - 4f, small(), CloudMusicGui.INSTANCE.getTEXT_FAINT());
        rounded(g, prX, prY, prX+prW, prY+3f, 1.5f, CloudMusicGui.INSTANCE.getPROGRESS_BG(), Color4b.TRANSPARENT);
        if (frac > 0) rounded(g, prX, prY, prX+prW*frac, prY+3f, 1.5f, CloudMusicGui.INSTANCE.getACCENT(), Color4b.TRANSPARENT);
        if (inBox(mx, my, prX, prY-3f, prX+prW, prY+6f))
            rounded(g, prX+prW*frac-2f, prY-1f, prX+prW*frac+2f, prY+4f, 2f, CloudMusicGui.INSTANCE.getACCENT(), Color4b.TRANSPARENT);

        // volume
        float vx = DESIGN_WIDTH - 60f, vy = pt + 20f, vw = 40f;
        int vol = player.getVolumePercentage();
        drawText(g, "♪", vx - 10f, vy - 4f, small(), CloudMusicGui.INSTANCE.getTEXT_FAINT());
        rounded(g, vx, vy, vx+vw, vy+3f, 1.5f, CloudMusicGui.INSTANCE.getPROGRESS_BG(), Color4b.TRANSPARENT);
        rounded(g, vx, vy, vx+vw*vol/100f, vy+3f, 1.5f, CloudMusicGui.INSTANCE.getACCENT(), Color4b.TRANSPARENT);
    }

    // ── mouse/key ─────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubled) {
        if (e.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return true;
        float mx = lx((float)e.x()), my = ly((float)e.y());
        if (mx < 0 || mx > DESIGN_WIDTH || my < 0 || my > DESIGN_HEIGHT) return true;

        // close button
        if (inBox(mx, my, DESIGN_WIDTH-14f, -1f, DESIGN_WIDTH, HEADER_H))  {
            Minecraft.getInstance().gui.setScreen(null); return true; }

        // settings button
        if (inBox(mx, my, DESIGN_WIDTH-24f, 5f, DESIGN_WIDTH-10f, HEADER_H)) {
            Minecraft.getInstance().gui.setScreen(new CloudMusicSettingsScreen()); return true; }

        // search bar click
        if (inBox(mx, my, SIDEBAR_WIDTH+100f, 6f, SIDEBAR_WIDTH+240f, 24f)) {
            searchActive = !searchActive; return true; }

        // drag header
        if (my < HEADER_H) { draggingWin = true; dragOx = mx; dragOy = my; return true; }

        // sidebar items
        if (mx < SIDEBAR_WIDTH) return clickSidebar(mx, my);

        // player
        if (my >= playerTop()) return clickPlayer(mx, my);

        // content
        return clickContent(mx, my);
    }

    private boolean clickSidebar(float mx, float my) {
        float y = HEADER_H + 8f - sidebarScroll + 14f;
        View[] views = { View.LIKED, View.FAVORITES, View.CLOUD, View.LOCAL };
        for (View v : views) {
            if (inBox(mx, my, 4f, y, SIDEBAR_WIDTH-4f, y+ITEM_H)) {
                if (v == View.LIKED && likedPlaylist != null) { openPlaylist(likedPlaylist); return true; }
                view = v; contentScroll = 0f; return true;
            }
            y += ITEM_H + 2f;
        }
        y += 6f + 14f;
        for (PlayList pl : myPlaylists) {
            if (inBox(mx, my, 4f, y, SIDEBAR_WIDTH-4f, y+ITEM_H)) { openPlaylist(pl); return true; }
            y += ITEM_H + 2f;
        }
        return true;
    }

    private boolean clickPlayer(float mx, float my) {
        var player = MusicCommand.getPlayer();
        float pt = playerTop();
        float midX = DESIGN_WIDTH / 2f;
        if (inBox(mx, my, midX-40f, pt+5f, midX-24f, pt+19f)) { player.prev(); return true; }
        if (inBox(mx, my, midX-10f, pt+3f, midX+10f, pt+23f)) { player.switchPlay(); return true; }
        if (inBox(mx, my, midX+25f, pt+5f, midX+41f, pt+19f)) { player.next(); return true; }

        float prX = SIDEBAR_WIDTH+8f, prY = pt+22f, prW = DESIGN_WIDTH-SIDEBAR_WIDTH-100f;
        if (inBox(mx, my, prX, prY-3f, prX+prW, prY+6f)) {
            draggingProgress = true;
            IMusic m = player.getPlayingMusic();
            if (m != null) player.seek((long)((mx-prX)/prW * m.getDurationSecond() * 1000L));
            return true;
        }
        float vx = DESIGN_WIDTH-60f, vy = pt+20f;
        if (inBox(mx, my, vx, vy-3f, vx+40f, vy+6f)) {
            draggingVolume = true;
            player.volumeSet((int)((mx-vx)/40f*100));
            return true;
        }
        return true;
    }

    private boolean clickContent(float mx, float my) {
        if (view != View.LIKED && view != View.PLAYLIST && view != View.SEARCH) return true;
        List<IMusic> list = view == View.SEARCH ? searchResults : songs;
        float cx = contentX();
        float cy = contentTop() + 4f - contentScroll
            + (view == View.SEARCH ? 18f : COVER_SIZE + 10f + 15f);
        for (int i = 0; i < list.size(); i++) {
            if (inBox(mx, my, cx, cy, contentRight(), cy+ROW_H)) {
                MusicCommand.playMusicsFrom(new ArrayList<>(list), i); return true; }
            cy += ROW_H;
        }
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent e, double ox, double oy) {
        float mx = lx((float)e.x()), my = ly((float)e.y());
        if (draggingWin) {
            winLeft = Math.max(0, Math.min((float)e.x()-dragOx*(winW/DESIGN_WIDTH), width-winW));
            winTop  = Math.max(0, Math.min((float)e.y()-dragOy*(winH/DESIGN_HEIGHT), height-winH));
            return true;
        }
        if (draggingProgress) {
            var player = MusicCommand.getPlayer(); IMusic m = player.getPlayingMusic();
            float prX = SIDEBAR_WIDTH+8f, prW = DESIGN_WIDTH-SIDEBAR_WIDTH-100f;
            if (m != null) player.seek((long)(Math.min(1f,Math.max(0f,(mx-prX)/prW))*m.getDurationSecond()*1000L));
            return true;
        }
        if (draggingVolume) {
            MusicCommand.getPlayer().volumeSet((int)(Math.min(1f,Math.max(0f,(mx-(DESIGN_WIDTH-60f))/40f))*100));
            return true;
        }
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent e) {
        draggingWin = draggingProgress = draggingVolume = false; return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hAmt, double vAmt) {
        float lmx = lx((float)mx), lmy = ly((float)my);
        if (lmx < SIDEBAR_WIDTH) sidebarScroll = Math.max(0f, sidebarScroll - (float)vAmt*12f);
        else if (lmy < playerTop()) contentScroll = Math.max(0f, contentScroll - (float)vAmt*12f);
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        if (e.key() == GLFW.GLFW_KEY_ESCAPE) {
            if (searchActive) { searchActive = false; return true; }
            Minecraft.getInstance().gui.setScreen(null); return true;
        }
        if (searchActive && e.key() == GLFW.GLFW_KEY_BACKSPACE && !searchText.isEmpty()) {
            searchText = searchText.substring(0, searchText.length()-1); return true;
        }
        if (searchActive && e.key() == GLFW.GLFW_KEY_ENTER && !searchText.isEmpty()) {
            runSearch(searchText); return true;
        }
        return super.keyPressed(e);
    }

    @Override
    public boolean charTyped(CharacterEvent e) {
        if (searchActive) { searchText += e.codepointAsString(); return true; }
        return super.charTyped(e);
    }

    private void runSearch(String q) {
        view = View.SEARCH; contentScroll = 0f;
        CloudMusicAsync.INSTANCE.run(
            () -> MusicCommand.searchMusics(q),
            r -> { searchResults = r; return Unit.INSTANCE; },
            ex -> Unit.INSTANCE
        );
    }

    // ── draw helpers ──────────────────────────────────────────────────────

    private static void quad(GuiGraphicsExtractor g,
                              float x1, float y1, float x2, float y2, Color4b c) {
        Render2DKt.drawQuad(g, x1, y1, x2, y2, c, Color4b.TRANSPARENT);
    }

    private static void rounded(GuiGraphicsExtractor g,
                                 float x1, float y1, float x2, float y2,
                                 float r, Color4b fill, Color4b outline) {
        Render2DKt.drawRoundedRect(g, x1, y1, x2, y2, r, fill, outline, 1);
    }

    private void drawText(GuiGraphicsExtractor g, String t, float x, float y,
                          float scale, Color4b c) {
        drawCloudMusicText(g, t, x, y, scale, c, false,
            HorizontalAnchor.START, VerticalAnchor.TOP);
    }

    private void drawTextBold(GuiGraphicsExtractor g, String t, float x, float y,
                               float scale, Color4b c) {
        drawCloudMusicTextBold(g, t, x, y, scale, c, false,
            HorizontalAnchor.START, VerticalAnchor.TOP);
    }

    private static void drawTexture(GuiGraphicsExtractor g, Identifier id, float x, float y, float s) {
        var tex = Minecraft.getInstance().getTextureManager().getTexture(id);
        if (tex == null) return;
        Render2DKt.drawTexQuad(g, RenderExtensionsKt.getTextureSetup(tex),
            x, y, x+s, y+s, 0f, 0f, 1f, 1f, -1, RenderPipelines.GUI_TEXTURED);
    }
}
