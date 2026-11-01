package net.ccbluex.liquidbounce.injection.mixins.baritone;

import baritone.api.BaritoneAPI;
import baritone.Baritone;
import baritone.api.event.events.TickEvent;
import baritone.behavior.InventoryBehavior;
import net.ccbluex.liquidbounce.features.module.modules.world.ModuleRikkaMine;
import net.ccbluex.liquidbounce.utils.pathing.RikkaMiningTools;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Provision a harvest-capable inventory tool when Baritone's stone-based inventory policy cannot. */
@Mixin(value = InventoryBehavior.class, remap = false)
public abstract class MixinInventoryBehavior {
    @Inject(method = "onTick", at = @At("TAIL"))
    private void liquidbounce$provisionResourceTool(TickEvent event, CallbackInfo ci) {
        if (event.getType() != TickEvent.Type.IN || !ModuleRikkaMine.INSTANCE.getRunning()) return;
        var settings = BaritoneAPI.getSettings();
        if (!settings.allowInventory.value) return;

        var baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
        if ((Object) this != ((Baritone) baritone).getInventoryBehavior()) return;
        if (!baritone.getMineProcess().isActive()) return;
        var context = baritone.getPlayerContext();
        if (context.player().containerMenu != context.player().inventoryMenu) return;
        var target = context.getSelectedBlock();
        if (target.isEmpty()) return;
        BlockState state = context.world().getBlockState(target.get());
        if (!ModuleRikkaMine.INSTANCE.isResourceTarget(state.getBlock())) return;

        var inventory = context.player().getInventory();
        int reserve = settings.itemSaverThreshold.value;
        if (RikkaMiningTools.select(inventory, state, true, settings.preferSilkTouch.value,
            settings.itemSaver.value, reserve) >= 0) return;
        int source = RikkaMiningTools.selectInventory(inventory, state, true,
            settings.preferSilkTouch.value, settings.itemSaver.value, reserve);
        if (source >= 0) {
            ((InventoryBehavior) (Object) this).attemptToPutOnHotbar(source, slot -> false);
        }
    }
}
