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
package net.ccbluex.liquidbounce.features.module.modules.world.scaffold.vape.movement

import kotlin.math.abs

/** Direct compatibility port of Vape 4.21's TargetPositionMovementTask. */
internal class TargetPositionMovementTask(
    var targetX: Double,
    var targetZ: Double,
) : PlayerMovementTask() {

    override fun hasReachedTarget(): Boolean {
        remainingX = if (ignoreX) 0.0 else targetX - player.x
        remainingZ = if (ignoreZ) 0.0 else targetZ - player.z
        val tolerance = if (sneakNearTarget) 0.1 else completionTolerance
        return abs(remainingX) <= tolerance && abs(remainingZ) <= tolerance
    }
}
