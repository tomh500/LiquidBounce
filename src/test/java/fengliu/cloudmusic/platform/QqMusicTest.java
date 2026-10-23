package fengliu.cloudmusic.platform;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class QqMusicTest {
    @Test void acceptsLegacyPlaylistMetadataAndKeepsTheVersionTitle() {
        var song = new QqMusic(JsonParser.parseString("""
                {"songid":42,"songmid":"Mid123","songname":"Song (Live)","interval":200,
                 "albumname":"Album","albummid":"Album123","singer":[{"name":"A"},{"name":"B"}]}
                """).getAsJsonObject());
        assertEquals("Song (Live)", song.getName());
        assertEquals("A / B", song.getArtist());
        assertEquals("Album", song.getAlbum());
        assertEquals("qq:Mid123", song.getKey());
        assertTrue(song.getPicUrl().endsWith("Album123.jpg"));
    }
    @Test void credentialsRejectHeaderInjectionAndCanBeCleared() {
        try {
            QqMusicApi.setSession("uin=123; qm_keyst=test-only-not-a-real-secret");
            assertTrue(QqMusicApi.hasSession());
            assertThrows(IllegalArgumentException.class, () -> QqMusicApi.setSession("uin=123\r\nOther: header"));
            assertThrows(IllegalArgumentException.class, () -> QqMusicApi.setSession("uin=123"));
        } finally { QqMusicApi.setSession(""); }
        assertFalse(QqMusicApi.hasSession());
    }
}
