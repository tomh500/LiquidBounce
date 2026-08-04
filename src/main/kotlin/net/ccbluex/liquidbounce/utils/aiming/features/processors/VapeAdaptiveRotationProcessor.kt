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
import kotlin.math.abs
import kotlin.math.sign

/** New-version equivalent of Vape's fixed/adaptive rotation step. */
class VapeAdaptiveRotationProcessor(
    private val speed: () -> Float,
    private val linearAcceleration: Boolean = true,
    private val scaleAxesProportionally: Boolean = true,
) : RotationProcessor {

    override fun process(
        rotationTarget: RotationTarget,
        currentRotation: Rotation,
        targetRotation: Rotation,
    ): Rotation {
        val yawError = RotationUtil.angleDifference(targetRotation.yaw, currentRotation.yaw)
        val pitchError = RotationUtil.angleDifference(targetRotation.pitch, currentRotation.pitch)
        val absoluteYawError = abs(yawError)
        val absolutePitchError = abs(pitchError)
        val baseStep = speed().coerceIn(1f, 25f) * 0.25f

        fun step(error: Float, otherError: Float): Float {
            if (error == 0f) return 0f

            var amount = baseStep
            if (scaleAxesProportionally && otherError > 0f && error < otherError) {
                amount *= error / otherError
            }
            if (linearAcceleration) {
                amount += error * 0.05f
            }
            return amount.coerceAtMost(error)
        }

        return Rotation(
            currentRotation.yaw + step(absoluteYawError, absolutePitchError) * sign(yawError),
            currentRotation.pitch + step(absolutePitchError, absoluteYawError) * sign(pitchError),
        )
    }
}
