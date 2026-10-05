package fengliu.cloudmusic.music163;

import com.google.gson.JsonParser;
import fengliu.cloudmusic.music163.data.PlayList;
import fengliu.cloudmusic.util.HttpClient;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlayListSummaryTest {
    @Test
    void acceptsUserPlaylistSummariesWithoutTracksDescriptionOrTags() {
        var summary = JsonParser.parseString("""
                {"id":1,"name":"My list","coverImgUrl":"cover","trackCount":8,
                 "playCount":12,"creator":{"userId":2,"nickname":"Listener"}}
                """).getAsJsonObject();
        var playlist = new PlayList(new HttpClient("https://music.163.com"), summary);
        assertEquals(8, playlist.count);
        assertTrue(playlist.tags.isEmpty());
        assertTrue(playlist.getMusics().isEmpty());
    }
}
