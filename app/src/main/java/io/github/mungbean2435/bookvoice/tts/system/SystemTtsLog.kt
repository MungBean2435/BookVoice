package io.github.mungbean2435.bookvoice.tts.system

import android.content.Context
import android.os.Build
import io.github.mungbean2435.bookvoice.data.settings.AppInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 系统 TTS 的调试日志。
 *
 * 目的：真机上系统引擎「不响」时，能直接看到初始化、音色、语言、合成、播放
 * 每一步的真实返回值，而不是只看到一句笼统的失败提示。
 *
 * - 内存里保留最近 [MAX_LINES] 行，日志页实时刷新；
 * - 同时追加到私有目录的 `system-tts.log`，带 256 KB 轮转，重启后仍可查；
 * - 不记录正文，只记录字数、音色 ID、语言、返回码、字节数、异常；
 * - 完整日志会随「设置 → 诊断 → 导出诊断」一起导出，也可在日志页复制全文。
 */
object SystemTtsLog {

    private const val MAX_LINES = 400
    private const val MAX_BYTES = 256 * 1024L
    private const val MAX_MESSAGE = 1200
    private const val FILE_NAME = "system-tts.log"

    private val lock = Any()
    private val buffer = ArrayDeque<String>()
    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = _lines.asStateFlow()

    private val io = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "system-tts-log").apply { isDaemon = true }
    }

    @Volatile
    private var logFile: File? = null

    private val timeFormat = ThreadLocal.withInitial {
        SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)
    }

    fun init(context: Context) {
        if (logFile != null) return
        val file = File(context.filesDir, FILE_NAME)
        logFile = file
        io.execute { loadTail(file) }
        record(
            "启动",
            "BookVoice ${AppInfo.versionName(context)}；Android " +
                "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})；" +
                "${Build.MANUFACTURER} ${Build.MODEL}"
        )
    }

    /** [tag] 用短标签（初始化 / 引擎 / 音色 / 合成 / 播放 / 错误 / 设置），方便扫日志。 */
    fun record(tag: String, message: String) {
        val line = "${timeFormat.get()!!.format(Date())} [${tag}] ${sanitize(message)}"
        synchronized(lock) {
            buffer.addLast(line)
            while (buffer.size > MAX_LINES) buffer.removeFirst()
            _lines.value = buffer.toList()
        }
        io.execute { append(line) }
    }

    /** 完整日志（含上一次运行），供复制 / 导出。 */
    fun text(): String {
        // 先把排队的写入落盘，复制时不会漏掉刚发生的最后一行。
        runCatching { io.submit { }.get(2, TimeUnit.SECONDS) }
        return synchronized(lock) {
            val file = logFile
            val fromFile = if (file != null) {
                runCatching {
                    val old = File(file.parentFile, "$FILE_NAME.old")
                    buildString {
                        if (old.exists()) append(old.readText())
                        if (file.exists()) append(file.readText())
                    }
                }.getOrNull()
            } else {
                null
            }
            fromFile?.takeIf { it.isNotBlank() } ?: buffer.joinToString("\n")
        }
    }

    fun clear() {
        synchronized(lock) {
            buffer.clear()
            _lines.value = emptyList()
        }
        io.execute {
            val file = logFile ?: return@execute
            runCatching { file.delete() }
            runCatching { File(file.parentFile, "$FILE_NAME.old").delete() }
        }
    }

    private fun loadTail(file: File) {
        val tail = runCatching {
            if (!file.exists()) return@runCatching emptyList()
            file.readLines().takeLast(MAX_LINES)
        }.getOrDefault(emptyList())
        if (tail.isEmpty()) return
        synchronized(lock) {
            // 如果启动后已经产生了新日志，旧内容放在前面，新内容保留在末尾。
            if (buffer.isEmpty()) {
                buffer.addAll(tail)
            } else {
                val current = buffer.toList()
                buffer.clear()
                buffer.addAll((tail + current).takeLast(MAX_LINES))
            }
            _lines.value = buffer.toList()
        }
    }

    private fun append(line: String) {
        val file = logFile ?: return
        runCatching {
            file.parentFile?.mkdirs()
            if (file.exists() && file.length() > MAX_BYTES) {
                val old = File(file.parentFile, "$FILE_NAME.old")
                runCatching { old.delete() }
                runCatching { file.renameTo(old) }
            }
            file.appendText(line + "\n")
        }
    }

    /** 日志会随诊断导出，按现有诊断的隐私规则去掉 URL，并压成单行。 */
    private fun sanitize(message: String): String {
        val singleLine = message
            .replace(Regex("https?://\\S+"), "（地址已隐藏）")
            .replace('\n', ' ')
            .replace('\r', ' ')
            .trim()
        return if (singleLine.length > MAX_MESSAGE) singleLine.take(MAX_MESSAGE) + "…" else singleLine
    }
}
