package io.github.mungbean2435.bookvoice.ui.player

import io.github.mungbean2435.bookvoice.data.db.Block
import io.github.mungbean2435.bookvoice.data.db.ChapterImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderRowsTest {

    private fun block(chapter: Int, idx: Int, text: String = "c${chapter}b$idx") = Block(
        bookId = "book",
        chapterIdx = chapter,
        idx = idx,
        text = text,
        charCount = text.length,
        hash = "h"
    )

    private fun image(chapter: Int, idx: Int, afterBlock: Int, path: String = "p$chapter-$idx") =
        ChapterImage(
            bookId = "book",
            chapterIdx = chapter,
            idx = idx,
            afterBlockIdx = afterBlock,
            path = path
        )

    @Test
    fun emptyInputHasNoRows() {
        assertTrue(buildRows(emptyList(), emptyList()).isEmpty())
    }

    @Test
    fun chaptersAreContinuousWithBreakBetweenThem() {
        val rows = buildRows(
            listOf(block(0, 0), block(0, 1), block(1, 0), block(1, 1)),
            emptyList()
        )
        assertEquals(
            listOf("t0:0", "t0:1", "b1", "t1:0", "t1:1"),
            rows.map { it.key }
        )
        assertEquals(0, rows[0].chapterIdx)
        assertEquals(1, rows[3].chapterIdx)
    }

    @Test
    fun imageOnlyChapterIsIncluded() {
        val rows = buildRows(
            listOf(block(0, 0), block(2, 0)),
            listOf(image(1, 0, 0), image(1, 1, 0))
        )
        assertEquals(
            listOf("t0:0", "b1", "p1:0", "p1:1", "b2", "t2:0"),
            rows.map { it.key }
        )
        assertTrue(rows.any { it is DisplayRow.Picture && it.chapterIdx == 1 })
    }

    @Test
    fun picturesFollowTheirAnchorBlock() {
        val rows = buildRows(
            listOf(block(0, 0), block(0, 1), block(0, 2)),
            listOf(image(0, 0, 0), image(0, 1, 1), image(0, 2, 2))
        )
        assertEquals(
            listOf("p0:0", "t0:0", "p0:1", "t0:1", "p0:2", "t0:2"),
            rows.map { it.key }
        )
    }

    @Test
    fun sameBlockIndexAcrossChaptersDoesNotCollide() {
        val rows = buildRows(listOf(block(0, 0), block(1, 0)), emptyList())
        assertEquals(2, rows.filterIsInstance<DisplayRow.Text>().size)
        assertEquals(rows.size, rows.map { it.key }.distinct().size)
    }

    @Test
    fun skeletonRowsKeepTheSameKeysAsFullRows() {
        val blocks = listOf(block(0, 0), block(0, 1), block(1, 0), block(2, 0))
        val images = listOf(image(0, 0, 0), image(1, 0, 0))
        val layout = ChapterLayout.of(
            blockCounts = blocks.groupBy { it.chapterIdx }.mapValues { it.value.size },
            images = images
        )
        val skeleton = buildSkeletonRows(layout)
        assertEquals(buildRows(blocks, images).map { it.key }, skeleton.map { it.key })
        assertTrue(skeleton.none { it is DisplayRow.Text })
    }

    @Test
    fun skeletonRowsUseProvidedTextsAndStubsElsewhere() {
        val layout = ChapterLayout.of(mapOf(0 to 2, 1 to 1), emptyList())
        val rows = buildSkeletonRows(layout, mapOf(0 to listOf(block(0, 1))))
        assertEquals(listOf("t0:0", "t0:1", "b1", "t1:0"), rows.map { it.key })
        assertTrue(rows[0] is DisplayRow.Stub)
        assertTrue(rows[1] is DisplayRow.Text)
        assertTrue(rows[3] is DisplayRow.Stub)
    }

    @Test
    fun replacingAChapterKeepsLengthAndKeyOrder() {
        val blocks = listOf(block(0, 0), block(1, 0), block(1, 1), block(2, 0))
        val images = listOf(image(1, 0, 0))
        val layout = ChapterLayout.of(
            blockCounts = blocks.groupBy { it.chapterIdx }.mapValues { it.value.size },
            images = images
        )
        val skeleton = buildSkeletonRows(layout)
        val filled = replaceChapterRows(
            skeleton,
            chapterIdx = 1,
            chapterRows = buildChapterRows(1, 2, images, emptyMap(), includeBreak = true)
        )
        assertEquals(skeleton.size, filled.size)
        assertEquals(skeleton.map { it.key }, filled.map { it.key })
        val chapterOne = filled.filter { it.chapterIdx == 1 }
        assertTrue(chapterOne.isNotEmpty())
        assertTrue(chapterOne.filterIsInstance<DisplayRow.Text>().isEmpty())
    }

    @Test
    fun chapterRowsOnlyIncludeBreakWhenAsked() {
        val rows = buildChapterRows(1, 1, emptyList(), emptyMap(), includeBreak = false)
        assertEquals(listOf("t1:0"), rows.map { it.key })
    }
}
