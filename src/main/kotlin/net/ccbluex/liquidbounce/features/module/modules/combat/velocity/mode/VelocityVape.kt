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
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.combat.shouldBeAttacked
import net.ccbluex.liquidbounce.utils.entity.rotation
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3
import kotlin.random.Random

/** Vape-compatible percentage velocity mode. */
internal object VelocityVape : VelocityMode("Vape") {

    init {
        flattenOptions()
    }

    private val chance by float("Chance", 40f, 0f..100f, "%")
    private val horizontal by float("Horizontal", 90f, 0f..100f, "%")
    private val vertical by float("Vertical", 100f, 0f..100f, "%")
    private val ticks by int("Ticks", 1, 0..10, "ticks")
    private val waterCheck by boolean("WaterCheck", false)
    private val onlyWhenTargeting by boolean("OnlyWhenTargeting", false)
    private val kiteMode by boolean("KiteMode", false)
    private val kiteHorizontal by float("KiteHorizontal", 120f, 100f..300f, "%")
        .visibleWhen { kiteMode }
    private val kiteVertical by float("KiteVertical", 120f, 100f..300f, "%")
        .visibleWhen { kiteMode }
    private val alwaysKite by boolean("AlwaysKite", false)
        .visibleWhen { kiteMode }

    private var pendingVelocity: Vec3? = null
    private var ticksRemaining = 0

    private fun nearbyPlayers(): Sequence<Player> = world.players().asSequence().filter {
        it !== player && it.shouldBeAttacked() && it.distanceTo(player) < 6f
    }

    private fun isPlayerFacingOpponent() = nearbyPlayers().any { opponent ->
        player.rotation.directionAngleTo(
            Rotation.lookingAt(opponent.boundingBox.center, player.eyePosition)
        ) < FACING_ANGLE
    }

    private fun isOpponentFacingPlayer() = nearbyPlayers().any { opponent ->
        opponent.rotation.directionAngleTo(
            Rotation.lookingAt(player.boundingBox.center, opponent.eyePosition)
        ) < FACING_ANGLE
    }

    private fun shouldApply(playerFacingOpponent: Boolean, opponentFacingPlayer: Boolean): Boolean {
        if (waterCheck && player.isInWater) return false
        if (onlyWhenTargeting && !playerFacingOpponent && !opponentFacingPlayer && !kiteMode) return false
        return Random.nextDouble(100.0) <= chance
    }

    private fun isKiting(playerFacingOpponent: Boolean, opponentFacingPlayer: Boolean): Boolean {
        return kiteMode && opponentFacingPlayer && (alwaysKite || !playerFacingOpponent)
    }

    private fun reducedScales(): Pair<Double, Double> {
        val jitter = Random.nextDouble()
        val horizontalPercent = if (horizontal > 0f) (horizontal + 5.0 * jitter).coerceAtMost(100.0) else 0.0
        var verticalPercent = if (vertical > 0f) vertical + 5.0 * jitter else 0.0
        if (verticalPercent >= 90.0) verticalPercent = 100.0
        return horizontalPercent / 100.0 to verticalPercent / 100.0
    }

    private fun scaleVelocity(velocity: Vec3, horizontalScale: Double, verticalScale: Double): Vec3 {
        return Vec3(
            velocity.x * horizontalScale,
            velocity.y * verticalScale,
            velocity.z * horizontalScale,
        )
    }

    @Suppress("unused")
    private val packetHandler = handler<PacketEvent> { event ->
        val packet = event.packet as? ClientboundSetEntityMotionPacket ?: return@handler
        if (packet.id != player.id) return@handler

        val playerFacingOpponent = isPlayerFacingOpponent()
        val opponentFacingPlayer = isOpponentFacingPlayer()
        if (!shouldApply(playerFacingOpponent, opponentFacingPlayer)) return@handler

        val velocity = Vec3(
            packet.movement.x / 8000.0,
            packet.movement.y / 8000.0,
            packet.movement.z / 8000.0,
        )

        if (isKiting(playerFacingOpponent, opponentFacingPlayer)) {
            val scaled = scaleVelocity(velocity, kiteHorizontal / 100.0, kiteVertical / 100.0)
            packet.movement.x = scaled.x * 8000.0
            packet.movement.y = scaled.y * 8000.0
            packet.movement.z = scaled.z * 8000.0
            return@handler
        }

        if (ticks > 0) {
            pendingVelocity = velocity
            ticksRemaining = ticks
            return@handler
        }

        val (horizontalScale, verticalScale) = reducedScales()
        val scaled = scaleVelocity(velocity, horizontalScale, verticalScale)
        packet.movement.x = scaled.x * 8000.0
        packet.movement.y = scaled.y * 8000.0
        packet.movement.z = scaled.z * 8000.0
    }

    @Suppress("unused")
    private val tickHandler = handler<GameTickEvent> {
        if (waterCheck && player.isInWater) {
            pendingVelocity = null
            ticksRemaining = 0
            return@handler
        }

        val pending = pendingVelocity ?: return@handler
        if (ticksRemaining-- > 0) return@handler

        val (horizontalScale, verticalScale) = reducedScales()
        val current = player.deltaMovement
        player.deltaMovement = Vec3(
            current.x * horizontalScale,
            if (pending.y != 0.0 && current.y > 0.0) current.y * verticalScale else current.y,
            current.z * horizontalScale,
        )
        pendingVelocity = null
    }

    override fun disable() {
        pendingVelocity = null
        ticksRemaining = 0
        super.disable()
    }

    private const val FACING_ANGLE = 60f
}
