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

import net.ccbluex.liquidbounce.config.types.group.Mode
import net.ccbluex.liquidbounce.config.types.group.ModeValueGroup
import net.ccbluex.liquidbounce.config.types.list.Tagged
import net.ccbluex.liquidbounce.features.module.modules.combat.ModuleRikkaKAHelper
import net.ccbluex.liquidbounce.features.module.modules.combat.killaura.features.KillAuraAutoBlock
import net.ccbluex.liquidbounce.utils.block.SwingMode
import net.ccbluex.liquidbounce.utils.collection.itemSortedSetOf
import net.ccbluex.liquidbounce.utils.combat.attackEntity
import net.ccbluex.liquidbounce.utils.entity.armorItems
import net.ccbluex.liquidbounce.utils.input.InputTracker.isPressedOnAny
import net.ccbluex.liquidbounce.utils.item.armorValue
import net.ccbluex.liquidbounce.utils.item.attackDamage
import net.ccbluex.liquidbounce.utils.item.getEnchantment
import net.minecraft.world.InteractionHand
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.enchantment.Enchantments
import kotlin.math.abs
import kotlin.math.atan
import kotlin.random.Random

internal const val RANGE_INCREMENT = 0.1f

/**
 * PurePacket KillAura mode. Settings and tick logic are isolated here so upstream
 * KillAura changes do not collide with the Vape-compatible implementation.
 */
@Suppress("MagicNumber")
internal object Vape : Mode("PurePacket") {
    override val parent: ModeValueGroup<Mode>
        get() = ModuleKillAura.modes

    init {
        flattenOptions()
    }

    override fun enable() = ModuleKillAura.resetAlternativeState()

    val cooldown by boolean("Cooldown", false)
    val attackRate by intRange("AttacksPerSecond", 6..13, 1..20, "attacks")
        .visibleWhen { !cooldown }
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
    val ignoreNaked by boolean("IgnoreNaked", false)
    val ignoreInvisible by boolean("IgnoreInvisible", false)
    val ignoreBehindWalls by boolean("IgnoreBehindWalls", false)
    val limitToItems by boolean("LimitToItems", false)
    val allowedItems by items("AllowedItems", itemSortedSetOf())
        .visibleWhen { limitToItems }
}

internal enum class VapeTargetMode(override val tag: String) : Tagged {
    DISTANCE("Distance"),
    YAW("Yaw"),
    ARMOR("Armor"),
    THREAT("Threat"),
    HEALTH("Health"),
}

internal object KillAuraVapeState {
    var lastClickAt = 0L
    val clickDelay = VapeClickDelay()
    var pauseTicks = 0

    fun reset() {
        lastClickAt = 0L
        clickDelay.reset()
        pauseTicks = 0
    }
}

@Suppress("CognitiveComplexMethod", "ComplexCondition", "ReturnCount")
internal fun ModuleKillAura.runVapeTick() {
    if (KillAuraAutoBlock.blockVisual) {
        KillAuraAutoBlock.stopBlocking()
    }

    if (KillAuraVapeState.pauseTicks > 0) {
        KillAuraVapeState.pauseTicks--
        return
    }

    if (Vape.guiCheck && mc.gui.screen() != null) {
        KillAuraVapeState.pauseTicks = 1
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

    // All modes share LB's target selector for target types, FOV, priority and anti-bot filtering.
    val targets = ModuleRikkaKAHelper.killAuraTarget?.let(::listOf) ?: targetTracker.targets()
        .asSequence()
        .filter {
            isValidVapeTarget(it, Vape.ignoreNaked, Vape.ignoreInvisible, Vape.ignoreBehindWalls)
        }
        .filter { player.distanceTo(it) <= Vape.attackRange }
        .filter { vapeYawAngle(it) <= Vape.maxAngle.toInt() / 2 }
        .sortedWith(vapeTargetComparator(Vape.targetMode))
        .take(Vape.maxTargets)
        .toList()

    targetTracker.target = targets.firstOrNull()
    val clickReady = Vape.cooldown || System.currentTimeMillis() - KillAuraVapeState.lastClickAt >=
        KillAuraVapeState.clickDelay.calculateNextDelayMillis(Vape.attackRate)
    if (targets.isEmpty() || !clickReady || Vape.cooldown && player.getAttackStrengthScale(0f) < 1f) {
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

    if (!Vape.cooldown) {
        KillAuraVapeState.lastClickAt = System.currentTimeMillis()
    }
}

/**
 * Vape's target mode comparators. Armor/Threat fall back to distance for non-player targets.
 *
 * @see gg.vape.utils.EntityDistanceComparator
 * @see gg.vape.utils.EntityAngleComparator
 * @see gg.vape.utils.EntityArmorValueComparator
 * @see gg.vape.utils.EntityEquipmentValueComparator
 * @see gg.vape.utils.EntityHealthComparator
 */
internal fun ModuleKillAura.vapeTargetComparator(targetMode: VapeTargetMode): Comparator<LivingEntity> =
    when (targetMode) {
    VapeTargetMode.DISTANCE -> compareBy { player.distanceTo(it) }
    VapeTargetMode.YAW -> compareBy { vapeYawAngle(it) }
    VapeTargetMode.HEALTH -> compareBy { it.health }
    VapeTargetMode.THREAT -> Comparator { first, second ->
        if (first is Player && second is Player) {
            vapeThreatValue(first).compareTo(vapeThreatValue(second))
        } else {
            player.distanceTo(first).compareTo(player.distanceTo(second))
        }
    }
    VapeTargetMode.ARMOR -> Comparator { first, second ->
        if (first is Player && second is Player) {
            vapeEquipmentValue(first).compareTo(vapeEquipmentValue(second))
        } else {
            player.distanceTo(first).compareTo(player.distanceTo(second))
        }
    }
}

/**
 * Vape's horizontal yaw angle between the player's facing direction and the entity.
 *
 * @see gg.vape.utils.RotationUtil.a
 */
internal fun ModuleKillAura.vapeYawAngle(entity: Entity): Int {
    val dx = entity.x - player.x
    val dz = entity.z - player.z
    var direction = 0.0
    when {
        dz > 0.0 && dx > 0.0 -> direction = Math.toDegrees(-atan(dx / dz))
        dz > 0.0 && dx < 0.0 -> direction = Math.toDegrees(-atan(dx / dz))
        dz < 0.0 && dx > 0.0 -> direction = -90.0 + Math.toDegrees(atan(dz / dx))
        dz < 0.0 && dx < 0.0 -> direction = 90.0 + Math.toDegrees(atan(dz / dx))
    }
    val difference = (abs(direction - player.yRot.toDouble()) % 360.0).toInt()
    return if (difference > 180) 360 - difference else difference
}

/**
 * Vape's held-item threat score with the Resistance multiplier.
 *
 * @see gg.vape.utils.EntityArmorValueComparator.calculateArmorValue
 */
internal fun ModuleKillAura.vapeThreatValue(target: Player): Float {
    var value = target.mainHandItem.attackDamage.toFloat()
    val resistance = target.getEffect(MobEffects.RESISTANCE)
    if (resistance != null && resistance.duration > 0) {
        value *= 1.375f * resistance.amplifier
    }
    return value
}

/**
 * Vape's equipment score: armor pieces plus the small protection enchantment bonuses.
 *
 * @see gg.vape.utils.EntityEquipmentValueComparator.calculateEquipmentValue
 */
internal fun ModuleKillAura.vapeEquipmentValue(target: Player): Double {
    var value = 0.0
    for (stack in target.armorItems) {
        if (stack.isEmpty) continue
        value += stack.armorValue
        value += stack.getEnchantment(Enchantments.FEATHER_FALLING) * 0.1
        value += stack.getEnchantment(Enchantments.FIRE_PROTECTION) * 0.1
        value += stack.getEnchantment(Enchantments.BLAST_PROTECTION) * 0.1
    }
    return value
}

internal fun ModuleKillAura.isValidVapeTarget(
    entity: LivingEntity,
    ignoreNaked: Boolean,
    ignoreInvisible: Boolean,
    ignoreBehindWalls: Boolean,
): Boolean {
    if (entity === player || entity.isRemoved || !entity.isAlive) return false
    if (ignoreNaked && entity is Player && entity.armorValue == 0) return false
    if (ignoreInvisible && entity.isInvisible) return false
    return !ignoreBehindWalls || player.hasLineOfSight(entity)
}

/** Exact burst and delay-spike distribution from Vape's RandomClickDelayValue. */
@Suppress("MagicNumber")
internal class VapeClickDelay {
    private var burstActive = false
    private var burstLength = 0
    private var burstProgress = 0

    fun reset() {
        burstActive = false
        burstLength = 0
        burstProgress = 0
    }

    /**
     * Vape re-computes the delay on every tick via hasClickDelayElapsed(), so the burst state
     * advances per tick rather than per click.
     *
     * @see gg.vape.value.RandomClickDelayValue.calculateNextDelayMillis
     */
    fun calculateNextDelayMillis(cpsRange: IntRange): Long {
        // Vape always uses the maximum CPS from the range; its random pick is dead code.
        val cps = cpsRange.last.coerceAtLeast(1)
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
            delay += Random.nextInt(45) + 25
        }
        return delay
    }
}
