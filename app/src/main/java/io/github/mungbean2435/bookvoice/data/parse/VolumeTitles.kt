package io.github.mungbean2435.bookvoice.data.parse

/**
 * 卷标题识别。只认独立成行的短标题，避免把正文里的"第一卷"误判成卷名。
 * 支持：第X卷/部/篇/集、卷X、Volume/Part/Book N，可带标题后缀。
 */
object VolumeTitles {

    private val VOLUME = Regex(
        "^\\s*(?:" +
            "第\\s*[0-9０-９零〇一二三四五六七八九十百千万两]{1,12}\\s*[卷部篇集]" +
            "|卷\\s*[0-9０-９零〇一二三四五六七八九十百千万两]{1,12}" +
            "|(?:volume|part|book)\\s+[0-9]{1,3}" +
            ")(?:\\s*[:：、.．·—-].*|\\s+.*)?$",
        RegexOption.IGNORE_CASE
    )

    private const val MAX_LEN = 60

    fun isVolumeTitle(text: String): Boolean {
        val line = text.trim()
        return line.isNotEmpty() && line.length <= MAX_LEN && VOLUME.matches(line)
    }
}
