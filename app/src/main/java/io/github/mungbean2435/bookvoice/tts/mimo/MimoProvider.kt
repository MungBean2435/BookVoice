package io.github.mungbean2435.bookvoice.tts.mimo

import android.util.Base64
import io.github.mungbean2435.bookvoice.tts.StreamResult
import io.github.mungbean2435.bookvoice.tts.SynthRequest
import io.github.mungbean2435.bookvoice.tts.SynthResult
import io.github.mungbean2435.bookvoice.tts.TtsCatalog
import io.github.mungbean2435.bookvoice.tts.TtsException
import io.github.mungbean2435.bookvoice.tts.ProviderConfig
import io.github.mungbean2435.bookvoice.tts.TtsProvider
import io.github.mungbean2435.bookvoice.tts.Voice
import io.github.mungbean2435.bookvoice.tts.catalog.CatalogHttp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * 小米 MiMo TTS。实测协议（来自本机探测脚本，不是猜的）：
 *   POST {baseUrl}
 *   header: api-key: <KEY>
 *   body: {model, messages:[{role:"assistant",content:text}], audio:{format:"wav",voice}, stream:false}
 *   → choices[0].message.audio.data (base64) + choices[0].finish_reason
 */
class MimoProvider : TtsProvider {

    override val id: String = "mimo"
    override val displayName: String = "小米 MiMo"
    override val audioFormat: String = "wav"
    override val supportsStreaming: Boolean get() = true
    override val defaultConfig: ProviderConfig = ProviderConfig(
        baseUrl = "https://api.xiaomimimo.com/v1/chat/completions",
        model = "mimo-v2.5-tts",
        voice = "mimo_default"
    )

    override fun voices(): List<Voice> = MIMO_VOICES

    override fun builtinCatalog(): TtsCatalog = TtsCatalog(
        models = listOf("mimo-v2.5-tts"),
        voices = MIMO_VOICES,
        fromNetwork = false,
        note = "MiMo 没有音色列表接口，使用官方内置音色"
    )

    override suspend fun fetchCatalog(config: ProviderConfig): TtsCatalog =
        withContext(Dispatchers.IO) {
            val builtin = builtinCatalog()
            if (config.apiKey.isBlank()) return@withContext builtin
            val base = config.baseUrl.ifBlank { defaultConfig.baseUrl }
            val origin = runCatching { CatalogHttp.originOf(base) }.getOrElse {
                return@withContext builtin.copy(note = "接口地址无法解析，使用内置目录")
            }
            val result = runCatching {
                val (code, body) = CatalogHttp.requestJson(
                    url = "$origin/v1/models",
                    method = "GET",
                    headers = mapOf("api-key" to config.apiKey)
                )
                if (code !in 200..299) throw TtsException.Server("HTTP $code")
                CatalogHttp.parseOpenAiModelIds(body)
            }
            val remote = result.getOrNull().orEmpty()
                .filter { it == "mimo-v2.5-tts" }
                .distinct()
            if (remote.isEmpty()) {
                val why = if (result.isSuccess) {
                    "模型接口没有返回可用的 TTS 模型"
                } else {
                    "模型列表获取失败：${result.exceptionOrNull()?.message ?: "网络错误"}"
                }
                builtin.copy(note = "$why；使用内置目录")
            } else {
                builtin.copy(
                    models = (remote + builtin.models).distinct(),
                    fromNetwork = true,
                    note = "已在线获取模型；音色使用官方内置目录"
                )
            }
        }

    override suspend fun synthesize(request: SynthRequest): SynthResult = withContext(Dispatchers.IO) {
        if (request.apiKey.isBlank()) throw TtsException.Auth("还没有填 MiMo API Key")

        val payload = payload(request, stream = false, audioFormat = request.format)

        val connection = (URL(request.baseUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 30_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("api-key", request.apiKey)
        }
        // 预取/切书取消时，断开阻塞中的读，别让合成协程挂到 readTimeout。
        coroutineContext[Job]?.invokeOnCompletion { cause ->
            if (cause != null) connection.disconnect()
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

        if (code !in 200..299) throw mapError(code, body)

        val json = runCatching { JSONObject(body) }
            .getOrElse { throw TtsException.Server("响应不是 JSON：${body.take(120)}") }
        val choice = json.optJSONArray("choices")?.optJSONObject(0)
            ?: throw TtsException.Empty("响应里没有 choices")
        val data = choice.optJSONObject("message")?.optJSONObject("audio")?.optString("data").orEmpty()
        if (data.isBlank()) throw TtsException.Empty("响应里没有音频数据")

        val base64 = if (data.startsWith("data:")) data.substringAfter("base64,", "") else data
        val bytes = runCatching { Base64.decode(base64, Base64.DEFAULT) }
            .getOrElse { throw TtsException.Server("音频解码失败") }

        val finish = choice.optString("finish_reason", "").ifBlank { null }
        val expectedMs = request.text.length * 196L
        val actualMs = wavDurationMs(bytes).takeIf { it > 0 } ?: expectedMs
        // 实测：4500 字会被"压缩"，5000 字直接截断；时长明显短于预期就判定不可信
        val truncated = finish == "length" || (expectedMs > 0 && actualMs < expectedMs * 7 / 10)

        SynthResult(
            audio = bytes,
            mime = "audio/wav",
            finishReason = finish,
            truncated = truncated,
            durationMs = actualMs
        )
    }

    /**
     * 流式合成：MiMo SSE 的音频在 choices[0].delta.audio.data（base64 PCM16，24kHz 单声道）。
     * 实测首块 0.8s 左右到达，比非流式整块（150 字约 8s）快得多。
     */
    override suspend fun streamPcm(
        request: SynthRequest,
        onPcm: (ByteArray) -> Unit
    ): StreamResult = withContext(Dispatchers.IO) {
        if (request.apiKey.isBlank()) throw TtsException.Auth("还没有填 MiMo API Key")
        val payload = payload(request, stream = true, audioFormat = "pcm16")

        val connection = (URL(request.baseUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            // 流式连接会持续有数据；20s 空闲视为断流（非流式整块要 30s，故单独放宽）
            readTimeout = 20_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "text/event-stream")
            setRequestProperty("api-key", request.apiKey)
        }

        var total = 0L
        var finish: String? = null
        // 取消时断开连接：invokeOnCompletion 要等协程真正结束才触发，而 readLine 正阻塞着，
        // 只靠它会导致协程永远结束不了。这里用独立协程轮询 isActive，一取消就 disconnect。
        val job = coroutineContext[Job]
        val cancellation = job?.invokeOnCompletion { cause ->
            if (cause != null) runCatching { connection.disconnect() }
        }
        val forceClose = CoroutineScope(Dispatchers.IO).launch {
            while (job != null && job.isActive) delay(200L)
            runCatching { connection.disconnect() }
        }
        try {
            connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            if (code !in 200..299) {
                val body = connection.errorStream?.bufferedReader(Charsets.UTF_8)
                    ?.use { it.readText() }.orEmpty()
                throw mapError(code, body)
            }
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                while (true) {
                    val line = reader.readLine() ?: break
                    if (!line.startsWith("data:")) continue
                    val data = line.substring(5).trim()
                    if (data.isEmpty()) continue
                    if (data == "[DONE]") break
                    val json = runCatching { JSONObject(data) }.getOrNull() ?: continue
                    json.optJSONObject("error")?.let { error ->
                        val message = error.optString("message").ifBlank { error.toString() }
                        throw TtsException.Server("MiMo 流式错误：${message.take(120)}")
                    }
                    val choice = json.optJSONArray("choices")?.optJSONObject(0) ?: continue
                    choice.optString("finish_reason").takeIf { it.isNotBlank() }?.let { finish = it }
                    val audio = (choice.optJSONObject("delta") ?: choice.optJSONObject("message"))
                        ?.optJSONObject("audio") ?: continue
                    val data64 = audio.optString("data")
                    if (data64.isBlank()) continue
                    val pcm = runCatching { Base64.decode(data64, Base64.DEFAULT) }
                        .getOrElse { throw TtsException.Server("音频解码失败") }
                    if (pcm.isNotEmpty()) {
                        total += pcm.size
                        onPcm(pcm)
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: TtsException) {
            throw e
        } catch (e: IOException) {
            throw TtsException.Network("网络错误：${e.message ?: "连接失败"}")
        } catch (e: Exception) {
            throw TtsException.Server("流式解析失败：${e.message ?: "未知错误"}")
        } finally {
            forceClose.cancel()
            cancellation?.dispose()
            connection.disconnect()
        }

        if (total == 0L) throw TtsException.Empty("流式响应里没有音频数据")
        val durationMs = total * 1000L / 48_000L
        val expectedMs = request.text.length * 196L
        StreamResult(
            pcmBytes = total,
            durationMs = durationMs,
            finishReason = finish,
            truncated = finish == "length" || (expectedMs > 0 && durationMs < expectedMs * 7 / 10)
        )
    }

    private fun payload(request: SynthRequest, stream: Boolean, audioFormat: String): JSONObject =
        JSONObject().apply {
            put("model", request.model)
            put(
                "messages",
                JSONArray().put(
                    JSONObject().apply {
                        put("role", "assistant")
                        put("content", request.text)
                    }
                )
            )
            put(
                "audio",
                JSONObject().apply {
                    put("format", audioFormat)
                    put("voice", request.voiceId)
                }
            )
            put("stream", stream)
        }

    private fun mapError(code: Int, body: String): TtsException = when {
        code == 402 -> TtsException.Quota("MiMo 账户余额不足或额度用尽，请到控制台充值/查看额度")
        code == 401 || code == 403 -> TtsException.Auth("Key 无效或没有权限")
        code == 429 -> TtsException.RateLimited("请求太频繁，稍后再试")
        code == 400 -> TtsException.TooLong("请求被拒绝（通常是文本过长）")
        code >= 500 -> TtsException.Server("服务端错误 ($code)")
        else -> TtsException.Server("HTTP $code：${body.take(120)}")
    }

    /** 从 WAV 头读时长；不是标准 WAV 就返回 0，交给调用方用字数估算。 */
    private fun wavDurationMs(bytes: ByteArray): Long {
        if (bytes.size < 44) return 0
        if (bytes[0] != 'R'.code.toByte() || bytes[1] != 'I'.code.toByte() ||
            bytes[2] != 'F'.code.toByte() || bytes[3] != 'F'.code.toByte()
        ) {
            return 0
        }
        val byteRate = leInt(bytes, 28)
        if (byteRate <= 0) return 0

        var offset = 12
        while (offset + 8 <= bytes.size) {
            val id = String(bytes, offset, 4, Charsets.US_ASCII)
            val size = leInt(bytes, offset + 4)
            if (id == "data") {
                val dataSize = if (size > 0 && offset + 8 + size <= bytes.size) size else bytes.size - offset - 8
                return dataSize.toLong() * 1000 / byteRate
            }
            if (size < 0) break
            offset += 8 + size + (size and 1)
        }
        val fallback = (bytes.size - 44).toLong()
        return if (fallback > 0) fallback * 1000 / byteRate else 0
    }

    private fun leInt(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)
}

/** 官方预置音色：https://mimo.mi.com/docs/zh-CN/quick-start/usage-guide/audio/speech-synthesis-v2.5 */
private val MIMO_VOICES = listOf(
    Voice("mimo_default", "MiMo-默认", "随集群，通常冰糖 / Mia"),
    Voice("冰糖", "冰糖", "中文 · 女声"),
    Voice("茉莉", "茉莉", "中文 · 女声"),
    Voice("苏打", "苏打", "中文 · 男声"),
    Voice("白桦", "白桦", "中文 · 男声"),
    Voice("Mia", "Mia", "英文 · 女声"),
    Voice("Chloe", "Chloe", "英文 · 女声"),
    Voice("Milo", "Milo", "英文 · 男声"),
    Voice("Dean", "Dean", "英文 · 男声")
)
