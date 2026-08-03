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
package net.ccbluex.liquidbounce.features.module.modules.combat

import net.ccbluex.liquidbounce.config.types.list.Tagged
import net.ccbluex.liquidbounce.event.events.MouseRotationEvent
import net.ccbluex.liquidbounce.event.events.RotationUpdateEvent
import net.ccbluex.liquidbounce.event.events.WorldRenderEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.features.module.modules.combat.killaura.KillAuraRequirements
import net.ccbluex.liquidbounce.features.module.modules.render.ModuleDebug
import net.ccbluex.liquidbounce.features.module.modules.render.ModuleDebug.debugGeometry
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.utils.aiming.RotationTarget
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.aiming.data.RotationWithVector
import net.ccbluex.liquidbounce.utils.aiming.features.MovementCorrection
import net.ccbluex.liquidbounce.utils.aiming.features.processors.anglesmooth.impl.InterpolationAngleSmooth
import net.ccbluex.liquidbounce.utils.aiming.features.processors.anglesmooth.impl.LinearAngleSmooth
import net.ccbluex.liquidbounce.utils.aiming.features.processors.anglesmooth.impl.SigmoidAngleSmooth
import net.ccbluex.liquidbounce.utils.aiming.point.PointTracker
import net.ccbluex.liquidbounce.utils.aiming.preference.LeastDifferencePreference
import net.ccbluex.liquidbounce.utils.aiming.utils.RotationUtil
import net.ccbluex.liquidbounce.utils.aiming.utils.raytraceBox
import net.ccbluex.liquidbounce.utils.aiming.utils.setRotation
import net.ccbluex.liquidbounce.utils.client.Timer
import net.ccbluex.liquidbounce.utils.combat.TargetPriority
import net.ccbluex.liquidbounce.utils.combat.TargetTracker
import net.ccbluex.liquidbounce.utils.entity.rotation
import net.ccbluex.liquidbounce.utils.entity.movementSideways
import net.ccbluex.liquidbounce.utils.inventory.InventoryManager
import net.ccbluex.liquidbounce.utils.collection.itemSortedSetOf
import net.ccbluex.liquidbounce.utils.combat.shouldBeAttacked
import net.ccbluex.liquidbounce.utils.item.attackDamage
import net.ccbluex.liquidbounce.utils.math.getNearestPoint
import net.ccbluex.liquidbounce.utils.render.TargetRenderer
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Items
import net.minecraft.world.phys.HitResult

/**
 * Aimbot module
 *
 * Automatically faces selected entities around you.
 */
object ModuleAimbot : ClientModule("Aimbot", ModuleCategories.COMBAT, aliases = listOf("AimAssist", "AutoAim")) {

    private val mode by enumChoice("Mode", AimbotMode.LIQUID_BOUNCE)
    private val vapeRequireMouseDown by boolean("RequireMouseDown", true)
        .visibleWhen { mode == AimbotMode.VAPE }
    private val vapeAimVertically by boolean("AimVertically", false)
        .visibleWhen { mode == AimbotMode.VAPE }
    private val vapeMaxAngle by float("MaxAngle", 180f, 1f..360f, "degrees")
        .visibleWhen { mode == AimbotMode.VAPE }
    private val vapeHorizontalSpeed by float("HorizontalSpeed", 5f, 1f..10f, "degrees")
        .visibleWhen { mode == AimbotMode.VAPE }
    private val vapeVerticalSpeed by float("VerticalSpeed", 5f, 1f..10f, "degrees")
        .visibleWhen { mode == AimbotMode.VAPE && vapeAimVertically }
    private val vapeCheckBlockBreak by boolean("CheckBlockBreak", false)
        .visibleWhen { mode == AimbotMode.VAPE }
    private val vapeBreakBlocksWhitelist by boolean("BreakBlocksWhitelist", false)
        .visibleWhen { mode == AimbotMode.VAPE && vapeCheckBlockBreak }
    private val vapeBlockBreakItems by items(
        "BlockBreakItems",
        itemSortedSetOf(
            Items.WOODEN_PICKAXE, Items.STONE_PICKAXE, Items.IRON_PICKAXE,
            Items.GOLDEN_PICKAXE, Items.DIAMOND_PICKAXE, Items.NETHERITE_PICKAXE,
            Items.WOODEN_SHOVEL, Items.STONE_SHOVEL, Items.IRON_SHOVEL,
            Items.GOLDEN_SHOVEL, Items.DIAMOND_SHOVEL, Items.NETHERITE_SHOVEL,
        )
    ).visibleWhen { mode == AimbotMode.VAPE && vapeCheckBlockBreak && vapeBreakBlocksWhitelist }
    private val vapeStrafeIncrease by boolean("StrafeIncrease", false)
        .visibleWhen { mode == AimbotMode.VAPE }
    private val vapeLimitToItems by boolean("LimitToItems", false)
        .visibleWhen { mode == AimbotMode.VAPE }
    private val vapeAllowedItems by items(
        "AllowedItems",
        itemSortedSetOf(
            Items.WOODEN_SWORD, Items.STONE_SWORD, Items.IRON_SWORD,
            Items.GOLDEN_SWORD, Items.DIAMOND_SWORD, Items.NETHERITE_SWORD,
        )
    )
        .visibleWhen { mode == AimbotMode.VAPE && vapeLimitToItems }
    private val vapeTargetArea by enumChoice("TargetArea", VapeTargetArea.CENTER)
        .visibleWhen { mode == AimbotMode.VAPE }
    private val vapeTargetMode by enumChoice("TargetMode", VapeTargetMode.YAW)
        .visibleWhen { mode == AimbotMode.VAPE }

    private val range = float("Range", 4.2f, 1f..8f)

    val targetTracker = tree(TargetTracker(TargetPriority.DIRECTION, range = range)).also {
        it.visibleWhen { mode == AimbotMode.LIQUID_BOUNCE }
    }

    init {
        tree(TargetRenderer(this, targetTracker))
    }
    private val pointTracker = tree(PointTracker(this)).also {
        it.visibleWhen { mode == AimbotMode.LIQUID_BOUNCE }
    }

    private val requires by multiEnumChoice<KillAuraRequirements>("Requires")
        .visibleWhen { mode == AimbotMode.LIQUID_BOUNCE }

    private val requirementsMet
        get() = when (mode) {
            AimbotMode.LIQUID_BOUNCE -> mc.gui.screen() == null && requires.all { it.asBoolean }
            AimbotMode.VAPE -> (!vapeRequireMouseDown || mc.options.keyAttack.isDown) &&
                canAimWhileBreakingBlocks() &&
                (!vapeLimitToItems || player.mainHandItem.item in vapeAllowedItems)
        }

    private var blockBreakCooldownUntil = 0L

    private fun canAimWhileBreakingBlocks(): Boolean {
        if (!vapeCheckBlockBreak) {
            blockBreakCooldownUntil = 0L
            return true
        }

        val shouldCheckBlock = !vapeBreakBlocksWhitelist || player.mainHandItem.item in vapeBlockBreakItems
        if (!shouldCheckBlock) {
            blockBreakCooldownUntil = 0L
            return true
        }

        if (mc.hitResult?.type == HitResult.Type.BLOCK) {
            blockBreakCooldownUntil = System.currentTimeMillis() + BLOCK_BREAK_COOLDOWN_MS
            return false
        }

        return System.currentTimeMillis() >= blockBreakCooldownUntil
    }

    private val angleSmooth = modes(this, "AngleSmooth") {
        arrayOf(
            InterpolationAngleSmooth(it),
            SigmoidAngleSmooth(it),
            LinearAngleSmooth(it)
        )
    }.also { it.visibleWhen { mode == AimbotMode.LIQUID_BOUNCE } }

    private val axis by multiEnumChoice<Axis>("Axis", Axis.HORIZONTAL, Axis.VERTICAL)
        .visibleWhen { mode == AimbotMode.LIQUID_BOUNCE }

    private val ignores by multiEnumChoice<IgnoreOpened>("Ignore")
        .visibleWhen { mode == AimbotMode.LIQUID_BOUNCE }

    private var targetRotation: Rotation? = null
    private var playerRotation: Rotation? = null

    @Suppress("unused", "ComplexCondition")
    private val tickHandler = handler<RotationUpdateEvent> { _ ->
        playerRotation = player.rotation

        if (!requirementsMet) {
            targetTracker.reset()
            targetRotation = null
            return@handler
        }

        targetRotation = findNextTargetRotation()?.let { (target, rotation) ->
            when (mode) {
                AimbotMode.LIQUID_BOUNCE -> angleSmooth.activeMode.process(
                    RotationTarget(
                        rotation = rotation.rotation,
                        entity = target,
                        processors = listOf(angleSmooth.activeMode),
                        ticksUntilReset = 1,
                        resetThreshold = 1f,
                        considerInventory = true,
                        movementCorrection = MovementCorrection.CHANGE_LOOK
                    ),
                    player.rotation,
                    rotation.rotation
                )
                AimbotMode.VAPE -> rotation.rotation.takeIf {
                    player.rotation.directionAngleTo(it) <= vapeMaxAngle / 2f
                }
            }
        }

        // Update Auto Weapon
        ModuleAutoWeapon.onTarget(targetTracker.target)
    }

    override fun onDisabled() {
        targetTracker.reset()
    }

    @Suppress("unused")
    private val renderHandler = handler<WorldRenderEvent> { event ->
        val partialTicks = event.partialTicks
        val target = targetTracker.target ?: return@handler

        if (mode == AimbotMode.LIQUID_BOUNCE) {
            if (IgnoreOpened.SCREEN !in ignores && mc.gui.screen() != null) {
                return@handler
            }

            if (IgnoreOpened.CONTAINER !in ignores && (InventoryManager.isInventoryOpen ||
                    mc.gui.screen() is AbstractContainerScreen<*>)) {
                return@handler
            }
        }

        lookAt(partialTicks)
    }

    @Suppress("unused")
    private val mouseMovement = handler<MouseRotationEvent> { event ->
        fun updateRotation(rotation: Rotation): Rotation =
            RotationUtil.applyMouseTurnDelta(rotation, event.cursorDeltaX, event.cursorDeltaY)

        playerRotation?.let { rotation ->
            playerRotation = updateRotation(rotation)
        }

        targetRotation?.let { rotation ->
            targetRotation = updateRotation(rotation)
        }
    }

    /**
     * Looks at the target rotation, with interpolation based on the timer speed and partial ticks to make it smooth.
     */
    private fun lookAt(partialTicks: Float) {
        val playerRotation = playerRotation ?: return
        val targetRotation = targetRotation ?: return
        val timerSpeed = Timer.timerSpeed
        val interpolatedRotation = when (mode) {
            AimbotMode.VAPE -> {
                val yawDifference = RotationUtil.angleDifference(targetRotation.yaw, playerRotation.yaw)
                val strafeInput = player.input.movementSideways
                val strafingAway = yawDifference > 0f && strafeInput < 0f ||
                    yawDifference < 0f && strafeInput > 0f
                val strafeMultiplier = if (vapeStrafeIncrease && strafingAway) 1.6f else 1f
                playerRotation.towardsLinear(
                    targetRotation,
                    vapeHorizontalSpeed * strafeMultiplier * partialTicks,
                    vapeVerticalSpeed * partialTicks,
                )
            }
            AimbotMode.LIQUID_BOUNCE -> playerRotation.interpolateTo(targetRotation, timerSpeed * partialTicks)
        }

        player.setRotation(
            Rotation(
                yaw = if (mode == AimbotMode.VAPE || Axis.HORIZONTAL in axis) {
                    interpolatedRotation.yaw
                } else {
                    playerRotation.yaw
                },
                pitch = if (mode == AimbotMode.VAPE && vapeAimVertically ||
                    mode == AimbotMode.LIQUID_BOUNCE && Axis.VERTICAL in axis) {
                    interpolatedRotation.pitch
                } else {
                    playerRotation.pitch
                },
            )
        )
    }

    private fun findNextTargetRotation(): Pair<Entity, RotationWithVector>? {
        val targets = when (mode) {
            AimbotMode.LIQUID_BOUNCE -> targetTracker.targets()
            AimbotMode.VAPE -> world.entitiesForRendering()
                .asSequence()
                .filterIsInstance<LivingEntity>()
                .filter { it !== player && !it.isRemoved && it.isAlive && it.shouldBeAttacked() }
                .filter { player.distanceTo(it) <= range.get() }
                .sortedWith(vapeTargetComparator())
                .toList()
        }

        for (entity in targets) {
            val eyes = player.eyePosition
            val point = if (mode == AimbotMode.LIQUID_BOUNCE) {
                pointTracker.findPoint(eyes, entity)
            } else {
                val vec = when (vapeTargetArea) {
                    VapeTargetArea.CENTER -> entity.boundingBox.center
                    VapeTargetArea.CLOSEST -> entity.boundingBox.getNearestPoint(eyes)
                }
                val rotation = Rotation.lookingAt(vec, eyes)
                if (player.rotation.directionAngleTo(rotation) > vapeMaxAngle / 2f) {
                    continue
                }
                return entity to RotationWithVector(rotation, vec).also {
                    targetTracker.target = entity
                }
            }

            debugGeometry("Box") { ModuleDebug.DebuggedBox(point.box, Color4b.ORANGE.with(a = 90)) }
            debugGeometry("Point") { ModuleDebug.DebuggedPoint(point.pos, Color4b.WHITE, size = 0.1) }

            val rotationPreference = LeastDifferencePreference.leastDifferenceToLastPoint(eyes, point.pos)
            val rotation = raytraceBox(
                eyes = eyes,
                box = point.box,
                range = targetTracker.maxRange.toDouble(),
                wallsRange = 0.0,
                rotationPreference = rotationPreference
            ) ?: continue

            targetTracker.target = entity
            return entity to rotation
        }

        targetTracker.reset()
        return null
    }

    private enum class IgnoreOpened(
        override val tag: String
    ) : Tagged {
        SCREEN("Screen"),
        CONTAINER("Container")
    }

    private enum class Axis(override val tag: String) : Tagged {
        HORIZONTAL("Horizontal"),
        VERTICAL("Vertical")
    }

    private enum class AimbotMode(override val tag: String) : Tagged {
        LIQUID_BOUNCE("LiquidBounce"),
        VAPE("Vape"),
    }

    private enum class VapeTargetArea(override val tag: String) : Tagged {
        CENTER("Center"),
        CLOSEST("Closest"),
    }

    private fun vapeTargetComparator(): Comparator<LivingEntity> = when (vapeTargetMode) {
        VapeTargetMode.DISTANCE -> TargetPriority.DISTANCE
        VapeTargetMode.YAW -> TargetPriority.DIRECTION
        VapeTargetMode.HEALTH -> TargetPriority.HEALTH
        VapeTargetMode.THREAT -> compareBy { target ->
            if (target is Player) target.mainHandItem.attackDamage else player.distanceTo(target).toDouble()
        }
        VapeTargetMode.ARMOR -> compareBy { target ->
            if (target is Player) target.armorValue else player.distanceTo(target).toInt()
        }
    }

    private enum class VapeTargetMode(override val tag: String) : Tagged {
        DISTANCE("Distance"),
        YAW("Yaw"),
        ARMOR("Armor"),
        THREAT("Threat"),
        HEALTH("Health"),
    }

    private const val BLOCK_BREAK_COOLDOWN_MS = 250L
}
