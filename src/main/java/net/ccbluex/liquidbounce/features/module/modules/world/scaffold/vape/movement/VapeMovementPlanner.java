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
import org.jspecify.annotations.Nullable;

/** Vape's greedy waypoint steering, expressed in the yaw used by movement physics. */
public final class VapeMovementPlanner {

    private VapeMovementPlanner() {
    }

    public static float movementYaw(
        float cameraYaw,
        @Nullable Float rotationYaw,
        @Nullable MovementCorrection correction
    ) {
        // CHANGE_LOOK can still be interpolating the camera while physics already uses the
        // entire managed turn. SILENT transforms the keys later and must keep camera-relative input.
        return rotationYaw != null
            && (correction == MovementCorrection.CHANGE_LOOK || correction == MovementCorrection.STRICT)
            ? rotationYaw : cameraYaw;
    }

    public static Plan plan(
        double targetOffsetX,
        double targetOffsetZ,
        double velocityX,
        double velocityZ,
        float movementYaw,
        double forwardStep,
        double otherStep
    ) {
        var selected = new boolean[4];
        double projectedX = velocityX;
        double projectedZ = velocityZ;
        double bestDistance = Math.hypot(targetOffsetX - projectedX, targetOffsetZ - projectedZ);

        // Same forward, right, backward, left order and one-tick momentum projection as Vape 4.21.
        for (int index = 0; index < selected.length; index++) {
            double radians = Math.toRadians(movementYaw + index * 90.0);
            double movementStep = index == 0 ? forwardStep : otherStep;
            double deltaX = movementStep * -Math.sin(radians);
            double deltaZ = movementStep * Math.cos(radians);
            double candidateDistance = Math.hypot(
                targetOffsetX - (projectedX + deltaX),
                targetOffsetZ - (projectedZ + deltaZ)
            );
            if (candidateDistance < bestDistance) {
                selected[index] = true;
                projectedX += deltaX;
                projectedZ += deltaZ;
                bestDistance = candidateDistance;
            }
        }

        return new Plan(
            new DirectionalInput(selected[0], selected[2], selected[3], selected[1]),
            projectedX,
            projectedZ
        );
    }

    public record Plan(DirectionalInput input, double projectedX, double projectedZ) {
    }
}
