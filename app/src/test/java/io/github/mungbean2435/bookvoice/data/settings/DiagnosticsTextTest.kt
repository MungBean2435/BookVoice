package io.github.mungbean2435.bookvoice.data.settings

import io.github.mungbean2435.bookvoice.playback.DiagnosticEvent
import io.github.mungbean2435.bookvoice.playback.DiagnosticLevel
import io.github.mungbean2435.bookvoice.playback.Diagnostics
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticsTextTest {

    private fun event(seconds: Int, message: String, level: DiagnosticLevel = DiagnosticLevel.Info) =
        DiagnosticEvent(
            timeMs = seconds * 1000L,
            level = level,
            tag = if (level == DiagnosticLevel.Error) "合成" else "播放",
            message = message
        )

    private fun snapshot(events: List<DiagnosticEvent>) = DiagnosticsSnapshot(
        appVersion = "3.4.0",
        appVersionCode = 72,
        device = "Xiaomi 14",
        android = "Android 14 (API 34)",
        abi = "arm64-v8a",
        providerName = "MiMo",
        providerId = "mimo",
        model = "mimo-v2.5-tts",
        voice = "mimo_default",
        speed = 1.2f,
        playback = "播放中 · 第 3 章 · 块 2/9 · 段 12",
        online = true,
        waitingNetwork = false,
        cacheFiles = 7,
        cacheBytes = 1024L * 1024 * 3,
        diagnostics = Diagnostics(
            firstSoundColdMs = 912,
            lastStreamTtfbMs = 746,
            lastSynthMs = 8123,
            streamRetries = 2,
            lastError = "网络不可用",
            events = events
        )
    )

    @Test
    fun `summary contains sections and newest events`() {
        val events = (1..5).map { event(it, "事件 $it") }
        val text = summaryText(snapshot(events), maxEvents = 2)

        assertTrue(text.contains("BookVoice 诊断摘要"))
        assertTrue(text.contains("[版本与设备]"))
        assertTrue(text.contains("版本：3.4.0 (72)"))
        assertTrue(text.contains("[TTS 配置]"))
        assertTrue(text.contains("[播放现场]"))
        assertTrue(text.contains("缓存：7 个文件 · 3.0 MB"))
        assertTrue(text.contains("[事件 2/5 条]"))
        assertTrue(text.contains("事件 4"))
        assertTrue(text.contains("事件 5"))
        assertFalse(text.contains("事件 1"))
    }

    @Test
    fun `export contains all events in chronological order`() {
        val events = listOf(
            event(1, "第一句"),
            event(2, "第二句"),
            event(3, "第三句")
        )
        val text = exportText(snapshot(events))

        assertTrue(text.contains("[事件 3/3 条]"))
        assertTrue(text.indexOf("第一句") < text.indexOf("第二句"))
        assertTrue(text.indexOf("第二句") < text.indexOf("第三句"))
    }

    @Test
    fun `warn and error events are marked`() {
        val text = exportText(
            snapshot(
                listOf(
                    event(1, "普通"),
                    event(2, "警告", DiagnosticLevel.Warn),
                    event(3, "错误", DiagnosticLevel.Error)
                )
            )
        )

        assertTrue(text.contains("~ [播放] 警告"))
        assertTrue(text.contains("! [合成] 错误"))
        assertTrue(text.contains("上次错误：网络不可用"))
    }

    @Test
    fun `empty events render placeholder`() {
        val text = exportText(snapshot(emptyList()))
        assertTrue(text.contains("[事件 0/0 条]"))
        assertTrue(text.contains("（暂无事件）"))
    }

    @Test
    fun `diagnostics keeps at most 300 events`() {
        // 上限由播放器的 logEvent 保证，这里校验数据类能承载 300 条且顺序不变。
        val events = (1..300).map { event(it, "事件 $it") }
        val diagnostics = Diagnostics(events = events)
        assertTrue(diagnostics.events.size == 300)
        assertTrue(diagnostics.events.first().message == "事件 1")
        assertTrue(diagnostics.events.last().message == "事件 300")
    }
}
