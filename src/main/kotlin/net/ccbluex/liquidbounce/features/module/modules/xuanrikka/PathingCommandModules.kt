package net.ccbluex.liquidbounce.features.module.modules.xuanrikka

import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.utils.input.InputBind
import net.ccbluex.liquidbounce.utils.pathing.PathingEngine

abstract class RikkaProcessModule(
    name: String,
    primaryCategory: net.ccbluex.liquidbounce.features.module.ModuleCategory,
    aliases: List<String> = emptyList(),
) : ClientModule(name, primaryCategory, secondaryCategories = listOf(ModuleCategories.XUAN_RIKKA), aliases = aliases) {

    protected abstract fun startProcess()

    override fun onEnabled() {
        startProcess()
    }

    override fun onDisabled() {
        PathingEngine.cancel()
    }
}

object ModuleRikkaGoto : RikkaProcessModule("RikkaGoto", ModuleCategories.MOVEMENT, aliases = listOf("Goto")) {
    private val x by int("X", 0, -30_000_000..30_000_000)
    private val y by int("Y", 0, -2048..2048)
    private val z by int("Z", 0, -30_000_000..30_000_000)

    override fun startProcess() {
        PathingEngine.execute("goto", listOf(x.toString(), y.toString(), z.toString()))
    }
}

object ModuleRikkaExplore : RikkaProcessModule("RikkaExplore", ModuleCategories.WORLD, aliases = listOf("Explore")) {
    private val useCoordinates by boolean("UseCoordinates", false)
    private val x by int("X", 0, -30_000_000..30_000_000)
    private val z by int("Z", 0, -30_000_000..30_000_000)

    override fun startProcess() {
        val arguments = if (useCoordinates) listOf(x.toString(), z.toString()) else emptyList()
        PathingEngine.execute("explore", arguments)
    }
}

object ModuleRikkaFollow : RikkaProcessModule("RikkaFollow", ModuleCategories.MOVEMENT, aliases = listOf("Follow")) {
    private val target by text("Target", "players")

    override fun startProcess() {
        PathingEngine.execute("follow", listOf(target))
    }
}

object ModuleRikkaBuild : RikkaProcessModule("RikkaBuild", ModuleCategories.WORLD, aliases = listOf("Build")) {
    private val schematic by text("Schematic", "example.schem")

    override fun startProcess() {
        PathingEngine.execute("build", listOf(schematic))
    }
}

object ModuleRikkaResume : RikkaProcessModule("RikkaResume", ModuleCategories.MOVEMENT, aliases = listOf("Path")) {
    override fun startProcess() {
        PathingEngine.execute("path", emptyList())
    }
}

object ModuleRikkaPause : ClientModule(
    "RikkaPause",
    ModuleCategories.MOVEMENT,
    secondaryCategories = listOf(ModuleCategories.XUAN_RIKKA),
    bindAction = InputBind.BindAction.HOLD,
    aliases = listOf("Pause"),
) {
    override fun onEnabled() {
        PathingEngine.execute("pause", emptyList())
    }

    override fun onDisabled() {
        PathingEngine.execute("resume", emptyList())
    }
}

object ModuleRikkaStop : ClientModule(
    "RikkaStop",
    ModuleCategories.MOVEMENT,
    secondaryCategories = listOf(ModuleCategories.XUAN_RIKKA),
    bindAction = InputBind.BindAction.HOLD,
    aliases = listOf("Stop", "Cancel"),
) {
    override fun onEnabled() {
        PathingEngine.cancelAndDisableMovementModules()
    }
}
