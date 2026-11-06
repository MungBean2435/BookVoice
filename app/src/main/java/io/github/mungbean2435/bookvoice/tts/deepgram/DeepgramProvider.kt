package io.github.mungbean2435.bookvoice.tts.deepgram

import io.github.mungbean2435.bookvoice.tts.ProviderConfig
import io.github.mungbean2435.bookvoice.tts.SynthRequest
import io.github.mungbean2435.bookvoice.tts.SynthResult
import io.github.mungbean2435.bookvoice.tts.TtsCatalog
import io.github.mungbean2435.bookvoice.tts.TtsException
import io.github.mungbean2435.bookvoice.tts.TtsProvider
import io.github.mungbean2435.bookvoice.tts.Voice
import io.github.mungbean2435.bookvoice.tts.catalog.CatalogHttp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.coroutines.coroutineContext

/**
 * Deepgram TTS：Flux TTS（`/v2/speak`）与 Aura（`/v1/speak`）。
 *
 * Deepgram 把音色和语言都编进 model 字符串（`flux-wenjing-zh`、
 * `aura-2-thalia-en`），所以 [ProviderConfig.voice] 存完整的 model，
 * [ProviderConfig.model] 只存引擎系列，用来显示和给用户手动填。
 * 请求端点按音色自动选 v2 / v1；接口地址填到域名即可，也可填完整地址。
 */
class DeepgramProvider : TtsProvider {

    override val id: String = "deepgram"
    override val displayName: String = "Deepgram"
    override val audioFormat: String = "mp3"
    override val defaultConfig: ProviderConfig = ProviderConfig(
        baseUrl = "https://api.deepgram.com",
        model = DEEPGRAM_MODEL_FLUX,
        voice = DEEPGRAM_DEFAULT_VOICE
    )

    override fun voices(): List<Voice> = DEEPGRAM_BUILTIN_VOICES

    override fun builtinCatalog(): TtsCatalog = TtsCatalog(
        models = DEEPGRAM_MODELS,
        voices = DEEPGRAM_BUILTIN_VOICES,
        fromNetwork = false,
        note = "内置 Flux 中文 / 英文与 Aura 音色；Flux 中文为 Early Access"
    )

    override suspend fun fetchCatalog(config: ProviderConfig): TtsCatalog =
        withContext(Dispatchers.IO) {
            val builtin = builtinCatalog()
            if (config.apiKey.isBlank()) return@withContext builtin
            val origin = runCatching {
                CatalogHttp.originOf(config.baseUrl.ifBlank { defaultConfig.baseUrl })
            }.getOrElse {
                return@withContext builtin.copy(note = "接口地址无法解析，使用内置目录")
            }
            val result = runCatching {
                val (code, body) = CatalogHttp.requestJson(
                    url = "$origin/v1/models",
                    method = "GET",
                    headers = mapOf("Authorization" to "Token ${config.apiKey}")
                )
                if (code !in 200..299) {
                    throw deepgramError(code, deepgramErrorMessage(body))
                }
                parseDeepgramTtsModels(body)
            }
            val remote = result.getOrNull().orEmpty()
            if (remote.isEmpty()) {
                val why = if (result.isSuccess) {
                    "模型接口没有返回可用的 TTS 音色"
                } else {
                    "音色目录获取失败：${result.exceptionOrNull()?.message?.take(120) ?: "网络错误"}"
                }
                builtin.copy(note = "$why；使用内置目录")
            } else {
                val ids = (remote + builtin.voices.map { it.id }).distinct()
                builtin.copy(
                    voices = ids.map(::deepgramVoiceFor),
                    fromNetwork = true,
                    note = "已在线获取 ${remote.size} 个音色；Flux 中文来自内置目录，Early Access"
                )
            }
        }

    override suspend fun synthesize(request: SynthRequest): SynthResult =
        withContext(Dispatchers.IO) {
            if (request.apiKey.isBlank()) {
                throw TtsException.Auth("还没有填 Deepgram API Key")
            }
            val model = deepgramResolveModel(
                model = request.model,
                voice = request.voiceId,
                fallback = defaultConfig.voice
            )
            val endpoint = deepgramEndpoint(request.baseUrl, model)
            val url = "$endpoint?model=${URLEncoder.encode(model, "UTF-8")}&encoding=mp3"
            val payload = JSONObject().put("text", request.text)

            val started = System.currentTimeMillis()
            val connection = try {
                (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 15_000
                    readTimeout = 60_000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    setRequestProperty("Accept", "audio/mpeg")
                    // Deepgram 用 Token 前缀，不是 Bearer。
                    setRequestProperty("Authorization", "Token ${request.apiKey}")
                }
            } catch (e: Exception) {
                throw TtsException.Network("接口地址无法访问：${e.message ?: "URL 无效"}")
            }

            // 预取/切书取消时断开阻塞中的读，避免协程挂到 readTimeout。
            coroutineContext[Job]?.invokeOnCompletion { cause ->
                if (cause != null) connection.disconnect()
            }

            try {
                connection.outputStream.use {
                    it.write(payload.toString().toByteArray(Charsets.UTF_8))
                }
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val body = stream?.use { it.readBytes() } ?: ByteArray(0)
                if (code !in 200..299) {
                    throw deepgramError(code, deepgramErrorMessage(body))
                }
                if (body.isEmpty()) throw TtsException.Empty("没有收到音频数据")
                val contentType = connection.contentType ?: ""
                if (contentType.contains("json", ignoreCase = true)) {
                    throw TtsException.Server(
                        "Deepgram 返回了 JSON 而不是音频：${deepgramErrorMessage(body)}"
                    )
                }
                SynthResult(
                    audio = body,
                    mime = if (contentType.startsWith("audio/")) {
                        contentType.substringBefore(';')
                    } else {
                        "audio/mpeg"
                    },
                    finishReason = null,
                    truncated = false,
                    durationMs = System.currentTimeMillis() - started
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: TtsException) {
                throw e
            } catch (e: IOException) {
                throw TtsException.Network("网络错误：${e.message ?: "连接失败"}")
            } finally {
                connection.disconnect()
            }
        }

    private fun deepgramErrorMessage(raw: ByteArray): String =
        deepgramErrorMessage(raw.toString(Charsets.UTF_8))

    private fun deepgramErrorMessage(text: String): String {
        if (text.isBlank()) return ""
        val json = runCatching { JSONObject(text) }.getOrNull() ?: return text.take(200)
        for (key in listOf("err_msg", "message", "details", "error")) {
            val value = json.optString(key).trim()
            if (value.isNotEmpty()) return value.take(200)
        }
        return text.take(200)
    }
}

/**
 * 音色优先：用户选了 `flux-...` / `aura-...` 就直接用它；
 * 否则接受手动填在 model 里的完整 model；都没有才回落默认音色。
 */
internal fun deepgramResolveModel(model: String, voice: String, fallback: String): String {
    if (deepgramIsModel(voice)) return voice.trim()
    if (deepgramIsModel(model)) return model.trim()
    return fallback
}

/**
 * 接口地址默认只填域名；按音色选 `/v2/speak`（Flux）或 `/v1/speak`（Aura）。
 * 用户明确写了完整 `/v1/speak` 或 `/v2/speak` 时保留，便于走代理/区域端点。
 */
internal fun deepgramEndpoint(baseUrl: String, model: String): String {
    val raw = baseUrl.trim().ifBlank { "https://api.deepgram.com" }.trimEnd('/')
    val path = if (model.startsWith("flux-")) "v2/speak" else "v1/speak"
    return when {
        raw.endsWith("/v1/speak") || raw.endsWith("/v2/speak") -> raw
        raw.endsWith("/v1") || raw.endsWith("/v2") -> "${raw.substringBeforeLast('/')}/$path"
        else -> "$raw/$path"
    }
}

/** Deepgram 用 402 表示余额/额度不足：播放器要停下来，不能继续跳段。 */
internal fun deepgramError(code: Int, message: String): TtsException {
    val detail = message.ifBlank { "HTTP $code" }
    return when (code) {
        401 -> TtsException.Auth("Deepgram 鉴权失败：$detail")
        403 -> {
            val quota = listOf("quota", "credit", "balance", "fund", "billing", "payment")
                .any { detail.contains(it, ignoreCase = true) }
            if (quota) {
                TtsException.Quota("Deepgram 账户余额不足或额度用尽：$detail")
            } else {
                TtsException.Auth("Deepgram 鉴权失败：$detail")
            }
        }
        402 -> TtsException.Quota("Deepgram 账户余额不足或额度用尽：$detail")
        413, 422 -> TtsException.TooLong("文本过长：$detail")
        429 -> TtsException.RateLimited("请求太频繁：$detail")
        400 -> {
            val tooLong = listOf(
                "too long",
                "maximum",
                "limit",
                "characters",
                "text length"
            ).any { detail.contains(it, ignoreCase = true) }
            if (tooLong) {
                TtsException.TooLong("文本过长：$detail")
            } else {
                TtsException.Server("请求失败（400）：$detail")
            }
        }
        in 500..599 -> TtsException.Server("Deepgram 服务端错误（$code）：$detail")
        else -> TtsException.Server("HTTP $code：$detail")
    }
}
