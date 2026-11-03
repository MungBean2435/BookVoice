package io.github.mungbean2435.bookvoice.ui.glass

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Handler
import android.os.HandlerThread
import android.view.FrameMetrics
import android.view.Window
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext

/**
 * 档 3 运行时的掉帧看门狗。
 *
 * 用窗口的 FrameMetrics 监听，只在系统真的产帧时回调，不会像 withFrameNanos
 * 那样为了测量而一直请求帧。跳过开头 30 帧预热；连续 120 帧里超过 30 帧
 * 慢于 21ms 就判定掉帧，回调切回档 2。
 */
@Composable
fun LiquidGlassFrameWatcher(
    enabled: Boolean,
    onDegraded: () -> Unit
) {
    val context = LocalContext.current
    val activity = rememberActivity(context)
    val latestOnDegraded by rememberUpdatedState(onDegraded)

    DisposableEffect(enabled, activity) {
        val window = activity?.window
        if (!enabled || window == null) return@DisposableEffect onDispose {}

        val thread = HandlerThread("bookvoice-glass-frames").apply { start() }
        val handler = Handler(thread.looper)
        var warmup = WARMUP_FRAMES
        var frames = 0
        var slow = 0
        val listener = Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
            val total = metrics.getMetric(FrameMetrics.TOTAL_DURATION)
            if (warmup > 0) {
                warmup--
            } else if (total > SLOW_FRAME_NANOS) {
                slow++
            }
            if (warmup <= 0) {
                frames++
                if (frames >= SAMPLE_FRAMES) {
                    val degraded = slow >= SLOW_FRAME_LIMIT
                    frames = 0
                    slow = 0
                    if (degraded) activity.runOnUiThread { latestOnDegraded() }
                }
            }
        }

        val attached = runCatching {
            window.addOnFrameMetricsAvailableListener(listener, handler)
        }.isSuccess

        onDispose {
            if (attached) {
                runCatching { window.removeOnFrameMetricsAvailableListener(listener) }
            }
            thread.quitSafely()
        }
    }
}

private fun rememberActivity(context: Context): Activity? {
    var current: Context = context
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

private const val SAMPLE_FRAMES = 120
private const val SLOW_FRAME_LIMIT = 30
private const val SLOW_FRAME_NANOS = 21_000_000L
private const val WARMUP_FRAMES = 30
