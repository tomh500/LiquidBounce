package net.ccbluex.liquidbounce.utils.inventory

import net.ccbluex.liquidbounce.event.EventListener
import net.ccbluex.liquidbounce.event.EventManager
import net.ccbluex.liquidbounce.event.events.WorldChangeEvent
import net.ccbluex.liquidbounce.test.MinecraftBootstrap
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertSame

class AnchoredHotbarSwapControllerTest {
    @Test
    fun `disconnect discards pending inventory swap even when owner is inactive`() {
        MinecraftBootstrap.ensureInitialized()
        val owner = object : EventListener {
            override val running = false
        }
        val controller = AnchoredHotbarSwapController(owner, InventoryConstraints(), { 20 })
        try {
            val source = HotbarItemSlot(2)
            controller.requestSwapFromInventory(source)
            // Inspect the queued request without requiring a live inventory or sending packets.
            val request = controller.javaClass.getDeclaredField("requestedSourceSlot").apply {
                isAccessible = true
            }
            assertSame(source, request.get(controller))

            EventManager.callEvent(WorldChangeEvent(null))

            assertNull(request.get(controller), "A previous-world swap must never reach the new inventory")
        } finally {
            controller.unregister()
        }
    }
}
