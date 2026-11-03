package io.github.mungbean2435.bookvoice.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope

internal enum class TransportGlyph {
    PreviousChapter,
    PreviousBlock,
    Play,
    Pause,
    NextBlock,
    NextChapter
}

/**
 * 五个传输键自绘图标。不引 material-icons-extended：
 * 只要 5 个形状，没必要为此增加方法数与包体。
 */
@Composable
internal fun TransportGlyphIcon(
    glyph: TransportGlyph,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier) {
        when (glyph) {
            TransportGlyph.Play -> play(this, tint)
            TransportGlyph.Pause -> pause(this, tint)
            TransportGlyph.PreviousBlock -> triangleLeft(this, tint, 0.74f, 0.28f)
            TransportGlyph.NextBlock -> triangleRight(this, tint, 0.26f, 0.72f)
            TransportGlyph.PreviousChapter -> {
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(size.width * 0.12f, size.height * 0.16f),
                    size = Size(size.width * 0.08f, size.height * 0.68f),
                    cornerRadius = CornerRadius(2f, 2f)
                )
                triangleLeft(this, tint, 0.94f, 0.64f)
                triangleLeft(this, tint, 0.62f, 0.32f)
            }

            TransportGlyph.NextChapter -> {
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(size.width * 0.80f, size.height * 0.16f),
                    size = Size(size.width * 0.08f, size.height * 0.68f),
                    cornerRadius = CornerRadius(2f, 2f)
                )
                triangleRight(this, tint, 0.06f, 0.36f)
                triangleRight(this, tint, 0.38f, 0.68f)
            }
        }
    }
}

private fun play(scope: DrawScope, color: Color) = with(scope) {
    val path = Path().apply {
        moveTo(size.width * 0.28f, size.height * 0.14f)
        lineTo(size.width * 0.82f, size.height * 0.5f)
        lineTo(size.width * 0.28f, size.height * 0.86f)
        close()
    }
    drawPath(path, color)
}

private fun pause(scope: DrawScope, color: Color) = with(scope) {
    val barWidth = size.width * 0.13f
    val barHeight = size.height * 0.68f
    drawRoundRect(
        color = color,
        topLeft = Offset(size.width * 0.30f, size.height * 0.16f),
        size = Size(barWidth, barHeight),
        cornerRadius = CornerRadius(2f, 2f)
    )
    drawRoundRect(
        color = color,
        topLeft = Offset(size.width * 0.57f, size.height * 0.16f),
        size = Size(barWidth, barHeight),
        cornerRadius = CornerRadius(2f, 2f)
    )
}

private fun triangleLeft(scope: DrawScope, color: Color, right: Float, left: Float) = with(scope) {
    val path = Path().apply {
        moveTo(size.width * right, size.height * 0.14f)
        lineTo(size.width * left, size.height * 0.5f)
        lineTo(size.width * right, size.height * 0.86f)
        close()
    }
    drawPath(path, color)
}

private fun triangleRight(scope: DrawScope, color: Color, left: Float, right: Float) = with(scope) {
    val path = Path().apply {
        moveTo(size.width * left, size.height * 0.14f)
        lineTo(size.width * right, size.height * 0.5f)
        lineTo(size.width * left, size.height * 0.86f)
        close()
    }
    drawPath(path, color)
}
