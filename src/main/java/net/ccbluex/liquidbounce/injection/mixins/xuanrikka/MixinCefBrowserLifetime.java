package net.ccbluex.liquidbounce.injection.mixins.xuanrikka;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.ccbluex.liquidbounce.integration.compat.FrameRetirementQueue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

/** Adapts the bundled CEF implementation without keeping a fork of its browser classes. */
@Pseudo
@Mixin(targets = "net.ccbluex.liquidbounce.cef.browser.CefBackedBrowser", remap = false)
public abstract class MixinCefBrowserLifetime {
    @WrapOperation(method = "close", at = @At(value = "INVOKE",
        target = "Lnet/ccbluex/liquidbounce/cef/browser/CefOffscreenBrowser;close()V"))
    private void retireBrowser(@Coerce Object browser, Operation<Void> original) {
        // Upstream still unregisters rendering and input immediately. Only native resources wait.
        FrameRetirementQueue.BROWSERS.defer(() -> original.call(browser));
    }

    @WrapOperation(method = "close", at = @At(value = "INVOKE",
        target = "Lorg/cef/browser/CefRequestContext;dispose()V"))
    private void retireContext(@Coerce Object context, Operation<Void> original) {
        // FIFO order keeps the incognito context alive until its browser has closed.
        FrameRetirementQueue.BROWSERS.defer(() -> original.call(context));
    }

    @WrapOperation(method = "mouseReleased", at = @At(value = "INVOKE",
        target = "Lnet/ccbluex/liquidbounce/cef/browser/CefOffscreenBrowser;setFocus(Z)V"))
    private void keepFocusOnRelease(@Coerce Object browser, boolean focused, Operation<Void> original) {
        // A release belonging to the screen being closed must not steal focus back from the game.
        // Upstream retains focus acquisition on mouse press and keyboard input.
    }
}
