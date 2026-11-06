package io.github.mungbean2435.bookvoice.tts.openai

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
import kotlin.coroutines.coroutineContext

/**
 * OpenAI 兼容的 `/audio/speech` 供应商。
 *
 * 默认填 OpenAI 官方端点；也可以填 SiliconFlow、Groq、LM Studio、
 * GPT-SoVITS 网关等兼容服务的完整 `.../audio/speech` 地址。
 * 也接受只填到 `.../v1`，会自动补 `/audio/speech`。
 */
class OpenAiCompatibleProvider : TtsProvider {

    override val id: String = "openai"
    override val displayName: String = "OpenAI 兼容"
    override val audioFormat: String = "mp3"
    override val defaultConfig: ProviderConfig = ProviderConfig(
        baseUrl = "https://api.openai.com/v1/audio/speech",
        model = "gpt-4o-mini-tts",
        voice = "alloy"
    )

    override fun voices(): List<Voice> = OPENAI_VOICES

    override fun builtinCatalog(): TtsCatalog = TtsCatalog(
        models = OPENAI_MODELS,
        voices = OPENAI_VOICES,
        fromNetwork = false,
        note = "内置常用模型与音色；兼容服务可能只支持其中一部分"
    )

    override suspend fun fetchCatalog(config: ProviderConfig): TtsCatalog =
        withContext(Dispatchers.IO) {
            val builtin = builtinCatalog()
            if (config.apiKey.isBlank()) return@withContext builtin
            val origin = runCatching { CatalogHttp.originOf(config.baseUrl) }.getOrElse {
                return@withContext builtin.copy(note = "接口地址无法解析，使用内置目录")
            }
            val result = runCatching {
                val (code, body) = CatalogHttp.requestJson(
                    url = "$origin/v1/models",
                    method = "GET",
                    headers = mapOf("Authorization" to "Bearer ${config.apiKey}")
                )
                if (code !in 200..299) throw TtsException.Server("HTTP $code")
                CatalogHttp.parseOpenAiModelIds(body)
                    .filter { id ->
                        id.contains("tts", ignoreCase = true) ||
                            id.contains("speech", ignoreCase = true) ||
                            id.contains("audio", ignoreCase = true)
                    }
                    .distinct()
            }
            val remote = result.getOrNull().orEmpty()
            if (remote.isEmpty()) {
                val why = if (result.isSuccess) {
                    "模型接口没有返回可用的 TTS 模型"
                } else {
                    "模型列表获取失败：${result.exceptionOrNull()?.message?.take(80) ?: "网络错误"}"
                }
                builtin.copy(note = "$why；使用内置目录")
            } else {
                builtin.copy(
                    models = (remote + builtin.models).distinct(),
                    fromNetwork = true,
                    note = "已在线获取模型；音色使用内置目录"
                )
            }
        }

    override suspend fun synthesize(request: SynthRequest): SynthResult =
        withContext(Dispatchers.IO) {
            if (request.apiKey.isBlank()) {
                throw TtsException.Auth("还没有填 OpenAI 兼容服务的 API Key")
            }
            val model = request.model.ifBlank { defaultConfig.model }
            val voice = request.voiceId.ifBlank { defaultConfig.voice }
            val payload = JSONObject()
                .put("model", model)
                .put("input", request.text)
                .put("voice", voice)
                .put("response_format", "mp3")
            val started = System.currentTimeMillis()
            val endpoint = speechEndpoint(request.baseUrl)
            val connection = try {
                (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 15_000
                    readTimeout = 60_000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    setRequestProperty("Accept", "audio/mpeg")
                    setRequestProperty("Authorization", "Bearer ${request.apiKey}")
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
                    throw mapHttpError(code, body.toString(Charsets.UTF_8))
                }
                if (body.isEmpty()) throw TtsException.Empty("没有收到音频数据")
                val contentType = connection.contentType ?: ""
                if (contentType.contains("json", ignoreCase = true)) {
                    throw mapHttpError(code, body.toString(Charsets.UTF_8))
                }
                SynthResult(
                    audio = body,
                    mime = if (contentType.startsWith("audio/")) contentType.substringBefore(';') else "audio/mpeg",
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

    private fun speechEndpoint(baseUrl: String): String {
        val url = baseUrl.trim().ifBlank { defaultConfig.baseUrl }.trimEnd('/')
        return when {
            url.endsWith("/audio/speech") -> url
            url.endsWith("/v1") -> "$url/audio/speech"
            else -> url
        }
    }

    private fun mapHttpError(code: Int, body: String): TtsException {
        val message = runCatching {
            JSONObject(body).optJSONObject("error")?.optString("message")
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: body.take(200).ifBlank { "HTTP $code" }
        return when (code) {
            401, 403 -> TtsException.Auth("鉴权失败：$message")
            413, 422 -> TtsException.TooLong("文本过长：$message")
            429 -> TtsException.RateLimited("请求太频繁：$message")
            400 -> if (
                message.contains("too long", true) ||
                message.contains("maximum", true) ||
                message.contains("context", true)
            ) {
                TtsException.TooLong("文本过长：$message")
            } else {
                TtsException.Server("请求失败（400）：$message")
            }
            in 500..599 -> TtsException.Server("服务端错误（$code）：$message")
            else -> TtsException.Server("HTTP $code：$message")
        }
    }

    private companion object {
        val OPENAI_MODELS = listOf("gpt-4o-mini-tts", "tts-1", "tts-1-hd")
        val OPENAI_VOICES = listOf(
            Voice("alloy", "Alloy", "中性"),
            Voice("ash", "Ash", "清晰"),
            Voice("ballad", "Ballad", "柔和"),
            Voice("coral", "Coral", "温暖"),
            Voice("echo", "Echo", "沉稳"),
            Voice("fable", "Fable", "叙述"),
            Voice("onyx", "Onyx", "低沉"),
            Voice("nova", "Nova", "明亮"),
            Voice("sage", "Sage", "平静"),
            Voice("shimmer", "Shimmer", "轻盈"),
            Voice("verse", "Verse", "自然")
        )
    }
}
