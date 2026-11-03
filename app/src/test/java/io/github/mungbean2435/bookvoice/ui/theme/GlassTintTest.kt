package io.github.mungbean2435.bookvoice.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassTintTest {

    @Test
    fun parsesSixDigitColorWithHash() {
        assertEquals(Color(0xFF8F949B), parseGlassColor("#8F949B"))
    }

    @Test
    fun parsesSixDigitColorWithoutHashAndLowercase() {
        assertEquals(Color(0xFF8f949b), parseGlassColor("8f949b"))
    }

    @Test
    fun rejectsShortAlphaOrNonHexInput() {
        assertNull(parseGlassColor("#FFF"))
        assertNull(parseGlassColor("#8F949B00"))
        assertNull(parseGlassColor("#8F949Z"))
        assertNull(parseGlassColor(""))
    }

    @Test
    fun formatsHexWithoutAlpha() {
        assertEquals("#8F949B", glassColorHex(0xFF8F949B.toInt()))
        assertEquals("#000000", glassColorHex(0xFF000000.toInt()))
    }

    @Test
    fun followThemeUsesPrimary() {
        val primary = Color(0xFF1C4E7A)
        assertEquals(primary, resolvedGlassTint(true, 0xFF8F949B.toInt(), primary))
    }

    @Test
    fun contentTintDarkensMidGrayInLightTheme() {
        val glass = Color(0xFF8F949B)
        val content = glassContentTint(glass, dark = false)
        assertTrue(content.luminance() < glass.luminance())
        assertTrue(content.luminance() <= 0.25f)
    }

    @Test
    fun contentTintLightensMidGrayInDarkTheme() {
        val glass = Color(0xFF8F949B)
        val content = glassContentTint(glass, dark = true)
        assertTrue(content.luminance() > glass.luminance())
        assertTrue(content.luminance() >= 0.55f)
    }

    @Test
    fun contentTintKeepsAlreadyReadableColors() {
        val dark = Color(0xFF202124)
        val light = Color(0xFFE8EAED)
        assertEquals(dark, glassContentTint(dark, dark = false))
        assertEquals(light, glassContentTint(light, dark = true))
    }

    @Test
    fun fixedColorIgnoresPrimary() {
        assertEquals(
            Color(0xFF8F949B),
            resolvedGlassTint(false, 0xFF8F949B.toInt(), Color(0xFF1C4E7A))
        )
    }
}
