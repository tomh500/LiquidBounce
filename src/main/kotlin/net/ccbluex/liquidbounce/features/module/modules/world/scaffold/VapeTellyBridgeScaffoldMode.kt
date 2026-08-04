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
package net.ccbluex.liquidbounce.features.module.modules.world.scaffold

import net.ccbluex.liquidbounce.event.events.MovementInputEvent
import net.ccbluex.liquidbounce.features.module.MinecraftShortcuts
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.block.targetfinding.BlockPlacementTarget
import net.ccbluex.liquidbounce.utils.movement.DirectionalInput
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.random.Random

/** Path and landing-state adaptation of Vape's TellyBridgeScaffoldMode. */
internal object VapeTellyBridgeScaffoldMode : VapeScaffoldModeController, MinecraftShortcuts {

    private var direction = 0
    private var nextPlacement: BlockPos? = null
    private var lastPlacement: BlockPos? = null
    private var positioningTarget: Vec3? = null
    private var positioningDeadline = 0
    private var positioning = false
    private var wasAirborne = false
    private var manualActivationComplete = false
    private var bridgeLevel = 0
    private var verticalTransition = false
    private var consecutiveHeightIncreases = 0
    private var heightIncreaseThreshold = 1
    private var groundRotation: Rotation? = null

    override val readyToPlace get() = nextPlacement != null && !positioning
    override val shouldSprint get() = readyToPlace

    override fun reset() {
        direction = 0
        nextPlacement = null
        lastPlacement = null
        positioningTarget = null
        positioningDeadline = 0
        positioning = false
        wasAirborne = false
        manualActivationComplete = false
        bridgeLevel = 0
        verticalTransition = false
        consecutiveHeightIncreases = 0
        heightIncreaseThreshold = randomHeightIncreaseThreshold()
        groundRotation = null
    }

    override fun onActivated(nextPlacement: BlockPos, direction: Int) {
        this.direction = direction
        this.nextPlacement = nextPlacement
        this.lastPlacement = VapeScaffoldController.offset(nextPlacement, -1, direction)
        this.positioningTarget = computeMovementTarget(lastPlacement ?: nextPlacement)
        this.positioningDeadline = player.tickCount + 40
        this.positioning = true
        this.wasAirborne = !player.onGround()
        this.manualActivationComplete = true
        this.groundRotation = computeBridgeRotation(direction)
    }

    override fun handleMovement(event: MovementInputEvent) {
        val movementTarget = positioningTarget
        if (positioning && movementTarget != null) {
            if (horizontalDistance(movementTarget) <= 0.15 || player.tickCount >= positioningDeadline) {
                positioning = false
            } else {
                event.directionalInput = VapeScaffoldController.movementInputToward(movementTarget)
                return
            }
        }

        if (!player.onGround()) {
            wasAirborne = true
        } else if (wasAirborne || manualActivationComplete) {
            updateLandingCycle()
        }

        event.directionalInput = DirectionalInput.BACKWARDS
        player.isSprinting = true

        val anchor = lastPlacement
        if (player.onGround() && anchor != null && hasReachedPathEdge(anchor)) {
            event.jump = true
        }
    }

    override fun rotationFor(target: BlockPlacementTarget?): Rotation? {
        if (player.onGround()) {
            if (groundRotation == null || wasAirborne) {
                groundRotation = computeBridgeRotation(direction)
            }
            return groundRotation
        }

        val placement = nextPlacement ?: return target?.rotation ?: groundRotation
        val aimPoint = if (verticalTransition) {
            computeElevatedAimPoint(lastPlacement ?: placement)
        } else {
            computePlacementAimPoint(placement)
        }
        return Rotation.lookingAt(aimPoint, player.eyePosition)
    }

    override fun onPlacement(placed: BlockPos) {
        lastPlacement = placed
        if (verticalTransition) verticalTransition = false
        nextPlacement = VapeScaffoldController.offset(placed, 1, direction)
    }

    override fun targetedPosition(default: BlockPos) = nextPlacement ?: default

    private fun updateLandingCycle() {
        bridgeLevel = if (manualActivationComplete || !hasAxisMotion() && hasPassedPathEdge()) 0 else 1
        manualActivationComplete = false
        wasAirborne = false
        groundRotation = computeBridgeRotation(direction)

        if (bridgeLevel == 0) {
            consecutiveHeightIncreases = 0
            return
        }

        consecutiveHeightIncreases++
        if (ModuleScaffold.vapeYIncrease <= 0 ||
            consecutiveHeightIncreases < heightIncreaseThreshold) {
            return
        }

        val anchor = lastPlacement ?: return
        nextPlacement = anchor.above()
        verticalTransition = true
        consecutiveHeightIncreases = 0
        heightIncreaseThreshold = randomHeightIncreaseThreshold()
    }

    private fun computeMovementTarget(anchor: BlockPos): Vec3 {
        val offsets = when (direction) {
            6 -> 0.3 to 0.6
            8 -> 0.7 to 0.4
            7 -> 0.4 to 0.3
            else -> 0.6 to 0.7
        }
        return Vec3(anchor.x + offsets.first, player.y, anchor.z + offsets.second)
    }

    private fun computePlacementAimPoint(position: BlockPos): Vec3 {
        val y = position.y + 0.7
        return when (direction) {
            6 -> Vec3(position.x + 1.0, y, position.z + 0.2)
            8 -> Vec3(position.x.toDouble(), y, position.z + 0.8)
            7 -> Vec3(position.x + 0.8, y, position.z + 1.0)
            else -> Vec3(position.x + 0.2, y, position.z.toDouble())
        }
    }

    private fun computeElevatedAimPoint(position: BlockPos): Vec3 {
        val randomOffset = 0.45 + Math.random() * 0.2
        val y = position.y + 1.0
        return when (direction) {
            6 -> Vec3(position.x + randomOffset, y, position.z + 0.3)
            8 -> Vec3(position.x + 1.0 - randomOffset, y, position.z + 0.7)
            7 -> Vec3(position.x + 0.7, y, position.z + randomOffset)
            else -> Vec3(position.x + 0.3, y, position.z + 1.0 - randomOffset)
        }
    }

    private fun computeBridgeRotation(direction: Int): Rotation {
        val baseYaw = when (direction) {
            6 -> 230f
            8 -> 50f
            7 -> 320f
            else -> 140f
        }
        return Rotation(
            baseYaw + Random.nextFloat() * 8f - 4f,
            90f - Random.nextFloat() * 5f,
        )
    }

    private fun hasReachedPathEdge(pathPosition: BlockPos): Boolean = when (direction) {
        6 -> player.x - (pathPosition.x + 0.8) >= -0.05
        8 -> player.x - (pathPosition.x + 0.2) <= 0.05
        7 -> player.z - (pathPosition.z + 0.8) >= -0.05
        5 -> player.z - (pathPosition.z + 0.2) <= 0.05
        else -> false
    }

    private fun hasPassedPathEdge(): Boolean {
        val pathPosition = lastPlacement ?: return false
        return when (direction) {
            6 -> player.x - (pathPosition.x + 1.0) < -0.1 && abs(player.z - (pathPosition.z + 0.6)) <= 0.15
            8 -> player.x - pathPosition.x > 0.1 && abs(player.z - (pathPosition.z + 0.4)) <= 0.15
            7 -> player.z - (pathPosition.z + 1.0) < -0.1 && abs(player.x - (pathPosition.x + 0.4)) <= 0.15
            5 -> player.z - pathPosition.z > 0.1 && abs(player.x - (pathPosition.x + 0.6)) <= 0.15
            else -> false
        }
    }

    private fun hasAxisMotion() = if (direction % 2 == 0) {
        abs(player.deltaMovement.x) >= 0.1
    } else {
        abs(player.deltaMovement.z) >= 0.1
    }

    private fun randomHeightIncreaseThreshold(): Int {
        val configured = ModuleScaffold.vapeYIncrease
        if (configured <= 0) return 0
        val roll = Math.random()
        return when {
            roll < 0.15 -> configured + 1
            roll < 0.25 -> (configured - 1).coerceAtLeast(0)
            else -> configured
        }
    }

    private fun horizontalDistance(target: Vec3) = hypot(target.x - player.x, target.z - player.z)
}
