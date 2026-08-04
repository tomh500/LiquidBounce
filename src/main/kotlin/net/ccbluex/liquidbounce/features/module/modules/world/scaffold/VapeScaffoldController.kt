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
import net.ccbluex.liquidbounce.features.module.modules.world.scaffold.ModuleScaffold.ScaffoldImplementation
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
        get() = when (ModuleScaffold.mode) {
            ScaffoldImplementation.GOD_BRIDGE -> VapeGodBridgeScaffoldMode
            ScaffoldImplementation.TELLY_BRIDGE -> VapeTellyBridgeScaffoldMode
            ScaffoldImplementation.NORMAL -> error("Normal Scaffold does not have a Vape controller")
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
        if (ModuleScaffold.isTellyBridgeMode && ModuleScaffold.blockCount < 5) return false

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
        return !ModuleScaffold.isTellyBridgeMode ||
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
            return
        }

        if (!activationKeysHeld() && player.onGround()) {
            deactivate()
        }
    }

    fun canAutomate(): Boolean {
        updateState()
        return automated && activeMode.readyToPlace
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
        private var direction = 0
        private var placement: BlockPos? = null
        private var blocksPlaced = 0

        fun reset() {
            direction = 0
            placement = null
            blocksPlaced = 0
        }

        fun update(): Pair<BlockPos, Int>? {
            if (ModuleScaffold.findPlaceableSlots().isEmpty()) {
                reset()
                return null
            }

            val currentDirection = cardinalDirection()
            if (direction != 0 && currentDirection != direction) {
                placement = null
                blocksPlaced = 0
            }
            direction = currentDirection

            val playerBlock = BlockPos(floor(player.x).toInt(), placementY(), floor(player.z).toInt())
            val currentPlacement = placement
            if (currentPlacement == null && player.onGround()) {
                placement = sequenceOf(
                    playerBlock,
                    offset(playerBlock, 1, direction),
                    offset(playerBlock, 2, direction),
                ).firstOrNull(::isAir)
                return null
            }

            if (currentPlacement == null) return null
            if (blocksPlaced >= ModuleScaffold.vapeActivationBlocks) {
                val nextPlacement = offset(currentPlacement, 1, direction)
                reset()
                return nextPlacement to direction
            }

            if (!isAir(currentPlacement)) {
                blocksPlaced++
                val nextPlacement = offset(currentPlacement, 1, direction)
                // Vape leaves the final manually placed position intact. On the next tick the
                // activation-count branch consumes it and hands the following block to the bridge mode.
                placement = if (blocksPlaced >= ModuleScaffold.vapeActivationBlocks) {
                    currentPlacement
                } else if (isAir(nextPlacement)) {
                    nextPlacement
                } else {
                    null
                }
            } else if (hasPlacementDrifted(currentPlacement, playerBlock)) {
                placement = null
                blocksPlaced = 0
            }
            return null
        }

        private fun hasPlacementDrifted(placement: BlockPos, playerBlock: BlockPos): Boolean {
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
