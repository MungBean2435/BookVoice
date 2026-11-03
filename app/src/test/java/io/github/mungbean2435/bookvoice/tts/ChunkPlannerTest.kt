package io.github.mungbean2435.bookvoice.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChunkPlannerTest {

    private fun blocks(vararg texts: String) = texts.mapIndexed { index, text -> index to text }

    @Test
    fun mergesShortDialogueParagraphs() {
        val input = blocks("「你好。」", "「你好呀。」", "「今天天气不错。」", "「是啊。」")

        val chunks = ChunkPlanner.plan(input)

        assertEquals("四句短对话应合并成一块", 1, chunks.size)
        assertEquals(4, chunks[0].spans.size)
        assertTrue(chunks[0].text.length <= ChunkPlanner.FIRST_MAX)
    }

    @Test
    fun firstChunkIsShortOnLongParagraph() {
        val long = "这是第一句话。".repeat(80)   // 560 字

        val chunks = ChunkPlanner.plan(blocks(long))

        assertTrue("首块应 ≤150 字，实际 ${chunks[0].text.length}", chunks[0].text.length <= ChunkPlanner.FIRST_MAX)
        assertTrue(chunks.size > 1)
        chunks.forEach { assertTrue(it.text.length <= ChunkPlanner.MAX_PIECE) }
    }

    @Test
    fun chunksCoverAllText() {
        val texts = Array(20) { "第${it}段，这是一句用来测试合并的对话。" }

        val chunks = ChunkPlanner.plan(blocks(*texts))

        val original = texts.joinToString("")
        val rebuilt = chunks.joinToString("") { it.origin.replace("\n", "") }
        assertEquals(original, rebuilt)
    }

    @Test
    fun spansMapBackToBlocks() {
        val input = blocks("第一段文字。", "第二段文字。", "第三段文字。")

        val chunks = ChunkPlanner.plan(input)
        val chunk = chunks[0]

        assertEquals(3, chunk.spans.size)
        // 块内第 0 个字符 → 第 0 段第 0 字
        assertEquals(0 to 0, ChunkPlanner.locate(chunk, 0))
        // 第 1 段第一个字在 origin 里的位置 = 第0段长度 + 1（换行）
        val secondStart = input[0].second.length + 1
        assertEquals(1 to 0, ChunkPlanner.locate(chunk, secondStart))
        assertEquals(1 to 2, ChunkPlanner.locate(chunk, secondStart + 2))
    }

    @Test
    fun startsFromOffsetInsideBlock() {
        val text = "第一句。第二句。第三句。"

        val chunks = ChunkPlanner.plan(blocks(text), fromBlockIdx = 0, fromOffset = 4)

        assertTrue(chunks[0].origin.startsWith("第二句"))
        assertEquals(0, chunks[0].spans[0].blockIdx)
        assertEquals(4, chunks[0].spans[0].start)
    }

    @Test
    fun emptyInputProducesNoChunks() {
        assertTrue(ChunkPlanner.plan(emptyList()).isEmpty())
    }

    @Test
    fun lastChunkIsMarked() {
        val long = "这是一句测试文本。".repeat(60)

        val chunks = ChunkPlanner.plan(blocks(long))

        assertTrue(chunks.size > 1)
        assertTrue(chunks.last().lastOfChapter)
        assertTrue(chunks.dropLast(1).none { it.lastOfChapter })
    }
}
