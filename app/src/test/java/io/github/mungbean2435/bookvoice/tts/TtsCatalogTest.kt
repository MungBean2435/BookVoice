package io.github.mungbean2435.bookvoice.tts

import io.github.mungbean2435.bookvoice.tts.minimax.MiniMaxProvider
import io.github.mungbean2435.bookvoice.tts.minimax.isLegacyMiniMaxVoice
import io.github.mungbean2435.bookvoice.tts.mimo.MimoProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TtsCatalogTest {

    @Test
    fun minimaxBuiltinHasTtsModelsAndModernVoices() {
        val catalog = MiniMaxProvider().builtinCatalog()
        assertTrue(catalog.models.contains("speech-2.8-hd"))
        assertTrue(catalog.models.contains("speech-2.8-turbo"))
        assertTrue(catalog.voices.isNotEmpty())
        assertTrue(catalog.voices.all { it.id.startsWith("Chinese (Mandarin)_") })
        assertFalse(catalog.voices.any { it.id == "female-shaonv" })
        assertEquals(false, catalog.fromNetwork)
        assertTrue(catalog.note.isNotBlank())
    }

    @Test
    fun mimoBuiltinHasPresetVoices() {
        val catalog = MimoProvider().builtinCatalog()
        assertEquals(listOf("mimo-v2.5-tts"), catalog.models)
        assertEquals(9, catalog.voices.size)
        assertTrue(catalog.voices.any { it.id == "冰糖" })
        assertTrue(catalog.voices.any { it.id == "Chloe" })
        assertEquals(false, catalog.fromNetwork)
        assertTrue(catalog.note.isNotBlank())
    }

    @Test
    fun legacyMinimaxVoicesAreDetected() {
        assertTrue(isLegacyMiniMaxVoice("female-shaonv"))
        assertTrue(isLegacyMiniMaxVoice("female-yujie"))
        assertTrue(isLegacyMiniMaxVoice("male-qn-qingse"))
        assertFalse(isLegacyMiniMaxVoice("Chinese (Mandarin)_Warm_Girl"))
        assertFalse(isLegacyMiniMaxVoice("Chinese (Mandarin)_News_Anchor"))
    }
}
