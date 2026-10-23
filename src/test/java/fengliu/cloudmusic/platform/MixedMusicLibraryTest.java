package fengliu.cloudmusic.platform;

import com.google.gson.JsonParser;
import fengliu.cloudmusic.music163.data.Music;
import fengliu.cloudmusic.util.HttpClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class MixedMusicLibraryTest {
    static { net.ccbluex.liquidbounce.test.MinecraftBootstrap.INSTANCE.ensureInitialized(); }
    @TempDir Path directory;
    private MixedMusicLibrary library() {
        return new MixedMusicLibrary(directory.resolve("library.json"), () -> new HttpClient("https://music.163.com"));
    }
    static QqMusic qq() {
        return new QqMusic(JsonParser.parseString("""
                {"id":42,"mid":"TestMid123","title":"Same title","interval":180,
                 "album":{"name":"Album","mid":"Album123"},"singer":[{"name":"Singer"}],
                 "file":{"media_mid":"Media123"},"url":"https://invalid.test/?token=secret"}
                """).getAsJsonObject());
    }
    static Music netease() {
        return new Music(new HttpClient("https://music.163.com"), JsonParser.parseString("""
                {"id":42,"name":"Same title","dt":180000,"alia":[],"ar":[{"name":"Singer"}],
                 "al":{"name":"Album","picUrl":"https://invalid.test/cover.jpg"}}
                """).getAsJsonObject(), null);
    }

    @Test void sameNumericIdAcrossPlatformsSurvivesDeduplicationAndRestart() throws Exception {
        var library = library();
        assertEquals(2, library.add(List.of(netease(), qq(), qq())));
        assertEquals(0, library.add(List.of(qq())));
        var restored = library().songs();
        assertEquals(List.of("netease:42", "qq:TestMid123"), restored.stream().map(s -> s.getKey()).toList());
        assertEquals(180000, restored.get(0).getDuration());
        assertEquals(180000, restored.get(1).getDuration());
        assertEquals("Singer", ((QqMusic) restored.get(1)).getArtist());
        assertEquals("Media123", ((QqMusic) restored.get(1)).getMediaMid());
        assertFalse(Files.readString(directory.resolve("library.json")).contains("secret"));
        library.remove("qq:TestMid123");
        assertEquals(List.of("netease:42"), library().songs().stream().map(s -> s.getKey()).toList());
    }

    @Test void damagedLibraryIsNeverReplacedByAnEmptyOrPartialCollection() throws Exception {
        var path = directory.resolve("library.json");
        Files.writeString(path, "broken original");
        assertThrows(IllegalStateException.class, () -> library().add(List.of(qq())));
        assertEquals("broken original", Files.readString(path));
    }

    @Test void failedSaveDoesNotChangeTheInMemoryCollection() throws Exception {
        var library = library();
        library.add(List.of(qq()));
        Files.delete(directory.resolve("library.json"));
        Files.createDirectory(directory.resolve("library.json"));
        Files.writeString(directory.resolve("library.json/keep"), "keep");
        assertThrows(IllegalStateException.class, () -> library.remove(qq().getKey()));
        assertEquals(1, library.songs().size());
    }

    @Test void acceptsOnlyTheSelectedPlatformsPlaylistReferences() {
        assertEquals(123, MusicPlatformService.playlistId("qq", "https://y.qq.com/n/ryqq/playlist/123"));
        assertEquals(456, MusicPlatformService.playlistId("netease", "https://music.163.com/#/playlist?id=456"));
        assertThrows(IllegalArgumentException.class, () -> MusicPlatformService.playlistId("qq", "https://music.163.com/playlist?id=123"));
        assertThrows(IllegalArgumentException.class, () -> MusicPlatformService.playlistId("qq", "https://y.qq.com.evil.test/playlist/123"));
        assertThrows(IllegalArgumentException.class, () -> MusicPlatformService.playlistId("netease", "https://music.163.com/song?id=123"));
    }
}
