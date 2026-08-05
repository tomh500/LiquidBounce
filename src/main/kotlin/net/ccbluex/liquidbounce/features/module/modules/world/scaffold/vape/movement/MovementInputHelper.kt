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
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** Compatibility port of Vape 4.21's MovementInputHelper.applyMovementToward. */
internal object MovementInputHelper : MinecraftShortcuts {

    fun applyMovementToward(
        targetOffsetX: Double,
        targetOffsetZ: Double,
        requireSupportedMovement: Boolean = false,
    ): DirectionalInput {
        val headings = doubleArrayOf(
            player.yRot.toDouble(),
            (player.yRot + 90.0) % 360.0,
            (player.yRot + 180.0) % 360.0,
            (player.yRot + 270.0) % 360.0,
        )
        val selected = BooleanArray(4)
        var projectedX = player.x + player.deltaMovement.x
        var projectedZ = player.z + player.deltaMovement.z
        val targetX = player.x + targetOffsetX
        val targetZ = player.z + targetOffsetZ
        var bestDistance = hypot(targetX - projectedX, targetZ - projectedZ)

        headings.forEachIndexed { index, heading ->
            val movementStep = getMovementStep(index)
            val radians = Math.toRadians(heading)
            val deltaX = movementStep * -sin(radians)
            val deltaZ = movementStep * cos(radians)
            val candidateDistance = hypot(
                targetX - (projectedX + deltaX),
                targetZ - (projectedZ + deltaZ),
            )
            if (candidateDistance < bestDistance) {
                selected[index] = true
                projectedX += deltaX
                projectedZ += deltaZ
                bestDistance = candidateDistance
            }
        }

        if (requireSupportedMovement && !hasSupportingCollision(projectedX, projectedZ)) {
            selected.fill(false)
        }

        return DirectionalInput(
            forwards = selected[0],
            right = selected[1],
            backwards = selected[2],
            left = selected[3],
        )
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
