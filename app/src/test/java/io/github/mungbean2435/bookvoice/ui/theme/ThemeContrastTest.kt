package io.github.mungbean2435.bookvoice.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 明暗两套品牌色的 WCAG 对比度回归。
 * 动态取色的角色由 M3 色调板保证，这里锁死 Android 11 及以下的兜底色。
 */
class ThemeContrastTest {

    private fun ratio(foreground: Color, background: Color): Double {
        val a = foreground.luminance().toDouble() + 0.05
        val b = background.luminance().toDouble() + 0.05
        return if (a > b) a / b else b / a
    }

    private fun check(scheme: ColorScheme, label: String) {
        val pairs = listOf(
            "onPrimary/primary" to (scheme.onPrimary to scheme.primary),
            "onPrimaryContainer/primaryContainer" to
                (scheme.onPrimaryContainer to scheme.primaryContainer),
            "onSecondary/secondary" to (scheme.onSecondary to scheme.secondary),
            "onSecondaryContainer/secondaryContainer" to
                (scheme.onSecondaryContainer to scheme.secondaryContainer),
            "onSurface/surface" to (scheme.onSurface to scheme.surface),
            "onSurfaceVariant/surface" to (scheme.onSurfaceVariant to scheme.surface),
            "onSurfaceVariant/containerHighest" to
                (scheme.onSurfaceVariant to scheme.surfaceContainerHighest),
            "onSurfaceVariant/containerHigh" to
                (scheme.onSurfaceVariant to scheme.surfaceContainerHigh),
            "primary/surface" to (scheme.primary to scheme.surface),
            "onSurface/containerHigh" to (scheme.onSurface to scheme.surfaceContainerHigh),
            "error/surface" to (scheme.error to scheme.surface)
        )
        pairs.forEach { (name, pair) ->
            val value = ratio(pair.first, pair.second)
            assertTrue(
                "$label $name 对比度 $value < 4.5",
                value >= 4.5
            )
        }
    }

    @Test
    fun lightSchemeMeetsWcag() {
        check(BrandLightColors, "light")
    }

    @Test
    fun darkSchemeMeetsWcag() {
        check(BrandDarkColors, "dark")
    }

    @Test
    fun settingsIconTonesMeetContrast() {
        SettingsTone.entries.forEach { tone ->
            val value = ratio(Color.White, tone.container)
            assertTrue(
                "settings tone ${tone.name} 对比度 $value < 4.5",
                value >= 4.5
            )
        }
    }

    /**
     * 阅读页正文会叠两层 primary 淡色：当前段 6%、句高亮再叠 18%。
     * 6 套色板 × 明暗都检查正文两色，避免为了「轻高亮」把对比度降到 4.5 以下。
     */
    @Test
    fun readingSurfacesMeetWcagAcrossPalettes() {
        val reading = BookVoiceReadingTokens()
        BookVoicePalette.entries.forEach { palette ->
            listOf(false, true).forEach { dark ->
                val scheme = cleanColorScheme(schemeFor(palette, dark), dark)
                val base = scheme.background
                val paragraph = lerp(base, scheme.primary, reading.highlightAlpha)
                val sentence = lerp(paragraph, scheme.primary, reading.sentenceHighlightAlpha)
                val label = "${palette.name} ${if (dark) "dark" else "light"}"
                listOf(
                    "body" to base,
                    "paragraph" to paragraph,
                    "sentence" to sentence
                ).forEach { (surface, background) ->
                    assertRatio(scheme.onSurface, background, label, "onSurface/$surface")
                    assertRatio(
                        scheme.onSurfaceVariant,
                        background,
                        label,
                        "onSurfaceVariant/$surface"
                    )
                }
                val bar = ratio(scheme.primary, base)
                assertTrue("$label primary/background 对比度 $bar < 3.0", bar >= 3.0)
            }
        }
    }

    private fun assertRatio(
        foreground: Color,
        background: Color,
        label: String,
        name: String,
        min: Double = 4.5
    ) {
        val value = ratio(foreground, background)
        assertTrue("$label $name 对比度 $value < $min", value >= min)
    }
}
