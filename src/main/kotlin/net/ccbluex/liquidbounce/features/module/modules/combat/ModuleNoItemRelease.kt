package net.ccbluex.liquidbounce.features.module.modules.combat

import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.features.module.ModuleOrigin
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket

/** Keeps an item-use action active by filtering the client release packet. */
object ModuleNoItemRelease : ClientModule(
    "NoItemRelease",
    ModuleCategories.COMBAT,
    aliases = listOf("BlockHit"),
    origin = ModuleOrigin.XUAN_RIKKA,
) {
    private val onlyWhileUsing by boolean("OnlyWhileUsing", true)
    private val allowInScreens by boolean("AllowInScreens", false)

    @Suppress("unused")
    private val packetHandler = handler<PacketEvent> { event ->
        val packet = event.packet as? ServerboundPlayerActionPacket ?: return@handler
        if (packet.action != ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM) {
            return@handler
        }
        if (!allowInScreens && mc.gui.screen() != null) {
            return@handler
        }
        if (!onlyWhileUsing || player.isUsingItem) {
            event.cancelEvent()
        }
    }
}
