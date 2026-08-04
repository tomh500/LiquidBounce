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
package net.ccbluex.liquidbounce.features.module.modules.player

import net.ccbluex.liquidbounce.config.types.list.Tagged
import net.ccbluex.liquidbounce.event.events.GameTickEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.features.module.ModuleOrigin
import net.ccbluex.liquidbounce.utils.aiming.utils.RotationUtil
import net.ccbluex.liquidbounce.utils.combat.shouldBeAttacked
import net.ccbluex.liquidbounce.utils.kotlin.random
import net.ccbluex.liquidbounce.utils.range.RangeValueGroup
import net.minecraft.world.item.component.AttackRange
import kotlin.math.abs
import kotlin.random.Random

/** Extends entity interaction range using LiquidBounce or Vape behavior. */
object ModuleReach : ClientModule(
    "Reach",
    ModuleCategories.PLAYER,
    origin = ModuleOrigin.LIQUID_BOUNCE_MODIFIED,
) {

    private val mode by enumChoice("Mode", ReachMode.LIQUID_BOUNCE)
        .onChanged { resetVapeState() }

    val entity = tree(RangeValueGroup("Entity", 1f, 0f)).also {
        it.visibleWhen { mode == ReachMode.LIQUID_BOUNCE }
    }
    val blockRangeIncrease by float("BlockRangeIncrease", 0.5f, 0f..64f)
        .visibleWhen { mode == ReachMode.LIQUID_BOUNCE }

    private val vapeRange by floatRange("Range", 3f..3.1f, 3f..4f, "blocks")
        .visibleWhen { mode == ReachMode.VAPE }
    private val vapeChance by float("Chance", 50f, 0f..100f, "%")
        .visibleWhen { mode == ReachMode.VAPE }
    private val vapeChanceMode by enumChoice("ChanceMode", VapeChanceMode.ADVANCED)
        .visibleWhen { mode == ReachMode.VAPE }
    private val vapeVerticalCheck by boolean("VerticalCheck", false)
        .visibleWhen { mode == ReachMode.VAPE }
    private val vapeOnlyWhileSprinting by boolean("OnlyWhileSprinting", false)
        .visibleWhen { mode == ReachMode.VAPE }
    private val vapeDisableInWater by boolean("DisableInWater", false)
        .visibleWhen { mode == ReachMode.VAPE }

    private var vapeCurrentRange = VANILLA_RANGE
    private var vapeReachActive = false
    private var vapeHitCooldown = 0
    private var vapeLastTargetId: Int? = null

    val currentEntityRange: Float
        get() = when (mode) {
            ReachMode.LIQUID_BOUNCE -> entity.interactionRange
            ReachMode.VAPE -> vapeCurrentRange
        }

    val currentThroughWallsRange: Float
        get() = if (mode == ReachMode.LIQUID_BOUNCE) entity.interactionThroughWallsRange else 0f

    val currentBlockRangeIncrease: Float
        get() = if (mode == ReachMode.LIQUID_BOUNCE) blockRangeIncrease else 0f

    fun adjustAttackRange(attackRange: AttackRange): AttackRange = when (mode) {
        ReachMode.LIQUID_BOUNCE -> entity.adjustAttackRange(attackRange)
        ReachMode.VAPE -> attackRange.withMaxReach(currentEntityRange)
    }

    private fun AttackRange.withMaxReach(reach: Float) = AttackRange(
        minReach,
        reach,
        minCreativeReach,
        maxOf(maxCreativeReach, reach),
        hitboxMargin,
        mobFactor,
    )

    private fun canUseVapeReach(): Boolean {
        if (vapeDisableInWater && (player.isInWater || player.isUnderWater)) return false
        return !vapeOnlyWhileSprinting || player.isSprinting
    }

    private fun rollRange(): Float = if (Random.nextFloat() * 100f < vapeChance) {
        vapeRange.random()
    } else {
        VANILLA_RANGE
    }

    private fun updateAdvancedReach() {
        if (vapeHitCooldown > 0) vapeHitCooldown--

        val maxRange = vapeRange.endInclusive.toDouble()
        val target = world.entitiesForRendering().asSequence()
            .filter { it !== player && it.shouldBeAttacked() }
            .filter { player.distanceTo(it) <= maxRange }
            .filter { !vapeVerticalCheck || abs(it.y - player.y) <= VERTICAL_TOLERANCE }
            .minByOrNull { RotationUtil.crosshairAngleToEntity(it) }
            ?.takeIf { RotationUtil.crosshairAngleToEntity(it) <= TARGET_ANGLE }

        if (target == null || vapeLastTargetId != null && target.id != vapeLastTargetId) {
            vapeLastTargetId = target?.id
            vapeReachActive = false
            vapeCurrentRange = VANILLA_RANGE
            return
        }

        if (vapeHitCooldown == 0 && canUseVapeReach()) {
            vapeReachActive = Random.nextFloat() * 100f < vapeChance
            if (vapeReachActive) vapeHitCooldown = ADVANCED_COOLDOWN_TICKS
        }

        vapeLastTargetId = target.id
        vapeCurrentRange = if (vapeReachActive) vapeRange.random() else VANILLA_RANGE
    }

    @Suppress("unused")
    private val tickHandler = handler<GameTickEvent> {
        if (mode != ReachMode.VAPE || !canUseVapeReach()) {
            vapeCurrentRange = VANILLA_RANGE
            vapeReachActive = false
            return@handler
        }

        if (vapeChanceMode == VapeChanceMode.ADVANCED) {
            updateAdvancedReach()
        } else {
            vapeCurrentRange = rollRange()
        }
    }

    override fun onDisabled() {
        resetVapeState()
        super.onDisabled()
    }

    private fun resetVapeState() {
        vapeCurrentRange = VANILLA_RANGE
        vapeReachActive = false
        vapeHitCooldown = 0
        vapeLastTargetId = null
    }

    private enum class ReachMode(override val tag: String) : Tagged {
        LIQUID_BOUNCE("LiquidBounce"),
        VAPE("Vape"),
    }

    private enum class VapeChanceMode(override val tag: String) : Tagged {
        ADVANCED("Advanced"),
        NORMAL("Normal"),
    }

    private const val VANILLA_RANGE = 3f
    private const val VERTICAL_TOLERANCE = 0.2
    private const val TARGET_ANGLE = 8f
    private const val ADVANCED_COOLDOWN_TICKS = 10
}
