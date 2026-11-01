package net.ccbluex.liquidbounce.injection.mixins.baritone;

import baritone.api.BaritoneAPI;
import baritone.api.utils.IPlayerContext;
import baritone.utils.BlockBreakHelper;
import net.ccbluex.liquidbounce.features.module.modules.world.packetmine.ModulePacketMine;
import net.ccbluex.liquidbounce.features.module.modules.world.ModuleRikkaMine;
import net.ccbluex.liquidbounce.utils.pathing.RikkaMiningTools;
import net.ccbluex.liquidbounce.utils.pathing.PathingEngine;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Gives PacketMine explicit ownership of automated breaks without intercepting manual clicks. */
@Mixin(value = BlockBreakHelper.class, remap = false)
public abstract class MixinBlockBreakHelper {
    @Shadow @Final private IPlayerContext ctx;
    @Shadow private boolean wasHitting;

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void liquidbounce$breakWithPacketMine(boolean leftClick, CallbackInfo ci) {
        if (ctx != BaritoneAPI.getProvider().getPrimaryBaritone().getPlayerContext()) {
            return;
        }
        HitResult trace = ctx.objectMouseOver();
        if (leftClick && PathingEngine.INSTANCE.isMining()
            && trace instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK
            && ModuleRikkaMine.INSTANCE.isResourceTarget(ctx.world().getBlockState(hit.getBlockPos()).getBlock())) {
            var settings = BaritoneAPI.getSettings();
            int slot = RikkaMiningTools.select(ctx.player().getInventory(),
                ctx.world().getBlockState(hit.getBlockPos()), true, settings.preferSilkTouch.value,
                settings.itemSaver.value, settings.itemSaverThreshold.value);
            if (slot < 0) {
                ModuleRikkaMine.INSTANCE.notifyMissingTool();
                // Wait for inventory provisioning rather than destroy a resource without its drops.
                ModulePacketMine.INSTANCE.handleRikkaBlockBreaking(null);
                wasHitting = false;
                ci.cancel();
                return;
            }
        }
        if (ModulePacketMine.INSTANCE.handleRikkaBlockBreaking(
            leftClick && trace instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK
                ? hit.getBlockPos() : null)) {
            wasHitting = false;
            ci.cancel();
        }
    }

    @Inject(method = "stopBreakingBlock", at = @At("HEAD"))
    private void liquidbounce$stopPacketMine(CallbackInfo ci) {
        if (ctx == BaritoneAPI.getProvider().getPrimaryBaritone().getPlayerContext()) {
            ModulePacketMine.INSTANCE.handleRikkaBlockBreaking(null);
        }
    }
}
