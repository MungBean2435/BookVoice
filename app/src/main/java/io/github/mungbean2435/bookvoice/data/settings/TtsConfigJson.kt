package io.github.mungbean2435.bookvoice.data.settings

import io.github.mungbean2435.bookvoice.tts.ProviderRegistry
import io.github.mungbean2435.bookvoice.tts.Voice
import org.json.JSONArray
import org.json.JSONObject

/**
 * TtsConfig 的 JSON 编解码。Settings 和 BackupManager 共用，避免两边字段漂移。
 * 备份传 [includeApiKey] = false。
 */
object TtsConfigJson {

    fun encode(config: TtsConfig, includeApiKey: Boolean): JSONObject = JSONObject().apply {
        put("id", config.id)
        put("name", config.name)
        put("providerId", config.providerId)
        if (includeApiKey) put("apiKey", config.apiKey)
        put("baseUrl", config.baseUrl)
        put("model", config.model)
        put("voice", config.voice)
        put("models", JSONArray(config.models))
        put(
            "voices",
            JSONArray().apply {
                config.voices.forEach { voice ->
                    put(
                        JSONObject().apply {
                            put("id", voice.id)
                            put("label", voice.label)
                            put("hint", voice.hint)
                        }
                    )
                }
            }
        )
    }

    fun decode(item: JSONObject): TtsConfig {
        val providerId = item.optString("providerId")
        return TtsConfig(
            id = item.optString("id"),
            name = item.optString("name").ifBlank {
                ProviderRegistry.get(providerId)?.displayName ?: providerId
            },
            providerId = providerId,
            apiKey = item.optString("apiKey"),
            baseUrl = item.optString("baseUrl"),
            model = item.optString("model"),
            voice = item.optString("voice"),
            models = readModels(item.optJSONArray("models")),
            voices = readVoices(item.optJSONArray("voices"))
        )
    }

    private fun readModels(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            array.optString(index).trim().takeIf { it.isNotEmpty() }
        }.distinct()
    }

    private fun readVoices(array: JSONArray?): List<Voice> {
        if (array == null) return emptyList()
        val out = LinkedHashMap<String, Voice>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val id = item.optString("id").trim()
            if (id.isEmpty()) continue
            out.putIfAbsent(
                id,
                Voice(
                    id = id,
                    label = item.optString("label").ifBlank { id },
                    hint = item.optString("hint")
                )
            )
        }
        return out.values.toList()
    }
}
