/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package net.ccbluex.liquidbounce.features.module.modules.world.clutch;

import net.ccbluex.liquidbounce.utils.entity.FallingPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClutchTargetPlannerTest {

    @Test
    void risingJumpPlansTheFutureDescendingCatchInsteadOfAnIntersectingBlock() {
        var frames = List.of(
            frame(0, 0.5, 2.0, 0.5, 2.4),
            frame(1, 0.5, 2.4, 0.5, 2.1),
            frame(2, 0.5, 2.1, 0.5, 1.9)
        );

        assertEquals(List.of(new ClutchTargetPlanner.CatchTarget(new BlockPos(0, 1, 0), 3)),
            ClutchTargetPlanner.catchTargets(frames, 0.6));
    }

    @Test
    void fastFallChecksEverySurfaceCrossedDuringTheImpactTick() {
        var targets = ClutchTargetPlanner.catchTargets(List.of(frame(2, 0.5, 5.2, 0.5, 2.7)), 0.6);

        assertEquals(List.of(new BlockPos(0, 4, 0), new BlockPos(0, 3, 0), new BlockPos(0, 2, 0)),
            targets.stream().map(ClutchTargetPlanner.CatchTarget::pos).toList());
        assertTrue(targets.stream().allMatch(target -> target.ticksUntilImpact() == 3));
    }

    @Test
    void catchUsesFootprintBeforeHorizontalMovementBecauseVerticalCollisionRunsFirst() {
        var targets = ClutchTargetPlanner.catchTargets(List.of(frame(0, 0.7, 2.1, 1.6, 1.9)), 0.6);

        assertEquals(List.of(new ClutchTargetPlanner.CatchTarget(new BlockPos(0, 1, 0), 1)), targets);
    }

    @Test
    void choosesReachableLedgeBeforeDistantLethalGround() {
        var ledge = new BlockPos(0, 63, 0);
        var ground = new BlockPos(3, 4, 0);
        var targets = List.of(
            new ClutchTargetPlanner.CatchTarget(ledge, 2),
            new ClutchTargetPlanner.CatchTarget(ground, 30)
        );
        var result = ClutchTargetPlanner.selectPlan(targets, null, 5,
            pos -> pos.equals(ledge) ? List.of(ledge) : null);

        assertEquals(ledge, result.target().pos());
    }

    @Test
    void supportBudgetIncludesEveryPlacementAndItsTick() {
        var catchBlock = new BlockPos(2, 63, 0);
        var support = new BlockPos(1, 63, 0);
        var twoTickTarget = List.of(new ClutchTargetPlanner.CatchTarget(catchBlock, 2));
        var path = List.of(support, catchBlock);

        assertNull(ClutchTargetPlanner.selectPlan(twoTickTarget, null, 1, pos -> path));
        assertNull(ClutchTargetPlanner.selectPlan(
            List.of(new ClutchTargetPlanner.CatchTarget(catchBlock, 1)), null, 5, pos -> path));
        assertEquals(path, ClutchTargetPlanner.selectPlan(twoTickTarget, null, 2, pos -> path).placements());
    }

    @Test
    void retainsAchievableCatchWhileTurningButReplansIfItBecomesUnreachable() {
        var first = new BlockPos(0, 63, 0);
        var preferred = new BlockPos(1, 63, 0);
        var targets = List.of(
            new ClutchTargetPlanner.CatchTarget(first, 2),
            new ClutchTargetPlanner.CatchTarget(preferred, 3)
        );

        assertEquals(preferred, ClutchTargetPlanner.selectPlan(
            targets, preferred, 5, List::of).target().pos());
        assertEquals(first, ClutchTargetPlanner.selectPlan(
            targets, preferred, 5, pos -> pos.equals(preferred) ? null : List.of(first)).target().pos());
    }

    private static FallingPlayer.MovementFrame frame(int tick, double beforeX, double beforeY,
                                                    double afterX, double afterY) {
        return new FallingPlayer.MovementFrame(tick,
            new Vec3(beforeX, beforeY, 0.5), new Vec3(afterX, afterY, 0.5));
    }
}
