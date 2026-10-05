package fengliu.cloudmusic.config;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RikkaMusicSettingsPolicyTest {
    @Test
    void hotkeyPrefixDoesNotLeakIntoDisplayKeys() {
        assertEquals("next.music", RikkaMusicSettingsPolicy.key("cloudmusic.config.hotkey.next.music"));
        assertEquals("volume", RikkaMusicSettingsPolicy.key("cloudmusic.config.volume"));
    }

    @Test
    void hidesRetiredSettingsAndLegacyOpenShortcutButKeepsWorkingControls() {
        for (String key : new String[]{"hotkey.open.config.gui", "lyric.scale", "music.info.x", "music.player.progress.bar.color", "login.cookie", "stop.play.show.ui"})
            assertFalse(RikkaMusicSettingsPolicy.active("cloudmusic.config." + key), key);
        for (String key : new String[]{"hotkey.next.music", "gui.window.x", "volume", "http.time.out", "play.loop"})
            assertTrue(RikkaMusicSettingsPolicy.active("cloudmusic.config." + key), key);
    }
}
