package io.github.mungbean2435.bookvoice.tts.deepgram

import io.github.mungbean2435.bookvoice.tts.TtsException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeepgramProviderTest {

    @Test
    fun `flux voice uses v2 endpoint`() {
        assertEquals(
            "https://api.deepgram.com/v2/speak",
            deepgramEndpoint("https://api.deepgram.com", "flux-wenjing-zh")
        )
    }

    @Test
    fun `aura voice uses v1 endpoint`() {
        assertEquals(
            "https://api.deepgram.com/v1/speak",
            deepgramEndpoint("https://api.deepgram.com", "aura-2-thalia-en")
        )
    }

    @Test
    fun `explicit speak path is kept`() {
        assertEquals(
            "https://api.deepgram.com/v1/speak",
            deepgramEndpoint("https://api.deepgram.com/v1/speak/", "flux-wenjing-zh")
        )
    }

    @Test
    fun `custom proxy path keeps its prefix`() {
        assertEquals(
            "https://proxy.example/deepgram/v2/speak",
            deepgramEndpoint("https://proxy.example/deepgram", "flux-wenjing-zh")
        )
    }

    @Test
    fun `voice model wins over display model`() {
        assertEquals(
            "flux-wenjing-zh",
            deepgramResolveModel("Flux TTS", "flux-wenjing-zh", "flux-bo-zh")
        )
    }

    @Test
    fun `manual model works when voice is blank`() {
        assertEquals(
            "aura-2-thalia-en",
            deepgramResolveModel("aura-2-thalia-en", "", "flux-bo-zh")
        )
    }

    @Test
    fun `fallback voice is used for display-only values`() {
        assertEquals(
            "flux-bo-zh",
            deepgramResolveModel("Flux TTS", "", "flux-bo-zh")
        )
    }

    @Test
    fun `402 is a fatal quota error`() {
        val error = deepgramError(402, "insufficient funds")
        assertTrue(error is TtsException.Quota)
        assertTrue(error.message.orEmpty().contains("余额不足"))
    }

    @Test
    fun `401 is auth`() {
        assertTrue(deepgramError(401, "invalid credentials") is TtsException.Auth)
    }

    @Test
    fun `403 with billing wording is quota`() {
        assertTrue(deepgramError(403, "insufficient credits") is TtsException.Quota)
    }

    @Test
    fun `403 without billing wording is auth`() {
        assertTrue(deepgramError(403, "permission denied") is TtsException.Auth)
    }

    @Test
    fun `400 with text length hint is too long`() {
        assertTrue(deepgramError(400, "Text length exceeds maximum") is TtsException.TooLong)
    }

    @Test
    fun `429 is rate limited`() {
        assertTrue(deepgramError(429, "slow down") is TtsException.RateLimited)
    }

    @Test
    fun `unknown status is a server error with the detail`() {
        val error = deepgramError(418, "teapot")
        assertTrue(error is TtsException.Server)
        assertTrue(error.message.orEmpty().contains("teapot"))
    }
}
