package net.ccbluex.liquidbounce.utils.input;

import java.util.Map;
import java.util.Set;

/** Command aliases stay separate from Minecraft's canonical names used in saved bindings. */
public final class InputKeyNames {
    private static final String KEYBOARD_PREFIX = "key.keyboard.";
    private static final Map<String, String> ALIASES = Map.ofEntries(
        Map.entry("rshift", "right.shift"), Map.entry("rightshift", "right.shift"),
        Map.entry("lshift", "left.shift"), Map.entry("leftshift", "left.shift"),
        Map.entry("rctrl", "right.control"), Map.entry("rcontrol", "right.control"),
        Map.entry("lctrl", "left.control"), Map.entry("lcontrol", "left.control"),
        Map.entry("ralt", "right.alt"), Map.entry("lalt", "left.alt"),
        Map.entry("rwin", "right.win"), Map.entry("lwin", "left.win")
    );

    private InputKeyNames() { }

    public static String canonicalName(String translationKey) {
        if (!translationKey.startsWith(KEYBOARD_PREFIX)) {
            return translationKey;
        }
        String name = translationKey.substring(KEYBOARD_PREFIX.length());
        return KEYBOARD_PREFIX + ALIASES.getOrDefault(name, name);
    }

    public static Set<String> aliases() {
        return ALIASES.keySet();
    }
}
