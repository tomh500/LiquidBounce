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
import net.ccbluex.liquidbounce.features.module.modules.world.scaffold.ModuleScaffold.VapeScaffoldMode
import net.ccbluex.liquidbounce.utils.aiming.RotationManager
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.aiming.utils.RotationUtil
import net.ccbluex.liquidbounce.utils.block.targetfinding.BlockPlacementTarget
import net.ccbluex.liquidbounce.utils.input.InputTracker.isPressedOnAny
import net.ccbluex.liquidbounce.utils.item.getBlock
import net.ccbluex.liquidbounce.utils.movement.DirectionalInput
import net.ccbluex.liquidbounce.utils.movement.getDegreesRelativeToView
import net.ccbluex.liquidbounce.utils.movement.getDirectionalInputForDegrees
import net.minecraft.core.BlockPos
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.floor

/** Shared dispatch and activation tracking adapted from Vape Scaffold.java. */
internal object VapeScaffoldController : MinecraftShortcuts {

    private val activation = ManualBridgeActivation()
    private var automated = false

    private val activeMode: VapeScaffoldModeController
        get() = when (ModuleScaffold.vapeMode) {
            VapeScaffoldMode.GOD_BRIDGE -> VapeGodBridgeScaffoldMode
            VapeScaffoldMode.TELLY_BRIDGE -> VapeTellyBridgeScaffoldMode
        }

    val shouldSprint get() = automated && activeMode.shouldSprint

    fun reset() {
        automated = false
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
        if (ModuleScaffold.vapeMode == VapeScaffoldMode.TELLY_BRIDGE && ModuleScaffold.blockCount < 5) return false

        if (ModuleScaffold.vapeWhitelistEnabled) {
            val heldAllowed = InteractionHand.entries.any { hand ->
                player.getItemInHand(hand).getBlock() in ModuleScaffold.vapeWhitelist
            }
            if (!heldAllowed) return false
        }
        return true
    }

    private fun activationKeysHeld(): Boolean {
        if (!mc.options.keyDown.isPressedOnAny) return false
        return ModuleScaffold.vapeMode != VapeScaffoldMode.TELLY_BRIDGE ||
            !ModuleScaffold.vapeRequireRightClick || mc.options.keyUse.isPressedOnAny
    }

    private fun updateState() {
        if (!canActivate()) {
            deactivate()
            return
        }

        if (!automated) {
            activation.update()?.let { (nextPlacement, direction) ->
                automated = true
                activeMode.onActivated(nextPlacement, direction)
            }
        }

        if (!activationKeysHeld() && (!automated || player.onGround())) {
            deactivate()
        }
    }

    fun canAutomate(): Boolean {
        updateState()
        return automated && activeMode.readyToPlace
    }

    fun onManualPlacement(placed: BlockPos) {
        if (automated || !canActivate() || !activationKeysHeld()) return
        activation.record(placed)
    }

    fun onAutomatedPlacement(placed: BlockPos) {
        if (automated) activeMode.onPlacement(placed)
    }

    fun targetedPosition(default: BlockPos): BlockPos =
        if (automated) activeMode.targetedPosition(default) else default

    fun handleMovement(event: MovementInputEvent) {
        updateState()
        if (automated) activeMode.handleMovement(event)
    }

    fun rotationFor(target: BlockPlacementTarget?): Rotation? =
        if (automated && activeMode.readyToPlace) activeMode.rotationFor(target) else null

    fun rotationSpeed(rotation: Rotation): Float {
        val yawDistance = abs(RotationUtil.angleDifference(rotation.yaw, RotationManager.serverRotation.yaw))
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

    internal fun movementInputToward(target: Vec3): DirectionalInput {
        val degrees = getDegreesRelativeToView(target.subtract(player.position()), player.yRot)
        return getDirectionalInputForDegrees(DirectionalInput.NONE, degrees, deadAngle = 20f)
    }

    // Player Y is the feet coordinate; subtracting a tiny epsilon selects the block below the feet.
    internal fun placementY() = floor(player.y - 0.01).toInt()

    internal fun isAir(position: BlockPos) = world.getBlockState(position).isAir

    internal fun angleDistance(first: Float, second: Float) =
        abs(RotationUtil.angleDifference(first, second))

    private fun deactivate() {
        automated = false
        activation.reset()
        VapeGodBridgeScaffoldMode.reset()
        VapeTellyBridgeScaffoldMode.reset()
    }

    private class ManualBridgeActivation {
        private data class PendingPlacement(val position: BlockPos, val expiresAt: Int)

        private val pending = ArrayDeque<PendingPlacement>()
        private var direction = 0
        private var lastPlacement: BlockPos? = null
        private var blocksPlaced = 0

        fun reset() {
            pending.clear()
            direction = 0
            lastPlacement = null
            blocksPlaced = 0
        }

        fun record(position: BlockPos) {
            if (!player.onGround()) return
            if (abs(position.y - placementY()) > 1) return
            if (position.distToCenterSqr(player.position()) > 16.0) return
            pending.removeAll { it.position == position }
            pending.addLast(PendingPlacement(position.immutable(), player.tickCount + 10))
        }

        fun update(): Pair<BlockPos, Int>? {
            while (pending.isNotEmpty()) {
                val candidate = pending.first()
                if (isAir(candidate.position)) {
                    if (player.tickCount <= candidate.expiresAt) return null
                    pending.removeFirst()
                    continue
                }

                pending.removeFirst()
                val currentDirection = cardinalDirection()
                val expected = lastPlacement?.let { offset(it, 1, currentDirection) }
                if (direction != currentDirection || expected != null && expected != candidate.position) {
                    blocksPlaced = 0
                    lastPlacement = null
                }
                direction = currentDirection
                lastPlacement = candidate.position
                blocksPlaced++

                if (blocksPlaced >= ModuleScaffold.vapeActivationBlocks) {
                    val nextPlacement = offset(candidate.position, 1, direction)
                    pending.clear()
                    return nextPlacement to direction
                }
            }
            return null
        }
    }
}

internal interface VapeScaffoldModeController {
    val readyToPlace: Boolean
    val shouldSprint: Boolean get() = false
    fun reset()
    fun onActivated(nextPlacement: BlockPos, direction: Int)
    fun onPlacement(placed: BlockPos) = Unit
    fun targetedPosition(default: BlockPos) = default
    fun handleMovement(event: MovementInputEvent)
    fun rotationFor(target: BlockPlacementTarget?): Rotation?
}
