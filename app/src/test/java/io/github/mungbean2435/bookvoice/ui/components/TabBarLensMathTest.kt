package io.github.mungbean2435.bookvoice.ui.components

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TabBarLensMathTest {

    // 3x 密度下的真实数值：bar 358dp × 64dp、左右 4dp 内边距、三个等分 tab。
    private val barWidthPx = 1074f
    private val barHeightPx = 192f
    private val insetPx = 12f
    private val tabWidthPx = (barWidthPx - insetPx * 2f) / 3f
    private val restWidthPx = tabWidthPx - insetPx * 2f
    private val restHeightPx = barHeightPx - insetPx * 2f

    private fun tabCenter(index: Int): Float = insetPx + tabWidthPx * (index + 0.5f)

    private fun frameAt(
        centerX: Float,
        press: Float = 0f,
        velocity: Float = 0f,
        marginPx: Float = insetPx
    ) = lensFrame(
        centerX = centerX,
        barWidthPx = barWidthPx,
        barHeightPx = barHeightPx,
        restWidthPx = restWidthPx,
        restHeightPx = restHeightPx,
        press = press,
        velocity = velocity,
        marginPx = marginPx
    )

    private fun assertInside(rect: Rect, marginPx: Float) {
        assertTrue("left=${rect.left}", rect.left >= marginPx - 0.01f)
        assertTrue("top=${rect.top}", rect.top >= marginPx - 0.01f)
        assertTrue("right=${rect.right}", rect.right <= barWidthPx - marginPx + 0.01f)
        assertTrue("bottom=${rect.bottom}", rect.bottom <= barHeightPx - marginPx + 0.01f)
    }

    @Test
    fun insideTravelRangeKeepsPointerPosition() {
        assertEquals(120f, rubberBandXIn(120f, 50f, 300f, 390f), 0.001f)
    }

    @Test
    fun overscrollIsDamped() {
        val start = 50f
        val pulled = rubberBandXIn(start - 100f, start, 300f, 390f)
        assertTrue(pulled < start)
        assertTrue(pulled > start - 100f)
    }

    @Test
    fun overscrollNeverExceedsSixPercentOfBar() {
        val start = 50f
        val width = 390f
        val pulled = rubberBandXIn(-10_000f, start, 300f, width)
        val maxOvershoot = width * LensRubberBandFraction
        assertTrue(pulled >= start - maxOvershoot)
    }

    @Test
    fun rightOverscrollIsMirrored() {
        val end = 300f
        val pulled = rubberBandXIn(10_000f, 50f, end, 390f)
        assertTrue(pulled > end)
        assertTrue(pulled <= end + 390f * LensRubberBandFraction)
    }

    @Test
    fun nearestIndexUsesTabCenters() {
        val centers = listOf(0 to 50f, 1 to 150f, 2 to 250f)
        assertEquals(0, nearestIndexIn(centers, 40f))
        assertEquals(1, nearestIndexIn(centers, 140f))
        assertEquals(2, nearestIndexIn(centers, 260f))
        assertEquals(-1, nearestIndexIn(emptyList(), 100f))
    }

    @Test
    fun pressScaleGrowsFromOneToThirtyFourPercent() {
        assertEquals(1f, lensPressScale(0f), 0.0001f)
        assertEquals(1.34f, lensPressScale(1f), 0.0001f)
        assertEquals(1.34f, lensPressScale(2f), 0.0001f)
    }

    @Test
    fun magnificationGrowsFromOneToTwentyPercent() {
        assertEquals(1f, lensMagnification(0f), 0.0001f)
        assertEquals(1.2f, lensMagnification(1f), 0.0001f)
    }

    @Test
    fun squashIsBoundedAtFourteenAndSixPercent() {
        val fast = lensSquash(velocityPx = 1_000_000f, referenceWidthPx = 390f)
        assertTrue(fast.stretch <= 1.14f)
        assertTrue(fast.squash >= 0.94f)

        val rest = lensSquash(velocityPx = 0f, referenceWidthPx = 390f)
        assertEquals(1f, rest.stretch, 0.0001f)
        assertEquals(1f, rest.squash, 0.0001f)
    }

    @Test
    fun restFrameKeepsTabInsetOnEveryTab() {
        val first = frameAt(tabCenter(0))!!.rect
        assertEquals(tabCenter(0) - restWidthPx / 2f, first.left, 0.01f)
        assertEquals(insetPx, first.top, 0.01f)
        assertEquals(barHeightPx - insetPx, first.bottom, 0.01f)
        assertInside(first, insetPx)

        val middle = frameAt(tabCenter(1))!!.rect
        assertInside(middle, insetPx)

        val last = frameAt(tabCenter(2))!!.rect
        assertEquals(tabCenter(2) + restWidthPx / 2f, last.right, 0.01f)
        assertInside(last, insetPx)
    }

    @Test
    fun pressedLensNeverLeavesCapsule() {
        val velocities = listOf(-1_000_000f, -4000f, 0f, 4000f, 1_000_000f)
        val centers = listOf(
            -500f, 0f, tabCenter(0), tabCenter(1), tabCenter(2), barWidthPx, barWidthPx + 500f
        )
        for (pressStep in 0..20) {
            val press = pressStep / 20f
            for (center in centers) {
                for (velocity in velocities) {
                    val rect = frameAt(center, press, velocity)!!.rect
                    assertInside(rect, insetPx)
                }
            }
        }
    }

    @Test
    fun pressedLensIsCompressedIntoCapsuleInsteadOfGrowing() {
        // 静止高度正好是 barHeight - 8dp，四边只剩 4dp；按下放大必须被压回 1 倍。
        for (index in 0..2) {
            val frame = frameAt(tabCenter(index), press = 1f)!!
            assertTrue("scaleX=${frame.scaleX}", frame.scaleX <= 1f + 1e-4f)
            assertTrue("scaleY=${frame.scaleY}", frame.scaleY <= 1f + 1e-4f)
            assertTrue(frame.rect.height <= restHeightPx + 0.01f)
            assertInside(frame.rect, insetPx)
        }
    }

    @Test
    fun fastDragSquashStaysInsideCapsule() {
        val frame = frameAt(tabCenter(0), press = 1f, velocity = 1_000_000f)!!
        assertTrue(frame.scaleX > 1f)
        assertTrue(frame.scaleY < 1f)
        assertInside(frame.rect, insetPx)
    }

    @Test
    fun centerIsClampedToCapsuleAtTheEdges() {
        val left = frameAt(-10_000f)!!.rect
        assertEquals(insetPx, left.left, 0.05f)
        val right = frameAt(barWidthPx + 10_000f)!!.rect
        assertEquals(barWidthPx - insetPx, right.right, 0.05f)
    }

    @Test
    fun containLensOffAllowsAboutFiveDpOverflow() {
        val overflow = -5f * 3f
        val frame = frameAt(tabCenter(1), press = 1f, marginPx = overflow)!!
        assertEquals(overflow, frame.rect.top, 0.05f)
        assertEquals(barHeightPx - overflow, frame.rect.bottom, 0.05f)
        // 溢出也有上限，不会无限涨出去。
        assertInside(frame.rect, overflow)
    }

    @Test
    fun unmeasuredGeometryReturnsNull() {
        assertNull(
            lensFrame(
                centerX = Float.NaN,
                barWidthPx = barWidthPx,
                barHeightPx = barHeightPx,
                restWidthPx = restWidthPx,
                restHeightPx = restHeightPx,
                press = 0f,
                velocity = 0f,
                marginPx = insetPx
            )
        )
        assertNull(
            lensFrame(
                centerX = tabCenter(0),
                barWidthPx = 0f,
                barHeightPx = barHeightPx,
                restWidthPx = restWidthPx,
                restHeightPx = restHeightPx,
                press = 0f,
                velocity = 0f,
                marginPx = insetPx
            )
        )
        assertNotNull(frameAt(tabCenter(0)))
    }
}
