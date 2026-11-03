package io.github.mungbean2435.bookvoice.tts.minimax

import io.github.mungbean2435.bookvoice.tts.TtsException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class MiniMaxHexTest {

    @Test
    fun decodesLowercaseHex() {
        assertArrayEquals(byteArrayOf(0x00, 0x7F, 0xFF.toByte()), hexToBytes("007fff"))
    }

    @Test
    fun decodesUppercaseHex() {
        assertArrayEquals(byteArrayOf(0xAB.toByte(), 0xCD.toByte()), hexToBytes("ABCD"))
    }

    @Test
    fun emptyStringGivesEmptyArray() {
        assertEquals(0, hexToBytes("").size)
    }

    @Test(expected = TtsException.Server::class)
    fun rejectsOddLength() {
        hexToBytes("abc")
    }

    @Test(expected = TtsException.Server::class)
    fun rejectsNonHexChars() {
        hexToBytes("zz")
    }
}
