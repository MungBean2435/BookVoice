package io.github.mungbean2435.bookvoice.ui.nav

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackGateTest {

    private var now = 0L

    private fun gate(windowMs: Long = 350L) = BackGate(windowMs = windowMs, clock = { now })

    @Test
    fun `first back is handled`() {
        val gate = gate()
        assertTrue(gate.tryHandle())
    }

    @Test
    fun `rapid second back inside transition window is swallowed`() {
        val gate = gate()
        assertTrue(gate.tryHandle())
        now = 100
        assertFalse(gate.tryHandle())
        now = 349
        assertFalse(gate.tryHandle())
    }

    @Test
    fun `back after transition window is handled again`() {
        val gate = gate()
        assertTrue(gate.tryHandle())
        now = 350
        assertTrue(gate.tryHandle())
    }

    @Test
    fun `swallowing does not extend the window`() {
        val gate = gate()
        assertTrue(gate.tryHandle())
        now = 300
        assertFalse(gate.tryHandle())
        now = 360
        assertTrue(gate.tryHandle())
    }
}
