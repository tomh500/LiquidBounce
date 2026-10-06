package fengliu.cloudmusic.platform;

import com.google.gson.*;
import fengliu.cloudmusic.music163.IMusic;
import fengliu.cloudmusic.music163.data.Music;
import fengliu.cloudmusic.util.HttpClient;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** A local collection, independent of either account. All writes replace the file atomically. */
public final class MixedMusicLibrary {
    private final Path file;
    private final java.util.function.Supplier<HttpClient> neteaseClient;
    private LinkedHashMap<String, IMusic> songs;

    public MixedMusicLibrary(Path file, java.util.function.Supplier<HttpClient> neteaseClient) {
        this.file = file;
        this.neteaseClient = neteaseClient;
    }

    public synchronized List<IMusic> songs() {
        load();
        List<IMusic> snapshot = new ArrayList<>();
        for (IMusic song : songs.values()) {
            // Rebind NetEase entries after login/logout; never retain an old account's transport.
            snapshot.add(song instanceof Music ? new Music(neteaseClient.get(), metadata(song), null) : song);
        }
        return snapshot;
    }

    public synchronized int add(List<IMusic> additions) {
        load();
        var updated = new LinkedHashMap<>(songs);
        for (IMusic song : additions) {
            if (!(song instanceof Music) && !(song instanceof QqMusic))
                throw new IllegalArgumentException("混合曲库目前支持网易云单曲和 QQ 音乐");
            updated.putIfAbsent(song.getKey(), song);
        }
        if (updated.size() > 10000) throw new IllegalArgumentException("实验版曲库最多支持 10000 首歌曲");
        int added = updated.size() - songs.size();
        save(updated);
        return added;
    }

    public synchronized void remove(String key) {
        load();
        var updated = new LinkedHashMap<>(songs);
        updated.remove(key);
        save(updated);
    }

    private void load() {
        if (songs != null) return;
        var loaded = new LinkedHashMap<String, IMusic>();
        if (Files.exists(file)) {
            try {
                var root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                if (root.get("version").getAsInt() != 1) throw new IllegalArgumentException("Unsupported version");
                for (var entry : root.getAsJsonArray("songs")) {
                    JsonObject data = entry.getAsJsonObject();
                    String source = data.get("source").getAsString();
                    IMusic song = switch (source) {
                        case "qq" -> new QqMusic(data.getAsJsonObject("metadata"));
                        case "netease" -> new Music(neteaseClient.get(), data.getAsJsonObject("metadata"), null);
                        default -> throw new IllegalArgumentException("Unknown source");
                    };
                    loaded.put(song.getKey(), song);
                }
            } catch (IOException | RuntimeException error) {
                throw new IllegalStateException("混合曲库读取失败，原文件已保留，请检查 rikkamusic-library.json");
            }
        }
        songs = loaded;
    }

    private void save(LinkedHashMap<String, IMusic> updated) {
        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        JsonArray entries = new JsonArray();
        for (IMusic song : updated.values()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("source", song.getSource());
            entry.add("metadata", metadata(song));
            entries.add(entry);
        }
        root.add("songs", entries);
        Path temp = null;
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            temp = Files.createTempFile(file.toAbsolutePath().getParent(), "rikkamusic-library-", ".tmp");
            Files.writeString(temp, new GsonBuilder().setPrettyPrinting().create().toJson(root));
            Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            songs = updated;
        } catch (IOException error) {
            throw new IllegalStateException("混合曲库保存失败，原曲库未更改");
        } finally {
            if (temp != null) try { Files.deleteIfExists(temp); } catch (IOException ignored) { }
        }
    }

    private static JsonObject metadata(IMusic song) {
        if (song instanceof QqMusic qq) return qq.metadata();
        Music music = (Music) song;
        JsonObject data = new JsonObject();
        data.addProperty("id", music.getId());
        data.addProperty("name", music.getName());
        data.addProperty("dt", music.getDuration());
        data.add("alia", new JsonArray());
        data.add("ar", music.artists.deepCopy());
        JsonObject album = music.album.deepCopy();
        if (music.getPicUrl() != null) album.addProperty("picUrl", music.getPicUrl());
        data.add("al", album);
        return data;
    }
}
