package io.github.mungbean2435.bookvoice.ui.book

import io.github.mungbean2435.bookvoice.data.db.Chapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChapterSectionsTest {

    private val chapters = listOf(
        Chapter("b", 0, "序章", volume = "第一卷"),
        Chapter("b", 1, "第一章", volume = "第一卷"),
        Chapter("b", 2, "第二章", volume = "第二卷"),
        Chapter("b", 3, "第三章", volume = "第二卷"),
        Chapter("b", 4, "附录", volume = null)
    )

    @Test
    fun groupsConsecutiveChaptersByVolume() {
        val sections = buildChapterSections(chapters)

        assertEquals(3, sections.size)
        assertEquals("第一卷", sections[0].volume)
        assertEquals(2, sections[0].chapters.size)
        assertEquals("第二卷", sections[1].volume)
        assertEquals(null, sections[2].volume)
        assertEquals("附录", sections[2].chapters.single().title)
    }

    @Test
    fun collapsedVolumeHidesItsChapters() {
        val sections = buildChapterSections(chapters)

        val rows = buildChapterRows(sections, expandedVolume = "volume:第一卷")

        assertEquals(1, rows.count { it is ChapterRow.Header && it.expanded })
        assertEquals(2, rows.count { it is ChapterRow.Item && it.indented })
        assertTrue(rows.any { it is ChapterRow.Item && it.chapter.title == "附录" })
        assertTrue(rows.none { it is ChapterRow.Item && it.chapter.title == "第二章" })
    }

    @Test
    fun currentSectionKeyMatchesVolumeOfCurrentChapter() {
        val sections = buildChapterSections(chapters)

        assertEquals("volume:第二卷", currentSectionKey(sections, 2))
        assertEquals(null, currentSectionKey(sections, 4))
    }

    @Test
    fun rowsKeepStableKeys() {
        val sections = buildChapterSections(chapters)
        val rows = buildChapterRows(sections, expandedVolume = "volume:第一卷")

        assertEquals(rows.size, rows.map { it.key }.distinct().size)
    }
}
