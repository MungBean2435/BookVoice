package io.github.mungbean2435.bookvoice.ui.components

import android.graphics.BlurMaskFilter
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** 内阴影模糊半径 8dp，15% 黑；外阴影已经去掉，厚度全靠内阴影和内侧亮边。 */
private val LensInnerShadow = 8.dp
private const val LensInnerShadowAlpha = 0.15f

/** 按下时镜头内容上浮 1.5dp：抬升感用位移表达，不再靠放大外框。 */
private val LensContentLift = 1.5.dp

/**
 * 胶囊圆角：半径固定等于高度的一半。镜头 layer 的裁剪和 bar 内容层的抠洞共用这份形状，
 * 保证任何时候镜头都裁在自己的胶囊里，不会画出圆角外。
 */
internal val LensCapsuleShape: Shape = object : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline = Outline.Rounded(
        RoundRect(
            rect = Rect(Offset.Zero, size),
            cornerRadius = CornerRadius(size.height / 2f)
        )
    )
}

/**
 * 镜头本体。
 *
 * 布局尺寸固定为「静止镜头 rect」（tab 宽高各内缩 4dp），按下/拖动/形变全部走
 * `graphicsLayer`，所以每帧只重绘、不重新测量，也不会把 bar 撑开。
 *
 * 几何：位置、大小、缩放都由 [lensFrame] 统一算（与 bar 内容层抠洞用同一份），
 * 最终 rect 一定被 clamp 在 bar 的 4dp 内边距里（[marginPx]），放不下就压缩缩放。
 * 镜头 layer 自己按胶囊形状裁剪；不再向外投 14dp 阴影。
 *
 * 视觉顺序：胶囊底色（用户的玻璃色）→ 放大的 bar 内容（SrcIn 染成同一颜色，上浮 1.5dp）
 * → 内阴影 → 内侧 1px 亮边。
 */
@Composable
internal fun TabBarLens(
    state: TabBarLensState,
    contentLayer: GraphicsLayer,
    restWidthPx: Float,
    restHeightPx: Float,
    barWidthPx: Float,
    barHeightPx: Float,
    marginPx: Float,
    glassColor: Color,
    contentTint: Color,
    dark: Boolean,
    refractionEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val widthDp = with(density) { restWidthPx.toDp() }
    val heightDp = with(density) { restHeightPx.toDp() }

    // SrcIn 把镜头里录下的图标/文字统一染成同色系高对比色，同时保留原来的 alpha。
    // 换成灰玻璃后不会残留主题蓝，也不会出现浅灰字配灰玻璃的低对比。
    val tintPaint = remember(contentTint) {
        Paint().apply { colorFilter = ColorFilter.tint(contentTint, BlendMode.SrcIn) }
    }
    val innerShadowRadiusPx = with(density) { LensInnerShadow.toPx() }
    val contentLiftPx = with(density) { LensContentLift.toPx() }
    val refraction = rememberTabLensRefraction(
        enabled = refractionEnabled,
        widthPx = restWidthPx.roundToInt(),
        heightPx = restHeightPx.roundToInt(),
        densityScale = density.density
    )
    val innerShadowPaint = remember(innerShadowRadiusPx) {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = innerShadowRadiusPx * 2f
            maskFilter = BlurMaskFilter(innerShadowRadiusPx, BlurMaskFilter.Blur.NORMAL)
        }
    }

    Box(
        modifier = modifier
            .size(widthDp, heightDp)
            .graphicsLayer {
                val frame = lensFrame(
                    centerX = state.center.value,
                    barWidthPx = barWidthPx,
                    barHeightPx = barHeightPx,
                    restWidthPx = restWidthPx,
                    restHeightPx = restHeightPx,
                    press = state.press.value,
                    velocity = state.velocity.value,
                    marginPx = marginPx
                )
                if (frame != null) {
                    // 布局尺寸是静止镜头；缩放 + 平移把可视 rect 精确落到 frame.rect。
                    translationX = frame.rect.center.x - restWidthPx / 2f
                    translationY = frame.rect.center.y - restHeightPx / 2f
                    scaleX = frame.scaleX
                    scaleY = frame.scaleY
                    alpha = 1f
                } else {
                    alpha = 0f
                }
                shape = LensCapsuleShape
                clip = true
            }
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // 折射只作用在镜头自己的内容上，中间权重为 0，保持原样。
                    val progress = state.press.value.coerceIn(0f, 1f)
                    val frame = lensFrame(
                        centerX = state.center.value,
                        barWidthPx = barWidthPx,
                        barHeightPx = barHeightPx,
                        restWidthPx = restWidthPx,
                        restHeightPx = restHeightPx,
                        press = state.press.value,
                        velocity = state.velocity.value,
                        marginPx = marginPx
                    )
                    val lensEffect = refraction
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        lensEffect != null &&
                        frame != null &&
                        progress > 0.02f
                    ) {
                        lensEffect.update(progress, frame.visualScale)
                        renderEffect = lensEffect.effect
                        compositingStrategy = CompositingStrategy.Offscreen
                    } else {
                        renderEffect = null
                        compositingStrategy = CompositingStrategy.Auto
                    }
                }
        ) {
            val progress = state.press.value.coerceIn(0f, 1f)
            val frame = lensFrame(
                centerX = state.center.value,
                barWidthPx = barWidthPx,
                barHeightPx = barHeightPx,
                restWidthPx = restWidthPx,
                restHeightPx = restHeightPx,
                press = state.press.value,
                velocity = state.velocity.value,
                marginPx = marginPx
            ) ?: return@Canvas

            val magnification = lensMagnification(progress)
            val corner = CornerRadius(size.height / 2f)

            drawRoundRect(color = lensFill(glassColor, dark, progress), cornerRadius = corner)

            // 镜头里那一小块 bar：以镜头 rect 中心为轴放大，再用 SrcIn 着色。
            // 内层补偿外层缩放，所以视觉上始终是 magnification 倍；
            // 再按 1.5dp 上浮采样，做出按下抬升感（不动外框，不越界）。
            val liftInBarUnits = (contentLiftPx * progress) / magnification
            drawContext.canvas.saveLayer(
                Rect(0f, 0f, size.width, size.height),
                tintPaint
            )
            withTransform({
                translate(size.width / 2f, size.height / 2f)
                scale(
                    scaleX = magnification / frame.scaleX,
                    scaleY = magnification / frame.scaleY,
                    pivot = Offset.Zero
                )
                translate(
                    -frame.rect.center.x,
                    -frame.rect.center.y - liftInBarUnits
                )
            }) {
                drawLayer(contentLayer)
            }
            drawContext.canvas.restore()

            drawInnerShadow(progress, innerShadowPaint, innerShadowRadiusPx)
            drawSpecularRim(progress)
        }
    }
}

private fun lensFill(glassColor: Color, dark: Boolean, progress: Float): Color {
    val rest = glassColor.copy(alpha = if (dark) 0.20f else 0.14f)
    val lifted = glassColor.copy(alpha = if (dark) 0.34f else 0.26f)
    return lerp(rest, lifted, progress)
}

/** 圆角内侧一圈柔和的黑，镜头才有玻璃厚度；用固定模糊半径 + 随进度变 alpha。 */
private fun DrawScope.drawInnerShadow(
    progress: Float,
    paint: android.graphics.Paint,
    radius: Float
) {
    if (progress <= 0.01f) return
    val corner = size.height / 2f + radius
    paint.color = Color.Black.copy(alpha = LensInnerShadowAlpha * progress).toArgb()
    drawIntoCanvas { canvas ->
        canvas.nativeCanvas.drawRoundRect(
            -radius,
            -radius,
            size.width + radius,
            size.height + radius,
            corner,
            corner,
            paint
        )
    }
}

/** 内侧 1px 亮边：顶部更亮、底部一点反弹光，随按下淡入；描边内缩半个线宽，完全画在圆角里。 */
private fun DrawScope.drawSpecularRim(progress: Float) {
    if (progress <= 0.01f) return
    val stroke = 1.dp.toPx()
    val inset = stroke / 2f
    val rimWidth = (size.width - stroke).coerceAtLeast(0f)
    val rimHeight = (size.height - stroke).coerceAtLeast(0f)
    drawRoundRect(
        brush = Brush.verticalGradient(
            0f to Color.White.copy(alpha = 0.55f * progress),
            0.30f to Color.White.copy(alpha = 0.10f * progress),
            0.72f to Color.Transparent,
            1f to Color.White.copy(alpha = 0.30f * progress)
        ),
        topLeft = Offset(inset, inset),
        size = Size(rimWidth, rimHeight),
        cornerRadius = CornerRadius(rimHeight / 2f),
        style = Stroke(width = stroke)
    )
}
