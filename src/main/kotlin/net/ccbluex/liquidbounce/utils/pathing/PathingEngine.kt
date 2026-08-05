package net.ccbluex.liquidbounce.utils.pathing

import baritone.api.BaritoneAPI
import baritone.api.process.IBaritoneProcess
import baritone.api.process.PathingCommand
import baritone.api.process.PathingCommandType
import net.ccbluex.liquidbounce.event.events.NotificationEvent
import net.ccbluex.liquidbounce.features.module.ModuleManager
import net.ccbluex.liquidbounce.features.module.modules.xuanrikka.RikkaAutomationModule
import net.ccbluex.liquidbounce.utils.combat.CombatManager
import net.ccbluex.liquidbounce.utils.client.chat
import net.ccbluex.liquidbounce.utils.client.notification
import net.minecraft.network.chat.Component
import net.minecraft.world.level.block.Block
import java.util.function.BiConsumer
import java.util.function.Consumer

/** Host-facing adapter for the integrated pathing engine. */
@Suppress("TooManyFunctions")
object PathingEngine {

    private val settings
        get() = BaritoneAPI.getSettings()

    private val primary
        get() = BaritoneAPI.getProvider().primaryBaritone

    private val commandManager
        get() = primary.commandManager

    private var pathingMovementActive = false
    private var strafeWasEnabled = false
    private var combatPauserInstalled = false

    private val combatPauser = object : IBaritoneProcess {
        override fun isActive(): Boolean =
            isRikkaAutomationActive() && CombatManager.isInCombat

        override fun onTick(calcFailed: Boolean, isSafeToCancel: Boolean): PathingCommand {
            // Rikka must not retain movement, mining, placement, or a stale look target while combat owns the player.
            primary.inputOverrideHandler.clearAllKeys()
            primary.lookBehavior.updateTarget(null, false)
            return PathingCommand(null, PathingCommandType.REQUEST_PAUSE)
        }

        override fun isTemporary() = true

        override fun onLostControl() = Unit

        override fun priority() = IBaritoneProcess.DEFAULT_PRIORITY + 1

        override fun displayName0() = "LiquidBounce combat pause"
    }

    fun onStrafeStateChanged(enabled: Boolean) {
        if (isPathing()) {
            strafeWasEnabled = enabled
        } else {
            pathingMovementActive = false
            strafeWasEnabled = enabled
        }
    }

    fun shouldSuspendStrafe(actualEnabled: Boolean): Boolean {
        val pathing = isPathing()
        if (pathing != pathingMovementActive) {
            pathingMovementActive = pathing
            if (pathing) {
                strafeWasEnabled = actualEnabled
            } else if (!actualEnabled) {
                strafeWasEnabled = false
            }
        }
        return pathing && strafeWasEnabled
    }

    fun isPathing(): Boolean = primary.pathingBehavior.isPathing

    fun isRikkaAutomationEnabled(): Boolean =
        ModuleManager.getModules().any { it is RikkaAutomationModule && it.enabled }

    fun isRikkaAutomationActive(): Boolean =
        isRikkaAutomationEnabled() && (primary.pathingBehavior.hasPath() || isMining())

    fun shouldPauseRikkaAutomationForCombat(): Boolean =
        isRikkaAutomationEnabled() && CombatManager.isInCombat

    /**
     * Whether the integrated pathing engine currently needs to control the player.
     * Mining can briefly stop pathing between targets, so it must be included too.
     */
    fun isAutomatedMovement(): Boolean = isPathing() || isMining()

    private fun sanitize(text: String): String = text
        .replace("Baritone", "LiquidBounce", ignoreCase = true)
        .replace("Baritoe", "LiquidBounce", ignoreCase = true)

    fun installOutputBridge() {
        if (!combatPauserInstalled) {
            primary.pathingControlManager.registerProcess(combatPauser)
            combatPauserInstalled = true
        }
        settings.chatControl.value = false
        settings.prefixControl.value = false
        settings.logAsToast.value = false
        settings.useMessageTag.value = true
        settings.logger.value = Consumer { message: Component ->
            chat(sanitize(message.string))
        }
        settings.notifier.value = BiConsumer { message, error ->
            notification(
                "LiquidBounce",
                sanitize(message),
                if (error) NotificationEvent.Severity.ERROR else NotificationEvent.Severity.INFO,
            )
        }
        settings.toaster.value = BiConsumer { title, message ->
            notification(
                "LiquidBounce",
                sanitize("${title.string}: ${message.string}"),
                NotificationEvent.Severity.INFO,
            )
        }
    }

    fun configure(
        legitMine: Boolean,
        exploreForBlocks: Boolean,
        mineGoalUpdateInterval: Int,
        maxOreLocations: Int,
        minY: Int,
        maxY: Int,
        exposedOnly: Boolean,
        exposedDistance: Int,
    ) {
        installOutputBridge()
        settings.legitMine.value = legitMine
        settings.exploreForBlocks.value = exploreForBlocks
        settings.mineGoalUpdateInterval.value = mineGoalUpdateInterval
        settings.mineMaxOreLocationsCount.value = maxOreLocations
        settings.minYLevelWhileMining.value = minY
        settings.maxYLevelWhileMining.value = maxY
        settings.allowOnlyExposedOres.value = exposedOnly
        settings.allowOnlyExposedOresDistance.value = exposedDistance

        // LiquidBounce owns all user-facing output and command handling.
    }

    fun commandNames(): List<String> = commandManager.registry.entries
        .flatMap { it.names }
        .filter { it.isNotBlank() && !it.equals("baritone", true) }
        .distinctBy { it.lowercase() }

    fun execute(command: String, arguments: List<String>): Boolean {
        installOutputBridge()
        val line = buildString {
            append(command)
            arguments.forEach { argument ->
                append(' ')
                if (argument.any { it.isWhitespace() }) {
                    append('"').append(argument.replace("\"", "\\\"")).append('"')
                } else {
                    append(argument)
                }
            }
        }
        val executed = commandManager.execute(line)
        if (!executed) {
            chat("Unknown pathing command: $command")
        }
        return executed
    }

    fun complete(command: String, arguments: List<String>): List<String> {
        val prefix = buildString {
            append(command)
            if (arguments.isNotEmpty()) {
                append(' ').append(arguments.joinToString(" "))
            }
        }
        return commandManager.tabComplete(prefix).toList()
    }

    fun mine(quantity: Int, blocks: Collection<Block>) {
        primary.mineProcess.mine(quantity, *blocks.toTypedArray())
    }

    fun cancelMining() {
        primary.mineProcess.cancel()
    }

    fun cancel() {
        execute("cancel", emptyList())
    }

    fun cancelAndDisableRikkaAutomationModules() {
        cancel()
        ModuleManager.getModules()
            .filter { it is RikkaAutomationModule && it.enabled }
            .forEach { it.enabled = false }
    }

    fun isMining(): Boolean = primary.mineProcess.isActive
}
