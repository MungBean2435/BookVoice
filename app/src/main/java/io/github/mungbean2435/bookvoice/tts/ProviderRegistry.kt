package io.github.mungbean2435.bookvoice.tts

import io.github.mungbean2435.bookvoice.tts.mimo.MimoProvider
import io.github.mungbean2435.bookvoice.tts.minimax.MiniMaxProvider

/** 供应商注册表：加一家新供应商只要在这里注册一行。 */
object ProviderRegistry {

    private val providers = linkedMapOf(
        "mimo" to MimoProvider(),
        "minimax" to MiniMaxProvider()
    )

    fun all(): List<TtsProvider> = providers.values.toList()

    fun get(id: String): TtsProvider? = providers[id]

    /** 配置里写的供应商不存在时回落到第一个，避免直接崩。 */
    fun active(id: String): TtsProvider = providers[id] ?: providers.values.first()
}
