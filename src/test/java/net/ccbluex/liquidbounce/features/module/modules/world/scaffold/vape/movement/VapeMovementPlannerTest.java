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
package net.ccbluex.liquidbounce.features.module.modules.world.scaffold.vape.movement;

import net.ccbluex.liquidbounce.utils.aiming.features.MovementCorrection;
import net.ccbluex.liquidbounce.utils.movement.DirectionalInput;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VapeMovementPlannerTest {

    @Test
    void airborneTurnKeepsSteeringTowardLandingWhileCameraInterpolates() {
        // The visible camera still faces south, while Telly's placement rotation faces north.
        float physicsYaw = 180f;
        float movementYaw = VapeMovementPlanner.movementYaw(0f, physicsYaw, MovementCorrection.CHANGE_LOOK);
        var plan = VapeMovementPlanner.plan(0.0, 3.0, 0.0, 0.23, movementYaw, 0.006, 0.004);

        assertEquals(DirectionalInput.BACKWARDS, plan.input());
        assertTrue(worldForwardAcceleration(plan.input(), physicsYaw) > 0.0);

        // Planning against the interpolated camera would accelerate away from the landing block.
        var oldPlan = VapeMovementPlanner.plan(0.0, 3.0, 0.0, 0.23, 0f, 0.006, 0.004);
        assertTrue(worldForwardAcceleration(oldPlan.input(), physicsYaw) < 0.0);
    }

    @Test
    void placementTurnsKeepAllFourCardinalLandingDirections() {
        for (int direction = 0; direction < 4; direction++) {
            float targetYaw = direction * 90f;
            double radians = Math.toRadians(targetYaw);
            double targetX = -Math.sin(radians) * 3.0;
            double targetZ = Math.cos(radians) * 3.0;
            float physicsYaw = targetYaw + 180f;
            float movementYaw = VapeMovementPlanner.movementYaw(
                targetYaw, physicsYaw, MovementCorrection.CHANGE_LOOK
            );
            var plan = VapeMovementPlanner.plan(targetX, targetZ, 0.0, 0.0, movementYaw, 0.006, 0.004);

            assertEquals(DirectionalInput.BACKWARDS, plan.input());
            assertTrue(targetX * plan.projectedX() + targetZ * plan.projectedZ() > 0.0);
        }
    }

    @Test
    void arrivalSteeringBrakesForMomentumInsteadOfAcceleratingPastLanding() {
        var plan = VapeMovementPlanner.plan(0.0, 0.15, 0.0, 0.3, 0f, 0.006, 0.004);

        assertFalse(plan.input().getForwards());
        assertTrue(plan.input().getBackwards());
        assertTrue(plan.projectedZ() < 0.3);
    }

    @Test
    void silentCorrectionKeepsCameraRelativeInputForTheLaterKeyboardTransform() {
        assertEquals(20f, VapeMovementPlanner.movementYaw(20f, 160f, MovementCorrection.SILENT));
        assertEquals(20f, VapeMovementPlanner.movementYaw(20f, 160f, MovementCorrection.OFF));
        assertEquals(20f, VapeMovementPlanner.movementYaw(20f, null, MovementCorrection.CHANGE_LOOK));
        assertEquals(160f, VapeMovementPlanner.movementYaw(20f, 160f, MovementCorrection.STRICT));
    }

    private static double worldForwardAcceleration(DirectionalInput input, float physicsYaw) {
        int forwards = (input.getForwards() ? 1 : 0) - (input.getBackwards() ? 1 : 0);
        int sideways = (input.getLeft() ? 1 : 0) - (input.getRight() ? 1 : 0);
        double radians = Math.toRadians(physicsYaw);
        return forwards * Math.cos(radians) + sideways * Math.sin(radians);
    }
}
