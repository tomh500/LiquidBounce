/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LiquidBounce is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LiquidBounce. If not, see <https://www.gnu.org/licenses/>.
 */
@file:Suppress("TooManyFunctions")

package net.ccbluex.liquidbounce.features.module.modules.world.scaffold

import net.ccbluex.liquidbounce.event.events.KeybindIsPressedEvent
import net.ccbluex.liquidbounce.event.events.MouseRotationEvent
import net.ccbluex.liquidbounce.event.events.MovementInputEvent
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.events.SprintEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.global.GlobalVapeRotationSettings
import net.ccbluex.liquidbounce.utils.aiming.RotationManager
import net.ccbluex.liquidbounce.utils.collection.blockSortedSetOf
import net.ccbluex.liquidbounce.utils.item.getBlock
import net.ccbluex.liquidbounce.utils.kotlin.EventPriorityConvention
import net.ccbluex.liquidbounce.utils.kotlin.Priority
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket
import net.minecraft.world.level.block.Blocks

// Vape Scaffold settings are constructed here so ModuleScaffold.kt only holds thin
// delegated declarations. Keys and defaults must stay in sync with Vape's flat options.

internal fun ModuleScaffold.vapeBlockCountSetting() = boolean("BlockCount", false)
    .visibleWhen { !isLiquidBounceMode }

internal fun ModuleScaffold.vapePitchCheckSetting() = boolean("PitchCheck", false)
    .visibleWhen { !isLiquidBounceMode }

internal fun ModuleScaffold.vapePitchSetting() = float("Pitch", 45f, 0f..90f)
    .visibleWhen { !isLiquidBounceMode && vapePitchCheck }

internal fun ModuleScaffold.vapeBlacklistEnabledSetting() = boolean("Blacklist", true)
    .visibleWhen { !isLiquidBounceMode }

internal fun ModuleScaffold.vapeBlacklistSetting() = blocks(
    "BlockBlacklist",
    blockSortedSetOf(
        Blocks.DISPENSER,
        Blocks.NOTE_BLOCK,
        Blocks.COBWEB,
        Blocks.TNT,
        Blocks.SPAWNER,
        Blocks.ENCHANTING_TABLE,
        Blocks.OAK_FENCE,
        Blocks.JUKEBOX,
        Blocks.MELON,
        Blocks.COMMAND_BLOCK,
        Blocks.ANVIL,
        Blocks.GLASS_PANE,
        Blocks.IRON_BARS,
        Blocks.ICE,
        Blocks.PACKED_ICE,
        Blocks.REDSTONE_BLOCK,
        Blocks.GOLD_ORE,
        Blocks.IRON_ORE,
        Blocks.COAL_ORE,
        Blocks.LAPIS_ORE,
        Blocks.REDSTONE_ORE,
        Blocks.ACACIA_STAIRS,
        Blocks.OAK_PRESSURE_PLATE,
        Blocks.STONE_PRESSURE_PLATE,
        Blocks.BEACON,
        Blocks.OAK_SAPLING,
        Blocks.POWERED_RAIL,
        Blocks.DETECTOR_RAIL,
        Blocks.SHORT_GRASS,
        Blocks.DEAD_BUSH,
        Blocks.DANDELION,
        Blocks.POPPY,
        Blocks.BROWN_MUSHROOM,
        Blocks.RED_MUSHROOM,
        Blocks.LADDER,
        Blocks.RAIL,
        Blocks.OAK_TRAPDOOR,
        Blocks.LILY_PAD,
        Blocks.TRIPWIRE_HOOK,
        Blocks.SNOW,
        Blocks.TRAPPED_CHEST,
        Blocks.DAYLIGHT_DETECTOR,
        Blocks.HOPPER,
        Blocks.CHEST,
        Blocks.TORCH,
        Blocks.LEVER,
        Blocks.REDSTONE_TORCH,
        Blocks.STONE_BUTTON,
        Blocks.OAK_BUTTON,
        Blocks.CACTUS,
    ).apply {
        addAll(Blocks.STAINED_GLASS_PANE.asList())
        addAll(Blocks.CARPET.asList())
    }
).visibleWhen { !isLiquidBounceMode && vapeBlacklistEnabled }

internal fun ModuleScaffold.vapeWhitelistEnabledSetting() = boolean("Whitelist", false)
    .visibleWhen { !isLiquidBounceMode }

internal fun ModuleScaffold.vapeWhitelistSetting() = blocks("BlockWhitelist", blockSortedSetOf())
    .visibleWhen { !isLiquidBounceMode && vapeWhitelistEnabled }

internal fun ModuleScaffold.vapeActivationBlocksSetting() = int("ActivationBlocks", 2, 1..4)
    .visibleWhen { !isLiquidBounceMode }

internal fun ModuleScaffold.vapeRequireRightClickSetting() = boolean("RequireRightClick", true)
    .visibleWhen { isTellyBridgeMode }

internal fun ModuleScaffold.vapeYIncreaseSetting() = int("YIncrease", 1, 0..3)
    .visibleWhen { isTellyBridgeMode }

internal fun ModuleScaffold.vapeAllowSprintSetting() = boolean("AllowSprint", true)
    .visibleWhen { !isLiquidBounceMode }

internal fun ModuleScaffold.vapeForceSprintSetting() = boolean("ForceSprint", false)
    .visibleWhen { !isLiquidBounceMode && vapeAllowSprint }

@Suppress("unused")
internal fun ModuleScaffold.vapeActivationHandler() = handler<PacketEvent> { event ->
    val packet = event.packet as? ServerboundUseItemOnPacket ?: return@handler
    if (isLiquidBounceMode || event.isCancelled) return@handler

    VapeScaffoldController.onManualPlacementRequest(packet)
}

@Suppress("unused")
internal fun ModuleScaffold.vapeMouseRotationHandler() = handler<MouseRotationEvent> { event ->
    if (!isLiquidBounceMode && !event.isCancelled) {
        VapeScaffoldController.onMouseRotation(event.cursorDeltaX, event.cursorDeltaY)
    }
}

@Suppress("unused")
internal fun ModuleScaffold.vapeUseKeyHandler() = handler<KeybindIsPressedEvent>(
    priority = EventPriorityConvention.SAFETY_FEATURE,
) { event ->
    if (!isLiquidBounceMode && VapeScaffoldController.isAutomated && event.keyBinding == mc.options.keyUse) {
        event.isPressed = false
    }
}

@Suppress("unused")
internal fun ModuleScaffold.vapeSprintHandler() = handler<SprintEvent>(
    priority = EventPriorityConvention.SAFETY_FEATURE
) { event ->
    if (!isLiquidBounceMode && VapeScaffoldController.isAutomated &&
        (event.source == SprintEvent.Source.INPUT || event.source == SprintEvent.Source.MOVEMENT_TICK)
    ) {
        // This is the final Scaffold decision, after Sprint and other movement modules
        // have proposed their state. Vape bridge paths must not inherit their policy.
        event.sprint = vapeAllowSprint && (vapeForceSprint || VapeScaffoldController.shouldSprint)
    }
}

/**
 * Vape rotation branch of the Scaffold rotation update. Mirrors the old inline
 * implementation including the block-count update performed before aiming.
 */
internal fun ModuleScaffold.handleVapeRotationUpdate() {
    if (!VapeScaffoldController.canRotate()) {
        currentTarget = null
        return
    }

    val blockInHotbar = findBestValidHotbarSlotForTarget()
    nextBlock = if (blockInHotbar == null) null else player.inventory.getItem(blockInHotbar).getBlock()

    currentTarget = null
    val rotation = VapeScaffoldController.rotationFor(null)
    if (rotation == null) {
        VapeScaffoldController.onRotationReleased()
        return
    }

    val rotationTarget = GlobalVapeRotationSettings.rotationTarget(
        rotation,
        speed = { VapeScaffoldController.rotationSpeed(rotation) },
        silentAim = false,
        scaleAxesProportionally = VapeScaffoldController.scaleAxesProportionally,
        emulateMouseController = true,
        mouseRotationState = VapeScaffoldController.mouseRotationState,
        tolerance = VapeScaffoldController.rotationTolerance,
    )
    RotationManager.setRotationTarget(
        rotationTarget,
        priority = Priority.IMPORTANT_FOR_PLAYER_LIFE,
        provider = this,
    )
    VapeScaffoldController.onRotationSubmitted(rotationTarget)
}

internal fun ModuleScaffold.handleVapeMovement(event: MovementInputEvent) {
    VapeScaffoldController.handleMovement(event)
}
