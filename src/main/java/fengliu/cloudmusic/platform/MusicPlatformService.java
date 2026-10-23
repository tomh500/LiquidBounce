package fengliu.cloudmusic.platform;

import com.google.gson.*;
import fengliu.cloudmusic.CloudMusicClient;
import fengliu.cloudmusic.command.MusicCommand;
import fengliu.cloudmusic.gui.RikkaMusicBrowse;
import fengliu.cloudmusic.music163.IMusic;
import fengliu.cloudmusic.music163.data.Music;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public final class MusicPlatformService {
    private static final class LibraryHolder {
        private static final MixedMusicLibrary LIBRARY = new MixedMusicLibrary(
                CloudMusicClient.MC_PATH.resolve("LiquidBounce/rikkamusic-library.json"), () -> MusicCommand.getMusic163().getHttpClient());
    }

    private record SearchPage(List<IMusic> songs, int total, String error) { }

    private static SearchPage searchProvider(String source, String query, int page) {
        try {
            if (source.equals("qq")) {
                var result = QqMusicApi.search(query, page, 8);
                return new SearchPage(result.songs(), result.total(), "");
            }
            var result = MusicCommand.getMusic163().search(query, 1, page, 8).getAsJsonObject("result");
            var items = result.getAsJsonArray("songs");
            List<IMusic> songs = new ArrayList<>();
            if (items != null) for (var item : items) songs.add(new Music(MusicCommand.getMusic163().getHttpClient(), item.getAsJsonObject(), null));
            return new SearchPage(songs, result.has("songCount") ? result.get("songCount").getAsInt() : songs.size(), "");
        } catch (RuntimeException error) {
            return new SearchPage(List.of(), 0, (source.equals("qq") ? "QQ 音乐" : "网易云音乐") + " 搜索暂不可用，请稍后重试");
        }
    }

    public static JsonObject search(String source, String query, int page) {
        if (!Set.of("netease", "qq", "all").contains(source)) throw new IllegalArgumentException("未知音乐来源");
        List<String> sources = source.equals("all") ? List.of("netease", "qq") : List.of(source);
        var futures = sources.stream().map(s -> CompletableFuture.supplyAsync(() -> searchProvider(s, query, page))).toList();
        var pages = futures.stream().map(CompletableFuture::join).toList();
        List<IMusic> songs = new ArrayList<>();
        // Interleave platform rankings without treating same-title covers as the same recording.
        for (int i = 0; i < 8; i++) for (var result : pages) if (i < result.songs.size()) songs.add(result.songs.get(i));
        JsonObject result = collection(songs);
        result.addProperty("type", "song");
        result.addProperty("page", page);
        result.addProperty("total", pages.stream().mapToInt(p -> p.total).sum());
        result.addProperty("pageCount", Math.max(1, pages.stream().mapToInt(p -> (p.total + 7) / 8).max().orElse(1)));
        result.addProperty("warning", String.join("；", pages.stream().map(p -> p.error).filter(e -> !e.isEmpty()).toList()));
        result.add("items", result.get("songs"));
        return result;
    }

    private static JsonObject collection(List<IMusic> songs) {
        JsonObject result = new JsonObject();
        JsonArray tracks = new JsonArray();
        songs.forEach(song -> tracks.add(RikkaMusicBrowse.song(song)));
        result.add("songs", tracks);
        result.addProperty("queueToken", RikkaMusicBrowse.remember(songs));
        return result;
    }

    public static JsonObject library() { return collection(LibraryHolder.LIBRARY.songs()); }
    public static JsonObject collect(String token, Integer index) {
        var songs = RikkaMusicBrowse.queue(token);
        if (index != null) {
            if (index < 0 || index >= songs.size()) throw new IllegalArgumentException("无效歌曲序号");
            songs = List.of(songs.get(index));
        }
        int added = LibraryHolder.LIBRARY.add(songs);
        var result = library();
        result.addProperty("added", added);
        return result;
    }
    public static JsonObject remove(String key) { LibraryHolder.LIBRARY.remove(key); return library(); }

    public static JsonObject importPlaylist(String source, String reference) {
        long id = playlistId(source, reference);
        List<IMusic> songs;
        if (source.equals("qq")) songs = QqMusicApi.playlist(id);
        else {
            var playlist = MusicCommand.getMusic163().playlist(id);
            songs = playlist.getMusics();
            if (songs.size() < playlist.count) throw new IllegalStateException("网易云歌单返回不完整，请登录后重试，未导入");
        }
        if (songs.isEmpty()) throw new IllegalArgumentException("歌单为空或不可访问");
        int added = LibraryHolder.LIBRARY.add(songs);
        var result = library();
        result.addProperty("added", added);
        return result;
    }

    public static long playlistId(String source, String reference) {
        if (!Set.of("netease", "qq").contains(source)) throw new IllegalArgumentException("请选择歌单来源");
        String value = reference.trim();
        if (!value.matches("[0-9]{1,18}")) {
            java.net.URI uri;
            try { uri = java.net.URI.create(value); } catch (RuntimeException e) { throw new IllegalArgumentException("请输入歌单 ID 或完整歌单链接"); }
            String host = uri.getHost();
            boolean valid = source.equals("qq") ? "y.qq.com".equals(host) : "music.163.com".equals(host);
            if (!valid || !("https".equals(uri.getScheme()) || "http".equals(uri.getScheme())))
                throw new IllegalArgumentException("歌单链接与所选平台不符");
            String pattern = source.equals("qq") ? "(?:[?&](?:id|disstid)=|/playlist/)([0-9]{1,18})(?:[^0-9]|$)"
                    : "[?&]id=([0-9]{1,18})(?:[^0-9]|$)";
            var matcher = java.util.regex.Pattern.compile(pattern).matcher(value);
            if (!matcher.find() || source.equals("netease") && !value.contains("playlist"))
                throw new IllegalArgumentException("请输入歌单 ID 或完整歌单链接，不支持短链接");
            value = matcher.group(1);
        }
        long id = Long.parseLong(value);
        if (id <= 0) throw new IllegalArgumentException("歌单 ID 无效");
        return id;
    }

    private MusicPlatformService() { }
}
