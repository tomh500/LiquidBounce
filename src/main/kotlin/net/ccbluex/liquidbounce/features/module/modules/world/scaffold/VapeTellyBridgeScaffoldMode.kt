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
import net.ccbluex.liquidbounce.features.module.modules.world.scaffold.vape.movement.PlayerMovementTaskManager
import net.ccbluex.liquidbounce.features.module.modules.world.scaffold.vape.movement.TargetPositionMovementTask
import net.ccbluex.liquidbounce.utils.aiming.RotationManager
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.block.targetfinding.BlockPlacementTarget
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.floor
import kotlin.random.Random

/** World-confirmed bridge path and landing cycle from Vape 4.21's TellyBridgeScaffoldMode. */
internal object VapeTellyBridgeScaffoldMode : VapeScaffoldModeController, MinecraftShortcuts {

    private val bridgePath = mutableListOf<BlockPos>()
    private val movementManager = PlayerMovementTaskManager()
    private var movementTask: TargetPositionMovementTask? = null
    private var bridgingActive = false
    private var direction = 0
    private var bridgeLevel = 0
    private var wasAirborne = false
    private var manualActivationComplete = false
    private var consecutiveHeightIncreases = 0
    private var fixedRotation: Rotation? = null
    private var fixedRotationSpeed: Float? = null
    private var pointAimTarget: Vec3? = null
    private var pointAimDirty = true
    private var pointRotationSpeed: Float? = null
    private var pointSpeedInitialized = false
    private var lastUpdateTick = Int.MIN_VALUE

    override val readyToPlace: Boolean
        get() = bridgingActive && bridgePath.isNotEmpty()
    override val shouldSprint get() = bridgingActive
    override val scaleAxesProportionally get() = false
    override val rotationTolerance get() = if (fixedRotation != null && player.onGround()) 0.5f else 0f

    override fun reset() {
        bridgePath.clear()
        movementManager.reset()
        movementTask = null
        bridgingActive = false
        direction = 0
        bridgeLevel = 0
        wasAirborne = false
        manualActivationComplete = false
        consecutiveHeightIncreases = 0
        fixedRotation = null
        fixedRotationSpeed = null
        pointAimTarget = null
        pointAimDirty = true
        pointRotationSpeed = null
        pointSpeedInitialized = false
        lastUpdateTick = Int.MIN_VALUE
    }

    override fun onActivated(activationAnchor: BlockPos, direction: Int) {
        reset()
        this.direction = direction
        bridgePath += activationAnchor.immutable()

        val movementAnchor = computeElevatedPlacementPoint(
            VapeScaffoldController.offset(activationAnchor, -1, direction),
            lateralOffset = 0.0,
        )
        submitMovementTask(computeTargetPosition(movementAnchor), waitForGround = false)
        manualActivationComplete = true
    }

    override fun update() {
        if (lastUpdateTick == player.tickCount) return
        lastUpdateTick = player.tickCount

        movementManager.updateCompletion()
        if (!bridgingActive && movementTask?.completed == true) {
            bridgingActive = true
        }
        if (!bridgingActive) return

        syncConfirmedPath()
        if (!player.onGround()) {
            wasAirborne = true
        } else if (manualActivationComplete || wasAirborne) {
            updateLandingCycle()
        }

        if (!player.onGround()) {
            if (fixedRotation != null) {
                fixedRotation = null
                fixedRotationSpeed = null
                VapeScaffoldController.resetRotationIntegrator()
            }
            updatePointAimTarget()
        }
    }

    override fun targetedPosition(default: BlockPos) = nextPathPosition() ?: default

    override fun handleMovement(event: MovementInputEvent) {
        val completedTask = movementManager.updateCompletion()
        if (!bridgingActive && movementTask?.completed == true) {
            bridgingActive = true
        }

        // Vape advances the next landing task before applying the movement keys for
        // that same tick.  Doing this here avoids a one-tick NONE input gap when a
        // task reaches its target exactly on landing.
        if (completedTask != null && bridgingActive && player.onGround() && (manualActivationComplete || wasAirborne)) {
            updateLandingCycle()
        }

        event.directionalInput = movementManager.applyMovementInput()

        if (!bridgingActive) return

        event.jump = false
        val pathPosition = bridgePath.lastOrNull() ?: return
        // Vape presses sprint before evaluating this edge.  The player entity's
        // sprint flag is updated later in the tick, so checking it here skips
        // the first jump and sends the player straight off the bridge.
        if (player.onGround() && (bridgeLevel != 0 || bridgingActive) && hasReachedPathEdge(pathPosition)) {
            event.jump = true
        }
    }

    override fun rotationFor(target: BlockPlacementTarget?): Rotation? {
        if (!bridgingActive) return null

        fixedRotation?.takeIf { player.onGround() }?.let { return it }

        val aimTarget = pointAimTarget ?: return target?.rotation
        val desired = Rotation.lookingAt(aimTarget, player.eyePosition)
        val yaw = if (canUpdatePointYaw()) {
            desired.yaw
        } else {
            RotationManager.currentRotation?.yaw ?: player.yRot
        }
        return Rotation(yaw, desired.pitch)
    }

    override fun rotationSpeed(rotation: Rotation): Float {
        if (fixedRotation != null && player.onGround()) {
            return fixedRotationSpeed ?: VapeScaffoldController.defaultRotationSpeed(rotation)
        }
        if (canUpdatePointYaw() && !pointSpeedInitialized) {
            pointRotationSpeed = VapeScaffoldController.directionRotationSpeed(direction)
            pointSpeedInitialized = true
        }
        return pointRotationSpeed ?: VapeScaffoldController.directionRotationSpeed(direction)
    }

    override fun isValidPlacementHit(hitResult: BlockHitResult): Boolean {
        if (hitResult.blockPos != bridgePath.lastOrNull()) return false

        // Vape原版逻辑：检查方向索引来验证放置尝试
        // Direction索引: DOWN=0, UP=1, NORTH=2, SOUTH=3, WEST=4, EAST=5
        val directionIndex = hitResult.direction.ordinal

        val enoughAttempts = if (bridgeLevel != 0 && bridgePath.size == 4) {
            directionIndex == 1  // 必须是UP
        } else {
            directionIndex > 1   // 必须是侧面方向
        }

        return enoughAttempts
    }

    override fun canDeactivateSafely(): Boolean {
        if (!bridgingActive) return true
        if (!player.onGround()) return false

        var sampleX = player.x
        var sampleZ = player.z
        when (direction) {
            6 -> sampleX += 0.15
            8 -> sampleX -= 0.15
            7 -> sampleZ += 0.15
            5 -> sampleZ -= 0.15
            else -> return true
        }
        val sample = BlockPos(
            floor(sampleX).toInt(),
            VapeScaffoldController.placementY(),
            floor(sampleZ).toInt(),
        )
        return !VapeScaffoldController.isAir(sample)
    }

    private fun syncConfirmedPath() {
        var index = bridgePath.lastIndex
        while (index >= (bridgePath.size - 3).coerceAtLeast(0) && VapeScaffoldController.isAir(bridgePath[index])) {
            bridgePath.removeAt(index)
            pointAimTarget = null
            pointAimDirty = true
            index--
        }

        val nextPosition = nextPathPosition() ?: return
        if (VapeScaffoldController.isAir(nextPosition)) return

        if (bridgePath.size == PATH_RESET_SIZE) bridgePath.clear()
        bridgePath += nextPosition.immutable()
        fixedRotation = null
        pointAimTarget = null
        pointAimDirty = true
        VapeScaffoldController.resetRotationIntegrator()
    }

    private fun updateLandingCycle() {
        bridgeLevel = if (manualActivationComplete || !hasAxisMotion() && hasPassedPathEdge()) 0 else 1
        manualActivationComplete = false
        fixedRotation = null
        fixedRotationSpeed = null
        pointAimDirty = true

        if (bridgeLevel == 1 && consecutiveHeightIncreases >= randomHeightIncreaseThreshold()) {
            if (ModuleScaffold.vapeYIncrease == 0 && !isAxisMotionBelowThreshold()) {
                submitMovementTask(computeMovementTarget(initialMove = false), waitForGround = false)
                repeatLastPathPosition(5)
                wasAirborne = false
                return
            }

            bridgeLevel = 0
            val lastPosition = bridgePath.lastOrNull() ?: return
            val transitionTarget = computeElevatedPlacementPoint(
                VapeScaffoldController.offset(lastPosition, 1, direction),
                lateralOffset = 0.0,
            )
            submitMovementTask(computeTargetPosition(transitionTarget), waitForGround = false)
            repeatLastPathPosition(1)
            updatePointAimTarget(force = true)
            return
        }

        if (bridgeLevel == 0) {
            VapeScaffoldController.resetRotationIntegrator()
            fixedRotation = computeBridgeRotation().also {
                fixedRotationSpeed = VapeScaffoldController.defaultRotationSpeed(it)
            }
            submitMovementTask(computeMovementTarget(initialMove = true), waitForGround = false)
            repeatLastPathPosition(1)
            consecutiveHeightIncreases = 0
        } else {
            submitMovementTask(computeMovementTarget(initialMove = false), waitForGround = false)
            repeatLastPathPosition(4)
            consecutiveHeightIncreases++
        }
        wasAirborne = false
    }

    private fun repeatLastPathPosition(targetSize: Int) {
        val lastPosition = bridgePath.lastOrNull() ?: return
        bridgePath.clear()
        repeat(targetSize) { bridgePath += lastPosition }
    }

    private fun nextPathPosition(): BlockPos? {
        val lastPosition = bridgePath.lastOrNull() ?: return null
        return nextPathPosition(lastPosition, bridgePath.size, bridgeLevel, direction)
    }

    private fun computeNextPlacementPoint(): Vec3? {
        val lastPosition = bridgePath.lastOrNull() ?: return null
        return if (bridgeLevel != 0 && bridgePath.size == 4) {
            computeElevatedPlacementPoint(lastPosition, lateralOffset = 0.2)
        } else {
            computePlacementAimPoint(lastPosition)
        }
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

    private fun computeElevatedPlacementPoint(position: BlockPos, lateralOffset: Double): Vec3 {
        val randomOffset = 0.45 + Math.random() * 0.2
        val y = position.y + 1.0
        return when (direction) {
            6 -> Vec3(position.x + randomOffset, y, position.z + 0.5 - lateralOffset)
            8 -> Vec3(position.x + 1.0 - randomOffset, y, position.z + 0.5 + lateralOffset)
            7 -> Vec3(position.x + 0.5 + lateralOffset, y, position.z + randomOffset)
            else -> Vec3(position.x + 0.5 - lateralOffset, y, position.z + 1.0 - randomOffset)
        }
    }

    private fun computeMovementTarget(initialMove: Boolean): Vec3 {
        val lastPosition = bridgePath.lastOrNull() ?: player.blockPosition()
        val distance = if (initialMove) 3 else 2
        val offset = VapeScaffoldController.offset(lastPosition, distance, direction)
        return computeTargetPosition(Vec3(offset.x.toDouble(), player.y, offset.z.toDouble()))
    }

    private fun computeTargetPosition(horizontalPosition: Vec3): Vec3 {
        val blockX = floor(horizontalPosition.x)
        val blockZ = floor(horizontalPosition.z)
        return when (direction) {
            6 -> Vec3(blockX + 0.3, player.y, blockZ + 0.6)
            8 -> Vec3(blockX + 0.7, player.y, blockZ + 0.4)
            7 -> Vec3(blockX + 0.4, player.y, blockZ + 0.3)
            else -> Vec3(blockX + 0.6, player.y, blockZ + 0.7)
        }
    }

    private fun updatePointAimTarget(force: Boolean = false) {
        val candidate = computeNextPlacementPoint() ?: return
        val previous = pointAimTarget
        if (force || pointAimDirty || previous == null || floor(previous.x) != floor(candidate.x) ||
            floor(previous.y) != floor(candidate.y) || floor(previous.z) != floor(candidate.z)
        ) {
            pointAimTarget = candidate
            pointAimDirty = false
            pointRotationSpeed = VapeScaffoldController.directionRotationSpeed(direction)
            pointSpeedInitialized = false
        }
    }

    private fun canUpdatePointYaw(): Boolean {
        val placementPosition = bridgePath.lastOrNull() ?: return false
        val previousTickX = floor(player.x - player.deltaMovement.x).toInt()
        val previousTickZ = floor(player.z - player.deltaMovement.z).toInt()
        return when (direction) {
            6 -> previousTickX > placementPosition.x
            8 -> previousTickX < placementPosition.x
            7 -> previousTickZ > placementPosition.z
            5 -> previousTickZ < placementPosition.z
            else -> false
        }
    }

    private fun computeBridgeRotation(): Rotation {
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

    private fun hasReachedPathEdge(pathPosition: BlockPos) = hasReachedPathEdge(
        direction,
        player.x,
        player.z,
        pathPosition.x,
        pathPosition.z,
    )

    internal fun hasReachedPathEdge(
        direction: Int,
        playerX: Double,
        playerZ: Double,
        pathX: Int,
        pathZ: Int,
    ): Boolean = when (direction) {
        6 -> playerX - (pathX + 0.8) >= -0.05
        8 -> playerX - (pathX - 0.2) <= 0.05
        7 -> playerZ - (pathZ + 0.8) >= -0.05
        5 -> playerZ - (pathZ - 0.2) <= 0.05
        else -> false
    }

    internal fun nextPathPosition(lastPosition: BlockPos, pathSize: Int, bridgeLevel: Int, direction: Int) =
        if (bridgeLevel != 0 && pathSize == 4) {
            lastPosition.above()
        } else {
            VapeScaffoldController.offset(lastPosition, 1, direction)
        }

    private fun hasPassedPathEdge(): Boolean {
        val pathPosition = bridgePath.lastOrNull() ?: return false
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

    private fun isAxisMotionBelowThreshold() = if (direction % 2 == 0) {
        abs(player.deltaMovement.x) < 0.6
    } else {
        abs(player.deltaMovement.z) < 0.6
    }

    private fun randomHeightIncreaseThreshold(): Int {
        val configured = ModuleScaffold.vapeYIncrease
        if (configured == 0) return 0
        val roll = Math.random()
        return when {
            roll < 0.15 -> configured + 1
            roll < 0.25 -> configured - 1
            else -> configured
        }
    }

    private fun submitMovementTask(target: Vec3, waitForGround: Boolean) {
        movementTask = TargetPositionMovementTask(target.x, target.z).also {
            it.restoreInputOnCompletion = false
            it.waitForGroundAfterArrival = waitForGround
            movementManager.submit(it)
        }
    }

    private const val PATH_RESET_SIZE = 6
}
