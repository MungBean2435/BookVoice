package io.github.mungbean2435.bookvoice.ui.morph

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import io.github.mungbean2435.bookvoice.ui.AppViewModel
import io.github.mungbean2435.bookvoice.ui.nav.NavScene
import io.github.mungbean2435.bookvoice.ui.player.PlayerOverlayState
import io.github.mungbean2435.bookvoice.ui.player.PlayerScreen
import io.github.mungbean2435.bookvoice.ui.theme.BookVoiceDesign
import kotlinx.coroutines.CancellationException

/** 底图压暗的最大不透明度。 */
private const val MASK_MAX_ALPHA = 0.16f

/** 内容淡入：p=0.15 开始，p=0.40 就完全实心。 */
private const val CONTENT_FADE_START = 0.15f
private const val CONTENT_FADE_LENGTH = 0.25f

/**
 * 阅读页 overlay：容器变形。
 *
 * 全程只有一条 0..1 进度 p：
 * - 卡片矩形每帧 lerp(胶囊矩形, 整屏, p)，圆角 22dp → 32dp；
 * - 底图整块缩到 1-0.06p、圆角 28dp*p，压暗 0.16p，p=1 时不画；
 * - 阅读页是卡片里的内容：左上角为原点，scale = card.width / screenWidth，
 *   translate = (card.left, card.top)，按卡片圆角裁形；
 * - 封面不飞行：它就是阅读页内容的一部分，靠左上角锚定。
 *
 * 所有 progress 读取都发生在 drawBehind / graphicsLayer 里（绘制阶段），
 * 组合阶段不读 p，所以动画期间不会重组阅读页。
 *
 * 没有起点快照（胶囊不在 / 换了另一本书 / 重建后矩形失效）→ 整页 150ms 淡入。
 */
@Composable
fun MorphOverlayHost(
    state: PlayerOverlayState,
    vm: AppViewModel,
    bookId: String,
    chapter: Int,
    backEnabled: Boolean,
    onBack: () -> Unit,
    onOpenChapters: (Int) -> Unit,
    onOpenSettings: () -> Unit,
    onEditTtsConfig: (String) -> Unit
) {
    val density = LocalDensity.current
    val motion = BookVoiceDesign.motion
    val background = MaterialTheme.colorScheme.background

    // 阅读页在 progress 还是 0 的空转帧里挂上：首次组合 + 排版（约 300ms）
    // 落在静止帧里，弹簧开始后不再中途卡顿。
    val readerMounted = !state.settled
    // 阅读页首帧没画出来时内容层保持透明，画出来再 150ms 淡入，避免半空卡片。
    val paintedAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (state.readerPainted) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(150),
        label = "readerPaintedAlpha"
    )
    // 正文等变形结束再组合：derivedStateOf 只在布尔翻转时触发重组，组合期不逐帧读 progress。
    val bodyReady by remember(state) {
        derivedStateOf {
            val origin = state.origin
            val known = origin != null && origin.bookId == bookId
            !known || (!state.running && state.progress >= 0.999f)
        }
    }
    val cardCornerEndPx = with(density) { motion.morphCardCorner.toPx() }
    val screenHeightPx = with(density) {
        LocalConfiguration.current.screenHeightDp.dp.toPx()
    }.coerceAtLeast(1f)

    // 只在 0.999 这一档翻一次，动画期间不重组整棵阅读页。
    val fullyExpanded by remember { derivedStateOf { state.progress >= 0.999f } }
    // 变形途中吃掉整屏事件（打开中 / 关闭中忽略重复点击），完全展开后交给阅读页。
    val blocked by remember {
        derivedStateOf { !state.settled && (state.progress < 0.999f || state.running) }
    }
    val blockedNow = rememberUpdatedState(blocked)

    Box(
        Modifier
            .fillMaxSize()
            .zIndex(20f)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (blockedNow.value) event.changes.forEach { it.consume() }
                        }
                    }
                }
                .drawBehind {
                    val p = state.progress
                    if (p <= 0.001f || p >= 0.999f) return@drawBehind
                    // 遮罩：底图随卡片长起来一起压暗。
                    drawRect(Color.Black, alpha = MASK_MAX_ALPHA * p)
                    // 没有起点就不画卡片，交给整页淡入。
                    val origin = state.origin ?: return@drawBehind
                    if (origin.bookId != bookId) return@drawBehind
                    val card = lerpRect(origin.cardRect, Rect(0f, 0f, size.width, size.height), p)
                    if (card.width < 1f || card.height < 1f) return@drawBehind
                    val corner = origin.cardCornerPx + (cardCornerEndPx - origin.cardCornerPx) * p
                    // 关闭最后两成卡片先淡出，让胶囊 / 书架透出来。
                    val cardAlpha = if (state.dismissing) {
                        (p / motion.morphDismissFade).coerceIn(0f, 1f)
                    } else {
                        1f
                    }
                    drawRoundRect(
                        color = lerp(origin.cardColor, background, p).copy(alpha = cardAlpha),
                        topLeft = card.topLeft,
                        size = card.size,
                        cornerRadius = CornerRadius(corner)
                    )
                }
                .then(if (fullyExpanded) Modifier else Modifier.clearAndSetSemantics { })
        ) {
            // 阅读页单独一层：动画只改层的变换参数，不重画内容，低端机才有帧。
            Box(
                Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        if (!state.readerPainted) state.markReaderPainted()
                        drawContent()
                    }
                    .graphicsLayer {
                        val p = state.progress
                        val origin = state.origin
                        val known = origin != null && origin.bookId == bookId
                        when {
                            // 没有起点快照：整页淡入（150ms 由状态机的 tween 驱动）。
                            !known -> {
                                alpha = p * paintedAlpha
                                scaleX = 1f
                                scaleY = 1f
                                translationX = 0f
                                translationY = 0f
                                clip = false
                            }

                            // 起点 / 到头：归零，不做持续变换。
                            p <= 0.001f || p >= 0.999f -> {
                                alpha = if (p >= 0.999f) paintedAlpha else 0f
                                scaleX = 1f
                                scaleY = 1f
                                translationX = 0f
                                translationY = 0f
                                clip = false
                            }

                            else -> {
                                val card = lerpRect(
                                    origin!!.cardRect,
                                    Rect(0f, 0f, size.width, size.height),
                                    p
                                )
                                // 等比：宽度的比例同时给高度，内容不会被压扁。
                                val scale = (card.width / size.width).coerceIn(0.001f, 1f)
                                val corner = origin.cardCornerPx +
                                    (cardCornerEndPx - origin.cardCornerPx) * p
                                alpha = ((p - CONTENT_FADE_START) / CONTENT_FADE_LENGTH)
                                    .coerceIn(0f, 1f) * paintedAlpha
                                transformOrigin = TransformOrigin(0f, 0f)
                                scaleX = scale
                                scaleY = scale
                                translationX = card.left
                                translationY = card.top
                                // 裁成卡片形状：圆角换算回层的局部空间。
                                clip = alpha > 0.001f
                                if (clip) {
                                    shape = RoundedCornerShape((corner / scale).toDp())
                                }
                            }
                        }
                    }
            ) {
                if (readerMounted) {
                    NavScene(active = fullyExpanded) {
                        key(bookId, chapter) {
                            PlayerScreen(
                                vm = vm,
                                bookId = bookId,
                                startChapter = chapter,
                                bodyReady = bodyReady,
                                onBack = onBack,
                                onOpenChapters = onOpenChapters,
                                onOpenSettings = onOpenSettings,
                                onEditTtsConfig = onEditTtsConfig
                            )
                        }
                    }
                }
            }
        }

        // 顶部抓手：完全展开才出现；拖动中保留，否则手一按进度掉下 1 就没了。
        if (fullyExpanded || state.dragging) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 44.dp)
                    .size(width = 160.dp, height = 48.dp)
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragStart = { state.beginDrag(state.progress) },
                            onVerticalDrag = { change, amount ->
                                change.consume()
                                state.dragBy(-amount / screenHeightPx)
                            },
                            onDragEnd = { state.settleDrag(state.progress >= 0.5f) },
                            onDragCancel = { state.settleDrag(state.progress >= 0.5f) }
                        )
                    },
                contentAlignment = Alignment.TopCenter
            ) {
                Box(
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .size(width = 32.dp, height = 4.dp)
                        .background(
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.42f),
                            shape = CircleShape
                        )
                )
            }
        }
    }

    PredictiveBackHandler(enabled = backEnabled && state.expanded) { events ->
        try {
            state.beginDrag(state.progress)
            events.collect { event ->
                state.dragTo(backProgressToMorph(event.progress, motion.morphBackFactor))
            }
            state.settleDrag(false)
        } catch (cancelled: CancellationException) {
            // 用户中途松手取消：回到展开态，不关阅读页。
            state.settleDrag(true)
            throw cancelled
        }
    }
}
