package io.github.mungbean2435.bookvoice.tts

/** 一家供应商的一套配置。Key 只存本机。 */
data class ProviderConfig(
    val apiKey: String = "",
    val baseUrl: String = "",
    val model: String = "",
    val voice: String = ""
)

/** 供应商能力的最小描述。 */
data class Voice(
    val id: String,
    val label: String,
    /** 中文女声 / 系统 / 克隆 / 生成 等短标签。 */
    val hint: String = ""
)

/** 一次「在线/内置目录」的结果。 */
data class TtsCatalog(
    val models: List<String> = emptyList(),
    val voices: List<Voice> = emptyList(),
    val fromNetwork: Boolean = false,
    val note: String = ""
)

data class SynthRequest(
    val text: String,
    val apiKey: String,
    val baseUrl: String,
    val model: String,
    val voiceId: String,
    val format: String = "wav",
    val speed: Double = 1.0
)

data class SynthResult(
    val audio: ByteArray,
    val mime: String,
    val finishReason: String?,
    val truncated: Boolean,
    val durationMs: Long
)

/** 供应商错误要能区分对待：Key 错不重试，限流退避，400 大概率是超长。 */
sealed class TtsException(message: String) : Exception(message) {
    class Auth(message: String) : TtsException(message)
    class TooLong(message: String) : TtsException(message)
    class RateLimited(message: String) : TtsException(message)
    class Network(message: String) : TtsException(message)
    class Server(message: String) : TtsException(message)
    class Empty(message: String) : TtsException(message)

    /** 账户余额/免费额度耗尽：必须停止整段朗读，不能当成单块失败继续跳段。 */
    class Quota(message: String) : TtsException(message)
}

interface TtsProvider {
    val id: String
    val displayName: String

    /** 这家返回的音频容器格式，决定缓存文件扩展名。 */
    val audioFormat: String

    /** 第一次使用这家供应商时的默认端点/模型。 */
    val defaultConfig: ProviderConfig

    fun voices(): List<Voice>

    /** 没有网络/Key 时也能用的目录。 */
    fun builtinCatalog(): TtsCatalog = TtsCatalog(
        models = listOf(defaultConfig.model).filter { it.isNotBlank() },
        voices = voices(),
        fromNetwork = false
    )

    /**
     * 尽力从供应商拉取目录。实现必须自己兜底，不把 Key 写进日志；
     * 失败时返回 builtinCatalog() + note，而不是让 UI 崩掉。
     */
    suspend fun fetchCatalog(config: ProviderConfig): TtsCatalog = builtinCatalog()

    suspend fun synthesize(request: SynthRequest): SynthResult
}
