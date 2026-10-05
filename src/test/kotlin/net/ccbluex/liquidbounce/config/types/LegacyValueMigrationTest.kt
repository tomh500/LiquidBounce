package net.ccbluex.liquidbounce.config.types

import com.google.gson.JsonParser
import net.ccbluex.liquidbounce.config.deserializeLeafValue
import net.ccbluex.liquidbounce.config.types.group.ValueGroup
import net.ccbluex.liquidbounce.config.types.list.Tagged
import net.ccbluex.liquidbounce.test.MinecraftBootstrap
import kotlin.test.Test
import kotlin.test.assertEquals

class LegacyValueMigrationTest {
    companion object {
        init {
            MinecraftBootstrap.ensureInitialized()
        }
    }

    private enum class Paint(override val tag: String) : Tagged {
        AUTO("Auto"), ON("On"), OFF("Off")
    }

    private fun load(value: Value<*>, serialized: String) {
        deserializeLeafValue(value, JsonParser.parseString(serialized).asJsonObject)
    }

    @Test
    fun `old acceleration booleans preserve explicit on and off preferences`() {
        val value = ValueGroup("GuiRenderer").enumChoice("AcceleratedPaint", Paint.AUTO)
        load(value, """{"value":true}""")
        assertEquals(Paint.ON, value.get())
        load(value, """{"value":false}""")
        assertEquals(Paint.OFF, value.get())
        load(value, """{"value":"Auto"}""")
        assertEquals(Paint.AUTO, value.get())
    }

    @Test
    fun `scalar pitch migrates to a fixed floating range`() {
        val value = ValueGroup("Rotations").floatRange("Pitch", 60f..80f, 0f..90f)
        load(value, """{"value":70.0}""")
        assertEquals(70f..70f, value.get())
        load(value, """{"value":{"from":62.0,"to":78.0}}""")
        assertEquals(62f..78f, value.get())
    }

    @Test
    fun `scalar integer delay migrates without changing modern intervals`() {
        val value = ValueGroup("Timing").intRange("Delay", 1..4, 0..20)
        load(value, """{"value":5}""")
        assertEquals(5..5, value.get())
        load(value, """{"value":{"from":2,"to":6}}""")
        assertEquals(2..6, value.get())
    }

    @Test
    fun `ordinary booleans and numeric parameters keep their original shapes`() {
        val group = ValueGroup("Settings")
        val enabled = group.boolean("Enabled", false)
        val speed = group.float("Speed", 1f, 0f..10f)
        load(enabled, """{"value":true}""")
        load(speed, """{"value":2.5}""")
        assertEquals(true, enabled.get())
        assertEquals(2.5f, speed.get())
    }
}
