package net.ccbluex.liquidbounce.injection.mixins.xuanrikka;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.blaze3d.platform.InputConstants;
import net.ccbluex.liquidbounce.utils.input.WindowsShiftCompatibility;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(InputConstants.class)
public abstract class MixinWindowsShiftState {
    @ModifyReturnValue(method = "isKeyDown", at = @At("RETURN"))
    private static boolean recoveredRightShift(boolean original, int scanCode) {
        return original || scanCode == InputConstants.KEY_RSHIFT && WindowsShiftCompatibility.isPressed();
    }
}
