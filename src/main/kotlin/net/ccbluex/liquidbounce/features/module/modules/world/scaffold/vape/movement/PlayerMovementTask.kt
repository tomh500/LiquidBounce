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
package net.ccbluex.liquidbounce.features.module.modules.world.scaffold.vape.movement

import net.ccbluex.liquidbounce.features.module.MinecraftShortcuts
import net.ccbluex.liquidbounce.utils.movement.DirectionalInput

/** Direct compatibility port of Vape 4.21's PlayerMovementTask. */
internal abstract class PlayerMovementTask : MinecraftShortcuts {
    var remainingX = 0.0
        protected set
    var remainingZ = 0.0
        protected set
    var requireSupportedMovement = false
    var ignoreX = false
    var ignoreZ = false
    var restoreInputOnCompletion = true
    var completionTolerance = 0.2
    var sneakNearTarget = false
    var waitForGroundAfterArrival = false
    var targetReached = false
        private set
    var completed = false
        private set

    fun updateCompletion(): Boolean {
        if (completed || mc.gui.screen() != null) return false

        val reachedTarget = hasReachedTarget()
        if (reachedTarget) {
            targetReached = true
            completed = true
            return true
        }

        // Kept in the same order as Vape 4.21. Its reached-target branch
        // completes immediately, so this is normally only a fallback branch.
        if (targetReached && (!waitForGroundAfterArrival || player.onGround())) {
            completed = true
            return true
        }
        return false
    }

    fun applyMovementInput(): DirectionalInput {
        if (remainingX == 0.0 && remainingZ == 0.0) return DirectionalInput.NONE
        return MovementInputHelper.applyMovementToward(
            targetOffsetX = remainingX,
            targetOffsetZ = remainingZ,
            requireSupportedMovement = requireSupportedMovement,
        )
    }

    protected abstract fun hasReachedTarget(): Boolean
}
