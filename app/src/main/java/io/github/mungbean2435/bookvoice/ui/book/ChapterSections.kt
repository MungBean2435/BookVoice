package io.github.mungbean2435.bookvoice.ui.book

import io.github.mungbean2435.bookvoice.data.db.Chapter

/** 目录里的一段连续章节；volume 为空表示这本书没有卷结构。 */
internal data class ChapterSection(
    val volume: String?,
    val chapters: List<Chapter>
)

internal sealed interface ChapterRow {
    val key: String

    data class Header(
        val volume: String,
        val chapterCount: Int,
        val expanded: Boolean,
        val sectionIndex: Int
    ) : ChapterRow {
        override val key: String = "volume:$sectionIndex"
    }

    data class Item(
        val chapter: Chapter,
        val indented: Boolean
    ) : ChapterRow {
        override val key: String = "chapter:${chapter.idx}"
    }
}

/** 把章节按连续的 volume 归成段；没有 volume 的章留在原来的位置。 */
internal fun buildChapterSections(chapters: List<Chapter>): List<ChapterSection> {
    val out = ArrayList<ChapterSection>()
    for (chapter in chapters) {
        val volume = chapter.volume?.trim()?.takeIf { it.isNotEmpty() }
        val last = out.lastOrNull()
        if (last != null && last.volume == volume) {
            out[out.lastIndex] = last.copy(chapters = last.chapters + chapter)
        } else {
            out.add(ChapterSection(volume = volume, chapters = listOf(chapter)))
        }
    }
    return out
}

internal fun sectionKey(section: ChapterSection, index: Int): String =
    section.volume?.let { "volume:$it" } ?: "section:$index"

/**
 * 展开后的扁平行。卷头的 key 用 section 序号，章节 key 用全书序号，
 * 这样折叠/展开时 Compose 不会把旧行错配到新章节上。
 */
internal fun buildChapterRows(
    sections: List<ChapterSection>,
    expandedVolume: String?
): List<ChapterRow> {
    val rows = ArrayList<ChapterRow>()
    sections.forEachIndexed { index, section ->
        if (section.volume == null) {
            section.chapters.forEach { rows.add(ChapterRow.Item(it, indented = false)) }
        } else {
            val expanded = expandedVolume == sectionKey(section, index)
            rows.add(
                ChapterRow.Header(
                    volume = section.volume,
                    chapterCount = section.chapters.size,
                    expanded = expanded,
                    sectionIndex = index
                )
            )
            if (expanded) {
                section.chapters.forEach { rows.add(ChapterRow.Item(it, indented = true)) }
            }
        }
    }
    return rows
}

/** 当前章所在的卷；没有卷或找不到时返回 null。 */
internal fun currentSectionKey(sections: List<ChapterSection>, currentChapter: Int): String? {
    sections.forEachIndexed { index, section ->
        if (section.chapters.any { it.idx == currentChapter }) {
            return section.volume?.let { sectionKey(section, index) }
        }
    }
    return null
}
