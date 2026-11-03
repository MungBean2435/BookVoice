package io.github.mungbean2435.bookvoice.ui.player

import io.github.mungbean2435.bookvoice.data.db.Block
import io.github.mungbean2435.bookvoice.data.db.ChapterImage

/**
 * 连续阅读的一行。
 *
 * 行里带着 [chapterIdx]，这样整本书可以放在同一个 LazyColumn 里：
 * 手动滑到章末会自然滑进下一章，只有插图的章节也不会被跳过。
 *
 * [Stub] 是还没拉正文的段落占位，key 与 [Text] 完全相同：
 * 换入/换出只改同一行的内容，列表长度与键序永远不变，不会跳位。
 */
internal sealed interface DisplayRow {
    val chapterIdx: Int
    val key: String

    data class Text(val block: Block) : DisplayRow {
        override val chapterIdx: Int get() = block.chapterIdx
        override val key: String get() = "t${block.chapterIdx}:${block.idx}"
    }

    /** 正文还没加载的占位段；键与 [Text] 一致，换文本时键不变。 */
    data class Stub(override val chapterIdx: Int, val blockIdx: Int) : DisplayRow {
        override val key: String get() = "t$chapterIdx:$blockIdx"
    }

    data class Picture(
        override val chapterIdx: Int,
        val idx: Int,
        val path: String
    ) : DisplayRow {
        override val key: String get() = "p${chapterIdx}:$idx"
    }

    /** 章与章之间的留白，不显示文字，避免两章首尾粘在一起。 */
    data class Break(override val chapterIdx: Int) : DisplayRow {
        override val key: String get() = "b$chapterIdx"
    }
}

/**
 * 全书的「形状」：章顺序、每章段数、每章插图。
 * 首屏一次查询就能拿到，之后正文按章换入，行数与键序不再变化。
 */
internal data class ChapterLayout(
    val chapterIdxs: List<Int>,
    val blockCounts: Map<Int, Int>,
    val imagesByChapter: Map<Int, List<ChapterImage>>
) {
    fun blockCountOf(chapterIdx: Int): Int = blockCounts[chapterIdx] ?: 0

    fun imagesOf(chapterIdx: Int): List<ChapterImage> = imagesByChapter[chapterIdx].orEmpty()

    fun contains(chapterIdx: Int): Boolean = chapterIdxs.binarySearch(chapterIdx) >= 0

    companion object {
        fun of(blockCounts: Map<Int, Int>, images: List<ChapterImage>): ChapterLayout {
            val imagesByChapter = images.groupBy { it.chapterIdx }
            val chapterIdxs = (blockCounts.keys + imagesByChapter.keys).sorted()
            return ChapterLayout(chapterIdxs, blockCounts, imagesByChapter)
        }
    }
}

/** 把全书段落与插图拼成一个连续列表（按章节顺序）。 */
internal fun buildRows(blocks: List<Block>, images: List<ChapterImage>): List<DisplayRow> {
    val blocksByChapter = blocks.groupBy { it.chapterIdx }
    val imagesByChapter = images.groupBy { it.chapterIdx }
    val chapterIdxs = (blocksByChapter.keys + imagesByChapter.keys).sorted()
    val out = ArrayList<DisplayRow>(blocks.size + images.size + chapterIdxs.size)
    chapterIdxs.forEachIndexed { index, chapterIdx ->
        if (index > 0) out.add(DisplayRow.Break(chapterIdx))
        val byPosition = imagesByChapter[chapterIdx].orEmpty().groupBy { it.afterBlockIdx }
        byPosition[0]?.forEach { out.add(DisplayRow.Picture(chapterIdx, it.idx, it.path)) }
        blocksByChapter[chapterIdx].orEmpty().forEach { block ->
            out.add(DisplayRow.Text(block))
            byPosition[block.idx + 1]?.forEach { out.add(DisplayRow.Picture(chapterIdx, it.idx, it.path)) }
        }
    }
    return out
}

/**
 * 单章的行：章首留白 + 插图 + 段落。
 * [texts] 为空时所有段落都是 [DisplayRow.Stub]，行的形状与真实文本完全一致。
 */
internal fun buildChapterRows(
    chapterIdx: Int,
    blockCount: Int,
    images: List<ChapterImage>,
    texts: Map<Int, Block>,
    includeBreak: Boolean
): List<DisplayRow> {
    val out = ArrayList<DisplayRow>(blockCount + images.size + 1)
    if (includeBreak) out.add(DisplayRow.Break(chapterIdx))
    val byPosition = images.groupBy { it.afterBlockIdx }
    byPosition[0]?.forEach { out.add(DisplayRow.Picture(chapterIdx, it.idx, it.path)) }
    for (idx in 0 until blockCount) {
        val block = texts[idx]
        out.add(
            if (block != null) DisplayRow.Text(block) else DisplayRow.Stub(chapterIdx, idx)
        )
        byPosition[idx + 1]?.forEach { out.add(DisplayRow.Picture(chapterIdx, it.idx, it.path)) }
    }
    return out
}

/**
 * 首帧用的整本骨架：所有章都先按段数占位，长度与键序即最终形态；
 * [textsByChapter] 里给了正文的章直接显示文本。
 */
internal fun buildSkeletonRows(
    layout: ChapterLayout,
    textsByChapter: Map<Int, List<Block>> = emptyMap()
): List<DisplayRow> {
    val slices = layout.chapterIdxs.mapIndexed { index, chapterIdx ->
        buildChapterRows(
            chapterIdx = chapterIdx,
            blockCount = layout.blockCountOf(chapterIdx),
            images = layout.imagesOf(chapterIdx),
            texts = textsByChapter[chapterIdx]?.associateBy { it.idx }.orEmpty(),
            includeBreak = index > 0
        )
    }
    val out = ArrayList<DisplayRow>(slices.sumOf { it.size })
    slices.forEach { out.addAll(it) }
    return out
}

/**
 * 用一章的新行替换旧行。章的段数、插图数、章首留白都不变，
 * 所以替换前后列表总长度一致，只换掉了这一章的内容。
 */
internal fun replaceChapterRows(
    rows: List<DisplayRow>,
    chapterIdx: Int,
    chapterRows: List<DisplayRow>
): List<DisplayRow> {
    val first = rows.indexOfFirst { it.chapterIdx == chapterIdx }
    if (first < 0) return rows
    var last = first
    while (last < rows.size && rows[last].chapterIdx == chapterIdx) last++
    val out = ArrayList<DisplayRow>(rows.size - (last - first) + chapterRows.size)
    out.addAll(rows.subList(0, first))
    out.addAll(chapterRows)
    out.addAll(rows.subList(last, rows.size))
    return out
}
