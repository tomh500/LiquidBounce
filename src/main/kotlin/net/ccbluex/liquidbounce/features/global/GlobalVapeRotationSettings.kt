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
package net.ccbluex.liquidbounce.features.global

import net.ccbluex.liquidbounce.config.types.group.ValueGroup
import net.ccbluex.liquidbounce.config.types.list.Tagged
import net.ccbluex.liquidbounce.event.EventListener
import net.ccbluex.liquidbounce.event.events.WorldRenderEvent
import net.ccbluex.liquidbounce.event.events.GameTickEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.modules.combat.ModuleHitbox
import net.ccbluex.liquidbounce.features.module.modules.player.ModuleReach
import net.ccbluex.liquidbounce.features.module.modules.render.ModuleRotations
import net.ccbluex.liquidbounce.render.drawLine
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.render.engine.type.Vec3f
import net.ccbluex.liquidbounce.render.renderEnvironment
import net.ccbluex.liquidbounce.utils.aiming.RotationManager
import net.ccbluex.liquidbounce.utils.aiming.RotationTarget
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.aiming.features.MovementCorrection
import net.ccbluex.liquidbounce.utils.aiming.features.processors.VapeAdaptiveRotationProcessor
import net.ccbluex.liquidbounce.utils.client.RestrictedSingleUseAction
import net.ccbluex.liquidbounce.utils.math.toVec3f
import net.ccbluex.liquidbounce.utils.kotlin.EventPriorityConvention
import kotlin.math.max

/** Shared settings used by Vape-compatible silent rotation implementations. */
object GlobalVapeRotationSettings : ValueGroup("VapeRotations"), EventListener {

    private val movement by enumChoice("Movement", VapeMovement.PROPER)
    val thirdPersonAimView by boolean("3rdPersonAimView", false)
    private val aimIndicator by boolean("AimIndicator", false)
    val useReach by boolean("UseReach", false)
    val useHitboxes by boolean("UseHitboxes", false)

    val movementCorrection: MovementCorrection
        get() = when (movement) {
            VapeMovement.NONE -> MovementCorrection.OFF
            VapeMovement.SLOW -> MovementCorrection.STRICT
            VapeMovement.PROPER -> MovementCorrection.SILENT
        }

    val hasActiveVapeRotation: Boolean
        get() = RotationManager.activeRotationTarget?.vapeCompatible == true

    fun interactionRange(baseRange: Double): Double = if (useReach && ModuleReach.running) {
        max(baseRange, ModuleReach.currentEntityRange.toDouble())
    } else {
        baseRange
    }

    fun hitboxExpansion(): Double = if (useHitboxes && ModuleHitbox.running) {
        ModuleHitbox.size.toDouble()
    } else {
        0.0
    }

    fun rotationTarget(
        rotation: Rotation,
        speed: () -> Float,
        silentAim: Boolean,
        considerInventory: Boolean = true,
        whenReached: RestrictedSingleUseAction? = null,
    ) = RotationTarget(
        rotation = rotation,
        processors = listOf(VapeAdaptiveRotationProcessor(speed)),
        ticksUntilReset = 2,
        resetThreshold = 2f,
        considerInventory = considerInventory,
        movementCorrection = if (silentAim) movementCorrection else MovementCorrection.CHANGE_LOOK,
        whenReached = whenReached,
        vapeCompatible = true,
    )

    @Suppress("unused")
    private val modelUpdater = handler<GameTickEvent>(priority = EventPriorityConvention.READ_FINAL_STATE) {
        if (ModuleRotations.running) return@handler

        if (thirdPersonAimView && hasActiveVapeRotation) {
            ModuleRotations.updateModelRotation()
        } else if (ModuleRotations.modelRotation != null) {
            ModuleRotations.clearModelRotation()
        }
    }

    @Suppress("unused")
    private val renderHandler = handler<WorldRenderEvent> { event ->
        if (!aimIndicator || !hasActiveVapeRotation) {
            return@handler
        }

        val current = RotationManager.currentRotation ?: return@handler
        val previous = RotationManager.previousRotation ?: current
        val direction = previous.directionVector
            .lerp(current.directionVector, event.partialTicks.toDouble())
            .toVec3f()
        val eye = Vec3f.eyeVector(event.camera)

        event.renderEnvironment {
            drawLine(eye, eye.fma(100f, direction), Color4b.WHITE.argb)
        }
    }

    private enum class VapeMovement(override val tag: String) : Tagged {
        NONE("None"),
        SLOW("Slow"),
        PROPER("Proper"),
    }
}
