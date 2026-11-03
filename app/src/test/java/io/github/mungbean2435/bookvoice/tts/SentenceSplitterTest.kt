package io.github.mungbean2435.bookvoice.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SentenceSplitterTest {

    private val text = "第一句。第二句！第三句？"

    @Test
    fun findsFirstSentence() {
        assertEquals(0 to 4, SentenceSplitter.rangeAt(text, 0))
        assertEquals(0 to 4, SentenceSplitter.rangeAt(text, 3))
    }

    @Test
    fun findsMiddleSentence() {
        assertEquals(4 to 8, SentenceSplitter.rangeAt(text, 4))
        assertEquals(4 to 8, SentenceSplitter.rangeAt(text, 7))
    }

    @Test
    fun findsLastSentence() {
        assertEquals(8 to 12, SentenceSplitter.rangeAt(text, 11))
    }

    @Test
    fun singleSentenceWithoutMark() {
        val plain = "没有标点的一句话"
        assertEquals(0 to plain.length, SentenceSplitter.rangeAt(plain, 2))
    }

    @Test
    fun emptyTextReturnsNull() {
        assertNull(SentenceSplitter.rangeAt("", 0))
    }
}
