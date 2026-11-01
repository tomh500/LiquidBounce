package net.ccbluex.liquidbounce.features.module.modules.combat

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TriggerbotFirstHitGateTest {
    @Test
    fun `waits for local damage and keeps permission for the same target`() {
        val gate = TriggerbotFirstHitGate()
        gate.observeDamage(0)
        assertFalse(gate.permits(1, true))
        gate.observeDamage(10)
        assertTrue(gate.permits(1, true))
        gate.observeDamage(0)
        assertTrue(gate.permits(1, true))
        assertFalse(gate.permits(2, true))
    }

    @Test
    fun `ongoing damage does not grant permission to a newly selected target`() {
        val gate = TriggerbotFirstHitGate()
        gate.observeDamage(10)
        gate.observeDamage(9)
        assertFalse(gate.permits(1, true))
        gate.observeDamage(0)
        gate.observeDamage(10)
        assertTrue(gate.permits(1, true))
    }

    @Test
    fun `disabling first hit immediately releases an existing wait`() {
        val gate = TriggerbotFirstHitGate()
        assertFalse(gate.permits(1, true))
        assertTrue(gate.permits(1, false))
        assertFalse(gate.permits(1, true))
    }

    @Test
    fun `enabling during existing damage does not count it as a new hit`() {
        val gate = TriggerbotFirstHitGate()
        gate.reset(10)
        gate.observeDamage(9)
        assertFalse(gate.permits(1, true))
    }
}
