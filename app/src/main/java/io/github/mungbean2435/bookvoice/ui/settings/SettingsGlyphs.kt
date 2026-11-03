package io.github.mungbean2435.bookvoice.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/** 设置页的线性图标；只画形状，不引 material-icons-extended。 */
internal enum class SettingsGlyph {
    Voice,
    Playback,
    Rules,
    Storage,
    BackupOut,
    BackupIn,
    Diagnostics,
    KeepAlive,
    About,
    Clear,
    Sun,
    Moon
}

@Composable
internal fun SettingsGlyphIcon(
    glyph: SettingsGlyph,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier) {
        val sw = 2.dp.toPx()
        when (glyph) {
            SettingsGlyph.Voice -> voice(tint)
            SettingsGlyph.Playback -> playback(tint)
            SettingsGlyph.Rules -> rules(tint)
            SettingsGlyph.Storage -> storage(tint, sw)
            SettingsGlyph.BackupOut -> backup(tint, sw, up = true)
            SettingsGlyph.BackupIn -> backup(tint, sw, up = false)
            SettingsGlyph.Diagnostics -> diagnostics(tint, sw)
            SettingsGlyph.KeepAlive -> keepAlive(tint, sw)
            SettingsGlyph.About -> about(tint, sw)
            SettingsGlyph.Clear -> clear(tint, sw)
            SettingsGlyph.Sun -> sun(tint, sw)
            SettingsGlyph.Moon -> moon(tint)
        }
    }
}

private fun DrawScope.voice(color: Color) {
    val heights = listOf(0.30f, 0.58f, 0.78f, 0.50f, 0.28f)
    val xs = listOf(0.14f, 0.32f, 0.50f, 0.68f, 0.86f)
    val barWidth = size.width * 0.09f
    heights.forEachIndexed { index, height ->
        val top = size.height * (0.5f - height / 2f)
        drawRoundRect(
            color = color,
            topLeft = Offset(size.width * xs[index] - barWidth / 2f, top),
            size = Size(barWidth, size.height * height),
            cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
        )
    }
}

private fun DrawScope.playback(color: Color) {
    val path = Path().apply {
        moveTo(size.width * 0.32f, size.height * 0.18f)
        lineTo(size.width * 0.80f, size.height * 0.5f)
        lineTo(size.width * 0.32f, size.height * 0.82f)
        close()
    }
    drawPath(path, color)
}

private fun DrawScope.rules(color: Color) {
    listOf(0.28f, 0.5f, 0.72f).forEach { y ->
        drawCircle(
            color = color,
            radius = size.width * 0.055f,
            center = Offset(size.width * 0.17f, size.height * y)
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.34f, size.height * y),
            end = Offset(size.width * 0.84f, size.height * y),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}

private fun DrawScope.storage(color: Color, stroke: Float) {
    listOf(0.16f, 0.40f, 0.64f).forEach { y ->
        drawRoundRect(
            color = color,
            topLeft = Offset(size.width * 0.18f, size.height * y),
            size = Size(size.width * 0.64f, size.height * 0.20f),
            cornerRadius = CornerRadius(size.width * 0.06f, size.width * 0.06f),
            style = Stroke(width = stroke)
        )
    }
}

private fun DrawScope.backup(color: Color, stroke: Float, up: Boolean) {
    drawLine(
        color, Offset(size.width * 0.22f, size.height * 0.54f),
        Offset(size.width * 0.22f, size.height * 0.82f), stroke, StrokeCap.Round
    )
    drawLine(
        color, Offset(size.width * 0.22f, size.height * 0.82f),
        Offset(size.width * 0.78f, size.height * 0.82f), stroke, StrokeCap.Round
    )
    drawLine(
        color, Offset(size.width * 0.78f, size.height * 0.82f),
        Offset(size.width * 0.78f, size.height * 0.54f), stroke, StrokeCap.Round
    )
    val shaftStart = if (up) 0.70f else 0.28f
    val shaftEnd = if (up) 0.28f else 0.66f
    val tipY = if (up) 0.24f else 0.70f
    val wingY = if (up) 0.42f else 0.52f
    drawLine(
        color, Offset(size.width * 0.5f, size.height * shaftStart),
        Offset(size.width * 0.5f, size.height * shaftEnd), stroke, StrokeCap.Round
    )
    drawLine(
        color, Offset(size.width * 0.5f, size.height * tipY),
        Offset(size.width * 0.34f, size.height * wingY), stroke, StrokeCap.Round
    )
    drawLine(
        color, Offset(size.width * 0.5f, size.height * tipY),
        Offset(size.width * 0.66f, size.height * wingY), stroke, StrokeCap.Round
    )
}

private fun DrawScope.diagnostics(color: Color, stroke: Float) {
    val path = Path().apply {
        moveTo(size.width * 0.08f, size.height * 0.56f)
        lineTo(size.width * 0.28f, size.height * 0.56f)
        lineTo(size.width * 0.38f, size.height * 0.28f)
        lineTo(size.width * 0.50f, size.height * 0.74f)
        lineTo(size.width * 0.60f, size.height * 0.46f)
        lineTo(size.width * 0.92f, size.height * 0.46f)
    }
    drawPath(
        path,
        color,
        style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
}

private fun DrawScope.keepAlive(color: Color, stroke: Float) {
    drawRoundRect(
        color = color,
        topLeft = Offset(size.width * 0.16f, size.height * 0.28f),
        size = Size(size.width * 0.62f, size.height * 0.44f),
        cornerRadius = CornerRadius(size.width * 0.08f, size.width * 0.08f),
        style = Stroke(width = stroke)
    )
    drawRoundRect(
        color = color,
        topLeft = Offset(size.width * 0.80f, size.height * 0.42f),
        size = Size(size.width * 0.06f, size.height * 0.16f),
        cornerRadius = CornerRadius(size.width * 0.02f, size.width * 0.02f)
    )
    val bolt = Path().apply {
        moveTo(size.width * 0.50f, size.height * 0.34f)
        lineTo(size.width * 0.36f, size.height * 0.55f)
        lineTo(size.width * 0.47f, size.height * 0.55f)
        lineTo(size.width * 0.42f, size.height * 0.72f)
        lineTo(size.width * 0.62f, size.height * 0.48f)
        lineTo(size.width * 0.51f, size.height * 0.48f)
        lineTo(size.width * 0.56f, size.height * 0.34f)
        close()
    }
    drawPath(bolt, color)
}

private fun DrawScope.about(color: Color, stroke: Float) {
    drawCircle(
        color = color,
        radius = size.width * 0.30f,
        center = Offset(size.width * 0.5f, size.height * 0.5f),
        style = Stroke(width = stroke)
    )
    drawCircle(
        color = color,
        radius = size.width * 0.035f,
        center = Offset(size.width * 0.5f, size.height * 0.33f)
    )
    drawLine(
        color, Offset(size.width * 0.5f, size.height * 0.45f),
        Offset(size.width * 0.5f, size.height * 0.68f), stroke, StrokeCap.Round
    )
}

private fun DrawScope.clear(color: Color, stroke: Float) {
    drawLine(
        color, Offset(size.width * 0.22f, size.height * 0.30f),
        Offset(size.width * 0.78f, size.height * 0.30f), stroke, StrokeCap.Round
    )
    drawLine(
        color, Offset(size.width * 0.40f, size.height * 0.30f),
        Offset(size.width * 0.40f, size.height * 0.22f), stroke, StrokeCap.Round
    )
    drawLine(
        color, Offset(size.width * 0.60f, size.height * 0.30f),
        Offset(size.width * 0.60f, size.height * 0.22f), stroke, StrokeCap.Round
    )
    drawLine(
        color, Offset(size.width * 0.40f, size.height * 0.22f),
        Offset(size.width * 0.60f, size.height * 0.22f), stroke, StrokeCap.Round
    )
    val body = Path().apply {
        moveTo(size.width * 0.30f, size.height * 0.38f)
        lineTo(size.width * 0.35f, size.height * 0.80f)
        lineTo(size.width * 0.65f, size.height * 0.80f)
        lineTo(size.width * 0.70f, size.height * 0.38f)
    }
    drawPath(
        body,
        color,
        style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
    drawLine(
        color, Offset(size.width * 0.44f, size.height * 0.46f),
        Offset(size.width * 0.45f, size.height * 0.70f), stroke, StrokeCap.Round
    )
    drawLine(
        color, Offset(size.width * 0.56f, size.height * 0.46f),
        Offset(size.width * 0.55f, size.height * 0.70f), stroke, StrokeCap.Round
    )
}

private fun DrawScope.sun(color: Color, stroke: Float) {
    drawCircle(
        color = color,
        radius = size.width * 0.20f,
        center = Offset(size.width * 0.5f, size.height * 0.5f),
        style = Stroke(width = stroke)
    )
    for (index in 0 until 8) {
        val angle = Math.toRadians((index * 45).toDouble())
        val inner = 0.30f
        val outer = 0.43f
        drawLine(
            color = color,
            start = Offset(
                size.width * (0.5f + (inner * cos(angle)).toFloat()),
                size.height * (0.5f + (inner * sin(angle)).toFloat())
            ),
            end = Offset(
                size.width * (0.5f + (outer * cos(angle)).toFloat()),
                size.height * (0.5f + (outer * sin(angle)).toFloat())
            ),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}

private fun DrawScope.moon(color: Color) {
    val outer = Path().apply {
        addOval(
            Rect(
                left = size.width * 0.16f,
                top = size.height * 0.14f,
                right = size.width * 0.84f,
                bottom = size.height * 0.86f
            )
        )
    }
    val inner = Path().apply {
        addOval(
            Rect(
                left = size.width * 0.40f,
                top = size.height * 0.08f,
                right = size.width * 0.94f,
                bottom = size.height * 0.66f
            )
        )
    }
    val crescent = Path().apply {
        op(outer, inner, PathOperation.Difference)
    }
    drawPath(crescent, color)
}
