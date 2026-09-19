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
package net.ccbluex.liquidbounce.utils.aiming.features.processors

import net.ccbluex.liquidbounce.utils.aiming.RotationTarget
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.aiming.utils.RotationUtil
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VapeAdaptiveRotationProcessorTest {

    @Test
    fun `one tick rescue reaches large turns despite controller speed cap`() {
        for (yaw in listOf(90f, 180f, -180f)) {
            val target = Rotation(yaw, 80f)
            val processor = VapeAdaptiveRotationProcessor(speed = { 3.5f }, ticksUntilTarget = { 1 })
            val result = processor.process(RotationTarget(target), Rotation.ZERO, target)

            assertEquals(0f, RotationUtil.angleDifference(target.yaw, result.yaw), 0.0001f)
            assertEquals(target.pitch, result.pitch, 0.0001f)
        }
    }

    @Test
    fun `both axes arrive before the predicted impact`() {
        var remainingTicks = 5
        val target = Rotation(-160f, 75f)
        val processor = VapeAdaptiveRotationProcessor(
            speed = { 3.5f },
            ticksUntilTarget = { remainingTicks },
        )
        var current = Rotation.ZERO
        while (remainingTicks > 0) {
            val next = processor.process(RotationTarget(target), current, target)
            assertTrue(next.yaw in target.yaw..current.yaw)
            assertTrue(next.pitch in current.pitch..target.pitch)
            current = next
            remainingTicks--
        }

        assertEquals(target.yaw, current.yaw, 0.0001f)
        assertEquals(target.pitch, current.pitch, 0.0001f)
    }

    @Test
    fun `ordinary Vape rotations retain the previous speed and axis scaling`() {
        val target = Rotation(90f, 45f)
        val processor = VapeAdaptiveRotationProcessor(speed = { 3.5f })
        val result = processor.process(RotationTarget(target), Rotation.ZERO, target)

        assertEquals(5.375f, result.yaw, 0.0001f)
        assertEquals(2.6875f, result.pitch, 0.0001f)
    }

    @Test
    fun `deadline keeps faster configured turns`() {
        val target = Rotation(90f, 45f)
        val ordinary = VapeAdaptiveRotationProcessor(speed = { 20f })
        val deadline = VapeAdaptiveRotationProcessor(speed = { 20f }, ticksUntilTarget = { 100 })
        val request = RotationTarget(target)

        assertEquals(
            ordinary.process(request, Rotation.ZERO, target),
            deadline.process(request, Rotation.ZERO, target),
        )
    }

    @Test
    fun `expired deadline uses shortest yaw path without overshooting either axis`() {
        val current = Rotation(179f, 79.95f)
        val target = Rotation(-179f, 80f)
        val processor = VapeAdaptiveRotationProcessor(speed = { 100f }, ticksUntilTarget = { 0 })
        val result = processor.process(RotationTarget(target), current, target)

        assertEquals(181f, result.yaw, 0.0001f)
        assertEquals(80f, result.pitch, 0.0001f)
    }
}
