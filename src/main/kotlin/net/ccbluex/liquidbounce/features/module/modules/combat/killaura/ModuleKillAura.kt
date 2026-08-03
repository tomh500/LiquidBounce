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
package net.ccbluex.liquidbounce.features.module.modules.combat.killaura

import com.google.gson.JsonObject
import net.ccbluex.liquidbounce.config.types.list.Tagged
import net.ccbluex.liquidbounce.event.events.RotationUpdateEvent
import net.ccbluex.liquidbounce.event.events.SprintEvent
import net.ccbluex.liquidbounce.event.events.WorldRenderEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.features.module.modules.combat.ModuleAutoWeapon
import net.ccbluex.liquidbounce.features.module.modules.combat.criticals.ModuleCriticals.CriticalsSelectionMode
import net.ccbluex.liquidbounce.features.module.modules.combat.elytratarget.ModuleElytraTarget
import net.ccbluex.liquidbounce.features.module.modules.combat.killaura.KillAuraRotationsValueGroup.KillAuraRotationTiming.ON_TICK
import net.ccbluex.liquidbounce.features.module.modules.combat.killaura.KillAuraRotationsValueGroup.KillAuraRotationTiming.SNAP
import net.ccbluex.liquidbounce.features.module.modules.combat.killaura.ModuleKillAura.RaycastMode.TRACE_ALL
import net.ccbluex.liquidbounce.features.module.modules.combat.killaura.ModuleKillAura.RaycastMode.TRACE_NONE
import net.ccbluex.liquidbounce.features.module.modules.combat.killaura.ModuleKillAura.RaycastMode.TRACE_ONLYENEMY
import net.ccbluex.liquidbounce.features.module.modules.combat.killaura.ModuleKillAura.waitTicks
import net.ccbluex.liquidbounce.features.module.modules.combat.killaura.features.KillAuraAutoBlock
import net.ccbluex.liquidbounce.features.module.modules.combat.killaura.features.KillAuraFailSwing
import net.ccbluex.liquidbounce.features.module.modules.combat.killaura.features.KillAuraFailSwing.dealWithFakeSwing
import net.ccbluex.liquidbounce.features.module.modules.combat.killaura.features.KillAuraFightBot
import net.ccbluex.liquidbounce.features.module.modules.combat.killaura.features.KillAuraNotifyWhenFail
import net.ccbluex.liquidbounce.features.module.modules.combat.killaura.features.KillAuraNotifyWhenFail.failedHits
import net.ccbluex.liquidbounce.features.module.modules.combat.killaura.features.KillAuraNotifyWhenFail.renderFailedHits
import net.ccbluex.liquidbounce.features.module.modules.combat.killaura.features.KillAuraRange
import net.ccbluex.liquidbounce.features.module.modules.combat.killaura.features.KillAuraRangeIndicator
import net.ccbluex.liquidbounce.features.module.modules.misc.debugrecorder.modes.GenericDebugRecorder
import net.ccbluex.liquidbounce.features.module.modules.render.ModuleDebug
import net.ccbluex.liquidbounce.features.module.modules.render.ModuleDebug.debugGeometry
import net.ccbluex.liquidbounce.features.module.modules.render.ModuleDebug.debugParameter
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.render.renderEnvironment
import net.ccbluex.liquidbounce.utils.aiming.RotationManager
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.aiming.data.RotationWithVector
import net.ccbluex.liquidbounce.utils.aiming.point.PointTracker
import net.ccbluex.liquidbounce.utils.aiming.preference.LeastDifferencePreference
import net.ccbluex.liquidbounce.utils.aiming.utils.raytraceBox
import net.ccbluex.liquidbounce.utils.block.SwingMode
import net.ccbluex.liquidbounce.utils.combat.CombatManager
import net.ccbluex.liquidbounce.utils.combat.attackEntity
import net.ccbluex.liquidbounce.utils.combat.shouldBeAttacked
import net.ccbluex.liquidbounce.utils.entity.rotation
import net.ccbluex.liquidbounce.utils.entity.squaredBoxedDistanceTo
import net.ccbluex.liquidbounce.utils.inventory.InventoryManager.isInventoryOpen
import net.ccbluex.liquidbounce.utils.inventory.isInContainerScreen
import net.ccbluex.liquidbounce.utils.input.InputTracker.isPressedOnAny
import net.ccbluex.liquidbounce.utils.collection.itemSortedSetOf
import net.ccbluex.liquidbounce.utils.kotlin.Priority
import net.ccbluex.liquidbounce.utils.math.sq
import net.ccbluex.liquidbounce.utils.aiming.utils.RotationUtil
import net.ccbluex.liquidbounce.utils.combat.TargetPriority
import net.ccbluex.liquidbounce.utils.item.attackDamage
import net.ccbluex.liquidbounce.utils.raytracing.findEntityInCrosshair
import net.ccbluex.liquidbounce.utils.raytracing.isLookingAtEntity
import net.ccbluex.liquidbounce.utils.render.TargetRenderer
import net.minecraft.client.gui.screens.inventory.ContainerScreen
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import kotlin.random.Random

/**
 * KillAura module
 *
 * Automatically attacks enemies.
 */
@Suppress("MagicNumber")
object ModuleKillAura : ClientModule("KillAura", ModuleCategories.COMBAT) {

    private val modeValue = enumChoice("Mode", KillAuraMode.LIQUID_BOUNCE)
    private val mode by modeValue

    internal val isLiquidBounceMode: Boolean
        get() = mode == KillAuraMode.LIQUID_BOUNCE

    private val vapeAttackRate by intRange("AttacksPerSecond", 6..13, 1..20, "attacks")
        .visibleWhen { mode == KillAuraMode.VAPE }
    private val vapeSwingRangeValue = float("SwingRange", 4f, 0f..6f, "blocks")
        .visibleWhen { mode == KillAuraMode.VAPE }
    private val vapeSwingRange by vapeSwingRangeValue
    private val vapeAttackRangeValue = float("AttackRange", 3.5f, 0f..6f, "blocks")
        .visibleWhen { mode == KillAuraMode.VAPE }
    private val vapeAttackRange by vapeAttackRangeValue
    private val vapeMaxAngle by float("MaxAngle", 90f, 1f..360f, "degrees")
        .visibleWhen { mode == KillAuraMode.VAPE }
    private val vapeMaxTargets by int("MaxTargets", 1, 1..6)
        .visibleWhen { mode == KillAuraMode.VAPE }
    private val vapeTargetMode by enumChoice("TargetMode", VapeTargetMode.DISTANCE)
        .visibleWhen { mode == KillAuraMode.VAPE }
    private val vapePerfectSwing by boolean("PerfectSwing", false)
        .visibleWhen { mode == KillAuraMode.VAPE }
    private val vapeDisableOnDeath by boolean("DisableOnDeath", false)
        .visibleWhen { mode == KillAuraMode.VAPE }
    private val vapeRequireMouseDown by boolean("RequireMouseDown", false)
        .visibleWhen { mode == KillAuraMode.VAPE }
    private val vapeGuiCheck by boolean("GuiCheck", true)
        .visibleWhen { mode == KillAuraMode.VAPE }
    private val vapeShowTarget by boolean("ShowTarget", false)
        .visibleWhen { mode == KillAuraMode.VAPE }
    private val vapeLimitToItems by boolean("LimitToItems", false)
        .visibleWhen { mode == KillAuraMode.VAPE }
    private val vapeAllowedItems by items("AllowedItems", itemSortedSetOf())
        .visibleWhen { mode == KillAuraMode.VAPE && vapeLimitToItems }

    // Attack speed
    val clicker = tree(KillAuraClicker).also { it.visibleWhen { mode == KillAuraMode.LIQUID_BOUNCE } }
    val range = tree(KillAuraRange).also { it.visibleWhen { mode == KillAuraMode.LIQUID_BOUNCE } }
    val targetTracker = tree(KillAuraTargetTracker).also {
        it.visibleWhen { mode == KillAuraMode.LIQUID_BOUNCE }
    }

    // Rotation
    private val rotations = tree(KillAuraRotationsValueGroup).also {
        it.visibleWhen { mode == KillAuraMode.LIQUID_BOUNCE }
    }
    private val pointTracker = tree(PointTracker(this)).also {
        it.visibleWhen { mode == KillAuraMode.LIQUID_BOUNCE }
    }

    private val requires by multiEnumChoice<KillAuraRequirements>("Requires")
        .visibleWhen { mode == KillAuraMode.LIQUID_BOUNCE }

    private val requirementsMet
        get() = requires.all { it.asBoolean }

    // Bypass techniques
    internal val raycast by enumChoice("Raycast", TRACE_ALL)
        .visibleWhen { mode == KillAuraMode.LIQUID_BOUNCE }
    private val criticalsSelectionMode by enumChoice("Criticals", CriticalsSelectionMode.SMART)
        .visibleWhen { mode == KillAuraMode.LIQUID_BOUNCE }
    private val keepSprint by boolean("KeepSprint", true)
        .visibleWhen { mode == KillAuraMode.LIQUID_BOUNCE }

    // Inventory Handling
    internal val ignoreOpenInventory by boolean("IgnoreOpenInventory", true)
        .visibleWhen { mode == KillAuraMode.LIQUID_BOUNCE }
    internal val simulateInventoryClosing by boolean("SimulateInventoryClosing", true)
        .visibleWhen { mode == KillAuraMode.LIQUID_BOUNCE }

    /**
     * The use of suspend [waitTicks] is a bit too
     * risky for a large and complex module
     * such as KillAura. So back to the basics.
     */
    internal var waitTicks = 0
    private var vapeNextAttackAt = 0L
    private var vapePauseTicks = 0

    init {
        modeValue.onChanged { newMode ->
            targetTracker.reset()
            vapeNextAttackAt = 0L
            vapePauseTicks = 0
            if (newMode == KillAuraMode.VAPE) {
                waitTicks = 0
            }
        }
        vapeSwingRangeValue.onChange { newRange ->
            newRange.coerceAtLeast(vapeAttackRange + RANGE_INCREMENT)
        }
        vapeAttackRangeValue.onChange { newRange ->
            newRange.coerceAtMost(vapeSwingRange - RANGE_INCREMENT)
        }
        tree(KillAuraAutoBlock).visibleWhen { mode == KillAuraMode.LIQUID_BOUNCE }
        tree(TargetRenderer(this) {
            targetTracker.target?.takeIf { mode == KillAuraMode.LIQUID_BOUNCE || vapeShowTarget }
                ?.takeUnless { ModuleElytraTarget.isSameTargetRendering(it) }
        })
        tree(KillAuraFailSwing).visibleWhen { mode == KillAuraMode.LIQUID_BOUNCE }
        tree(KillAuraFightBot).visibleWhen { mode == KillAuraMode.LIQUID_BOUNCE }
        tree(KillAuraRangeIndicator).visibleWhen { mode == KillAuraMode.LIQUID_BOUNCE }
    }

    override fun onDisabled() {
        targetTracker.reset()
        vapeNextAttackAt = 0L
        vapePauseTicks = 0
        failedHits.clear()
        KillAuraNotifyWhenFail.failedHitsIncrement = 0
    }

    @Suppress("unused")
    private val renderHandler = handler<WorldRenderEvent> { event ->
        if (mode != KillAuraMode.LIQUID_BOUNCE) {
            return@handler
        }

        event.renderEnvironment {
            renderFailedHits()
            KillAuraRangeIndicator.render(this, event.partialTicks)
        }
    }

    @Suppress("unused")
    private val rotationUpdateHandler = handler<RotationUpdateEvent> {
        if (mode != KillAuraMode.LIQUID_BOUNCE) {
            return@handler
        }

        if (waitTicks > 0) {
            waitTicks--
        }

        // Make sure killaura-logic is not running while inventory is open
        val isInInventoryScreen = isInventoryOpen || mc.gui.screen() is ContainerScreen
        val shouldResetTarget = player.isSpectator || player.isDeadOrDying || !requirementsMet

        if (isInInventoryScreen && !ignoreOpenInventory || shouldResetTarget) {
            // Reset current target
            targetTracker.reset()
            return@handler
        }

        // Update the current target tracker to make sure you attack the best enemy
        updateTarget()

        // Update Auto Weapon
        ModuleAutoWeapon.onTarget(targetTracker.target)
    }

    @Suppress("unused")
    private val gameHandler = tickHandler {
        if (mode == KillAuraMode.VAPE) {
            runVapeTick()
            return@tickHandler
        }

        if (player.isDeadOrDying || player.isSpectator) {
            return@tickHandler
        }

        // Check if there is target to attack
        val target = targetTracker.target

        if (CombatManager.shouldPauseCombat) {
            KillAuraAutoBlock.stopBlocking()
            return@tickHandler
        }

        if (target == null) {
            val hasUnblocked = KillAuraAutoBlock.stopBlocking()

            // Deal with fake swing when there is no target
            if (KillAuraFailSwing.enabled && requirementsMet) {
                if (hasUnblocked && KillAuraAutoBlock.pauseOnUnblockTicks > 0) {
                    waitTicks = KillAuraAutoBlock.pauseOnUnblockTicks
                } else {
                    dealWithFakeSwing(null)
                }
            }
            return@tickHandler
        }

        // Check if the module should (not) continue after the blocking state is updated
        if (!requirementsMet) {
            return@tickHandler
        }

        val rotation = (if (rotations.rotationTiming == ON_TICK) {
            findRotation(target, range.interactionRange, range.interactionThroughWallsRange)?.rotation
        } else {
            null
        } ?: RotationManager.currentRotation ?: player.rotation).normalize()

        val crosshairTarget = when {
            raycast != TRACE_NONE -> {
                findEntityInCrosshair(range.interactionRange.toDouble(), rotation, predicate = {
                    when (raycast) {
                        TRACE_ONLYENEMY -> it.shouldBeAttacked()
                        TRACE_ALL -> true
                        else -> false
                    }
                })?.entity ?: target
            }
            else -> target
        }

        if (crosshairTarget is LivingEntity && crosshairTarget.shouldBeAttacked() && crosshairTarget != target) {
            targetTracker.target = crosshairTarget
        }

        attackTarget(crosshairTarget, rotation)
    }

    val shouldBlockSprinting
        get() = !ModuleElytraTarget.running
            && criticalsSelectionMode.shouldStopSprinting(clicker, targetTracker.target)

    @Suppress("unused")
    private val sprintHandler = handler<SprintEvent> { event ->
        if (mode != KillAuraMode.LIQUID_BOUNCE) {
            return@handler
        }

        if (shouldBlockSprinting && (event.source == SprintEvent.Source.MOVEMENT_TICK ||
                event.source == SprintEvent.Source.INPUT)) {
            event.sprint = false
        }
    }

    @Suppress("CognitiveComplexMethod", "CyclomaticComplexMethod")
    private fun attackTarget(target: Entity, rotation: Rotation) {
        // Make it seem like we are blocking
        KillAuraAutoBlock.makeSeemBlock()

        debugParameter("Rotation") { rotation }
        debugParameter("Target") { target.scoreboardName }

        val attackHitResult = isLookingAtEntity(
            toEntity = target,
            rotation = rotation,
            range = range.interactionRange.toDouble(),
            throughWallsRange = range.interactionThroughWallsRange.toDouble()
        )

        debugParameter("Target Hit Result") { attackHitResult?.location }

        val isInRange = ModuleElytraTarget.canIgnoreKillAuraRotations ||
            attackHitResult != null && range.isInRange(pos = attackHitResult.location)
        debugParameter("Is In Range") { isInRange }

        // Check if our target is in range, otherwise deal with auto block
        if (!isInRange) {
            if (KillAuraAutoBlock.enabled && KillAuraAutoBlock.onScanRange &&
                player.squaredBoxedDistanceTo(target) <= range.scanRange.sq()) {
                if (KillAuraClicker.ticksSinceLastClick >= KillAuraAutoBlock.reblockTicks) {
                    KillAuraAutoBlock.startBlocking()
                }

                return
            }

            // Make sure we are not blocking
            val hasUnblocked = KillAuraAutoBlock.stopBlocking()
            if (hasUnblocked && KillAuraAutoBlock.pauseOnUnblockTicks > 0) {
                waitTicks = KillAuraAutoBlock.pauseOnUnblockTicks
            }else if (KillAuraFailSwing.enabled) {
                dealWithFakeSwing(target)
            }
            return
        }

        debugParameter("Valid Rotation") { rotation }

        val mainHandStack = player.mainHandItem

        // Attack enemy, according to the attack scheduler
        if (clicker.isClickTick && canAttackNow(target, mainHandStack) &&
            !KillAuraAutoBlock.isPrioritizingBlocking) {
            clicker.prepareForAttack(rotation) {
                // On each click, we check if we are still ready to attack
                if (!canAttackNow(target, mainHandStack)) {
                    return@prepareForAttack false
                }

                // Attack enemy
                attackEntity(target, SwingMode.DO_NOT_HIDE, keepSprint && !shouldBlockSprinting)
                range.update()
                KillAuraNotifyWhenFail.failedHitsIncrement = 0
                KillAuraAutoBlock.hasBlockedSinceAttack = false

                GenericDebugRecorder.recordDebugInfo(ModuleKillAura, "attackEntity", JsonObject().apply {
                    add("player", GenericDebugRecorder.debugObject(player))
                    add("targetPos", GenericDebugRecorder.debugObject(target))
                })

                true
            }
        } else if (KillAuraClicker.ticksSinceLastClick >= KillAuraAutoBlock.reblockTicks) {
            KillAuraAutoBlock.startBlocking()
        }
    }

    private fun updateTarget() {
        // Calculate maximum range based on enemy distance
        val maximumRange = if (targetTracker.closestSquaredEnemyDistance > range.interactionRange.sq()) {
            range.scanRange
        } else {
            range.interactionRange
        }

        debugParameter("Maximum Range") { maximumRange }
        debugParameter("Range") { range }
        val squaredMaxRange = maximumRange.sq()
        val squaredNormalRange = range.interactionRange.sq()

        // Find a suitable target
        val target = targetTracker.targets()
            .filter { entity -> entity.squaredBoxedDistanceTo(player) <= squaredMaxRange }
            .sortedBy { entity -> if (entity.squaredBoxedDistanceTo(player) <= squaredNormalRange) 0 else 1 }
            .firstOrNull { entity -> processTarget(entity, maximumRange, range.interactionThroughWallsRange) }

        if (target != null) {
            targetTracker.target = target
        } else if (KillAuraFightBot.enabled) {
            KillAuraFightBot.updateTarget()

            RotationManager.setRotationTarget(
                rotations.toRotationTarget(
                    KillAuraFightBot.getMovementRotation(),
                    considerInventory = !ignoreOpenInventory
                ),
                priority = Priority.IMPORTANT_FOR_USAGE_2,
                provider = ModuleKillAura
            )
        } else {
            targetTracker.reset()
        }
    }

    @Suppress("CognitiveComplexMethod", "ComplexCondition", "ReturnCount")
    private fun runVapeTick() {
        if (KillAuraAutoBlock.blockVisual) {
            KillAuraAutoBlock.stopBlocking()
        }

        if (vapePauseTicks > 0) {
            vapePauseTicks--
            return
        }

        if (vapeGuiCheck && mc.gui.screen() != null) {
            vapePauseTicks = 1
            targetTracker.reset()
            return
        }

        if (vapeDisableOnDeath && (player.isDeadOrDying || player.health <= 0f)) {
            enabled = false
            return
        }

        if (player.isDeadOrDying || player.isSpectator ||
            vapeRequireMouseDown && !mc.options.keyAttack.isPressedOnAny ||
            vapeLimitToItems && player.mainHandItem.item !in vapeAllowedItems
        ) {
            targetTracker.reset()
            return
        }

        val targets = world.entitiesForRendering()
            .asSequence()
            .filterIsInstance<LivingEntity>()
            .filter { it !== player && !it.isRemoved && it.isAlive && it.shouldBeAttacked() }
            .filter { player.distanceTo(it) <= vapeSwingRange }
            .filter { RotationUtil.crosshairAngleToEntity(it) <= vapeMaxAngle / 2f }
            .sortedWith(vapeTargetComparator())
            .take(vapeMaxTargets)
            .toList()

        targetTracker.target = targets.firstOrNull()
        if (targets.isEmpty() || System.currentTimeMillis() < vapeNextAttackAt ||
            vapePerfectSwing && player.getAttackStrengthScale(0.5f) < 1f
        ) {
            return
        }

        var swung = false
        targets.forEach { target ->
            if (player.distanceTo(target) <= vapeAttackRange) {
                attackEntity(target, SwingMode.DO_NOT_HIDE)
            } else if (!swung) {
                SwingMode.DO_NOT_HIDE.swing(InteractionHand.MAIN_HAND)
            }
            swung = true
        }

        val cps = Random.nextInt(vapeAttackRate.first, vapeAttackRate.last + 1)
        vapeNextAttackAt = System.currentTimeMillis() + 1000L / cps
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

    @Suppress("ReturnCount")
    private fun processTarget(
        entity: LivingEntity,
        range: Float,
        wallsRange: Float
    ): Boolean {
        val (rotation, _) = findRotation(entity, range, wallsRange) ?: return false
        val ticks = rotations.calculateTicks(rotation)
        debugParameter("Rotation Ticks") { ticks }

        when (rotations.rotationTiming) {

            // If our click scheduler is not going to click the moment we reach the target,
            // we should not start aiming towards the target just yet.
            SNAP -> if (!clicker.willClickAt(ticks.coerceAtLeast(1))) {
                return true
            }

            // [ON_TICK] will always instantly aim onto the target on attack, however, if
            // our rotation is unable to be ready in time, we can at least start aiming towards
            // the target.
            ON_TICK -> if (ticks <= 1) {
                return true
            }

            else -> {
                // Continue with regular aiming
            }
        }

        RotationManager.setRotationTarget(
            rotations.toRotationTarget(
                rotation,
                entity,
                considerInventory = !ignoreOpenInventory
            ),
            priority = Priority.IMPORTANT_FOR_USAGE_2,
            provider = this@ModuleKillAura
        )
        return true
    }

    /**
     * Get the best spot to attack the entity
     *
     * @param entity The entity to attack
     * @param range The range to attack the entity (NOT SQUARED)
     *
     *  @return The best spot to attack the entity
     */
    private fun findRotation(entity: Entity, range: Float, wallsRange: Float): RotationWithVector? {
        val eyes = player.eyePosition
        val point = pointTracker.findPoint(eyes, entity)

        debugGeometry("Box") { ModuleDebug.DebuggedBox(point.box, Color4b.ORANGE.with(a = 90)) }
        debugGeometry("Point") { ModuleDebug.DebuggedPoint(point.pos, Color4b.WHITE, size = 0.1) }

        val rotationPreference = LeastDifferencePreference.leastDifferenceToLastPoint(eyes, point.pos)

        // raytrace to the point
        val rotation = raytraceBox(
            eyes = eyes,
            box = point.box,
            range = range.toDouble(),
            wallsRange = wallsRange.toDouble(),
            rotationPreference = rotationPreference
        )

        return if (rotation == null && rotations.aimThroughWalls) {
            val rotationThroughWalls = raytraceBox(
                eyes = eyes,
                box = point.box,
                // Since [range] is squared, we need to square root
                range = range.toDouble(),
                wallsRange = range.toDouble(),
                rotationPreference = rotationPreference
            )

            rotationThroughWalls
        } else {
            rotation
        }
    }

    /**
     * Check if we can attack the target at the current moment
     */
    internal fun canAttackNow(
        target: Entity? = null,
        itemStack: ItemStack = player.mainHandItem,
    ): Boolean {
        if (!itemStack.isItemEnabled(world.enabledFeatures())) {
            return false
        }

        if (player.cannotAttackWithItem(itemStack, 0)) {
            return false
        }

        val criticalHitAllowed = target == null || player.isFallFlying || criticalsSelectionMode.isCriticalHit(target)
        if (!criticalHitAllowed) {
            return false
        }

        val isInventoryBlockingAttack = (isInventoryOpen || isInContainerScreen) &&
            !ignoreOpenInventory && !simulateInventoryClosing
        return !isInventoryBlockingAttack
    }

    enum class RaycastMode(override val tag: String) : Tagged {
        TRACE_NONE("None"),
        TRACE_ONLYENEMY("Enemy"),
        TRACE_ALL("All")
    }

    private enum class KillAuraMode(override val tag: String) : Tagged {
        LIQUID_BOUNCE("LiquidBounce"),
        VAPE("Vape"),
    }

    private enum class VapeTargetMode(override val tag: String) : Tagged {
        DISTANCE("Distance"),
        YAW("Yaw"),
        ARMOR("Armor"),
        THREAT("Threat"),
        HEALTH("Health"),
    }

    private const val RANGE_INCREMENT = 0.1f

}
