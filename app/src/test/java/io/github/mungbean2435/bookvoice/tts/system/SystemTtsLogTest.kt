package io.github.mungbean2435.bookvoice.tts.system

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemTtsLogTest {

    @After
    fun tearDown() {
        SystemTtsLog.clear()
    }

    @Test
    fun recordsTaggedSingleLineAndScrubsUrls() {
        SystemTtsLog.clear()
        SystemTtsLog.record("测试", "第一行 https://example.com/path\n第二行")

        val lines = SystemTtsLog.lines.value
        assertEquals(1, lines.size)
        assertTrue(lines[0].contains("[测试]"))
        assertTrue(lines[0].contains("第一行"))
        assertTrue(lines[0].contains("第二行"))
        assertTrue(lines[0].contains("（地址已隐藏）"))
        assertFalse(lines[0].contains("example.com"))
        assertFalse(lines[0].contains("\n"))
    }

    @Test
    fun clearDropsInMemoryLines() {
        SystemTtsLog.record("测试", "保留一下")
        assertTrue(SystemTtsLog.lines.value.isNotEmpty())

        SystemTtsLog.clear()
        assertTrue(SystemTtsLog.lines.value.isEmpty())
    }
}
