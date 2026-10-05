package net.ccbluex.liquidbounce.injection.mixins.xuanrikka;

import net.ccbluex.liquidbounce.integration.compat.FrameRetirementQueue;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class MixinBrowserFrameRetirement {
    @Inject(method = "render", at = @At("TAIL"))
    private void retireFinishedBrowserFrames(CallbackInfo ci) {
        FrameRetirementQueue.BROWSERS.endFrame();
    }
}
