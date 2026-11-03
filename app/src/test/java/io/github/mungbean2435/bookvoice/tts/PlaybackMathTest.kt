package io.github.mungbean2435.bookvoice.tts

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackMathTest {

    @Test
    fun thousandCharsIsAboutThreeMinutes() {
        // 1000 字 × 196ms ≈ 196 秒
        assertEquals(196L, PlaybackMath.secondsForChars(1000))
    }

    @Test
    fun zeroCharsIsZeroSeconds() {
        assertEquals(0L, PlaybackMath.secondsForChars(0))
    }

    @Test
    fun negativeCharsClampsToZero() {
        assertEquals(0L, PlaybackMath.secondsForChars(-10))
    }

    @Test
    fun formatsUnderOneHour() {
        assertEquals("3:16", PlaybackMath.formatDuration(196))
        assertEquals("0:05", PlaybackMath.formatDuration(5))
    }

    @Test
    fun formatsOverOneHour() {
        assertEquals("1:00:05", PlaybackMath.formatDuration(3605))
    }
}
