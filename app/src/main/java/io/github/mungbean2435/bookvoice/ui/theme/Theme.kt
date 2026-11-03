package io.github.mungbean2435.bookvoice.ui.theme

import android.os.Build
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** 让设置页等非 Material 组件知道当前实际明暗（外观可覆盖系统）。 */
internal val LocalBookVoiceDarkTheme = staticCompositionLocalOf { false }

private val MaterialBookVoiceShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

private val CleanBookVoiceShapes = Shapes(
    extraSmall = ContinuousRoundedCornerShape(14.dp),
    small = ContinuousRoundedCornerShape(16.dp),
    medium = ContinuousRoundedCornerShape(18.dp),
    large = ContinuousRoundedCornerShape(24.dp),
    extraLarge = ContinuousRoundedCornerShape(28.dp)
)

/**
 * 动态取色优先（Android 12+ / API 31+），关闭后按外观设置里的取色风格回退。
 * Clean 风格再对中性面做 1% / 1.6% 的品牌色染色，强调色只留在交互件上。
 */
@Composable
fun BookVoiceTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    style: BookVoiceStyle? = null,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    remember(context) {
        AppearanceSettings.init(context)
        true
    }
    val appearance by AppearanceSettings.state.collectAsStateWithLifecycle()

    val useDynamic = dynamicColor && appearance.dynamicColor
    val accentScheme = when {
        useDynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        appearance.palette != BookVoicePalette.Ink ->
            schemeFor(appearance.palette, darkTheme)

        darkTheme -> BrandDarkColors
        else -> BrandLightColors
    }

    val activeStyle = style ?: appearance.style
    val isClean = activeStyle == BookVoiceStyle.Clean
    val colorScheme = if (isClean) cleanColorScheme(accentScheme, darkTheme) else accentScheme
    val tokens = if (isClean) CleanDesignTokens else MaterialDesignTokens
    val typography = if (isClean) CleanTypography else BookVoiceTypography
    val shapes = if (isClean) CleanBookVoiceShapes else MaterialBookVoiceShapes

    val fallbackIndication = LocalIndication.current
    val highlightColor = rememberUpdatedState(colorScheme.onSurface)
    val pressIndication = remember(tokens, highlightColor) {
        BookVoicePressIndication(tokens, highlightColor)
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
        shapes = shapes,
        content = {
            CompositionLocalProvider(
                LocalBookVoiceTokens provides tokens,
                LocalBookVoiceDarkTheme provides darkTheme,
                LocalIndication provides
                    if (tokens.pressScaleEnabled) pressIndication else fallbackIndication,
                content = content
            )
        }
    )
}
