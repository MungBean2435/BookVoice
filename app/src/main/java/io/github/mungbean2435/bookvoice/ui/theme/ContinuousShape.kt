package io.github.mungbean2435.bookvoice.ui.theme

import android.os.Build
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.min

/*
 * 连续圆角：拐角沿边延长 1.1 倍，三次贝塞尔控制柄为 0.643。
 * 参数来自公开的 miuix squircle 曲线；这里是按数值独立实现。
 */

internal const val CONTINUOUS_CORNER_EXTENSION = 1.1f
private const val CONTINUOUS_CORNER_CONTROL = 0.643f
private const val MAX_GENERIC_OUTLINE_AXIS_PX = 8192f

private fun canUseGenericOutline(width: Float, height: Float): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
        width < MAX_GENERIC_OUTLINE_AXIS_PX &&
        height < MAX_GENERIC_OUTLINE_AXIS_PX

/**
 * 四个角分别可配的连续曲率圆角。
 *
 * 与正圆弧不同，曲线从离角更远的切点开始，再用较短的贝塞尔控制柄收拢，
 * 视觉上更接近 squircle。超出平台能力时退回普通圆角，避免裁剪异常。
 */
class ContinuousRoundedCornerShape(
    topStart: CornerSize,
    topEnd: CornerSize,
    bottomEnd: CornerSize,
    bottomStart: CornerSize,
    private val extension: Float = CONTINUOUS_CORNER_EXTENSION
) : CornerBasedShape(topStart, topEnd, bottomEnd, bottomStart) {

    override fun copy(
        topStart: CornerSize,
        topEnd: CornerSize,
        bottomEnd: CornerSize,
        bottomStart: CornerSize
    ): ContinuousRoundedCornerShape = ContinuousRoundedCornerShape(
        topStart = topStart,
        topEnd = topEnd,
        bottomEnd = bottomEnd,
        bottomStart = bottomStart,
        extension = extension
    )

    override fun createOutline(
        size: Size,
        topStart: Float,
        topEnd: Float,
        bottomEnd: Float,
        bottomStart: Float,
        layoutDirection: LayoutDirection
    ): Outline {
        if (size.width <= 0f || size.height <= 0f) {
            return Outline.Rectangle(size.toRect())
        }
        val topLeft = (if (layoutDirection == LayoutDirection.Ltr) topStart else topEnd)
            .coerceAtLeast(0f)
        val topRight = (if (layoutDirection == LayoutDirection.Ltr) topEnd else topStart)
            .coerceAtLeast(0f)
        val bottomRight = (if (layoutDirection == LayoutDirection.Ltr) bottomEnd else bottomStart)
            .coerceAtLeast(0f)
        val bottomLeft = (if (layoutDirection == LayoutDirection.Ltr) bottomStart else bottomEnd)
            .coerceAtLeast(0f)
        if (topLeft + topRight + bottomRight + bottomLeft == 0f) {
            return Outline.Rectangle(size.toRect())
        }
        if (!canUseGenericOutline(size.width, size.height)) {
            return Outline.Rounded(
                RoundRect(
                    rect = size.toRect(),
                    topLeft = CornerRadius(topLeft),
                    topRight = CornerRadius(topRight),
                    bottomRight = CornerRadius(bottomRight),
                    bottomLeft = CornerRadius(bottomLeft)
                )
            )
        }

        val shortSideHalf = min(size.width, size.height) * 0.5f
        val curveExtension = extension.coerceIn(1f, 2f)
        fun cornerRun(radius: Float): Float =
            (radius * curveExtension).coerceIn(0f, shortSideHalf)

        val tl = cornerRun(topLeft)
        val tr = cornerRun(topRight)
        val br = cornerRun(bottomRight)
        val bl = cornerRun(bottomLeft)
        // 控制柄离角越近，曲线越平；0.643 即手柄占拐角长度的 64.3%。
        val handle = 1f - CONTINUOUS_CORNER_CONTROL

        val path = Path().apply {
            moveTo(tl, 0f)
            lineTo(size.width - tr, 0f)
            cubicTo(
                size.width - tr * handle, 0f,
                size.width, tr * handle,
                size.width, tr
            )
            lineTo(size.width, size.height - br)
            cubicTo(
                size.width, size.height - br * handle,
                size.width - br * handle, size.height,
                size.width - br, size.height
            )
            lineTo(bl, size.height)
            cubicTo(
                bl * handle, size.height,
                0f, size.height - bl * handle,
                0f, size.height - bl
            )
            lineTo(0f, tl)
            cubicTo(
                0f, tl * handle,
                tl * handle, 0f,
                tl, 0f
            )
            close()
        }
        return Outline.Generic(path)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ContinuousRoundedCornerShape) return false
        return topStart == other.topStart &&
            topEnd == other.topEnd &&
            bottomEnd == other.bottomEnd &&
            bottomStart == other.bottomStart &&
            extension == other.extension
    }

    override fun hashCode(): Int {
        var result = topStart.hashCode()
        result = 31 * result + topEnd.hashCode()
        result = 31 * result + bottomEnd.hashCode()
        result = 31 * result + bottomStart.hashCode()
        result = 31 * result + extension.hashCode()
        return result
    }

    override fun toString(): String =
        "ContinuousRoundedCornerShape(" +
            "topStart=$topStart, topEnd=$topEnd, " +
            "bottomEnd=$bottomEnd, bottomStart=$bottomStart)"
}

fun ContinuousRoundedCornerShape(radius: Dp): ContinuousRoundedCornerShape {
    val corner = CornerSize(radius)
    return ContinuousRoundedCornerShape(
        topStart = corner,
        topEnd = corner,
        bottomEnd = corner,
        bottomStart = corner
    )
}

fun ContinuousRoundedCornerShape(
    topStart: Dp,
    topEnd: Dp,
    bottomEnd: Dp,
    bottomStart: Dp
): ContinuousRoundedCornerShape = ContinuousRoundedCornerShape(
    topStart = CornerSize(topStart),
    topEnd = CornerSize(topEnd),
    bottomEnd = CornerSize(bottomEnd),
    bottomStart = CornerSize(bottomStart)
)
