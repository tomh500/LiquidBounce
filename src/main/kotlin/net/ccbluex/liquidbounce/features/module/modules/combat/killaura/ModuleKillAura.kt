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
import net.ccbluex.liquidbounce.config.types.group.Mode
import net.ccbluex.liquidbounce.config.types.group.ModeValueGroup
import net.ccbluex.liquidbounce.config.types.list.Tagged
import net.ccbluex.liquidbounce.event.events.RotationUpdateEvent
import net.ccbluex.liquidbounce.event.events.SprintEvent
import net.ccbluex.liquidbounce.event.events.WorldRenderEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.features.global.GlobalVapeRotationSettings
import net.ccbluex.liquidbounce.features.module.modules.combat.ModuleAutoWeapon
import net.ccbluex.liquidbounce.features.module.modules.combat.ModuleRikkaKAHelper
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
import net.ccbluex.liquidbounce.utils.aiming.RotationTarget
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.aiming.data.RotationWithVector
import net.ccbluex.liquidbounce.utils.aiming.features.processors.RotationProcessor
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
import net.ccbluex.liquidbounce.utils.combat.Targets
import net.ccbluex.liquidbounce.utils.item.attackDamage
import net.ccbluex.liquidbounce.utils.raytracing.findEntityInCrosshair
import net.ccbluex.liquidbounce.utils.raytracing.isLookingAtEntity
import net.ccbluex.liquidbounce.utils.render.TargetRenderer
import net.ccbluex.liquidbounce.utils.math.getNearestPoint
import net.minecraft.client.gui.screens.inventory.ContainerScreen
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * KillAura module
 *
 * Automatically attacks enemies.
 */
@Suppress("MagicNumber")
object ModuleKillAura : ClientModule("KillAura", ModuleCategories.COMBAT) {

    private val modes = choices("Mode", LiquidBounce, arrayOf(LiquidBounce, Vape, Silent)).apply(::tagBy)

    internal val isLiquidBounceMode: Boolean
        get() = modes.activeMode === LiquidBounce

    private object LiquidBounce : Mode("LiquidBounce") {
        override val parent: ModeValueGroup<Mode>
            get() = modes

        override fun enable() = resetAlternativeState()
    }

    private object Vape : Mode("Vape") {
        override val parent: ModeValueGroup<Mode>
            get() = modes

        init {
            flattenOptions()
        }

        override fun enable() = resetAlternativeState()

        val attackRate by intRange("AttacksPerSecond", 6..13, 1..20, "attacks")
        val swingRangeValue = float("SwingRange", 4f, 0f..6f, "blocks")
        val swingRange by swingRangeValue
        val attackRangeValue = float("AttackRange", 3.5f, 0f..6f, "blocks")
        val attackRange by attackRangeValue
        val maxAngle by float("MaxAngle", 90f, 1f..360f, "degrees")
        val maxTargets by int("MaxTargets", 1, 1..6)
        val targetMode by enumChoice("TargetMode", VapeTargetMode.DISTANCE)
        val disableOnDeath by boolean("DisableOnDeath", false)
        val requireMouseDown by boolean("RequireMouseDown", false)
        val guiCheck by boolean("GuiCheck", true)
        val targets by multiEnumChoice<Targets>("Targets", Targets.PLAYERS)
        val ignoreNaked by boolean("IgnoreNaked", false)
        val ignoreInvisible by boolean("IgnoreInvisible", false)
        val ignoreBehindWalls by boolean("IgnoreBehindWalls", false)
        val limitToItems by boolean("LimitToItems", false)
        val allowedItems by items("AllowedItems", itemSortedSetOf())
            .visibleWhen { limitToItems }
    }

    private object Silent : Mode("Silent") {
        override val parent: ModeValueGroup<Mode>
            get() = modes

        init {
            flattenOptions()
        }

        override fun enable() = resetAlternativeState()

        val aimSpeed by float("AimSpeed", 7f, 1f..10f)
        val cooldown by boolean("Cooldown", true)
        val attackRate by intRange("AttacksPerSecond", 6..13, 1..20, "attacks")
            .visibleWhen { !cooldown }
        val extraSwingDistance by float("ExtraSwingDistance", 1f, 0f..3f, "blocks")
        val maxAngle by float("MaxAngle", 120f, 1f..360f, "degrees")
        val targetMode by enumChoice("TargetMode", VapeTargetMode.DISTANCE)
        val targetArea by enumChoice("TargetArea", SilentTargetArea.CENTER)
        val switchTargets by boolean("Switch", false)
        val disableOnDeath by boolean("DisableOnDeath", false)
        val breakBlocks by boolean("BreakBlocks", false)
        val breakBlocksDelay by intRange("BreakBlocksDelay", 0..10, 0..2000, "ms")
            .visibleWhen { breakBlocks }
        val breakBlocksWhitelist by boolean("BreakBlocksWhitelist", false)
            .visibleWhen { breakBlocks }
        val blockBreakItems by items("BlockBreakingItems", defaultBlockBreakItems())
            .visibleWhen { breakBlocks && breakBlocksWhitelist }
        val requireMouseDown by boolean("RequireMouseDown", false)
        val targets by multiEnumChoice<Targets>("Targets", Targets.PLAYERS)
        val ignoreNaked by boolean("IgnoreNaked", false)
        val ignoreInvisible by boolean("IgnoreInvisible", false)
        val ignoreBehindWalls by boolean("IgnoreBehindWalls", false)
        val limitToItems by boolean("LimitToItems", false)
        val allowedItems by items("AllowedItems", itemSortedSetOf())
            .visibleWhen { limitToItems }
    }

    // Attack speed
    val clicker = tree(KillAuraClicker).also { it.visibleWhen { isLiquidBounceMode } }
    val range = tree(KillAuraRange).also { it.visibleWhen { isLiquidBounceMode } }
    val targetTracker = tree(KillAuraTargetTracker).also {
        it.visibleWhen { isLiquidBounceMode }
    }

    // Rotation
    private val rotations = tree(KillAuraRotationsValueGroup).also {
        it.visibleWhen { isLiquidBounceMode }
    }
    private val pointTracker = tree(PointTracker(this)).also {
        it.visibleWhen { isLiquidBounceMode }
    }

    private val requires by multiEnumChoice<KillAuraRequirements>("Requires")
        .visibleWhen { isLiquidBounceMode }

    private val requirementsMet
        get() = requires.all { it.asBoolean }

    // Bypass techniques
    internal val raycast by enumChoice("Raycast", TRACE_ALL)
        .visibleWhen { isLiquidBounceMode }
    private val criticalsSelectionMode by enumChoice("Criticals", CriticalsSelectionMode.SMART)
        .visibleWhen { isLiquidBounceMode }
    private val keepSprint by boolean("KeepSprint", true)
        .visibleWhen { isLiquidBounceMode }

    // Inventory Handling
    internal val ignoreOpenInventory by boolean("IgnoreOpenInventory", true)
        .visibleWhen { isLiquidBounceMode }
    internal val simulateInventoryClosing by boolean("SimulateInventoryClosing", true)
        .visibleWhen { isLiquidBounceMode }

    /**
     * The use of suspend [waitTicks] is a bit too
     * risky for a large and complex module
     * such as KillAura. So back to the basics.
     */
    internal var waitTicks = 0
    private var vapeNextAttackAt = 0L
    private val vapeClickDelay = VapeClickDelay()
    private var vapePauseTicks = 0
    private var silentNextAttackAt = 0L
    private var silentBreakAllowedAt = 0L
    private var silentTargetId: Int? = null
    private var silentAimRotation: Rotation? = null
    private val silentPitchJitter = SilentAimJitter(-0.3, 0.25)
    private val silentXJitter = SilentAimJitter(-0.15, 0.15)
    private val silentZJitter = SilentAimJitter(-0.15, 0.15)

    init {
        Vape.swingRangeValue.onChange { newRange ->
            newRange.coerceAtLeast(Vape.attackRange + RANGE_INCREMENT)
        }
        Vape.attackRangeValue.onChange { newRange ->
            newRange.coerceAtMost(Vape.swingRange - RANGE_INCREMENT)
        }
        tree(KillAuraAutoBlock).visibleWhen { isLiquidBounceMode }
        tree(TargetRenderer(this) {
            targetTracker.target?.takeUnless { ModuleElytraTarget.isSameTargetRendering(it) }
        })
        tree(KillAuraFailSwing).visibleWhen { isLiquidBounceMode }
        tree(KillAuraFightBot).visibleWhen { isLiquidBounceMode }
        tree(KillAuraRangeIndicator).visibleWhen { isLiquidBounceMode }
    }

    private fun resetAlternativeState() {
        targetTracker.reset()
        vapeNextAttackAt = 0L
        vapeClickDelay.reset()
        vapePauseTicks = 0
        silentNextAttackAt = 0L
        silentBreakAllowedAt = 0L
        silentTargetId = null
        silentAimRotation = null
        SilentRotationProcessor.reset()
        waitTicks = 0
    }

    override fun onDisabled() {
        resetAlternativeState()
        failedHits.clear()
        KillAuraNotifyWhenFail.failedHitsIncrement = 0
    }

    @Suppress("unused")
    private val renderHandler = handler<WorldRenderEvent> { event ->
        if (!isLiquidBounceMode) {
            return@handler
        }

        event.renderEnvironment {
            renderFailedHits()
            KillAuraRangeIndicator.render(this, event.partialTicks)
        }
    }

    @Suppress("unused")
    private val rotationUpdateHandler = handler<RotationUpdateEvent> {
        if (modes.activeMode === Silent) {
            updateSilentTargetAndRotation()
            return@handler
        }

        if (!isLiquidBounceMode) {
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
        when (modes.activeMode) {
            Vape -> {
                runVapeTick()
                return@tickHandler
            }
            Silent -> {
                runSilentTick()
                return@tickHandler
            }
            else -> Unit
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
        if (!isLiquidBounceMode) {
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
        ModuleRikkaKAHelper.killAuraTarget?.let { helperTarget ->
            targetTracker.target = helperTarget
            return
        }

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

        if (Vape.guiCheck && mc.gui.screen() != null) {
            vapePauseTicks = 1
            targetTracker.reset()
            return
        }

        if (Vape.disableOnDeath && (player.isDeadOrDying || player.health <= 0f)) {
            enabled = false
            return
        }

        if (player.isDeadOrDying || player.isSpectator ||
            Vape.requireMouseDown && !mc.options.keyAttack.isPressedOnAny ||
            Vape.limitToItems && player.mainHandItem.item !in Vape.allowedItems
        ) {
            targetTracker.reset()
            return
        }

        val targets = ModuleRikkaKAHelper.killAuraTarget?.let(::listOf) ?: world.entitiesForRendering()
            .asSequence()
            .filterIsInstance<LivingEntity>()
            .filter { isValidVapeTarget(it, Vape.targets, Vape.ignoreNaked,
                Vape.ignoreInvisible, Vape.ignoreBehindWalls) }
            .filter { player.distanceTo(it) <= Vape.swingRange }
            .filter { RotationUtil.crosshairAngleToEntity(it) <= Vape.maxAngle / 2f }
            .sortedWith(vapeTargetComparator(Vape.targetMode))
            .take(Vape.maxTargets)
            .toList()

        targetTracker.target = targets.firstOrNull()
        if (targets.isEmpty() || System.currentTimeMillis() < vapeNextAttackAt) {
            return
        }

        var swung = false
        targets.forEach { target ->
            if (player.distanceTo(target) <= Vape.attackRange) {
                attackEntity(target, SwingMode.DO_NOT_HIDE)
            } else if (!swung) {
                SwingMode.DO_NOT_HIDE.swing(InteractionHand.MAIN_HAND)
            }
            swung = true
        }

        vapeNextAttackAt = System.currentTimeMillis() + vapeClickDelay.nextDelay(Vape.attackRate)
    }

    private fun vapeTargetComparator(targetMode: VapeTargetMode): Comparator<LivingEntity> = when (targetMode) {
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

    private fun isValidVapeTarget(
        entity: LivingEntity,
        targets: Set<Targets>,
        ignoreNaked: Boolean,
        ignoreInvisible: Boolean,
        ignoreBehindWalls: Boolean,
    ): Boolean {
        if (entity === player || entity.isRemoved || !entity.isAlive || !entity.shouldBeAttacked(targets)) return false
        if (ignoreNaked && entity is Player && entity.armorValue == 0) return false
        if (ignoreInvisible && entity.isInvisible) return false
        return !ignoreBehindWalls || player.hasLineOfSight(entity)
    }

    private fun shouldPauseSilentForBlockBreaking(): Boolean {
        if (!Silent.breakBlocks) return false

        val itemAllowed = !Silent.breakBlocksWhitelist || player.mainHandItem.item in Silent.blockBreakItems
        val breakingBlock = itemAllowed && mc.options.keyAttack.isPressedOnAny &&
            mc.hitResult?.type == HitResult.Type.BLOCK

        if (breakingBlock) {
            silentBreakAllowedAt = System.currentTimeMillis() +
                Random.nextInt(Silent.breakBlocksDelay.first, Silent.breakBlocksDelay.last + 1)
            return true
        }

        return System.currentTimeMillis() < silentBreakAllowedAt
    }

    private fun canRunSilent(): Boolean {
        if (Silent.disableOnDeath && (player.isDeadOrDying || player.health <= 0f)) {
            enabled = false
            return false
        }

        return !player.isDeadOrDying && !player.isSpectator && mc.gui.screen() == null &&
            (!Silent.requireMouseDown || mc.options.keyAttack.isPressedOnAny) &&
            (!Silent.limitToItems || player.mainHandItem.item in Silent.allowedItems) &&
            !shouldPauseSilentForBlockBreaking()
    }

    private fun silentInteractionRange(): Double = GlobalVapeRotationSettings.interactionRange(
        SILENT_BASE_RANGE + Silent.extraSwingDistance.toDouble()
    )

    private fun silentAimPoint(target: LivingEntity): Vec3 {
        silentPitchJitter.update()
        silentXJitter.update()
        silentZJitter.update()

        val box = target.boundingBox.inflate(GlobalVapeRotationSettings.hitboxExpansion())
        val base = when (Silent.targetArea) {
            SilentTargetArea.CENTER -> box.center
            SilentTargetArea.CLOSEST -> box.getNearestPoint(player.eyePosition)
        }
        val motionScale = 1.0 + target.deltaMovement.horizontalDistance()
        val targetY = if (player.eyeY < box.minY) {
            box.minY + silentPitchJitter.current * 0.5
        } else {
            min(player.eyeY, box.maxY) - SILENT_VERTICAL_OFFSET + silentPitchJitter.current
        }

        return Vec3(
            base.x + silentXJitter.current * motionScale,
            targetY.coerceIn(box.minY, box.maxY),
            base.z + silentZJitter.current * motionScale,
        )
    }

    private fun updateSilentTargetAndRotation() {
        if (!canRunSilent()) {
            targetTracker.reset()
            silentAimRotation = null
            return
        }

        val interactionRange = silentInteractionRange()
        val baseComparator = vapeTargetComparator(Silent.targetMode)
        val comparator = if (Silent.switchTargets) {
            compareBy<LivingEntity> { it.hurtTime }.then(baseComparator)
        } else {
            baseComparator
        }

        val target = ModuleRikkaKAHelper.killAuraTarget ?: world.entitiesForRendering().asSequence()
            .filterIsInstance<LivingEntity>()
            .filter { isValidVapeTarget(it, Silent.targets, Silent.ignoreNaked,
                Silent.ignoreInvisible, Silent.ignoreBehindWalls) }
            .filter { player.eyePosition.distanceTo(it.boundingBox.getNearestPoint(player.eyePosition)) <= interactionRange }
            .filter { RotationUtil.crosshairAngleToEntity(it) <= Silent.maxAngle / 2f }
            .sortedWith(comparator)
            .firstOrNull()

        if (target == null) {
            targetTracker.reset()
            silentTargetId = null
            silentAimRotation = null
            SilentRotationProcessor.reset()
            return
        }

        if (silentTargetId != target.id) {
            SilentRotationProcessor.randomizeGains()
            silentTargetId = target.id
        }

        targetTracker.target = target
        val aimPoint = silentAimPoint(target)
        val rotation = Rotation.lookingAt(aimPoint, player.eyePosition)
        silentAimRotation = rotation

        // SilentAura owns its complete rotation plan; LB rotation settings must not leak into it.
        RotationManager.setRotationTarget(
            RotationTarget(
                rotation = rotation,
                entity = target,
                processors = listOf(SilentRotationProcessor),
                ticksUntilReset = 2,
                resetThreshold = 2f,
                considerInventory = true,
                movementCorrection = GlobalVapeRotationSettings.movementCorrection,
                vapeCompatible = true,
            ),
            priority = Priority.IMPORTANT_FOR_USAGE_2,
            provider = ModuleKillAura,
        )
    }

    private fun runSilentTick() {
        if (!canRunSilent()) {
            targetTracker.reset()
            return
        }

        val target = targetTracker.target ?: return
        val targetRotation = silentAimRotation ?: return
        val managedRotation = RotationManager.currentRotation ?: return
        val inRange = player.eyePosition.distanceTo(
            target.boundingBox.inflate(GlobalVapeRotationSettings.hitboxExpansion())
                .getNearestPoint(player.eyePosition)
        ) <= silentInteractionRange()

        // Vape only lets its hidden clicker fire once silent aim is within roughly three degrees.
        if (!inRange || managedRotation.directionAngleTo(targetRotation) >= SILENT_READY_ANGLE ||
            Silent.cooldown && player.getAttackStrengthScale(0f) < 1f ||
            !Silent.cooldown && System.currentTimeMillis() < silentNextAttackAt
        ) {
            return
        }

        attackEntity(target, SwingMode.DO_NOT_HIDE)
        if (!Silent.cooldown) {
            val cps = Random.nextInt(Silent.attackRate.first, Silent.attackRate.last + 1)
            silentNextAttackAt = System.currentTimeMillis() + 1000L / cps
        }
    }

    private object SilentRotationProcessor : RotationProcessor {
        private var pitchIntegral = 0f
        private var yawIntegral = 0f
        private var pitchScale = 1f
        private var pitchIntegralScale = 1f
        private var yawScale = 1f
        private var yawIntegralScale = 1f
        private var previousRotation: Rotation? = null

        fun randomizeGains() {
            pitchScale = Random.nextDouble(0.85, 1.15).toFloat()
            pitchIntegralScale = Random.nextDouble(0.85, 1.15).toFloat()
            yawScale = Random.nextDouble(0.8, 1.2).toFloat()
            yawIntegralScale = Random.nextDouble(0.85, 1.15).toFloat()
            pitchIntegral = 0f
            yawIntegral = 0f
        }

        fun reset() {
            pitchIntegral = 0f
            yawIntegral = 0f
            previousRotation = null
        }

        override fun process(
            rotationTarget: RotationTarget,
            currentRotation: Rotation,
            targetRotation: Rotation,
        ): Rotation {
            val previous = previousRotation ?: currentRotation
            var yawError = RotationUtil.angleDifference(targetRotation.yaw, currentRotation.yaw)
            var pitchError = RotationUtil.angleDifference(targetRotation.pitch, currentRotation.pitch)
            val previousYawStep = RotationUtil.angleDifference(currentRotation.yaw, previous.yaw)
            val previousPitchStep = RotationUtil.angleDifference(currentRotation.pitch, previous.pitch)
            val sameYawDirection = sign(yawError) == sign(previousYawStep)

            var yawProportionalGain = 0.1f * yawScale
            if (sameYawDirection && abs(yawError) < 20f) {
                yawProportionalGain *= 2.5f
            }
            if (player.distanceTo(targetTracker.target ?: player) < 0.8f) {
                val distanceScale = player.distanceTo(targetTracker.target ?: player) / 0.8f
                pitchError *= distanceScale * distanceScale
                yawError *= distanceScale
            }

            val pitchControlError = pitchError - previousPitchStep
            val yawControlError = yawError - previousYawStep
            pitchIntegral += pitchControlError * CONTROL_INTEGRATION_STEP
            yawIntegral += yawControlError * CONTROL_INTEGRATION_STEP

            val pitchAdjustment = 0.45f * pitchScale * pitchControlError +
                0.91f * pitchIntegralScale * pitchIntegral
            var yawAdjustment = yawProportionalGain * yawControlError +
                0.33f * yawIntegralScale * yawIntegral
            if (abs(yawError) > 120f) {
                yawIntegral = 0f
                yawAdjustment = 0f
            }

            val controlled = Rotation(
                targetRotation.yaw + yawAdjustment / 3f,
                (targetRotation.pitch + pitchAdjustment).coerceIn(-90f, 90f),
            )
            val gaussian = sqrt(-2.0 * ln(Random.nextDouble().coerceAtLeast(0.0001))) *
                cos(Math.PI * 2.0 * Random.nextDouble())
            val multiplier = exp(0.65 + 0.25 * gaussian).coerceIn(1.4, 3.0)
            val maxStep = (Silent.aimSpeed * multiplier * VAPE_ROTATION_STEP_SCALE).toFloat()
            val result = currentRotation.towardsLinear(controlled, maxStep, maxStep)
            previousRotation = result
            return result
        }
    }

    /** Exact burst and delay-spike distribution from Vape's RandomClickDelayValue. */
    private class VapeClickDelay {
        private var burstActive = false
        private var burstLength = 0
        private var burstProgress = 0

        fun reset() {
            burstActive = false
            burstLength = 0
            burstProgress = 0
        }

        fun nextDelay(cpsRange: IntRange): Long {
            val cps = Random.nextInt(cpsRange.first, cpsRange.last + 1).coerceAtLeast(1)
            var delay = 1000L / cps
            if (!burstActive) {
                when {
                    Random.nextInt(4) == 1 -> {
                        burstActive = true
                        burstLength = 1 + Random.nextInt(5)
                    }
                    Random.nextInt(10) != 1 && Random.nextInt(10) == 1 -> {
                        burstActive = true
                        burstLength = 5 + Random.nextInt(10)
                    }
                }
            }
            if (burstActive && ++burstProgress >= burstLength) {
                burstProgress = 0
                burstActive = false
            }
            if (Random.nextInt(48) % 10 == 0 && !burstActive) {
                delay += Random.nextLong(25L, 70L)
            }
            return delay
        }
    }

    private class SilentAimJitter(private val min: Double, private val max: Double) {
        var current = 0.0
            private set
        private var target = 0.0
        private var nextRetargetAt = 0L

        fun update() {
            val now = System.currentTimeMillis()
            if (now >= nextRetargetAt) {
                target = Random.nextDouble(min, max)
                nextRetargetAt = now + Random.nextLong(100L, 1000L)
            }
            val step = 0.01 + Random.nextDouble(0.0, 0.05)
            current = when {
                target > current -> minOf(target, current + step)
                target < current -> maxOf(target, current - step)
                else -> current
            }
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

    private enum class VapeTargetMode(override val tag: String) : Tagged {
        DISTANCE("Distance"),
        YAW("Yaw"),
        ARMOR("Armor"),
        THREAT("Threat"),
        HEALTH("Health"),
    }

    private enum class SilentTargetArea(override val tag: String) : Tagged {
        CENTER("Center"),
        CLOSEST("Closest"),
    }

    private fun defaultBlockBreakItems() = itemSortedSetOf(
        Items.WOODEN_PICKAXE, Items.STONE_PICKAXE, Items.IRON_PICKAXE,
        Items.GOLDEN_PICKAXE, Items.DIAMOND_PICKAXE, Items.NETHERITE_PICKAXE,
        Items.WOODEN_SHOVEL, Items.STONE_SHOVEL, Items.IRON_SHOVEL,
        Items.GOLDEN_SHOVEL, Items.DIAMOND_SHOVEL, Items.NETHERITE_SHOVEL,
    )

    private const val RANGE_INCREMENT = 0.1f
    private const val SILENT_BASE_RANGE = 3.0
    private const val SILENT_READY_ANGLE = 3f
    private const val SILENT_VERTICAL_OFFSET = 0.275
    private const val CONTROL_INTEGRATION_STEP = 0.05f
    private const val VAPE_ROTATION_STEP_SCALE = 1.875

}
