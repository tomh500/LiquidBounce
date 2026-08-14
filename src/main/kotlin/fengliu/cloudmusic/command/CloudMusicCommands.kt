package fengliu.cloudmusic.command

import net.ccbluex.liquidbounce.features.command.Command
import net.ccbluex.liquidbounce.event.EventListener
import net.ccbluex.liquidbounce.event.events.ChatSendEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.command.CommandManager
import net.ccbluex.liquidbounce.features.command.builder.CommandBuilder
import net.ccbluex.liquidbounce.features.command.builder.ParameterBuilder
import net.ccbluex.liquidbounce.features.command.dsl.castVarargNotRequired
import net.ccbluex.liquidbounce.utils.kotlin.EventPriorityConvention

/**
 * Registers the merged CloudMusic command tree under the LiquidBounce command
 * system. The original Brigadier tree from the standalone mod is kept in
 * [MusicCommand] and executed with the client's '.' command prefix.
 */
object CloudMusicCommands : EventListener {

    private val source = LbClientCommandSource()

    @Volatile
    private var registered = false

    private val argsParameter = ParameterBuilder
        .begin<String>("args")
        .vararg()
        .optional()
        .autocompletedWith { begin, args ->
            val rawArgs = args.drop(1).joinToString(" ")
            MusicCommand.completeCommand(
                if (begin.isEmpty()) "$rawArgs " else rawArgs,
                source,
            )
        }
        .build()

    private val command: Command = CommandBuilder
        .begin("rikkamusic")
        .alias("music")
        .parameter(argsParameter)
        .handler {
            val args = argsParameter.castVarargNotRequired()?.joinToString(" ") ?: ""
            MusicCommand.executeCommand(args, source)
        }
        .build()

    /**
     * Keep CloudMusic's complete Brigadier input intact. The generic LB command
     * parser intentionally tokenizes varargs, which loses the quoted/greedy
     * argument semantics used throughout the original CloudMusic command tree.
     */
    @Suppress("unused")
    private val rawCommandHandler = handler<ChatSendEvent>(
        priority = (EventPriorityConvention.FIRST_PRIORITY + 1).toShort(),
    ) { event ->
        val rawArgs = extractRawArgs(event.message) ?: return@handler
        MusicCommand.executeCommand(rawArgs, source)
        event.cancelEvent()
    }

    private fun extractRawArgs(message: String): String? {
        val prefix = CommandManager.GlobalSettings.prefix
        val roots = arrayOf("${prefix}rikkamusic", "${prefix}music", "/rikkamusic", "/music")
        val root = roots.firstOrNull { root ->
            message.equals(root, ignoreCase = true) ||
                (message.startsWith(root, ignoreCase = true) &&
                    message.getOrNull(root.length)?.isWhitespace() == true)
        }
            ?: return null
        return message.substring(root.length).trimStart()
    }

    /**
     * Registers `.rikkamusic` and its `.music` alias once into the client command manager.
     */
    fun register() {
        if (registered) {
            return
        }
        registered = true
        net.ccbluex.liquidbounce.features.command.CommandManager.addCommand(command)
    }
}
