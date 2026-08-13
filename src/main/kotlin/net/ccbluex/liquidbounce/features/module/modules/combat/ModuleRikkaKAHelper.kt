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
import net.ccbluex.liquidbounce.features.module.modules.movement.autododge.ModuleAutoDodge
import net.ccbluex.liquidbounce.utils.combat.Targets
import net.ccbluex.liquidbounce.utils.combat.shouldBeAttacked
import net.ccbluex.liquidbounce.utils.client.player
import net.ccbluex.liquidbounce.utils.pathing.PathingEngine
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.NeutralMob
import net.minecraft.world.entity.monster.Enemy
import net.minecraft.world.entity.monster.Creeper
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
    private val creeperSafetyDistance by float("CreeperSafetyDistance", 3f, 2f..6f, "blocks")
    private val engageRange by float("EngageRange", 3.2f, 2f..5f, "blocks")
    private val creeperRetreatDistance by float("CreeperRetreatDistance", 10f, 6f..20f, "blocks")
    private var installed = false
    private var threat: LivingEntity? = null
    private var creeperAvoidance: Creeper? = null

    val killAuraTarget: LivingEntity?
        get() = threat?.takeIf(::canAttack)

    override fun onEnabled() {
        if (!installed) {
            BaritoneAPI.getProvider().primaryBaritone.pathingControlManager.registerProcess(threatProcess)
            installed = true
        }
    }

    override fun onDisabled() {
        threat = null
        creeperAvoidance = null
    }

    private val threatProcess = object : IBaritoneProcess {
        override fun isActive(): Boolean {
            if (shouldYieldToAutoDodge()) {
                threat = null
                BaritoneAPI.getProvider().primaryBaritone.inputOverrideHandler.clearAllKeys()
                return false
            }

            val activeCreeper = creeperAvoidance?.takeIf { it.isAlive && it.distanceTo(player) <= threatRange }
            val nearbyCreeper = findCreeper()
            creeperAvoidance = activeCreeper ?: nearbyCreeper?.takeIf {
                !ModuleKillAura.enabled || it.distanceTo(player) < creeperSafetyDistance
            }
            threat = creeperAvoidance ?: findThreat()
            return threat != null
        }

        override fun onTick(calcFailed: Boolean, isSafeToCancel: Boolean): PathingCommand {
            val target = threat ?: return PathingCommand(null, PathingCommandType.DEFER)
            val goal = if (target is Creeper) {
                BaritoneAPI.getProvider().primaryBaritone.inputOverrideHandler.clearAllKeys()
                if (target.distanceTo(player) >= creeperSafetyDistance) {
                    // Do not resume the old mine path through a live creeper. KA can kill it from here.
                    return PathingCommand(null, PathingCommandType.REQUEST_PAUSE)
                }
                val separation = player.position().subtract(target.position())
                val away = if (separation.lengthSqr() > 0.01) {
                    separation.normalize()
                } else {
                    player.lookAngle.multiply(-1.0, 0.0, -1.0).normalize()
                }.scale(creeperRetreatDistance.toDouble())
                GoalNear(BlockPos.containing(player.position().add(away)), 2)
            } else {
                GoalNear(target.blockPosition(), max(2, engageRange.toInt()))
            }
            return PathingCommand(goal, PathingCommandType.SET_GOAL_AND_PATH)
        }

        override fun isTemporary() = true
        override fun onLostControl() = Unit
        override fun priority() = IBaritoneProcess.DEFAULT_PRIORITY + 10
        override fun displayName0() = "Rikka threat response"
    }

    private fun findThreat(): LivingEntity? {
        if (!enabled || !PathingEngine.isRikkaAutomationEnabled()) return null
        return world.entitiesForRendering().asSequence()
            .filterIsInstance<LivingEntity>()
            .filter(::isThreat)
            .filter { it is Creeper || ModuleKillAura.enabled }
            .minWithOrNull(compareBy<LivingEntity> { if (it is Creeper) 0 else 1 }.thenBy { it.distanceToSqr(player) })
    }

    private fun findCreeper(): Creeper? {
        if (!enabled || !PathingEngine.isRikkaAutomationEnabled()) return null
        return world.entitiesForRendering().asSequence()
            .filterIsInstance<Creeper>()
            .filter { it.isAlive && it.distanceTo(player) <= threatRange }
            .minByOrNull { it.distanceToSqr(player) }
    }

    /** Let LiquidBounce's predictive arrow dodge own movement while it has an actual evasion to execute. */
    private fun shouldYieldToAutoDodge(): Boolean =
        ModuleAutoDodge.running && ModuleAutoDodge.getInflictedHit(player.position()) != null

    private fun isThreat(entity: LivingEntity): Boolean {
        if (!entity.isAlive || entity.distanceTo(player) > threatRange) return false
        if (entity is Creeper) return false
        if (!entity.shouldBeAttacked(targets)) return false
        return entity is Enemy || entity is NeutralMob && entity.persistentAngerTarget == player.uuid
    }

    private fun canAttack(entity: LivingEntity): Boolean =
        entity.distanceTo(player) <= engageRange && (entity !is Creeper || entity.swellDir <= 0)
}
