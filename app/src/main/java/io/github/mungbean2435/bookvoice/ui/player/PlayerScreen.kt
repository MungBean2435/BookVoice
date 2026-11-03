package io.github.mungbean2435.bookvoice.ui.player

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mungbean2435.bookvoice.data.db.Block
import io.github.mungbean2435.bookvoice.data.db.Book
import io.github.mungbean2435.bookvoice.data.db.Bookmark
import io.github.mungbean2435.bookvoice.data.db.Chapter
import io.github.mungbean2435.bookvoice.data.db.ChapterImage
import io.github.mungbean2435.bookvoice.data.db.Failure
import io.github.mungbean2435.bookvoice.data.settings.Metrics
import io.github.mungbean2435.bookvoice.data.settings.Settings
import io.github.mungbean2435.bookvoice.data.settings.TtsConfigs
import io.github.mungbean2435.bookvoice.playback.PlayerState
import io.github.mungbean2435.bookvoice.playback.Speech
import io.github.mungbean2435.bookvoice.tts.PlaybackMath
import io.github.mungbean2435.bookvoice.tts.Voice
import io.github.mungbean2435.bookvoice.ui.AppViewModel
import io.github.mungbean2435.bookvoice.ui.components.FileImage
import io.github.mungbean2435.bookvoice.ui.theme.BookVoiceDesign
import io.github.mungbean2435.bookvoice.ui.theme.BookVoiceReadingTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.UUID

/**
 * 主界面：连续阅读 + 聚焦式听书。
 * 全书放在同一个滚动列表里，章与章之间可以直接滑过去；
 * 当前段用中性底色 + 字重强调，非当前段保持完整对比度。
 */

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlayerScreen(
    vm: AppViewModel,
    bookId: String,
    startChapter: Int,
    onBack: () -> Unit,
    onOpenChapters: (Int) -> Unit,
    onOpenSettings: () -> Unit,
    onCoverBounds: ((Rect) -> Unit)? = null,
    // 变形动画期间只挂轻量外壳（顶栏 + 骨架），正文等动画结束再组合，
    // 免得正文的文本测量挤进「点击→首帧」这段关键路径。
    bodyReady: Boolean = true
) {
    val library = remember(vm) { vm.library() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val player = remember { Speech.get() }

    val playerState by player.state.collectAsStateWithLifecycle()
    val highlight by player.highlight.collectAsStateWithLifecycle()
    val sleepMinutes by player.sleepMinutes.collectAsStateWithLifecycle()
    val waitingNetwork by player.waitingNetwork.collectAsStateWithLifecycle()
    val progress by player.progress.collectAsStateWithLifecycle()
    val cacheProgress by player.cacheProgress.collectAsStateWithLifecycle()
    val settings by Settings.state.collectAsStateWithLifecycle()
    val activeConfig = settings.activeConfigOrDefault()

    var book by remember { mutableStateOf<Book?>(null) }
    var chapters by remember { mutableStateOf<List<Chapter>>(emptyList()) }
    var rows by remember { mutableStateOf<List<DisplayRow>>(emptyList()) }
    var layout by remember { mutableStateOf<ChapterLayout?>(null) }
    var loading by remember { mutableStateOf(true) }
    val bodyAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (bodyReady) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(150),
        label = "readerBodyAlpha"
    )
    // 只保留当前章 ±1（以及正在播放的章）的正文；其余章在 rows 里是占位行。
    val loadedTexts = remember { HashMap<Int, List<Block>>() }
    // [0]=打开起点纳秒, [1]=查询 ms, [2]=构建 rows ms, [3]=是否已上报
    val openTiming = remember { LongArray(4) }

    var displayedChapter by remember { mutableIntStateOf(startChapter) }
    var didInitialScroll by remember { mutableStateOf(false) }
    var followPlayback by remember { mutableStateOf(false) }
    // 上一拍播放所在的章，用来判断“是不是从当前正文自然播过去的”。
    var lastPlayingChapter by remember { mutableIntStateOf(-1) }

    var showBookmarks by remember { mutableStateOf(false) }
    var showChapters by remember { mutableStateOf(false) }
    var showFailures by remember { mutableStateOf(false) }
    var showSleep by remember { mutableStateOf(false) }
    var showSpeed by remember { mutableStateOf(false) }
    var showVoice by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showReadingSettings by remember { mutableStateOf(false) }
    var showPlaybackSettings by remember { mutableStateOf(false) }
    var bookmarks by remember { mutableStateOf<List<Bookmark>>(emptyList()) }
    var failures by remember { mutableStateOf<List<Failure>>(emptyList()) }
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    val listState = rememberLazyListState()
    var autoFollow by remember { mutableStateOf(true) }
    // 只有手指拖动才算“用户自己在翻”；animateScrollToItem 等程序化滚动不能停掉跟随。
    val isDragged by listState.interactionSource.collectIsDraggedAsState()
    // 冷启动隐藏；显隐只影响浮层，不参与正文布局。
    var chromeVisible by remember { mutableStateOf(false) }
    val tapZone = remember { TapZoneHolder() }
    val tokens = BookVoiceDesign.tokens
    val reading = tokens.reading
    val density = LocalDensity.current
    val paragraphSpacing = with(density) {
        (settings.fontSizeSp * reading.paragraphSpacingEm).sp.toDp()
    }
    val chromeSlidePx = with(density) { reading.chromeSlideDistance.roundToPx() }
    val miniBarReserve = tokens.miniPlayerHeight + reading.chromeVerticalPadding * 2

    val playingChapter = when (val s = playerState) {
        is PlayerState.Playing -> s.chapterIdx
        is PlayerState.Paused -> s.chapterIdx
        is PlayerState.Buffering -> s.chapterIdx
        else -> -1
    }
    val playingBlock = when (val s = playerState) {
        is PlayerState.Playing -> s.blockIdx
        is PlayerState.Paused -> s.blockIdx
        is PlayerState.Buffering -> s.blockIdx
        else -> -1
    }
    val isPlaying = playerState is PlayerState.Playing
    val isBuffering = playerState is PlayerState.Buffering || waitingNetwork

    val headerItemCount = if (book != null) 1 else 0
    // 第一行可见内容（可能是章间留白 / 插图），用来同步左上角章节名。
    val visibleRowIndex by remember(rows, book) {
        derivedStateOf {
            (listState.firstVisibleItemIndex - headerItemCount).coerceAtLeast(0)
        }
    }
    val visibleRow by remember(rows, book) {
        derivedStateOf { rows.getOrNull(visibleRowIndex) }
    }
    val visibleChapter by remember(rows, book) {
        derivedStateOf { visibleRow?.chapterIdx ?: -1 }
    }
    // 视口附近的第一段正文（可能是还没拉正文的占位段）；没在播放时用它定位书签和当前段。
    val visibleBlock by remember(rows, book) {
        derivedStateOf<Pair<Int, Int>?> {
            var i = visibleRowIndex
            val end = minOf(rows.size, i + 24)
            while (i < end) {
                when (val row = rows[i]) {
                    is DisplayRow.Text -> return@derivedStateOf row.chapterIdx to row.block.idx
                    is DisplayRow.Stub -> return@derivedStateOf row.chapterIdx to row.blockIdx
                    // 不越过章界去拿下一章的第一段，书签会落到错误的章。
                    is DisplayRow.Break -> return@derivedStateOf null
                    is DisplayRow.Picture -> Unit
                }
                i++
            }
            null
        }
    }

    val highlightBlock = highlight
        ?.takeIf { it.chapterIdx == displayedChapter }
        ?.blockIdx
    val savedBlock = book
        ?.takeIf { it.lastChapterIdx == displayedChapter }
        ?.lastBlockIdx
        ?: 0
    val currentBlock = highlightBlock ?: when {
        playingChapter == displayedChapter && playingBlock >= 0 -> playingBlock
        visibleBlock?.first == displayedChapter -> visibleBlock?.second ?: savedBlock
        else -> savedBlock
    }

    // P0 首屏：只查 book + chapters + 每章段数 + 全书插图元数据 + 首章正文，
    // 一次建出「全书骨架行」立刻渲染；其余章的正文由下面的窗口加载补齐。
    LaunchedEffect(bookId) {
        openTiming[0] = Metrics.takeOpenStartNanos().takeIf { it != 0L } ?: System.nanoTime()
        val startChapter = displayedChapter
        val queryStart = System.nanoTime()
        val initial = withContext(Dispatchers.IO) {
            InitialPlayerData(
                book = library.book(bookId),
                chapters = library.chapters(bookId),
                blockCounts = library.chapterBlockCounts(bookId),
                images = library.allImages(bookId),
                startBlocks = library.blocks(bookId, startChapter)
            )
        }
        openTiming[1] = (System.nanoTime() - queryStart) / 1_000_000
        val buildStart = System.nanoTime()
        book = initial.book
        chapters = initial.chapters
        val loadedLayout = ChapterLayout.of(initial.blockCounts, initial.images)
        layout = loadedLayout
        loadedTexts[startChapter] = initial.startBlocks
        rows = buildSkeletonRows(loadedLayout, mapOf(startChapter to initial.startBlocks))
        openTiming[2] = (System.nanoTime() - buildStart) / 1_000_000
        if (initial.book == null || loadedLayout.chapterIdxs.isEmpty()) {
            loading = false
            didInitialScroll = true
        }
    }

    // 打开后只定位一次：回到上次读到的那一段。骨架一直盖在列表上，等这里滚完才撤，
    // 所以正文第一次露出来就在正确位置；用户已经在拖动时不再抢。
    LaunchedEffect(rows, book) {
        if (didInitialScroll || rows.isEmpty()) return@LaunchedEffect
        val header = if (book != null) 1 else 0
        val targetChapter = displayedChapter.coerceAtLeast(0)
        val targetBlock = book
            ?.takeIf { it.lastChapterIdx == targetChapter }
            ?.lastBlockIdx
            ?: 0
        if ((targetChapter > 0 || targetBlock > 0) && !listState.isScrollInProgress) {
            val index = rows.indexOfFirst {
                it is DisplayRow.Text &&
                    it.chapterIdx == targetChapter &&
                    it.block.idx == targetBlock
            }
            if (index >= 0) listState.scrollToItem(index + header)
        }
        didInitialScroll = true
        loading = false
        scope.launch { reportOpenTiming(openTiming, chapters, layout) }
    }

    // 手动滑过章界时，左上角章节名跟着第一行可见内容走。
    // 播放跟随中的程序化滚动不算手动，不能反向改写 displayedChapter，
    // 否则跨多章滚动会被中途的可见章打断。
    LaunchedEffect(visibleChapter, didInitialScroll) {
        if (!didInitialScroll) return@LaunchedEffect
        if (followPlayback && autoFollow && playingBlock >= 0) return@LaunchedEffect
        if (visibleChapter >= 0 && visibleChapter != displayedChapter) {
            displayedChapter = visibleChapter
        }
    }

    // 播放推进到新章节时跟随：点读/跟随时任意跨章；没有显式跟随时，
    // 只跟“从正在看的这一章自然播下去”的情况（允许跳过只有插图的空章）。
    LaunchedEffect(playingChapter) {
        val follow = shouldFollowChapter(
            playingChapter = playingChapter,
            displayedChapter = displayedChapter,
            followPlayback = followPlayback,
            previousPlayingChapter = lastPlayingChapter
        )
        lastPlayingChapter = playingChapter
        if (!follow) return@LaunchedEffect
        followPlayback = true
        autoFollow = true
        displayedChapter = playingChapter
    }

    // 手指拖动 → 暂停跟随；点击“回到朗读”会重新打开。
    LaunchedEffect(isDragged) {
        if (isDragged) autoFollow = false
    }

    // 不朗读时手动翻到哪里，就把阅读进度记到哪里（700ms 防抖）；
    // 播放中由 SpeechPlayer 按音频段落记录，避免互相覆盖。
    LaunchedEffect(didInitialScroll, displayedChapter, currentBlock, playingBlock) {
        if (!didInitialScroll || rows.isEmpty() || playingBlock >= 0) return@LaunchedEffect
        delay(700)
        withContext(Dispatchers.IO) {
            library.saveProgress(bookId, displayedChapter, currentBlock, 0)
        }
    }

    // 朗读推进 → 把播放段滚到顶栏下方；全书列表里跨多少章都能定位。
    LaunchedEffect(
        playingChapter,
        playingBlock,
        rows,
        displayedChapter,
        autoFollow,
        didInitialScroll
    ) {
        if (!didInitialScroll || !autoFollow) return@LaunchedEffect
        if (playingChapter < 0 || playingBlock < 0) return@LaunchedEffect
        if (playingChapter != displayedChapter) return@LaunchedEffect
        val index = rows.indexOfFirst {
            it is DisplayRow.Text &&
                it.chapterIdx == playingChapter &&
                it.block.idx == playingBlock
        }
        if (index >= 0) {
            listState.animateScrollToItem(index + headerItemCount)
        }
    }

    // P1 正文按需：只在内存里保留当前章 ±1（以及正在播放的章）的 Block，
    // 其余章在 rows 里保持占位行；换入/换出只改内容、不改键序，所以不会跳位。
    LaunchedEffect(displayedChapter, playingChapter, layout, didInitialScroll) {
        val l = layout ?: return@LaunchedEffect
        if (!didInitialScroll) return@LaunchedEffect
        delay(120) // 连续滑章时只处理停下来后的那一章
        val wanted = sortedSetOf<Int>()
        for (c in intArrayOf(displayedChapter - 1, displayedChapter, displayedChapter + 1)) {
            if (l.contains(c)) wanted.add(c)
        }
        if (playingChapter >= 0 && l.contains(playingChapter)) wanted.add(playingChapter)
        val dirty = HashSet<Int>()
        for (c in l.chapterIdxs) {
            if (c !in wanted && loadedTexts.remove(c) != null) dirty.add(c)
        }
        val missing = wanted.filter { !loadedTexts.containsKey(it) }
        if (missing.isNotEmpty()) {
            val fetched = withContext(Dispatchers.IO) {
                missing.map { chapterIdx -> chapterIdx to library.blocks(bookId, chapterIdx) }
            }
            if (!isActive) return@LaunchedEffect
            fetched.forEach { (chapterIdx, blocks) ->
                loadedTexts[chapterIdx] = blocks
                dirty.add(chapterIdx)
            }
        }
        if (dirty.isEmpty()) return@LaunchedEffect
        var next = rows
        for (chapterIdx in dirty) {
            val index = l.chapterIdxs.binarySearch(chapterIdx)
            if (index < 0) continue
            next = replaceChapterRows(
                next,
                chapterIdx,
                buildChapterRows(
                    chapterIdx = chapterIdx,
                    blockCount = l.blockCountOf(chapterIdx),
                    images = l.imagesOf(chapterIdx),
                    texts = loadedTexts[chapterIdx]?.associateBy { it.idx }.orEmpty(),
                    includeBreak = index > 0
                )
            )
        }
        rows = next
    }

    LaunchedEffect(playerState) {
        val state = playerState
        if (state is PlayerState.Error) {
            Toast.makeText(context, state.message, Toast.LENGTH_LONG).show()
            player.clearError()
        }
    }

    LaunchedEffect(bookId, playerState) {
        failures = withContext(Dispatchers.IO) { library.failures(bookId) }
    }

    LaunchedEffect(showBookmarks, bookId) {
        if (showBookmarks) bookmarks = withContext(Dispatchers.IO) { library.bookmarks(bookId) }
    }

    val totalChars = (book?.charCount ?: 1).toLong().coerceAtLeast(1)
    val fraction = progress
        ?.takeIf { it.bookId == bookId && it.totalChars > 0 }
        ?.let { it.charsBefore.toFloat() / it.totalChars }
        ?: 0f
    val shownFraction = if (dragging) dragValue else fraction.coerceIn(0f, 1f)
    val currentChapterTitle = chapters.getOrNull(displayedChapter)?.title.orEmpty()
    val chromeState = PlayerChromeState(
        fraction = shownFraction,
        elapsed = PlaybackMath.formatDuration(
            PlaybackMath.secondsForChars((shownFraction * totalChars).toLong())
        ),
        remaining = "-" + PlaybackMath.formatDuration(
            PlaybackMath.secondsForChars(((1f - shownFraction) * totalChars).toLong())
        ),
        isPlaying = isPlaying,
        isBuffering = isBuffering,
        waitingNetwork = waitingNetwork,
        speedText = String.format(Locale.US, "%.2f×", settings.speed),
        voiceText = activeConfig.voice.ifBlank { "未设置" },
        configName = activeConfig.name,
        sleepText = if (sleepMinutes > 0) "$sleepMinutes 分" else "未设置",
        cacheText = if (cacheProgress.second > 0) {
            "${cacheProgress.first}/${cacheProgress.second}"
        } else {
            "离线"
        }
    )

    val topStatus = when {
        waitingNetwork -> "网络中断" to true
        isBuffering -> "缓冲中…" to false
        cacheProgress.second > 0 && cacheProgress.first < cacheProgress.second ->
            "缓存 ${cacheProgress.first}/${cacheProgress.second}" to false
        else -> "" to false
    }

    fun startPlayback(chapter: Int, block: Int, offset: Int = 0) {
        followPlayback = true
        autoFollow = true
        displayedChapter = chapter
        player.playFrom(bookId, chapter, block, offset)
    }

    fun togglePlayback() {
        when (playerState) {
            is PlayerState.Playing, is PlayerState.Paused, is PlayerState.Buffering -> {
                followPlayback = true
                player.togglePause()
            }

            else -> startPlayback(displayedChapter, currentBlock)
        }
    }

    fun seekPreview(value: Float) {
        dragging = true
        dragValue = value
    }

    fun finishSeek() {
        dragging = false
        player.seekToFraction(bookId, dragValue, totalChars)
        followPlayback = true
    }

    fun addBookmark() {
        val markChapter = visibleBlock?.first
            ?: visibleRow?.chapterIdx
            ?: displayedChapter
        val markBlock = visibleBlock
            ?.takeIf { it.first == markChapter }
            ?.second
            ?: if (playingChapter == markChapter) playingBlock.coerceAtLeast(0) else 0
        val mark = Bookmark(
            id = UUID.randomUUID().toString(),
            bookId = bookId,
            chapterIdx = markChapter,
            blockIdx = markBlock,
            offsetMs = 0,
            note = null,
            createdAt = System.currentTimeMillis()
        )
        bookmarks = (bookmarks + mark).sortedWith(
            compareBy({ it.chapterIdx }, { it.blockIdx })
        )
        scope.launch(Dispatchers.IO) { library.addBookmark(mark) }
    }

    /** 滚动模式下的“翻页”：左边缘回退一屏，右边缘前进一屏。 */
    fun pageBy(direction: Int) {
        val viewport = listState.layoutInfo.viewportSize.height
        if (viewport <= 0) return
        if (direction > 0 && !listState.canScrollForward) return
        if (direction < 0 && !listState.canScrollBackward) return
        autoFollow = false
        scope.launch {
            listState.animateScrollBy(
                value = viewport.toFloat() * direction,
                animationSpec = tween(reading.pageTurnDurationMs)
            )
        }
    }

    /**
     * 恢复跟随并把播放段滚到顶栏下方。
     * 全书是一个列表，跨章时直接定位到目标段。
     */
    fun resumeFollow() {
        autoFollow = true
        followPlayback = true
        if (playingChapter < 0) return
        displayedChapter = playingChapter
        scope.launch {
            val targetBlock = playingBlock.coerceAtLeast(0)
            val index = rows.indexOfFirst {
                it is DisplayRow.Text &&
                    it.chapterIdx == playingChapter &&
                    it.block.idx == targetBlock
            }
            if (index >= 0) {
                listState.animateScrollToItem(index + headerItemCount)
            }
        }
    }

    fun handleTap(zone: ReadingTapZone, onText: Boolean, block: Block?) {
        when (zone) {
            ReadingTapZone.LeftEdge -> pageBy(-1)
            ReadingTapZone.RightEdge -> pageBy(1)
            ReadingTapZone.Center -> when (
                tapAction(chromeVisible, settings.tapToRead, onText)
            ) {
                TapAction.ShowChrome -> chromeVisible = true
                TapAction.HideChrome -> chromeVisible = false
                TapAction.ReadBlock -> block?.let { startPlayback(it.chapterIdx, it.idx) }
            }
        }
    }

    Scaffold(
        topBar = {},
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 固定 48dp 顶栏槽：收起=章节名+右侧状态，展开=操作行。
            // 它参与布局而不是压在正文上，正文从它下面开始滚动。
            Box(
                    Modifier
                        .fillMaxWidth()
                        .height(reading.topChromeHeight)
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    Crossfade(
                        targetState = chromeVisible,
                        animationSpec = tween(reading.chromeEnterDurationMs),
                        label = "topChrome",
                        modifier = Modifier.fillMaxSize()
                    ) { expanded ->
                        if (expanded) {
                            PlayerTopBar(
                                tapToRead = settings.tapToRead,
                                onBack = onBack,
                                onOpenChapters = { showChapters = true },
                                onOpenReadingSettings = { showReadingSettings = true },
                                onOpenBookmarks = { showBookmarks = true },
                                onOpenMenu = { showMenu = true },
                                onClose = { chromeVisible = false },
                                menu = {
                                    DropdownMenu(
                                        expanded = showMenu,
                                        onDismissRequest = { showMenu = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("整页目录") },
                                            onClick = {
                                                showMenu = false
                                                onOpenChapters(displayedChapter)
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("定时关闭") },
                                            onClick = {
                                                showMenu = false
                                                showSleep = true
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = {
                                                Text("待补段落（${failures.size}）")
                                            },
                                            onClick = {
                                                showMenu = false
                                                if (failures.isNotEmpty()) {
                                                    showFailures = true
                                                }
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("设置") },
                                            onClick = {
                                                showMenu = false
                                                onOpenSettings()
                                            }
                                        )
                                    }
                                }
                            )
                        } else {
                            CollapsedChapterRow(
                                chapterTitle = currentChapterTitle,
                                statusText = topStatus.first,
                                statusIsError = topStatus.second,
                                coverPath = book?.coverPath,
                                coverFormat = book?.format.orEmpty(),
                                // 变形落点：收起态顶栏那格 40dp 封面一直在。
                                onCoverBounds = onCoverBounds
                            )
                        }
                    }
                    if (chromeVisible) {
                        HorizontalDivider(
                            modifier = Modifier.align(Alignment.BottomCenter),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                        )
                    }
                }

                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                if (bodyReady) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = bodyAlpha }
                        .pointerInput(chromeVisible, settings.tapToRead, reading) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val zone = readingTapZone(
                                    x = down.position.x,
                                    widthPx = size.width,
                                    edgeFraction = reading.edgeZoneFraction
                                )
                                tapZone.zone = zone
                                var moved = false
                                var consumed = false
                                var totalDy = 0f
                                var pressed: Boolean
                                do {
                                    val event = awaitPointerEvent()
                                    if (event.changes.any { it.isConsumed }) consumed = true
                                    event.changes.forEach { change ->
                                        if (
                                            (change.position - change.previousPosition)
                                                .getDistance() > viewConfiguration.touchSlop
                                        ) {
                                            moved = true
                                        }
                                        if (change.id == down.id) {
                                            totalDy +=
                                                change.position.y - change.previousPosition.y
                                        }
                                    }
                                    if (
                                        totalDy > viewConfiguration.touchSlop ||
                                        totalDy < -viewConfiguration.touchSlop
                                    ) {
                                        moved = true
                                    }
                                    pressed = event.changes.any { it.pressed }
                                } while (pressed)

                                if (!moved && !consumed) {
                                    handleTap(zone, onText = false, block = null)
                                } else if (
                                    chromeVisible &&
                                    chromeSwipeHides(
                                        dy = totalDy,
                                        thresholdPx = reading.swipeThreshold.toPx()
                                    )
                                ) {
                                    // 上滑、下滑都收起；呼出只靠点击中部，避免
                                    // 正常阅读滚动时浮层乱弹。
                                    chromeVisible = false
                                }
                            }
                        },
                    contentPadding = PaddingValues(
                        start = reading.horizontalPadding,
                        end = reading.horizontalPadding,
                        top = reading.contentTopPadding,
                        bottom = reading.contentBottomPadding
                    )
                ) {
                    book?.let { currentBook ->
                        item(key = "book-header") {
                            PlayerBookHeader(
                                book = currentBook,
                                onOpenChapters = { showChapters = true },
                                onOpenBookmarks = { showBookmarks = true }
                            )
                        }
                    }
                    items(rows, key = {
                        when (it) {
                            is DisplayRow.Text -> "t${it.chapterIdx}:${it.block.idx}"
                            // 占位段与真实段落共用同一个键，换入正文时不会当成新行插入。
                            is DisplayRow.Stub -> it.key
                            is DisplayRow.Picture -> "p${it.chapterIdx}:${it.path}"
                            is DisplayRow.Break -> it.key
                        }
                    }) { row ->
                        when (row) {
                            is DisplayRow.Break -> {
                                Spacer(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(28.dp)
                                )
                            }

                            is DisplayRow.Picture -> {
                                // 不设高度上限：按图片原始比例占满宽度，避免长图被
                                // heightIn 居中裁掉上下（插图显示不完整的根因）。
                                FileImage(
                                    path = row.path,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp)
                                        .clip(tokens.cardShape)
                                )
                            }

                            is DisplayRow.Stub -> {
                                StubParagraph(bottomPadding = paragraphSpacing)
                            }

                            is DisplayRow.Text -> {
                                val block = row.block
                                val isCurrent = block.chapterIdx == displayedChapter &&
                                    block.idx == currentBlock
                                val highlightColor = if (isCurrent) {
                                    MaterialTheme.colorScheme.primary.copy(
                                        alpha = reading.highlightAlpha
                                    )
                                } else {
                                    Color.Transparent
                                }
                                val barColor = MaterialTheme.colorScheme.primary
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = paragraphSpacing)
                                        .combinedClickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                            onClick = {
                                                handleTap(
                                                    tapZone.zone,
                                                    onText = true,
                                                    block = block
                                                )
                                            }
                                        )
                                        .background(highlightColor, tokens.cardShape)
                                        .drawBehind {
                                            if (isCurrent && size.height > 4.dp.toPx()) {
                                                drawRoundRect(
                                                    color = barColor,
                                                    topLeft = Offset(0f, 2.dp.toPx()),
                                                    size = Size(
                                                        width = reading.highlightBarWidth.toPx(),
                                                        height = size.height - 4.dp.toPx()
                                                    ),
                                                    cornerRadius = CornerRadius(
                                                        reading.highlightBarWidth.toPx() / 2f
                                                    )
                                                )
                                            }
                                        }
                                        .padding(
                                            start = reading.highlightBarWidth +
                                                reading.highlightBarGutter,
                                            end = 4.dp
                                        )
                                ) {
                                    FocusParagraph(
                                        text = block.text,
                                        chapterIdx = block.chapterIdx,
                                        blockIdx = block.idx,
                                        highlight = highlight,
                                        fontSize = settings.fontSizeSp.sp,
                                        isCurrent = isCurrent
                                    )
                                }
                            }
                        }
                    }
                }
                }

                // 浮层收起时，恢复跟随的按钮停靠底部 16dp，不依赖底栏高度。
                // 声明在浮层之前：退场动画期间会被底栏盖住，动画结束才可点。
                if (!autoFollow && playingBlock >= 0 && !chromeVisible) {
                    FilledTonalButton(
                        onClick = { resumeFollow() },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp)
                            .heightIn(min = 48.dp)
                    ) {
                        Text("回到朗读", style = MaterialTheme.typography.labelMedium)
                    }
                }

                // 下浮层：改成图二的迷你播放条；完整控制在半屏设置里。
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.align(Alignment.BottomCenter)) {
                PlayerBottomChrome(visible = chromeVisible) {
                    PlayerMiniBar(
                        state = chromeState,
                        book = book,
                        chapterTitle = currentChapterTitle,
                        onOpenSettings = { showPlaybackSettings = true },
                        onClose = { chromeVisible = false },
                        onToggle = { togglePlayback() },
                        onPrev = {
                            startPlayback(
                                displayedChapter,
                                (currentBlock - 1).coerceAtLeast(0)
                            )
                        },
                        onNext = {
                            startPlayback(displayedChapter, currentBlock + 1)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .consumePanelTouches()
                            .padding(
                                horizontal = tokens.miniPlayerSidePadding,
                                vertical = reading.chromeVerticalPadding
                            )
                    )
                }
                    }
                }

                // 浮层展开时，回到朗读放在迷你条上方，不必先打开半屏设置。
                if (!autoFollow && playingBlock >= 0 && chromeVisible) {
                    FilledTonalButton(
                        onClick = { resumeFollow() },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 16.dp, bottom = miniBarReserve + 8.dp)
                            .heightIn(min = 48.dp)
                    ) {
                        Text("回到朗读", style = MaterialTheme.typography.labelMedium)
                    }
                }

                // 加载/定位期间盖一层固定骨架（顶栏槽已在上面），
                // 正文第一次露出来就在上次读到的位置，不再有全屏转圈。
                if (loading || !bodyReady) {
                    ReaderSkeleton(
                        reading = reading,
                        fontSizeSp = settings.fontSizeSp
                    )
                }
            }
        }
    }

    if (showPlaybackSettings) {
        PlaybackSettingsSheet(
            state = chromeState,
            autoFollow = autoFollow,
            onResumeFollow = { resumeFollow() },
            onSeek = { seekPreview(it) },
            onSeekFinished = { finishSeek() },
            onToggle = { togglePlayback() },
            onPrevChapter = {
                startPlayback((displayedChapter - 1).coerceAtLeast(0), 0)
            },
            onPrevBlock = {
                startPlayback(
                    displayedChapter,
                    (currentBlock - 1).coerceAtLeast(0)
                )
            },
            onNextBlock = {
                startPlayback(displayedChapter, currentBlock + 1)
            },
            onNextChapter = {
                startPlayback(displayedChapter + 1, 0)
            },
            onSpeed = { showSpeed = true },
            onVoice = { showVoice = true },
            onSleep = { showSleep = true },
            onCache = { player.cacheChapter(bookId, displayedChapter) },
            onDismiss = { showPlaybackSettings = false }
        )
    }

    if (showReadingSettings) {
        ReadingSettingsSheet(
            fontSizeSp = settings.fontSizeSp,
            tapToRead = settings.tapToRead,
            onFontSizeChange = { size ->
                Settings.update {
                    it.copy(
                        fontSizeSp = size.coerceIn(
                            MIN_READING_FONT_SP,
                            MAX_READING_FONT_SP
                        )
                    )
                }
            },
            onTapToReadChange = { enabled ->
                Settings.update { it.copy(tapToRead = enabled) }
            },
            onDismiss = { showReadingSettings = false }
        )
    }

    if (showBookmarks) {
        BookmarkSheet(
            bookmarks = bookmarks,
            onSelect = { mark ->
                showBookmarks = false
                startPlayback(
                    mark.chapterIdx,
                    mark.blockIdx,
                    mark.offsetMs.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
                )
            },
            onDelete = { mark ->
                bookmarks = bookmarks.filterNot { it.id == mark.id }
                scope.launch(Dispatchers.IO) { library.deleteBookmark(mark.id) }
            },
            onAdd = { addBookmark() },
            onDismiss = { showBookmarks = false }
        )
    }

    if (showChapters) {
        ChapterSheet(
            chapters = chapters,
            currentChapter = displayedChapter,
            onSelect = { index ->
                showChapters = false
                // 只有插图的章没有可朗读的正文，直接翻过去，别让播放器跳到下一章。
                // 用布局元数据判断，不依赖这一章正文是否已经拉进内存。
                val hasText = (layout?.blockCountOf(index) ?: 0) > 0
                if (hasText) {
                    startPlayback(index, 0)
                } else {
                    followPlayback = false
                    autoFollow = false
                    displayedChapter = index
                    scope.launch {
                        val rowIndex = rows.indexOfFirst { it.chapterIdx == index }
                        if (rowIndex >= 0) {
                            listState.animateScrollToItem(rowIndex + headerItemCount)
                        }
                    }
                }
            },
            onDismiss = { showChapters = false }
        )
    }

    if (showFailures) {
        AlertDialog(
            onDismissRequest = { showFailures = false },
            title = { Text("待补段落") },
            text = {
                Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                    failures.forEach { failure ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "第 ${failure.chapterIdx + 1} 章 · 第 ${failure.blockIdx + 1} 段",
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        showFailures = false
                                        startPlayback(failure.chapterIdx, failure.blockIdx)
                                    }
                                    .padding(vertical = 10.dp)
                            )
                            TextButton(onClick = {
                                failures = failures.filterNot {
                                    it.chapterIdx == failure.chapterIdx && it.blockIdx == failure.blockIdx
                                }
                                scope.launch(Dispatchers.IO) {
                                    library.clearFailure(
                                        bookId,
                                        failure.chapterIdx,
                                        failure.blockIdx
                                    )
                                }
                            }) { Text("删除") }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showFailures = false }) { Text("关闭") }
            }
        )
    }

    if (showSleep) {
        AlertDialog(
            onDismissRequest = { showSleep = false },
            title = { Text("定时关闭") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    listOf(15, 30, 45, 60).forEach { minutes ->
                        TextButton(onClick = {
                            player.startSleepTimer(minutes)
                            showSleep = false
                        }) { Text("$minutes 分钟") }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    player.startSleepTimer(0)
                    showSleep = false
                }) { Text("取消定时") }
            }
        )
    }

    if (showSpeed) {
        var value by remember { mutableFloatStateOf(settings.speed) }
        AlertDialog(
            onDismissRequest = { showSpeed = false },
            title = { Text("语速") },
            text = {
                Column {
                    Text(String.format(Locale.US, "%.2f×", value))
                    Slider(
                        value = value,
                        onValueChange = { value = it },
                        valueRange = 0.5f..2f
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    Settings.update { it.copy(speed = value) }
                    player.applySpeed(value)
                    showSpeed = false
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { showSpeed = false }) { Text("取消") }
            }
        )
    }

    if (showVoice) {
        var value by remember(activeConfig.id, settings.configs) {
            mutableStateOf(activeConfig.voice)
        }
        AlertDialog(
            onDismissRequest = { showVoice = false },
            title = { Text("音色") },
            text = {
                Column {
                    Text(
                        "当前配置：${activeConfig.name}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (activeConfig.voices.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(activeConfig.voices, key = { it.id }) { voice ->
                                FilterChip(
                                    selected = value == voice.id,
                                    onClick = { value = voice.id },
                                    label = { Text(voice.label) }
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = value,
                        onValueChange = { value = it },
                        label = { Text("音色 ID") },
                        singleLine = true
                    )
                    Text(
                        "点上面的已保存音色，或直接填 ID；保存后会自动加入音色库。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    Settings.update { old ->
                        val voiceValue = value.trim().ifBlank { old.activeConfigOrDefault().voice }
                        if (old.configs.isEmpty()) {
                            // 配置被删光时，音色胶囊仍然应该能把输入存下来。
                            val created = old.activeConfigOrDefault().copy(
                                id = TtsConfigs.newId(),
                                voice = voiceValue,
                                voices = if (voiceValue.isBlank()) {
                                    emptyList()
                                } else {
                                    listOf(Voice(voiceValue, voiceValue))
                                }
                            )
                            old.copy(configs = listOf(created), activeConfigId = created.id)
                        } else {
                            val current = old.activeConfigOrDefault()
                            old.copy(
                                configs = old.configs.map {
                                    if (it.id == current.id) {
                                        val library = if (
                                            voiceValue.isBlank() ||
                                            current.voices.any { voice -> voice.id == voiceValue }
                                        ) {
                                            current.voices
                                        } else {
                                            current.voices + Voice(voiceValue, voiceValue)
                                        }
                                        it.copy(voice = voiceValue, voices = library)
                                    } else {
                                        it
                                    }
                                }
                            )
                        }
                    }
                    showVoice = false
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { showVoice = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun PlayerBottomChrome(
    visible: Boolean,
    content: @Composable () -> Unit
) {
    val reading = BookVoiceDesign.reading
    val slidePx = with(LocalDensity.current) { reading.chromeSlideDistance.roundToPx() }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(reading.chromeEnterDurationMs)) +
            slideInVertically(
                animationSpec = tween(reading.chromeEnterDurationMs),
                initialOffsetY = { slidePx }
            ),
        exit = fadeOut(tween(reading.chromeExitDurationMs)) +
            slideOutVertically(
                animationSpec = tween(reading.chromeExitDurationMs),
                targetOffsetY = { slidePx }
            )
    ) {
        content()
    }
}



@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FocusParagraph(
    text: String,
    chapterIdx: Int,
    blockIdx: Int,
    highlight: io.github.mungbean2435.bookvoice.playback.Highlight?,
    fontSize: TextUnit,
    isCurrent: Boolean
) {
    val reading = BookVoiceDesign.reading
    val mark = highlight?.takeIf { it.chapterIdx == chapterIdx && it.blockIdx == blockIdx }
    val rendered = if (
        mark != null &&
        mark.start in 0 until text.length &&
        mark.end in (mark.start + 1)..text.length
    ) {
        buildAnnotatedString {
            append(text)
            addStyle(
                SpanStyle(
                    background = MaterialTheme.colorScheme.primary.copy(
                        alpha = reading.sentenceHighlightAlpha
                    )
                ),
                mark.start,
                mark.end
            )
        }
    } else {
        AnnotatedString(text)
    }

    Text(
        text = rendered,
        modifier = Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.bodyLarge.copy(
            fontSize = fontSize,
            lineHeight = (fontSize.value * reading.lineHeightMultiplier).sp
        ),
        color = if (isCurrent) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        fontWeight = if (isCurrent) FontWeight.Medium else FontWeight.Normal,
        textAlign = TextAlign.Start,
        overflow = TextOverflow.Ellipsis
    )
}

/** 首屏一次取回的数据；IO 已经在后台做完，剩下只是建行。 */
private data class InitialPlayerData(
    val book: Book?,
    val chapters: List<Chapter>,
    val blockCounts: Map<Int, Int>,
    val images: List<ChapterImage>,
    val startBlocks: List<Block>
)

/**
 * 点书 → 首帧正文可见的耗时打点，一次打开只报一次。
 * 两次 withFrameNanos 等骨架撤掉后的首帧真正上屏。
 */
private suspend fun reportOpenTiming(
    timing: LongArray,
    chapters: List<Chapter>,
    layout: ChapterLayout?
) {
    if (timing[3] != 0L || timing[0] == 0L) return
    timing[3] = 1L
    withFrameNanos { }
    withFrameNanos { }
    val total = (System.nanoTime() - timing[0]) / 1_000_000
    Metrics.lastOpenMs = total
    val chapterCount = layout?.chapterIdxs?.size ?: chapters.size
    val blockCount = layout?.blockCounts?.values?.sum() ?: 0
    Metrics.lastOpenBreakdown =
        "查询 ${timing[1]} / 构建 ${timing[2]} / 首帧 $total ms · " +
            "$chapterCount 章 $blockCount 段"
}

/** 首屏加载/定位期间的固定骨架：顶栏槽之外的 6 条灰块，替代全屏转圈。 */
@Composable
private fun ReaderSkeleton(reading: BookVoiceReadingTokens, fontSizeSp: Int) {
    val tokens = BookVoiceDesign.tokens
    val density = LocalDensity.current
    val lineHeight = with(density) {
        (fontSizeSp * reading.lineHeightMultiplier).sp.toDp()
    }
    val gap = with(density) {
        (fontSizeSp * reading.paragraphSpacingEm).sp.toDp()
    }
    val widths = listOf(1f, 0.94f, 0.88f, 1f, 0.9f, 0.62f)
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(
                start = reading.horizontalPadding,
                end = reading.horizontalPadding,
                top = reading.contentTopPadding
            )
    ) {
        widths.forEach { fraction ->
            Box(
                Modifier
                    .padding(bottom = gap + lineHeight * 0.6f)
                    .fillMaxWidth(fraction)
                    .height(lineHeight * 1.6f)
                    .clip(tokens.cardShape)
                    .background(
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)
                    )
            )
        }
    }
}

/** 正文还没拉到的段落占位：两行灰条，高度随字号走，滚动长度不会突变。 */
@Composable
private fun StubParagraph(bottomPadding: Dp) {
    val reading = BookVoiceDesign.reading
    val tokens = BookVoiceDesign.tokens
    val barColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
    val barHeight = bottomPadding * 2f
    val barGap = bottomPadding * 0.35f
    Column(
        Modifier
            .fillMaxWidth()
            .padding(
                start = reading.highlightBarWidth + reading.highlightBarGutter,
                end = 4.dp,
                bottom = bottomPadding
            )
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(barHeight)
                .clip(tokens.cardShape)
                .background(barColor)
        )
        Spacer(Modifier.height(barGap))
        Box(
            Modifier
                .fillMaxWidth(0.68f)
                .height(barHeight)
                .clip(tokens.cardShape)
                .background(barColor)
        )
    }
}

/** 点击分区在按下时写入，段落 onClick 只读取；不用 State，避免每次按下都重组。 */
private class TapZoneHolder {
    var zone: ReadingTapZone = ReadingTapZone.Center
}

/**
 * 浮层自己占住命中路径，但从不消费事件：
 * 按钮仍走自己的 clickable，面板空白处的点击也不会落到下面的正文。
 */
private fun Modifier.consumePanelTouches(): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        while (awaitPointerEvent().changes.any { it.pressed }) {
            // 只等手势结束，不改 consumed。
        }
    }
}
