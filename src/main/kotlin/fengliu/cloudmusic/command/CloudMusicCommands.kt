package fengliu.cloudmusic.command

import net.ccbluex.liquidbounce.features.command.Command
import net.ccbluex.liquidbounce.features.command.builder.CommandBuilder
import net.ccbluex.liquidbounce.features.command.builder.ParameterBuilder
import net.ccbluex.liquidbounce.features.command.dsl.castVarargNotRequired

/**
 * Registers the merged CloudMusic command tree under the LiquidBounce command
 * system. The original Brigadier tree from the standalone mod is kept in
 * [MusicCommand] and executed with the client's '.' command prefix.
 */
object CloudMusicCommands {

    private val source = LbClientCommandSource()

    @Volatile
    private var registered = false

    private val argsParameter = ParameterBuilder
        .begin<String>("args")
        .vararg()
        .optional()
        .build()

    private val command: Command = CommandBuilder
        .begin("cloudmusic")
        .parameter(argsParameter)
        .handler {
            val args = argsParameter.castVarargNotRequired()?.joinToString(" ") ?: ""
            MusicCommand.executeCommand(args, source)
        }
        .build()

    /**
     * Registers `.cloudmusic` once into the client command manager.
     */
    fun register() {
        if (registered) {
            return
        }
        registered = true
        net.ccbluex.liquidbounce.features.command.CommandManager.addCommand(command)
    }
}
