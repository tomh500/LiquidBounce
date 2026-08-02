package net.ccbluex.liquidbounce.features.command.commands.ingame

import net.ccbluex.liquidbounce.features.command.Command
import net.ccbluex.liquidbounce.features.command.builder.CommandBuilder
import net.ccbluex.liquidbounce.features.command.builder.ParameterBuilder
import net.ccbluex.liquidbounce.utils.pathing.PathingEngine

/** Registers the integrated pathing engine commands in LiquidBounce's command tree. */
object CommandPathing {

    fun createCommands(existing: Collection<Command>): List<Command> {
        val taken = existing.flatMap { listOf(it.name) + it.aliases }
            .toMutableSet()

        return PathingEngine.commandNames()
            .filter { taken.add(it.lowercase()) }
            .map { name -> createCommand(name) }
    }

    private fun createCommand(name: String): Command = CommandBuilder
        .begin(name)
        .parameter(
            ParameterBuilder
                .begin<String>("arguments")
                .verifiedBy(ParameterBuilder.STRING_VALIDATOR)
                .optional()
                .vararg()
                .build()
        )
        .handler {
            val arguments = (args[0] as? Array<*>)
                ?.mapNotNull { it as? String }
                ?: emptyList()
            PathingEngine.execute(name, arguments)
        }
        .build()
}
