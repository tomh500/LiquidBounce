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
package net.ccbluex.liquidbounce.features.module.modules.world.scaffold

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VapeScaffoldGeometryTest {

    @Test
    fun `telly negative directions use the trailing edge`() {
        assertFalse(VapeTellyBridgeScaffoldMode.hasReachedPathEdge(8, 9.9, 0.0, 10, 0))
        assertTrue(VapeTellyBridgeScaffoldMode.hasReachedPathEdge(8, 9.85, 0.0, 10, 0))

        assertFalse(VapeTellyBridgeScaffoldMode.hasReachedPathEdge(5, 0.0, 9.9, 0, 10))
        assertTrue(VapeTellyBridgeScaffoldMode.hasReachedPathEdge(5, 0.0, 9.85, 0, 10))
    }

    @Test
    fun `telly fourth level-one path position rises vertically`() {
        val anchor = BlockPos(4, 63, -2)

        assertEquals(
            anchor.above(),
            VapeTellyBridgeScaffoldMode.nextPathPosition(anchor, pathSize = 4, bridgeLevel = 1, direction = 6),
        )
        assertEquals(
            anchor.east(),
            VapeTellyBridgeScaffoldMode.nextPathPosition(anchor, pathSize = 5, bridgeLevel = 1, direction = 6),
        )
    }

    @Test
    fun `god bridge placement face follows Vape motion gate`() {
        assertTrue(VapeGodBridgeScaffoldMode.isPlacementFaceValid(0.0, false, Direction.NORTH))
        assertFalse(VapeGodBridgeScaffoldMode.isPlacementFaceValid(0.0, false, Direction.UP))
        assertTrue(VapeGodBridgeScaffoldMode.isPlacementFaceValid(0.11, false, Direction.UP))
        assertTrue(VapeGodBridgeScaffoldMode.isPlacementFaceValid(0.0, true, Direction.UP))
        assertFalse(VapeGodBridgeScaffoldMode.isPlacementFaceValid(0.11, false, Direction.DOWN))
    }
}
