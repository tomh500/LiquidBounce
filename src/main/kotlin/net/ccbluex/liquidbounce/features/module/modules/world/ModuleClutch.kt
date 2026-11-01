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
package net.ccbluex.liquidbounce.features.module.modules.world

import net.ccbluex.liquidbounce.event.EventManager
import net.ccbluex.liquidbounce.event.events.BlockCountChangeEvent
import net.ccbluex.liquidbounce.event.events.GameTickEvent
import net.ccbluex.liquidbounce.event.events.MovementInputEvent
import net.ccbluex.liquidbounce.event.events.RotationUpdateEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.EventListener
import net.ccbluex.liquidbounce.event.events.WorldChangeEvent
import net.ccbluex.liquidbounce.features.global.GlobalVapeRotationSettings
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.features.module.ModuleOrigin
import net.ccbluex.liquidbounce.features.module.modules.world.clutch.ClutchTargetPlanner
import net.ccbluex.liquidbounce.features.module.modules.world.scaffold.ScaffoldBlockItemSelection
import net.ccbluex.liquidbounce.utils.aiming.RotationManager
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.aiming.utils.RotationUtil
import net.ccbluex.liquidbounce.utils.block.fallDamageMultiplier
import net.ccbluex.liquidbounce.utils.block.isUnobstructed
import net.ccbluex.liquidbounce.utils.block.placer.BlockPlacer
import net.ccbluex.liquidbounce.utils.block.placer.VapeBlockPlacerRotation
import net.ccbluex.liquidbounce.utils.collection.blockSortedSetOf
import net.ccbluex.liquidbounce.utils.client.SilentHotbar
import net.ccbluex.liquidbounce.utils.entity.FallingPlayer
import net.ccbluex.liquidbounce.utils.entity.getEffectiveDamage
import net.ccbluex.liquidbounce.utils.entity.rotation
import net.ccbluex.liquidbounce.utils.inventory.HotbarItemSlot
import net.ccbluex.liquidbounce.utils.inventory.Slots
import net.ccbluex.liquidbounce.utils.input.InputTracker.isPressedOnAny
import net.ccbluex.liquidbounce.utils.item.getBlock
import net.ccbluex.liquidbounce.utils.kotlin.Priority
import net.ccbluex.liquidbounce.utils.kotlin.random
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.tags.BlockTags
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import kotlin.math.abs
import kotlin.math.max

/** Vape-style fall rescue rebuilt on the current collision and placement APIs. */
object ModuleClutch : ClientModule(
    "Clutch",
    ModuleCategories.WORLD,
    disableOnQuit = true,
    aliases = listOf("VapeClutch", "FallClutch"),
    origin = ModuleOrigin.XUAN_RIKKA,
) {

    private val onVoid by boolean("OnVoid", true)
    private val onLethalFall by boolean("OnLethalFall", true)
    private val onMoreThanXBlocks by boolean("OnMoreThanXBlocks", false)
    private val blocksThreshold by int("Blocks", 6, 3..10)
        .visibleWhen { onMoreThanXBlocks }
    private val speed by float("Speed", 3.5f, 1f..10f)
    private val silentAim by boolean("SilentAim", false)
    private val resetAngle by boolean("ResetAngle", true)
        .visibleWhen { !silentAim }
    private val resetAngleDelay by intRange("ResetAngleDelay", 3..6, 0..10, "ticks")
        .visibleWhen { !silentAim && resetAngle }
    private val returnToLastSlot by boolean("ReturnToLastSlot", true)
    private val returnDelay by intRange("ReturnDelay", 3..6, 0..10, "ticks")
        .visibleWhen { returnToLastSlot }
    private val clutchMoveDelay by intRange("ClutchMoveDelay", 3..6, 0..10, "ticks")
    private val failDelay by int("FailDelay", 100, 0..500, "ms")
    private val allowStaircaseUp by boolean("AllowStaircaseUp", true)
    private val showBlockCount by boolean("ShowBlockCount", false)
    private val limitBlocks by boolean("LimitBlocks", false)
    private val maxBlocks by int("MaxBlocks", 5, 1..10)
        .visibleWhen { limitBlocks }
    private val blacklist by boolean("Blacklist", true)
    private val blacklistBlocks by blocks("BlockBlacklist", defaultBlacklist())
        .visibleWhen { blacklist }
    private val heldWhitelist by boolean("HeldWhitelist", false)
    private val whitelistBlocks by blocks("BlockWhitelist", blockSortedSetOf())
        .visibleWhen { heldWhitelist }

    private val placer = tree(BlockPlacer(
        "VapePlacer",
        this,
        Priority.IMPORTANT_FOR_PLAYER_LIFE,
        ::slotFinder,
        vapeRotation = {
            VapeBlockPlacerRotation(speed, silentAim, ticksUntilTarget = {
                (ticksUntilImpact - remainingPlacements + 1).coerceAtLeast(1)
            })
        },
        cooldownOverride = { 0 },
        supportDelayOverride = { 0 },
    )).apply {
        doNotIncludeAlways()
        visibleWhen { false }
    }

    private var clutching = false
    private var queuedBlocks = 0
    private var lastAttempt = 0L
    private var previousSlot: Int? = null
    private var returnTicks = -1
    private var moveDelayTicks = 0
    private var staircaseTicks = 0
    private var originalRotation: Rotation? = null
    private var resetRotationTicks = -1
    private var resetRotationTarget: Rotation? = null
    private var showingBlockCount = false
    private var ticksUntilImpact = 1
    private var remainingPlacements = 0
    private var catchTarget: BlockPos? = null

    override fun onEnabled() = resetState(clearRenderCount = false)

    @Suppress("unused")
    private val worldChangeListener = object : EventListener {
        private val worldChangeHandler = handler<WorldChangeEvent> {
            // Old slots, landing positions and camera resets belong to the previous world.
            // Do not restore the previous world's selected slot into the new inventory.
            resetState(clearRenderCount = true)
        }
    }

    override fun onDisabled() {
        restoreSlotNow()
        placer.disable()
        resetState(clearRenderCount = true)
    }

    private fun resetState(clearRenderCount: Boolean) {
        clutching = false
        queuedBlocks = 0
        lastAttempt = 0L
        returnTicks = -1
        moveDelayTicks = 0
        staircaseTicks = 0
        originalRotation = null
        resetRotationTicks = -1
        resetRotationTarget = null
        previousSlot = null
        ticksUntilImpact = 1
        remainingPlacements = 0
        catchTarget = null
        placer.clear()
        SilentHotbar.resetSlot(placer)
        if (clearRenderCount) EventManager.callEvent(BlockCountChangeEvent(null, null))
        showingBlockCount = false
    }

    @Suppress("unused")
    private val rotationHandler = handler<RotationUpdateEvent> {
        // Plan before BlockPlacer's rotation update, so activation does not cost a fall tick.
        if (player.onGround()) {
            if (clutching) finishClutch()
        } else if (!clutching) {
            tryStartClutch()
        }

        if (clutching) {
            val plan = findPlacementPlan()
            if (plan == null) {
                failAttempt()
            } else {
                catchTarget = plan.target.pos
                ticksUntilImpact = plan.target.ticksUntilImpact
                remainingPlacements = plan.placements.size
                queuedBlocks = max(queuedBlocks, remainingPlacements)
                placer.update(plan.placements)
            }
            return@handler
        }

        val target = resetRotationTarget ?: return@handler
        if (resetRotationTicks > 0) {
            resetRotationTicks--
            return@handler
        }

        RotationManager.setRotationTarget(
            GlobalVapeRotationSettings.rotationTarget(
                target,
                speed = { resetSpeed(target) },
                silentAim = false,
            ),
            priority = Priority.IMPORTANT_FOR_PLAYER_LIFE,
            provider = this@ModuleClutch,
        )
        val current = RotationManager.serverRotation
        if (abs(RotationUtil.angleDifference(target.yaw, current.yaw)) < 1f && abs(target.pitch - current.pitch) < 1f) {
            resetRotationTarget = null
        }
    }

    @Suppress("unused")
    private val tickHandler = handler<GameTickEvent> {
        updateDelayedActions()

        if (showBlockCount) {
            EventManager.callEvent(BlockCountChangeEvent(findBestBlockSlot()?.itemStack?.getBlock(), countValidBlocks()))
            showingBlockCount = true
        } else if (showingBlockCount) {
            EventManager.callEvent(BlockCountChangeEvent(null, null))
            showingBlockCount = false
        }

        if (player.onGround() && staircaseTicks > 0) staircaseTicks--
    }

    private fun tryStartClutch() {
        if (System.currentTimeMillis() - lastAttempt < failDelay || !shouldActivate()) return
        if (findBestBlockSlot() == null) {
            failAttempt()
            return
        }

        clutching = true
        queuedBlocks = 0
        originalRotation = player.rotation
        resetRotationTarget = null
        returnTicks = -1
    }

    @Suppress("unused")
    private val movementHandler = handler<MovementInputEvent> { event ->
        if (moveDelayTicks > 0) {
            event.directionalInput = net.ccbluex.liquidbounce.utils.movement.DirectionalInput.NONE
            event.jump = false
        }
    }

    private fun shouldActivate(): Boolean {
        if (player.isFallFlying || player.abilities.flying || mc.gui.screen() != null) return false
        if (allowStaircaseUp && staircaseTicks > 0 && mc.options.keyJump.isPressedOnAny) return true
        val collision = FallingPlayer.fromPlayer(player, RotationManager.movementYaw).findCollision(PREDICTION_TICKS)
        val landing = collision?.pos
        if (landing == null) return onVoid

        val drop = (player.y - landing.y - 1.0).coerceAtLeast(player.fallDistance.toDouble())
        val exceedsThreshold = onMoreThanXBlocks && drop >= blocksThreshold
        val multiplier = landing.fallDamageMultiplier(player)
        val rawDamage = player.calculateFallDamage(drop, multiplier).toFloat()
        val effectiveDamage = player.getEffectiveDamage(player.damageSources().fall(), rawDamage)
        val lethal = onLethalFall && effectiveDamage >= player.health + player.absorptionAmount
        return lethal || exceedsThreshold
    }

    private fun findPlacementPlan(): ClutchTargetPlanner.Plan? {
        val trajectory = FallingPlayer.fromPlayer(player, RotationManager.movementYaw)
            .predictTrajectory(PREDICTION_TICKS)
        val targets = ClutchTargetPlanner.catchTargets(trajectory, player.bbWidth.toDouble())
        // Support search must not exclude a support just because it was in last tick's placement queue.
        placer.clear()
        val placementLimit = if (limitBlocks) maxBlocks else Int.MAX_VALUE
        return ClutchTargetPlanner.selectPlan(targets, catchTarget, placementLimit, ::findCatchPath)
    }

    private fun findCatchPath(target: BlockPos): List<BlockPos>? {
        if (!world.getBlockState(target).canBeReplaced()) {
            // The catch was placed successfully. Keep waiting for its predicted landing frame.
            return if (target == catchTarget) emptyList() else null
        }
        val reach = placer.range.toDouble() + 1.0
        if (target.distToCenterSqr(player.eyePosition) > reach * reach || !target.isUnobstructed()) return null
        if (placer.canClickPlace(target)) return listOf(target)

        val path = placer.support.findSupport(target)?.toList()?.asReversed() ?: return null
        // A solid neighbor alone is insufficient: its placement face must be reachable and visible.
        return path.takeIf { it.isNotEmpty() && placer.canClickPlace(it.first()) }
    }

    private fun finishClutch() {
        clutching = false
        placer.clear()
        catchTarget = null
        remainingPlacements = 0
        SilentHotbar.resetSlot(placer)
        staircaseTicks = if (allowStaircaseUp) STAIRCASE_WINDOW_TICKS else 0
        if (queuedBlocks > 1) moveDelayTicks = clutchMoveDelay.random()
        queuedBlocks = 0

        if (returnToLastSlot && previousSlot != null) returnTicks = returnDelay.random()
        if (!silentAim && resetAngle) {
            resetRotationTarget = originalRotation
            resetRotationTicks = resetAngleDelay.random()
        }
        originalRotation = null
    }

    private fun failAttempt() {
        clutching = false
        queuedBlocks = 0
        placer.clear()
        catchTarget = null
        remainingPlacements = 0
        SilentHotbar.resetSlot(placer)
        lastAttempt = System.currentTimeMillis()
        if (returnToLastSlot && previousSlot != null) returnTicks = returnDelay.random()
    }

    private fun updateDelayedActions() {
        if (moveDelayTicks > 0) moveDelayTicks--
        if (returnTicks > 0) {
            returnTicks--
        } else if (returnTicks == 0) {
            restoreSlotNow()
            returnTicks = -1
        }
    }

    private fun restoreSlotNow() {
        val slot = previousSlot ?: return
        if (returnToLastSlot) mc.player?.inventory?.selectedSlot = slot
        previousSlot = null
    }

    private fun slotFinder(@Suppress("UNUSED_PARAMETER") pos: BlockPos?): HotbarItemSlot? {
        val slot = findBestBlockSlot() ?: return null
        val selected = player.inventory.selectedSlot
        val hotbarIndex = slot.hotbarIndex ?: return slot
        if (hotbarIndex != selected) {
            if (previousSlot == null) previousSlot = selected
            player.inventory.selectedSlot = hotbarIndex
        }
        return slot
    }

    private fun findBestBlockSlot(): HotbarItemSlot? {
        val selected = player.inventory.selectedSlot
        val slots = if (heldWhitelist) {
            listOf(Slots.Hotbar[selected]).filter { isValidBlock(it) && it.itemStack.getBlock() in whitelistBlocks }
        } else {
            Slots.Hotbar.filter(::isValidBlock)
        }
        return slots.maxWithOrNull(
            compareBy<HotbarItemSlot> { vapePriority(it.itemStack.getBlock()) }
                .thenBy { it.itemStack.count }
        )
    }

    private fun isValidBlock(slot: HotbarItemSlot): Boolean {
        val stack = slot.itemStack
        val block = stack.getBlock() ?: return false
        return ScaffoldBlockItemSelection.isValidBlock(stack) && (!blacklist || block !in blacklistBlocks)
    }

    private fun countValidBlocks() = Slots.Hotbar.sumOf { slot ->
        if (isValidBlock(slot) && (!heldWhitelist || slot.hotbarIndex == player.inventory.selectedSlot &&
                slot.itemStack.getBlock() in whitelistBlocks)) slot.itemStack.count else 0
    }

    private fun vapePriority(block: Block?): Int {
        block ?: return 0
        val state = block.defaultBlockState()
        val id = BuiltInRegistries.BLOCK.getKey(block).path
        return when {
            state.`is`(BlockTags.WOOL) -> 7
            id.contains("stone") && !id.contains("sandstone") -> 6
            state.`is`(BlockTags.PLANKS) -> 5
            id.contains("sandstone") -> 4
            id.contains("terracotta") -> 3
            block === Blocks.END_STONE -> 2
            block === Blocks.OBSIDIAN -> 1
            else -> 0
        }
    }

    private fun resetSpeed(target: Rotation): Float {
        val yawDistance = abs(RotationUtil.angleDifference(target.yaw, RotationManager.serverRotation.yaw))
        return max(1f, yawDistance / 90f * 5f)
    }

    private fun defaultBlacklist() = blockSortedSetOf(
        Blocks.TNT,
        Blocks.COBWEB,
        Blocks.DISPENSER,
        Blocks.NOTE_BLOCK,
        Blocks.SPAWNER,
        Blocks.ENCHANTING_TABLE,
        Blocks.CHEST,
        Blocks.TRAPPED_CHEST,
        Blocks.HOPPER,
        Blocks.CACTUS,
        Blocks.ICE,
        Blocks.PACKED_ICE,
    )

    private const val PREDICTION_TICKS = 50
    private const val STAIRCASE_WINDOW_TICKS = 10
}
