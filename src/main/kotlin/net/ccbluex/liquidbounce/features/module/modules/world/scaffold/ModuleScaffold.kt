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
package net.ccbluex.liquidbounce.features.module.modules.world.scaffold

import net.ccbluex.liquidbounce.config.types.group.ToggleableValueGroup
import net.ccbluex.liquidbounce.config.types.list.Tagged
import net.ccbluex.liquidbounce.event.EventManager
import net.ccbluex.liquidbounce.event.EventListener
import net.ccbluex.liquidbounce.event.events.BlockCountChangeEvent
import net.ccbluex.liquidbounce.event.events.GameTickEvent
import net.ccbluex.liquidbounce.event.events.KeybindIsPressedEvent
import net.ccbluex.liquidbounce.event.events.MouseRotationEvent
import net.ccbluex.liquidbounce.event.events.MovementInputEvent
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.events.RotationUpdateEvent
import net.ccbluex.liquidbounce.event.events.SprintEvent
import net.ccbluex.liquidbounce.event.events.WorldChangeEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.event.waitTicks
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.features.module.ModuleOrigin
import net.ccbluex.liquidbounce.features.global.GlobalVapeRotationSettings
import net.ccbluex.liquidbounce.features.module.modules.movement.ModuleSafeWalk
import net.ccbluex.liquidbounce.features.module.modules.player.nofall.modes.NoFallBlink
import net.ccbluex.liquidbounce.features.module.modules.render.ModuleDebug
import net.ccbluex.liquidbounce.features.module.modules.render.ModuleDebug.debugGeometry
import net.ccbluex.liquidbounce.features.module.modules.render.ModuleDebug.debugParameter
import net.ccbluex.liquidbounce.features.module.modules.world.scaffold.ModuleScaffold.ScaffoldRotationValueGroup.RotationTimingMode.NORMAL
import net.ccbluex.liquidbounce.features.module.modules.world.scaffold.ModuleScaffold.ScaffoldRotationValueGroup.RotationTimingMode.ON_TICK
import net.ccbluex.liquidbounce.features.module.modules.world.scaffold.ModuleScaffold.ScaffoldRotationValueGroup.RotationTimingMode.ON_TICK_SNAP
import net.ccbluex.liquidbounce.features.module.modules.world.scaffold.ModuleScaffold.ScaffoldRotationValueGroup.considerInventory
import net.ccbluex.liquidbounce.features.module.modules.world.scaffold.ModuleScaffold.ScaffoldRotationValueGroup.rotationTiming
import net.ccbluex.liquidbounce.features.module.modules.world.scaffold.ScaffoldBlockItemSelection.isValidBlock
import net.ccbluex.liquidbounce.features.module.modules.world.scaffold.features.ScaffoldAccelerationFeature
import net.ccbluex.liquidbounce.features.module.modules.world.scaffold.features.ScaffoldAutoBlockFeature
import net.ccbluex.liquidbounce.features.module.modules.world.scaffold.features.ScaffoldBlinkFeature
import net.ccbluex.liquidbounce.features.module.modules.world.scaffold.features.ScaffoldCeilingFeature
import net.ccbluex.liquidbounce.features.module.modules.world.scaffold.features.ScaffoldJumpStrafe
import net.ccbluex.liquidbounce.features.module.modules.world.scaffold.features.ScaffoldLedgeExtension
import net.ccbluex.liquidbounce.features.module.modules.world.scaffold.features.ScaffoldMovementPrediction
import net.ccbluex.liquidbounce.features.module.modules.world.scaffold.features.ScaffoldSpeedLimiterFeature
import net.ccbluex.liquidbounce.features.module.modules.world.scaffold.features.ScaffoldSprintControlFeature
import net.ccbluex.liquidbounce.features.module.modules.world.scaffold.features.ScaffoldStrafeFeature
import net.ccbluex.liquidbounce.features.module.modules.world.scaffold.features.ledge
import net.ccbluex.liquidbounce.features.module.modules.world.scaffold.techniques.ScaffoldBreezilyTechnique
import net.ccbluex.liquidbounce.features.module.modules.world.scaffold.techniques.ScaffoldExpandTechnique
import net.ccbluex.liquidbounce.features.module.modules.world.scaffold.techniques.ScaffoldGodBridgeTechnique
import net.ccbluex.liquidbounce.features.module.modules.world.scaffold.techniques.ScaffoldNormalTechnique
import net.ccbluex.liquidbounce.features.module.modules.world.scaffold.techniques.normal.ScaffoldDownFeature
import net.ccbluex.liquidbounce.features.module.modules.world.scaffold.techniques.normal.ScaffoldEagleFeature
import net.ccbluex.liquidbounce.features.module.modules.world.scaffold.tower.ScaffoldTowerHypixel
import net.ccbluex.liquidbounce.features.module.modules.world.scaffold.tower.ScaffoldTowerKarhu
import net.ccbluex.liquidbounce.features.module.modules.world.scaffold.tower.ScaffoldTowerMotion
import net.ccbluex.liquidbounce.features.module.modules.world.scaffold.tower.ScaffoldTowerNone
import net.ccbluex.liquidbounce.features.module.modules.world.scaffold.tower.ScaffoldTowerPulldown
import net.ccbluex.liquidbounce.features.module.modules.world.scaffold.tower.ScaffoldTowerVulcan
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.utils.aiming.RotationManager
import net.ccbluex.liquidbounce.utils.aiming.RotationsValueGroup
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.aiming.utils.RotationUtil
import net.ccbluex.liquidbounce.utils.aiming.utils.withFixedYaw
import net.ccbluex.liquidbounce.utils.block.SwingMode
import net.ccbluex.liquidbounce.utils.block.doPlacement
import net.ccbluex.liquidbounce.utils.block.targetBlockPos
import net.ccbluex.liquidbounce.utils.block.targetfinding.BlockPlacementTarget
import net.ccbluex.liquidbounce.utils.clicking.Clicker
import net.ccbluex.liquidbounce.utils.client.SilentHotbar
import net.ccbluex.liquidbounce.utils.client.Timer
import net.ccbluex.liquidbounce.utils.collection.blockSortedSetOf
import net.ccbluex.liquidbounce.utils.entity.moving
import net.ccbluex.liquidbounce.utils.entity.rotation
import net.ccbluex.liquidbounce.utils.item.PreferAverageHardBlocks
import net.ccbluex.liquidbounce.utils.item.PreferFavourableBlocks
import net.ccbluex.liquidbounce.utils.item.PreferFullCubeBlocks
import net.ccbluex.liquidbounce.utils.item.PreferSolidBlocks
import net.ccbluex.liquidbounce.utils.item.PreferStackSize
import net.ccbluex.liquidbounce.utils.item.PreferWalkableBlocks
import net.ccbluex.liquidbounce.utils.item.getBlock
import net.ccbluex.liquidbounce.utils.input.InputTracker.isPressedOnAny
import net.ccbluex.liquidbounce.utils.kotlin.EventPriorityConvention
import net.ccbluex.liquidbounce.utils.kotlin.Priority
import net.ccbluex.liquidbounce.utils.math.copy
import net.ccbluex.liquidbounce.utils.math.geometry.Line
import net.ccbluex.liquidbounce.utils.math.minus
import net.ccbluex.liquidbounce.utils.math.allEmpty
import net.ccbluex.liquidbounce.utils.math.topCenter
import net.ccbluex.liquidbounce.utils.movement.DirectionalInput
import net.ccbluex.liquidbounce.utils.render.placement.PlacementRenderer
import net.ccbluex.liquidbounce.utils.raytracing.traceFromPlayer
import net.ccbluex.liquidbounce.utils.sorting.ComparatorChain
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.PosRot
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.Pose
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import kotlin.math.abs

/**
 * Scaffold module
 *
 * Places blocks under you.
 */
@Suppress("TooManyFunctions")
object ModuleScaffold : ClientModule(
    "Scaffold",
    ModuleCategories.WORLD,
    origin = ModuleOrigin.LIQUID_BOUNCE_MODIFIED,
) {

    internal val mode by enumChoice("Mode", ScaffoldImplementation.NORMAL)
        .apply(::tagBy)
        .onChanged { reset() }
    internal val isLiquidBounceMode get() = mode == ScaffoldImplementation.NORMAL
    internal val isTellyBridgeMode get() = mode == ScaffoldImplementation.TELLY_BRIDGE
    private val vapeBlockCount by boolean("BlockCount", false)
        .visibleWhen { !isLiquidBounceMode }
    internal val vapePitchCheck by boolean("PitchCheck", false)
        .visibleWhen { !isLiquidBounceMode }
    internal val vapePitch by float("Pitch", 45f, 0f..90f)
        .visibleWhen { !isLiquidBounceMode && vapePitchCheck }
    internal val vapeBlacklistEnabled by boolean("Blacklist", true)
        .visibleWhen { !isLiquidBounceMode }
    internal val vapeBlacklist by blocks(
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
        )
            .apply {
                addAll(Blocks.STAINED_GLASS_PANE.asList())
                addAll(Blocks.CARPET.asList())
            }
    )
        .visibleWhen { !isLiquidBounceMode && vapeBlacklistEnabled }
    internal val vapeWhitelistEnabled by boolean("Whitelist", false)
        .visibleWhen { !isLiquidBounceMode }
    internal val vapeWhitelist by blocks("BlockWhitelist", blockSortedSetOf())
        .visibleWhen { !isLiquidBounceMode && vapeWhitelistEnabled }
    internal val vapeActivationBlocks by int("ActivationBlocks", 2, 1..4)
        .visibleWhen { !isLiquidBounceMode }
    internal val vapeRequireRightClick by boolean("RequireRightClick", true)
        .visibleWhen { isTellyBridgeMode }
    internal val vapeYIncrease by int("YIncrease", 1, 0..3)
        .visibleWhen { isTellyBridgeMode }

    private val delay by intRange("Delay", 0..0, 0..40, "ticks")
        .visibleWhen { isLiquidBounceMode }
    private val minDist by float("MinDist", 0.0f, 0.0f..0.25f)
        .visibleWhen { isLiquidBounceMode }
    private val timer by float("Timer", 1f, 0.01f..10f)
        .visibleWhen { isLiquidBounceMode }

    init {
        tree(ScaffoldBlockItemSelection).visibleWhen { isLiquidBounceMode }
        tree(ScaffoldAutoBlockFeature).visibleWhen { isLiquidBounceMode }
        tree(ScaffoldMovementPrediction).visibleWhen { isLiquidBounceMode }
    }

    internal val technique = choices(
        "Technique",
        ScaffoldNormalTechnique,
        arrayOf(
            ScaffoldNormalTechnique,
            ScaffoldExpandTechnique,
            ScaffoldGodBridgeTechnique,
            ScaffoldBreezilyTechnique
        )
    ).also { it.visibleWhen { isLiquidBounceMode } }

    private val sameYMode by enumChoice("SameY", SameYMode.OFF)
        .visibleWhen { isLiquidBounceMode }

    @Suppress("unused")
    private enum class SameYMode(
        override val tag: String,
        val getTargetedBlockPos: (BlockPos) -> BlockPos?
    ) : Tagged {

        OFF("Off", { null }),

        /**
         * Places blocks at the same Y level as the player
         */
        ON("On", { blockPos -> blockPos.copy(y = placementY) }),

        /**
         * Places blocks at the same Y level only while the physical jump key is not held
         */
        JUMP_KEY("JumpKey", { blockPos ->
            (if (mc.options.keyJump.isDown) OFF else ON).getTargetedBlockPos(blockPos)
        }),

        /**
         * Places blocks at the same Y level as the player, but only if the player is not falling
         */
        FALLING("Falling", { blockPos ->
            (if (player.deltaMovement.y < 0.2) ON else OFF).getTargetedBlockPos(blockPos)
        }),

        /**
         * Similar to FALLING, but only when a certain velocity is triggered and after
         * 2 jumps
         */
        HYPIXEL("Hypixel", { blockPos ->
            if (player.deltaMovement.y == -0.15233518685055708 && jumps >= 2) {
                jumps = 0

                blockPos.copy(y = startY)
            } else {
                blockPos.copy(y = startY - 1)
            }
        })

    }

    /**
     * Scaffold tower mode
     */
    val towerMode = choices("Tower", 0) {
        arrayOf(
            ScaffoldTowerNone,
            ScaffoldTowerMotion,
            ScaffoldTowerPulldown,
            ScaffoldTowerKarhu,
            ScaffoldTowerVulcan,
            ScaffoldTowerHypixel
        )
    }.also { it.visibleWhen { isLiquidBounceMode } }

    internal val isTowering: Boolean
        get() = if (isLiquidBounceMode && towerMode.activeMode != ScaffoldTowerNone && mc.options.keyJump.isDown) {
            this.wasTowering = true
            true
        } else {
            false
        }
    private var wasTowering: Boolean = false

    private val activeTechnique get() = if (!isLiquidBounceMode) {
        ScaffoldNormalTechnique
    } else if (isTowering) {
        ScaffoldNormalTechnique
    } else {
        technique.activeMode
    }

    // SafeWalk feature - uses the SafeWalk module as a base
    private object LiquidBounceScaffoldListener : EventListener {
        override val running get() = ModuleScaffold.running && isLiquidBounceMode
        override fun parent() = ModuleScaffold
    }

    @Suppress("unused")
    private val safeWalkMode = modes(
        LiquidBounceScaffoldListener,
        "SafeWalk",
        1,
        ModuleSafeWalk::safeWalkChoices,
    ).also {
        it.visibleWhen { isLiquidBounceMode }
    }

    internal object ScaffoldRotationValueGroup : RotationsValueGroup(this) {

        val considerInventory by boolean("ConsiderInventory", false)
        val rotationTiming by enumChoice("RotationTiming", NORMAL)

        enum class RotationTimingMode(override val tag: String) : Tagged {

            /**
             * Rotates the player before the block is placed
             */
            NORMAL("Normal"),

            /**
             * Rotates the player on the tick the block is placed
             */
            ON_TICK("OnTick"),

            /**
             * Similar to ON_TICK, but the player will keep the rotation after placing
             */
            ON_TICK_SNAP("OnTickSnap")

        }

    }

    private var currentTarget: BlockPlacementTarget? = null
    private val effectiveRotationTiming get() = if (isLiquidBounceMode) rotationTiming else NORMAL

    private val swingMode by enumChoice("Swing", SwingMode.DO_NOT_HIDE)
        .visibleWhen { isLiquidBounceMode }

    private object SimulatePlacementAttempts : ToggleableValueGroup(this, "SimulatePlacementAttempts", false) {
        val clicker = tree(Clicker(ModuleScaffold, mc.options.keyUse, null, maxCps = 100))
        val failedAttemptsOnly by boolean("FailedAttemptsOnly", true)
    }

    init {
        tree(ScaffoldRotationValueGroup).visibleWhen { isLiquidBounceMode }
        tree(ScaffoldSprintControlFeature).visibleWhen { isLiquidBounceMode }
        tree(SimulatePlacementAttempts).visibleWhen { isLiquidBounceMode }
        tree(ScaffoldAccelerationFeature).visibleWhen { isLiquidBounceMode }
        tree(ScaffoldStrafeFeature).visibleWhen { isLiquidBounceMode }
        tree(ScaffoldJumpStrafe).visibleWhen { isLiquidBounceMode }
        tree(ScaffoldSpeedLimiterFeature).visibleWhen { isLiquidBounceMode }
        tree(ScaffoldBlinkFeature).visibleWhen { isLiquidBounceMode }
    }

    /**
     * Temporarily turns on [net.ccbluex.liquidbounce.features.module.modules.movement.speed.ModuleSpeed]
     * while Scaffold is enabled.
     */
    val autoSpeed by boolean("AutoSpeed", false)
        .visibleWhen { isLiquidBounceMode }
    val shouldAutoSpeed get() = isLiquidBounceMode && autoSpeed

    private val ledge by boolean("Ledge", true)
        .visibleWhen { isLiquidBounceMode }

    private val renderer = tree(PlacementRenderer("Render", true, this, keep = false))

    private var placementY = 0
    private var forceSneak = 0
    private var startY = 0
    private var jumps = 0

    private var nextBlock: Block? = null

    val blockCount: Int
        get() {
            fun ItemStack.blockCount() = if (isValidForCurrentMode(this)) this.count else 0

            return player.offhandItem.blockCount() + if (ScaffoldAutoBlockFeature.enabled || !isLiquidBounceMode) {
                findPlaceableSlots().sumOf { it.value.blockCount() }
            } else {
                player.inventory.getItem(player.inventory.selectedSlot).blockCount()
            }
        }

    val isBlockBelow: Boolean
        get() {
            // Check if there is a collision box below the player
            // In this case we expand the bounding box by 0.5 in all directions and check if there is a collision
            // This might cause for "Spider-like" behavior, but it's the most reliable way to check
            // and usually the scaffold should start placing blocks
            return !world.getBlockCollisions(
                player,
                player.boundingBox.inflate(0.5, 0.0, 0.5).move(0.0, -1.05, 0.0)
            ).allEmpty()
        }

    /**
     * This comparator will estimate the value of a block. If this comparator says that Block A > Block B, Scaffold will
     * prefer Block A over Block B.
     * The chain will prefer the block that is solid. If both are solid, it goes to the next criteria
     * (in this case full cube) and so on
     */
    private val BLOCK_COMPARATOR_FOR_HOTBAR =
        ComparatorChain(
            PreferFavourableBlocks,
            PreferSolidBlocks,
            PreferFullCubeBlocks,
            PreferWalkableBlocks,
            PreferAverageHardBlocks(neutralRange = true),
            PreferStackSize.PREFER_MORE,
            PreferAverageHardBlocks(neutralRange = false),
        )
    @JvmField
    val BLOCK_COMPARATOR_FOR_INVENTORY =
        ComparatorChain(
            PreferFavourableBlocks,
            PreferSolidBlocks,
            PreferFullCubeBlocks,
            PreferWalkableBlocks,
            PreferAverageHardBlocks(neutralRange = true),
            PreferStackSize.PREFER_FEWER,
            PreferAverageHardBlocks(neutralRange = false),
        )

    override fun onEnabled() {
        // Placement Y is the Y coordinate of the block below the player
        placementY = player.blockPosition().y - 1
        startY = player.blockPosition().y
        jumps = 2

        ScaffoldMovementPlanner.reset()
        VapeScaffoldController.reset()

        super.onEnabled()
    }

    override fun onDisabled() {
        reset()
    }

    private fun reset() {
        NoFallBlink.waitUntilGround = false
        ScaffoldMovementPlanner.reset()
        ScaffoldMovementPrediction.reset()
        SilentHotbar.resetSlot(this)
        nextBlock = null
        updateRenderCount(null)
        forceSneak = 0
        currentTarget = null
        renderer.clearSilently()
        VapeScaffoldController.reset()
    }

    @Suppress("unused")
    private val worldChangeHandler = handler<WorldChangeEvent> {
        reset()
    }

    private fun updateRenderCount(count: Int?) {
        EventManager.callEvent(BlockCountChangeEvent(nextBlock, count))
    }

    @Suppress("unused")
    private val rotationUpdateHandler = handler<RotationUpdateEvent> {
        if (!isLiquidBounceMode && !VapeScaffoldController.canRotate()) {
            currentTarget = null
            return@handler
        }

        if (isLiquidBounceMode) {
            NoFallBlink.waitUntilGround = true
        }

        val blockInHotbar = findBestValidHotbarSlotForTarget()

        val bestStack = if (blockInHotbar == null) {
            nextBlock = null
            ItemStack(Items.SANDSTONE, 64)
        } else {
            player.inventory.getItem(blockInHotbar).also {
                nextBlock = it.getBlock()
            }
        }

        if (!isLiquidBounceMode) {
            currentTarget = null
            val rotation = VapeScaffoldController.rotationFor(null)
            if (rotation == null) {
                VapeScaffoldController.onRotationReleased()
                return@handler
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
                provider = this@ModuleScaffold,
            )
            VapeScaffoldController.onRotationSubmitted(rotationTarget)
            return@handler
        }

        val optimalLine = this.currentOptimalLine

        val predictedPos = ScaffoldMovementPrediction.getPredictedPlacementPos(optimalLine) ?: player.position()
        // Check if the player is probably going to sneak at the predicted position
        val predictedPose = if (ScaffoldEagleFeature.enabled &&
            ScaffoldEagleFeature.shouldEagle(DirectionalInput(player.input))) {
            Pose.CROUCHING
        } else {
            Pose.STANDING
        }

        debugGeometry("predictedPos") {
            ModuleDebug.DebuggedPoint(predictedPos, Color4b.GREEN, size = 0.1)
        }

        val technique = activeTechnique

        val target = technique.findPlacementTarget(predictedPos, predictedPose, optimalLine, bestStack)
            .also { this.currentTarget = it }

        debugGeometry("lineToBlock") {
            // Debug stuff
            val b = target?.placedBlock?.topCenter ?: return@debugGeometry null
            val a = optimalLine?.getNearestPointTo(b)  ?: return@debugGeometry null

            // Debug the line a-b
            ModuleDebug.DebuggedLineSegment(
                from = a,
                to = b,
                Color4b.RED,
            )
        }

        // Do not aim yet in SKIP mode, since we want to aim at the block only when we are about to place it
        if (effectiveRotationTiming == NORMAL) {
            val rotation = technique.getRotations(target) ?: return@handler

            RotationManager.setRotationTarget(
                rotation,
                considerInventory = considerInventory,
                valueGroup = ScaffoldRotationValueGroup,
                provider = this@ModuleScaffold,
                priority = Priority.IMPORTANT_FOR_PLAYER_LIFE
            )
        }
    }

    var currentOptimalLine: Line? = null
        private set
    var rawInput = DirectionalInput.NONE
        private set

    @Suppress("unused")
    private val handleMovementInput = handler<MovementInputEvent>(
        priority = EventPriorityConvention.MODEL_STATE
    ) { event ->
        this.currentOptimalLine = null
        this.rawInput = event.directionalInput

        if (!isLiquidBounceMode) return@handler

        val currentInput = event.directionalInput

        if (currentInput == DirectionalInput.NONE) {
            return@handler
        }

        this.currentOptimalLine = ScaffoldMovementPlanner.getOptimalMovementLine(event.directionalInput)
    }

    @Suppress("unused")
    private val movementInputHandler = handler<MovementInputEvent>(
        // Runs after the model state
        priority = EventPriorityConvention.SAFETY_FEATURE
    ) { event ->
        if (!isLiquidBounceMode) {
            VapeScaffoldController.handleMovement(event)
            return@handler
        }

        if (forceSneak > 0) {
            event.sneak = true
            forceSneak--
        }

        // Ledge feature - AutoJump and AutoSneak
        if (ledge) {
            val technique = activeTechnique

            val ledgeAction = ledge(
                this.currentTarget,
                RotationManager.currentRotation ?: player.rotation,
                technique as? ScaffoldLedgeExtension
            )

            if (ledgeAction.jump) {
                event.jump = true
            }

            if (ledgeAction.stopInput) {
                event.directionalInput = DirectionalInput.NONE
            }

            if (ledgeAction.stepBack) {
                event.directionalInput = event.directionalInput.copy(
                    forwards = false,
                    backwards = true
                )
            }

            if (ledgeAction.sneakTime > forceSneak) {
                event.sneak = true
                forceSneak = ledgeAction.sneakTime
            }
        }
    }

    @Suppress("unused")
    private val vapeActivationHandler = handler<PacketEvent> { event ->
        val packet = event.packet as? ServerboundUseItemOnPacket ?: return@handler
        if (isLiquidBounceMode || event.isCancelled) return@handler

        VapeScaffoldController.onManualPlacementRequest(packet)
    }

    @Suppress("unused")
    private val vapeMouseRotationHandler = handler<MouseRotationEvent> { event ->
        if (!isLiquidBounceMode && !event.isCancelled) {
            VapeScaffoldController.onMouseRotation(event.cursorDeltaX, event.cursorDeltaY)
        }
    }

    @Suppress("unused")
    private val vapeUseKeyHandler = handler<KeybindIsPressedEvent>(
        priority = EventPriorityConvention.SAFETY_FEATURE,
    ) { event ->
        if (!isLiquidBounceMode && VapeScaffoldController.isAutomated && event.keyBinding == mc.options.keyUse) {
            event.isPressed = false
        }
    }

    @Suppress("unused")
    private val vapeSprintHandler = handler<SprintEvent>(priority = EventPriorityConvention.SAFETY_FEATURE) { event ->
        if (!isLiquidBounceMode && VapeScaffoldController.shouldSprint &&
            (event.source == SprintEvent.Source.INPUT || event.source == SprintEvent.Source.MOVEMENT_TICK)
        ) {
            event.sprint = true
        }
    }

    @Suppress("unused")
    private val timerHandler = handler<GameTickEvent> {
        if (isLiquidBounceMode && timer != 1f) {
            Timer.requestTimerSpeed(timer, Priority.IMPORTANT_FOR_USAGE_1, this@ModuleScaffold)
        }
    }

    @Suppress("unused")
    private val tickHandler = tickHandler {
        if (!isLiquidBounceMode) {
            val canPlace = VapeScaffoldController.canAutomate()
            updateRenderCount(if (vapeBlockCount) blockCount else null)
            if (canPlace) {
                tryVapePlacement()
            }
            VapeScaffoldController.finishPlacementTick()
            return@tickHandler
        }

        updateRenderCount(blockCount)

        if (player.onGround()) {
            // Placement Y is the Y coordinate of the block below the player
            placementY = player.blockPosition().y - 1
            jumps++
            wasTowering = false
        }

        if (mc.options.keyJump.isDown) {
            startY = player.blockPosition().y
            jumps = 2
        }

        debugParameter("IsTowering") { isTowering }
        debugParameter("WasTowering") { wasTowering }

        val target = currentTarget
        val technique = activeTechnique

        val currentRotation = if ((effectiveRotationTiming == ON_TICK ||
                effectiveRotationTiming == ON_TICK_SNAP) && target != null) {
            technique.getRotations(target) ?: (RotationManager.currentRotation ?: player.rotation)
        } else {
            RotationManager.currentRotation ?: player.rotation
        }.normalize()
        val currentCrosshairTarget = technique.getCrosshairTarget(target, currentRotation)
        val currentDelay = delay.random()

        var hasBlockInMainHand = isValidForCurrentMode(player.inventory.getItem(player.inventory.selectedSlot))
        val hasBlockInOffHand = isValidForCurrentMode(player.offhandItem)

        if (ScaffoldAutoBlockFeature.alwaysHoldBlock) {
            hasBlockInMainHand = handleSilentBlockSelection(hasBlockInMainHand, hasBlockInOffHand)
        }

        // Prioritize by all means the main hand if it has a block
        val suitableHand = InteractionHand.entries.firstOrNull {
            isValidForCurrentMode(player.getItemInHand(it))
        }

        fun commonPlaceSucceed(placed: BlockPos) {
            ScaffoldMovementPlanner.trackPlacedBlock(placed)
            renderer.addBlock(placed)
            ScaffoldEagleFeature.onBlockPlacement()
            ScaffoldBlinkFeature.onBlockPlacement()
            ScaffoldSprintControlFeature.onBlockPlacement()
        }

        if (simulatePlacementAttempts(currentCrosshairTarget, suitableHand) && player.moving
            && SimulatePlacementAttempts.clicker.isClickTick
        ) {
            SimulatePlacementAttempts.clicker.click {
                doPlacement(currentCrosshairTarget!!, suitableHand!!, {
                    commonPlaceSucceed(currentCrosshairTarget.targetBlockPos)
                    true
                }, swingMode = swingMode)
                true
            }
        }

        if (target == null || currentCrosshairTarget == null) {
            return@tickHandler
        }

        // Does the crosshair target meet the requirements?
        if (!target.doesCrosshairTargetMatchRequirements(currentCrosshairTarget) ||
            !isValidCrosshairTarget(currentCrosshairTarget)
        ) {
            return@tickHandler
        }

        if (!ScaffoldAutoBlockFeature.alwaysHoldBlock) {
            hasBlockInMainHand = handleSilentBlockSelection(hasBlockInMainHand, hasBlockInOffHand)
        }

        if (!hasBlockInMainHand && !hasBlockInOffHand) {
            return@tickHandler
        }

        val handToInteractWith = if (hasBlockInMainHand) InteractionHand.MAIN_HAND else InteractionHand.OFF_HAND
        var wasSuccessful = false

        if (effectiveRotationTiming == ON_TICK || effectiveRotationTiming == ON_TICK_SNAP) {
            // Check if server rotation matches the current rotation
            if (currentRotation != RotationManager.serverRotation) {
                network.send(
                    PosRot(
                        player.x, player.y, player.z,
                        currentRotation.yaw,
                        currentRotation.pitch,
                        player.onGround(),
                        player.horizontalCollision
                    )
                )
            }

            if (effectiveRotationTiming == ON_TICK_SNAP) {
                RotationManager.setRotationTarget(
                    currentRotation,
                    considerInventory = considerInventory,
                    valueGroup = ScaffoldRotationValueGroup,
                    provider = this@ModuleScaffold,
                    priority = Priority.IMPORTANT_FOR_PLAYER_LIFE
                )
            }
        }

        // Take the fall off position before placing the block
        val previousFallOffPos = currentOptimalLine?.let(ScaffoldMovementPrediction::getFallOffPositionOnLine)

        doPlacement(currentCrosshairTarget, handToInteractWith, {
            commonPlaceSucceed(target.placedBlock)
            currentTarget = null
            wasSuccessful = true
            true
        }, swingMode = swingMode)

        if (effectiveRotationTiming == ON_TICK && RotationManager.serverRotation != player.rotation) {
            network.send(
                PosRot(
                    player.x, player.y, player.z, player.withFixedYaw(currentRotation), player.xRot, player.onGround(),
                    player.horizontalCollision
                )
            )
        }

        if (wasSuccessful) {
            ScaffoldMovementPrediction.onPlace(currentOptimalLine, previousFallOffPos)

            waitTicks(currentDelay)
        }
    }

    private fun tryVapePlacement() {
        val crosshairTarget = traceFromPlayer(
            rotation = RotationManager.currentRotation ?: player.rotation,
            range = GlobalVapeRotationSettings.interactionRange(3.0),
        )
        if (!VapeScaffoldController.isValidPlacementHit(crosshairTarget)) return

        var hasBlockInMainHand = isValidForCurrentMode(player.inventory.getItem(player.inventory.selectedSlot))
        val hasBlockInOffHand = isValidForCurrentMode(player.offhandItem)
        hasBlockInMainHand = handleSilentBlockSelection(hasBlockInMainHand, hasBlockInOffHand)
        if (!hasBlockInMainHand && !hasBlockInOffHand) return

        val hand = if (hasBlockInMainHand) InteractionHand.MAIN_HAND else InteractionHand.OFF_HAND
        doPlacement(
            crosshairTarget,
            hand,
            onPlacementSuccess = {
                renderer.addBlock(crosshairTarget.targetBlockPos)
                true
            },
            swingMode = SwingMode.DO_NOT_HIDE,
        )
    }

    internal fun findPlaceableSlots() = buildList(9) {
        for (i in 0..8) {
            val stack = player.inventory.getItem(i)

            if (isValidForCurrentMode(stack)) {
                add(IndexedValue(i, stack))
            }
        }
    }

    private fun isValidForCurrentMode(stack: ItemStack) = if (isLiquidBounceMode) {
        isValidBlock(stack)
    } else {
        stack.getBlock() != null && VapeScaffoldController.isAllowedBlock(stack)
    }

    private fun findBestValidHotbarSlotForTarget(): Int? {
        val placeableSlots = findPlaceableSlots()
        if (!isLiquidBounceMode) return placeableSlots.firstOrNull()?.index

        val doNotUseBelowCount = ScaffoldAutoBlockFeature.doNotUseBelowCount

        val (slot, _) = placeableSlots
            .filter { (_, stack) -> stack.count > doNotUseBelowCount }
            .maxWithOrNull { o1, o2 -> BLOCK_COMPARATOR_FOR_HOTBAR.compare(o1.value, o2.value) }
            ?: placeableSlots.maxWithOrNull { o1, o2 -> BLOCK_COMPARATOR_FOR_HOTBAR.compare(o1.value, o2.value) }
            ?: return null

        return slot
    }

    internal fun isValidCrosshairTarget(rayTraceResult: BlockHitResult): Boolean {
        if (!isLiquidBounceMode) return true

        val diff = rayTraceResult.location - player.eyePosition

        val side = rayTraceResult.direction

        // Apply minDist
        if (side.axis != Direction.Axis.Y) {
            val dist = if (side == Direction.NORTH || side == Direction.SOUTH) diff.z else diff.x

            if (abs(dist) < minDist) {
                return false
            }
        }

        return true
    }

    internal fun getTargetedPosition(blockPos: BlockPos) = when {
        isTowering || wasTowering -> towerMode.activeMode.getTargetedPosition(blockPos)
        isLiquidBounceMode && ScaffoldDownFeature.running && ScaffoldDownFeature.shouldGoDown ->
            blockPos.offset(0, -2, 0)
        isLiquidBounceMode && ScaffoldCeilingFeature.running && ScaffoldCeilingFeature.canConstructCeiling() ->
            blockPos.offset(0, 3, 0)
        isLiquidBounceMode && player.input.keyPresses.jump && (!player.moving || player.horizontalCollision) ->
            blockPos.offset(0, -1, 0)
        else -> if (isLiquidBounceMode) {
            sameYMode.getTargetedBlockPos(blockPos) ?: blockPos.offset(0, -1, 0)
        } else {
            VapeScaffoldController.targetedPosition(blockPos.offset(0, -1, 0))
        }
    }

    private fun simulatePlacementAttempts(
        hitResult: BlockHitResult?,
        suitableHand: InteractionHand?,
    ): Boolean {
        val stack = suitableHand?.let(player::getItemInHand) ?: return false

        if (hitResult == null || !SimulatePlacementAttempts.enabled) {
            return false
        }

        if (hitResult.type != HitResult.Type.BLOCK) {
            return false
        }

        val context = UseOnContext(player, suitableHand, hitResult)

        val canPlaceOnFace = (stack.item as BlockItem).getPlacementState(BlockPlaceContext(context)) != null

        return when {
            SimulatePlacementAttempts.failedAttemptsOnly -> {
                !canPlaceOnFace
            }

            sameYMode != SameYMode.OFF &&
                (sameYMode != SameYMode.JUMP_KEY || mc.options.keyJump.isDown) -> {
                context.clickedPos.y == placementY && (hitResult.direction != Direction.UP || !canPlaceOnFace)
            }

            else -> {
                val isTargetUnderPlayer = context.clickedPos.y <= player.blockY - 1
                val isTowering =
                    context.clickedPos.y == player.blockY - 1 &&
                        canPlaceOnFace &&
                        context.clickedFace == Direction.UP

                isTargetUnderPlayer && !isTowering
            }
        }
    }

    private fun handleSilentBlockSelection(hasBlockInMainHand: Boolean, hasBlockInOffHand: Boolean): Boolean {
        if (!isLiquidBounceMode) {
            if (hasBlockInMainHand || hasBlockInOffHand) return hasBlockInMainHand

            val blockSlot = findBestValidHotbarSlotForTarget() ?: return false
            player.inventory.selectedSlot = blockSlot
            return true
        }

        // Handle silent block selection
        if (ScaffoldAutoBlockFeature.enabled && !hasBlockInMainHand && !hasBlockInOffHand) {
            val bestMainHandSlot = findBestValidHotbarSlotForTarget()

            if (bestMainHandSlot != null) {
                SilentHotbar.selectSlotSilently(
                    this, bestMainHandSlot,
                    ScaffoldAutoBlockFeature.slotResetDelay
                )

                return true
            } else {
                SilentHotbar.resetSlot(this)
            }
        } else {
            SilentHotbar.resetSlot(this)
        }

        return hasBlockInMainHand
    }

    internal enum class ScaffoldImplementation(override val tag: String) : Tagged {
        NORMAL("Normal") {
            override val tagAliases = listOf("LiquidBounce")
        },
        GOD_BRIDGE("GodBridge") {
            override val tagAliases = listOf("Legit")
        },
        TELLY_BRIDGE("TellyBridge"),
    }

}
