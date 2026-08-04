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
import net.ccbluex.liquidbounce.utils.input.InputTracker.isPressedOnAny
import net.ccbluex.liquidbounce.utils.movement.DirectionalInput
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import kotlin.math.floor

/** State-for-state adaptation of Vape's BlatantScaffoldMode bridge loop. */
internal object VapeGodBridgeScaffoldMode : VapeScaffoldModeController, MinecraftShortcuts {

    private val directionCycle = intArrayOf(5, 4, 6, 1, 7, 2, 8, 3)

    private var direction = 0
    private var reversed = false
    private var nextPlacement: BlockPos? = null
    private var targetPosition: Vec3? = null
    private var positioning = false
    private var positioningDeadline = 0
    private var movementStartedAt = 0L
    private var previousLeft = false
    private var previousRight = false

    override val readyToPlace get() = nextPlacement != null && !positioning

    override fun reset() {
        direction = 0
        reversed = false
        nextPlacement = null
        targetPosition = null
        positioning = false
        positioningDeadline = 0
        movementStartedAt = 0L
        previousLeft = false
        previousRight = false
    }

    override fun onActivated(nextPlacement: BlockPos, direction: Int) {
        this.direction = direction
        this.reversed = shouldReverse(direction)
        this.nextPlacement = nextPlacement
        this.targetPosition = computePlacementPoint(nextPlacement, direction, reversed)
        this.positioning = !mc.options.keyJump.isPressedOnAny
        this.positioningDeadline = player.tickCount + 40
        this.movementStartedAt = System.currentTimeMillis()
    }

    override fun onPlacement(placed: BlockPos) {
        nextPlacement = VapeScaffoldController.offset(placed, 1, direction)
        targetPosition = computePlacementPoint(nextPlacement ?: placed, direction, reversed)
        movementStartedAt = System.currentTimeMillis()
    }

    override fun targetedPosition(default: BlockPos) = nextPlacement ?: default

    override fun handleMovement(event: MovementInputEvent) {
        updateDirectionSwitch()

        val target = targetPosition
        if (positioning && target != null) {
            if (horizontalDistance(target) <= 0.15 || player.tickCount >= positioningDeadline) {
                positioning = false
                movementStartedAt = System.currentTimeMillis()
            } else {
                event.directionalInput = VapeScaffoldController.movementInputToward(target)
                return
            }
        }

        if (isAtEdge()) {
            event.directionalInput = DirectionalInput.NONE
            return
        }

        val diagonalDirection = direction < 5
        event.directionalInput = DirectionalInput(
            forwards = false,
            backwards = true,
            left = !diagonalDirection && !reversed,
            right = !diagonalDirection && reversed,
        )
    }

    override fun rotationFor(target: BlockPlacementTarget?): Rotation {
        val slowPitch = System.currentTimeMillis() - movementStartedAt > if (direction < 5) 500L else 300L
        if (direction < 5) {
            val pathPoint = targetPosition
            val pathOffset = if (pathPoint == null) 0f else (sideOfPath(pathPoint) * 0.1f)
            val yaw = when (direction) {
                1 -> 135f - pathOffset
                2 -> -135f - pathOffset
                3 -> -45f - pathOffset
                else -> 45f - pathOffset
            }
            return Rotation(yaw, if (slowPitch) 83f else 81f)
        }

        val point = targetPosition ?: target?.placedBlock?.let {
            computePlacementPoint(it, direction, reversed)
        } ?: player.position()
        val yaw = when (direction) {
            6 -> if (reversed) 135f + 20f * (point.z - player.z).toFloat()
                else 45f + 20f * (point.z - player.z).toFloat()
            8 -> if (reversed) -45f - 20f * (point.z - player.z).toFloat()
                else -135f - 20f * (point.z - player.z).toFloat()
            7 -> if (reversed) -135f - 20f * (point.x - player.x).toFloat()
                else 135f + 20f * (player.x - point.x).toFloat()
            else -> if (reversed) 45f + 20f * (point.x - player.x).toFloat()
                else -45f - 20f * (player.x - point.x).toFloat()
        }
        return Rotation(yaw, if (slowPitch) 80f else 78f)
    }

    private fun updateDirectionSwitch() {
        val left = mc.options.keyLeft.isPressedOnAny
        val right = mc.options.keyRight.isPressedOnAny
        val leftPressed = left && !previousLeft
        val rightPressed = right && !previousRight
        previousLeft = left
        previousRight = right
        if (!leftPressed && !rightPressed) return

        val offset = when {
            leftPressed && reversed -> -1
            leftPressed -> 1
            reversed -> 1
            else -> -1
        }
        val index = directionCycle.indexOf(direction)
        if (index < 0) return
        direction = directionCycle[(index + offset + directionCycle.size) % directionCycle.size]
        reversed = shouldReverse(direction)

        val playerBlock = BlockPos(floor(player.x).toInt(), VapeScaffoldController.placementY(), floor(player.z).toInt())
        nextPlacement = if (VapeScaffoldController.isAir(playerBlock)) {
            playerBlock
        } else {
            VapeScaffoldController.offset(playerBlock, 1, direction)
        }
        targetPosition = computePlacementPoint(nextPlacement ?: playerBlock, direction, reversed)
        positioning = targetPosition?.let { horizontalDistance(it) > 0.15 } == true
        positioningDeadline = player.tickCount + 40
        movementStartedAt = System.currentTimeMillis()
    }

    private fun isAtEdge(): Boolean {
        if (!player.onGround()) return false
        val target = targetPosition ?: return false
        if (direction > 4) {
            val sample = when (direction) {
                6 -> BlockPos(floor(player.x - 0.15).toInt(), VapeScaffoldController.placementY(), floor(target.z).toInt())
                8 -> BlockPos(floor(player.x + 0.15).toInt(), VapeScaffoldController.placementY(), floor(target.z).toInt())
                7 -> BlockPos(floor(target.x).toInt(), VapeScaffoldController.placementY(), floor(player.z - 0.15).toInt())
                else -> BlockPos(floor(target.x).toInt(), VapeScaffoldController.placementY(), floor(player.z + 0.15).toInt())
            }
            return VapeScaffoldController.isAir(sample)
        }

        val checkBox = player.boundingBox
            .deflate(0.16, 0.0, 0.16)
            .move(player.deltaMovement.x, -1.0, player.deltaMovement.z)
        return world.noCollision(player, checkBox)
    }

    private fun shouldReverse(direction: Int): Boolean {
        val bounds = when (direction) {
            6 -> 135f to 45f
            8 -> 315f to 225f
            7 -> 225f to 135f
            5 -> 45f to 315f
            else -> return !reversed
        }
        return VapeScaffoldController.angleDistance(player.yRot, bounds.first) <=
            VapeScaffoldController.angleDistance(player.yRot, bounds.second)
    }

    private fun computePlacementPoint(position: BlockPos, direction: Int, reversed: Boolean): Vec3 {
        val offsets = when (direction) {
            1 -> if (reversed) 0.65 to 0.35 else 0.35 to 0.65
            2 -> if (reversed) 0.65 to 0.65 else 0.35 to 0.35
            3 -> if (reversed) 0.35 to 0.65 else 0.65 to 0.35
            4 -> if (reversed) 0.35 to 0.35 else 0.65 to 0.65
            6 -> if (reversed) 0.8 to 0.8 else 0.8 to 0.2
            8 -> if (reversed) 0.2 to 0.2 else 0.2 to 0.8
            7 -> if (reversed) 0.2 to 0.8 else 0.8 to 0.8
            else -> if (reversed) 0.8 to 0.2 else 0.2 to 0.2
        }
        return Vec3(position.x + offsets.first, position.y.toDouble(), position.z + offsets.second)
    }

    private fun sideOfPath(pathPoint: Vec3): Float {
        val directionVector = VapeScaffoldController.offset(BlockPos.ZERO, 1, direction)
        return ((directionVector.x * (player.z - pathPoint.z)) -
            (directionVector.z * (player.x - pathPoint.x))).toFloat()
    }

    private fun horizontalDistance(target: Vec3) =
        kotlin.math.hypot(target.x - player.x, target.z - player.z)
}
