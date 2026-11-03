package io.github.mungbean2435.bookvoice.tts.catalog

import io.github.mungbean2435.bookvoice.tts.TtsException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** 供应商目录接口的小工具：只做 HTTP + JSON，不碰 UI、不记录 Key。 */
internal object CatalogHttp {

    fun originOf(baseUrl: String): String {
        val url = URL(baseUrl.trim())
        val port = if (url.port > 0 && url.port != url.defaultPort) ":${url.port}" else ""
        return "${url.protocol}://${url.host}$port"
    }

    fun requestJson(
        url: String,
        method: String,
        headers: Map<String, String>,
        body: String? = null,
        connectTimeoutMs: Int = 10_000,
        readTimeoutMs: Int = 10_000
    ): Pair<Int, String> {
        val connection = try {
            (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                instanceFollowRedirects = true
                setRequestProperty("Accept", "application/json")
                headers.forEach { (name, value) -> setRequestProperty(name, value) }
                if (body != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                }
            }
        } catch (e: Exception) {
            throw TtsException.Network("网络错误：${e.message ?: "地址无法访问"}")
        }

        return try {
            if (body != null) {
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            code to text
        } catch (e: IOException) {
            throw TtsException.Network("网络错误：${e.message ?: "连接失败"}")
        } finally {
            connection.disconnect()
        }
    }

    /** OpenAI 风格 `{"data":[{"id":"..."}]}`。不是这个形状就返回空。 */
    fun parseOpenAiModelIds(body: String): List<String> = runCatching {
        val data = JSONObject(body).optJSONArray("data") ?: return@runCatching emptyList()
        (0 until data.length()).mapNotNull { index ->
            data.optJSONObject(index)?.optString("id")?.trim()?.takeIf { it.isNotEmpty() }
        }
    }.getOrDefault(emptyList())
}
