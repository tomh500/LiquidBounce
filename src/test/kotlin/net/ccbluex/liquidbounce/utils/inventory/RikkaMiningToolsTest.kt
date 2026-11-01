package net.ccbluex.liquidbounce.utils.inventory

import net.ccbluex.liquidbounce.utils.pathing.RikkaMiningTools
import net.ccbluex.liquidbounce.utils.pathing.RikkaMiningTools.Candidate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RikkaMiningToolsTest {
    private val iron = Candidate(0, true, 0.13, 0, 0, 2, false)
    private val fortuneDiamond = Candidate(1, true, 0.18, 3, 0, 4, false)
    private val stone = Candidate(2, true, 0.09, 0, 0, 1, false)

    @Test
    fun `reserve fortune pick for resources and use stone for clearing`() {
        val tools = listOf(iron, fortuneDiamond, stone)
        assertEquals(1, RikkaMiningTools.choose(tools, true, false)?.slot())
        assertEquals(2, RikkaMiningTools.choose(tools, false, false)?.slot())
    }

    @Test
    fun `unharvestable resource is never accepted regardless of speed`() {
        val wrong = Candidate(0, false, 0.9, 3, 0, 1, false)
        assertNull(RikkaMiningTools.choose(listOf(wrong), true, false))
        assertEquals(1, RikkaMiningTools.choose(listOf(wrong, fortuneDiamond), true, false)?.slot())
    }

    @Test
    fun `silk preference and durability protection precede speed`() {
        val silk = Candidate(3, true, 0.1, 0, 1, 4, false)
        assertEquals(3, RikkaMiningTools.choose(listOf(silk, fortuneDiamond), true, true)?.slot())
        val worn = Candidate(4, true, 0.9, 5, 0, 5, true)
        assertEquals(0, RikkaMiningTools.choose(listOf(worn, iron), true, false)?.slot())
    }

    @Test
    fun `inventory source is selected only when the hotbar cannot harvest the resource`() {
        val hotbar = listOf(Candidate(2, false, 0.07, 0, 0, 1, false))
        val inventory = listOf(Candidate(12, true, 0.13, 0, 0, 2, false))
        assertNull(RikkaMiningTools.choose(hotbar, true, false))
        assertEquals(12, RikkaMiningTools.choose(inventory, true, false)?.slot())
    }
}
