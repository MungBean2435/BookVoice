package io.github.mungbean2435.bookvoice.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.math.hypot
import kotlin.math.max

interface ThemeRevealController {
    suspend fun reveal(
        originInWindow: Offset,
        durationMs: Int,
        change: () -> Unit
    )
}

val LocalThemeRevealController = staticCompositionLocalOf<ThemeRevealController?> { null }

/**
 * 换肤揭示宿主：先截住旧窗口，再把新内容按按钮圆心扩成圆形。
 *
 * PixelCopy 只在这里用一次；不参与日常绘制。截图失败就退化为直接换肤。
 */
@Composable
fun ThemeRevealHost(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    var snapshot by remember { mutableStateOf<ImageBitmap?>(null) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    var revealing by remember { mutableStateOf(false) }
    val progress = remember { Animatable(0f) }

    val controller = remember(activity) {
        object : ThemeRevealController {
            override suspend fun reveal(
                originInWindow: Offset,
                durationMs: Int,
                change: () -> Unit
            ) {
                if (activity == null || revealing) {
                    change()
                    return
                }
                revealing = true
                try {
                    val bitmap = captureWindow(activity)
                    if (bitmap == null) {
                        change()
                        return
                    }
                    snapshot = bitmap.asImageBitmap()
                    origin = originInWindow
                    progress.snapTo(0f)
                    change()
                    // 等新主题完成一次组合与绘制，再从 0 扩到全屏。
                    withFrameNanos { }
                    progress.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(
                            durationMillis = durationMs,
                            easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
                        )
                    )
                    snapshot = null
                    progress.snapTo(0f)
                } finally {
                    revealing = false
                    snapshot = null
                    if (progress.value != 0f) {
                        runCatching { progress.snapTo(0f) }
                    }
                }
            }
        }
    }

    CompositionLocalProvider(LocalThemeRevealController provides controller) {
        Box(Modifier.fillMaxSize()) {
            snapshot?.let { image ->
                Image(
                    bitmap = image,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        val oldFrame = snapshot
                        if (oldFrame == null) {
                            drawContent()
                            return@drawWithContent
                        }
                        val maxRadius = hypot(
                            max(origin.x, size.width - origin.x).toDouble(),
                            max(origin.y, size.height - origin.y).toDouble()
                        ).toFloat() + 1f
                        val radius = (maxRadius * progress.value).coerceAtLeast(0f)
                        val circle = Path().apply {
                            addOval(Rect(center = origin, radius = radius))
                        }
                        val contentScope = this
                        clipPath(circle) {
                            contentScope.drawContent()
                        }
                    }
            ) {
                content()
            }
        }
    }
}

private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return current as? Activity
}

private suspend fun captureWindow(activity: Activity): Bitmap? =
    suspendCancellableCoroutine { continuation ->
        val window = activity.window
        val view = window.decorView
        val width = view.width
        val height = view.height
        if (width <= 0 || height <= 0) {
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        PixelCopy.request(
            window,
            bitmap,
            { result ->
                if (!continuation.isActive) {
                    bitmap.recycle()
                } else if (result == PixelCopy.SUCCESS) {
                    continuation.resume(bitmap)
                } else {
                    bitmap.recycle()
                    continuation.resume(null)
                }
            },
            Handler(Looper.getMainLooper())
        )
    }
