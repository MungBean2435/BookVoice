package io.github.mungbean2435.bookvoice.tts.minimax

import io.github.mungbean2435.bookvoice.tts.ProviderConfig
import io.github.mungbean2435.bookvoice.tts.SynthRequest
import io.github.mungbean2435.bookvoice.tts.SynthResult
import io.github.mungbean2435.bookvoice.tts.TtsCatalog
import io.github.mungbean2435.bookvoice.tts.TtsException
import io.github.mungbean2435.bookvoice.tts.TtsProvider
import io.github.mungbean2435.bookvoice.tts.Voice
import io.github.mungbean2435.bookvoice.tts.catalog.CatalogHttp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * MiniMax 同步语音合成（T2A v2）。
 * 文档：https://platform.minimax.io/docs/api-reference/speech-t2a-http
 * 请求：POST /v1/t2a_v2，Bearer 鉴权，返回 data.audio 为 **hex 编码**。
 * 输出格式只有 mp3 / pcm / flac，没有 wav，所以这里选 mp3。
 */
class MiniMaxProvider : TtsProvider {

    override val id: String = "minimax"
    override val displayName: String = "MiniMax"
    override val audioFormat: String = "mp3"
    override val defaultConfig: ProviderConfig = ProviderConfig(
        baseUrl = "https://api.minimax.cn/v1/t2a_v2",
        model = "speech-2.8-hd",
        voice = ""
    )

    override fun voices(): List<Voice> = MINIMAX_BUILTIN_VOICES

    override fun builtinCatalog(): TtsCatalog = TtsCatalog(
        models = MINIMAX_MODELS,
        voices = MINIMAX_BUILTIN_VOICES,
        fromNetwork = false,
        note = "使用内置目录；填 Key 可获取账号音色"
    )

    override suspend fun fetchCatalog(config: ProviderConfig): TtsCatalog =
        withContext(Dispatchers.IO) {
            val builtin = builtinCatalog()
            if (config.apiKey.isBlank()) return@withContext builtin
            val base = config.baseUrl.ifBlank { defaultConfig.baseUrl }
            val origin = runCatching { CatalogHttp.originOf(base) }.getOrElse {
                return@withContext builtin.copy(note = "接口地址无法解析，使用内置目录")
            }
            coroutineScope {
                val voiceJob = async { runCatching { fetchVoices(origin, config.apiKey) } }
                val modelJob = async { runCatching { fetchModels(origin, config.apiKey) } }
                val voiceResult = voiceJob.await()
                val modelResult = modelJob.await()

                val voices = voiceResult.getOrNull()?.takeIf { it.isNotEmpty() }
                val models = modelResult.getOrNull()
                    ?.filter { it.startsWith("speech") }
                    ?.distinct()
                    ?.takeIf { it.isNotEmpty() }

                val notes = buildList {
                    if (voices != null) {
                        add("已在线获取 ${voices.size} 个音色")
                    } else {
                        add(
                            "音色获取失败：${
                                voiceResult.exceptionOrNull()?.message?.take(120) ?: "未知错误"
                            }，使用内置音色"
                        )
                    }
                    if (models != null) {
                        add("模型已在线获取")
                    } else {
                        add("模型列表不可用，使用内置模型")
                    }
                }
                TtsCatalog(
                    models = (models.orEmpty() + builtin.models).distinct(),
                    voices = voices ?: builtin.voices,
                    fromNetwork = voices != null || models != null,
                    note = notes.joinToString("；")
                )
            }
        }

    private suspend fun fetchVoices(origin: String, apiKey: String): List<Voice> {
        val (code, body) = CatalogHttp.requestJson(
            url = "$origin/v1/get_voice",
            method = "POST",
            headers = mapOf("Authorization" to "Bearer $apiKey"),
            body = """{"voice_type":"all"}"""
        )
        if (code !in 200..299) throw TtsException.Server("HTTP $code")
        val json = runCatching { JSONObject(body) }.getOrElse {
            throw TtsException.Server("音色响应不是 JSON")
        }
        val base = json.optJSONObject("base_resp")
        val status = base?.optInt("status_code", 0) ?: 0
        if (status != 0) {
            val raw = base?.optString("status_msg").orEmpty().take(120)
            throw miniMaxStatusError(status, raw)
        }

        val out = LinkedHashMap<String, Voice>()
        fun add(array: JSONArray?, hint: String) {
            if (array == null) return
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val id = item.optString("voice_id").trim()
                if (id.isEmpty()) continue
                // speech-2.6 / 2.8 会拒绝 speech-01 旧音色，目录里不再提供
                if (isLegacyMiniMaxVoice(id)) continue
                val label = item.optString("voice_name").trim().ifBlank { id }
                out.putIfAbsent(id, Voice(id, label, hint))
            }
        }
        add(json.optJSONArray("system_voice"), "系统")
        add(json.optJSONArray("voice_cloning"), "克隆")
        add(json.optJSONArray("voice_generation"), "生成")
        if (out.isEmpty()) throw TtsException.Empty("没有返回音色")
        return out.values.toList()
    }

    private suspend fun fetchModels(origin: String, apiKey: String): List<String> {
        val (code, body) = CatalogHttp.requestJson(
            url = "$origin/v1/models",
            method = "GET",
            headers = mapOf("Authorization" to "Bearer $apiKey")
        )
        if (code !in 200..299) throw TtsException.Server("HTTP $code")
        val ids = CatalogHttp.parseOpenAiModelIds(body)
            .filter { it.startsWith("speech") }
            .distinct()
        if (ids.isEmpty()) throw TtsException.Empty("没有 TTS 模型")
        return ids
    }

    override suspend fun synthesize(request: SynthRequest): SynthResult = withContext(Dispatchers.IO) {
        if (request.apiKey.isBlank()) throw TtsException.Auth("还没有填 MiniMax API Key")

        val payload = JSONObject().apply {
            put("model", request.model)
            put("text", request.text)
            put("stream", false)
            put("output_format", "hex")
            put(
                "voice_setting",
                JSONObject().apply {
                    put("voice_id", request.voiceId)
                    put("speed", request.speed)
                    put("vol", 1.0)
                    put("pitch", 0)
                }
            )
            put(
                "audio_setting",
                JSONObject().apply {
                    put("sample_rate", 32000)
                    put("bitrate", 128000)
                    put("format", "mp3")
                    put("channel", 1)
                }
            )
        }

        val connection = (URL(request.baseUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 30_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", "Bearer ${request.apiKey}")
        }

        val body: String
        val code: Int
        try {
            connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        } catch (e: IOException) {
            throw TtsException.Network("网络错误：${e.message ?: "连接失败"}")
        } finally {
            connection.disconnect()
        }

        if (code == 401 || code == 403) throw TtsException.Auth("Key 无效或没有权限")
        if (code == 429) throw TtsException.RateLimited("请求太频繁，稍后再试")
        if (code !in 200..299) throw TtsException.Server("HTTP $code：${body.take(160)}")

        val json = runCatching { JSONObject(body) }
            .getOrElse { throw TtsException.Server("响应不是 JSON：${body.take(160)}") }

        // MiniMax 用 HTTP 200 + base_resp.status_code 表示业务错误
        val base = json.optJSONObject("base_resp")
        val statusCode = base?.optInt("status_code", 0) ?: 0
        if (statusCode != 0) {
            val raw = base?.optString("status_msg").orEmpty()
            throw miniMaxStatusError(statusCode, raw)
        }

        val audioHex = json.optJSONObject("data")?.optString("audio").orEmpty()
        if (audioHex.isBlank()) throw TtsException.Empty("响应里没有音频")
        val bytes = hexToBytes(audioHex)

        val extra = json.optJSONObject("extra_info")
        val durationMs = extra?.optLong("audio_length", 0L) ?: 0L
        val expectedMs = request.text.length * 196L
        val truncated = durationMs > 0 && expectedMs > 0 && durationMs < expectedMs * 7 / 10

        SynthResult(
            audio = bytes,
            mime = "audio/mpeg",
            finishReason = null,
            truncated = truncated,
            durationMs = if (durationMs > 0) durationMs else expectedMs
        )
    }

}

/**
 * 把 MiniMax 的英文错误码翻成异常类型。
 * 1008 余额不足是 [TtsException.Quota]：播放器必须停下来提示用户，
 * 不能走「记一条待补、继续下一块」的路径，否则会快速跳完整本书。
 */
internal fun miniMaxStatusError(statusCode: Int, raw: String): TtsException = when (statusCode) {
    1008 -> TtsException.Quota("MiniMax 账户余额不足，请到 MiniMax 控制台充值或查看免费额度")
    1004 -> TtsException.Auth("MiniMax API Key 无效或没有权限")
    2054 -> TtsException.Server("MiniMax 音色不存在或不被当前模型支持；请换用 Chinese (Mandarin)_* 新音色")
    2013 -> TtsException.Server(
        if (raw.isBlank()) "MiniMax 参数错误" else "MiniMax 参数错误：$raw"
    )
    1024 -> TtsException.Server("MiniMax 服务端内部错误，请稍后再试")
    else -> TtsException.Server(raw.ifBlank { "MiniMax 错误码 $statusCode" })
}

/** hex → 字节。奇数长度或非法字符直接抛错，不静默截断。 */
internal fun hexToBytes(hex: String): ByteArray {
    if (hex.length % 2 != 0) throw TtsException.Server("音频数据长度异常")
    val out = ByteArray(hex.length / 2)
    for (i in out.indices) {
        val high = Character.digit(hex[i * 2], 16)
        val low = Character.digit(hex[i * 2 + 1], 16)
        if (high < 0 || low < 0) throw TtsException.Server("音频数据不是合法 hex")
        out[i] = ((high shl 4) or low).toByte()
    }
    return out
}

private val MINIMAX_MODELS = listOf(
    "speech-2.8-hd",
    "speech-2.8-turbo",
    "speech-2.6-hd",
    "speech-2.6-turbo"
)

/**
 * 新版模型只认现代音色 ID；旧 speech-01 的 female-shaonv 等会被 2054 拒绝。
 * 这里选系统音色表里常用的中文音色，联网后会用账号目录覆盖。
 */
private val MINIMAX_BUILTIN_VOICES = listOf(
    Voice("Chinese (Mandarin)_Warm_Girl", "温暖少女", "中文 · 女声"),
    Voice("Chinese (Mandarin)_News_Anchor", "新闻女声", "中文 · 女声"),
    Voice("Chinese (Mandarin)_Gentle_Senior", "温柔学姐", "中文 · 女声"),
    Voice("Chinese (Mandarin)_Reliable_Executive", "沉稳高管", "中文 · 男声"),
    Voice("Chinese (Mandarin)_Crisp_Girl", "清脆少女", "中文 · 女声"),
    Voice("Chinese (Mandarin)_Soft_Girl", "柔和少女", "中文 · 女声"),
    Voice("Chinese (Mandarin)_Warm_Bestie", "温暖闺蜜", "中文 · 女声"),
    Voice("Chinese (Mandarin)_Male_Announcer", "播报男声", "中文 · 男声"),
    Voice("Chinese (Mandarin)_Gentleman", "温润男声", "中文 · 男声"),
    Voice("Chinese (Mandarin)_Lyrical_Voice", "抒情男声", "中文 · 男声")
)
