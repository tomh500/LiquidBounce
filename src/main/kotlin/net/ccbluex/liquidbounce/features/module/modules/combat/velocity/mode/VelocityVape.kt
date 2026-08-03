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
package net.ccbluex.liquidbounce.features.module.modules.combat.velocity.mode

import net.ccbluex.liquidbounce.event.events.GameTickEvent
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.modules.combat.killaura.ModuleKillAura
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket
import net.minecraft.world.phys.Vec3
import kotlin.random.Random

/** Vape-compatible percentage velocity mode. */
internal object VelocityVape : VelocityMode("Vape") {

    private val chance by float("Chance", 100f, 0f..100f, "%")
    private val horizontal by float("Horizontal", 90f, 0f..100f, "%")
    private val vertical by float("Vertical", 100f, 0f..100f, "%")
    private val ticks by int("Ticks", 0, 0..10, "ticks")
    private val waterCheck by boolean("WaterCheck", false)
    private val onlyWhenTargeting by boolean("OnlyWhenTargeting", false)
    private val kiteMode by boolean("KiteMode", false)
    private val kiteHorizontal by float("KiteHorizontal", 120f, 100f..300f, "%")
    private val kiteVertical by float("KiteVertical", 120f, 100f..300f, "%")
    private val alwaysKite by boolean("AlwaysKite", false)

    private var pendingVelocity: Vec3? = null
    private var ticksRemaining = 0

    private fun shouldApply(): Boolean {
        if (waterCheck && (player.isInWater || player.isInLava)) return false
        if (onlyWhenTargeting && ModuleKillAura.targetTracker.target == null) return false
        return Random.nextFloat() * 100f < chance
    }

    private fun isKiting(): Boolean {
        return kiteMode && (alwaysKite || ModuleKillAura.targetTracker.target != null)
    }

    private fun scaleVelocity(velocity: Vec3): Vec3 {
        val horizontalScale = (if (isKiting()) kiteHorizontal else horizontal) / 100.0
        val verticalScale = (if (isKiting()) kiteVertical else vertical) / 100.0
        return Vec3(
            velocity.x * horizontalScale,
            velocity.y * verticalScale,
            velocity.z * horizontalScale,
        )
    }

    @Suppress("unused")
    private val packetHandler = handler<PacketEvent> { event ->
        val packet = event.packet as? ClientboundSetEntityMotionPacket ?: return@handler
        if (packet.id != player.id || !shouldApply()) return@handler

        val velocity = Vec3(
            packet.movement.x / 8000.0,
            packet.movement.y / 8000.0,
            packet.movement.z / 8000.0,
        )

        if (ticks > 0 && !isKiting()) {
            event.cancelEvent()
            pendingVelocity = velocity
            ticksRemaining = ticks
            return@handler
        }

        val scaled = scaleVelocity(velocity)
        packet.movement.x = scaled.x * 8000.0
        packet.movement.y = scaled.y * 8000.0
        packet.movement.z = scaled.z * 8000.0
    }

    @Suppress("unused")
    private val tickHandler = handler<GameTickEvent> {
        val pending = pendingVelocity ?: return@handler
        if (--ticksRemaining > 0) return@handler

        val scaled = scaleVelocity(pending)
        val current = player.deltaMovement
        player.deltaMovement = Vec3(
            current.x + scaled.x,
            current.y + scaled.y,
            current.z + scaled.z,
        )
        pendingVelocity = null
    }

    override fun disable() {
        pendingVelocity = null
        ticksRemaining = 0
        super.disable()
    }
}
