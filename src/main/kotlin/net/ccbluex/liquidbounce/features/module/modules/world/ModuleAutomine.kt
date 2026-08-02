package net.ccbluex.liquidbounce.features.module.modules.world

import net.ccbluex.liquidbounce.event.events.NotificationEvent
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.utils.collection.blockSortedSetOf
import net.ccbluex.liquidbounce.utils.client.notification
import net.ccbluex.liquidbounce.utils.pathing.PathingEngine
import net.minecraft.world.level.block.Blocks

object ModuleAutomine : ClientModule("Automine", ModuleCategories.WORLD) {

    private val blocks by blocks(
        "Blocks",
        blockSortedSetOf(
            Blocks.DIAMOND_ORE,
            Blocks.DEEPSLATE_DIAMOND_ORE,
        ),
    )

    private val quantity by int("Quantity", 0, 0..2304, "items")
    private val legitMine by boolean("LegitMine", false)
    private val exploreForBlocks by boolean("ExploreForBlocks", true)
    private val mineGoalUpdateInterval by int("GoalUpdateInterval", 5, 0..40, "ticks")
    private val maxOreLocations by int("MaxOreLocations", 64, 1..256)
    private val minY by int("MinY", 0, -64..2031)
    private val maxY by int("MaxY", 2031, -64..2031)
    private val exposedOnly by boolean("ExposedOnly", false)
    private val exposedDistance by int("ExposedDistance", 1, 1..8)

    override fun onEnabled() {
        if (blocks.isEmpty()) {
            notification(name, "Select at least one block", NotificationEvent.Severity.ERROR)
            enabled = false
            return
        }

        PathingEngine.configure(
            legitMine,
            exploreForBlocks,
            mineGoalUpdateInterval,
            maxOreLocations,
            minY,
            maxY,
            exposedOnly,
            exposedDistance,
        )
        PathingEngine.mine(quantity, blocks)
    }

    override fun onDisabled() {
        PathingEngine.cancelMining()
    }
}
