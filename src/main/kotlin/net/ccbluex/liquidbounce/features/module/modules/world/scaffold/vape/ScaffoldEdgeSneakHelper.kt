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
package net.ccbluex.liquidbounce.features.module.modules.world.scaffold.vape

import net.ccbluex.liquidbounce.event.events.MovementInputEvent
import net.ccbluex.liquidbounce.features.module.MinecraftShortcuts
import net.ccbluex.liquidbounce.utils.entity.isCloseToEdge
import net.ccbluex.liquidbounce.utils.movement.DirectionalInput
import kotlin.random.Random

/** Movement-event adaptation of Vape 4.21's ScaffoldEdgeSneakHelper. */
internal class ScaffoldEdgeSneakHelper : MinecraftShortcuts {
    private var sneakUntilTick = 0

    fun reset() {
        sneakUntilTick = 0
    }

    fun apply(event: MovementInputEvent) {
        val physicalInput = DirectionalInput(mc.options)
        val shouldSneak = player.onGround() && !physicalInput.forwards &&
            player.isCloseToEdge(event.directionalInput, distance = 0.2)

        if (shouldSneak) {
            // Vape's default 100-500 ms release delay is 2-10 game ticks.
            sneakUntilTick = player.tickCount + Random.nextInt(2, 11)
        }
        if (shouldSneak || player.tickCount < sneakUntilTick) {
            event.sneak = true
        }
    }
}
