package net.ccbluex.liquidbounce.features.module.modules.combat;

/** Tracks local damage edges independently of whether the crosshair currently has a target. */
public final class TriggerbotFirstHitGate {
    private int previousHurtTime;
    private boolean justHurt;
    private int targetId = -1;
    private boolean waiting;

    public void observeDamage(int hurtTime) {
        justHurt = previousHurtTime <= 0 && hurtTime > 0;
        previousHurtTime = hurtTime;
    }

    public boolean permits(int target, boolean enabled) {
        if (!enabled) {
            resetTarget();
            return true;
        }
        if (targetId != target) {
            targetId = target;
            waiting = true;
        }
        if (justHurt) {
            waiting = false;
        }
        return !waiting;
    }

    public void resetTarget() {
        targetId = -1;
        waiting = false;
    }

    public void reset(int currentHurtTime) {
        resetTarget();
        previousHurtTime = currentHurtTime;
        justHurt = false;
    }
}
