package io.github.mungbean2435.bookvoice.ui.glass

import android.graphics.RenderEffect as AndroidRenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect

/**
 * 胶囊的 RenderEffect 只跟「档位 + 尺寸 + 模糊半径 + density」有关，滚动时尺寸不变，
 * 所以 remember 命中后不会再创建；失败时返回 null，交给调用方落回纯色/档 2。
 */
@Composable
fun rememberNavGlassEffect(
    tier: GlassTier,
    widthPx: Int,
    heightPx: Int,
    cornerRadiusPx: Float,
    blurRadiusPx: Float,
    densityScale: Float,
    onLiquidFailure: () -> Unit
): RenderEffect? {
    val effect = remember(tier, widthPx, heightPx, cornerRadiusPx, blurRadiusPx, densityScale) {
        if (widthPx <= 0 || heightPx <= 0 || cornerRadiusPx <= 0f || blurRadiusPx <= 0f) {
            return@remember null
        }
        runCatching {
            when (tier) {
                GlassTier.Solid -> null
                GlassTier.Blur -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    blurEffect(blurRadiusPx)
                } else {
                    null
                }

                GlassTier.Liquid -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    liquidEffect(
                        widthPx = widthPx.toFloat(),
                        heightPx = heightPx.toFloat(),
                        cornerRadiusPx = cornerRadiusPx,
                        blurRadiusPx = blurRadiusPx,
                        densityScale = densityScale
                    )
                } else {
                    null
                }
            }
        }.getOrNull()
    }
    val latestFailure by rememberUpdatedState(onLiquidFailure)
    LaunchedEffect(tier, effect, widthPx, heightPx) {
        // 首帧还没测量时 effect 必然为 null，不能当成渲染失败。
        if (tier == GlassTier.Liquid && effect == null && widthPx > 0 && heightPx > 0) {
            latestFailure()
        }
    }
    return effect
}

@RequiresApi(Build.VERSION_CODES.S)
private fun blurEffect(radiusPx: Float): RenderEffect =
    AndroidRenderEffect
        .createBlurEffect(radiusPx, radiusPx, Shader.TileMode.CLAMP)
        .asComposeRenderEffect()

/**
 * 先 28dp 模糊，再在边缘窄带做折射。中间 weight 为 0，保持普通毛玻璃；
 * 边缘 (1-depth)^2 渐入，同时用 1.6dp 的高光收边。
 *
 * 用 createChainEffect(refraction, blur)：blur 先作用于页面快照，折射着色器
 * 通过 "content" 子着色器读取模糊后的结果。
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun liquidEffect(
    widthPx: Float,
    heightPx: Float,
    cornerRadiusPx: Float,
    blurRadiusPx: Float,
    densityScale: Float
): RenderEffect {
    val shader = RuntimeShader(LIQUID_EDGE_SHADER)
    shader.setFloatUniform("size", widthPx, heightPx)
    shader.setFloatUniform("band", EDGE_BAND_DP * densityScale)
    shader.setFloatUniform("strength", REFRACTION)
    shader.setFloatUniform("rim", RIM_DP * densityScale)
    shader.setFloatUniform("rimHighlight", RIM_HIGHLIGHT)
    shader.setFloatUniform("cornerRadius", cornerRadiusPx)

    val refraction = AndroidRenderEffect.createRuntimeShaderEffect(shader, "content")
    val blur = AndroidRenderEffect.createBlurEffect(
        blurRadiusPx,
        blurRadiusPx,
        Shader.TileMode.CLAMP
    )
    return AndroidRenderEffect.createChainEffect(refraction, blur).asComposeRenderEffect()
}

/** 数值与设计稿一致：半径 28dp、折射 0.42、边缘带 14dp、收边 1.6dp、高光 0.16。 */
private const val REFRACTION = 0.42f
private const val EDGE_BAND_DP = 14f
private const val RIM_DP = 1.6f
private const val RIM_HIGHLIGHT = 0.16f

private const val LIQUID_EDGE_SHADER = """
uniform shader content;
uniform float2 size;
uniform float band;
uniform float strength;
uniform float rim;
uniform float rimHighlight;
uniform float cornerRadius;

float roundedBoxSdf(float2 p, float2 halfSize, float radius) {
    float2 q = abs(p) - halfSize + radius;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - radius;
}

half4 main(float2 p) {
    float2 halfSize = size * 0.5;
    float radius = min(cornerRadius, min(halfSize.x, halfSize.y));
    float dist = roundedBoxSdf(p - halfSize, halfSize, radius);
    float depth = clamp(-dist / max(band, 1.0), 0.0, 1.0);
    float weight = (1.0 - depth) * (1.0 - depth);
    float2 center = halfSize;
    float2 delta = p - center;
    float2 dir = delta / max(length(delta), 0.001);
    float2 offset = dir * (band * strength) * weight;
    half4 sampled = content.eval(p - offset);
    half r = content.eval(p - offset * 1.06).r;
    half b = content.eval(p - offset * 0.94).b;
    float edge = 1.0 - smoothstep(0.0, rim, abs(dist));
    sampled.rgb += half3(rimHighlight * edge * sampled.a);
    return half4(r, sampled.g, b, sampled.a);
}
"""
