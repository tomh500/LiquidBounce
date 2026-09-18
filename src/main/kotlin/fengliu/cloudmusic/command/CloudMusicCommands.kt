package fengliu.cloudmusic.command

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import net.ccbluex.liquidbounce.event.EventListener
import net.ccbluex.liquidbounce.event.events.ChatSendEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.command.CommandManager
import net.ccbluex.liquidbounce.features.command.CommandRegistrar
import net.ccbluex.liquidbounce.features.command.brigadier.ClientCommandSource
import net.ccbluex.liquidbounce.utils.kotlin.EventPriorityConvention

/** Bridges the original CloudMusic command tree into the client command system. */
object CloudMusicCommands : EventListener, CommandRegistrar {

    private val source = LbClientCommandSource()

    @Volatile
    private var registered = false

    override fun register(dispatcher: CommandDispatcher<ClientCommandSource>) {
        for (root in listOf("rikkamusic", "music")) {
            dispatcher.register(
                LiteralArgumentBuilder.literal<ClientCommandSource>(root)
                    .executes {
                        MusicCommand.executeCommand("", source)
                        1
                    }
                    .then(
                        RequiredArgumentBuilder.argument<ClientCommandSource, String>(
                            "arguments", StringArgumentType.greedyString()
                        ).suggests { _, builder ->
                            MusicCommand.completeCommand(builder.remaining, source)
                                .forEach(builder::suggest)
                            builder.buildFuture()
                        }.executes { context ->
                            MusicCommand.executeCommand(StringArgumentType.getString(context, "arguments"), source)
                            1
                        }
                    )
            )
        }
    }

    /** Keep quoted and greedy arguments intact when commands are sent through chat. */
    @Suppress("unused")
    private val rawCommandHandler = handler<ChatSendEvent>(
        priority = (EventPriorityConvention.FIRST_PRIORITY + 100).toShort(),
    ) { event ->
        val rawArgs = extractRawArgs(event.message) ?: return@handler
        MusicCommand.executeCommand(rawArgs, source)
        event.cancelEvent()
    }

    private fun extractRawArgs(message: String): String? {
        val prefix = CommandManager.GlobalSettings.prefix
        val roots = arrayOf("${prefix}rikkamusic", "${prefix}music")
        val root = roots.firstOrNull { root ->
            message.equals(root, ignoreCase = true) ||
                (message.startsWith(root, ignoreCase = true) &&
                    message.getOrNull(root.length)?.isWhitespace() == true)
        } ?: return null
        return message.substring(root.length).trimStart()
    }

    fun register() {
        if (registered) return
        registered = true
        MusicCommand.registerAll()
        CommandManager.register(this)
    }
}
