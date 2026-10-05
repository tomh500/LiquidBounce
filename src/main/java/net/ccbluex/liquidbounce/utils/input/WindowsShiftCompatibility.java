package net.ccbluex.liquidbounce.utils.input;

import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.sdl.SDLKeyboard;
import org.lwjgl.sdl.SDLEvents;
import org.lwjgl.sdl.SDLKeycode;
import org.lwjgl.sdl.SDLVideo;
import org.lwjgl.sdl.SDL_KeyboardEvent;
import org.lwjgl.sdl.SDL_Event;
import org.lwjgl.system.Platform;
import org.slf4j.LoggerFactory;

/** Backports SDL's Windows E0 36 right Shift mapping (MC-311424). */
public final class WindowsShiftCompatibility {
    private static boolean recoveredShiftDown;
    private static int recoveredWindow;
    private static boolean reportedRecovery;

    private WindowsShiftCompatibility() { }

    public static void repair(SDL_KeyboardEvent event) {
        if (Platform.get() != Platform.WINDOWS) {
            return;
        }
        boolean recovered = event.scancode() == 0 && event.key() == 0
            && (Short.toUnsignedInt(event.raw()) == 0xE036 || Short.toUnsignedInt(event.raw()) == 0x36);
        if (recovered) {
            if (!reportedRecovery) {
                LoggerFactory.getLogger("LiquidBounce/Keyboard").info(
                    "Recovered Windows right Shift from raw scancode 0x{} (MC-311424)",
                    Integer.toHexString(Short.toUnsignedInt(event.raw())));
                reportedRecovery = true;
            }
            event.scancode(InputConstants.KEY_RSHIFT);
            event.key(SDLKeycode.SDLK_RSHIFT);
            recoveredShiftDown = event.down();
            recoveredWindow = event.windowID();
        } else if (event.scancode() == InputConstants.KEY_RSHIFT && !event.down()) {
            recoveredShiftDown = false;
        }

        // SDL's native state still associates the original event with UNKNOWN.
        // Keep keyboard combinations consistent with the recovered physical key.
        if (isPressed()) {
            event.mod((short) (event.mod() | SDLKeycode.SDL_KMOD_RSHIFT));
        } else if (recovered) {
            event.mod((short) (event.mod() & ~SDLKeycode.SDL_KMOD_RSHIFT));
        }
    }

    public static boolean isPressed() {
        if (!recoveredShiftDown) {
            return false;
        }
        long focus = SDLKeyboard.SDL_GetKeyboardFocus();
        if (focus == 0 || SDLVideo.SDL_GetWindowID(focus) != recoveredWindow) {
            recoveredShiftDown = false;
        }
        return recoveredShiftDown;
    }

    public static void windowEvent(SDL_Event event) {
        if ((event.type() == SDLEvents.SDL_EVENT_WINDOW_FOCUS_LOST
            || event.type() == SDLEvents.SDL_EVENT_WINDOW_DESTROYED)
            && event.window().windowID() == recoveredWindow) {
            recoveredShiftDown = false;
        }
    }
}
