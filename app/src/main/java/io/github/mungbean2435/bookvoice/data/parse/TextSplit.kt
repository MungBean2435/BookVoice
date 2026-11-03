package io.github.mungbean2435.bookvoice.data.parse

/**
 * 切块规则（与 M2 的 TTS 合成块保持一致）：
 * 一个自然段 ≤ [MAX_CHARS]，超长按句末标点切，绝不在句子中间硬切。
 */
object TextSplit {

    const val MAX_CHARS = 800

    private val SENTENCE_MARKS = charArrayOf('。', '！', '？', '!', '?', '…', '；', ';', '，', ',', '、')

    fun splitLong(text: String, maxChars: Int = MAX_CHARS): List<String> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return emptyList()
        if (trimmed.length <= maxChars) return listOf(trimmed)

        val out = ArrayList<String>()
        var start = 0
        while (start < trimmed.length) {
            var end = minOf(start + maxChars, trimmed.length)
            if (end < trimmed.length) {
                val window = trimmed.substring(start, end)
                val cut = window.lastIndexOfAny(SENTENCE_MARKS)
                if (cut > maxChars / 2) end = start + cut + 1
            }
            val piece = trimmed.substring(start, end).trim()
            if (piece.isNotEmpty()) out.add(piece)
            start = end
        }
        return out
    }
}
