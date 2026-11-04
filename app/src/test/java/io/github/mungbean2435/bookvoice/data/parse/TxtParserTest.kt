package io.github.mungbean2435.bookvoice.data.parse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TxtParserTest {

    @Test
    fun splitsChaptersByHeader() {
        val text = """
            第一章 夜行的灯

            夜色落在这座城市的上空。

            风从江面上吹过来。

            第二章 旧相册

            我翻开那本旧相册。
        """.trimIndent()

        val book = TxtParser.parse(text, "测试书名")

        assertEquals("测试书名", book.title)
        assertEquals(2, book.chapters.size)
        assertEquals("第一章 夜行的灯", book.chapters[0].title)
        assertEquals(2, book.chapters[0].blocks.size)
        assertEquals("第二章 旧相册", book.chapters[1].title)
    }

    @Test
    fun textBeforeFirstHeaderBecomesPreface() {
        val text = "这是一段没有章节名的开头，用来测试前言的处理逻辑是否正常。\n\n第一章 开始\n\n正文第一段。"

        val book = TxtParser.parse(text, "书")

        assertEquals(2, book.chapters.size)
        assertEquals("前言", book.chapters[0].title)
        assertEquals("第一章 开始", book.chapters[1].title)
    }

    @Test
    fun fileWithoutHeadersBecomesSingleChapter() {
        val text = "第一段内容。\n第二段内容。\n第三段内容。"

        val book = TxtParser.parse(text, "书")

        assertEquals(1, book.chapters.size)
        assertEquals("正文", book.chapters[0].title)
        assertEquals(3, book.chapters[0].blocks.size)
    }

    @Test
    fun stripsBom() {
        val text = "\uFEFF第一章 带 BOM\n\n正文。"

        val book = TxtParser.parse(text, "书")

        assertEquals("第一章 带 BOM", book.chapters[0].title)
    }

    @Test
    fun longParagraphIsSplitWithoutLosingText() {
        val sentence = "这是一个用来测试切分逻辑的句子，长度足够触发按句切分。"
        val paragraph = sentence.repeat(120)
        val parts = TextSplit.splitLong(paragraph)

        assertTrue(parts.size > 1)
        parts.forEach { assertTrue(it.length <= TextSplit.MAX_CHARS) }
        assertEquals(paragraph, parts.joinToString(""))
    }

    @Test
    fun volumeHeadersGroupFollowingChapters() {
        val text = """
            第一卷 起风

            第一章 夜航

            海面没有灯。

            第二章 灯塔

            灯亮了一夜。

            第二卷 归途

            第一章 回港

            船靠岸时天刚亮。
        """.trimIndent()

        val book = TxtParser.parse(text, "书")

        assertEquals(3, book.chapters.size)
        assertEquals("第一章 夜航", book.chapters[0].title)
        assertEquals("第一卷 起风", book.chapters[0].volume)
        assertEquals("第一卷 起风", book.chapters[1].volume)
        assertEquals("第二卷 归途", book.chapters[2].volume)
    }

    @Test
    fun volumeIntroIsAttachedToFirstChapter() {
        val text = """
            第一卷 起风

            本卷讲述一次远航。

            第一章 夜航

            海面没有灯。
        """.trimIndent()

        val book = TxtParser.parse(text, "书")

        assertEquals(1, book.chapters.size)
        assertEquals("第一章 夜航", book.chapters[0].title)
        assertEquals("第一卷 起风", book.chapters[0].volume)
        assertEquals("本卷讲述一次远航。", book.chapters[0].blocks.first())
    }

    @Test
    fun volumeWithoutChaptersStillProducesContent() {
        val text = """
            第一卷 起风

            只有一段卷首语，没有章标题。
        """.trimIndent()

        val book = TxtParser.parse(text, "书")

        assertEquals(1, book.chapters.size)
        assertEquals("第一卷 起风", book.chapters[0].title)
        assertEquals("第一卷 起风", book.chapters[0].volume)
        assertTrue(book.chapters[0].blocks.isNotEmpty())
    }
}
