package fengliu.cloudmusic.music163;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LyricTest {
    @Test
    void sortsRepeatedTimestampsAndDoesNotShowFutureLyrics() {
        var lyric = Lyric.fromLrc("[00:10.000][00:20.000]repeat\n[00:05.000]first");
        assertEquals("", lyric.snapshot(4999, 30000).original());
        assertEquals("first", lyric.snapshot(5000, 30000).original());
        assertEquals("repeat", lyric.snapshot(20000, 30000).original());
        assertArrayEquals(new long[]{5000, 10000}, lyric.getWindowTimes(6000, 0, 1));
    }

    @Test
    void longLinesUseFullIntervalAndSeekResamplesTextAndHighlightTogether() {
        var lyric = Lyric.fromLrc("[00:01.000]long line\n[00:11.000]last");
        assertEquals(.5f, lyric.snapshot(6000, 21000).progress());
        assertEquals(.5f, lyric.snapshot(16000, 21000).progress());
        var backward = lyric.snapshot(1000, 21000);
        assertEquals("long line", backward.original());
        assertEquals(0f, backward.progress());
        assertEquals(1f, lyric.snapshot(22000, 21000).progress());
    }

    @Test
    void parsesFractionalSecondsAndBlankInterludes() {
        assertEquals(12300, Lyric.timeStrToTime("00:12.3"));
        assertEquals(12340, Lyric.timeStrToTime("00:12.34"));
        assertEquals(12345, Lyric.timeStrToTime("00:12.345"));
        var lyric = Lyric.fromLrc("[00:01.000]voice\n[00:02.000]\n[00:03.000]next");
        assertEquals("", lyric.snapshot(2500, 4000).original());
    }
}
