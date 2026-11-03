package io.github.mungbean2435.bookvoice.ui.components

import androidx.compose.ui.graphics.ImageBitmap
import android.util.LruCache

/**
 * 封面位图内存缓存。
 *
 * 两个用途：
 * - 书架/胶囊/阅读页头部用同一个 path 时不再重复解码，切书也不会先闪一帧占位；
 * - 容器变形起飞的那一瞬间要拿“当前实际显示的那张图”，只能同步取，所以必须缓存。
 *
 * 只收封面这类小图（目标宽度 ≤ 512px）。阅读页整页插图是 1080px，不进缓存。
 */
object CoverCache {
    private const val MAX_BYTES = 12 * 1024 * 1024

    private val cache = object : LruCache<String, ImageBitmap>(MAX_BYTES) {
        override fun sizeOf(key: String, value: ImageBitmap): Int =
            value.width * value.height * 4
    }

    fun get(path: String?): ImageBitmap? {
        if (path.isNullOrBlank()) return null
        return synchronized(cache) { cache.get(path) }
    }

    /**
     * 同一路径只保留更大的那张：胶囊按 40dp 先解出来时是 107px，
     * 后面书架按 160dp 解出 427px 要能替换掉，否则变形飞起来是糊的。
     */
    fun put(path: String, bitmap: ImageBitmap) {
        if (path.isBlank()) return
        synchronized(cache) {
            val old = cache.get(path)
            if (old == null || old.width < bitmap.width) cache.put(path, bitmap)
        }
    }

    fun clear() {
        synchronized(cache) { cache.evictAll() }
    }

    /**
     * 封面文件是否真的存在：只有确实没有封面时才显示 EPUB 文字占位，
     * 路径失效（书被删了、缓存目录被清）不应该一直挂着中性色块。
     */
    fun fileExists(path: String?): Boolean {
        if (path.isNullOrBlank()) return false
        return runCatching { java.io.File(path).isFile }.getOrDefault(false)
    }
}
