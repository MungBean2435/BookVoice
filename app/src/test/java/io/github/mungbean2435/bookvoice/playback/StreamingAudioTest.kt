package io.github.mungbean2435.bookvoice.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamingAudioTest {

    private fun le16(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)

    private fun le32(bytes: ByteArray, offset: Int): Long =
        (bytes[offset].toLong() and 0xFF) or
            ((bytes[offset + 1].toLong() and 0xFF) shl 8) or
            ((bytes[offset + 2].toLong() and 0xFF) shl 16) or
            ((bytes[offset + 3].toLong() and 0xFF) shl 24)

    private fun ascii(bytes: ByteArray, offset: Int, length: Int) =
        String(bytes, offset, length, Charsets.US_ASCII)

    @Test
    fun liveHeaderDescribesStreamingPcm() {
        val header = PcmWav.liveHeader()

        assertEquals(44, header.size)
        assertEquals("RIFF", ascii(header, 0, 4))
        assertEquals("WAVE", ascii(header, 8, 4))
        assertEquals("fmt ", ascii(header, 12, 4))
        assertEquals("data", ascii(header, 36, 4))
        assertEquals(1, le16(header, 20))          // PCM
        assertEquals(1, le16(header, 22))          // 单声道
        assertEquals(24_000L, le32(header, 24))    // 采样率
        assertEquals(48_000L, le32(header, 28))    // 字节率
        assertEquals(2, le16(header, 32))          // block align
        assertEquals(16, le16(header, 34))         // 位深
        assertEquals(0xFFFFFFFFL, le32(header, 40))
    }

    @Test
    fun fileHeaderWritesRealDataSize() {
        val header = PcmWav.fileHeader(9_600L)

        assertEquals(36L + 9_600L, le32(header, 4))
        assertEquals(9_600L, le32(header, 40))
    }

    @Test
    fun durationUses48kBytesPerSecond() {
        assertEquals(0L, PcmWav.durationMs(0))
        assertEquals(500L, PcmWav.durationMs(24_000))
        assertEquals(1_000L, PcmWav.durationMs(48_000))
    }

    @Test
    fun bufferReadsAppendedBlocks() {
        val buffer = PcmStreamBuffer()
        buffer.append(byteArrayOf(1, 2, 3, 4))
        buffer.append(byteArrayOf(5, 6, 7, 8))

        val first = ByteArray(4)
        assertEquals(4, buffer.read(first, 0, first.size))
        assertTrue(first.contentEquals(byteArrayOf(1, 2, 3, 4)))

        val second = ByteArray(4)
        assertEquals(4, buffer.read(second, 0, second.size))
        assertTrue(second.contentEquals(byteArrayOf(5, 6, 7, 8)))
        assertEquals(8L, buffer.bytesWritten)
    }

    @Test
    fun finishReturnsEofAfterQueuedData() {
        val buffer = PcmStreamBuffer()
        buffer.append(byteArrayOf(1, 2))
        buffer.finish()

        val out = ByteArray(4)
        assertEquals(2, buffer.read(out, 0, out.size))
        assertEquals(-1, buffer.read(out, 0, out.size))
    }

    @Test
    fun cancelStopsReaderImmediately() {
        val buffer = PcmStreamBuffer()
        buffer.append(byteArrayOf(1, 2, 3))
        buffer.cancel()

        assertEquals(-1, buffer.read(ByteArray(4), 0, 4))
        // 取消后不再接受数据。
        buffer.append(byteArrayOf(9))
        assertEquals(-1, buffer.read(ByteArray(4), 0, 4))
    }
}
