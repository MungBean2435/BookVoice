package io.github.mungbean2435.bookvoice.tts

/** 句级高亮用：给定字符位置，返回所在句子的区间。 */
object SentenceSplitter {

    private const val MARKS = "。！？…!?；;\n"

    fun rangeAt(text: String, charIndex: Int): Pair<Int, Int>? {
        if (text.isEmpty()) return null
        val index = charIndex.coerceIn(0, text.length - 1)
        var start = 0
        for (i in text.indices) {
            if (MARKS.indexOf(text[i]) >= 0) {
                val end = i + 1
                if (index < end) return start to end
                start = end
            }
        }
        return if (start < text.length) start to text.length else null
    }
}
