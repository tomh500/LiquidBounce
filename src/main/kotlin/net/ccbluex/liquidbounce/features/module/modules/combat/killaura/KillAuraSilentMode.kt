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
@file:Suppress("MagicNumber", "TooManyFunctions")

package net.ccbluex.liquidbounce.features.module.modules.combat.killaura

import net.ccbluex.liquidbounce.config.types.group.Mode
import net.ccbluex.liquidbounce.config.types.group.ModeValueGroup
import net.ccbluex.liquidbounce.config.types.list.Tagged
import net.ccbluex.liquidbounce.features.global.GlobalVapeRotationSettings
import net.ccbluex.liquidbounce.features.module.modules.combat.ModuleRikkaKAHelper
import net.ccbluex.liquidbounce.features.module.modules.render.ModuleFreeCam
import net.ccbluex.liquidbounce.utils.aiming.RotationManager
import net.ccbluex.liquidbounce.utils.aiming.RotationTarget
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.aiming.features.processors.RotationProcessor
import net.ccbluex.liquidbounce.utils.aiming.utils.RotationUtil
import net.ccbluex.liquidbounce.utils.block.SwingMode
import net.ccbluex.liquidbounce.utils.collection.itemSortedSetOf
import net.ccbluex.liquidbounce.utils.combat.attackEntity
import net.ccbluex.liquidbounce.utils.input.InputTracker.isPressedOnAny
import net.ccbluex.liquidbounce.utils.kotlin.Priority
import net.ccbluex.liquidbounce.utils.math.firstHit
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Items
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

private const val SILENT_BASE_RANGE = 3.0
private const val SILENT_READY_ANGLE = 3f
private const val SILENT_VERTICAL_OFFSET = 0.275
private const val CONTROL_INTEGRATION_STEP = 0.05f
// 50 controller updates per tick x 0.25 step factor x 0.15 degrees per mickey, with Vape's
// mouse-sensitivity compensation cancelling out: 50 * 0.25 * 0.15 = 1.875.
private const val VAPE_ROTATION_STEP_SCALE = 1.875f

/**
 * Vape SilentAura mode. Settings and tick logic are isolated here so upstream
 * KillAura changes do not collide with the Vape-compatible implementation.
 */
internal object Silent : Mode("Silent") {
    override val parent: ModeValueGroup<Mode>
        get() = ModuleKillAura.modes

    init {
        flattenOptions()
    }

    override fun enable() = ModuleKillAura.resetAlternativeState()

    val aimSpeed by float("AimSpeed", 7f, 1f..10f)
    val cooldown by boolean("Cooldown", false)
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
    val ignoreNaked by boolean("IgnoreNaked", false)
    val ignoreInvisible by boolean("IgnoreInvisible", false)
    val ignoreBehindWalls by boolean("IgnoreBehindWalls", false)
    val limitToItems by boolean("LimitToItems", false)
    val allowedItems by items("AllowedItems", itemSortedSetOf())
        .visibleWhen { limitToItems }
}

internal enum class SilentTargetArea(override val tag: String) : Tagged {
    CENTER("Center"),
    CLOSEST("Closest"),
}

internal fun defaultBlockBreakItems() = itemSortedSetOf(
    Items.WOODEN_PICKAXE, Items.STONE_PICKAXE, Items.IRON_PICKAXE,
    Items.GOLDEN_PICKAXE, Items.DIAMOND_PICKAXE, Items.NETHERITE_PICKAXE,
    Items.WOODEN_SHOVEL, Items.STONE_SHOVEL, Items.IRON_SHOVEL,
    Items.GOLDEN_SHOVEL, Items.DIAMOND_SHOVEL, Items.NETHERITE_SHOVEL,
)

internal object KillAuraSilentState {
    var lastClickAt = 0L
    var breakAllowedAt = 0L
    var targetId: Int? = null
    var aimRotation: Rotation? = null
    val pitchJitter = SilentAimJitter(-0.3, 0.25)
    val xJitter = SilentAimJitter(-0.15, 0.15)
    val zJitter = SilentAimJitter(-0.15, 0.15)

    fun reset() {
        lastClickAt = 0L
        breakAllowedAt = 0L
        targetId = null
        aimRotation = null
        SilentRotationProcessor.reset()
    }
}

private fun ModuleKillAura.shouldPauseSilentForBlockBreaking(): Boolean {
    if (!Silent.breakBlocks) return false

    val itemAllowed = !Silent.breakBlocksWhitelist || player.mainHandItem.item in Silent.blockBreakItems
    val breakingBlock = itemAllowed && mc.options.keyAttack.isPressedOnAny &&
        mc.hitResult?.type == HitResult.Type.BLOCK

    if (breakingBlock) {
        KillAuraSilentState.breakAllowedAt = System.currentTimeMillis() +
            Random.nextInt(Silent.breakBlocksDelay.first, Silent.breakBlocksDelay.last + 1)
        return true
    }

    return System.currentTimeMillis() < KillAuraSilentState.breakAllowedAt
}

private fun ModuleKillAura.canRunSilent(): Boolean {
    if (Silent.disableOnDeath && (player.isDeadOrDying || player.health <= 0f)) {
        enabled = false
        return false
    }

    return !player.isDeadOrDying && !player.isSpectator && requirementsMet && mc.gui.screen() == null &&
        !ModuleFreeCam.enabled &&
        (!Silent.requireMouseDown || mc.options.keyAttack.isPressedOnAny) &&
        (!Silent.limitToItems || player.mainHandItem.item in Silent.allowedItems) &&
        !shouldPauseSilentForBlockBreaking()
}

private fun ModuleKillAura.silentInteractionRange(): Double = GlobalVapeRotationSettings.interactionRange(
    SILENT_BASE_RANGE + Silent.extraSwingDistance.toDouble()
)

internal data class SilentAimState(
    val point: Vec3,
    val distanceToTarget: Double,
    val horizontalTargetMotion: Double,
    val targetPitch: Float,
)

/** Vape's aim coordinates: entity feet (Center) or the closest hitbox point (Closest). */
private fun ModuleKillAura.silentAimCoords(target: LivingEntity): Triple<Double, Double, Double> {
    return when (Silent.targetArea) {
        SilentTargetArea.CENTER -> Triple(target.x, target.y, target.z)
        SilentTargetArea.CLOSEST -> {
            // Vape clamps to the raw bounding box, no hitbox expansion (SilentAura.computeAimCoords).
            val box = target.boundingBox
            var currentX = player.x.coerceIn(box.minX, box.maxX)
            var currentZ = player.z.coerceIn(box.minZ, box.maxZ)
            if (currentX == player.x) currentX = player.x + 0.01
            if (currentZ == player.z) currentZ = player.z + 0.01
            Triple(currentX, target.y, currentZ)
        }
    }
}

/** Vape measures range to the un-jittered aim coordinates. */
private fun ModuleKillAura.isSilentInRange(target: LivingEntity, interactionRange: Double): Boolean {
    val (targetX, targetY, targetZ) = silentAimCoords(target)
    return player.position().distanceTo(Vec3(targetX, targetY, targetZ)) <= interactionRange
}

private fun ModuleKillAura.silentAimState(target: LivingEntity): SilentAimState {
    KillAuraSilentState.pitchJitter.update()
    KillAuraSilentState.xJitter.update()
    KillAuraSilentState.zJitter.update()

    val (targetX, targetY, targetZ) = silentAimCoords(target)
    val motionX = target.x - target.xo
    val motionZ = target.z - target.zo
    val horizontalTargetMotion = sqrt(motionX * motionX + motionZ * motionZ)
    val distanceToTarget = player.position().distanceTo(Vec3(targetX, targetY, targetZ))
    val jitteredTargetX = targetX + KillAuraSilentState.xJitter.current * (1.0 + horizontalTargetMotion)
    val jitteredTargetZ = targetZ + KillAuraSilentState.zJitter.current * (1.0 + horizontalTargetMotion)
    val playerEyeY = player.eyePosition.y
    val targetHeight = target.boundingBox.ysize
    val jitteredTargetY = if (playerEyeY < targetY) {
        targetY + KillAuraSilentState.pitchJitter.current * 0.5
    } else {
        min(playerEyeY, targetY + targetHeight) - SILENT_VERTICAL_OFFSET + KillAuraSilentState.pitchJitter.current
    }

    // Vape's RotationUtil.h computes the pitch from the player's feet toward the raw aim
    // coordinates with only the vertical jitter applied.
    val targetPitch = Math.toDegrees(
        Math.atan2(player.y - jitteredTargetY, Math.hypot(targetX - player.x, targetZ - player.z))
    ).toFloat()

    return SilentAimState(
        point = Vec3(jitteredTargetX, jitteredTargetY, jitteredTargetZ),
        distanceToTarget = distanceToTarget,
        horizontalTargetMotion = horizontalTargetMotion,
        targetPitch = targetPitch,
    )
}

/**
 * Horizontal distance from the aim ray (managed yaw) at the target's distance.
 *
 * @see gg.vape.utils.RotationUtil.L
 */
private fun ModuleKillAura.lookRayProximity(target: Entity, managedYaw: Float): Double {
    val distance = player.distanceTo(target)
    val rad = Math.toRadians(managedYaw + 90.0)
    val rayX = player.x + cos(rad) * distance
    val rayZ = player.z + sin(rad) * distance
    val dx = rayX - target.x
    val dz = rayZ - target.z
    return sqrt(dx * dx + dz * dz)
}

/** Mirrors Vape's isLookingAtTarget: the managed rotation ray has to hit the target hitbox. */
private fun ModuleKillAura.isLookingAtSilentTarget(target: Entity): Boolean {
    val managedRotation = RotationManager.currentRotation ?: return false
    val eyes = player.eyePosition
    val direction = managedRotation.directionVector
    val reach = silentInteractionRange()
    val hit = target.boundingBox.inflate(GlobalVapeRotationSettings.hitboxExpansion())
        .firstHit(eyes, eyes.add(direction.scale(reach)))
    return hit != null
}

internal fun ModuleKillAura.updateSilentTargetAndRotation() {
    if (!canRunSilent()) {
        targetTracker.reset()
        KillAuraSilentState.aimRotation = null
        return
    }

    val interactionRange = silentInteractionRange()
    val baseComparator = vapeTargetComparator(Silent.targetMode)
    val comparator = if (Silent.switchTargets) {
        // Vape re-sorts candidates by entity id when Switch is enabled.
        compareBy<LivingEntity> { it.id }.then(baseComparator)
    } else {
        baseComparator
    }

    val target = ModuleRikkaKAHelper.killAuraTarget ?: targetTracker.targets().asSequence()
        .filter { isValidVapeTarget(it, Silent.ignoreNaked,
            Silent.ignoreInvisible, Silent.ignoreBehindWalls) }
        .filter { isSilentInRange(it, interactionRange) }
        .filter { vapeYawAngle(it) <= Silent.maxAngle.toInt() / 2 }
        .sortedWith(comparator)
        .firstOrNull()

    if (target == null) {
        targetTracker.reset()
        KillAuraSilentState.targetId = null
        KillAuraSilentState.aimRotation = null
        SilentRotationProcessor.reset()
        return
    }

    if (KillAuraSilentState.targetId != target.id) {
        SilentRotationProcessor.randomizeGains()
        KillAuraSilentState.targetId = target.id
    }

    targetTracker.target = target
    val aimState = silentAimState(target)
    // Vape uses the yaw to the jittered point but the feet-based pitch target from RotationUtil.h.
    val rotation = Rotation(
        yaw = Rotation.lookingAt(aimState.point, player.eyePosition).yaw,
        pitch = aimState.targetPitch,
    )
    KillAuraSilentState.aimRotation = rotation

    SilentRotationProcessor.prepare(
        distanceToTarget = aimState.distanceToTarget,
        horizontalTargetMotion = aimState.horizontalTargetMotion,
    )

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

internal fun ModuleKillAura.runSilentTick() {
    if (!canRunSilent()) {
        targetTracker.reset()
        return
    }

    val target = targetTracker.target ?: return

    // Vape re-computes the click delay on every tick (RandomClickDelayValue.hasClickDelayElapsed).
    val clickReady = Silent.cooldown ||
        System.currentTimeMillis() - KillAuraSilentState.lastClickAt >=
        KillAuraVapeState.clickDelay.calculateNextDelayMillis(Silent.attackRate)

    // Vape only clicks once the managed (silent) yaw brings the target within reach of the aim ray.
    val managedRotation = RotationManager.currentRotation ?: RotationManager.serverRotation
    val readyToAttack = lookRayProximity(target, managedRotation.yaw) < SILENT_READY_ANGLE &&
        isSilentInRange(target, silentInteractionRange())
    if (!readyToAttack || Silent.cooldown && player.getAttackStrengthScale(0f) < 1f || !clickReady) {
        return
    }

    attackEntity(target, SwingMode.DO_NOT_HIDE, keepSprint && !shouldBlockSprinting)
    if (!Silent.cooldown) {
        KillAuraSilentState.lastClickAt = System.currentTimeMillis()
    }
}

/**
 * PID-style silent aim controller ported from Vape SilentAura.updateAim.
 *
 * @see gg.vape.module.combat.SilentAura.updateAim
 * @see gg.vape.module.combat.silentaura.SilentAuraRotationController
 */
internal object SilentRotationProcessor : RotationProcessor {
    private var pitchIntegral = 0f
    private var yawIntegral = 0f
    private var pitchProportionalScale = 1f
    private var pitchIntegralScale = 1f
    private var yawProportionalScale = 1f
    private var yawIntegralScale = 1f
    private var previousRotation: Rotation? = null

    private var distanceToTarget = 0.0
    private var horizontalTargetMotion = 0.0
    private var onTarget = false

    fun randomizeGains() {
        pitchProportionalScale = 0.85f + Random.nextFloat() * 0.3f
        pitchIntegralScale = 0.85f + Random.nextFloat() * 0.3f
        yawProportionalScale = 0.8f + Random.nextFloat() * 0.4f
        yawIntegralScale = 0.85f + Random.nextFloat() * 0.3f
        pitchIntegral = 0f
        yawIntegral = 0f
    }

    fun prepare(distanceToTarget: Double, horizontalTargetMotion: Double) {
        this.distanceToTarget = distanceToTarget
        this.horizontalTargetMotion = horizontalTargetMotion
        this.onTarget = ModuleKillAura.targetTracker.target?.let {
            ModuleKillAura.isLookingAtSilentTarget(it)
        } == true
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
        val previousPitchStep = RotationUtil.angleDifference(currentRotation.pitch, previous.pitch)
        var previousYawStep = RotationUtil.angleDifference(currentRotation.yaw, previous.yaw)
        val integrationStep = CONTROL_INTEGRATION_STEP
        val yawContinuingInSameDirection = sign(yawError) == sign(previousYawStep)
        val playerHorizontalSpeed = sqrt(ModuleKillAura.player.deltaMovement.x * ModuleKillAura.player.deltaMovement.x +
            ModuleKillAura.player.deltaMovement.z * ModuleKillAura.player.deltaMovement.z)

        val pitchProportionalGain = 0.45f * pitchProportionalScale
        val pitchIntegralGain = 0.91f * pitchIntegralScale
        var yawProportionalGain = (if (onTarget) 0.05f else 0.1f) * yawProportionalScale
        val yawIntegralGain = 0.33f * yawIntegralScale
        val playerVerticalMotion = ModuleKillAura.player.deltaMovement.y
        if (abs(playerVerticalMotion) > 0.1) {
            pitchError *= 1.0f + Random.nextFloat() * 0.32f
        }
        if (yawContinuingInSameDirection && abs(yawError) < 20f) {
            yawProportionalGain *= 2.5f
            previousYawStep *= (1.0f + min(horizontalTargetMotion + playerHorizontalSpeed, 0.25)).toFloat()
        }
        if (distanceToTarget < 0.8) {
            val distanceScale = distanceToTarget / 0.8
            pitchError *= (distanceScale * distanceScale).toFloat()
            yawError *= distanceScale.toFloat()
        }

        val pitchControlError = pitchError - previousPitchStep +
            previousYawStep * integrationStep * (if (Random.nextFloat() >= 0.5f) -1f else 1f)
        val yawControlError = yawError - previousYawStep
        pitchIntegral += pitchControlError * integrationStep
        yawIntegral += yawControlError * integrationStep

        val pitchAdjustment = pitchProportionalGain * pitchControlError + pitchIntegralGain * pitchIntegral
        var yawAdjustment = yawProportionalGain * yawControlError + yawIntegralGain * yawIntegral
        if (abs(yawError) > 120f) {
            yawIntegral = 0f
            yawAdjustment = 0f
        }

        // Vape targets managedYaw + yawError + yawAdjustment / 3 on yaw but only
        // managedPitch + pitchAdjustment on pitch: the error term is absent on the pitch axis.
        val controlled = Rotation(
            targetRotation.yaw + yawAdjustment / 3f,
            (currentRotation.pitch + pitchAdjustment).coerceIn(-90f, 90f),
        )

        val gaussian = sqrt(-2.0 * ln(Random.nextDouble().coerceAtLeast(0.0001))) *
            cos(Math.PI * 2.0 * Random.nextDouble())
        val multiplier = exp(0.65 + 0.25 * gaussian).coerceIn(1.4, 3.0)
        // Vape accumulates ~50 controller updates per tick at speed * 0.25 per update and applies
        // each mickey as mouseScale * 0.15 degrees; its sensitivity compensation cancels out to
        // 1.875 * speed degrees per tick.
        var maxStep = Silent.aimSpeed * multiplier * VAPE_ROTATION_STEP_SCALE
        if (distanceToTarget < 0.8) {
            maxStep *= (distanceToTarget / 0.8).toFloat()
        }
        val result = currentRotation.towardsLinear(controlled, maxStep.toFloat(), maxStep.toFloat())
        previousRotation = result
        return result
    }
}

internal class SilentAimJitter(private val min: Double, private val max: Double) {
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
