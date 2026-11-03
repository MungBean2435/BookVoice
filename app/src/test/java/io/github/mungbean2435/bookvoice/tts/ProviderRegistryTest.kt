package io.github.mungbean2435.bookvoice.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderRegistryTest {

    @Test
    fun registersBothProviders() {
        val ids = ProviderRegistry.all().map { it.id }
        assertTrue(ids.contains("mimo"))
        assertTrue(ids.contains("minimax"))
    }

    @Test
    fun providerFormatsMatchTheirApis() {
        assertEquals("wav", ProviderRegistry.get("mimo")?.audioFormat)
        assertEquals("mp3", ProviderRegistry.get("minimax")?.audioFormat)
    }

    @Test
    fun unknownProviderFallsBackToFirst() {
        assertNotNull(ProviderRegistry.active("nope"))
        assertEquals("mimo", ProviderRegistry.active("nope").id)
    }
}
