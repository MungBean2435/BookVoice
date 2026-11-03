package io.github.mungbean2435.bookvoice.data.settings

import io.github.mungbean2435.bookvoice.tts.ProviderConfig
import io.github.mungbean2435.bookvoice.tts.Voice
import java.util.UUID

/**
 * 一套完整的 TTS 配置。
 *
 * 2.1.0 之前是「每家供应商一份配置」；现在同一个 providerId 可以有多条，
 * 由 [TtsSettings.activeConfigId] 指定当前朗读使用哪一条。
 */
data class TtsConfig(
    val id: String,
    val name: String,
    val providerId: String,
    val apiKey: String = "",
    val baseUrl: String = "",
    /** 当前选中的模型。 */
    val model: String = "",
    /** 当前选中的音色 ID。 */
    val voice: String = "",
    /** 这个配置里导入过的模型；音色同理。 */
    val models: List<String> = emptyList(),
    val voices: List<Voice> = emptyList()
)

/**
 * 纯函数集合：不依赖 Android，方便单元测试。
 */
object TtsConfigs {

    fun newId(): String = UUID.randomUUID().toString()

    /** 默认名称重名时追加 2、3…，避免列表里两条一模一样。 */
    fun uniqueName(base: String, existing: Collection<String>): String {
        val trimmed = base.trim().ifBlank { "未命名配置" }
        if (trimmed !in existing) return trimmed
        var index = 2
        while ("$trimmed $index" in existing) index++
        return "$trimmed $index"
    }

    fun template(
        providerId: String,
        displayName: String,
        baseUrl: String,
        model: String,
        voice: String,
        models: List<String> = listOf(model),
        voices: List<Voice> = if (voice.isNotBlank()) listOf(Voice(voice, voice)) else emptyList(),
        existingNames: Collection<String> = emptyList()
    ): TtsConfig = normalizeLibrary(
        TtsConfig(
            id = newId(),
            name = uniqueName(displayName, existingNames),
            providerId = providerId,
            baseUrl = baseUrl,
            model = model,
            voice = voice,
            models = models,
            voices = voices
        ),
        knownVoices = voices
    )

    fun duplicate(config: TtsConfig, existingNames: Collection<String>): TtsConfig =
        config.copy(id = newId(), name = uniqueName("${config.name} 副本", existingNames))

    /** 删除当前项后顺延到剩余第一条；删空返回空字符串。 */
    fun activeIdAfterDelete(configs: List<TtsConfig>, activeId: String, deletedId: String): String {
        val remaining = configs.filterNot { it.id == deletedId }
        if (remaining.isEmpty()) return ""
        if (activeId != deletedId && remaining.any { it.id == activeId }) return activeId
        return remaining.first().id
    }

    fun replace(configs: List<TtsConfig>, config: TtsConfig): List<TtsConfig> =
        if (configs.any { it.id == config.id }) {
            configs.map { if (it.id == config.id) config else it }
        } else {
            configs + config
        }

    /**
     * 保证「库」和「选中值」一致：
     * - 选中项一定在库里；
     * - 库里没有选中值时选第一条；
     * - 去重；
     * - [knownVoices] 用来把只有 ID 的旧音色补上显示名。
     */
    fun normalizeLibrary(config: TtsConfig, knownVoices: List<Voice> = emptyList()): TtsConfig {
        val models = (config.models + config.model)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()

        val voiceMap = LinkedHashMap<String, Voice>()
        config.voices.forEach { voice ->
            if (voice.id.isNotBlank()) voiceMap.putIfAbsent(voice.id, voice)
        }
        val selectedVoice = config.voice.trim()
        if (selectedVoice.isNotEmpty()) {
            val existing = voiceMap[selectedVoice]
            val known = knownVoices.firstOrNull { it.id == selectedVoice }
            if (existing == null) {
                voiceMap[selectedVoice] = known ?: Voice(selectedVoice, selectedVoice)
            } else if (known != null && existing.label == selectedVoice && known.label != selectedVoice) {
                voiceMap[selectedVoice] = existing.copy(
                    label = known.label,
                    hint = existing.hint.ifBlank { known.hint }
                )
            }
        }
        val voices = voiceMap.values.toList()
        val model = config.model.trim().ifBlank { models.firstOrNull().orEmpty() }
        val voice = selectedVoice.ifBlank { voices.firstOrNull()?.id.orEmpty() }
        return config.copy(model = model, voice = voice, models = models, voices = voices)
    }

    /**
     * 2.0.x 的 providers（每家一份）迁移成多配置。
     *
     * [templates] 是注册表里每家供应商的默认配置，id 直接用 providerId；
     * 旧数据里有的字段覆盖默认值，没有的保留默认值。
     */
    fun fromLegacy(
        legacy: Map<String, ProviderConfig>,
        activeProvider: String,
        templates: List<TtsConfig>
    ): Pair<List<TtsConfig>, String> {
        val configs = templates.map { template ->
            val old = legacy[template.providerId]
            if (old == null) {
                template
            } else {
                val model = old.model.ifBlank { template.model }
                val voice = old.voice.ifBlank { template.voice }
                template.copy(
                    apiKey = old.apiKey,
                    baseUrl = old.baseUrl.ifBlank { template.baseUrl },
                    model = model,
                    voice = voice,
                    models = listOf(model).filter { it.isNotBlank() },
                    voices = if (voice.isNotBlank()) {
                        listOf(template.voices.firstOrNull { it.id == voice } ?: Voice(voice, voice))
                    } else {
                        emptyList()
                    }
                )
            }
        }
        val active = configs.firstOrNull { it.providerId == activeProvider }?.id
            ?: configs.firstOrNull()?.id
            .orEmpty()
        return configs to active
    }
}
