package io.github.mungbean2435.bookvoice.playback

/** 诊断事件等级：界面按等级着色，「问题」筛选只显示 Warn/Error。 */
enum class DiagnosticLevel { Info, Warn, Error }

/**
 * 一条播放事件。
 *
 * [timeMs] 是应用启动后的毫秒数，导出时格式化成秒，避免把系统时间写进诊断。
 */
data class DiagnosticEvent(
    val timeMs: Long,
    val level: DiagnosticLevel,
    val tag: String,
    val message: String
)

data class CacheStats(val files: Int, val bytes: Long)

data class Diagnostics(
    val firstSoundColdMs: Long = 0,
    val firstSoundWarmMs: Long = 0,
    val cacheHits: Int = 0,
    val cacheMisses: Int = 0,
    val streamFallbacks: Int = 0,
    val lastStreamTtfbMs: Long = 0,
    /** 最近一次合成请求的墙钟耗时（含网络等待）。 */
    val lastSynthMs: Long = 0,
    val chunksStarted: Int = 0,
    val streamRetries: Int = 0,
    val firstPacketTimeouts: Int = 0,
    val idleTimeouts: Int = 0,
    val stallForced: Int = 0,
    val lastError: String = "",
    val events: List<DiagnosticEvent> = emptyList()
)
