package io.github.mungbean2435.bookvoice.data.settings

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import io.github.mungbean2435.bookvoice.tts.ProviderConfig
import io.github.mungbean2435.bookvoice.tts.ProviderRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class TtsSettings(
    val activeConfigId: String = "",
    val configs: List<TtsConfig> = emptyList(),
    val speed: Float = 1.0f,
    val gapSentenceMs: Int = 150,
    val gapParagraphMs: Int = 300,
    val cacheLimitMb: Int = 512,
    val builtinRulesEnabled: Boolean = true,
    val fontSizeSp: Int = 17,
    val readingLineHeight: Float = 1.7f,
    val readingParagraphSpacingDp: Int = 10,
    val readingHorizontalPaddingDp: Int = 20,
    val readingFontFamily: String = "system",
    val tapToRead: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.FollowSystem
) {
    /** 当前启用配置；id 失效时回落到第一条。 */
    fun activeConfigOrNull(): TtsConfig? =
        configs.firstOrNull { it.id == activeConfigId } ?: configs.firstOrNull()

    /** 播放/试听用：即使一条配置都没有，也回落到 MiMo 模板，行为与旧版一致。 */
    fun activeConfigOrDefault(): TtsConfig {
        activeConfigOrNull()?.let { return it }
        val provider = ProviderRegistry.get("mimo") ?: ProviderRegistry.all().first()
        return TtsConfig(
            id = "",
            name = provider.displayName,
            providerId = provider.id,
            baseUrl = provider.defaultConfig.baseUrl,
            model = provider.defaultConfig.model,
            voice = provider.defaultConfig.voice
        )
    }

    fun config(id: String): TtsConfig? = configs.firstOrNull { it.id == id }
}

/**
 * 设置存私有 SharedPreferences：Key 不加密（设备本身有锁屏），但
 * allowBackup=false + 不打日志 + 不进仓库，这是开源红线。
 */
object Settings {

    private const val PREFS = "bookvoice_settings"
    private const val KEY_PROVIDERS = "providers"
    private const val KEY_CONFIGS = "configs"
    private const val KEY_ACTIVE_CONFIG = "activeConfigId"
    private const val KEY_ACTIVE_PROVIDER = "activeProvider"
    private lateinit var prefs: SharedPreferences

    /** configs JSON 坏掉时置 true：在用户新增配置前不要用空列表覆盖原始串。 */
    private var configsUnreadable = false

    private val _state = MutableStateFlow(TtsSettings())
    val state: StateFlow<TtsSettings> = _state.asStateFlow()

    /** 只暴露外观模式，避免 MainActivity 因语速等设置变化而重组整棵树。 */
    private val _themeMode = MutableStateFlow(ThemeMode.FollowSystem)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _state.value = read()
        _themeMode.value = _state.value.themeMode
    }

    @Synchronized
    fun update(transform: (TtsSettings) -> TtsSettings) {
        val next = normalizeActive(transform(_state.value))
        val editor = prefs.edit()
        if (!configsUnreadable || next.configs.isNotEmpty()) {
            editor
                .putString(KEY_CONFIGS, encodeConfigs(next.configs))
                .putString(KEY_ACTIVE_CONFIG, next.activeConfigId)
            configsUnreadable = false
        }
        editor
            .putFloat("speed", next.speed)
            .putInt("gapSentenceMs", next.gapSentenceMs)
            .putInt("gapParagraphMs", next.gapParagraphMs)
            .putInt("cacheLimitMb", next.cacheLimitMb)
            .putBoolean("builtinRulesEnabled", next.builtinRulesEnabled)
            .putInt("fontSizeSp", next.fontSizeSp)
            .putFloat("readingLineHeight", next.readingLineHeight)
            .putInt("readingParagraphSpacingDp", next.readingParagraphSpacingDp)
            .putInt("readingHorizontalPaddingDp", next.readingHorizontalPaddingDp)
            .putString("readingFontFamily", next.readingFontFamily)
            .putBoolean("tapToRead", next.tapToRead)
            .putString("themeMode", next.themeMode.prefValue)
            .apply()
        _state.value = next
        _themeMode.value = next.themeMode
    }

    /**
     * 与 [update] 同语义，但同步 commit 并返回是否真的落盘。
     * 首次引导的步骤要用它：写失败必须停在当前步，不能先改 UI 再假装成功。
     */
    @Synchronized
    fun commit(transform: (TtsSettings) -> TtsSettings): Boolean {
        val next = normalizeActive(transform(_state.value))
        val includeConfigs = !configsUnreadable || next.configs.isNotEmpty()
        val editor = prefs.edit()
        if (includeConfigs) {
            editor
                .putString(KEY_CONFIGS, encodeConfigs(next.configs))
                .putString(KEY_ACTIVE_CONFIG, next.activeConfigId)
        }
        editor
            .putFloat("speed", next.speed)
            .putInt("gapSentenceMs", next.gapSentenceMs)
            .putInt("gapParagraphMs", next.gapParagraphMs)
            .putInt("cacheLimitMb", next.cacheLimitMb)
            .putBoolean("builtinRulesEnabled", next.builtinRulesEnabled)
            .putInt("fontSizeSp", next.fontSizeSp)
            .putFloat("readingLineHeight", next.readingLineHeight)
            .putInt("readingParagraphSpacingDp", next.readingParagraphSpacingDp)
            .putInt("readingHorizontalPaddingDp", next.readingHorizontalPaddingDp)
            .putString("readingFontFamily", next.readingFontFamily)
            .putBoolean("tapToRead", next.tapToRead)
            .putString("themeMode", next.themeMode.prefValue)
        val ok = editor.commit()
        if (ok) {
            if (includeConfigs) configsUnreadable = false
            _state.value = next
            _themeMode.value = next.themeMode
        }
        return ok
    }

    private fun normalizeActive(settings: TtsSettings): TtsSettings {
        val configs = settings.configs.map(::normalizeConfig)
        val active = settings.activeConfigId
            .takeIf { id -> configs.any { it.id == id } }
            ?: configs.firstOrNull()?.id
            .orEmpty()
        return settings.copy(configs = configs, activeConfigId = active)
    }

    /** 补默认模型/音色、补库、去掉失效选中值。所有写路径都走这里。 */
    private fun normalizeConfig(config: TtsConfig): TtsConfig {
        val provider = ProviderRegistry.get(config.providerId)
        val defaults = provider?.defaultConfig
        val seeded = config.copy(
            model = config.model.ifBlank {
                config.models.firstOrNull() ?: defaults?.model.orEmpty()
            },
            voice = config.voice.ifBlank {
                config.voices.firstOrNull()?.id ?: defaults?.voice.orEmpty()
            }
        )
        return TtsConfigs.normalizeLibrary(seeded, provider?.voices().orEmpty())
    }

    private fun read(): TtsSettings {
        val defaults = TtsSettings()
        val stored = readConfigs()
        val (configs, active) = stored ?: migrateLegacy()
        return TtsSettings(
            activeConfigId = active
                .takeIf { id -> configs.any { it.id == id } }
                ?: configs.firstOrNull()?.id
                .orEmpty(),
            configs = configs,
            speed = prefs.getFloat("speed", defaults.speed),
            gapSentenceMs = prefs.getInt("gapSentenceMs", defaults.gapSentenceMs),
            gapParagraphMs = prefs.getInt("gapParagraphMs", defaults.gapParagraphMs),
            cacheLimitMb = prefs.getInt("cacheLimitMb", defaults.cacheLimitMb),
            builtinRulesEnabled = prefs.getBoolean("builtinRulesEnabled", defaults.builtinRulesEnabled),
            fontSizeSp = prefs.getInt("fontSizeSp", defaults.fontSizeSp),
            readingLineHeight = prefs.getFloat("readingLineHeight", defaults.readingLineHeight),
            readingParagraphSpacingDp = prefs.getInt(
                "readingParagraphSpacingDp",
                defaults.readingParagraphSpacingDp
            ),
            readingHorizontalPaddingDp = prefs.getInt(
                "readingHorizontalPaddingDp",
                defaults.readingHorizontalPaddingDp
            ),
            readingFontFamily = prefs.getString(
                "readingFontFamily",
                defaults.readingFontFamily
            ) ?: defaults.readingFontFamily,
            tapToRead = prefs.getBoolean("tapToRead", defaults.tapToRead),
            themeMode = ThemeMode.from(prefs.getString("themeMode", null))
        )
    }

    /**
     * 读新版配置。null 表示还没迁移过（或旧的 configs 是坏 JSON），
     * 空数组则是合法状态：用户把配置全删了，不能重新迁移。
     */
    private fun readConfigs(): Pair<List<TtsConfig>, String>? {
        if (!prefs.contains(KEY_CONFIGS)) return null
        val raw = prefs.getString(KEY_CONFIGS, null)
        if (raw == null) {
            configsUnreadable = true
            return emptyList<TtsConfig>() to ""
        }
        val active = prefs.getString(KEY_ACTIVE_CONFIG, "").orEmpty()
        val configs = decodeConfigs(raw)
        if (configs == null) {
            // 坏 JSON 不要用旧的 providers 重新迁移，否则会用旧数据覆盖新配置。
            // 返回空列表让界面显示「还没有配置」，原始串仍留在 prefs 里。
            Log.w("BookVoice", "configs JSON 解析失败，保留原值，不重新迁移")
            configsUnreadable = true
            return emptyList<TtsConfig>() to active
        }
        configsUnreadable = false
        return configs to active
    }

    /** 2.0.x → 2.1.0：每家供应商各迁移成一条配置，旧 Key/端点/模型/音色不丢。 */
    private fun migrateLegacy(): Pair<List<TtsConfig>, String> {
        val legacy = readProviders()
        val templates = ProviderRegistry.all().map { provider ->
            TtsConfig(
                id = provider.id,
                name = provider.displayName,
                providerId = provider.id,
                baseUrl = provider.defaultConfig.baseUrl,
                model = provider.defaultConfig.model,
                voice = provider.defaultConfig.voice,
                models = listOf(provider.defaultConfig.model).filter { it.isNotBlank() },
                voices = if (provider.defaultConfig.voice.isNotBlank()) {
                    listOf(
                        io.github.mungbean2435.bookvoice.tts.Voice(
                            provider.defaultConfig.voice,
                            provider.defaultConfig.voice
                        )
                    )
                } else {
                    emptyList()
                }
            )
        }
        val (migrated, active) = TtsConfigs.fromLegacy(
            legacy = legacy,
            activeProvider = prefs.getString(KEY_ACTIVE_PROVIDER, "").orEmpty(),
            templates = templates
        )
        val configs = migrated.map(::normalizeConfig)
        prefs.edit()
            .putString(KEY_CONFIGS, encodeConfigs(configs))
            .putString(KEY_ACTIVE_CONFIG, active)
            .apply()
        configsUnreadable = false
        return configs to active
    }

    /** 老版本是扁平字段，这里一次性迁移成 providers["mimo"]，不丢 Key。 */
    private fun readProviders(): Map<String, ProviderConfig> {
        val stored = prefs.getString(KEY_PROVIDERS, null)
        if (!stored.isNullOrBlank()) {
            return normalizeLegacy(decodeProviders(stored))
        }
        val legacyKey = prefs.getString("apiKey", "").orEmpty()
        val legacyBase = prefs.getString("baseUrl", "").orEmpty()
        val legacyModel = prefs.getString("model", "").orEmpty()
        val legacyVoice = prefs.getString("voice", "").orEmpty()
        if (legacyKey.isBlank() && legacyBase.isBlank() && legacyModel.isBlank() && legacyVoice.isBlank()) {
            return emptyMap()
        }
        val migrated = mapOf(
            "mimo" to ProviderConfig(
                apiKey = legacyKey,
                baseUrl = legacyBase,
                model = legacyModel,
                voice = legacyVoice
            )
        )
        prefs.edit().putString(KEY_PROVIDERS, encodeProviders(migrated)).apply()
        return migrated
    }

    /** 老默认值写成了国际站，国内账号用不了，这里自动纠正一次。 */
    private fun normalizeLegacy(providers: Map<String, ProviderConfig>): Map<String, ProviderConfig> {
        val minimax = providers["minimax"] ?: return providers
        if (minimax.baseUrl.trim() != "https://api.minimax.io/v1/t2a_v2") return providers
        val fixed = providers + ("minimax" to minimax.copy(baseUrl = "https://api.minimax.cn/v1/t2a_v2"))
        prefs.edit().putString(KEY_PROVIDERS, encodeProviders(fixed)).apply()
        return fixed
    }

    private fun encodeProviders(providers: Map<String, ProviderConfig>): String {
        val root = JSONObject()
        providers.forEach { (id, config) ->
            root.put(
                id,
                JSONObject().apply {
                    put("apiKey", config.apiKey)
                    put("baseUrl", config.baseUrl)
                    put("model", config.model)
                    put("voice", config.voice)
                }
            )
        }
        return root.toString()
    }

    private fun decodeProviders(raw: String): Map<String, ProviderConfig> {
        val out = LinkedHashMap<String, ProviderConfig>()
        runCatching {
            val root = JSONObject(raw)
            root.keys().forEach { id ->
                val item = root.optJSONObject(id) ?: return@forEach
                out[id] = ProviderConfig(
                    apiKey = item.optString("apiKey"),
                    baseUrl = item.optString("baseUrl"),
                    model = item.optString("model"),
                    voice = item.optString("voice")
                )
            }
        }
        return out
    }

    private fun encodeConfigs(configs: List<TtsConfig>): String {
        val array = JSONArray()
        configs.forEach { config ->
            array.put(TtsConfigJson.encode(config, includeApiKey = true))
        }
        return array.toString()
    }

    private fun decodeConfigs(raw: String): List<TtsConfig>? = runCatching {
        val array = JSONArray(raw)
        val seenIds = HashSet<String>()
        (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            if (item.optString("providerId").isBlank()) return@mapNotNull null
            val config = TtsConfigJson.decode(item)
            val rawId = config.id.ifBlank { TtsConfigs.newId() }
            val id = if (seenIds.add(rawId)) rawId else TtsConfigs.newId()
            normalizeConfig(config.copy(id = id))
        }
    }.getOrNull()
}
