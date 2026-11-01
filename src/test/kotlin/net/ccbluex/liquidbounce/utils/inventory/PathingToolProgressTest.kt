package net.ccbluex.liquidbounce.utils.inventory

import baritone.utils.ToolSet
import net.ccbluex.liquidbounce.test.MinecraftBootstrap
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import net.minecraft.core.HolderSet
import net.minecraft.core.component.DataComponentMap
import net.minecraft.core.component.DataComponents
import net.minecraft.core.component.PatchedDataComponentMap
import net.minecraft.world.item.component.Tool
import net.minecraft.world.item.enchantment.ItemEnchantments
import kotlin.test.Test
import kotlin.test.assertTrue

class PathingToolProgressTest {
    @Test
    fun `stone pickaxes outperform bare hands without instant mining`() {
        MinecraftBootstrap.ensureInitialized()
        val state = Blocks.STONE.defaultBlockState()
        val bareHand = ToolSet.calculateSpeedVsBlock(ItemStack.EMPTY, state)
        // 26.3 item components normally bind from datapacks; use explicit tool fixtures here.
        for (speed in listOf(2f, 4f, 6f, 8f)) {
            val components = PatchedDataComponentMap(DataComponentMap.EMPTY)
            components.set(DataComponents.TOOL, Tool(listOf(Tool.Rule.minesAndDrops(
                HolderSet.direct(Blocks.STONE.builtInRegistryHolder()), speed
            )), 1f, 1, true))
            components.set(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY)
            val stack = ItemStack(Items.IRON_PICKAXE.builtInRegistryHolder(), 1, components)
            val progress = ToolSet.calculateSpeedVsBlock(stack, state)
            assertTrue(progress > bareHand, "Tool speed $speed must rank ahead of an empty slot")
            assertTrue(progress < 1.0, "Useful tools do not need to instant-mine stone")
        }
    }
}
