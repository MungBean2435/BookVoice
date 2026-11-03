package io.github.mungbean2435.bookvoice.ui.components

import androidx.compose.ui.geometry.Rect
import kotlin.math.abs
import kotlin.math.min

/** 速度驱动的形变：沿行进方向拉伸 [stretch]，垂直方向压扁 [squash]。 */
internal data class LensSquash(val stretch: Float, val squash: Float)

/** 按下的目标缩放：1 → 1.34；真正绘制时会压到胶囊内放得下的大小。 */
internal fun lensPressScale(progress: Float): Float =
    1f + 0.34f * progress.coerceIn(0f, 1f)

/** 镜头内 bar 内容的放大：1 → 1.2；比镜头本身涨得慢，所以镜片会「呼吸」。 */
internal fun lensMagnification(progress: Float): Float =
    1f + 0.20f * progress.coerceIn(0f, 1f)

/**
 * 速度越大，横向拉得越长、纵向越扁；上限 14% / 6%，避免高速拖动时失真。
 * 速度先按镜头宽度归一化，不同屏幕宽度的手感一致。
 */
internal fun lensSquash(velocityPx: Float, referenceWidthPx: Float): LensSquash {
    val width = referenceWidthPx.coerceAtLeast(1f)
    val normalized = abs(velocityPx) / (width * 12f)
    val stretch = (normalized * 0.6f).coerceAtMost(0.14f)
    val squash = (normalized * 0.12f).coerceAtMost(0.06f)
    return LensSquash(stretch = 1f + stretch, squash = 1f - squash)
}

/**
 * 镜头这一帧最终要画成什么样，全部是 bar 本地坐标。
 *
 * [rect] 已经 clamp 过：只要 [marginPx] 非负，四边都不会越过它；
 * 放不下时先压缩 [scaleX] / [scaleY]，不会画到框外。
 * [scaleX] / [scaleY] 是相对镜头静止尺寸的倍数，绘制层直接拿去做 graphicsLayer 缩放。
 */
internal data class LensFrame(
    val rect: Rect,
    val scaleX: Float,
    val scaleY: Float
) {
    /** 折射、内阴影按当前视觉缩放折算用；squash 时取横纵平均。 */
    val visualScale: Float get() = (scaleX + scaleY) / 2f
}

/**
 * 先量后画：镜头 rect 由 tab rect（已在调用方内缩成静止镜头）推导，
 * 加上按下缩放和速度形变，再整体 clamp 到 bar bounds。
 *
 * - 缩放基准是镜头自身的静止宽高，不缩外层容器；
 * - 纵向可用高度 = barHeight - 2 * margin（默认就是 barHeight - 8dp），
 *   所以按下缩放先按「能放下的最大等比倍数」压一次，再乘速度 squash；
 * - 最后按 rect 半宽/半高把中心 clamp 进可画区域，保证四边都不越界。
 *
 * [marginPx] 为负表示允许溢出（containLens = false 时传 -5dp）。
 * 未测量出 bar / 镜头尺寸，或镜头中心还是 NaN 时返回 null，调用方跳过绘制。
 */
internal fun lensFrame(
    centerX: Float,
    barWidthPx: Float,
    barHeightPx: Float,
    restWidthPx: Float,
    restHeightPx: Float,
    press: Float,
    velocity: Float,
    marginPx: Float
): LensFrame? {
    if (!centerX.isFinite() ||
        barWidthPx <= 0f || barHeightPx <= 0f ||
        restWidthPx <= 0f || restHeightPx <= 0f
    ) {
        return null
    }

    // 极端小 bar 也至少留 1px 可画区域；margin 为负时保持负值（允许溢出）。
    val margin = marginPx
        .coerceAtMost(barWidthPx / 2f - 0.5f)
        .coerceAtMost(barHeightPx / 2f - 0.5f)
    val left = margin
    val top = margin
    val right = barWidthPx - margin
    val bottom = barHeightPx - margin
    val allowedWidth = (right - left).coerceAtLeast(1f)
    val allowedHeight = (bottom - top).coerceAtLeast(1f)

    val maxScaleX = allowedWidth / restWidthPx
    val maxScaleY = allowedHeight / restHeightPx
    // 按下放大先整体压进可用区域（保持等比），再把 squash 的拉伸/压扁按各轴上限制一次。
    // containLens = true 时可用高度正好等于静止高度，所以这里会压到 1，镜头外框不再变大，
    // 抬升感交给内容位移 + 内阴影 + 内侧亮边（见 TabBarLens）。
    val baseScale = min(lensPressScale(press), min(maxScaleX, maxScaleY))
    val squash = lensSquash(velocity, restWidthPx)
    val scaleX = min(baseScale * squash.stretch, maxScaleX)
    val scaleY = min(baseScale * squash.squash, maxScaleY)

    val width = restWidthPx * scaleX
    val height = restHeightPx * scaleY
    val minCenterX = left + width / 2f
    val maxCenterX = right - width / 2f
    val cx = if (minCenterX <= maxCenterX) {
        centerX.coerceIn(minCenterX, maxCenterX)
    } else {
        (left + right) / 2f
    }
    val centerY = (top + bottom) / 2f
    val cy = if (height >= allowedHeight) {
        centerY
    } else {
        centerY.coerceIn(top + height / 2f, bottom - height / 2f)
    }

    return LensFrame(
        rect = Rect(
            left = cx - width / 2f,
            top = cy - height / 2f,
            right = cx + width / 2f,
            bottom = cy + height / 2f
        ),
        scaleX = scaleX,
        scaleY = scaleY
    )
}
