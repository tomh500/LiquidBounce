package net.ccbluex.liquidbounce.injection.mixins.xuanrikka;

import com.mojang.blaze3d.platform.SDLEventHandler;
import com.llamalad7.mixinextras.sugar.Local;
import net.ccbluex.liquidbounce.utils.input.WindowsShiftCompatibility;
import org.lwjgl.sdl.SDL_Event;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Repair before MaLiLib and vanilla copy the native event into a KeyEvent.
@Mixin(value = SDLEventHandler.class, priority = 1100)
public abstract class MixinWindowsShiftInput {
    @Inject(method = "handleKeyEvent", at = @At("HEAD"))
    private void repairRightShift(SDL_Event event, CallbackInfo ci) {
        WindowsShiftCompatibility.repair(event.key());
    }

    @Inject(method = "pollEvents", at = @At(value = "INVOKE",
        target = "Lcom/mojang/blaze3d/platform/Window;handleEvent(Lorg/lwjgl/sdl/SDL_Event;)V"))
    private void clearShiftOnFocusLoss(CallbackInfo ci, @Local SDL_Event event) {
        WindowsShiftCompatibility.windowEvent(event);
    }
}
