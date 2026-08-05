package net.ccbluex.liquidbounce.features.module.modules.combat

import baritone.api.BaritoneAPI
import baritone.api.pathing.goals.GoalNear
import baritone.api.process.IBaritoneProcess
import baritone.api.process.PathingCommand
import baritone.api.process.PathingCommandType
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.features.module.ModuleOrigin
import net.ccbluex.liquidbounce.features.module.modules.combat.killaura.ModuleKillAura
import net.ccbluex.liquidbounce.utils.combat.Targets
import net.ccbluex.liquidbounce.utils.combat.shouldBeAttacked
import net.ccbluex.liquidbounce.utils.client.player
import net.ccbluex.liquidbounce.utils.pathing.PathingEngine
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.NeutralMob
import net.minecraft.world.entity.monster.Enemy
import net.minecraft.world.entity.monster.Creeper
import net.minecraft.world.entity.projectile.arrow.AbstractArrow
import kotlin.math.max

/** Keeps Rikka automation safe by withdrawing from immediate threats and handing other targets to KillAura. */
object ModuleRikkaKAHelper : ClientModule(
    "RikkaKAHelper",
    ModuleCategories.COMBAT,
    secondaryCategories = listOf(ModuleCategories.XUAN_RIKKA),
    origin = ModuleOrigin.XUAN_RIKKA,
) {
    private val targets by multiEnumChoice<Targets>("Targets", Targets.HOSTILE, Targets.ANGERABLE)
    private val threatRange by float("ThreatRange", 16f, 4f..32f, "blocks")
    private val engageRange by float("EngageRange", 3.2f, 2f..5f, "blocks")
    private val creeperRetreatDistance by float("CreeperRetreatDistance", 10f, 6f..20f, "blocks")
    private var installed = false
    private var threat: LivingEntity? = null
    private var projectileThreat: AbstractArrow? = null

    val killAuraTarget: LivingEntity?
        get() = threat?.takeIf { canAttack(it) }

    override fun onEnabled() {
        if (!installed) {
            BaritoneAPI.getProvider().primaryBaritone.pathingControlManager.registerProcess(threatProcess)
            installed = true
        }
    }

    override fun onDisabled() {
        threat = null
        projectileThreat = null
    }

    private val threatProcess = object : IBaritoneProcess {
        override fun isActive(): Boolean {
            threat = findThreat()
            projectileThreat = findProjectileThreat()
            return threat != null || projectileThreat != null
        }

        override fun onTick(calcFailed: Boolean, isSafeToCancel: Boolean): PathingCommand {
            projectileThreat?.let { arrow ->
                val motion = arrow.deltaMovement.normalize()
                val sideStep = player.position().add(-motion.z * 4.0, 0.0, motion.x * 4.0)
                return PathingCommand(GoalNear(BlockPos.containing(sideStep), 1), PathingCommandType.SET_GOAL_AND_PATH)
            }

            val target = threat ?: return PathingCommand(null, PathingCommandType.DEFER)
            val goal = if (target is Creeper) {
                BaritoneAPI.getProvider().primaryBaritone.inputOverrideHandler.clearAllKeys()
                val away = player.position().subtract(target.position()).normalize().scale(creeperRetreatDistance.toDouble())
                GoalNear(BlockPos.containing(player.position().add(away)), 2)
            } else {
                GoalNear(target.blockPosition(), max(2, engageRange.toInt()))
            }
            return PathingCommand(goal, PathingCommandType.SET_GOAL_AND_PATH)
        }

        override fun isTemporary() = true
        override fun onLostControl() = Unit
        override fun priority() = IBaritoneProcess.DEFAULT_PRIORITY + 2
        override fun displayName0() = "Rikka threat response"
    }

    private fun findThreat(): LivingEntity? {
        if (!enabled || !PathingEngine.isRikkaAutomationEnabled()) return null
        return world.entitiesForRendering().asSequence()
            .filterIsInstance<LivingEntity>()
            .filter(::isThreat)
            .filter { it is Creeper || ModuleKillAura.enabled }
            .minByOrNull { it.distanceToSqr(player) }
    }

    private fun findProjectileThreat(): AbstractArrow? {
        if (!enabled || !PathingEngine.isRikkaAutomationEnabled()) return null
        return world.entitiesForRendering().asSequence()
            .filterIsInstance<AbstractArrow>()
            .filter { arrow ->
                val toPlayer = player.position().subtract(arrow.position())
                arrow.distanceTo(player) <= 8f && arrow.deltaMovement.dot(toPlayer) > 0.0
            }
            .minByOrNull { it.distanceToSqr(player) }
    }

    private fun isThreat(entity: LivingEntity): Boolean {
        if (!entity.isAlive || entity.distanceTo(player) > threatRange || !entity.shouldBeAttacked(targets)) return false
        return entity is Enemy || entity is NeutralMob && entity.persistentAngerTarget == player.uuid
    }

    private fun canAttack(entity: LivingEntity): Boolean =
        entity.distanceTo(player) <= engageRange && (entity !is Creeper || entity.swellDir <= 0)
}
