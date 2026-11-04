package io.github.mungbean2435.bookvoice.playback

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSpec

/**
 * 正在增长的一段 PCM 音频：合成器往里写，ExoPlayer 的 DataSource 从里面读。
 *
 * 读不到数据时会阻塞等待（最多 200ms 一轮），这样播放器可以边收边播；
 * 合成结束调用 [finish] 返回 EOF，播放器正常进入 ENDED。
 */
class PcmStreamBuffer {
    private val lock = Object()
    private val queue = ArrayDeque<ByteArray>()
    private var readOffset = 0
    private var finished = false
    private var cancelled = false
    private var total = 0L

    fun append(bytes: ByteArray) {
        if (bytes.isEmpty()) return
        synchronized(lock) {
            if (finished || cancelled) return
            queue.addLast(bytes)
            total += bytes.size
            lock.notifyAll()
        }
    }

    fun finish() {
        synchronized(lock) {
            finished = true
            lock.notifyAll()
        }
    }

    fun cancel() {
        synchronized(lock) {
            cancelled = true
            queue.clear()
            lock.notifyAll()
        }
    }

    val bytesWritten: Long
        get() = synchronized(lock) { total }

    /** 供应商已经结束（正常或超时收尾）：不会再追加数据。 */
    val isFinished: Boolean
        get() = synchronized(lock) { finished || cancelled }

    /** 返回读到的字节数；流结束/取消返回 -1。 */
    fun read(dst: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        synchronized(lock) {
            while (true) {
                if (cancelled) return -1
                val head = queue.firstOrNull()
                if (head != null) {
                    val available = head.size - readOffset
                    if (available <= 0) {
                        queue.removeFirst()
                        readOffset = 0
                        continue
                    }
                    val count = minOf(available, length)
                    System.arraycopy(head, readOffset, dst, offset, count)
                    readOffset += count
                    if (readOffset >= head.size) {
                        queue.removeFirst()
                        readOffset = 0
                    }
                    return count
                }
                if (finished) return -1
                try {
                    lock.wait(200)
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return -1
                }
            }
        }
    }
}

/** 把 [PcmStreamBuffer] 包装成 media3 的 DataSource；长度未知，读到 EOF 为止。 */
class PcmStreamDataSource(private val stream: PcmStreamBuffer) : BaseDataSource(false) {
    private var uri: Uri = Uri.EMPTY
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        uri = dataSpec.uri
        opened = true
        transferStarted(dataSpec)
        return C.LENGTH_UNSET.toLong()
    }

    override fun getUri(): Uri = uri

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val count = stream.read(buffer, offset, length)
        if (count > 0) bytesTransferred(count)
        return count
    }

    override fun close() {
        if (!opened) return
        opened = false
        transferEnded()
    }
}

/**
 * MiMo 流式返回的 PCM 是 24 kHz / 单声道 / 16-bit LE（实测 48000 B/s）。
 * 播放器需要 WAV 容器，所以自己拼 44 字节头：流式播放时 data size 写未知，
 * 缓存落盘时写真实大小。
 */
object PcmWav {
    const val SAMPLE_RATE = 24_000
    const val CHANNELS = 1
    const val BITS_PER_SAMPLE = 16
    const val BYTES_PER_SECOND = SAMPLE_RATE * CHANNELS * BITS_PER_SAMPLE / 8

    fun liveHeader(): ByteArray = header(0xFFFFFFFFL)

    fun fileHeader(pcmBytes: Long): ByteArray = header(pcmBytes)

    fun durationMs(pcmBytes: Long): Long = pcmBytes * 1000L / BYTES_PER_SECOND

    private fun header(dataSize: Long): ByteArray {
        val out = ByteArray(44)
        // 流式时 RIFF size 也写未知，保持和 data size 一致。
        val riffSize = if (dataSize == 0xFFFFFFFFL) 0xFFFFFFFFL else (dataSize + 36L) and 0xFFFFFFFFL
        writeAscii(out, 0, "RIFF")
        writeLe32(out, 4, riffSize)
        writeAscii(out, 8, "WAVE")
        writeAscii(out, 12, "fmt ")
        writeLe32(out, 16, 16)
        writeLe16(out, 20, 1) // PCM
        writeLe16(out, 22, CHANNELS)
        writeLe32(out, 24, SAMPLE_RATE.toLong())
        writeLe32(out, 28, BYTES_PER_SECOND.toLong())
        writeLe16(out, 32, (CHANNELS * BITS_PER_SAMPLE / 8))
        writeLe16(out, 34, BITS_PER_SAMPLE)
        writeAscii(out, 36, "data")
        writeLe32(out, 40, dataSize)
        return out
    }

    private fun writeAscii(out: ByteArray, offset: Int, value: String) {
        for (i in value.indices) out[offset + i] = value[i].code.toByte()
    }

    private fun writeLe16(out: ByteArray, offset: Int, value: Int) {
        out[offset] = (value and 0xFF).toByte()
        out[offset + 1] = ((value ushr 8) and 0xFF).toByte()
    }

    private fun writeLe32(out: ByteArray, offset: Int, value: Long) {
        out[offset] = (value and 0xFF).toByte()
        out[offset + 1] = ((value ushr 8) and 0xFF).toByte()
        out[offset + 2] = ((value ushr 16) and 0xFF).toByte()
        out[offset + 3] = ((value ushr 24) and 0xFF).toByte()
    }
}
