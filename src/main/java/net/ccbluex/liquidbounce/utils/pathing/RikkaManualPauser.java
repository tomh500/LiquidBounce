package net.ccbluex.liquidbounce.utils.pathing;

import baritone.api.BaritoneAPI;
import baritone.api.process.IBaritoneProcess;
import baritone.api.process.PathingCommand;
import baritone.api.process.PathingCommandType;
import net.ccbluex.liquidbounce.features.module.modules.xuanrikka.ModuleRikkaPause;

/** A module-owned pause cannot accidentally release a pause set by a command. */
public final class RikkaManualPauser implements IBaritoneProcess {
    @Override
    public boolean isActive() {
        return ModuleRikkaPause.INSTANCE.getRunning();
    }

    @Override
    public PathingCommand onTick(boolean calcFailed, boolean isSafeToCancel) {
        BaritoneAPI.getProvider().getPrimaryBaritone().getInputOverrideHandler().clearAllKeys();
        return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
    }

    @Override
    public boolean isTemporary() {
        return true;
    }

    @Override
    public void onLostControl() { }

    @Override
    public double priority() {
        return DEFAULT_PRIORITY + 200;
    }

    @Override
    public String displayName0() {
        return "Rikka manual pause";
    }
}
