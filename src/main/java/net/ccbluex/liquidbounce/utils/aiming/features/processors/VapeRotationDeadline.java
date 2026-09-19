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
package net.ccbluex.liquidbounce.utils.aiming.features.processors;

/** Converts a predicted placement deadline into a minimum angular step. */
public final class VapeRotationDeadline {

    private VapeRotationDeadline() {
    }

    /**
     * Keep faster configured turns, while reaching the target within the remaining ticks.
     * Callers supply the wrapped yaw difference or the direct pitch difference in degrees;
     * the deadline must be applied after conversion from Vape controller speed.
     */
    public static float step(float plannedStep, float remainingAngle, int remainingTicks) {
        float distance = Math.abs(remainingAngle);
        float minimumStep = distance / Math.max(remainingTicks, 1);
        float magnitude = Math.min(distance, Math.max(Math.abs(plannedStep), minimumStep));
        return Math.copySign(magnitude, remainingAngle);
    }
}
