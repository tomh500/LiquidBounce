package net.ccbluex.liquidbounce.features.command.commands.ingame

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import net.ccbluex.liquidbounce.features.command.CommandRegistrar
import net.ccbluex.liquidbounce.features.command.brigadier.ClientCommandSource
import net.ccbluex.liquidbounce.utils.pathing.PathingEngine

/** Registers the integrated pathing commands against the current Brigadier dispatcher. */
object CommandPathing : CommandRegistrar {

    override fun register(dispatcher: CommandDispatcher<ClientCommandSource>) {
        PathingEngine.commandNames().forEach { name ->
            if (dispatcher.root.children.any { it.name.equals(name, ignoreCase = true) }) {
                return@forEach
            }

            dispatcher.register(
                LiteralArgumentBuilder.literal<ClientCommandSource>(name)
                    .executes { if (PathingEngine.execute(name, emptyList())) 1 else 0 }
                    .then(
                        RequiredArgumentBuilder.argument<ClientCommandSource, String>(
                            "arguments", StringArgumentType.greedyString()
                        ).executes { context ->
                            val arguments = StringArgumentType.getString(context, "arguments")
                                .split(Regex("\\s+"))
                                .filter(String::isNotEmpty)
                            if (PathingEngine.execute(name, arguments)) 1 else 0
                        }
                    )
            )
        }
    }
}
