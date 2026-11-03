package io.github.mungbean2435.bookvoice.ui.glass

import android.os.Build

/**
 * 底部导航的三档玻璃。
 *
 * - [Solid]：纯色 + 描边（Android 11 及以下，或快速模糊不可用）
 * - [Blur]：实时页面快照 + 28dp 模糊 + 0.75 tint（Android 12+，也是液态玻璃关闭时的档位）
 * - [Liquid]：在模糊层上增加边缘折射（Android 13+，需要用户开启且设备可用）
 */
enum class GlassTier {
    Solid,
    Blur,
    Liquid
}

/** 液态玻璃开关不可用的原因；[None] 表示可用。 */
enum class LiquidGlassBlock {
    None,
    UnsupportedSdk,
    PowerSave,
    LowRam
}

data class LiquidGlassAvailability(
    val sdkInt: Int,
    val powerSave: Boolean,
    val lowRam: Boolean
) {
    val block: LiquidGlassBlock = resolveLiquidGlassBlock(sdkInt, powerSave, lowRam)
    val available: Boolean get() = block == LiquidGlassBlock.None
    val reason: String? get() = liquidGlassBlockReason(block)
}

fun resolveLiquidGlassBlock(
    sdkInt: Int,
    powerSave: Boolean,
    lowRam: Boolean
): LiquidGlassBlock = when {
    sdkInt < Build.VERSION_CODES.TIRAMISU -> LiquidGlassBlock.UnsupportedSdk
    powerSave -> LiquidGlassBlock.PowerSave
    lowRam -> LiquidGlassBlock.LowRam
    else -> LiquidGlassBlock.None
}

fun liquidGlassBlockReason(block: LiquidGlassBlock): String? = when (block) {
    LiquidGlassBlock.None -> null
    LiquidGlassBlock.UnsupportedSdk -> "需要 Android 13（API 33）及以上"
    LiquidGlassBlock.PowerSave -> "省电模式下不可用，退出省电后恢复"
    LiquidGlassBlock.LowRam -> "低内存设备上不可用"
}

/**
 * 档位只看四个事实，避免 UI 和渲染层各自判断一套：
 * 系统版本、用户开关、设备是否允许、本次进程是否已经回落。
 */
fun resolveGlassTier(
    sdkInt: Int,
    liquidGlassEnabled: Boolean,
    liquidGlassAvailable: Boolean,
    fellBack: Boolean
): GlassTier = when {
    sdkInt < Build.VERSION_CODES.S -> GlassTier.Solid
    sdkInt < Build.VERSION_CODES.TIRAMISU -> GlassTier.Blur
    !liquidGlassEnabled || !liquidGlassAvailable || fellBack -> GlassTier.Blur
    else -> GlassTier.Liquid
}

/** 玻璃底色不透明度：档 2 是 0.75，档 3 暗色 0.48 / 亮色 0.56。 */
fun glassTintAlpha(tier: GlassTier, dark: Boolean, hasCustomBackground: Boolean): Float = when (tier) {
    GlassTier.Solid -> if (hasCustomBackground) 0.62f else 1f
    GlassTier.Blur -> 0.75f
    GlassTier.Liquid -> if (dark) 0.48f else 0.56f
}

/** 只在边缘参与折射；中间用 (1-depth)^2 归零，保持毛玻璃原样。 */
fun refractionWeight(depth: Float): Float {
    val d = depth.coerceIn(0f, 1f)
    return (1f - d) * (1f - d)
}
