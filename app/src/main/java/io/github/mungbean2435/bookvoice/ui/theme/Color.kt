package io.github.mungbean2435.bookvoice.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * 固定品牌色兜底（Android 11 及以下，或用户关闭动态取色时使用）。
 * 墨蓝 + 暖白纸感；所有正文组合均通过 WCAG 4.5:1（见 ThemeContrastTest）。
 */
internal val BrandLightColors = lightColorScheme(
    primary = Color(0xFF1C4E7A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD5E4F2),
    onPrimaryContainer = Color(0xFF071B2B),
    inversePrimary = Color(0xFF9CCBF8),
    secondary = Color(0xFF4F6070),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD3E5F5),
    onSecondaryContainer = Color(0xFF0B1D2A),
    tertiary = Color(0xFF3F6358),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFC2E8DA),
    onTertiaryContainer = Color(0xFF06251C),
    background = Color(0xFFFAF9F6),
    onBackground = Color(0xFF191C1E),
    surface = Color(0xFFFAF9F6),
    onSurface = Color(0xFF191C1E),
    surfaceVariant = Color(0xFFDDE3E8),
    onSurfaceVariant = Color(0xFF41484D),
    surfaceTint = Color(0xFF1C4E7A),
    inverseSurface = Color(0xFF2E3133),
    inverseOnSurface = Color(0xFFEFF1F3),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    outline = Color(0xFF71787D),
    outlineVariant = Color(0xFFC1C7CC),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFAF9F6),
    surfaceDim = Color(0xFFDAD9D5),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF4F3EF),
    surfaceContainer = Color(0xFFEEEDE9),
    surfaceContainerHigh = Color(0xFFE8E7E3),
    surfaceContainerHighest = Color(0xFFE2E1DD)
)

internal val BrandDarkColors = darkColorScheme(
    primary = Color(0xFF9CCBF8),
    onPrimary = Color(0xFF003355),
    primaryContainer = Color(0xFF17466E),
    onPrimaryContainer = Color(0xFFD5E4F2),
    inversePrimary = Color(0xFF1C4E7A),
    secondary = Color(0xFFB7C8D9),
    onSecondary = Color(0xFF22323F),
    secondaryContainer = Color(0xFF384957),
    onSecondaryContainer = Color(0xFFD3E5F5),
    tertiary = Color(0xFFA6D5C4),
    onTertiary = Color(0xFF0B372C),
    tertiaryContainer = Color(0xFF274E43),
    onTertiaryContainer = Color(0xFFC2E8DA),
    background = Color(0xFF101416),
    onBackground = Color(0xFFE0E3E5),
    surface = Color(0xFF101416),
    onSurface = Color(0xFFE0E3E5),
    surfaceVariant = Color(0xFF3F484F),
    onSurfaceVariant = Color(0xFFBFC8CE),
    surfaceTint = Color(0xFF9CCBF8),
    inverseSurface = Color(0xFFE0E3E5),
    inverseOnSurface = Color(0xFF2E3133),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
    outline = Color(0xFF899399),
    outlineVariant = Color(0xFF3F484F),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF363A3C),
    surfaceDim = Color(0xFF101416),
    surfaceContainerLowest = Color(0xFF0B0F11),
    surfaceContainerLow = Color(0xFF161A1C),
    surfaceContainer = Color(0xFF1A1E20),
    surfaceContainerHigh = Color(0xFF24282A),
    surfaceContainerHighest = Color(0xFF2E3235)
)

/**
 * Clean 风格的中性面：沿用品牌色的暖白/墨黑，只做极轻的 accent 染色。
 * 这里不引入参考项目的色板，避免把 GPL 资源带进来。
 */
private data class CleanNeutrals(
    val background: Color,
    val surfaceDim: Color,
    val surfaceBright: Color,
    val containerLowest: Color,
    val containerLow: Color,
    val container: Color,
    val containerHigh: Color,
    val containerHighest: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val outline: Color,
    val outlineVariant: Color,
    val inverseSurface: Color,
    val inverseOnSurface: Color
)

private val CleanLightNeutrals = CleanNeutrals(
    background = BrandLightColors.background,
    surfaceDim = BrandLightColors.surfaceDim,
    surfaceBright = BrandLightColors.surfaceBright,
    containerLowest = BrandLightColors.surfaceContainerLowest,
    containerLow = BrandLightColors.surfaceContainerLow,
    container = BrandLightColors.surfaceContainer,
    containerHigh = BrandLightColors.surfaceContainerHigh,
    containerHighest = BrandLightColors.surfaceContainerHighest,
    onSurface = BrandLightColors.onSurface,
    onSurfaceVariant = BrandLightColors.onSurfaceVariant,
    outline = BrandLightColors.outline,
    outlineVariant = BrandLightColors.outlineVariant,
    inverseSurface = BrandLightColors.inverseSurface,
    inverseOnSurface = BrandLightColors.inverseOnSurface
)

private val CleanDarkNeutrals = CleanNeutrals(
    background = BrandDarkColors.background,
    surfaceDim = BrandDarkColors.surfaceDim,
    surfaceBright = BrandDarkColors.surfaceBright,
    containerLowest = BrandDarkColors.surfaceContainerLowest,
    containerLow = BrandDarkColors.surfaceContainerLow,
    container = BrandDarkColors.surfaceContainer,
    containerHigh = BrandDarkColors.surfaceContainerHigh,
    containerHighest = BrandDarkColors.surfaceContainerHighest,
    onSurface = BrandDarkColors.onSurface,
    onSurfaceVariant = BrandDarkColors.onSurfaceVariant,
    outline = BrandDarkColors.outline,
    outlineVariant = BrandDarkColors.outlineVariant,
    inverseSurface = BrandDarkColors.inverseSurface,
    inverseOnSurface = BrandDarkColors.inverseOnSurface
)

private const val CleanTintLight = 0.01f
private const val CleanTintDark = 0.016f
private const val CleanSelectionLight = 0.12f
private const val CleanSelectionDark = 0.20f
private const val CleanOnSelectionTint = 0.12f

/**
 * 在 accent 色板之上重算 Clean 的中性面：
 * surface 只染 1% / 1.6%，选中容器 12% / 20%，surfaceTint 透明。
 */
internal fun cleanColorScheme(accent: ColorScheme, isDark: Boolean): ColorScheme {
    val neutrals = if (isDark) CleanDarkNeutrals else CleanLightNeutrals
    val tintAmount = if (isDark) CleanTintDark else CleanTintLight
    val selectionAmount = if (isDark) CleanSelectionDark else CleanSelectionLight
    val tint = accent.primary

    fun neutral(color: Color): Color = lerp(color, tint, tintAmount)

    val containerLow = neutral(neutrals.containerLow)
    val selectionContainer = lerp(containerLow, accent.primary, selectionAmount)
    val onSelectionContainer = lerp(neutrals.onSurface, accent.primary, CleanOnSelectionTint)

    return accent.copy(
        secondaryContainer = selectionContainer,
        onSecondaryContainer = onSelectionContainer,
        background = neutral(neutrals.background),
        onBackground = neutrals.onSurface,
        surface = neutral(neutrals.background),
        onSurface = neutrals.onSurface,
        surfaceVariant = neutral(neutrals.containerHighest),
        onSurfaceVariant = neutrals.onSurfaceVariant,
        surfaceTint = Color.Transparent,
        inverseSurface = neutrals.inverseSurface,
        inverseOnSurface = neutrals.inverseOnSurface,
        outline = neutrals.outline,
        outlineVariant = neutrals.outlineVariant,
        scrim = Color.Black,
        surfaceBright = neutral(neutrals.surfaceBright),
        surfaceDim = neutral(neutrals.surfaceDim),
        surfaceContainerLowest = neutral(neutrals.containerLowest),
        surfaceContainerLow = containerLow,
        surfaceContainer = neutral(neutrals.container),
        surfaceContainerHigh = neutral(neutrals.containerHigh),
        surfaceContainerHighest = neutral(neutrals.containerHighest)
    )
}

/**
 * 设置页图标底的 5 个装饰色，只用于分类识别，不承载状态语义；
 * 白色 glyph 对比度均 ≥4.5（见 ThemeContrastTest）。
 */
internal enum class SettingsTone(val container: Color) {
    Violet(Color(0xFF5B4BDB)),
    Blue(Color(0xFF1D5FD1)),
    Teal(Color(0xFF0F766E)),
    Amber(Color(0xFFA15C00)),
    Rose(Color(0xFFB3263E))
}
