package io.github.mungbean2435.bookvoice.data.settings

import io.github.mungbean2435.bookvoice.tts.ProviderConfig
import io.github.mungbean2435.bookvoice.tts.Voice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TtsConfigsTest {

    private val mimoTemplate = TtsConfig(
        id = "mimo",
        name = "小米 MiMo",
        providerId = "mimo",
        baseUrl = "https://api.xiaomimimo.com/v1/chat/completions",
        model = "mimo-v2.5-tts",
        voice = "mimo_default"
    )

    private val minimaxTemplate = TtsConfig(
        id = "minimax",
        name = "MiniMax",
        providerId = "minimax",
        baseUrl = "https://api.minimax.cn/v1/t2a_v2",
        model = "speech-2.8-hd",
        voice = ""
    )

    @Test
    fun uniqueName_appendsSuffix() {
        assertEquals("小米 MiMo", TtsConfigs.uniqueName("小米 MiMo", emptyList()))
        assertEquals("小米 MiMo 2", TtsConfigs.uniqueName("小米 MiMo", listOf("小米 MiMo")))
        assertEquals(
            "小米 MiMo 3",
            TtsConfigs.uniqueName("小米 MiMo", listOf("小米 MiMo", "小米 MiMo 2"))
        )
    }

    @Test
    fun template_usesDefaultsAndUniqueName() {
        val config = TtsConfigs.template(
            providerId = "mimo",
            displayName = "小米 MiMo",
            baseUrl = "https://example.com",
            model = "m",
            voice = "v",
            existingNames = listOf("小米 MiMo")
        )
        assertEquals("小米 MiMo 2", config.name)
        assertEquals("mimo", config.providerId)
        assertEquals("https://example.com", config.baseUrl)
        assertEquals("m", config.model)
        assertEquals("v", config.voice)
        assertTrue(config.id.isNotBlank())
    }

    @Test
    fun duplicate_getsNewIdAndName() {
        val source = mimoTemplate.copy(id = "x")
        val copy = TtsConfigs.duplicate(source, listOf("小米 MiMo", "小米 MiMo 副本"))
        assertTrue(copy.id != source.id)
        assertEquals("小米 MiMo 副本 2", copy.name)
        assertEquals(source.providerId, copy.providerId)
        assertEquals(source.apiKey, copy.apiKey)
    }

    @Test
    fun activeIdAfterDelete_survivesOrFallsBack() {
        val a = mimoTemplate.copy(id = "a")
        val b = minimaxTemplate.copy(id = "b")
        val configs = listOf(a, b)

        assertEquals("b", TtsConfigs.activeIdAfterDelete(configs, "b", "a"))
        assertEquals("b", TtsConfigs.activeIdAfterDelete(configs, "a", "a"))
        assertEquals("", TtsConfigs.activeIdAfterDelete(listOf(a), "a", "a"))
    }

    @Test
    fun fromLegacy_keepsKeyAndFields() {
        val legacy = mapOf(
            "mimo" to ProviderConfig(
                apiKey = "sk-old",
                baseUrl = "",
                model = "old-model",
                voice = "old-voice"
            )
        )
        val (configs, active) = TtsConfigs.fromLegacy(
            legacy = legacy,
            activeProvider = "mimo",
            templates = listOf(mimoTemplate, minimaxTemplate)
        )

        assertEquals(2, configs.size)
        assertEquals("sk-old", configs.first { it.providerId == "mimo" }.apiKey)
        assertEquals("old-model", configs.first { it.providerId == "mimo" }.model)
        assertEquals("old-voice", configs.first { it.providerId == "mimo" }.voice)
        // 空的端点回落到模板默认值
        assertEquals(mimoTemplate.baseUrl, configs.first { it.providerId == "mimo" }.baseUrl)
        assertEquals("mimo", active)
    }

    @Test
    fun fromLegacy_unknownActiveFallsBackToFirst() {
        val (configs, active) = TtsConfigs.fromLegacy(
            legacy = emptyMap(),
            activeProvider = "nope",
            templates = listOf(mimoTemplate, minimaxTemplate)
        )
        assertEquals(2, configs.size)
        assertEquals(configs.first().id, active)
    }

    @Test
    fun replace_updatesExistingOrAppends() {
        val a = mimoTemplate.copy(id = "a", name = "A")
        val b = minimaxTemplate.copy(id = "b", name = "B")
        val updatedA = a.copy(name = "A2")

        val replaced = TtsConfigs.replace(listOf(a), updatedA)
        assertEquals(listOf(updatedA), replaced)

        val appended = TtsConfigs.replace(listOf(a), b)
        assertEquals(listOf(a, b), appended)
    }

    @Test
    fun normalizeLibrary_addsSelectedAndUsesKnownLabel() {
        val config = TtsConfig(
            id = "x",
            name = "X",
            providerId = "mimo",
            model = "mimo-v2.5-tts",
            voice = "mimo_default"
        )
        val normalized = TtsConfigs.normalizeLibrary(
            config,
            listOf(Voice("mimo_default", "MiMo-默认", "预置"))
        )

        assertEquals(listOf("mimo-v2.5-tts"), normalized.models)
        assertEquals("mimo_default", normalized.voice)
        assertEquals("MiMo-默认", normalized.voices.single().label)
        assertEquals("预置", normalized.voices.single().hint)
    }

    @Test
    fun normalizeLibrary_selectsFirstAndDedupes() {
        val config = TtsConfig(
            id = "x",
            name = "X",
            providerId = "minimax",
            models = listOf("speech-2.8-hd", "speech-2.8-hd", "speech-2.6-hd"),
            voices = listOf(
                Voice("a", "A"),
                Voice("a", "A2"),
                Voice("b", "B")
            )
        )
        val normalized = TtsConfigs.normalizeLibrary(config)

        assertEquals(listOf("speech-2.8-hd", "speech-2.6-hd"), normalized.models)
        assertEquals("speech-2.8-hd", normalized.model)
        assertEquals(listOf("a", "b"), normalized.voices.map { it.id })
        assertEquals("A", normalized.voices.first().label)
        assertEquals("a", normalized.voice)
    }

    @Test
    fun template_and_fromLegacy_seedLibraries() {
        val template = TtsConfigs.template(
            providerId = "mimo",
            displayName = "小米 MiMo",
            baseUrl = "https://example.com",
            model = "mimo-v2.5-tts",
            voice = "mimo_default",
            voices = listOf(Voice("mimo_default", "MiMo-默认"))
        )
        assertEquals(listOf("mimo-v2.5-tts"), template.models)
        assertEquals("MiMo-默认", template.voices.single().label)

        val (configs, _) = TtsConfigs.fromLegacy(
            legacy = mapOf(
                "mimo" to ProviderConfig(
                    apiKey = "sk",
                    model = "mimo-v2.5-tts",
                    voice = "冰糖"
                )
            ),
            activeProvider = "mimo",
            templates = listOf(template)
        )
        assertEquals(listOf("mimo-v2.5-tts"), configs.single().models)
        assertEquals("冰糖", configs.single().voice)
        assertEquals("冰糖", configs.single().voices.single().id)
    }
}
