package net.ccbluex.liquidbounce.injection.mixins.baritone;

import baritone.utils.ToolSet;
import baritone.api.BaritoneAPI;
import net.ccbluex.liquidbounce.features.module.modules.world.ModuleRikkaMine;
import net.ccbluex.liquidbounce.utils.pathing.RikkaMiningTools;
import net.ccbluex.liquidbounce.utils.pathing.PathingEngine;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Give RikkaMine a harvest-aware tool policy for both path planning and execution. */
@Mixin(value = ToolSet.class, remap = false)
public abstract class MixinToolSet {
    @Shadow @Final private LocalPlayer player;

    @Inject(method = "getBestSlot(Lnet/minecraft/world/level/block/Block;ZZZ)I",
        at = @At("HEAD"), cancellable = true)
    private void liquidbounce$chooseMiningTool(Block block, boolean preferSilkTouch,
                                               boolean pathingCalculation, boolean preferFastest,
                                               CallbackInfoReturnable<Integer> cir) {
        if (!ModuleRikkaMine.INSTANCE.getEnabled() || !PathingEngine.INSTANCE.isMining()
            || player != BaritoneAPI.getProvider().getPrimaryBaritone().getPlayerContext().player()) return;
        var settings = BaritoneAPI.getSettings();
        int slot = RikkaMiningTools.select(player.getInventory(), block.defaultBlockState(),
            ModuleRikkaMine.INSTANCE.isResourceTarget(block), preferSilkTouch,
            settings.itemSaver.value, settings.itemSaverThreshold.value);
        // No harvestable tool is handled at the break boundary; ToolSet callers require a valid slot.
        cir.setReturnValue(slot < 0 ? player.getInventory().getSelectedSlot() : slot);
    }
}
