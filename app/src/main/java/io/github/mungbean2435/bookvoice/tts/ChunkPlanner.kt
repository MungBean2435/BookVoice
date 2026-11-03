package io.github.mungbean2435.bookvoice.tts

import io.github.mungbean2435.bookvoice.data.parse.TextSplit

/** 一块合成音频覆盖的原文区间：第 [blockIdx] 段的 [start, end) 字符。 */
data class ChunkSpan(val blockIdx: Int, val start: Int, val end: Int)

/**
 * 一个合成块。[text] 送 TTS，[origin] 是对应的原文（段落间用 \n 连接），
 * [spans] 把块内字符位置映射回"第几段第几个字"。
 */
data class PlannedChunk(
    val text: String,
    val origin: String,
    val spans: List<ChunkSpan>,
    val lastOfChapter: Boolean
)

/**
 * 把若干自然段合并成合成块。
 * 对话体小说一段只有十几个字，逐段发请求会"合成 2 秒、播放 5 秒"，
 * 永远追不上播放速度，所以这里按字数合并，目标 400 字一块。
 */
object ChunkPlanner {

    const val FIRST_MAX = 150
    const val TARGET = 400
    const val MAX_PIECE = 600

    private data class Piece(val blockIdx: Int, val start: Int, val end: Int, val text: String)

    fun plan(
        blocks: List<Pair<Int, String>>,
        fromBlockIdx: Int = Int.MIN_VALUE,
        fromOffset: Int = 0,
        normalize: (String) -> String = { it }
    ): List<PlannedChunk> {
        val pieces = buildPieces(blocks, fromBlockIdx, fromOffset)
        if (pieces.isEmpty()) return emptyList()

        val chunks = ArrayList<PlannedChunk>()
        var current = ArrayList<Piece>()
        var length = 0
        var limit = FIRST_MAX

        fun flush() {
            if (current.isEmpty()) return
            chunks.add(
                PlannedChunk(
                    text = current.joinToString("\n") { normalize(it.text) },
                    origin = current.joinToString("\n") { it.text },
                    spans = current.map { ChunkSpan(it.blockIdx, it.start, it.end) },
                    lastOfChapter = false
                )
            )
            current = ArrayList()
            length = 0
        }

        for (piece in pieces) {
            if (current.isNotEmpty() && length + 1 + piece.text.length > limit) {
                flush()
                limit = TARGET
            }
            current.add(piece)
            length += piece.text.length + 1
        }
        flush()

        return chunks.mapIndexed { index, chunk ->
            if (index == chunks.lastIndex) chunk.copy(lastOfChapter = true) else chunk
        }
    }

    /** 把块内字符位置映射成 (段号, 段内偏移)。 */
    fun locate(chunk: PlannedChunk, charIndex: Int): Pair<Int, Int>? {
        if (chunk.spans.isEmpty()) return null
        var acc = 0
        for (span in chunk.spans) {
            val length = span.end - span.start
            if (charIndex < acc + length) {
                return span.blockIdx to (span.start + (charIndex - acc))
            }
            acc += length + 1
        }
        val last = chunk.spans.last()
        return last.blockIdx to (last.end - 1).coerceAtLeast(last.start)
    }

    private fun buildPieces(
        blocks: List<Pair<Int, String>>,
        fromBlockIdx: Int,
        fromOffset: Int
    ): List<Piece> {
        val pieces = ArrayList<Piece>()
        blocks.forEach { (blockIdx, text) ->
            if (blockIdx < fromBlockIdx) return@forEach
            val start = if (blockIdx == fromBlockIdx) fromOffset.coerceIn(0, text.length) else 0
            if (start >= text.length) return@forEach
            val sliced = text.substring(start)
            var cursor = start
            TextSplit.splitLong(sliced, MAX_PIECE).forEach { part ->
                pieces.add(Piece(blockIdx, cursor, cursor + part.length, part))
                cursor += part.length
            }
        }

        // 首块要短：把第一片再切到 FIRST_MAX 以内
        if (pieces.isNotEmpty() && pieces[0].text.length > FIRST_MAX) {
            val first = pieces[0]
            val head = TextSplit.splitLong(first.text, FIRST_MAX)
            val replacement = ArrayList<Piece>()
            var cursor = first.start
            head.forEach { part ->
                replacement.add(Piece(first.blockIdx, cursor, cursor + part.length, part))
                cursor += part.length
            }
            pieces.removeAt(0)
            pieces.addAll(0, replacement)
        }
        return pieces
    }
}
