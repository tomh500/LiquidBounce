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
import net.ccbluex.liquidbounce.utils.aiming.utils.RotationUtil
import net.ccbluex.liquidbounce.utils.block.targetfinding.BlockPlacementTarget
import net.ccbluex.liquidbounce.utils.input.InputTracker.isPressedOnAny
import net.ccbluex.liquidbounce.utils.movement.DirectionalInput
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.floor

/** Vape 4.21 GodBridge control loop with edge braking, recovery and reversible turns. */
internal object VapeGodBridgeScaffoldMode : VapeScaffoldModeController, MinecraftShortcuts {

    private val directionCycle = intArrayOf(5, 4, 6, 1, 7, 2, 8, 3)

    private var direction = 0
    private var reversed = false
    private var targetPosition: Vec3? = null
    private var positioning = false
    private var positioningRotation: Rotation? = null
    private var positioningDeadline = 0
    private var lastMovingAt = 0L
    private var previousLeft = false
    private var previousRight = false
    private var keyIdle = true
    private var switching = false
    private var switchPrepared = false
    private var pendingDirection = 0
    private var pendingReversed = false
    private var pendingPosition: Vec3? = null
    private var switchDeadline = 0
    private var rotationPending = false
    private var turnRotation: Rotation? = null
    private var switchedToCardinal = false

    override val readyToPlace get() = direction != 0
    override val scaleAxesProportionally get() = rotationPending
    override val rotationTolerance get() = if (rotationPending) 0f else 0.5f

    override fun reset() {
        direction = 0
        reversed = false
        targetPosition = null
        positioning = false
        positioningRotation = null
        positioningDeadline = 0
        lastMovingAt = 0L
        previousLeft = false
        previousRight = false
        keyIdle = true
        switching = false
        switchPrepared = false
        pendingDirection = 0
        pendingReversed = false
        pendingPosition = null
        switchDeadline = 0
        rotationPending = false
        turnRotation = null
        switchedToCardinal = false
    }

    override fun onActivated(activationAnchor: BlockPos, direction: Int) {
        reset()
        this.direction = direction
        reversed = shouldReverse(direction)
        targetPosition = computePlacementPoint(activationAnchor, direction, reversed)
        positioning = !mc.options.keyJump.isPressedOnAny
        positioningDeadline = player.tickCount + POSITIONING_TICK_LIMIT
        lastMovingAt = System.currentTimeMillis()
        if (positioning) positioningRotation = computeCurrentRotation()
    }

    override fun handleMovement(event: MovementInputEvent) {
        val target = targetPosition
        if (positioning && target != null) {
            if (hasReached(target, MOVEMENT_TOLERANCE) || player.tickCount >= positioningDeadline) {
                positioning = false
                positioningRotation = null
            } else {
                event.directionalInput = VapeScaffoldController.movementInputToward(target)
                return
            }
        }

        if (rotationPending) {
            val rotation = rotationFor(null)
            if (rotation != null && VapeScaffoldController.angleDistance(
                    player.yRot,
                    rotation.yaw,
                ) > TURN_ROTATION_TOLERANCE
            ) {
                event.directionalInput = DirectionalInput.NONE
                return
            }
            rotationPending = false
            turnRotation = null
            switchedToCardinal = false
            VapeScaffoldController.resetRotationIntegrator()
        }

        if (handleDirectionSwitch(event)) return

        if (isAtEdge()) {
            event.directionalInput = DirectionalInput.NONE
            if (System.currentTimeMillis() - lastMovingAt >= STUCK_RECOVERY_DELAY) {
                recoverFromEdge()
            }
            return
        }

        lastMovingAt = System.currentTimeMillis()
        val diagonalDirection = direction < 5
        event.directionalInput = DirectionalInput(
            forwards = false,
            backwards = true,
            left = !diagonalDirection && !reversed,
            right = !diagonalDirection && reversed,
        )
    }

    override fun rotationFor(target: BlockPlacementTarget?): Rotation? {
        if (switching && switchPrepared) return null
        positioningRotation?.takeIf { positioning }?.let { return it }
        turnRotation?.takeIf { rotationPending }?.let { return it }

        return computeCurrentRotation(target)
    }

    private fun computeCurrentRotation(target: BlockPlacementTarget? = null): Rotation {
        val stoppedFor = System.currentTimeMillis() - lastMovingAt
        if (direction < 5) {
            val pathPoint = targetPosition
            val pathOffset = if (pathPoint == null) 0f else sideOfPath(pathPoint) * 0.1f
            val yaw = when (direction) {
                1 -> 135f - pathOffset
                2 -> -135f - pathOffset
                3 -> -45f - pathOffset
                else -> 45f - pathOffset
            }
            return Rotation(yaw, if (stoppedFor > 500L) 83f else 81f)
        }

        val point = targetPosition ?: target?.placedBlock?.let {
            computePlacementPoint(it, direction, reversed)
        } ?: player.position()
        val previousX = player.xo
        val previousZ = player.zo
        val yaw = when (direction) {
            6 -> if (reversed) 135f + 20f * (point.z - previousZ).toFloat()
                else 45f + 20f * (point.z - previousZ).toFloat()
            8 -> if (reversed) -45f - 20f * (point.z - previousZ).toFloat()
                else -135f - 20f * (point.z - previousZ).toFloat()
            7 -> if (reversed) -135f - 20f * (point.x - previousX).toFloat()
                else 135f + 20f * (previousX - point.x).toFloat()
            else -> if (reversed) 45f + 20f * (point.x - previousX).toFloat()
                else -45f - 20f * (previousX - point.x).toFloat()
        }
        return Rotation(yaw, if (stoppedFor > 300L) 80f else 78f)
    }

    override fun rotationSpeed(rotation: Rotation): Float {
        val divisor = if (rotationPending && switchedToCardinal) 12f else 15f
        val yawDistance = abs(RotationUtil.angleDifference(rotation.yaw, player.yRot))
        return (2f + yawDistance / divisor).coerceAtMost(12f)
    }

    override fun isValidPlacementHit(hitResult: BlockHitResult): Boolean =
        isPlacementFaceValid(player.deltaMovement.y, switching, hitResult.direction)

    override fun canDeactivateSafely() = hasNextSupport()

    private fun handleDirectionSwitch(event: MovementInputEvent): Boolean {
        val left = mc.options.keyLeft.isPressedOnAny
        val right = mc.options.keyRight.isPressedOnAny
        if (!switching) {
            val leftTransition = if (keyIdle) left && !previousLeft else !left && previousLeft
            val rightTransition = if (keyIdle) right && !previousRight else !right && previousRight
            previousLeft = left
            previousRight = right

            if (!leftTransition && !rightTransition) return false

            val rotationOffset = if (keyIdle) {
                keyIdle = false
                if (leftTransition) 1 else -1
            } else {
                keyIdle = true
                if (leftTransition) -1 else 1
            }
            pendingDirection = rotateDirection(direction, rotationOffset)
            pendingPosition = null
            switchPrepared = false
            switching = true
        }

        if (!switchPrepared) {
            val playerBlock = BlockPos(
                floor(player.x).toInt(),
                VapeScaffoldController.placementY(),
                floor(player.z).toInt(),
            )
            if (VapeScaffoldController.isAir(playerBlock) || isProjectedUnsupported() || isPastCorner(direction)) {
                return false
            }

            pendingReversed = shouldReverse(pendingDirection)
            pendingPosition = computePlacementPoint(playerBlock, pendingDirection, pendingReversed)
            switchDeadline = player.tickCount + POSITIONING_TICK_LIMIT
            switchPrepared = true

            val canTurnImmediately = direction > 4 &&
                ((!reversed && pendingDirection == rotateDirection(direction, -1)) ||
                    (reversed && pendingDirection == rotateDirection(direction, 1)))
            if (canTurnImmediately || pendingPosition?.let { hasReached(it, TURN_TASK_START_TOLERANCE) } == true) {
                pendingPosition = null
            }
        }

        val pendingTarget = pendingPosition
        if (pendingTarget != null && !hasReached(pendingTarget, MOVEMENT_TOLERANCE)) {
            if (player.tickCount < switchDeadline) {
                event.directionalInput = VapeScaffoldController.movementInputToward(pendingTarget)
            } else {
                // Vape cancels a timed-out task and retries the still-pending switch next tick.
                switchPrepared = false
                pendingPosition = null
                event.directionalInput = DirectionalInput.NONE
            }
            return true
        }

        commitDirectionSwitch()
        event.directionalInput = DirectionalInput.NONE
        return true
    }

    private fun commitDirectionSwitch() {
        direction = pendingDirection
        reversed = pendingReversed
        targetPosition = pendingPosition ?: computePlacementPoint(
            BlockPos(
                floor(player.x).toInt(),
                VapeScaffoldController.placementY(),
                floor(player.z).toInt(),
            ),
            direction,
            reversed,
        )
        switching = false
        switchPrepared = false
        pendingPosition = null
        lastMovingAt = System.currentTimeMillis()
        rotationPending = true
        switchedToCardinal = direction > 4
        turnRotation = computeCurrentRotation()
    }

    private fun recoverFromEdge() {
        val anchor = computeRecoveryAnchor()
        targetPosition = computePlacementPoint(anchor, direction, reversed)
        positioning = true
        positioningDeadline = player.tickCount + POSITIONING_TICK_LIMIT
        lastMovingAt = System.currentTimeMillis()
        positioningRotation = computeCurrentRotation()
    }

    private fun computeRecoveryAnchor(): BlockPos {
        val placementY = VapeScaffoldController.placementY() + if (player.deltaMovement.y > 0.0) 1 else 0
        var anchor = BlockPos(floor(player.x).toInt(), placementY, floor(player.z).toInt())
        if (VapeScaffoldController.isAir(anchor)) {
            anchor = VapeScaffoldController.offset(anchor, -1, direction)
            if (VapeScaffoldController.isAir(anchor)) {
                val sideDirection = rotateDirection(direction, 2)
                anchor = VapeScaffoldController.offset(anchor, 1, sideDirection)
                if (VapeScaffoldController.isAir(anchor)) {
                    anchor = VapeScaffoldController.offset(anchor, -2, sideDirection)
                }
            }
        }
        return anchor
    }

    private fun isAtEdge(): Boolean {
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

    private fun hasNextSupport(): Boolean {
        val target = targetPosition ?: return false
        var sampleX = player.x
        var sampleZ = player.z
        when (direction) {
            1 -> { sampleX += 0.2; sampleZ += 0.2 }
            2 -> { sampleX -= 0.2; sampleZ += 0.2 }
            3 -> { sampleX -= 0.2; sampleZ -= 0.2 }
            4 -> { sampleX += 0.2; sampleZ -= 0.2 }
            6 -> { sampleX += 0.25; sampleZ = target.z }
            8 -> { sampleX -= 0.25; sampleZ = target.z }
            7 -> { sampleX = target.x; sampleZ += 0.25 }
            5 -> { sampleX = target.x; sampleZ -= 0.25 }
            else -> return true
        }
        return !VapeScaffoldController.isAir(
            BlockPos(
                floor(sampleX).toInt(),
                VapeScaffoldController.placementY(),
                floor(sampleZ).toInt(),
            )
        )
    }

    private fun isProjectedUnsupported(): Boolean {
        val checkBox = player.boundingBox
            .deflate(0.2, 0.0, 0.2)
            .move(player.deltaMovement.x, -1.0, player.deltaMovement.z)
        return world.noCollision(player, checkBox)
    }

    private fun isPastCorner(direction: Int): Boolean {
        val blockX = floor(player.x)
        val blockZ = floor(player.z)
        return when (direction) {
            1 -> player.x - blockX + (player.z - blockZ) > 1.0
            2 -> blockX - player.x + (player.z - blockZ) > 1.0
            3 -> blockX - player.x + (blockZ - player.z) > 1.0
            4 -> player.x - blockX + (blockZ - player.z) > 1.0
            6 -> player.x - blockX > 0.5
            8 -> blockX - player.x > 0.5
            7 -> player.z - blockZ > 0.5
            5 -> blockZ - player.z > 0.5
            else -> false
        }
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

    private fun rotateDirection(direction: Int, offset: Int): Int {
        val index = directionCycle.indexOf(direction)
        if (index < 0) return direction
        return directionCycle[(index + offset).mod(directionCycle.size)]
    }

    private fun hasReached(target: Vec3, tolerance: Double) =
        abs(target.x - player.x) <= tolerance && abs(target.z - player.z) <= tolerance

    internal fun isPlacementFaceValid(verticalMotion: Double, switching: Boolean, face: Direction) =
        if (abs(verticalMotion) > 0.1 || switching) {
            face != Direction.DOWN
        } else {
            face.axis != Direction.Axis.Y
        }

    private const val POSITIONING_TICK_LIMIT = 40
    private const val MOVEMENT_TOLERANCE = 0.2
    private const val TURN_TASK_START_TOLERANCE = 0.15
    private const val TURN_ROTATION_TOLERANCE = 4f
    private const val STUCK_RECOVERY_DELAY = 800L
}
