package io.github.mungbean2435.bookvoice.ui.nav

import org.junit.Assert.assertEquals
import org.junit.Test

class SeekableBackTransitionTest {

    @Test
    fun `cancel duration follows the dragged fraction`() {
        assertEquals(120, backCancelMillis(0.1f, 300))
        assertEquals(150, backCancelMillis(0.5f, 300))
        assertEquals(240, backCancelMillis(0.8f, 300))
    }

    @Test
    fun `cancel duration never goes below the floor`() {
        assertEquals(120, backCancelMillis(0f, 300))
        assertEquals(120, backCancelMillis(0.01f, 300))
    }

    @Test
    fun `fraction is clamped`() {
        assertEquals(300, backCancelMillis(1.4f, 300))
        assertEquals(120, backCancelMillis(-1f, 300))
    }
}
