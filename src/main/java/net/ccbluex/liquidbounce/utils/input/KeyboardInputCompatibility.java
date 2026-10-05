package net.ccbluex.liquidbounce.utils.input;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.sdl.SDLKeycode;

/** Recovers a modifier's physical key when SDL supplies its keycode but no scancode. */
public final class KeyboardInputCompatibility {
    private KeyboardInputCompatibility() { }

    public static InputConstants.Key key(KeyEvent event) {
        if (event.key() == InputConstants.UNKNOWN.getValue()) {
            int keycode = event.keycode();
            if ((keycode & SDLKeycode.SDLK_SCANCODE_MASK) != 0) {
                int scanCode = keycode & ~SDLKeycode.SDLK_SCANCODE_MASK;
                if (scanCode >= InputConstants.KEY_LCONTROL && scanCode <= InputConstants.KEY_RGUI) {
                    return InputConstants.Type.KEYBOARD.getOrCreate(scanCode);
                }
            }
        }
        return InputConstants.getKey(event);
    }
}
