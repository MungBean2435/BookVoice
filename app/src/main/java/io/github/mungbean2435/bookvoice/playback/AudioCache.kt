package io.github.mungbean2435.bookvoice.playback

import android.content.Context
import java.io.File
import java.security.MessageDigest

/**
 * 音频缓存：一个合成块一个 wav 文件，按内容寻址。
 * key 里不含语速（变速在播放端），但含规则指纹（规则改了要重新合成）。
 */
class AudioCache(context: Context) {

    private val dir = File(context.filesDir, "audio").apply { mkdirs() }

    fun key(providerId: String, model: String, voice: String, format: String, rulesHash: String, text: String): String {
        val raw = "$providerId|$model|$voice|$format|$rulesHash|$text"
        val digest = MessageDigest.getInstance("SHA-1").digest(raw.toByteArray(Charsets.UTF_8))
        val sb = StringBuilder(digest.size * 2)
        for (byte in digest) sb.append("%02x".format(byte))
        return sb.toString()
    }

    fun existing(key: String, ext: String): File? {
        val file = File(dir, "$key.$ext")
        return if (file.exists() && file.length() > 1) file else null
    }

    fun store(key: String, bytes: ByteArray, ext: String): File {
        val file = File(dir, "$key.$ext")
        val tmp = File(dir, "$key.tmp")
        tmp.writeBytes(bytes)
        if (file.exists()) file.delete()
        tmp.renameTo(file)
        return file
    }

    fun totalBytes(): Long = dir.listFiles()?.sumOf { it.length() } ?: 0L

    fun fileCount(): Int = dir.listFiles()?.size ?: 0

    fun clear() {
        dir.listFiles()?.forEach { it.delete() }
    }

    /** 超上限就按最久未使用删，直到降到 80%。 */
    fun trim(limitBytes: Long) {
        val files = dir.listFiles()?.filter { it.isFile } ?: return
        var total = files.sumOf { it.length() }
        if (total <= limitBytes) return
        val target = (limitBytes * 8 / 10).coerceAtLeast(0)
        files.sortedBy { it.lastModified() }.forEach { file ->
            if (total <= target) return
            val size = file.length()
            if (file.delete()) total -= size
        }
    }
}
