package io.github.mungbean2435.bookvoice.tts

/** 时长估算与显示。没有真实全书时长，用 MiMo 实测的「每字约 196ms」。 */
object PlaybackMath {

    const val MS_PER_CHAR = 196.0

    fun secondsForChars(chars: Long): Long =
        (chars.coerceAtLeast(0) * MS_PER_CHAR / 1000).toLong()

    fun formatDuration(seconds: Long): String {
        val safe = seconds.coerceAtLeast(0)
        val hours = safe / 3600
        val minutes = (safe % 3600) / 60
        val secs = safe % 60
        return if (hours > 0) {
            String.format(java.util.Locale.US, "%d:%02d:%02d", hours, minutes, secs)
        } else {
            String.format(java.util.Locale.US, "%d:%02d", minutes, secs)
        }
    }
}
