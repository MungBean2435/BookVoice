package io.github.mungbean2435.bookvoice.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import java.util.Locale

/** 底部玻璃/镜头底色的默认值：中性灰，避免整块跟着 primary 发蓝。 */
internal val DefaultGlassTintArgb: Int = 0xFF8E8E8E.toInt()

/** 跟随主题时用 primary；否则用用户保存的 RGB。 */
internal fun resolvedGlassTint(followTheme: Boolean, argb: Int, primary: Color): Color =
    if (followTheme) primary else Color(argb)

/**
 * 镜头内图标/文字的颜色：沿用玻璃色的色相，但按明暗自动压深或提亮，
 * 避免「灰玻璃 + 浅灰字」在浅色主题下糊成一片。
 */
internal fun glassContentTint(glass: Color, dark: Boolean): Color {
    val lum = glass.luminance()
    return if (dark) {
        val target = 0.72f
        if (lum >= target) glass
        else lerp(glass, Color.White, ((target - lum) / (1f - lum).coerceAtLeast(0.001f)).coerceIn(0f, 0.88f))
    } else {
        val target = 0.08f
        if (lum <= target) glass
        else lerp(glass, Color.Black, (1f - target / lum.coerceAtLeast(0.001f)).coerceIn(0f, 0.88f))
    }
}

/**
 * 解析 `#RRGGBB`（`#` 可省）。只接受 6 位 RGB；
 * 镜头自己的按下透明度由渲染层决定，所以不允许带 alpha。
 */
internal fun parseGlassColor(input: String): Color? {
    val text = input.trim().removePrefix("#").removePrefix("#")
    if (text.length != 6) return null
    if (text.any { it.digitToIntOrNull(16) == null }) return null
    val rgb = text.toLongOrNull(16) ?: return null
    return Color(0xFF000000L or rgb)
}

/** `#RRGGBB`，用于设置页副标题与输入框回填。 */
internal fun glassColorHex(argb: Int): String =
    String.format(Locale.US, "#%06X", argb and 0xFFFFFF)
