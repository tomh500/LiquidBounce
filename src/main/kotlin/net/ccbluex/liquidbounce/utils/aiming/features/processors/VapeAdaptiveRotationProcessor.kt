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
import kotlin.math.roundToInt
import kotlin.math.sign

class VapeMouseRotationState {
    var pendingYawDelta = 0f
        internal set
    var pendingPitchDelta = 0f
        internal set

    fun reset() {
        pendingYawDelta = 0f
        pendingPitchDelta = 0f
    }
}

/** New-version equivalent of Vape's fixed/adaptive rotation step. */
class VapeAdaptiveRotationProcessor(
    private val speed: () -> Float,
    private val linearAcceleration: Boolean = true,
    private val scaleAxesProportionally: Boolean = true,
    private val emulateMouseController: Boolean = false,
    private val mouseRotationState: VapeMouseRotationState = VapeMouseRotationState(),
    private val tolerance: Float = 0f,
) : RotationProcessor {

    override fun process(
        rotationTarget: RotationTarget,
        currentRotation: Rotation,
        targetRotation: Rotation,
    ): Rotation {
        if (emulateMouseController) {
            return processMouseController(currentRotation, targetRotation)
        }

        val yawError = RotationUtil.angleDifference(targetRotation.yaw, currentRotation.yaw)
        val pitchError = RotationUtil.angleDifference(targetRotation.pitch, currentRotation.pitch)
        val absoluteYawError = abs(yawError)
        val absolutePitchError = abs(pitchError)
        // Vape clamps its controller speed at 100 before converting it into a 0.25x mouse step.
        // Clutch relies on this upper range to finish a landing-timed rotation.
        val baseStep = speed().coerceIn(1f, 100f) * 0.25f

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

    /**
     * Vape advances Scaffold's mouse controller around 50 times per game tick and converts each
     * controller step through the active mouse sensitivity. A single degree step per game tick is
     * substantially slower and does not reach the placement face before the bridge jump.
     */
    private fun processMouseController(currentRotation: Rotation, targetRotation: Rotation): Rotation {
        val rotationPerMouseStep = RotationUtil.gcd.toFloat()
        if (rotationPerMouseStep <= 0f) return targetRotation

        val mouseScale = rotationPerMouseStep / MOUSE_TURN_SCALE
        val updateCount = (REFERENCE_UPDATES_PER_TICK / mouseScale).roundToInt()
            .coerceIn(1, MAX_UPDATES_PER_TICK)
        val baseStep = speed().coerceIn(2f, 12f) * 0.25f

        var yaw = currentRotation.yaw
        var pitch = currentRotation.pitch

        repeat(updateCount) {
            fun predictedYaw() = yaw + mouseRotationState.pendingYawDelta.toInt() * rotationPerMouseStep
            fun predictedPitch() = pitch + mouseRotationState.pendingPitchDelta.toInt() * rotationPerMouseStep
            fun isOutsideTolerance(error: Float): Boolean =
                (error / rotationPerMouseStep).roundToInt() >
                    (tolerance / rotationPerMouseStep).roundToInt().coerceAtLeast(0)

            fun mouseStep(error: Float, otherError: Float): Float {
                var amount = baseStep
                if (scaleAxesProportionally && otherError > 0f && error < otherError) {
                    amount *= error / otherError
                }
                if (linearAcceleration) {
                    amount += error * 0.05f
                }
                return amount.coerceAtMost(error / rotationPerMouseStep)
            }

            val yawError = RotationUtil.angleDifference(targetRotation.yaw, predictedYaw())
            val absoluteYawError = abs(yawError)
            if (isOutsideTolerance(absoluteYawError)) {
                val pitchError = RotationUtil.angleDifference(targetRotation.pitch, predictedPitch())
                mouseRotationState.pendingYawDelta +=
                    mouseStep(absoluteYawError, abs(pitchError)) * sign(yawError)
            }

            val pitchError = RotationUtil.angleDifference(targetRotation.pitch, predictedPitch())
            val absolutePitchError = abs(pitchError)
            if (isOutsideTolerance(absolutePitchError)) {
                val updatedYawError = RotationUtil.angleDifference(targetRotation.yaw, predictedYaw())
                mouseRotationState.pendingPitchDelta +=
                    mouseStep(absolutePitchError, abs(updatedYawError)) * sign(pitchError)
            }

            val yawSteps = mouseRotationState.pendingYawDelta.toInt()
            val pitchSteps = mouseRotationState.pendingPitchDelta.toInt()
            yaw += yawSteps * rotationPerMouseStep
            pitch += pitchSteps * rotationPerMouseStep
            mouseRotationState.pendingYawDelta -= yawSteps
            mouseRotationState.pendingPitchDelta -= pitchSteps
        }

        return Rotation(yaw, pitch.coerceIn(-90f, 90f))
    }

    private companion object {
        const val MOUSE_TURN_SCALE = 0.15f
        const val REFERENCE_UPDATES_PER_TICK = 50f
        const val MAX_UPDATES_PER_TICK = 1024
    }
}
