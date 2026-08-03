/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package net.ccbluex.liquidbounce.features.module.modules.combat

import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.utils.clicking.Clicker
import net.ccbluex.liquidbounce.utils.collection.itemSortedSetOf
import net.ccbluex.liquidbounce.utils.input.InputTracker.isPressedOnAny
import net.minecraft.client.KeyMapping

/**
 * Right-click autoclicker compatible with the Vape RightClicker layout.
 * The click timing itself is handled by LB's Clicker scheduler.
 */
object ModuleRightClicker : ClientModule(
    "RightClicker",
    ModuleCategories.COMBAT,
    aliases = listOf("RightClick")
) {

    private val holdToClick by boolean("HoldToClick", true)
    private val startDelay by int("StartDelay", 0, 0..1000, "ms")
    private val useItemWhitelist by boolean("UseItemWhitelist", false)
    private val itemWhitelist by items("ItemWhitelist", itemSortedSetOf())
    private val clicker = tree(Clicker(this, mc.options.keyUse, itemCooldown = null))

    private var activationStartedAt = 0L

    override fun onEnabled() {
        activationStartedAt = 0L
    }

    @Suppress("unused")
    private val clickHandler = tickHandler {
        if (mc.gui.screen() != null) {
            activationStartedAt = 0L
            return@tickHandler
        }

        if (holdToClick && !mc.options.keyUse.isPressedOnAny) {
            activationStartedAt = 0L
            return@tickHandler
        }

        if (useItemWhitelist &&
            player.mainHandItem.item !in itemWhitelist &&
            player.offhandItem.item !in itemWhitelist
        ) {
            activationStartedAt = 0L
            return@tickHandler
        }

        if (activationStartedAt == 0L) {
            activationStartedAt = System.currentTimeMillis()
        }

        if (System.currentTimeMillis() - activationStartedAt < startDelay) {
            return@tickHandler
        }

        clicker.click {
            KeyMapping.click(mc.options.keyUse.key)
            true
        }
    }
}
