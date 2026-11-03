package io.github.mungbean2435.bookvoice.data.parse

import org.junit.Assert.assertEquals
import org.junit.Test

class TextDecodeTest {

    @Test
    fun decodesUtf8() {
        val bytes = "中文内容测试".toByteArray(Charsets.UTF_8)
        assertEquals("中文内容测试", TextDecode.decode(bytes))
    }

    @Test
    fun decodesGbk() {
        val bytes = "中文内容测试".toByteArray(charset("GBK"))
        assertEquals("中文内容测试", TextDecode.decode(bytes))
    }

    @Test
    fun stripsUtf8Bom() {
        val raw = "带BOM的内容".toByteArray(Charsets.UTF_8)
        val withBom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + raw
        assertEquals("带BOM的内容", TextDecode.decode(withBom))
    }
}
