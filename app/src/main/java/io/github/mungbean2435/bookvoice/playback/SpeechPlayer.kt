package io.github.mungbean2435.bookvoice.playback

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
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
import io.github.mungbean2435.bookvoice.tts.ProviderRegistry
import io.github.mungbean2435.bookvoice.tts.rules.RulePack
import io.github.mungbean2435.bookvoice.tts.rules.RulesEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class BookProgress(
    val bookId: String,
    val chapterIdx: Int,
    val blockIdx: Int,
    val charsBefore: Long,
    val totalChars: Long
)

data class Diagnostics(
    val firstSoundColdMs: Long = 0,
    val firstSoundWarmMs: Long = 0,
    val cacheHits: Int = 0,
    val cacheMisses: Int = 0
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

    private var cacheJob: Job? = null
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
        ExoPlayer.Builder(context).build().apply {
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
    private var prefetchJob: Job? = null
    private var nextChapterJob: Job? = null
    private var sleepJob: Job? = null

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

    /** [charOffset] 是 [blockIdx] 段内的起始字符（点哪句从哪句起播）。 */
    fun playFrom(bookId: String, chapterIdx: Int, blockIdx: Int, charOffset: Int = 0) {
        stopInternal(stopService = false)
        runCatching { exo.setPlaybackParameters(PlaybackParameters(playbackSpeed())) }
        playStartedAt = SystemClock.elapsedRealtime()
        firstSoundAt = 0L
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
        cacheJob = scope.launch {
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
            for (chunk in planned) {
                runCatching { ensureFile(chunk.text, bookId, chapterIdx, chunk.spans, rules) }
                done += 1
                _cacheProgress.value = done to planned.size
            }
        }
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
                startPrefetch()

                for (index in chunks.indices) {
                    currentIndex = index
                    val chunk = chunks[index]
                    val firstBlock = chunk.spans.firstOrNull()?.blockIdx ?: fromBlock
                    currentBlockIdx = firstBlock
                    _state.value = PlayerState.Buffering(chapter, firstBlock)
                    withContext(Dispatchers.IO) {
                        library.saveProgress(bookId, chapter, firstBlock, 0)
                    }
                    publishProgress(bookId, chapter, firstBlock)

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

                    maybePrefetchNextChapter(bookId, chapter, chapters.size, index)

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
                runCatching { ensureFile(chunk.text, "", -1, emptyList()) }
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
                    prefetchCursor++
                    runCatching { ensureFile(text, "", -1, emptyList()) }
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
        configOverride: TtsConfig? = null
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
            _diagnostics.update { d -> d.copy(cacheHits = d.cacheHits + 1) }
            currentChunkCached = true
            return it
        }
        currentChunkCached = false
        _diagnostics.update { d -> d.copy(cacheMisses = d.cacheMisses + 1) }

        return synthMutex.withLock {
            cache.existing(key, ext)?.let {
                currentChunkCached = true
                return@withLock it
            }

            var stored: File? = null
            var attempt = 0
            val deadline = System.currentTimeMillis() + waitMs

            while (stored == null) {
                try {
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
                    _waitingNetwork.value = false
                    if (result.truncated) {
                        Log.w("BookVoice", "chunk 疑似被截断：${text.length} 字，${result.durationMs} ms")
                    }
                    stored = cache.store(key, result.audio, ext)
                    cache.trim(Settings.state.value.cacheLimitMb.toLong() * 1024 * 1024)
                    if (bookId.isNotEmpty()) {
                        withContext(Dispatchers.IO) {
                            spans.forEach { span ->
                                Db.get().clearFailure(bookId, chapterIdx, span.blockIdx)
                            }
                        }
                    }
                } catch (e: TtsException) {
                    // 只有网络抖动 / 限流值得重试；配置错误（音色 ID、端点、404）必须立刻失败
                    if (e is TtsException.Network || e is TtsException.RateLimited) {
                        attempt += 1
                        if (System.currentTimeMillis() > deadline) {
                            _waitingNetwork.value = false
                            throw e
                        }
                        _waitingNetwork.value = true
                        val backoff = 1000L * (1 shl attempt.coerceAtMost(4))
                        delay(backoff.coerceAtMost(15_000L))
                    } else {
                        _waitingNetwork.value = false
                        throw e
                    }
                } catch (e: Exception) {
                    _waitingNetwork.value = false
                    throw e
                }
            }
            stored!!
        }
    }

    /** 按播放进度估算句子位置，并顺手把阅读进度写成「第几段」。 */
    /**
     * 段内连续高亮：从段落开头填充到当前读到位置，**不按标点切句**。
     * 供应商不给时间戳，所以位置仍是按播放进度估算的。
     */
    private suspend fun tickPlayback(chapterIdx: Int, chunk: PlannedChunk) {
        while (true) {
            val duration = exo.duration
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

    private suspend fun playAndWait(file: File) = suspendCancellableCoroutine { cont ->
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    exo.removeListener(this)
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
                exo.removeListener(this)
                if (cont.isActive) {
                    cont.resumeWithException(IllegalStateException(error.message ?: "播放失败"))
                }
            }
        }
        exo.addListener(listener)
        exo.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
        exo.prepare()
        exo.playWhenReady = !paused
        cont.invokeOnCancellation {
            exo.removeListener(listener)
            exo.stop()
        }
    }

    private fun stopInternal(stopService: Boolean) {
        playJob?.cancel()
        playJob = null
        prefetchJob?.cancel()
        prefetchJob = null
        nextChapterJob?.cancel()
        nextChapterJob = null
        sleepJob?.cancel()
        sleepJob = null
        _sleepMinutes.value = 0
        runCatching { exo.stop() }
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
