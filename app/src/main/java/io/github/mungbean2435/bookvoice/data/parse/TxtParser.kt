package io.github.mungbean2435.bookvoice.data.parse

/** 纯文本小说解析：先按行识别卷/章标题，再按行切自然段。 */
object TxtParser {

    /** 常见中文章节标题；限制行长，避免把正文里的"第一章"误判成标题。 */
    private val HEADER = Regex(
        "^\\s*(第[0-9０-９零〇一二三四五六七八九十百千万两]{1,12}[章节回卷部篇集]|序章|序言|楔子|引子|尾声|后记|番外)([\\s:：、.．]+.*)?$"
    )

    private const val MAX_TITLE_LEN = 48

    fun parse(text: String, fallbackTitle: String): ParsedBook {
        val chapters = ArrayList<MutableChapter>()
        val preface = StringBuilder()
        val volumeIntro = ArrayList<String>()
        var current: MutableChapter? = null
        var currentVolume: String? = null

        for (raw in text.split('\n')) {
            val line = raw.trim().removePrefix("\uFEFF").trim()
            if (line.isEmpty()) continue

            // 卷标题只切换归属，不单独成章；它后面到下一个标题之间的散文本
            // 归到本卷第一章的开头（有些 TXT 的卷首语就是这样排的）。
            if (VolumeTitles.isVolumeTitle(line)) {
                currentVolume = line
                current = null
                volumeIntro.clear()
                continue
            }

            val isHeader = line.length <= MAX_TITLE_LEN && HEADER.matches(line)
            if (isHeader) {
                val chapter = MutableChapter(line, currentVolume)
                if (volumeIntro.isNotEmpty()) {
                    chapter.blocks.addAll(volumeIntro)
                    volumeIntro.clear()
                }
                chapters.add(chapter)
                current = chapter
            } else {
                val target = current
                when {
                    target != null -> target.blocks.addAll(TextSplit.splitLong(line))
                    currentVolume != null -> volumeIntro.addAll(TextSplit.splitLong(line))
                    else -> preface.append(line).append('\n')
                }
            }
        }

        val result = ArrayList<ParsedChapter>()
        if (chapters.isEmpty()) {
            val blocks = preface.toString().split('\n').flatMap { TextSplit.splitLong(it) } +
                volumeIntro
            if (blocks.isEmpty()) throw IllegalArgumentException("没有解析到正文内容")
            result.add(
                ParsedChapter(
                    title = currentVolume ?: "正文",
                    blocks = blocks,
                    images = emptyList(),
                    volume = currentVolume
                )
            )
        } else {
            val prefaceBlocks = preface.toString().split('\n').flatMap { TextSplit.splitLong(it) }
            if (prefaceBlocks.isNotEmpty()) {
                result.add(ParsedChapter(title = "前言", blocks = prefaceBlocks, images = emptyList()))
            }
            chapters.forEach { c ->
                if (c.blocks.isNotEmpty()) {
                    result.add(
                        ParsedChapter(
                            title = c.title,
                            blocks = c.blocks,
                            images = emptyList(),
                            volume = c.volume
                        )
                    )
                }
            }
            // 卷标题后只有卷首语、没有章标题时，单独成章，避免文字丢失。
            if (volumeIntro.isNotEmpty()) {
                result.add(
                    ParsedChapter(
                        title = currentVolume ?: "正文",
                        blocks = volumeIntro,
                        images = emptyList(),
                        volume = currentVolume
                    )
                )
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

    private class MutableChapter(val title: String, val volume: String?) {
        val blocks = ArrayList<String>()
    }
}
