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
import net.ccbluex.liquidbounce.utils.aiming.RotationManager
import net.ccbluex.liquidbounce.utils.movement.DirectionalInput

/** Compatibility port of Vape 4.21's MovementInputHelper.applyMovementToward. */
internal object MovementInputHelper : MinecraftShortcuts {

    fun applyMovementToward(
        targetOffsetX: Double,
        targetOffsetZ: Double,
        requireSupportedMovement: Boolean = false,
    ): DirectionalInput {
        val movementYaw = VapeMovementPlanner.movementYaw(
            player.yRot,
            RotationManager.currentRotation?.yaw,
            RotationManager.activeRotationTarget?.movementCorrection,
        )
        val plan = VapeMovementPlanner.plan(
            targetOffsetX,
            targetOffsetZ,
            player.deltaMovement.x,
            player.deltaMovement.z,
            movementYaw,
            getMovementStep(0),
            getMovementStep(1),
        )

        return if (requireSupportedMovement && !hasSupportingCollision(
                player.x + plan.projectedX(), player.z + plan.projectedZ(),
            )) {
            DirectionalInput.NONE
        } else {
            plan.input()
        }
    }

    private fun getMovementStep(keyIndex: Int): Double {
        var step = when {
            player.isShiftKeyDown && player.onGround() -> 0.06
            player.isSprinting && keyIndex == 0 -> 0.3
            else -> 0.2
        }
        if (!player.onGround()) step *= 0.02
        return step
    }

    private fun hasSupportingCollision(projectedX: Double, projectedZ: Double): Boolean {
        val projectedBounds = player.boundingBox
            .move(projectedX - player.x, 0.0, projectedZ - player.z)
            .inflate(-0.15, 0.0, -0.15)
            .move(player.deltaMovement.x, -1.0, player.deltaMovement.z)
        return !world.noCollision(player, projectedBounds)
    }
}
