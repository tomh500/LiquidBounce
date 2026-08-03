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
package net.ccbluex.liquidbounce.config.types

import com.google.gson.JsonParser
import com.google.gson.JsonObject
import net.ccbluex.liquidbounce.config.deserializeLeafValue
import net.ccbluex.liquidbounce.config.expandLegacyModeValues
import net.ccbluex.liquidbounce.config.gson.fileGson
import net.ccbluex.liquidbounce.config.gson.interopGson
import net.ccbluex.liquidbounce.config.types.group.Mode
import net.ccbluex.liquidbounce.config.types.group.ModeValueGroup
import net.ccbluex.liquidbounce.config.types.group.ValueGroup
import net.ccbluex.liquidbounce.config.types.list.Tagged
import net.ccbluex.liquidbounce.test.MinecraftBootstrap
import java.util.function.ToIntFunction
import kotlin.test.Test
import kotlin.test.assertEquals

class ValueVisibilityTest {

    companion object {
        init {
            MinecraftBootstrap.ensureInitialized()
        }
    }

    @Test
    fun `visibility only filters interactive options`() {
        var showConditional = false
        val group = ValueGroup("Test").apply {
            boolean("Always", true)
            boolean("Conditional", true).visibleWhen { showConditional }
        }

        fun optionNames() = interopGson.toJsonTree(group).asJsonObject
            .getAsJsonArray("value")
            .map { it.asJsonObject.get("name").asString }

        assertEquals(listOf("Always"), optionNames())

        showConditional = true
        assertEquals(listOf("Always", "Conditional"), optionNames())

        showConditional = false
        val fileNames = fileGson.toJsonTree(group).asJsonObject
            .getAsJsonArray("value")
            .map { it.asJsonObject.get("name").asString }
        assertEquals(listOf("Always", "Conditional"), fileNames)
    }

    @Test
    fun `flattening follows the active mode`() {
        val root = ValueGroup("Test")
        val modes = root.modes(
            eventListener = null,
            name = "Mode",
            activeCallback = ToIntFunction { 1 },
        ) { parent ->
            arrayOf(
                testMode("LiquidBounce", parent),
                testMode("Vape", parent, flattened = true),
            )
        }

        fun isFlattened() = interopGson.toJsonTree(root).asJsonObject
            .getAsJsonArray("value")[0].asJsonObject
            .get("flattened").asBoolean

        assertEquals(true, isFlattened())
        modes.setByString("LiquidBounce")
        assertEquals(false, isFlattened())
    }

    @Test
    fun `choice migrates from legacy mode group`() {
        val group = ValueGroup("Test")
        val choice = group.enumChoice("Mode", TestChoice.LIQUID_BOUNCE)
        group.float("MaxAngle", 180f, 1f..360f)
        val legacyMode = JsonParser.parseString(
            """
            {
              "name": "Mode",
              "active": "Vape",
              "value": [],
              "choices": {
                "Vape": {
                  "name": "Vape",
                  "value": [
                    { "name": "MaxAngle", "value": 45.0 }
                  ]
                }
              }
            }
            """.trimIndent()
        ).asJsonObject

        deserializeLeafValue(choice, legacyMode)

        assertEquals(TestChoice.VAPE, choice.get())

        val valuesByName = mutableMapOf("Mode" to ArrayDeque<JsonObject>().apply { add(legacyMode) })
        expandLegacyModeValues(group.inner, valuesByName)
        assertEquals(45f, valuesByName.getValue("MaxAngle").single()["value"].asFloat)
    }

    private fun testMode(
        name: String,
        parentGroup: ModeValueGroup<Mode>,
        flattened: Boolean = false,
    ) = object : Mode(name) {
        override val parent: ModeValueGroup<Mode> = parentGroup

        init {
            boolean("Option", true)
            if (flattened) {
                flattenOptions()
            }
        }
    }

    private enum class TestChoice(override val tag: String) : Tagged {
        LIQUID_BOUNCE("LiquidBounce"),
        VAPE("Vape"),
    }
}
