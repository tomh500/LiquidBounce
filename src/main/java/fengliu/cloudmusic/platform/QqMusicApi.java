package fengliu.cloudmusic.platform;

import com.google.gson.*;
import fengliu.cloudmusic.music163.IMusic;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;

/** Isolated QQ transport: never forwards the NetEase cookie to another platform. */
public final class QqMusicApi {
    private static final Gson GSON = new Gson();
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    private static volatile String sessionCookie = "";
    private static QqCredentialStore credentialStore;
    private static String sessionMethod = "manual";
    private static String sessionNickname = "";
    private static long sessionExpiresAt;
    private static String storageError = "";
    private static final String GUID = Long.toString(new java.security.SecureRandom().nextLong(1_000_000_000L, 9_999_999_999L));

    public static boolean hasSession() { return !sessionCookie.isEmpty(); }

    public static synchronized void initializeStorage(java.nio.file.Path path) {
        credentialStore = new QqCredentialStore(path);
        sessionCookie = "";
        sessionMethod = "manual";
        sessionNickname = "";
        sessionExpiresAt = 0;
        storageError = "";
        try {
            var data = credentialStore.load();
            if (!data.has("cookie")) return;
            String cookie = QqMusic.text(data, "cookie");
            validateCookie(cookie);
            sessionCookie = cookie;
            sessionMethod = QqMusic.text(data, "method");
            sessionNickname = QqMusic.text(data, "nickname");
            sessionExpiresAt = data.has("expiresAt") ? data.get("expiresAt").getAsLong() : 0;
        } catch (RuntimeException error) {
            storageError = "QQ 登录文件读取失败，原文件已保留，请重新扫码登录";
        }
    }

    public static synchronized JsonObject sessionState() {
        JsonObject result = new JsonObject();
        result.addProperty("configured", hasSession());
        result.addProperty("persisted", credentialStore != null && hasSession());
        result.addProperty("method", sessionMethod);
        result.addProperty("nickname", sessionNickname);
        result.addProperty("expiresAt", sessionExpiresAt);
        result.addProperty("expired", expired());
        result.addProperty("storageError", storageError);
        return result;
    }

    private static synchronized boolean expired() { return sessionExpiresAt > 0 && System.currentTimeMillis() / 1000 >= sessionExpiresAt; }

    public static synchronized void setSession(String cookie) {
        installSession(cookie.trim(), "manual", "", 0);
    }

    private static void validateCookie(String cookie) {
        if (cookie.length() > 16384 || cookie.contains("\r") || cookie.contains("\n"))
            throw new IllegalArgumentException("QQ 凭据格式无效");
        var values = cookies(cookie);
        if (!cookie.isBlank() && (uin(values).isEmpty() || key(values).isEmpty()))
            throw new IllegalArgumentException("QQ 凭据需要包含账号标识和播放票据");
    }

    private static synchronized void installSession(String cookie, String method, String nickname, long expiresAt) {
        validateCookie(cookie);
        if (credentialStore != null) {
            if (cookie.isEmpty()) credentialStore.clear();
            else credentialStore.save(cookie, method, nickname, expiresAt);
        }
        sessionCookie = cookie;
        sessionMethod = method;
        sessionNickname = nickname;
        sessionExpiresAt = expiresAt;
        storageError = "";
    }

    public static synchronized void installLogin(JsonObject data, String method) {
        String id = QqMusic.text(data, "musicid");
        if (id.isBlank() || id.equals("0")) id = QqMusic.text(data, "str_musicid");
        String key = QqMusic.text(data, "musickey");
        if (!id.matches("[1-9][0-9]*") || key.isBlank() || key.matches(".*[\\s;\\r\\n].*"))
            throw new IllegalStateException("平台未返回有效的 QQ 音乐播放凭据，请重新扫码");
        String cookie = "uin=" + id + "; qqmusic_uin=" + id + "; qqmusic_key=" + key + "; qm_keyst=" + key
                + "; login_type=" + (method.equals("wx") ? "2; wxuin=" + id + "; wxskey=" + key : "1");
        long created = data.has("musickeyCreateTime") ? data.get("musickeyCreateTime").getAsLong() : System.currentTimeMillis() / 1000;
        long lifetime = data.has("keyExpiresIn") ? data.get("keyExpiresIn").getAsLong() : 0;
        long expires = created > 0 && lifetime > 0 ? created + lifetime : 0;
        String nickname = QqMusic.text(data, "nick");
        if (nickname.isBlank()) nickname = QqMusic.text(data, "nickname");
        installSession(cookie, method, nickname, expires);
    }

    private static Map<String, String> cookies(String cookie) {
        Map<String, String> result = new HashMap<>();
        for (String part : cookie.split(";")) {
            String[] entry = part.trim().split("=", 2);
            if (entry.length == 2) result.put(entry[0], entry[1]);
        }
        return result;
    }

    private static String uin(Map<String, String> values) {
        String value = values.getOrDefault("uin", values.getOrDefault("qqmusic_uin", values.getOrDefault("wxuin", "")));
        if ("2".equals(values.get("login_type"))) value = values.getOrDefault("wxuin", value);
        return value.replaceFirst("^o0*", "");
    }
    private static String key(Map<String, String> values) {
        return values.getOrDefault("qm_keyst", values.getOrDefault("qqmusic_key", values.getOrDefault("wxskey", "")));
    }

    private static JsonObject request(String module, String method, Map<String, ?> params, boolean playback) {
        if (playback && expired()) throw new IllegalStateException("QQ 音乐登录已过期，请重新扫码登录");
        String cookie = expired() ? "" : sessionCookie;
        var credentials = cookies(cookie);
        Map<String, Object> comm = new LinkedHashMap<>();
        comm.put("ct", playback && cookie.isEmpty() ? 24 : 19);
        comm.put("cv", playback ? 0 : 1859);
        comm.put("uin", uin(credentials).isEmpty() ? "0" : uin(credentials));
        comm.put("format", "json");
        if (!key(credentials).isEmpty()) {
            comm.put("authst", key(credentials));
            comm.put("tmeLoginType", "2".equals(credentials.get("login_type")) || key(credentials).startsWith("W_X") ? 1 : 2);
        }
        String body = GSON.toJson(Map.of("comm", comm, "req_0", Map.of("module", module, "method", method, "param", params)));
        var builder = HttpRequest.newBuilder(URI.create("https://u.y.qq.com/cgi-bin/musicu.fcg"))
                .timeout(Duration.ofSeconds(15)).header("Referer", "https://y.qq.com/")
                .header("User-Agent", "Mozilla/5.0").header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (!cookie.isEmpty()) builder.header("Cookie", cookie);
        try {
            var response = HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) throw new IllegalStateException("QQ 音乐暂不可用 (HTTP " + response.statusCode() + ")");
            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
            JsonObject block = json.getAsJsonObject("req_0");
            if (block == null || block.get("code").getAsInt() != 0) {
                String code = block == null ? "未知" : block.get("code").getAsString();
                throw new IllegalStateException(playback ? "QQ 未提供播放地址，请检查 QQ 播放凭据及歌曲权限 (" + code + ")"
                        : "QQ 音乐请求失败 (" + code + ")");
            }
            return block.getAsJsonObject("data");
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("QQ 音乐请求已取消");
        } catch (java.io.IOException | JsonParseException error) {
            // Do not attach transport payloads/headers or signed URLs to exceptions/logs.
            throw new IllegalStateException("QQ 音乐连接失败，请稍后重试");
        }
    }

    public record Search(List<IMusic> songs, int total) { }
    public static Search search(String query, int page, int limit) {
        JsonObject data = request("music.search.SearchCgiService", "DoSearchForQQMusicDesktop",
                Map.of("query", query, "page_num", page, "num_per_page", limit, "search_type", 0), false);
        List<IMusic> songs = new ArrayList<>();
        for (var item : data.getAsJsonObject("body").getAsJsonObject("song").getAsJsonArray("list"))
            songs.add(new QqMusic(item.getAsJsonObject()));
        return new Search(songs, data.getAsJsonObject("meta").get("sum").getAsInt());
    }

    public static List<IMusic> playlist(long id) {
        List<IMusic> songs = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int offset = 0; offset < 10000; offset += 100) {
            JsonObject data = request("music.srfDissInfo.DissInfo", "CgiGetDiss",
                    Map.of("disstid", id, "dirid", 0, "tag", true, "song_begin", offset, "song_num", 100,
                            "userinfo", true, "orderlist", true, "onlysonglist", false), false);
            var list = data.getAsJsonArray("songlist");
            if (list == null) throw new IllegalStateException("QQ 歌单不可访问");
            for (var item : list) {
                QqMusic song = new QqMusic(item.getAsJsonObject());
                if (!seen.add(song.getKey())) throw new IllegalStateException("QQ 歌单分页重复，请稍后重试");
                songs.add(song);
            }
            long total = data.has("total_song_num") ? data.get("total_song_num").getAsLong() : 0;
            if (total > 0 && songs.size() >= total || total == 0 && list.size() < 100) return songs;
            if (list.isEmpty()) throw new IllegalStateException("QQ 歌单返回不完整，未导入");
        }
        throw new IllegalArgumentException("实验版单次导入最多支持 10000 首歌曲");
    }

    public static String playUrl(QqMusic song) {
        var credentials = cookies(sessionCookie);
        String account = uin(credentials).isEmpty() ? "0" : uin(credentials);
        JsonObject data = request("vkey.GetVkeyServer", "CgiGetVkey",
                Map.of("guid", GUID, "songmid", List.of(song.getSourceId()), "songtype", List.of(0), "uin", account,
                        "loginflag", 1, "platform", "20", "filename", List.of("M500" + song.getMediaMid() + ".mp3")), true);
        JsonArray infos = data.getAsJsonArray("midurlinfo");
        if (infos != null && !infos.isEmpty()) {
            String path = QqMusic.text(infos.get(0).getAsJsonObject(), "purl");
            if (!path.isBlank()) {
                JsonArray hosts = data.getAsJsonArray("sip");
                String host = hosts != null && !hosts.isEmpty() ? hosts.get(0).getAsString() : "https://ws.stream.qqmusic.qq.com/";
                URI uri = URI.create(host.replaceFirst("^http:", "https:")).resolve(path);
                String domain = uri.getHost();
                if ("https".equals(uri.getScheme()) && domain != null &&
                        (domain.endsWith(".qq.com") || domain.endsWith(".qqmusic.qq.com"))) return uri.toString();
            }
        }
        throw new IllegalStateException("QQ 未提供可播放音源，请检查 QQ 播放凭据及歌曲权限");
    }

    private QqMusicApi() { }
}
