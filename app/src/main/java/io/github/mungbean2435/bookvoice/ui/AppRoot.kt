package io.github.mungbean2435.bookvoice.ui

import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.mungbean2435.bookvoice.data.db.Book
import io.github.mungbean2435.bookvoice.data.settings.CurrentBook
import io.github.mungbean2435.bookvoice.data.settings.Metrics
import io.github.mungbean2435.bookvoice.playback.PlayerState
import io.github.mungbean2435.bookvoice.playback.Speech
import io.github.mungbean2435.bookvoice.ui.book.ChapterListScreen
import io.github.mungbean2435.bookvoice.ui.bookmarks.BookmarksScreen
import io.github.mungbean2435.bookvoice.ui.components.AppTab
import io.github.mungbean2435.bookvoice.ui.components.LocalFloatingChromeReserve
import io.github.mungbean2435.bookvoice.ui.components.MiniPlayer
import io.github.mungbean2435.bookvoice.ui.components.BookVoiceTabBar
import io.github.mungbean2435.bookvoice.ui.glass.GlassTier
import io.github.mungbean2435.bookvoice.ui.glass.LiquidGlassFrameWatcher
import io.github.mungbean2435.bookvoice.ui.glass.captureNavGlassBackdrop
import io.github.mungbean2435.bookvoice.ui.glass.rememberLiquidGlassAvailability
import io.github.mungbean2435.bookvoice.ui.glass.rememberNavGlassBackdrop
import io.github.mungbean2435.bookvoice.ui.glass.resolveGlassTier
import io.github.mungbean2435.bookvoice.ui.nav.AppSceneStates
import io.github.mungbean2435.bookvoice.ui.nav.BackAction
import io.github.mungbean2435.bookvoice.ui.nav.BackGate
import io.github.mungbean2435.bookvoice.ui.nav.BackPolicy
import io.github.mungbean2435.bookvoice.ui.nav.NavScene
import io.github.mungbean2435.bookvoice.ui.nav.pageSlideEnter
import io.github.mungbean2435.bookvoice.ui.nav.pageSlideExit
import io.github.mungbean2435.bookvoice.ui.nav.mainTabFade
import io.github.mungbean2435.bookvoice.ui.nav.rememberAppSceneStates
import io.github.mungbean2435.bookvoice.ui.morph.MorphOrigin
import kotlinx.coroutines.launch
import io.github.mungbean2435.bookvoice.ui.morph.ProgressRoundedShape
import io.github.mungbean2435.bookvoice.ui.morph.cornerRadiusPx
import io.github.mungbean2435.bookvoice.ui.morph.MorphOverlayHost
import io.github.mungbean2435.bookvoice.ui.player.rememberPlayerOverlayState
import io.github.mungbean2435.bookvoice.ui.settings.SettingsPage
import io.github.mungbean2435.bookvoice.ui.settings.SettingsScene
import io.github.mungbean2435.bookvoice.ui.settings.TtsConfigEditScreen
import io.github.mungbean2435.bookvoice.ui.shelf.ShelfScreen
import io.github.mungbean2435.bookvoice.ui.theme.AppearanceSettings
import io.github.mungbean2435.bookvoice.ui.theme.BookVoiceDesign
import io.github.mungbean2435.bookvoice.ui.theme.resolvedGlassTint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** 旧存档里可能还留着已删除的 "Player"，统一回落到书架。 */
private val AppTabSaver = Saver<AppTab, String>(
    save = { it.name },
    restore = { name ->
        runCatching { AppTab.valueOf(name) }.getOrDefault(AppTab.Shelf)
    }
)

/**
 * 自研 tab 宿主（没有引入 navigation-compose）：书架 / 书签 / 设置三个 tab。
 *
 * 层级：
 * - 主 tab：AnimatedContent 交叉淡化，不做横滑；
 * - 书架：点书直接打开阅读 overlay；没有中间详情页；
 * - 设置内部：SettingsScene 首页 → 子页，复用同一条可预测返回 transition；
 * - 播放页：不是路由，而是盖在宿主上的全屏 overlay，收起后原 tab 和列表位置都还在。
 *
 * 状态归属：
 * - 每个页面的 LazyListState / LazyGridState / TopAppBarState 都在这里 rememberSaveable，
 *   页面只接收，不销毁；
 * - 每个 tab 场景包 SaveableStateProvider(route)，相当于 NavHost 的 saveState/restoreState；
 * - 设置子页、播放 overlay、目录浮层标识也都保存在这里。
 *
 * 返回：箭头、系统返回、手势都走同一个 [dispatchBack]，由 [BackPolicy] 决定优先级。
 */
@Composable
fun AppRoot(vm: AppViewModel = viewModel()) {
    val scenes = rememberAppSceneStates()
    val sceneHolder = rememberSaveableStateHolder()
    val settingsPageHolder = rememberSaveableStateHolder()
    val backGate = remember { BackGate() }

    // —— 导航状态 ——
    var selectedTab by rememberSaveable(stateSaver = AppTabSaver) {
        mutableStateOf(AppTab.Shelf)
    }
    var settingsPage by rememberSaveable { mutableStateOf<String?>(null) }
    var playerBookId by rememberSaveable { mutableStateOf<String?>(null) }
    var playerChapter by rememberSaveable { mutableIntStateOf(0) }
    var overlayExpanded by rememberSaveable { mutableStateOf(false) }
    var chaptersBookId by rememberSaveable { mutableStateOf<String?>(null) }
    var chaptersCurrent by rememberSaveable { mutableIntStateOf(0) }
    var editorOpen by rememberSaveable { mutableStateOf(false) }
    var editorConfigId by rememberSaveable { mutableStateOf<String?>(null) }
    var editorTemplateId by rememberSaveable { mutableStateOf("") }

    // 跨 scene 页面（TTS 配置编辑）离开时记下列表 index/offset，回来再对一次。
    var editorAnchorIndex by rememberSaveable { mutableIntStateOf(0) }
    var editorAnchorOffset by rememberSaveable { mutableIntStateOf(0) }
    var editorAnchorValid by rememberSaveable { mutableStateOf(false) }

    var backProgress by remember { mutableFloatStateOf(0f) }
    var ready by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.awaitReady()
        // 冷启动停在书架，不再自动打开最近一本进入阅读页。
        // 旧存档 / 降级安装留下的未知子页一律回首页。
        if (!SettingsPage.isKnown(settingsPage)) settingsPage = null
        ready = true
    }

    val player = remember { Speech.get() }
    val playbackState by player.state.collectAsStateWithLifecycle()
    val playbackProgress by player.progress.collectAsStateWithLifecycle()
    val books by vm.books.collectAsStateWithLifecycle()
    val playingBookId by player.playingBookId.collectAsStateWithLifecycle()
    val savedBookId by CurrentBook.bookId.collectAsStateWithLifecycle()
    // 胶囊的主键：最近打开的书优先（打开哪本显示哪本），其次正在播放 / 本次会话播放过的书。
    // 不再依赖 PlayerState：冷启动没有播放时胶囊也常驻。
    val miniBookId = savedBookId?.takeIf { it.isNotBlank() }
        ?: playbackProgress?.bookId?.takeIf { it.isNotBlank() }
        ?: playingBookId.takeIf { it.isNotBlank() }
    val miniBook = books.firstOrNull { it.id == miniBookId }
    val miniVisible = miniBook != null
    // 直接播放另一本书（书签 / 目录里点播放）时也把胶囊换过去。
    LaunchedEffect(playingBookId) {
        if (playingBookId.isNotBlank()) CurrentBook.remember(playingBookId)
    }

    // —— 底部玻璃 ——
    val appearance by AppearanceSettings.state.collectAsStateWithLifecycle()
    val glassTint = resolvedGlassTint(
        followTheme = appearance.glassTintFollowTheme,
        argb = appearance.glassTint,
        primary = MaterialTheme.colorScheme.primary
    )
    val glassAvailability = rememberLiquidGlassAvailability()
    val glassBackdrop = rememberNavGlassBackdrop()
    val context = LocalContext.current
    var fallbackNotified by remember { mutableStateOf(false) }
    LaunchedEffect(appearance.liquidGlass) {
        if (appearance.liquidGlass) fallbackNotified = false
    }
    // 渲染失败 / 掉帧：本次会话把开关关掉，档位回到 2，不崩、不反复重试。
    val onLiquidFailure: () -> Unit = {
        if (!fallbackNotified && appearance.liquidGlass) {
            fallbackNotified = true
            AppearanceSettings.update { it.copy(liquidGlass = false) }
            Toast.makeText(
                context,
                "液态玻璃已回落到档 2：渲染失败或掉帧",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    val glassTier = resolveGlassTier(
        sdkInt = Build.VERSION.SDK_INT,
        liquidGlassEnabled = appearance.liquidGlass,
        liquidGlassAvailable = glassAvailability.available,
        fellBack = false
    )
    // —— 播放 overlay ——
    val overlay = rememberPlayerOverlayState()
    val motion = BookVoiceDesign.motion
    // 底图退场的圆角：createOutline 里才读进度，绘制阶段取值，不触发重组。
    val baseShape = remember(motion) {
        ProgressRoundedShape(motion.morphBaseCorner) { overlay.progress }
    }
    val tokens = BookVoiceDesign.tokens
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val screenHeightPx = with(density) {
        configuration.screenHeightDp.dp.toPx()
    }.coerceAtLeast(1f)

    // 从 mini player 上滑时先保留底部 chrome，避免 mini 在拖拽中途被移除。
    val chromeHosted = !editorOpen &&
        chaptersBookId == null &&
        (selectedTab == AppTab.Shelf ||
            selectedTab == AppTab.Bookmarks ||
            selectedTab == AppTab.Settings)
    // chrome 显隐只认一个布尔：阅读页是否已经铺满（p 到 1 且动画停了）。
    // 不在 progress 阈值上切显隐——那会让整棵 AppRoot 跟着每帧重组。
    val readerOpen by remember {
        derivedStateOf {
            !overlay.settled && overlay.expanded && !overlay.running && overlay.progress >= 0.999f
        }
    }
    val showChrome = chromeHosted && !readerOpen
    // 玻璃背板跟 chrome 一起开关：变形期间录整页会卡帧，p=1 时已经看不见了。
    val chromeGlassOn = showChrome
    LiquidGlassFrameWatcher(
        enabled = ready && glassTier == GlassTier.Liquid && chromeGlassOn,
        onDegraded = onLiquidFailure
    )

    // 底部胶囊的容器矩形（整颗 64dp，不是左侧 40dp 封面），由 MiniPlayer 每帧上报。
    var capsuleRect by remember { mutableStateOf(Rect.Zero) }
    val capsuleCardColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val currentMiniBook by rememberUpdatedState(miniBook)
    val currentCapsuleRect by rememberUpdatedState(capsuleRect)
    val scope = rememberCoroutineScope()

    val openPlayer: (String?, Int?, MorphOrigin?) -> Unit = { bookId, chapter, origin ->
        if (bookId != null) {
            // 只有换书才会重新走首屏加载，计时从这里开始。
            if (bookId != playerBookId) Metrics.markOpenStart()
            // 主页胶囊跟着「打开的那本书」走，冷启动也记得。
            CurrentBook.remember(bookId)
            // 起点必须在 overlayExpanded 之前落地，第一帧就画在胶囊/封面上。
            overlay.prepare(origin)
            playerBookId = bookId
            playerChapter = chapter ?: books.firstOrNull { it.id == bookId }?.lastChapterIdx ?: 0
            overlayExpanded = true
        }
    }

    // 胶囊入口（书架点书、点底部胶囊都用这一套）：
    // 切 currentBook → 空转两帧（胶囊按新书重排 + 阅读页首次组合）→ 读胶囊实时矩形 → 起跑。
    // 胶囊不可见 / 当前书不是被点的这本 / 矩形为空 → 不带起点，整页淡入。
    val openFromCapsule: (Book, MorphOrigin?) -> Unit = { book, fallbackOrigin ->
        scope.launch {
            // 书架列表里的 Book 可能是本次会话早期的快照，阅读进度已经变了；
            // 打开前只补读一次最新的进度，避免总是从第一章重新开始。
            val fresh = withContext(Dispatchers.IO) {
                runCatching { vm.library().book(book.id) }.getOrNull()
            } ?: book
            // 1) 先切 currentBook，胶囊的标题和封面立刻换成这本书。
            CurrentBook.remember(book.id)
            // 2) 这一轮标成未收尾：宿主会在 progress 还是 0 的时候就挂上阅读页，
            //    首次组合 + 排版（约 300ms）落在静止帧里，而不是弹簧中间。
            overlay.beginOpen()
            if (book.id != playerBookId) Metrics.markOpenStart()
            playerBookId = book.id
            playerChapter = fresh.lastChapterIdx
            // 3) 只空转一帧：这一帧里胶囊按新书重排、阅读页开始组合。
            withFrameNanos { }
            // 4) 起点优先用胶囊容器矩形（胶囊一直在 onGloballyPositioned 里维护它）；
            //    胶囊还没换到这本书也没关系——卡片本来就是从胶囊那儿长出来的，
            //    换不换书只影响被卡片盖住那一瞬的内容。
            //    没有胶囊（本次启动还没打开过书）才退到书架书卡的矩形。
            val rect = currentCapsuleRect
            val origin = if (!rect.isEmpty) {
                MorphOrigin(
                    bookId = book.id,
                    title = book.title,
                    cardRect = rect,
                    // 胶囊是 22dp 连续圆角，卡片从它长到 32dp。
                    cardCornerPx = tokens.miniPlayerShape.cornerRadiusPx(density),
                    cardColor = capsuleCardColor,
                    coverRect = Rect.Zero,
                    cover = null
                )
            } else {
                fallbackOrigin?.copy(bookId = book.id, title = book.title)
            }
            overlay.prepare(origin)
            // 阅读页内容还没画出来也不用等：卡片照着 spring 长，正文由内容层自己淡入。
            overlayExpanded = true
        }
    }

    // 收起动画真正结束后再摘掉 overlay，避免收起途中它的命中区域还盖着 tab。
    // 等 expanded 先变 true，防止刚点开时 settledCollapsed 还是初始值而误收起。
    LaunchedEffect(overlayExpanded, playerBookId) {
        if (!overlayExpanded) return@LaunchedEffect
        val bookId = playerBookId
        if (bookId == null) {
            overlayExpanded = false
            return@LaunchedEffect
        }
        overlay.expand()
        // 等这一轮变形真正收尾（回到 0 且没有动画在跑），包括「展开途中被返回手势收回」
        // 这种走不到 1 的情况——只等 expanded 会永远等不到，overlay 就摘不掉了。
        snapshotFlow { overlay.settled }.filter { it }.first()
        overlayExpanded = false
    }

    // 阅读页收起后刷新书架快照，章节进度/上次位置在下一次打开时才是新的。
    LaunchedEffect(overlayExpanded) {
        if (!overlayExpanded && playerBookId != null) vm.refresh()
    }

    fun selectTab(tab: AppTab) {
        if (tab != selectedTab) selectedTab = tab
    }

    val backAction = BackPolicy.resolve(
        editorOpen = editorOpen,
        chaptersOpen = chaptersBookId != null,
        playerOverlayOpen = overlayExpanded,
        onSettingsTab = selectedTab == AppTab.Settings,
        settingsPageOpen = settingsPage != null,
        onBookmarksTab = selectedTab == AppTab.Bookmarks
    )

    // 唯一返回入口：箭头、系统返回、手势都调它。
    // 动作在调用瞬间按当前状态重新判定，避免转场回调拿到过期快照。
    val dispatchBack: () -> Unit = {
        val action = BackPolicy.resolve(
            editorOpen = editorOpen,
            chaptersOpen = chaptersBookId != null,
            playerOverlayOpen = overlayExpanded,
            onSettingsTab = selectedTab == AppTab.Settings,
            settingsPageOpen = settingsPage != null,
            onBookmarksTab = selectedTab == AppTab.Bookmarks
        )
        if (action != BackAction.None && backGate.tryHandle()) {
            when (action) {
                BackAction.CloseChapters -> chaptersBookId = null
                BackAction.CollapsePlayer -> overlay.collapse()
                BackAction.SettingsToHome -> settingsPage = null
                BackAction.SettingsToShelf -> selectedTab = AppTab.Shelf
                BackAction.None -> Unit
            }
        }
    }

    // 宿主层自己处理的返回：播放页目录、设置首页 → 书架。
    // 设置子页由 SettingsScene 的 SeekableTransitionState 接管；
    // 播放页 overlay 由 PlayerOverlayHost 的 PredictiveBackHandler 接管。
    val hostedBackAction = backAction == BackAction.CloseChapters ||
        backAction == BackAction.SettingsToShelf
    PredictiveBackHandler(enabled = hostedBackAction) { progressFlow ->
        var completed = false
        try {
            progressFlow.collect { event -> backProgress = event.progress }
            completed = true
        } finally {
            backProgress = 0f
        }
        if (completed) dispatchBack()
    }

    // overlay 已开始收起、但宿主状态还没摘掉时没有自己的 PredictiveBackHandler，
    // 用普通 BackHandler 兜底，仍然走同一个 dispatchBack。
    val fallbackBackEnabled =
        backAction == BackAction.CollapsePlayer && !overlay.expanded
    BackHandler(enabled = fallbackBackEnabled) { dispatchBack() }

    // 从 TTS 配置编辑回来后，兜底恢复离开时的列表位置（宿主已保存，正常是 no-op）。
    LaunchedEffect(editorOpen) {
        if (!editorOpen && editorAnchorValid) {
            val list = scenes.settings.ttsList.list
            if (list.firstVisibleItemIndex != editorAnchorIndex ||
                list.firstVisibleItemScrollOffset != editorAnchorOffset
            ) {
                list.scrollToItem(editorAnchorIndex, editorAnchorOffset)
            }
            editorAnchorValid = false
        }
    }

    if (!ready) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val chromeReserve = if (miniVisible) 166.dp else 96.dp

    CompositionLocalProvider(
        // 常开：这个值一变，整棵 tab 内容都要重新排版（关到最后两成时会卡一帧）。
        LocalFloatingChromeReserve provides chromeReserve
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            // 页面内容一直画到屏幕底部，胶囊浮在上面；档 2/3 时把整页录进背板，
            // 胶囊只采样自己背后那一块，所以滚动时里的内容会跟着虚化。
            Box(
                Modifier
                    .fillMaxSize()
                    .then(
                        if (chromeGlassOn && glassTier != GlassTier.Solid) {
                            Modifier.captureNavGlassBackdrop(glassBackdrop)
                        } else {
                            Modifier
                        }
                    )
                    .graphicsLayer {
                        // 底图整块往里退：scale = 1 - 0.06p，圆角 = 28dp * p。
                        // 这两个值都在绘制阶段读 progress，动画期间不触发重组。
                        val p = overlay.progress
                        if (p > 0.001f) {
                            val scale = 1f - (1f - motion.morphBaseScale) * p
                            scaleX = scale
                            scaleY = scale
                            shape = baseShape
                            clip = true
                        }
                    }
                    .drawWithContent {
                        // p=1 时整页被阅读页盖住，这一层直接跳过绘制。
                        if (overlay.progress < 0.999f) drawContent()
                    }
                    .background(MaterialTheme.colorScheme.background)
            ) {
                // 返回手势的「下层页面」预览：设置子页由可 seek 的转场负责，
                // 这里只剩「设置首页 → 书架」这一层还用静态垫页。
                if (backProgress > 0f && backAction == BackAction.SettingsToShelf) {
                    BackPreview(
                        action = backAction,
                        vm = vm,
                        scenes = scenes,
                        settingsPageHolder = settingsPageHolder
                    )
                }

                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            if (backAction == BackAction.SettingsToShelf) {
                                translationX = backProgress * size.width * 0.30f
                                alpha = 1f - backProgress * 0.08f
                            }
                        }
                ) {
                    AnimatedContent(
                        targetState = selectedTab,
                        transitionSpec = { mainTabFade() },
                        label = "mainTab",
                        modifier = Modifier.fillMaxSize()
                    ) { tab ->
                        NavScene(active = tab == selectedTab) {
                            sceneHolder.SaveableStateProvider(tab.name) {
                                when (tab) {
                                    AppTab.Shelf -> ShelfScreen(
                                        vm = vm,
                                        state = scenes.shelf,
                                        onOpen = { book, origin -> openFromCapsule(book, origin) },
                                        onDelete = { bookId -> vm.delete(bookId) },
                                        onOpenSettings = { selectedTab = AppTab.Settings }
                                    )

                                    AppTab.Bookmarks -> BookmarksScreen(
                                        vm = vm,
                                        state = scenes.bookmarks,
                                        onOpen = { entry ->
                                            val mark = entry.bookmark
                                            player.playFrom(
                                                mark.bookId,
                                                mark.chapterIdx,
                                                mark.blockIdx,
                                                mark.offsetMs
                                                    .coerceIn(0L, Int.MAX_VALUE.toLong())
                                                    .toInt()
                                            )
                                            openPlayer(mark.bookId, mark.chapterIdx, null)
                                        }
                                    )

                                    AppTab.Settings -> SettingsScene(
                                        page = settingsPage,
                                        states = scenes.settings,
                                        pageHolder = settingsPageHolder,
                                        onBack = dispatchBack,
                                        backEnabled = !overlayExpanded,
                                        previewEnabled = appearance.backGesturePreview &&
                                            !overlayExpanded,
                                        onSeekedBack = {
                                            backGate.tryHandle()
                                            settingsPage = null
                                        },
                                        onOpenPage = { page -> settingsPage = page },
                                        onOpenTtsEditor = { configId, templateId ->
                                            editorAnchorIndex =
                                                scenes.settings.ttsList.list.firstVisibleItemIndex
                                            editorAnchorOffset =
                                                scenes.settings.ttsList.list.firstVisibleItemScrollOffset
                                            editorAnchorValid = true
                                            editorConfigId = configId
                                            editorTemplateId = templateId
                                            editorOpen = true
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 播放页不是路由：从 mini player 上滑 / 点开时，整页从底部推上来，
            // 盖住当前 tab；返回只收起这个 overlay，不改变 selectedTab。
            val overlayBookId = playerBookId
            // 收起动画真正结束后就卸掉 overlay：留着的挂载会吃掉整屏的触摸事件，
            // 也会在起点位置留一张静止的封面。
            if (overlayBookId != null && (overlayExpanded || overlay.dragging || !overlay.settled)) {
                MorphOverlayHost(
                    state = overlay,
                    vm = vm,
                    bookId = overlayBookId,
                    chapter = playerChapter,
                    backEnabled = chaptersBookId == null,
                    onBack = dispatchBack,
                    onOpenChapters = { chapter ->
                        chaptersBookId = overlayBookId
                        chaptersCurrent = chapter
                    },
                    onOpenSettings = {
                        overlay.collapse()
                        settingsPage = null
                        selectedTab = AppTab.Settings
                    },
                    onEditTtsConfig = { id ->
                        // 保留阅读页和播放抽屉：编辑完返回仍回到正在听的位置。
                        editorAnchorValid = false
                        editorConfigId = id
                        editorTemplateId = ""
                        editorOpen = true
                    }
                )
            }

            // 播放页目录浮层：放在 overlay 之上；拖动时目录右移露出正文。
            val openChaptersBook = chaptersBookId
            if (openChaptersBook != null) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .zIndex(30f)
                        .graphicsLayer {
                            translationX = backProgress * size.width * 0.30f
                            alpha = 1f - backProgress * 0.08f
                        }
                ) {
                    ChapterListScreen(
                        vm = vm,
                        bookId = openChaptersBook,
                        currentChapter = chaptersCurrent,
                        onBack = dispatchBack,
                        onSelect = { chapter ->
                            if (openChaptersBook != playerBookId) Metrics.markOpenStart()
                            playerBookId = openChaptersBook
                            playerChapter = chapter
                            chaptersBookId = null
                            // 选章后立刻退出也要记住位置；阅读页的防抖保存来不及执行。
                            scope.launch(Dispatchers.IO) {
                                runCatching {
                                    vm.library().saveProgress(openChaptersBook, chapter, 0, 0)
                                }
                            }
                        }
                    )
                }
            }

            // 跨 scene 的 TTS 配置编辑：全屏，自己的未保存确认负责返回。
            // 进出都和设置子页一样：从右滑入，返回时原路滑出。
            AnimatedVisibility(
                visible = editorOpen,
                enter = pageSlideEnter(),
                exit = pageSlideExit(),
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(40f)
            ) {
                TtsConfigEditScreen(
                    configId = editorConfigId,
                    templateId = editorTemplateId,
                    onBack = { editorOpen = false }
                )
            }

            // 底部 chrome 的显隐只跟 readerOpen 这个布尔走：淡入淡出 150ms，
            // 不在 progress 阈值上开关（那会让整棵 AppRoot 每帧重组）。
            AnimatedVisibility(
                visible = showChrome,
                enter = fadeIn(tween(150)),
                exit = fadeOut(tween(150)),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 16.dp)
                ) {
                    if (miniVisible) {
                        MiniPlayer(
                            book = miniBook,
                            onBounds = { capsuleRect = it },
                            onOpen = {
                                val current = miniBook
                                if (current != null) {
                                    // 点胶囊和点书架走同一套：起点永远是胶囊容器自己的矩形。
                                    openFromCapsule(current, null)
                                } else {
                                    openPlayer(null, null, null)
                                }
                            },
                            onStart = {
                                val current = miniBook
                                if (current != null) {
                                    playerBookId = current.id
                                    playerChapter = current.lastChapterIdx
                                    player.playFrom(
                                        current.id,
                                        current.lastChapterIdx,
                                        current.lastBlockIdx
                                    )
                                }
                            },
                            onPullStart = { origin ->
                                val current = miniBook
                                if (current != null) {
                                    // 从迷你条拉起还没组合过的播放页，同样要计时。
                                    if (playerBookId == null) Metrics.markOpenStart()
                                    overlay.prepare(origin)
                                    playerBookId = current.id
                                    playerChapter = current.lastChapterIdx
                                    overlay.beginDrag(0f)
                                }
                            },
                            onPull = { dy ->

                                if (overlay.dragging) {
                                    overlay.dragBy(-dy / screenHeightPx)
                                }
                            },
                            onPullEnd = {

                                if (overlay.dragging) {
                                    val expand = overlay.progress >= 0.5f
                                    overlay.settleDrag(expand)
                                    if (expand) overlayExpanded = true
                                }
                            }
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                    BookVoiceTabBar(
                        selected = selectedTab,
                        onSelect = { tab -> selectTab(tab) },
                        tier = glassTier,
                        backdrop = glassBackdrop,
                        onLiquidFailure = onLiquidFailure,
                        glassTint = glassTint
                    )
                }
            }
        }
    }
}

/**
 * 返回手势进行中的静态预览：设置首页下面垫书架。
 * 设置子页已经换成可 seek 的真实转场，不再走这里。
 * 回调全部为空，预览不参与交互。
 */
@Composable
private fun BackPreview(
    action: BackAction,
    vm: AppViewModel,
    scenes: AppSceneStates,
    @Suppress("UNUSED_PARAMETER") settingsPageHolder: SaveableStateHolder
) {
    when (action) {
        BackAction.SettingsToShelf -> ShelfScreen(
            vm = vm,
            state = scenes.shelf,
            onOpen = { _, _ -> },
            onDelete = {},
            onOpenSettings = {}
        )

        BackAction.SettingsToHome,
        BackAction.CloseChapters,
        BackAction.CollapsePlayer,
        BackAction.None -> Unit
    }
}
