package net.ccbluex.liquidbounce.utils.pathing;

import baritone.api.BaritoneAPI;
import baritone.api.process.IBaritoneProcess;
import baritone.api.process.PathingCommand;
import baritone.api.process.PathingCommandType;
import net.ccbluex.liquidbounce.features.module.modules.combat.ModuleRikkaKAHelper;
import net.ccbluex.liquidbounce.features.module.modules.combat.killaura.ModuleKillAura;
import net.ccbluex.liquidbounce.utils.combat.CombatManager;
import net.minecraft.client.Minecraft;

/** Temporarily gives combat control without cancelling the underlying mining or pathing process. */
public final class RikkaCombatPauser implements IBaritoneProcess {
    public static boolean shouldPause() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.level != null
            && PathingEngine.INSTANCE.isRikkaAutomationEnabled()
            && (ModuleKillAura.INSTANCE.getEnabled()
                || CombatManager.INSTANCE.isInCombat()
                || ModuleRikkaKAHelper.INSTANCE.hasThreatForPathing());
    }

    @Override
    public boolean isActive() {
        return shouldPause();
    }

    @Override
    public PathingCommand onTick(boolean calcFailed, boolean isSafeToCancel) {
        var baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
        baritone.getInputOverrideHandler().clearAllKeys();
        baritone.getLookBehavior().updateTarget(null, false);
        return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
    }

    @Override
    public boolean isTemporary() {
        return true;
    }

    @Override
    public void onLostControl() {
        // Keep the underlying process intact so it resumes when isActive becomes false.
    }

    @Override
    public double priority() {
        // RikkaKAHelper's threat process uses DEFAULT_PRIORITY + 10.
        return IBaritoneProcess.DEFAULT_PRIORITY + 100;
    }

    @Override
    public String displayName0() {
        return "LiquidBounce combat pause";
    }
}
