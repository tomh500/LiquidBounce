package fengliu.cloudmusic.config;

import java.util.Set;

/** Public setting identifiers are independent of malilib's legacy name prefixes. */
public final class RikkaMusicSettingsPolicy {
    private static final Set<String> RETIRED = Set.of("login.cookie", "open.config.gui", "stop.play.show.ui", "gui.draggable.window");

    public static String key(String name) {
        String key = name.startsWith("cloudmusic.config.") ? name.substring("cloudmusic.config.".length()) : name;
        return key.startsWith("hotkey.") ? key.substring("hotkey.".length()) : key;
    }

    public static boolean active(String name) {
        String key = key(name);
        return !RETIRED.contains(key) && !key.startsWith("music.info") && !key.startsWith("music.progress")
                && !key.startsWith("music.player.progress") && !key.startsWith("lyric.");
    }

    private RikkaMusicSettingsPolicy() { }
}
