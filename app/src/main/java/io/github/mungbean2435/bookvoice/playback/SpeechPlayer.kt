package io.github.mungbean2435.bookvoice.playback

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import io.github.mungbean2435.bookvoice.data.db.Block
import io.github.mungbean2435.bookvoice.data.db.Db
import io.github.mungbean2435.bookvoice.data.repo.Library
import io.github.mungbean2435.bookvoice.data.settings.Settings
import io.github.mungbean2435.bookvoice.data.settings.TtsConfig
import io.github.mungbean2435.bookvoice.tts.ChunkPlanner
import io.github.mungbean2435.bookvoice.tts.ChunkSpan
import io.github.mungbean2435.bookvoice.tts.PlannedChunk
import io.github.mungbean2435.bookvoice.tts.SynthRequest
import io.github.mungbean2435.bookvoice.tts.TtsException
import io.github.mungbean2435.bookvoice.tts.TtsProvider
import io.github.mungbean2435.bookvoice.tts.ProviderRegistry
import io.github.mungbean2435.bookvoice.tts.rules.RulePack
import io.github.mungbean2435.bookvoice.tts.rules.RulesEngine
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class BookProgress(
    val bookId: String,
    val chapterIdx: Int,
    val blockIdx: Int,
    val charsBefore: Long,
    val totalChars: Long
)

sealed interface PlayerState {
    data object Idle : PlayerState
    data class Buffering(val chapterIdx: Int, val blockIdx: Int) : PlayerState
    data class Playing(
        val chapterIdx: Int,
        val blockIdx: Int,
        val chunk: Int,
        val totalChunks: Int
    ) : PlayerState

    data class Paused(val chapterIdx: Int, val blockIdx: Int) : PlayerState
    data class Error(val message: String) : PlayerState
}

/** 当前朗读到的那一句（在原文里的字符区间）。 */
data class Highlight(
    val chapterIdx: Int,
    val blockIdx: Int,
    val start: Int,
    val end: Int
)

object Speech {
    @Volatile
    private var player: SpeechPlayer? = null

    fun init(context: Context) {
        if (player == null) player = SpeechPlayer(context.applicationContext)
    }

    fun get(): SpeechPlayer = player ?: error("Speech 未初始化")
}

/**
 * 1.6.0 播放器：
 * - 合成单位从「一段」改成「若干段合并成的一块（≤400 字）」，对话体不再一段一顿；
 * - 章末提前预取下一章，跨章不留空档；
 * - 支持从「某段某字」起播（点哪句从哪句）。
 */
class SpeechPlayer(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val cache = AudioCache(context)
    private val library = Library(context)
    private val synthMutex = Mutex()

    private val _state = MutableStateFlow<PlayerState>(PlayerState.Idle)
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private val _diagnostics = MutableStateFlow(Diagnostics())
    val diagnostics: StateFlow<Diagnostics> = _diagnostics.asStateFlow()

    private val startedAt = SystemClock.elapsedRealtime()

    /**
     * 内存里的滚动事件日志：诊断页直接显示，用户截图或导出即可。
     * tag 用于筛选，level 用于着色和「问题」筛选。
     */
    private fun logEvent(
        message: String,
        tag: String = "通用",
        level: DiagnosticLevel = DiagnosticLevel.Info
    ) {
        val timeMs = SystemClock.elapsedRealtime() - startedAt
        // 异常信息里可能带出接口地址；诊断会被复制/导出，这里先脱敏。
        val safe = message.replace(Regex("https?://\\S+"), "（地址已隐藏）")
        if (level == DiagnosticLevel.Error) {
            Log.e("BookVoice", "event: $safe")
        } else {
            Log.i("BookVoice", "event: $safe")
        }
        _diagnostics.update { d ->
            d.copy(
                events = (d.events + DiagnosticEvent(timeMs, level, tag, safe)).takeLast(300),
                lastError = if (level == DiagnosticLevel.Error) safe else d.lastError
            )
        }
    }

    /** 诊断页的「标记」：在事件流里插一条，方便截图时对时点。 */
    fun markDiagnostics() {
        logEvent("—— 用户标记 ——", "标记")
    }

    /** 清空本次运行的诊断数值；不影响播放、缓存、书签和进度。 */
    fun clearDiagnostics() {
        _diagnostics.value = Diagnostics()
    }

    /** 缓存统计可能读文件系统，调用方放到 IO 线程。 */
    fun cacheStats(): CacheStats = CacheStats(cache.fileCount(), cache.totalBytes())

    /** 供诊断摘要显示当前网络状态。 */
    fun isNetworkAvailable(): Boolean = hasNetwork()

    private val _highlight = MutableStateFlow<Highlight?>(null)
    val highlight: StateFlow<Highlight?> = _highlight.asStateFlow()

    private val _waitingNetwork = MutableStateFlow(false)
    val waitingNetwork: StateFlow<Boolean> = _waitingNetwork.asStateFlow()

    private val _progress = MutableStateFlow<BookProgress?>(null)
    val progress: StateFlow<BookProgress?> = _progress.asStateFlow()

    private val _playingBookId = MutableStateFlow("")
    /** 正在朗读的书 id（空闲时为空串）。mini 胶囊靠它取封面，不依赖 progress 的存续。 */
    val playingBookId: StateFlow<String> = _playingBookId.asStateFlow()

    private val _cacheProgress = MutableStateFlow(0 to 0)
    val cacheProgress: StateFlow<Pair<Int, Int>> = _cacheProgress.asStateFlow()

    private val _cacheRunning = MutableStateFlow(false)
    val cacheRunning: StateFlow<Boolean> = _cacheRunning.asStateFlow()

    /** 缓存完成后失败的分段数；下一次缓存开始时归零。 */
    private val _cacheFailed = MutableStateFlow(0)
    val cacheFailed: StateFlow<Int> = _cacheFailed.asStateFlow()

    /** 这份进度属于哪一章；换章后 UI 不再把旧进度当成「已缓存」。 */
    private val _cacheChapterIdx = MutableStateFlow(-1)
    val cacheChapterIdx: StateFlow<Int> = _cacheChapterIdx.asStateFlow()

    private var cacheJob: Job? = null
    private var cacheGeneration = 0
    private var totalChars: Long = 0L

    private val _sleepMinutes = MutableStateFlow(0)
    val sleepMinutes: StateFlow<Int> = _sleepMinutes.asStateFlow()

    @Volatile
    var bookTitle: String = ""
        private set

    @Volatile
    var chapterTitle: String = ""
        private set

    private val exo: ExoPlayer by lazy {
        // 流式播放时音频是「边合成边到达」的，起播门槛要低（500ms），
        // 否则 ExoPlayer 会等 2.5s 缓冲，把流式的优势抵消掉。
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(50_000, 120_000, 500, 1_000)
            .build()
        ExoPlayer.Builder(context).setLoadControl(loadControl).build().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .build(),
                true
            )
            setPlaybackParameters(PlaybackParameters(playbackSpeed()))
        }
    }

    private var playJob: Job? = null
    private var streamJob: Job? = null
    private var streamBuffer: PcmStreamBuffer? = null
    private var playbackListener: Player.Listener? = null
    private var prefetchJob: Job? = null
    private var nextChapterJob: Job? = null
    private var sleepJob: Job? = null

    private companion object {
        /** 首包迟迟不到时的等待上限；超时先重试流式，不直接退回整块。 */
        const val FIRST_PCM_TIMEOUT_MS = 10_000L

        /** 首包超时/流式失败后最多再试几次（每次都会重新发流式请求）。 */
        const val MAX_STREAM_RETRIES = 2

        /** 播到一半没有新 PCM 就认为断流，结束本块继续往下读。 */
        const val STREAM_IDLE_TIMEOUT_MS = 12_000L
    }

    private var chunks: List<PlannedChunk> = emptyList()
    private var blockTexts: Map<Int, String> = emptyMap()
    private var currentIndex = -1
    private var prefetchCursor = 0
    private var nextChapterPrefetched = false
    private var lastSavedBlock = -1

    private var currentBookId: String = ""
    private var currentChapterIdx: Int = -1
    private var currentBlockIdx: Int = -1
    private var paused = false
    private var playStartedAt = 0L
    private var firstSoundAt = 0L
    private var currentChunkCached = false
    private var activeRules: List<RulePack> = listOf(RulesEngine.builtin)

    /**
     * 流式块的时长（毫秒）。流式 WAV 头里 data size 是未知值，exo.duration 会算出
     * 几十小时，所以进度换算用这个；普通文件块为 0，回退到 exo.duration。
     */
    @Volatile
    private var streamDurationMs: Long = 0L

    /** [charOffset] 是 [blockIdx] 段内的起始字符（点哪句从哪句起播）。 */
    fun playFrom(bookId: String, chapterIdx: Int, blockIdx: Int, charOffset: Int = 0) {
        stopInternal(stopService = false)
        runCatching { exo.setPlaybackParameters(PlaybackParameters(playbackSpeed())) }
        playStartedAt = SystemClock.elapsedRealtime()
        firstSoundAt = 0L
        logEvent("起播：第 ${chapterIdx + 1} 章 / 段 ${blockIdx}", "播放")
        playJob = scope.launch { runQueue(bookId, chapterIdx, blockIdx, charOffset) }
    }

    /**
     * 设置页试听：不绑定书、不写进度。
     * [config] 用于「还没保存的草稿配置」试听；为空时用当前启用配置。
     */
    fun speakPreview(text: String, config: TtsConfig? = null) {
        stopInternal(stopService = false)
        val previewConfig = config ?: Settings.state.value.activeConfigOrDefault()
        playJob = scope.launch {
            try {
                val previewProvider = ProviderRegistry.active(previewConfig.providerId)
                exo.setPlaybackParameters(
                    PlaybackParameters(
                        if (previewProvider.id == "minimax") 1f else Settings.state.value.speed
                    )
                )
                val planned = ChunkPlanner.plan(
                    listOf(0 to text),
                    normalize = { RulesEngine.normalize(it, activeRules) }
                )
                if (planned.isEmpty()) return@launch
                PlaybackService.start(context)
                blockTexts = mapOf(0 to text)
                for ((index, chunk) in planned.withIndex()) {
                    _state.value = PlayerState.Buffering(0, 0)
                    val file = ensureFile(
                        text = chunk.text,
                        bookId = "",
                        chapterIdx = -1,
                        spans = emptyList(),
                        waitMs = 20_000L,
                        configOverride = previewConfig
                    )
                    _state.value = PlayerState.Playing(0, 0, index, planned.size)
                    playAndWait(file)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: TtsException) {
                _state.value = PlayerState.Error(e.message ?: "合成失败")
            } catch (e: Exception) {
                _state.value = PlayerState.Error(e.message ?: "播放失败")
            } finally {
                if (_state.value !is PlayerState.Error) _state.value = PlayerState.Idle
                runCatching { exo.setPlaybackParameters(PlaybackParameters(playbackSpeed())) }
                PlaybackService.stop(context)
            }
        }
    }

    fun togglePause() {
        when (val s = _state.value) {
            is PlayerState.Playing -> {
                paused = true
                runCatching { exo.pause() }
                _state.value = PlayerState.Paused(s.chapterIdx, s.blockIdx)
            }

            is PlayerState.Paused -> {
                paused = false
                runCatching { exo.play() }
                _state.value = PlayerState.Playing(s.chapterIdx, s.blockIdx, currentIndex, chunks.size)
            }

            is PlayerState.Buffering -> {
                paused = !paused
                if (!paused) {
                    runCatching { exo.play() }
                    _state.value = PlayerState.Playing(s.chapterIdx, s.blockIdx, currentIndex, chunks.size)
                }
            }

            else -> Unit
        }
    }

    fun stop() {
        stopInternal(stopService = true)
    }

    fun clearError() {
        if (_state.value is PlayerState.Error) _state.value = PlayerState.Idle
    }

    fun applySpeed(speed: Float) {
        runCatching { exo.setPlaybackParameters(PlaybackParameters(playbackSpeed())) }
    }

    /** MiniMax 的语速由服务端处理，播放端不能再变一次，否则双重变速。 */
    private fun playbackSpeed(): Float =
        if (Settings.state.value.activeConfigOrDefault().providerId == "minimax") {
            1f
        } else {
            Settings.state.value.speed
        }

    /** [minutes] <= 0 表示取消。到点自动 stop。 */
    fun startSleepTimer(minutes: Int) {
        sleepJob?.cancel()
        sleepJob = null
        if (minutes <= 0) {
            _sleepMinutes.value = 0
            return
        }
        sleepJob = scope.launch {
            var remain = minutes
            _sleepMinutes.value = remain
            while (remain > 0) {
                delay(60_000)
                remain -= 1
                _sleepMinutes.value = remain
            }
            stop()
        }
    }

    private suspend fun publishProgress(bookId: String, chapter: Int, block: Int) {
        val before = withContext(Dispatchers.IO) { Db.get().charsBefore(bookId, chapter, block) }
        _progress.value = BookProgress(bookId, chapter, block, before, totalChars)
    }

    /** 进度条拖动：比例 → 全书字数 → 章节 → 段，然后从那里开始播。 */
    fun seekToFraction(bookId: String, fraction: Float, total: Long) {
        if (bookId.isBlank() || total <= 0) return
        scope.launch {
            val target = (fraction.coerceIn(0f, 1f) * total).toLong()
            val ranges = withContext(Dispatchers.IO) { Db.get().chapterCharRanges(bookId) }
            if (ranges.isEmpty()) return@launch
            val range = ranges.lastOrNull { it.startChars <= target } ?: ranges.first()
            val inChapter = (target - range.startChars).coerceAtLeast(0)
            val block = withContext(Dispatchers.IO) { Db.get().blockAtChar(bookId, range.chapterIdx, inChapter) }
            playFrom(bookId, range.chapterIdx, block, 0)
        }
    }

    /** 把整章合成进缓存（已有命中会跳过），供离线收听。 */
    fun cacheChapter(bookId: String, chapterIdx: Int) {
        if (cacheJob?.isActive == true) return
        val generation = ++cacheGeneration
        _cacheChapterIdx.value = chapterIdx
        _cacheFailed.value = 0
        _cacheProgress.value = 0 to 0
        _cacheRunning.value = true
        val job = scope.launch {
            try {
                val blocks = withContext(Dispatchers.IO) { library.blocks(bookId, chapterIdx) }
                if (blocks.isEmpty()) return@launch
                val settings = Settings.state.value
                val rules = withContext(Dispatchers.IO) {
                    if (settings.builtinRulesEnabled) {
                        listOf(RulesEngine.builtin) + library.rulePacks()
                    } else {
                        library.rulePacks()
                    }
                }
                val planned = ChunkPlanner.plan(
                    blocks = blocks.map { it.idx to it.text },
                    normalize = { RulesEngine.normalize(it, rules) }
                )
                _cacheProgress.value = 0 to planned.size
                var done = 0
                var failed = 0
                for (chunk in planned) {
                    // runCatching 会把取消也吞掉，所以先显式检查，再只捕获真实失败。
                    currentCoroutineContext().ensureActive()
                    try {
                        ensureFile(chunk.text, bookId, chapterIdx, chunk.spans, rules)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        failed += 1
                        _cacheFailed.value = failed
                    }
                    done += 1
                    _cacheProgress.value = done to planned.size
                }
            } finally {
                if (cacheGeneration == generation) _cacheRunning.value = false
            }
        }
        cacheJob = job
        job.invokeOnCompletion {
            if (cacheGeneration == generation) _cacheRunning.value = false
        }
    }

    /** 停止整章缓存；已经写入的块会保留，下次缓存直接命中。 */
    fun cancelCache() {
        cacheGeneration += 1
        cacheJob?.cancel()
        cacheJob = null
        _cacheRunning.value = false
        _cacheChapterIdx.value = -1
        _cacheFailed.value = 0
        _cacheProgress.value = 0 to 0
    }

    fun skipToBlock(blockIdx: Int) {
        val bookId = currentBookId
        val chapter = currentChapterIdx
        if (bookId.isBlank() || chapter < 0) return
        playFrom(bookId, chapter, blockIdx.coerceAtLeast(0), 0)
    }

    fun nextBlock() = skipToBlock(currentBlockIdx + 1)

    fun previousBlock() = skipToBlock((currentBlockIdx - 1).coerceAtLeast(0))

    // ---------------- 队列 ----------------

    private suspend fun runQueue(
        bookId: String,
        startChapter: Int,
        startBlock: Int,
        startOffset: Int
    ) {
        val book = withContext(Dispatchers.IO) { library.book(bookId) }
        val chapters = withContext(Dispatchers.IO) { library.chapters(bookId) }
        bookTitle = book?.title.orEmpty()
        _playingBookId.value = bookId
        totalChars = book?.charCount?.toLong() ?: 0L
        activeRules = withContext(Dispatchers.IO) {
            val packs = library.rulePacks()
            if (Settings.state.value.builtinRulesEnabled) {
                listOf(RulesEngine.builtin) + packs
            } else {
                packs
            }
        }

        var chapter = startChapter.coerceAtLeast(0)
        var fromBlock = startBlock
        var fromOffset = startOffset

        try {
            while (chapter < chapters.size) {
                val blocks = withContext(Dispatchers.IO) { library.blocks(bookId, chapter) }
                if (blocks.isEmpty()) {
                    // 只有插图/空章不能把整本书的队列终止掉，直接跳到下一章。
                    chapter += 1
                    fromBlock = 0
                    fromOffset = 0
                    continue
                }

                chapterTitle = chapters.getOrNull(chapter)?.title.orEmpty()
                currentBookId = bookId
                currentChapterIdx = chapter
                blockTexts = blocks.associate { it.idx to it.text }
                chunks = buildChunks(blocks, fromBlock, fromOffset)
                if (chunks.isEmpty()) {
                    chapter += 1
                    fromBlock = 0
                    fromOffset = 0
                    continue
                }

                currentIndex = 0
                prefetchCursor = 0
                lastSavedBlock = -1
                nextChapterPrefetched = false
                paused = false
                PlaybackService.start(context)
                // 流式供应商每块都边收边播，不再预取：预取会抢合成锁，
                // 网络抖动时曾把播放队列堵住最长 10 分钟（表现就是“读几句后不读了”）。
                val streamPlayback = streamingProvider() != null
                if (!streamPlayback) startPrefetch()

                for (index in chunks.indices) {
                    currentIndex = index
                    val chunk = chunks[index]
                    logEvent(
                        "开始第 ${index + 1}/${chunks.size} 块，${chunk.text.length} 字，第 ${chapter + 1} 章",
                        "队列"
                    )
                    _diagnostics.update { it.copy(chunksStarted = it.chunksStarted + 1) }
                    val firstBlock = chunk.spans.firstOrNull()?.blockIdx ?: fromBlock
                    currentBlockIdx = firstBlock
                    _state.value = PlayerState.Buffering(chapter, firstBlock)
                    withContext(Dispatchers.IO) {
                        library.saveProgress(bookId, chapter, firstBlock, 0)
                    }
                    publishProgress(bookId, chapter, firstBlock)

                    if (streamPlayback && canStreamChunk(chunk)) {
                        val streamed = playChunkStreamed(chunk, chapter, firstBlock, index)
                        if (streamed) {
                            val gap = Settings.state.value.gapParagraphMs
                            if (gap > 0) delay(gap.toLong())
                            continue
                        }
                        // 检查与真正开始之间恰好命中了缓存：走下面的文件路径。
                    }

                    val file = try {
                        ensureFile(chunk.text, bookId, chapter, chunk.spans)
                    } catch (e: TtsException.Auth) {
                        throw e
                    } catch (e: TtsException.Quota) {
                        // 余额/额度耗尽不是单块失败：继续上层 while 只会把整本书
                        // 快速跳完并不断写「待补」，必须让播放停在错误态。
                        throw e
                    } catch (e: Exception) {
                        withContext(Dispatchers.IO) {
                            chunk.spans.forEach { span ->
                                val text = blockTexts[span.blockIdx].orEmpty()
                                if (text.isNotEmpty()) {
                                    Db.get().recordFailure(bookId, chapter, span.blockIdx, text)
                                }
                            }
                        }
                        continue
                    }

                    if (!paused) {
                        _state.value = PlayerState.Playing(chapter, firstBlock, index, chunks.size)
                    }

                    if (!streamPlayback) {
                        maybePrefetchNextChapter(bookId, chapter, chapters.size, index)
                    } else {
                        // 当前块命中缓存时走文件路径，这里也要把下一块提前合成好，
                        // 否则会出现「缓存块 → 流式块 → 缓存块」交替，每隔一块仍有首包空档。
                        prefetchNextChunk(index)
                    }

                    val ticker = scope.launch { tickPlayback(chapter, chunk) }
                    try {
                        playAndWait(file)
                    } finally {
                        ticker.cancel()
                    }

                    val gap = Settings.state.value.gapParagraphMs
                    if (gap > 0) delay(gap.toLong())
                }

                chapter += 1
                fromBlock = 0
                fromOffset = 0
            }
            _state.value = PlayerState.Idle
        } catch (e: CancellationException) {
            throw e
        } catch (e: TtsException) {
            _state.value = PlayerState.Error(e.message ?: "合成失败")
        } catch (e: Exception) {
            _state.value = PlayerState.Error(e.message ?: "播放失败")
        } finally {
            prefetchJob?.cancel()
            nextChapterJob?.cancel()
            val current = _state.value
            if (current is PlayerState.Idle || current is PlayerState.Error) {
                PlaybackService.stop(context)
            }
        }
    }

    private fun buildChunks(blocks: List<Block>, fromBlock: Int, fromOffset: Int): List<PlannedChunk> =
        ChunkPlanner.plan(
            blocks = blocks.map { it.idx to it.text },
            fromBlockIdx = fromBlock,
            fromOffset = fromOffset.coerceAtLeast(0),
            normalize = { RulesEngine.normalize(it, activeRules) }
        )

    /** 一章只剩两块时，提前把下一章头两块合成好，跨章不留空档。 */
    private fun maybePrefetchNextChapter(
        bookId: String,
        chapter: Int,
        chapterCount: Int,
        index: Int
    ) {
        if (nextChapterPrefetched) return
        if (index < chunks.size - 2) return
        nextChapterPrefetched = true
        val next = chapter + 1
        if (next >= chapterCount) return

        nextChapterJob = scope.launch {
            val blocks = withContext(Dispatchers.IO) { library.blocks(bookId, next) }
            if (blocks.isEmpty()) return@launch
            buildChunks(blocks, 0, 0).take(2).forEach { chunk ->
                runCatching { ensureFile(chunk.text, "", -1, emptyList(), waitMs = 30_000L) }
            }
        }
    }

    private fun startPrefetch() {
        prefetchJob?.cancel()
        prefetchJob = scope.launch {
            while (isActive) {
                val target = minOf(currentIndex + 3, chunks.size)
                if (prefetchCursor < target) {
                    val text = chunks[prefetchCursor].text
                    // 预取只等 30s；失败就停，不反复撞限流，也不长期占着合成锁。
                    val ok = runCatching {
                        ensureFile(text, "", -1, emptyList(), waitMs = 30_000L)
                    }.isSuccess
                    if (!ok) break
                    prefetchCursor++
                } else {
                    delay(300)
                }
            }
        }
    }

    /**
     * 合成一块。网络类错误最多等 10 分钟（退避 2/4/8/16/15s），
     * 期间 waitingNetwork 为 true；Key 错、超长这类错误不等待。
     */
    private suspend fun ensureFile(
        text: String,
        bookId: String,
        chapterIdx: Int,
        spans: List<ChunkSpan>,
        rules: List<RulePack> = activeRules,
        waitMs: Long = 10 * 60_000L,
        configOverride: TtsConfig? = null,
        countStats: Boolean = true
    ): File {
        val settings = Settings.state.value
        val config = configOverride ?: settings.activeConfigOrDefault()
        val currentProvider = ProviderRegistry.active(config.providerId)
        val ext = currentProvider.audioFormat
        val key = cache.key(
            currentProvider.id,
            config.model,
            config.voice,
            ext,
            RulesEngine.signature(rules),
            text
        )
        cache.existing(key, ext)?.let {
            if (countStats) _diagnostics.update { d -> d.copy(cacheHits = d.cacheHits + 1) }
            currentChunkCached = true
            return it
        }
        currentChunkCached = false
        if (countStats) _diagnostics.update { d -> d.copy(cacheMisses = d.cacheMisses + 1) }

        val deadline = System.currentTimeMillis() + waitMs
        var attempt = 0

        // 每次拿锁只发一次合成请求；退避等待在锁外，避免一次网络抖动
        // 让预取协程抱着 synthMutex 睡几分钟，把播放队列整个堵死。
        while (true) {
            var stored: File? = null
            var failure: TtsException? = null
            val file = try {
                synthMutex.withLock {
                    cache.existing(key, ext) ?: run {
                        try {
                            val synthStartedAt = SystemClock.elapsedRealtime()
                            val result = currentProvider.synthesize(
                                SynthRequest(
                                    text = text,
                                    apiKey = config.apiKey,
                                    baseUrl = config.baseUrl.ifBlank {
                                        currentProvider.defaultConfig.baseUrl
                                    },
                                    model = config.model.ifBlank {
                                        currentProvider.defaultConfig.model
                                    },
                                    voiceId = config.voice.ifBlank {
                                        currentProvider.defaultConfig.voice
                                    },
                                    format = ext,
                                    speed = settings.speed.toDouble()
                                )
                            )
                            _diagnostics.update {
                                it.copy(
                                    lastSynthMs = SystemClock.elapsedRealtime() - synthStartedAt
                                )
                            }
                            if (result.truncated) {
                                Log.w(
                                    "BookVoice",
                                    "chunk 疑似被截断：${text.length} 字，${result.durationMs} ms"
                                )
                            }
                            stored = cache.store(key, result.audio, ext)
                            stored
                        } catch (e: TtsException) {
                            failure = e
                            null
                        }
                    }
                }
            } catch (e: Exception) {
                _waitingNetwork.value = false
                throw e
            }

            if (file != null) {
                currentChunkCached = true
                _waitingNetwork.value = false
                cache.trim(Settings.state.value.cacheLimitMb.toLong() * 1024 * 1024)
                if (bookId.isNotEmpty()) {
                    withContext(Dispatchers.IO) {
                        spans.forEach { span ->
                            Db.get().clearFailure(bookId, chapterIdx, span.blockIdx)
                        }
                    }
                }
                return file
            }

            val e = failure ?: error("合成没有返回音频：${text.length} 字")
            // 只有网络抖动 / 限流值得重试；配置错误（音色 ID、端点、404）必须立刻失败
            if (e !is TtsException.Network && e !is TtsException.RateLimited) {
                _waitingNetwork.value = false
                logEvent("合成失败：${e.message?.take(60)}", "合成", DiagnosticLevel.Error)
                throw e
            }
            attempt += 1
            if (System.currentTimeMillis() > deadline) {
                _waitingNetwork.value = false
                logEvent("合成等待超时：${e.message?.take(60)}", "网络", DiagnosticLevel.Error)
                throw e
            }
            _waitingNetwork.value = true
            val backoff = 1000L * (1 shl attempt.coerceAtMost(4))
            delay(backoff.coerceAtMost(15_000L))
        }
    }

    /** 按播放进度估算句子位置，并顺手把阅读进度写成「第几段」。 */
    /**
     * 段内连续高亮：从段落开头填充到当前读到位置，**不按标点切句**。
     * 供应商不给时间戳，所以位置仍是按播放进度估算的。
     */
    private suspend fun tickPlayback(chapterIdx: Int, chunk: PlannedChunk) {
        while (true) {
            val duration = if (streamDurationMs > 0) streamDurationMs else exo.duration
            if (duration > 0 && chunk.origin.isNotEmpty()) {
                val position = exo.currentPosition.coerceIn(0, duration)
                val charIndex = ((position.toFloat() / duration) * chunk.origin.length)
                    .toInt()
                    .coerceIn(0, chunk.origin.length - 1)

                ChunkPlanner.locate(chunk, charIndex)?.let { (blockIdx, offset) ->
                    _highlight.value = Highlight(
                        chapterIdx = chapterIdx,
                        blockIdx = blockIdx,
                        start = 0,
                        end = offset + 1
                    )
                    if (blockIdx != lastSavedBlock) {
                        lastSavedBlock = blockIdx
                        currentBlockIdx = blockIdx
                        withContext(Dispatchers.IO) {
                            library.saveProgress(currentBookId, chapterIdx, blockIdx, 0)
                        }
                        publishProgress(currentBookId, chapterIdx, blockIdx)
                    }
                }
            }
            delay(200)
        }
    }

    /** 当前供应商是否支持边收边播；支持时整章都不再预取。 */
    private fun streamingProvider(): TtsProvider? {
        val config = Settings.state.value.activeConfigOrDefault()
        val provider = ProviderRegistry.active(config.providerId)
        return if (provider.supportsStreaming) provider else null
    }

    /** 这一块能否走流式：供应商支持且缓存里还没有。 */
    private fun canStreamChunk(chunk: PlannedChunk): Boolean {
        val provider = streamingProvider() ?: return false
        val config = Settings.state.value.activeConfigOrDefault()
        val ext = provider.audioFormat
        val key = cache.key(
            provider.id, config.model, config.voice, ext,
            RulesEngine.signature(activeRules), chunk.text
        )
        return cache.existing(key, ext) == null
    }

    /** 当前是否有可用网络（Wi-Fi/流量且系统验证过连通）。 */
    private fun hasNetwork(): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return true
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    /**
     * 等网络恢复，期间界面显示「网络中断」。
     * 返回 false 表示超时；播放被取消时协程会直接取消，不会卡在这里。
     */
    private suspend fun awaitNetwork(timeoutMs: Long = 10 * 60_000L): Boolean {
        _waitingNetwork.value = true
        try {
            val deadline = SystemClock.elapsedRealtime() + timeoutMs
            while (SystemClock.elapsedRealtime() < deadline) {
                if (hasNetwork()) return true
                delay(1_000L)
            }
            return hasNetwork()
        } finally {
            _waitingNetwork.value = false
        }
    }

    /**
     * 后台把下一块整块合成进缓存，让下一块可以走「文件直接播放」，
     * 避免在块边界再等 0.6–0.9s 的首包。只对支持流式的供应商生效。
     */
    private fun prefetchNextChunk(chunkIndex: Int) {
        val next = chunks.getOrNull(chunkIndex + 1) ?: return
        val settings = Settings.state.value
        val config = settings.activeConfigOrDefault()
        val provider = ProviderRegistry.active(config.providerId)
        if (!provider.supportsStreaming) return
        val ext = provider.audioFormat
        val rules = activeRules
        val key = cache.key(
            provider.id, config.model, config.voice, ext,
            RulesEngine.signature(rules), next.text
        )
        if (cache.existing(key, ext) != null) return
        nextChapterJob?.cancel()
        val request = SynthRequest(
            text = next.text,
            apiKey = config.apiKey,
            baseUrl = config.baseUrl.ifBlank { provider.defaultConfig.baseUrl },
            model = config.model.ifBlank { provider.defaultConfig.model },
            voiceId = config.voice.ifBlank { provider.defaultConfig.voice },
            format = "pcm16",
            speed = settings.speed.toDouble()
        )
        nextChapterJob = scope.launch(Dispatchers.IO) {
            runCatching {
                synthMutex.withLock {
                    if (cache.existing(key, ext) != null) return@withLock
                    val out = ByteArrayOutputStream()
                    val result = provider.streamPcm(request) { out.write(it) }
                    val pcm = out.toByteArray()
                    if (pcm.isNotEmpty()) {
                        cache.store(key, PcmWav.fileHeader(pcm.size.toLong()) + pcm, ext)
                        cache.trim(Settings.state.value.cacheLimitMb.toLong() * 1024 * 1024)
                        logEvent("预合成下一块 ${next.text.length} 字完成 ${result.durationMs}ms", "缓存")
                    }
                }
            }.onFailure {
                if (it !is CancellationException) {
                    logEvent("预合成下一块失败：${it.message?.take(30)}", "缓存", DiagnosticLevel.Warn)
                }
            }
        }
    }

    /**
     * 边合成边播一块：PCM 首包约 1s 到达，不必等整块 wav（400 字约 20s）。
     * 返回 false 表示这次不适用（供应商不支持/恰好已缓存），调用方走整块路径；
     * 返回 true 表示这一块已经处理完（成功，或失败后已回退/跳过）。
     */
    private suspend fun playChunkStreamed(
        chunk: PlannedChunk,
        chapterIdx: Int,
        firstBlock: Int,
        chunkIndex: Int,
        attempt: Int = 0
    ): Boolean {
        val settings = Settings.state.value
        val config = settings.activeConfigOrDefault()
        val provider = ProviderRegistry.active(config.providerId)
        if (!provider.supportsStreaming) return false

        val ext = provider.audioFormat
        val key = cache.key(
            provider.id, config.model, config.voice, ext,
            RulesEngine.signature(activeRules), chunk.text
        )
        if (cache.existing(key, ext) != null) return false

        // 如果上一块启动的「预合成下一块」还没结束，先取消它：它可能正占着
        // synthMutex，而本块要亲自流式合成。streamPcm 会在 200ms 内断开连接。
        if (nextChapterJob?.isActive == true) {
            nextChapterJob?.cancel()
            nextChapterJob = null
        }

        if (attempt == 0) {
            _diagnostics.update { it.copy(cacheMisses = it.cacheMisses + 1) }
        } else {
            _diagnostics.update { it.copy(streamRetries = it.streamRetries + 1) }
        }
        logEvent("流式开始（第 ${attempt + 1} 次尝试），${chunk.text.length} 字", "流式")
        currentChunkCached = false

        val buffer = PcmStreamBuffer()
        streamBuffer = buffer
        // 头先写未知长度：播放器读到流结束（read 返回 -1）就会正常 ENDED。
        buffer.append(PcmWav.liveHeader())
        val collected = ByteArrayOutputStream()
        val streamError = AtomicReference<Throwable?>(null)
        val timeoutKind = AtomicReference<String?>(null)
        val streamStartedAt = SystemClock.elapsedRealtime()
        val firstPcmAt = java.util.concurrent.atomic.AtomicLong(0L)
        val lastPcmAt = java.util.concurrent.atomic.AtomicLong(0L)

        // 合成完成前进度按字数估算；完成后换成真实时长。
        streamDurationMs = chunk.text.length * 196L

        val source = ProgressiveMediaSource.Factory(
            DataSource.Factory { PcmStreamDataSource(buffer) }
        ).createMediaSource(
            MediaItem.Builder()
                .setUri(Uri.parse("bookvoice-pcm://${key.take(12)}"))
                .setMimeType(MimeTypes.AUDIO_WAV)
                .build()
        )

        val request = SynthRequest(
            text = chunk.text,
            apiKey = config.apiKey,
            baseUrl = config.baseUrl.ifBlank { provider.defaultConfig.baseUrl },
            model = config.model.ifBlank { provider.defaultConfig.model },
            voiceId = config.voice.ifBlank { provider.defaultConfig.voice },
            format = "pcm16",
            speed = settings.speed.toDouble()
        )

        val job = scope.launch(Dispatchers.IO) {
            try {
                synthMutex.withLock {
                    val result = provider.streamPcm(request) { pcm ->
                        val now = SystemClock.elapsedRealtime()
                        if (firstPcmAt.compareAndSet(0L, now)) {
                            val ttfb = now - streamStartedAt
                            _diagnostics.update { it.copy(lastStreamTtfbMs = ttfb) }
                            logEvent("收到首个音频包 ${ttfb}ms", "流式")
                            Log.i("BookVoice", "流式首包 ${ttfb}ms，${chunk.text.length} 字")
                        }
                        lastPcmAt.set(now)
                        collected.write(pcm)
                        buffer.append(pcm)
                    }
                    if (result.truncated) {
                        Log.w(
                            "BookVoice",
                            "流式块疑似被截断：${chunk.text.length} 字，${result.durationMs} ms"
                        )
                    }
                    val pcm = collected.toByteArray()
                    cache.store(key, PcmWav.fileHeader(pcm.size.toLong()) + pcm, ext)
                    cache.trim(Settings.state.value.cacheLimitMb.toLong() * 1024 * 1024)
                    streamDurationMs = result.durationMs
                    _diagnostics.update {
                        it.copy(lastSynthMs = SystemClock.elapsedRealtime() - streamStartedAt)
                    }
                    logEvent("合成完成 ${result.durationMs}ms，${pcm.size} 字节", "合成")
                    Log.i(
                        "BookVoice",
                        "流式块完成 ${result.durationMs}ms，${pcm.size} 字节，${chunk.text.length} 字"
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // watchdog 主动取消时不算供应商错误，避免二次回退。
                if (timeoutKind.get() == null) streamError.set(e)
                logEvent("合成失败：${e.message?.take(40)}", "错误", DiagnosticLevel.Error)
                Log.w("BookVoice", "流式合成失败：${e.message}")
            } finally {
                buffer.finish()
            }
        }
        streamJob = job
        // 当前块开始后提前合成下一块：下一块命中缓存就不再等首包，
        // 块间只剩一次文件切换。取消时走 streamPcm 的 forceClose，不会抱着锁。
        prefetchNextChunk(chunkIndex)

        // 首包 10s 不到 / 中途 12s 没有新数据，就把这一块判超时，别让播放器干等 readTimeout。
        val watchdog = scope.launch {
            while (isActive) {
                delay(500)
                // 合成正常结束时不要再按「12 秒无新数据」判超时：音频早已收完，
                // 播放器可能还要播几十秒，误判会把整块标成「待补」。
                if (job.isCompleted) break
                val now = SystemClock.elapsedRealtime()
                val first = firstPcmAt.get()
                if (first == 0L) {
                    if (now - streamStartedAt > FIRST_PCM_TIMEOUT_MS) {
                        if (timeoutKind.compareAndSet(null, "first")) {
                            logEvent("首包 10s 未到，取消", "网络", DiagnosticLevel.Warn)
                            _diagnostics.update {
                                it.copy(firstPacketTimeouts = it.firstPacketTimeouts + 1)
                            }
                            Log.w("BookVoice", "流式首包 ${FIRST_PCM_TIMEOUT_MS}ms 未到，回退整块")
                            buffer.cancel()
                            job.cancel()
                        }
                        break
                    }
                } else if (now - lastPcmAt.get() > STREAM_IDLE_TIMEOUT_MS) {
                    if (timeoutKind.compareAndSet(null, "idle")) {
                        logEvent("12s 无新音频，结束本块", "网络", DiagnosticLevel.Warn)
                        _diagnostics.update {
                            it.copy(idleTimeouts = it.idleTimeouts + 1)
                        }
                        Log.w("BookVoice", "流式 ${STREAM_IDLE_TIMEOUT_MS}ms 无新数据，结束本块")
                        buffer.finish()
                        job.cancel()
                    }
                    break
                }
            }
        }

        var playbackFailure: Throwable? = null
        if (!paused) {
            _state.value = PlayerState.Playing(chapterIdx, firstBlock, chunkIndex, chunks.size)
        }
        // 兜底：合成已经结束、位置却不再前进，说明播放器没有收到 ENDED。
        // 正常播完最多等 3 秒就强制收尾，不能让队列停在这里。
        var playbackCont: CancellableContinuation<Unit>? = null
        val stallWatch = scope.launch {
            var lastPos = -1L
            var lastMove = SystemClock.elapsedRealtime()
            var stallLogged = false
            // 只累计真正处于播放态的时间；暂停不算，避免暂停久了被误判超时。
            var playedMs = 0L
            var lastTick = lastMove
            try {
                while (isActive) {
                    delay(500L)
                    val now = SystemClock.elapsedRealtime()
                    val dt = now - lastTick
                    lastTick = now
                    val pos = exo.currentPosition
                    val state = exo.playbackState
                    val pwr = exo.playWhenReady
                    val playing = !paused && pwr
                    if (playing) playedMs += dt

                    val finished = job.isCompleted || buffer.isFinished
                    val expected = streamDurationMs.takeIf { it > 0L }
                        ?: (chunk.text.length * 196L)
                    val speed = Settings.state.value.speed.coerceAtLeast(0.5f)

                    // 绝对兜底放最前面：位置一直被播放器推着走时也要命中。
                    if (finished && playedMs > (expected / speed).toLong() + 500L) {
                        logEvent(
                            "播放超过 ${expected}ms，超时收尾（位置=$pos，状态=$state）",
                            "播放器",
                            DiagnosticLevel.Warn
                        )
                        _diagnostics.update { it.copy(stallForced = it.stallForced + 1) }
                        playbackListener?.let { runCatching { exo.removeListener(it) } }
                        playbackListener = null
                        runCatching { exo.stop() }
                        playbackCont?.takeIf { it.isActive }?.resume(Unit)
                        break
                    }
                    if (pos != lastPos) {
                        lastPos = pos
                        lastMove = now
                        stallLogged = false
                        continue
                    }
                    if (!playing) {
                        lastMove = now
                        continue
                    }
                    val waited = now - lastMove
                    if (waited > 3_000L && !stallLogged) {
                        stallLogged = true
                        logEvent(
                            "停滞 ${waited}ms：位置=$pos 状态=$state 播放=$pwr " +
                                "合成结束=${job.isCompleted} 缓冲收尾=${buffer.isFinished}",
                            "播放器",
                            DiagnosticLevel.Warn
                        )
                    }
                    if (finished && waited > 3_000L && state != Player.STATE_ENDED) {
                        logEvent("播放停在 ${pos}ms，强制收尾", "播放器", DiagnosticLevel.Warn)
                        _diagnostics.update { it.copy(stallForced = it.stallForced + 1) }
                        Log.w("BookVoice", "流式播放停在 ${pos}ms，合成已结束，强制收尾")
                        playbackListener?.let { runCatching { exo.removeListener(it) } }
                        playbackListener = null
                        runCatching { exo.stop() }
                        playbackCont?.takeIf { it.isActive }?.resume(Unit)
                        break
                    }
                }
            } catch (e: CancellationException) {
                // 正常取消（切块、停止），不是异常。
            } catch (t: Throwable) {
                logEvent("看门狗异常：${t.message?.take(60)}", "看门狗", DiagnosticLevel.Warn)
                Log.w("BookVoice", "看门狗异常", t)
            }
        }
        try {
            val ticker = scope.launch { tickPlayback(chapterIdx, chunk) }
            try {
                // 取消时要先让阻塞中的 read 返回，否则 exo.stop() 会等加载线程。
                playMediaSourceAndWait(
                    source,
                    onCancel = { buffer.cancel() },
                    onReady = { playbackCont = it }
                )
            } finally {
                ticker.cancel()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            playbackFailure = e
        } finally {
            stallWatch.cancel()
            watchdog.cancel()
            if (job.isActive) job.cancel()
        }
        // 协程可能还阻塞在 readLine 上（取消不会立刻打断阻塞 IO），
        // 不能无限 join，否则这一块播完播放队列就永远停在这里。
        val joined = withTimeoutOrNull(3_000L) { job.join() }
        if (joined == null) {
            logEvent("合成协程 3s 未退出，取消", "错误", DiagnosticLevel.Error)
            Log.w("BookVoice", "流式合成协程未在 3s 内退出，取消并继续")
            job.cancel()
            buffer.cancel()
        }
        if (streamJob === job) streamJob = null
        if (streamBuffer === buffer) streamBuffer = null
        streamDurationMs = 0L

        // 中途断流：先判断是不是网络本身断了；离线时不能跳段，否则下一块同样没数据。
        if (timeoutKind.get() == "idle") {
            if (hasNetwork()) {
                recordChunkFailure(currentBookId, chapterIdx, chunk)
                return true
            }
            Log.w("BookVoice", "流式断流且网络不可用，等待网络恢复后重播本块")
            if (!awaitNetwork()) {
                throw TtsException.Network("网络中断，等待超时")
            }
            _state.value = PlayerState.Buffering(chapterIdx, firstBlock)
            val retry = ensureFile(
                chunk.text,
                currentBookId,
                chapterIdx,
                chunk.spans,
                waitMs = 120_000L
            )
            playAndWait(retry)
            return true
        }

        val failure = streamError.get() ?: playbackFailure
        if (failure == null && timeoutKind.get() == null) return true
        runCatching { exo.stop() }

        val actual = failure ?: IllegalStateException("流式首包超时")
        if (actual is TtsException.Auth ||
            actual is TtsException.Quota ||
            actual is TtsException.TooLong
        ) {
            throw actual
        }

        // 流式没拿到数据时，整块请求走的是同一网络，等只会更久：
        // 先重试流式（首包通常 1 秒），别急着退回整块。
        if (attempt < MAX_STREAM_RETRIES) {
            if (!hasNetwork()) {
                Log.w("BookVoice", "流式失败且网络不可用，等待网络恢复后重试")
                if (!awaitNetwork()) throw TtsException.Network("网络中断，等待超时")
            }
            Log.w("BookVoice", "流式失败（${actual.message}），重试 ${attempt + 1}/$MAX_STREAM_RETRIES")
            _state.value = PlayerState.Buffering(chapterIdx, firstBlock)
            delay(1_500L)
            return playChunkStreamed(chunk, chapterIdx, firstBlock, chunkIndex, attempt + 1)
        }

        // 重试后仍一个 PCM 字节都没收到：整块合成同样拿不到数据，别让用户白等。
        if (firstPcmAt.get() == 0L) {
            throw TtsException.Network("TTS 服务没有返回音频，请检查网络后重试")
        }

        // 已经播出一部分、重试仍失败：网络恢复后整块重播，保证这一块文本完整。
        if (!hasNetwork()) {
            Log.w("BookVoice", "流式回退前网络不可用，等待恢复")
            if (!awaitNetwork()) throw TtsException.Network("网络中断，等待超时")
        }
        Log.w("BookVoice", "流式回退到整块合成：${actual.message}")
        _diagnostics.update { it.copy(streamFallbacks = it.streamFallbacks + 1) }
        _state.value = PlayerState.Buffering(chapterIdx, firstBlock)
        val file = ensureFile(
            chunk.text,
            currentBookId,
            chapterIdx,
            chunk.spans,
            waitMs = 90_000L
        )
        playAndWait(file)
        return true
    }

    /** 把这一块标记成「待补」，下次缓存/重读时可以补上。 */
    private suspend fun recordChunkFailure(
        bookId: String,
        chapterIdx: Int,
        chunk: PlannedChunk
    ) {
        if (bookId.isEmpty()) return
        withContext(Dispatchers.IO) {
            chunk.spans.forEach { span ->
                val text = blockTexts[span.blockIdx].orEmpty()
                if (text.isNotEmpty()) {
                    Db.get().recordFailure(bookId, chapterIdx, span.blockIdx, text)
                }
            }
        }
    }

    private suspend fun playAndWait(file: File) {
        streamDurationMs = 0L
        suspendCancellableCoroutine { cont ->
            val listener = mediaListener(cont)
            exo.addListener(listener)
            exo.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            exo.prepare()
            exo.playWhenReady = !paused
            cont.invokeOnCancellation {
                exo.removeListener(listener)
                exo.stop()
            }
        }
    }

    private suspend fun playMediaSourceAndWait(
        source: MediaSource,
        onCancel: () -> Unit = {},
        onReady: ((CancellableContinuation<Unit>) -> Unit)? = null
    ) = suspendCancellableCoroutine { cont ->
        val listener = mediaListener(cont)
        exo.addListener(listener)
        playbackListener = listener
        onReady?.invoke(cont)
        exo.setMediaSource(source)
        exo.prepare()
        exo.playWhenReady = !paused
        cont.invokeOnCancellation {
            onCancel()
            playbackListener = null
            exo.removeListener(listener)
            exo.stop()
        }
    }

    private fun mediaListener(cont: CancellableContinuation<Unit>): Player.Listener =
        object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    logEvent("播放器 ENDED", "播放器")
                    exo.removeListener(this)
                    playbackListener = null
                    if (cont.isActive) cont.resume(Unit)
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying && firstSoundAt == 0L && playStartedAt > 0L) {
                    firstSoundAt = SystemClock.elapsedRealtime()
                    val elapsed = firstSoundAt - playStartedAt
                    _diagnostics.update {
                        if (currentChunkCached) it.copy(firstSoundWarmMs = elapsed)
                        else it.copy(firstSoundColdMs = elapsed)
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                logEvent("播放错误：${error.errorCodeName}", "播放器", DiagnosticLevel.Error)
                exo.removeListener(this)
                playbackListener = null
                if (cont.isActive) {
                    cont.resumeWithException(IllegalStateException(error.message ?: "播放失败"))
                }
            }
        }

    private fun stopInternal(stopService: Boolean) {
        playJob?.cancel()
        playJob = null
        streamJob?.cancel()
        streamJob = null
        // 先让阻塞中的 DataSource.read 返回，否则 exo.stop() 会等加载线程。
        streamBuffer?.cancel()
        streamBuffer = null
        prefetchJob?.cancel()
        prefetchJob = null
        nextChapterJob?.cancel()
        nextChapterJob = null
        sleepJob?.cancel()
        sleepJob = null
        _sleepMinutes.value = 0
        runCatching { exo.stop() }
        streamDurationMs = 0L
        paused = false
        currentBookId = ""
        _playingBookId.value = ""
        currentChapterIdx = -1
        currentBlockIdx = -1
        currentIndex = -1
        lastSavedBlock = -1
        chunks = emptyList()
        _progress.value = null
        _state.value = PlayerState.Idle
        _highlight.value = null
        _waitingNetwork.value = false
        if (stopService) PlaybackService.stop(context)
    }
}
