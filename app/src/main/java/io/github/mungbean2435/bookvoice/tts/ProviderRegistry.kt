package io.github.mungbean2435.bookvoice.tts

import android.content.Context
import io.github.mungbean2435.bookvoice.tts.deepgram.DeepgramProvider
import io.github.mungbean2435.bookvoice.tts.mimo.MimoProvider
import io.github.mungbean2435.bookvoice.tts.minimax.MiniMaxProvider
import io.github.mungbean2435.bookvoice.tts.openai.OpenAiCompatibleProvider
import io.github.mungbean2435.bookvoice.tts.system.SystemTtsProvider

/**
 * 供应商注册表：加一家无 Context 的供应商只要在 [baseProviders] 里注册一行。
 * 系统 TTS 需要 Context，必须在 `App.onCreate` 里先 [init] 再初始化 Settings。
 */
object ProviderRegistry {

    private val baseProviders = linkedMapOf(
        "mimo" to MimoProvider(),
        "minimax" to MiniMaxProvider(),
        "openai" to OpenAiCompatibleProvider(),
        "deepgram" to DeepgramProvider()
    )

    @Volatile
    private var providers: Map<String, TtsProvider> = baseProviders

    @Volatile
    private var initialized = false

    @Synchronized
    fun init(context: Context) {
        if (initialized) return
        providers = LinkedHashMap(baseProviders).apply {
            put("system", SystemTtsProvider(context.applicationContext))
        }
        initialized = true
    }

    fun all(): List<TtsProvider> = providers.values.toList()

    fun get(id: String): TtsProvider? = providers[id]

    /** 配置里写的供应商不存在时回落到第一个，避免直接崩。 */
    fun active(id: String): TtsProvider = providers[id] ?: providers.values.first()
}
