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

import it.unimi.dsi.fastutil.objects.ObjectArraySet
import net.ccbluex.liquidbounce.config.types.list.Tagged
import net.ccbluex.liquidbounce.event.events.MovementInputEvent
import net.ccbluex.liquidbounce.event.events.NotificationEvent
import net.ccbluex.liquidbounce.event.events.PlayerMovementTickEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.event.tickUntil
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.features.module.ModuleOrigin
import net.ccbluex.liquidbounce.features.module.modules.render.ModuleDebug.debugParameter
import net.ccbluex.liquidbounce.features.module.modules.world.scaffold.ScaffoldBlockItemSelection
import net.ccbluex.liquidbounce.utils.block.placer.BlockPlacer
import net.ccbluex.liquidbounce.utils.block.placer.VapeBlockPlacerRotation
import net.ccbluex.liquidbounce.utils.client.notification
import net.ccbluex.liquidbounce.utils.collection.Filter
import net.ccbluex.liquidbounce.utils.collection.blockSortedSetOf
import net.ccbluex.liquidbounce.utils.inventory.HotbarItemSlot
import net.ccbluex.liquidbounce.utils.inventory.Slots
import net.ccbluex.liquidbounce.utils.item.getBlock
import net.ccbluex.liquidbounce.utils.input.InputTracker.isPressedOnAny
import net.ccbluex.liquidbounce.utils.kotlin.Priority
import net.ccbluex.liquidbounce.utils.kotlin.random
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.tags.BlockTags
import net.minecraft.util.Mth
import net.minecraft.world.level.block.BedBlock
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import kotlin.math.roundToInt
import kotlin.random.Random

/** Builds blocks around the player using LiquidBounce or Vape behavior. */
object ModuleBlockIn : ClientModule(
    "BlockIn",
    ModuleCategories.WORLD,
    disableOnQuit = true,
    origin = ModuleOrigin.LIQUID_BOUNCE_MODIFIED,
) {

    private val mode by enumChoice("Mode", BlockInMode.LIQUID_BOUNCE)
        .apply(::tagBy)
        .onChanged { reconfigureMode() }

    private val lbPlacer = tree(BlockPlacer("Placer", this, Priority.NORMAL, ::slotFinder)).also {
        it.visibleWhen { mode == BlockInMode.LIQUID_BOUNCE }
    }
    private val vapePlacer = tree(BlockPlacer(
        "VapePlacer",
        this,
        Priority.IMPORTANT_FOR_PLAYER_LIFE,
        ::slotFinder,
        vapeRotation = {
            VapeBlockPlacerRotation(vapeAimSpeed, vapeSilentAim)
        },
        cooldownOverride = {
            (vapePlaceDelay.random() / MILLIS_PER_TICK.toFloat()).roundToInt()
        },
    )).apply {
        doNotIncludeAlways()
        visibleWhen { false }
    }

    private val autoDisable by boolean("AutoDisable", true)
        .visibleWhen { mode == BlockInMode.LIQUID_BOUNCE }
    private val placeOrder by enumChoice("PlaceOrder", Order.NORMAL)
        .visibleWhen { mode == BlockInMode.LIQUID_BOUNCE }
    private val filter by enumChoice("Filter", Filter.BLACKLIST)
        .visibleWhen { mode == BlockInMode.LIQUID_BOUNCE }
    private val blocks by blocks("Blocks", blockSortedSetOf())
        .visibleWhen { mode == BlockInMode.LIQUID_BOUNCE }

    private val vapeSilentAim by boolean("SilentAim", false)
        .visibleWhen { mode == BlockInMode.VAPE }
    private val vapeSneak by boolean("Sneak", false)
        .visibleWhen { mode == BlockInMode.VAPE }
    private val vapeKeepSneak by boolean("KeepSneak", true)
        .visibleWhen { mode == BlockInMode.VAPE && vapeSneak }
    private val vapeBedFinder by boolean("BedFinder", true)
        .visibleWhen { mode == BlockInMode.VAPE }
    private val vapeBlockPriority by enumChoice("BlockPriority", VapeBlockPriority.LOWEST_COST)
        .visibleWhen { mode == BlockInMode.VAPE }
    private val vapePlaceDelay by intRange("PlaceDelay", 0..30, 0..250, "ms")
        .visibleWhen { mode == BlockInMode.VAPE }
    private val vapeAimSpeed by float("AimSpeed", 12f, 1f..25f)
        .visibleWhen { mode == BlockInMode.VAPE }
    @Suppress("unused")
    private val vapeReturnToLastSlot by boolean("ReturnToLastSlot", true)
        .visibleWhen { mode == BlockInMode.VAPE }
    private val vapeUseBlacklist by boolean("UseBlacklist", true)
        .visibleWhen { mode == BlockInMode.VAPE }
    private val vapeBlacklist by blocks("BlockBlacklist", defaultVapeBlacklist())
        .visibleWhen { mode == BlockInMode.VAPE && vapeUseBlacklist }

    private val startPos = BlockPos.MutableBlockPos()
    private var rotateClockwise = false
    private var blockList = emptyList<BlockPos>()
    private var vapeCompleted = false
    private var vapeSneakLatched = false
    private var lastPhysicalSneak = false

    override fun onDisabled() {
        startPos.set(BlockPos.ZERO)
        blockList = emptyList()
        vapeCompleted = false
        vapeSneakLatched = false
        lbPlacer.disable()
        vapePlacer.disable()
    }

    override fun onEnabled() {
        startPos.set(player.blockPosition())
        rotateClockwise = Random.nextBoolean()
        vapeCompleted = false
        vapeSneakLatched = false
        lastPhysicalSneak = mc.options.keyShift.isPressedOnAny
        getPositions()
    }

    private fun reconfigureMode() {
        if (!running) return

        lbPlacer.disable()
        vapePlacer.disable()
        startPos.set(player.blockPosition())
        vapeCompleted = false
        vapeSneakLatched = false
        lastPhysicalSneak = mc.options.keyShift.isPressedOnAny
        getPositions()
    }

    private inline fun rotateSurroundings(first: Direction = player.direction, action: (Direction) -> Unit) {
        var direction = first
        repeat(4) {
            action(direction)
            direction = if (rotateClockwise) direction.clockWise else direction.counterClockWise
        }
    }

    private fun getPositions() {
        blockList = if (mode == BlockInMode.VAPE) vapePositions() else placeOrder.positions().asList()
        debugParameter("Place Count") { blockList.size }
    }

    private fun vapePositions(): List<BlockPos> {
        val playerHeight = Mth.ceil(player.bbHeight)
        val result = ObjectArraySet<BlockPos>(10)
        result += startPos.below()
        val preferredDirection = findBedDirection() ?: player.direction
        rotateSurroundings(preferredDirection) { direction ->
            val side = startPos.relative(direction)
            repeat(playerHeight) { height -> result += side.above(height) }
        }
        result += startPos.above(playerHeight)
        return result.toList()
    }

    private fun findBedDirection(): Direction? {
        if (!vapeBedFinder) return null

        var nearest: BlockPos? = null
        var nearestDistance = Double.POSITIVE_INFINITY
        for (x in -BED_SCAN_RADIUS..BED_SCAN_RADIUS) {
            for (y in -2..2) {
                for (z in -BED_SCAN_RADIUS..BED_SCAN_RADIUS) {
                    val pos = startPos.offset(x, y, z)
                    if (world.getBlockState(pos).block !is BedBlock) continue
                    val distance = pos.distSqr(startPos)
                    if (distance < nearestDistance) {
                        nearest = pos.immutable()
                        nearestDistance = distance
                    }
                }
            }
        }

        val bed = nearest ?: return null
        val dx = bed.x - startPos.x
        val dz = bed.z - startPos.z
        return if (kotlin.math.abs(dx) > kotlin.math.abs(dz)) {
            if (dx > 0) Direction.EAST else Direction.WEST
        } else if (dz != 0) {
            if (dz > 0) Direction.SOUTH else Direction.NORTH
        } else {
            null
        }
    }

    @Suppress("unused")
    private val tickHandler = tickHandler {
        val activeMode = mode
        val placer = if (activeMode == BlockInMode.VAPE) vapePlacer else lbPlacer
        val targets = blockList.filter { world.getBlockState(it).canBeReplaced() }
        placer.update(targets)
        tickUntil { placer.isDone() }

        if (mode != activeMode) return@tickHandler
        if (activeMode == BlockInMode.LIQUID_BOUNCE) {
            if (autoDisable) {
                notification(name, message("filled"), NotificationEvent.Severity.SUCCESS)
                enabled = false
            } else {
                getPositions()
            }
        } else {
            vapeCompleted = true
            vapeSneakLatched = vapeSneak && vapeKeepSneak
        }
    }

    @Suppress("unused")
    private val movementInputHandler = handler<MovementInputEvent> { event ->
        if (mode != BlockInMode.VAPE || !vapeSneak) return@handler

        val physicalSneak = mc.options.keyShift.isPressedOnAny
        if (vapeSneakLatched && physicalSneak && !lastPhysicalSneak) {
            vapeSneakLatched = false
        }
        lastPhysicalSneak = physicalSneak

        if (!vapeCompleted || vapeSneakLatched) {
            event.sneak = true
        }
    }

    @Suppress("unused")
    private val movementHandler = handler<PlayerMovementTickEvent> {
        if (mode != BlockInMode.LIQUID_BOUNCE) return@handler

        val currentPos = player.blockPosition()
        if (currentPos != startPos && currentPos != startPos.above()) {
            notification(name, message("positionChanged"), NotificationEvent.Severity.ERROR)
            enabled = false
        }
    }

    private fun slotFinder(pos: BlockPos?): HotbarItemSlot? {
        val blockSlots = Slots.OffhandWithHotbar.mapNotNull { slot ->
            val block = slot.itemStack.getBlock() ?: return@mapNotNull null
            if (!ScaffoldBlockItemSelection.isValidBlock(slot.itemStack)) return@mapNotNull null

            val allowed = if (mode == BlockInMode.VAPE) {
                !vapeUseBlacklist || block !in vapeBlacklist
            } else {
                filter(block, blocks)
            }
            if (!allowed) return@mapNotNull null
            slot to block
        }

        val selected = if (mode == BlockInMode.VAPE) {
            when (vapeBlockPriority) {
                VapeBlockPriority.LOWEST_COST -> blockSlots.maxByOrNull { vapePriority(it.second) }
                VapeBlockPriority.HARDEST -> blockSlots.maxByOrNull { it.second.defaultDestroyTime() }
            }
        } else if (pos in blockList) {
            blockSlots.maxByOrNull { it.second.defaultDestroyTime() }
        } else {
            blockSlots.minByOrNull { it.second.defaultDestroyTime() }
        }
        return selected?.first
    }

    private fun vapePriority(block: Block): Int {
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

    private enum class BlockInMode(override val tag: String) : Tagged {
        LIQUID_BOUNCE("LiquidBounce"),
        VAPE("Vape"),
    }

    private enum class VapeBlockPriority(override val tag: String) : Tagged {
        LOWEST_COST("LowestCost"),
        HARDEST("Hardest"),
    }

    private enum class Order(override val tag: String) : Tagged {
        NORMAL("Normal") {
            override fun positions(): Array<BlockPos> {
                val playerHeight = Mth.ceil(player.bbHeight)
                val result = ObjectArraySet<BlockPos>(10)
                result += startPos.below()
                rotateSurroundings { direction ->
                    val side = startPos.relative(direction)
                    repeat(playerHeight) { height -> result += side.above(height) }
                }
                result += startPos.above(playerHeight)
                return result.toTypedArray()
            }
        },
        RANDOM("Random") {
            override fun positions() = NORMAL.positions().apply { shuffle() }
        },
        BOTTOM_TOP("BottomTop") {
            override fun positions() = NORMAL.positions().apply { sortBy { it.y } }
        },
        TOP_BOTTOM("TopBottom") {
            override fun positions() = NORMAL.positions().apply { sortByDescending { it.y } }
        };

        abstract fun positions(): Array<BlockPos>
    }

    private fun defaultVapeBlacklist() = blockSortedSetOf(
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

    private const val BED_SCAN_RADIUS = 5
    private const val MILLIS_PER_TICK = 50
}
