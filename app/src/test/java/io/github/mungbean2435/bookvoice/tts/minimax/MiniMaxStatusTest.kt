package io.github.mungbean2435.bookvoice.tts.minimax

import io.github.mungbean2435.bookvoice.tts.TtsException
import org.junit.Assert.assertTrue
import org.junit.Test

class MiniMaxStatusTest {

    @Test
    fun `1008 insufficient balance is a fatal quota error`() {
        val error = miniMaxStatusError(1008, "insufficient balance")
        assertTrue(error is TtsException.Quota)
        assertTrue(error.message.orEmpty().contains("余额不足"))
    }

    @Test
    fun `1004 invalid key is auth`() {
        assertTrue(miniMaxStatusError(1004, "invalid api key") is TtsException.Auth)
    }

    @Test
    fun `2054 voice error is not treated as quota`() {
        val error = miniMaxStatusError(2054, "voice id not exist")
        assertTrue(error is TtsException.Server)
        assertTrue(error !is TtsException.Quota)
    }

    @Test
    fun `unknown status falls back to server with raw message`() {
        val error = miniMaxStatusError(9999, "something odd")
        assertTrue(error is TtsException.Server)
        assertTrue(error.message.orEmpty().contains("something odd"))
    }
}
