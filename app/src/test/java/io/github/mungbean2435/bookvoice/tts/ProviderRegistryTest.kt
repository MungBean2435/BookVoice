package io.github.mungbean2435.bookvoice.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderRegistryTest {

    @Test
    fun registersBuiltInProviders() {
        val ids = ProviderRegistry.all().map { it.id }
        assertTrue(ids.contains("mimo"))
        assertTrue(ids.contains("minimax"))
        assertTrue(ids.contains("openai"))
        assertTrue(ids.contains("deepgram"))
    }

    @Test
    fun providerFormatsMatchTheirApis() {
        assertEquals("wav", ProviderRegistry.get("mimo")?.audioFormat)
        assertEquals("mp3", ProviderRegistry.get("minimax")?.audioFormat)
        assertEquals("mp3", ProviderRegistry.get("openai")?.audioFormat)
        assertEquals("mp3", ProviderRegistry.get("deepgram")?.audioFormat)
    }

    @Test
    fun openAiCompatibleDefaultsAreUsable() {
        val provider = ProviderRegistry.get("openai")
        assertNotNull(provider)
        assertEquals("https://api.openai.com/v1/audio/speech", provider?.defaultConfig?.baseUrl)
        assertTrue(provider?.builtinCatalog()?.models?.contains("tts-1") == true)
    }

    @Test
    fun deepgramDefaultsAreUsable() {
        val provider = ProviderRegistry.get("deepgram")
        assertNotNull(provider)
        assertEquals("https://api.deepgram.com", provider?.defaultConfig?.baseUrl)
        assertEquals("flux-wenjing-zh", provider?.defaultConfig?.voice)
        assertTrue(provider?.builtinCatalog()?.voices?.any { it.id == "flux-wenjing-zh" } == true)
        assertTrue(provider?.builtinCatalog()?.models?.contains("Flux TTS") == true)
    }

    @Test
    fun unknownProviderFallsBackToFirst() {
        assertNotNull(ProviderRegistry.active("nope"))
        assertEquals("mimo", ProviderRegistry.active("nope").id)
    }
}
