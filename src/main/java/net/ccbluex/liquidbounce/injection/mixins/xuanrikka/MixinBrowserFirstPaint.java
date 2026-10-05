package net.ccbluex.liquidbounce.injection.mixins.xuanrikka;

import net.ccbluex.liquidbounce.integration.backend.browser.Browser;
import net.ccbluex.liquidbounce.integration.backend.browser.BrowserRenderer;
import net.ccbluex.liquidbounce.integration.backend.browser.BrowserState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BrowserRenderer.class, remap = false)
public abstract class MixinBrowserFirstPaint {
    @Shadow @Final private Browser browser;
    @Unique private BrowserState xuanrikka$lastState;

    @Inject(method = "render", at = @At("HEAD"))
    private void refreshCompletedPage(CallbackInfo ci) {
        if (!browser.getClass().getName().equals("net.ccbluex.liquidbounce.cef.browser.CefBackedBrowser")) {
            return;
        }
        BrowserState state = browser.getState();
        if (state != xuanrikka$lastState) {
            xuanrikka$lastState = state;
            if (state instanceof BrowserState.Success) {
                // Use the public browser contract; do not reach into CEF's load callbacks or buffers.
                browser.invalidate();
            }
        }
    }
}
