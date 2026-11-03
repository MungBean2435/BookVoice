package io.github.mungbean2435.bookvoice.ui.components

import android.graphics.RenderEffect as AndroidRenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import kotlin.math.abs

/**
 * 镜头边缘折射。只对镜头自己的内容做偏移采样：
 *
 * - 圆角矩形 SDF 求法线；
 * - `sample = p - normal * refraction * (1 - depth)²`，中间 depth=1 时权重归零，保持原样；
 * - 强度随按下进度 0 → 1，松手回落到无折射。
 *
 * API < 33 或用户关闭液态玻璃时 [rememberTabLensRefraction] 返回 null，
 * 镜头退回第二阶段（照样涨起、放大、阴影），不抛异常。
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal class TabLensRefraction(
    widthPx: Float,
    heightPx: Float,
    private val edgeBandPx: Float,
    private val maxStrengthPx: Float
) {
    private val shader = RuntimeShader(LENS_REFRACTION_SHADER)

    val effect: RenderEffect

    private var lastStrength = Float.NaN
    private var lastScale = Float.NaN

    init {
        shader.setFloatUniform("size", widthPx, heightPx)
        shader.setFloatUniform("band", edgeBandPx)
        // 先给最大值：即使某些驱动不刷新 uniform，也至少是「有折射」而不是一片平的。
        shader.setFloatUniform("strength", maxStrengthPx)
        effect = AndroidRenderEffect
            .createRuntimeShaderEffect(shader, "content")
            .asComposeRenderEffect()
    }

    /**
     * 镜头内容层是静止尺寸，外层再 scale，所以 4dp / 12dp 要按当前缩放折算，
     * 视觉上的折射宽度才保持设计值；squash 时取横纵平均即可。
     */
    fun update(progress: Float, visualScale: Float) {
        val scale = visualScale.coerceIn(0.5f, 2f)
        val strength = maxStrengthPx * progress.coerceIn(0f, 1f) / scale
        val band = edgeBandPx / scale
        if (abs(scale - lastScale) >= 0.002f) {
            lastScale = scale
            shader.setFloatUniform("band", band)
        }
        if (abs(strength - lastStrength) >= 0.01f) {
            lastStrength = strength
            shader.setFloatUniform("strength", strength)
        }
    }
}

@Composable
internal fun rememberTabLensRefraction(
    enabled: Boolean,
    widthPx: Int,
    heightPx: Int,
    densityScale: Float
): TabLensRefraction? {
    if (!enabled || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
    if (widthPx <= 0 || heightPx <= 0 || densityScale <= 0f) return null
    return remember(widthPx, heightPx, densityScale) {
        runCatching {
            TabLensRefraction(
                widthPx = widthPx.toFloat(),
                heightPx = heightPx.toFloat(),
                edgeBandPx = LENS_EDGE_BAND_DP * densityScale,
                maxStrengthPx = LENS_REFRACTION_DP * densityScale
            )
        }.getOrNull()
    }
}

/** 折射 4dp、边缘带 12dp。 */
private const val LENS_REFRACTION_DP = 4f
private const val LENS_EDGE_BAND_DP = 12f

private const val LENS_REFRACTION_SHADER = """
uniform shader content;
uniform float2 size;
uniform float band;
uniform float strength;

float lensSdf(float2 p, float2 halfSize, float radius) {
    float2 q = abs(p) - halfSize + radius;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - radius;
}

half4 main(float2 p) {
    float2 center = size * 0.5;
    float radius = min(center.x, center.y);
    float2 halfSize = center;
    float dist = lensSdf(p - center, halfSize, radius);
    float depth = clamp(-dist / max(band, 1.0), 0.0, 1.0);
    float edge = (1.0 - depth) * (1.0 - depth);

    float2 dx = float2(
        lensSdf(p + float2(1.0, 0.0) - center, halfSize, radius) -
            lensSdf(p - float2(1.0, 0.0) - center, halfSize, radius),
        lensSdf(p + float2(0.0, 1.0) - center, halfSize, radius) -
            lensSdf(p - float2(0.0, 1.0) - center, halfSize, radius)
    );
    float lengthDx = length(dx);
    float2 normal = lengthDx > 0.0001 ? dx / lengthDx : float2(0.0);

    float2 edgeOffset = normal * (strength * edge);
    // 采样严格限制在镜头自己的画布内，边缘不回采到画布外，避免出现黑边/脏边。
    float2 samplePoint = clamp(p - edgeOffset, float2(0.5), size - float2(0.5));
    half4 sampled = content.eval(samplePoint);
    float2 chroma = normal * (strength * edge * 0.04);
    half red = content.eval(clamp(samplePoint - chroma, float2(0.5), size - float2(0.5))).r;
    half blue = content.eval(clamp(samplePoint + chroma, float2(0.5), size - float2(0.5))).b;
    return half4(red, sampled.g, blue, sampled.a);
}
"""
