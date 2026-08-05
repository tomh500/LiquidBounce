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

import net.ccbluex.liquidbounce.utils.movement.DirectionalInput

/** Scaffold-owned port of Vape 4.21's PlayerMovementTaskManager. */
internal class PlayerMovementTaskManager {
    var activeTask: PlayerMovementTask? = null
        private set

    fun reset() {
        activeTask = null
    }

    fun submit(task: PlayerMovementTask) {
        activeTask = task
    }

    fun updateCompletion(): PlayerMovementTask? {
        val task = activeTask ?: return null
        if (!task.updateCompletion()) return null
        activeTask = null
        return task
    }

    fun applyMovementInput(): DirectionalInput {
        val task = activeTask ?: return DirectionalInput.NONE
        return task.applyMovementInput()
    }
}
