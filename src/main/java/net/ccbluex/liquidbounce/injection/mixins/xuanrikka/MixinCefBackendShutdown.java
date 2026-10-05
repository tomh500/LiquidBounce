package net.ccbluex.liquidbounce.injection.mixins.xuanrikka;

import net.ccbluex.liquidbounce.integration.compat.FrameRetirementQueue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.ccbluex.liquidbounce.cef.backend.CefBrowserBackend", remap = false)
public abstract class MixinCefBackendShutdown {
    @Inject(method = "stop", at = @At("HEAD"))
    private void flushRetiredBrowsers(CallbackInfo ci) {
        FrameRetirementQueue.BROWSERS.flush();
    }
}
