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
import net.ccbluex.liquidbounce.features.module.modules.render.ModuleFreeLook
import net.ccbluex.liquidbounce.features.module.modules.world.scaffold.ModuleScaffold.ScaffoldImplementation
import net.ccbluex.liquidbounce.features.module.modules.world.scaffold.vape.ScaffoldEdgeSneakHelper
import net.ccbluex.liquidbounce.features.module.modules.world.scaffold.vape.movement.MovementInputHelper
import net.ccbluex.liquidbounce.utils.aiming.RotationManager
import net.ccbluex.liquidbounce.utils.aiming.RotationTarget
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.aiming.features.processors.VapeMouseRotationState
import net.ccbluex.liquidbounce.utils.aiming.utils.RotationUtil
import net.ccbluex.liquidbounce.utils.block.targetfinding.BlockPlacementTarget
import net.ccbluex.liquidbounce.utils.input.InputTracker.isPressedOnAny
import net.ccbluex.liquidbounce.utils.item.getBlock
import net.ccbluex.liquidbounce.utils.movement.DirectionalInput
import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/** Shared dispatch and activation tracking adapted from Vape Scaffold.java. */
internal object VapeScaffoldController : MinecraftShortcuts {

    private val activation = ManualBridgeActivation()
    private val edgeSneakHelper = ScaffoldEdgeSneakHelper()
    val mouseRotationState = VapeMouseRotationState()
    private var automated = false
    private var activatedAtTick = Int.MIN_VALUE
    private var releaseMovement = false
    private var rotationClaimed = false
    private var pendingMouseDeltaX = 0.0
    private var pendingMouseDeltaY = 0.0
    private var mouseHistoryTick = Int.MIN_VALUE
    private val recentMouseMovement = ArrayDeque<Int>()

    private val activeMode: VapeScaffoldModeController
        get() = when (ModuleScaffold.mode) {
            ScaffoldImplementation.GOD_BRIDGE -> VapeGodBridgeScaffoldMode
            ScaffoldImplementation.TELLY_BRIDGE -> VapeTellyBridgeScaffoldMode
            ScaffoldImplementation.NORMAL -> error("Normal Scaffold does not have a Vape controller")
        }

    val shouldSprint get() = automated && activeMode.shouldSprint
    val scaleAxesProportionally get() = automated && activeMode.scaleAxesProportionally
    val rotationTolerance get() = if (automated) activeMode.rotationTolerance else 0f
    val isAutomated get() = automated

    fun reset() {
        automated = false
        activatedAtTick = Int.MIN_VALUE
        releaseMovement = false
        rotationClaimed = false
        mouseRotationState.reset()
        edgeSneakHelper.reset()
        resetMouseHistory()
        activation.reset()
        VapeGodBridgeScaffoldMode.reset()
        VapeTellyBridgeScaffoldMode.reset()
    }

    fun isAllowedBlock(stack: ItemStack): Boolean {
        val block = stack.getBlock() ?: return false
        return (!ModuleScaffold.vapeBlacklistEnabled || block !in ModuleScaffold.vapeBlacklist) &&
            (!ModuleScaffold.vapeWhitelistEnabled || block in ModuleScaffold.vapeWhitelist)
    }

    private fun canActivate(): Boolean {
        if (mc.gui.screen() != null || player.isFallFlying || player.abilities.flying) return false
        if (ModuleScaffold.vapePitchCheck && player.xRot < ModuleScaffold.vapePitch) return false
        if (ModuleScaffold.findPlaceableSlots().isEmpty()) return false
        if (ModuleScaffold.isTellyBridgeMode && ModuleScaffold.blockCount < 5) return false

        return true
    }

    private fun activationKeysHeld(): Boolean {
        if (!mc.options.keyDown.isPressedOnAny) return false
        return !ModuleScaffold.isTellyBridgeMode ||
            !ModuleScaffold.vapeRequireRightClick || mc.options.keyUse.isPressedOnAny
    }

    private fun updateState() {
        if (!canActivate()) {
            deactivate()
            return
        }

        if (!automated) {
            activation.update()?.let { (activationAnchor, direction) ->
                automated = true
                rotationClaimed = true
                activatedAtTick = player.tickCount
                resetMouseHistory()
                activeMode.onActivated(activationAnchor, direction)
            }
            return
        }

        val activeRotation = RotationManager.activeRotationTarget
        // A completed CHANGE_LOOK request temporarily leaves no active target. Vape keeps its
        // rotation claim during that gap; only a different live target means control was lost.
        val lostRotationControl = rotationClaimed && activeRotation != null && !activeRotation.vapeCompatible
        if (lostRotationControl) {
            deactivate()
            return
        }

        if (hasManualLookOverride()) {
            deactivate()
            return
        }

        if (!activationKeysHeld() && activeMode.canDeactivateSafely()) {
            deactivate()
            return
        }

    }

    fun canAutomate(): Boolean {
        updateState()
        return automated && player.tickCount > activatedAtTick && activeMode.readyToPlace
    }

    fun canRotate(): Boolean {
        updateState()
        return automated
    }

    /** Vape clicks using the old path phase, then advances the controller from confirmed world state. */
    fun finishPlacementTick() {
        updateState()
        if (automated) activeMode.update()
    }

    fun onRotationSubmitted(rotationTarget: RotationTarget) {
        rotationClaimed = rotationTarget.vapeCompatible
    }

    fun onRotationReleased() {
        rotationClaimed = false
        mouseRotationState.reset()
    }

    fun resetRotationIntegrator() {
        mouseRotationState.reset()
    }

    fun onMouseRotation(deltaX: Double, deltaY: Double) {
        if (!automated) return
        pendingMouseDeltaX += deltaX
        pendingMouseDeltaY += deltaY
    }

    fun onManualPlacementRequest(packet: ServerboundUseItemOnPacket) {
        if (automated || !canActivate()) return

        val stack = player.getItemInHand(packet.hand)
        if (!isAllowedBlock(stack) || stack.getBlock() == null) return

        val placement = BlockPlaceContext(UseOnContext(player, packet.hand, packet.hitResult)).clickedPos
        activation.record(placement)
    }

    fun targetedPosition(default: BlockPos): BlockPos =
        if (automated) activeMode.targetedPosition(default) else default

    fun handleMovement(event: MovementInputEvent) {
        updateState()
        if (releaseMovement) {
            event.directionalInput = DirectionalInput.NONE
            event.jump = false
            releaseMovement = false
            return
        }

        if (automated) {
            activeMode.handleMovement(event)
        } else {
            handleManualActivationMovement(event)
        }
    }

    fun rotationFor(target: BlockPlacementTarget?): Rotation? =
        if (automated) activeMode.rotationFor(target) else null

    fun isValidPlacementHit(hitResult: BlockHitResult): Boolean =
        automated && hitResult.type == HitResult.Type.BLOCK && activeMode.isValidPlacementHit(hitResult)

    fun rotationSpeed(rotation: Rotation): Float = if (automated) {
        activeMode.rotationSpeed(rotation)
    } else {
        defaultRotationSpeed(rotation)
    }

    internal fun defaultRotationSpeed(rotation: Rotation): Float {
        val yawDistance = abs(RotationUtil.angleDifference(rotation.yaw, player.yRot))
        return (2f + yawDistance / 8f).coerceAtMost(12f)
    }

    internal fun directionRotationSpeed(direction: Int): Float {
        val targetYaw = when (direction) {
            6 -> 90f
            8 -> 270f
            7 -> 0f
            5 -> 180f
            else -> player.yRot
        }
        val yawDistance = abs(RotationUtil.angleDifference(targetYaw, player.yRot))
        return (2f + yawDistance / 8f).coerceAtMost(12f)
    }

    internal fun cardinalDirection(): Int {
        val yaw = ((player.yRot + 180f) % 360f + 360f) % 360f
        return when {
            yaw > 315f || yaw <= 45f -> 7
            yaw <= 135f -> 8
            yaw <= 225f -> 5
            else -> 6
        }
    }

    internal fun offset(position: BlockPos, distance: Int, direction: Int): BlockPos = when (direction) {
        1 -> position.offset(distance, 0, distance)
        2 -> position.offset(-distance, 0, distance)
        3 -> position.offset(-distance, 0, -distance)
        4 -> position.offset(distance, 0, -distance)
        5 -> position.offset(0, 0, -distance)
        6 -> position.offset(distance, 0, 0)
        7 -> position.offset(0, 0, distance)
        8 -> position.offset(-distance, 0, 0)
        else -> position
    }

    /** Greedy four-key waypoint steering used by Vape's TargetPositionMovementTask. */
    internal fun movementInputToward(target: Vec3) = MovementInputHelper.applyMovementToward(
        targetOffsetX = target.x - player.x,
        targetOffsetZ = target.z - player.z,
    )

    // Vape treats an exact half-block height specially and otherwise targets one full block below the feet.
    internal fun placementY(): Int {
        val playerY = player.y
        return if (abs(playerY - playerY.toInt()) == 0.5) {
            floor(playerY).toInt()
        } else {
            floor(playerY - 1.0).toInt()
        }
    }

    internal fun isAir(position: BlockPos) = world.getBlockState(position).isAir

    internal fun angleDistance(first: Float, second: Float) =
        abs(RotationUtil.angleDifference(first, second))

    private fun deactivate() {
        releaseMovement = releaseMovement || automated
        automated = false
        activatedAtTick = Int.MIN_VALUE
        rotationClaimed = false
        mouseRotationState.reset()
        edgeSneakHelper.reset()
        resetMouseHistory()
        activation.reset()
        VapeGodBridgeScaffoldMode.reset()
        VapeTellyBridgeScaffoldMode.reset()
    }

    private fun hasManualLookOverride(): Boolean {
        if (mouseHistoryTick != player.tickCount) {
            mouseHistoryTick = player.tickCount
            recentMouseMovement.addFirst(abs(pendingMouseDeltaX).roundToInt())
            recentMouseMovement.addFirst(abs(pendingMouseDeltaY).roundToInt())
            pendingMouseDeltaX = 0.0
            pendingMouseDeltaY = 0.0
            while (recentMouseMovement.size > MOUSE_HISTORY_SIZE) {
                recentMouseMovement.removeLast()
            }
        }
        return !ModuleFreeLook.running && recentMouseMovement.sum() >= MANUAL_LOOK_THRESHOLD
    }

    private fun resetMouseHistory() {
        pendingMouseDeltaX = 0.0
        pendingMouseDeltaY = 0.0
        mouseHistoryTick = Int.MIN_VALUE
        recentMouseMovement.clear()
    }

    private fun handleManualActivationMovement(event: MovementInputEvent) {
        edgeSneakHelper.apply(event)
    }

    private class ManualBridgeActivation {
        private data class PendingPlacement(val position: BlockPos, val expiresAt: Int)

        private val pending = ArrayDeque<PendingPlacement>()
        private var direction = 0
        private var lastPlacement: BlockPos? = null
        private var blocksPlaced = 0

        fun reset() {
            direction = 0
            pending.clear()
            lastPlacement = null
            blocksPlaced = 0
        }

        fun record(position: BlockPos) {
            if (!player.onGround() || abs(position.y - placementY()) > 1 ||
                position.distToCenterSqr(player.position()) > 16.0
            ) return

            pending.removeAll { it.position == position }
            pending.addLast(PendingPlacement(position.immutable(), player.tickCount + PLACEMENT_CONFIRM_TICKS))
        }

        fun update(): Pair<BlockPos, Int>? {
            if (ModuleScaffold.findPlaceableSlots().isEmpty()) {
                reset()
                return null
            }

            pending.removeAll { player.tickCount > it.expiresAt }
            while (pending.isNotEmpty()) {
                val confirmedIndex = pending.indexOfFirst { !isAir(it.position) }
                if (confirmedIndex < 0) return null

                // A newer confirmed placement proves that older still-air attempts failed.
                repeat(confirmedIndex) { pending.removeFirst() }
                val candidate = pending.removeFirst()

                val currentDirection = cardinalDirection()
                val expected = lastPlacement?.let { offset(it, 1, currentDirection) }
                val playerBlock = BlockPos(floor(player.x).toInt(), placementY(), floor(player.z).toInt())
                if (direction != currentDirection || expected != null && expected != candidate.position ||
                    hasPlacementDrifted(candidate.position, playerBlock, currentDirection)
                ) {
                    blocksPlaced = 0
                    lastPlacement = null
                }

                direction = currentDirection
                lastPlacement = candidate.position
                blocksPlaced++

                if (blocksPlaced >= ModuleScaffold.vapeActivationBlocks) {
                    val activationAnchor = candidate.position
                    reset()
                    return activationAnchor to currentDirection
                }
            }
            return null
        }

        private fun hasPlacementDrifted(placement: BlockPos, playerBlock: BlockPos, direction: Int): Boolean {
            if (direction > 4 && if (direction % 2 == 0) placement.z != playerBlock.z else placement.x != playerBlock.x) {
                return true
            }
            if (direction < 5 && (kotlin.math.abs(placement.x - playerBlock.x) >= 4 ||
                    kotlin.math.abs(placement.z - playerBlock.z) >= 4)) return true
            if (placement.y != playerBlock.y) return true

            val origin = offset(placement, -blocksPlaced, direction)
            return origin.distToCenterSqr(player.position()) > (ModuleScaffold.vapeActivationBlocks + 2.0) *
                (ModuleScaffold.vapeActivationBlocks + 2.0)
        }

        private companion object {
            const val PLACEMENT_CONFIRM_TICKS = 20
        }
    }

    private const val MOUSE_HISTORY_SIZE = 6
    private const val MANUAL_LOOK_THRESHOLD = 10
}

internal interface VapeScaffoldModeController {
    val readyToPlace: Boolean
    val shouldSprint: Boolean get() = false
    val scaleAxesProportionally: Boolean get() = true
    val rotationTolerance: Float get() = 0.5f
    fun reset()
    fun onActivated(activationAnchor: BlockPos, direction: Int)
    fun update() = Unit
    fun targetedPosition(default: BlockPos) = default
    fun handleMovement(event: MovementInputEvent)
    fun rotationFor(target: BlockPlacementTarget?): Rotation?
    fun rotationSpeed(rotation: Rotation) = VapeScaffoldController.defaultRotationSpeed(rotation)
    fun isValidPlacementHit(hitResult: BlockHitResult): Boolean
    fun canDeactivateSafely(): Boolean
}
