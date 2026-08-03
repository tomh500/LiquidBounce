package net.ccbluex.liquidbounce.features.module.modules.combat

import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.features.module.ModuleOrigin
import net.ccbluex.liquidbounce.utils.block.SwingMode
import net.ccbluex.liquidbounce.utils.clicking.Clicker
import net.ccbluex.liquidbounce.utils.combat.attackEntity
import net.ccbluex.liquidbounce.utils.combat.shouldBeAttacked
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.EntityHitResult
import kotlin.random.Random

/** Vape-style crosshair triggerbot with first-hit and timing controls. */
object ModuleTriggerbot : ClientModule(
    "Triggerbot",
    ModuleCategories.COMBAT,
    aliases = listOf("TriggerBot"),
    origin = ModuleOrigin.XUAN_RIKKA,
) {
    private val requireMouseDown by boolean("RequireMouseDown", false)
    private val selectFirstHit by boolean("SelectFirstHit", false)
    private val mouseOverDelay by int("MouseOverDelay", 0, 0..200, "ms")
    private val extraDelay by int("ExtraDelay", 0, -20..20, "ticks")
    private val targetMissChance by float("TargetMissChance", 0f, 0f..100f, "%")
    private val clicker = tree(Clicker(this, mc.options.keyAttack, null, simulateAttackKeyDown = true))

    private var targetId = -1
    private var targetSeenAt = 0L
    private var firstHitWait = false

    override fun onDisabled() {
        targetId = -1
        targetSeenAt = 0L
        firstHitWait = false
        super.onDisabled()
    }

    @Suppress("unused")
    private val tickHandler = tickHandler {
        if (mc.gui.screen() != null || (requireMouseDown && !mc.options.keyAttack.isDown)) {
            resetTarget()
            return@tickHandler
        }

        val hit = mc.hitResult as? EntityHitResult
        val target = hit?.entity
        if (target !is LivingEntity || !target.shouldBeAttacked()) {
            resetTarget()
            return@tickHandler
        }

        if (target.id != targetId) {
            targetId = target.id
            targetSeenAt = System.currentTimeMillis()
            firstHitWait = selectFirstHit
        }

        if (firstHitWait) {
            if (target.hurtTime > 0) {
                firstHitWait = false
            } else {
                return@tickHandler
            }
        }

        if (System.currentTimeMillis() - targetSeenAt < mouseOverDelay ||
            player.attackStrengthTicker + extraDelay * 2 < player.currentItemAttackStrengthDelay
        ) {
            return@tickHandler
        }

        if (Random.nextFloat() * 100f < targetMissChance) {
            return@tickHandler
        }

        clicker.click {
            attackEntity(target, SwingMode.DO_NOT_HIDE)
            true
        }
    }

    private fun resetTarget() {
        targetId = -1
        targetSeenAt = 0L
        firstHitWait = false
    }
}
