package io.github.mungbean2435.bookvoice.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TapPolicyTest {

    @Test
    fun hiddenAlwaysShowsChrome() {
        assertEquals(TapAction.ShowChrome, tapAction(chromeVisible = false, tapToRead = true, onText = true))
        assertEquals(TapAction.ShowChrome, tapAction(chromeVisible = false, tapToRead = false, onText = false))
    }

    @Test
    fun visibleWithTapToReadReadsOnlyOnText() {
        assertEquals(TapAction.ReadBlock, tapAction(chromeVisible = true, tapToRead = true, onText = true))
        assertEquals(TapAction.HideChrome, tapAction(chromeVisible = true, tapToRead = true, onText = false))
    }

    @Test
    fun visibleWithoutTapToReadAlwaysHides() {
        assertEquals(TapAction.HideChrome, tapAction(chromeVisible = true, tapToRead = false, onText = true))
        assertEquals(TapAction.HideChrome, tapAction(chromeVisible = true, tapToRead = false, onText = false))
    }

    @Test
    fun tapZoneSplitsEdgesAtEighteenPercent() {
        val width = 1000
        assertEquals(ReadingTapZone.LeftEdge, readingTapZone(0f, width))
        assertEquals(ReadingTapZone.LeftEdge, readingTapZone(179f, width))
        assertEquals(ReadingTapZone.Center, readingTapZone(180f, width))
        assertEquals(ReadingTapZone.Center, readingTapZone(500f, width))
        assertEquals(ReadingTapZone.Center, readingTapZone(820f, width))
        assertEquals(ReadingTapZone.RightEdge, readingTapZone(821f, width))
        assertEquals(ReadingTapZone.RightEdge, readingTapZone(1000f, width))
    }

    @Test
    fun tapZoneFallsBackToCenterWhenWidthOrFractionIsInvalid() {
        assertEquals(ReadingTapZone.Center, readingTapZone(0f, 0))
        assertEquals(ReadingTapZone.Center, readingTapZone(0f, 100, edgeFraction = 0f))
        assertEquals(ReadingTapZone.LeftEdge, readingTapZone(0f, 100, edgeFraction = 0.8f))
        assertEquals(ReadingTapZone.Center, readingTapZone(50f, 100, edgeFraction = 0.8f))
        assertEquals(ReadingTapZone.RightEdge, readingTapZone(51f, 100, edgeFraction = 0.8f))
    }

    @Test
    fun swipeHidesInBothDirections() {
        assertFalse(chromeSwipeHides(23f, 24f))
        assertTrue(chromeSwipeHides(25f, 24f))
        assertTrue(chromeSwipeHides(-25f, 24f))
        assertFalse(chromeSwipeHides(-23f, 24f))
        assertFalse(chromeSwipeHides(100f, 0f))
    }
}
