package io.github.mungbean2435.bookvoice.ui.morph

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import io.github.mungbean2435.bookvoice.ui.theme.ContinuousRoundedCornerShape

/**
 * 容器变形的起点快照。所有 Rect 都是窗口坐标（px），在点击的那一刻冻结：
 * 列表可能马上滚走 / 回收，动画期间不能再回头去问列表要位置。
 *
 * @param cardRect 卡片起点：书架是封面矩形，胶囊是胶囊左侧那格 40dp 封面。
 * @param cardCornerPx 起点圆角（封面形状的圆角）。
 * @param cardColor 起点底色，卡片从这个颜色渐变到阅读页底色。
 * @param coverRect 飞出去的封面起始矩形（一般与 cardRect 相同）。
 * @param cover 当前实际显示的那张封面位图；没有就退化成一团底色。
 */
data class MorphOrigin(
    val bookId: String,
    val title: String,
    val cardRect: Rect,
    val cardCornerPx: Float,
    val cardColor: Color,
    val coverRect: Rect,
    val cover: ImageBitmap?
)

/** 起点和全屏矩形之间按 p 插值。 */
fun lerpRect(from: Rect, to: Rect, p: Float): Rect = Rect(
    left = from.left + (to.left - from.left) * p,
    top = from.top + (to.top - from.top) * p,
    right = from.right + (to.right - from.right) * p,
    bottom = from.bottom + (to.bottom - from.bottom) * p
)

/** 把形状的圆角取成 px，供数值插值用。只认圆角形状，其它形状当 0。 */
fun Shape.cornerRadiusPx(density: Density): Float = when (this) {
    is RoundedCornerShape -> topStart.toPx(Size.Zero, density)
    is ContinuousRoundedCornerShape -> topStart.toPx(Size.Zero, density)
    else -> 0f
}

/**
 * 底层页面的“随进度变圆”的形状：圆角在绘制时才读 [progress]，不会因为每帧变化重组。
 */
class ProgressRoundedShape(
    private val maxRadius: Dp,
    private val progress: () -> Float
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val radius = with(density) { maxRadius.toPx() } * progress().coerceIn(0f, 1f)
        return Outline.Rounded(RoundRect(Rect(Offset.Zero, size), CornerRadius(radius)))
    }
}

/**
 * 返回手势的进度 → 变形进度。
 * 手势拉满时只把阅读页缩到 60%，松手提交后剩下的交给关闭 tween，
 * 这样返回手势看起来是“在往回推”，而不是“一下子收没”。
 */
fun backProgressToMorph(back: Float, factor: Float): Float =
    1f - back.coerceIn(0f, 1f) * factor
