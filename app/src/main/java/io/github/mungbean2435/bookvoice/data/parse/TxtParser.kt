package io.github.mungbean2435.bookvoice.data.parse

/** 纯文本小说解析：先按行识别章节标题，再按行切自然段。 */
object TxtParser {

    /** 常见中文章节标题；限制行长，避免把正文里的"第一章"误判成标题。 */
    private val HEADER = Regex(
        "^\\s*(第[0-9０-９零〇一二三四五六七八九十百千万两]{1,12}[章节回卷部篇集]|序章|序言|楔子|引子|尾声|后记|番外)([\\s:：、.．]+.*)?$"
    )

    private const val MAX_TITLE_LEN = 48

    fun parse(text: String, fallbackTitle: String): ParsedBook {
        val chapters = ArrayList<MutableChapter>()
        val preface = StringBuilder()
        var current: MutableChapter? = null

        for (raw in text.split('\n')) {
            val line = raw.trim().removePrefix("\uFEFF").trim()
            if (line.isEmpty()) continue

            val isHeader = line.length <= MAX_TITLE_LEN && HEADER.matches(line)
            if (isHeader) {
                val chapter = MutableChapter(line)
                chapters.add(chapter)
                current = chapter
            } else {
                val target = current
                if (target != null) {
                    target.blocks.addAll(TextSplit.splitLong(line))
                } else {
                    preface.append(line).append('\n')
                }
            }
        }

        val result = ArrayList<ParsedChapter>()
        if (chapters.isEmpty()) {
            val blocks = preface.toString().split('\n').flatMap { TextSplit.splitLong(it) }
            if (blocks.isEmpty()) throw IllegalArgumentException("没有解析到正文内容")
            result.add(ParsedChapter(title = "正文", blocks = blocks, images = emptyList()))
        } else {
            val prefaceBlocks = preface.toString().split('\n').flatMap { TextSplit.splitLong(it) }
            if (prefaceBlocks.isNotEmpty()) {
                result.add(ParsedChapter(title = "前言", blocks = prefaceBlocks, images = emptyList()))
            }
            chapters.forEach { c ->
                if (c.blocks.isNotEmpty()) {
                    result.add(ParsedChapter(title = c.title, blocks = c.blocks, images = emptyList()))
                }
            }
            if (result.isEmpty()) throw IllegalArgumentException("没有解析到正文内容")
        }

        return ParsedBook(
            title = fallbackTitle,
            author = null,
            coverPath = null,
            chapters = result
        )
    }

    private class MutableChapter(val title: String) {
        val blocks = ArrayList<String>()
    }
}
