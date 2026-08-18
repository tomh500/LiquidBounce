/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LiquidBounce is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LiquidBounce. If not, see <https://www.gnu.org/licenses/>.
 */
@file:Suppress("MatchingDeclarationName")

package net.ccbluex.liquidbounce.utils.block.placer

import net.ccbluex.liquidbounce.features.global.GlobalVapeRotationSettings
import net.ccbluex.liquidbounce.utils.aiming.RotationManager
import net.ccbluex.liquidbounce.utils.block.targetfinding.BlockPlacementTarget
import net.ccbluex.liquidbounce.utils.client.RestrictedSingleUseAction
import net.minecraft.core.BlockPos

/**
 * Vape BlockPlacer rotation settings. Kept in a separate file so the upstream
 * BlockPlacer sources stay close to the original.
 */
data class VapeBlockPlacerRotation(val speed: Float, val silentAim: Boolean)

/**
 * Submits a Vape-compatible rotation target for BlockPlacer.
 */
internal fun BlockPlacer.setVapeRotationTarget(
    vapeRotation: VapeBlockPlacerRotation,
    pos: BlockPos,
    placementTarget: BlockPlacementTarget,
    whenReached: RestrictedSingleUseAction,
) {
    RotationManager.setRotationTarget(
        GlobalVapeRotationSettings.rotationTarget(
            placementTarget.rotation,
            speed = { vapeRotationSpeed?.invoke(pos, placementTarget) ?: vapeRotation.speed },
            silentAim = vapeRotation.silentAim,
            considerInventory = !ignoreOpenInventory,
            whenReached = whenReached,
        ),
        priority = priority,
        provider = module,
    )
}
