package io.github.mungbean2435.bookvoice.data.settings

import io.github.mungbean2435.bookvoice.playback.DiagnosticEvent
import io.github.mungbean2435.bookvoice.playback.DiagnosticLevel
import io.github.mungbean2435.bookvoice.playback.Diagnostics
import java.util.Locale

/**
 * 诊断页当前现场的快照。
 *
 * 只放展示需要的字段：不带 apiKey、baseUrl、书名和正文，避免复制/导出时泄露。
 */
data class DiagnosticsSnapshot(
    val appVersion: String,
    val appVersionCode: Long,
    val device: String,
    val android: String,
    val abi: String,
    val providerName: String,
    val providerId: String,
    val model: String,
    val voice: String,
    val speed: Float,
    val playback: String,
    val online: Boolean,
    val waitingNetwork: Boolean,
    val cacheFiles: Int,
    val cacheBytes: Long,
    val diagnostics: Diagnostics
)

/** 复制到剪贴板的摘要：最近 [maxEvents] 条事件。 */
fun summaryText(snapshot: DiagnosticsSnapshot, maxEvents: Int = 40): String {
    val events = snapshot.diagnostics.events.takeLast(maxEvents)
    return render(snapshot, events, title = "BookVoice 诊断摘要")
}

/** 导出文件：全部事件，按时间正序。 */
fun exportText(snapshot: DiagnosticsSnapshot): String =
    render(snapshot, snapshot.diagnostics.events, title = "BookVoice 诊断导出")

fun formatEvent(event: DiagnosticEvent): String {
    val mark = when (event.level) {
        DiagnosticLevel.Error -> "!"
        DiagnosticLevel.Warn -> "~"
        DiagnosticLevel.Info -> " "
    }
    val seconds = event.timeMs / 1000.0
    return String.format(
        Locale.US,
        "%6.1fs %s [%s] %s",
        seconds,
        mark,
        event.tag,
        event.message
    )
}

private fun render(
    snapshot: DiagnosticsSnapshot,
    events: List<DiagnosticEvent>,
    title: String
): String {
    val d = snapshot.diagnostics
    return buildString {
        appendLine(title)
        appendLine("（只包含本次运行的数据，不含 API Key、服务地址、书名和正文）")
        appendLine()
        appendLine("[版本与设备]")
        appendLine("版本：${snapshot.appVersion} (${snapshot.appVersionCode})")
        appendLine("设备：${snapshot.device}")
        appendLine("Android：${snapshot.android}")
        appendLine("ABI：${snapshot.abi}")
        appendLine()
        appendLine("[TTS 配置]")
        appendLine("供应商：${snapshot.providerName} (${snapshot.providerId})")
        appendLine("模型：${snapshot.model.ifBlank { "—" }}")
        appendLine("音色：${snapshot.voice.ifBlank { "—" }}")
        appendLine("语速：${String.format(Locale.US, "%.2f×", snapshot.speed)}")
        appendLine()
        appendLine("[播放现场]")
        appendLine("状态：${snapshot.playback}")
        val network = if (snapshot.online) "在线" else "离线"
        appendLine(
            "网络：$network" + if (snapshot.waitingNetwork) "（正在等待网络）" else ""
        )
        appendLine(
            "缓存：${snapshot.cacheFiles} 个文件 · ${formatBytes(snapshot.cacheBytes)}"
        )
        appendLine()
        appendLine("[指标]")
        appendLine("首声（冷/热）：${formatMs(d.firstSoundColdMs)} / ${formatMs(d.firstSoundWarmMs)}")
        appendLine("流式首包：${formatMs(d.lastStreamTtfbMs)}")
        appendLine("上次合成耗时：${formatMs(d.lastSynthMs)}")
        appendLine("流式回退：${d.streamFallbacks} 次")
        appendLine("流式重试：${d.streamRetries} 次")
        appendLine("首包超时：${d.firstPacketTimeouts} 次")
        appendLine("断流：${d.idleTimeouts} 次")
        appendLine("强制收尾：${d.stallForced} 次")
        appendLine("已开始块数：${d.chunksStarted}")
        appendLine("缓存命中：${d.cacheHits} 次 · 未命中 ${d.cacheMisses} 次")
        if (d.lastError.isNotBlank()) {
            appendLine("上次错误：${d.lastError.take(240)}")
        }
        appendLine()
        appendLine("[事件 ${events.size}/${d.events.size} 条]")
        if (events.isEmpty()) {
            appendLine("（暂无事件）")
        } else {
            events.forEach { appendLine(formatEvent(it)) }
        }
    }
}

private fun formatMs(value: Long): String = if (value > 0) "$value ms" else "—"

private fun formatBytes(bytes: Long): String = when {
    bytes <= 0 -> "0 B"
    bytes >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
    bytes >= 1024 -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
    else -> "$bytes B"
}
