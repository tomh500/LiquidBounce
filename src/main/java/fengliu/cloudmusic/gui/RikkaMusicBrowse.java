package fengliu.cloudmusic.gui;

import com.google.gson.*;
import fengliu.cloudmusic.command.MusicCommand;
import fengliu.cloudmusic.music163.IMusic;
import fengliu.cloudmusic.music163.data.*;
import java.util.*;

/** Browser collections retain their exact queue, including personal FM batches. */
public final class RikkaMusicBrowse {
    private static final Map<String, List<IMusic>> QUEUES = new LinkedHashMap<>();

    public static synchronized void clear() { QUEUES.clear(); }

    public static synchronized List<IMusic> queue(String token) {
        var songs = QUEUES.get(token);
        if (songs == null) throw new IllegalArgumentException("列表已失效，请刷新后播放");
        return new ArrayList<>(songs);
    }

    public static synchronized String remember(List<IMusic> songs) {
        while (QUEUES.size() >= 32) QUEUES.remove(QUEUES.keySet().iterator().next());
        String token = UUID.randomUUID().toString();
        QUEUES.put(token, List.copyOf(songs));
        return token;
    }

    public static JsonObject song(IMusic music) {
        JsonObject result = new JsonObject();
        result.addProperty("id", music.getId());
        result.addProperty("key", music.getKey());
        result.addProperty("source", music.getSource());
        result.addProperty("sourceId", music.getSourceId());
        result.addProperty("name", music.getName());
        result.addProperty("cover", music.getPicUrl() == null ? "" : music.getPicUrl());
        result.addProperty("duration", music.getDuration());
        result.addProperty("artist", music instanceof Music m && m.artists != null ? Music.getArtistsName(m.artists)
                : music instanceof DjMusic dj ? text(dj.dj, "nickname")
                : music instanceof fengliu.cloudmusic.platform.QqMusic qq ? qq.getArtist() : "");
        result.addProperty("album", music instanceof Music m ? text(m.album, "name")
                : music instanceof fengliu.cloudmusic.platform.QqMusic qq ? qq.getAlbum() : "");
        return result;
    }

    private static String text(JsonObject data, String key) {
        return data != null && data.has(key) && !data.get(key).isJsonNull() ? data.get(key).getAsString() : "";
    }

    private static JsonObject card(JsonObject item, String coverKey, String countKey) {
        JsonObject result = new JsonObject();
        result.addProperty("id", item.get("id").getAsLong());
        result.addProperty("name", text(item, "name"));
        result.addProperty("cover", text(item, coverKey));
        result.addProperty("count", item.has(countKey) ? item.get(countKey).getAsInt() : 0);
        return result;
    }

    public static JsonObject load(String kind, long id, int page) {
        var api = MusicCommand.getMusic163();
        var http = api.getHttpClient();
        int offset = Math.max(0, page - 1) * 30;
        JsonObject result = new JsonObject();
        JsonArray cards = new JsonArray();
        List<IMusic> songs = new ArrayList<>();
        result.addProperty("kind", kind);
        result.addProperty("page", page);
        result.addProperty("hasMore", false);
        switch (kind) {
            case "daily" -> { result.addProperty("name", "每日推荐"); songs = MusicCommand.getMy(false).recommendSongs(); }
            case "fm" -> { result.addProperty("name", "个人 FM"); songs = MusicCommand.getMy(false).fm(); }
            case "recommend" -> {
                result.addProperty("name", "推荐歌单");
                var json = http.POST_API("/api/v1/discovery/recommend/resource", null);
                for (var item : json.getAsJsonArray("recommend")) cards.add(card(item.getAsJsonObject(), "picUrl", "trackCount"));
            }
            case "podcasts" -> {
                result.addProperty("name", "收藏的播客");
                var json = http.POST_API("/api/djradio/get/subed", Map.of("limit", 30, "offset", offset, "total", true));
                for (var item : json.getAsJsonArray("djRadios")) cards.add(card(item.getAsJsonObject(), "picUrl", "programCount"));
                result.addProperty("hasMore", json.has("count") && offset + cards.size() < json.get("count").getAsInt());
            }
            case "podcast" -> {
                result.addProperty("name", "播客节目");
                var json = http.POST_API("/api/dj/program/byradio", Map.of("radioId", id, "limit", 30, "offset", offset, "asc", false));
                for (var item : json.getAsJsonArray("programs")) songs.add(new DjMusic(http, item.getAsJsonObject()));
                result.addProperty("hasMore", json.has("count") && offset + songs.size() < json.get("count").getAsInt());
            }
            case "artist" -> {
                var json = http.POST_API("/api/artist/head/info/get", Map.of("id", id));
                var artist = json.getAsJsonObject("data").getAsJsonObject("artist");
                result.addProperty("name", text(artist, "name"));
                result.addProperty("cover", text(artist, "cover").isEmpty() ? text(artist, "picUrl") : text(artist, "cover"));
                result.addProperty("description", text(artist, "briefDesc"));
                var tracks = http.POST_API("/api/artist/top/song", Map.of("id", id));
                for (var item : tracks.getAsJsonArray("songs")) songs.add(new Music(http, item.getAsJsonObject(), null));
            }
            case "profile", "playlists" -> {
                if (kind.equals("playlists")) id = MusicCommand.getMy(false).id;
                var user = api.user(id);
                result.addProperty("name", kind.equals("playlists") ? "我的歌单与收藏" : user.name);
                result.addProperty("cover", user.avatarUrl);
                result.addProperty("description", user.signature);
                result.addProperty("artistId", user.artistId);
                var json = http.POST_API("/api/user/playlist", Map.of("uid", id, "limit", 30, "offset", offset, "includeVideo", true));
                for (var item : json.getAsJsonArray("playlist")) {
                    var data = item.getAsJsonObject();
                    var playlist = card(data, "coverImgUrl", "trackCount");
                    playlist.addProperty("owned", data.getAsJsonObject("creator").get("userId").getAsLong() == id);
                    cards.add(playlist);
                }
                result.addProperty("hasMore", json.has("more") && json.get("more").getAsBoolean());
            }
            default -> throw new IllegalArgumentException("未知音乐页面");
        }
        result.add("items", cards);
        JsonArray tracks = new JsonArray();
        for (var music : songs) tracks.add(song(music));
        result.add("songs", tracks);
        result.addProperty("queueToken", remember(songs));
        return result;
    }

    private RikkaMusicBrowse() { }
}
