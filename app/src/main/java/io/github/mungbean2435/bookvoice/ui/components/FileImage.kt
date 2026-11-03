package io.github.mungbean2435.bookvoice.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 封面（目标宽度 ≤ 512px）走内存缓存；更大的整页插图仍然每次现解。 */
private const val CACHE_MAX_TARGET_PX = 512

/**
 * 最小图片加载：按目标宽度采样解码，避免把整本 EPUB 的插图撑爆内存。
 *
 * @param targetWidthPx 期望解码宽度。整页插图默认 1080；书架 56dp 缩略图传实际像素，
 * 否则会为一张小图解码 1080px 的 Bitmap。
 * 换 Coil 是 M5 之后再考虑的事。
 */
@Composable
fun FileImage(
    path: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.FillWidth,
    targetWidthPx: Int = 1080
) {
    val cacheable = targetWidthPx <= CACHE_MAX_TARGET_PX
    var bitmap by remember(path, targetWidthPx) {
        mutableStateOf(if (cacheable) CoverCache.get(path) else null)
    }
    LaunchedEffect(path, targetWidthPx) {
        if (bitmap != null) return@LaunchedEffect
        val loaded = withContext(Dispatchers.IO) { decodeSampled(path, targetWidthPx) }
        if (loaded != null) {
            if (cacheable) CoverCache.put(path, loaded)
            bitmap = loaded
        }
    }
    bitmap?.let {
        Image(
            bitmap = it,
            contentDescription = null,
            modifier = modifier,
            contentScale = contentScale
        )
    }
}

/**
 * 取一张封面并把它交给调用方：先读内存缓存（命中就同步返回，第一帧不空），
 * 没有再用 [decodeSampled] 解码。调用方拿到的是“正在显示”的那个对象，
 * 容器变形带走它就不会在起飞瞬间跳一下。
 */
@Composable
fun rememberCoverBitmap(path: String?, targetWidthPx: Int): ImageBitmap? {
    var bitmap by remember(path, targetWidthPx) { mutableStateOf(CoverCache.get(path)) }
    LaunchedEffect(path, targetWidthPx) {
        if (path == null || bitmap != null) return@LaunchedEffect
        val loaded = withContext(Dispatchers.IO) { decodeSampled(path, targetWidthPx) }
        if (loaded != null) {
            CoverCache.put(path, loaded)
            bitmap = loaded
        }
    }
    return bitmap
}

/** 两级采样：先把边长除到目标宽度附近，再整张解出来。 */
internal fun decodeSampled(path: String, targetWidthPx: Int): ImageBitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    val target = targetWidthPx.coerceAtLeast(1)
    var sample = 1
    while (bounds.outWidth > 0 && bounds.outWidth / (sample * 2) >= target) {
        sample *= 2
    }
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    BitmapFactory.decodeFile(path, options)?.asImageBitmap()
}.getOrNull()
