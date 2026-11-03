package io.github.mungbean2435.bookvoice.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import io.github.mungbean2435.bookvoice.ui.morph.MorphOrigin
import io.github.mungbean2435.bookvoice.ui.theme.BookVoiceDesign
import io.github.mungbean2435.bookvoice.ui.theme.BookVoiceMotionTokens
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 阅读页 overlay 的进度控制器。
 *
 * 展开 / 收起不是路由，而是同一个 0..1 进度：
 * - 点胶囊左侧封面、点书架任意一本书：0 → 1（spring，启动前空转两帧）；
 * - 顶部抓手下滑、返回手势：1 → 0；
 * - 关闭 tween 250ms，返回手势期间把 backProgress 映射成 1 - back*0.4。
 *
 * 起点快照（[MorphOrigin]）在这里持有：关闭时沿着同一个起点缩回去，
 * 旋转/重建后快照丢失，宿主就退化成淡入。
 */
@Stable
class PlayerOverlayState internal constructor(
    private val scope: CoroutineScope,
    private val motion: BookVoiceMotionTokens
) {
    private val anim = Animatable(0f)

    /** 0 = 收起（贴着胶囊/书架封面），1 = 完全展开。 */
    val progress: Float get() = anim.value

    /** 本次变形的起点；null 表示只能淡入（书签入口 / 重建后矩形失效）。 */
    var origin: MorphOrigin? by mutableStateOf(null)
        private set

    /** 是否在关闭过程中；只有关闭才做“最后 20% 卡片先淡出”。 */
    var dismissing: Boolean by mutableStateOf(false)
        private set

    /** 目标是否为展开；拖动中保持 true，避免中途被当成“已收起”。 */
    val expanded: Boolean get() = anim.targetValue >= 0.5f

    val running: Boolean get() = anim.isRunning

    var dragging: Boolean by mutableStateOf(false)
        private set

    /**
     * 这一轮变形是否已经收尾（回到 0 且没有动画在跑）。
     * 宿主靠它判断什么时候把 overlay 摘掉；prepare() 会先置 false，
     * 这样「刚挂上、还没开始走」的那一帧不会被当成已经收起。
     */
    var settled: Boolean by mutableStateOf(true)

    /** 阅读页是否已经画出第一帧（用来判断「点击→首帧」是否超预算）。 */
    var readerPainted: Boolean by mutableStateOf(false)
        private set

    private var dragFraction = 0f
    private var dragJob: Job? = null
    private var settleJob: Job? = null

    /**
     * 打开前记下起点（在设置 overlayExpanded 之前调用，保证第一帧就画在起点上）。
     * 打开途中 / 拖动中不换起点：连点或多入口竞争时，这次变形从头到尾只认一个快照。
     */
    fun markReaderPainted() {
        if (!readerPainted) readerPainted = true
    }

    /** 首帧没能在两帧内画出来：直接退化成整页淡入，别让画面静止等着。 */
    fun degradeToFade() {
        origin = null
    }

    fun prepare(origin: MorphOrigin?) {
        if (dragging || (anim.isRunning && anim.targetValue == 1f)) return
        this.origin = origin
        settled = false
    }

    /**
     * 只把这一轮标成「未收尾」，好让宿主在 progress 还是 0 的空转帧里就把阅读页挂上。
     * 起点（胶囊的实时矩形）要等胶囊按新书重排完才读得到，所以先挂载、后 prepare。
     */
    fun beginOpen() {
        if (dragging || (anim.isRunning && anim.targetValue == 1f)) return
        dismissing = false
        settled = false
        readerPainted = false
    }

    fun expand() {
        if (dragging || anim.targetValue == 1f) return
        dismissing = false
        settled = false
        // 没有起点快照（胶囊不可见 / 换了另一本书）：整页淡入，短平快，不硬飞。
        val fadeOnly = origin == null
        settleJob?.cancel()
        settleJob = scope.launch {
            // 空转的两帧由调用方（AppRoot.openFromCapsule）在切完 currentBook、
            // 挂上阅读页之后走完，这里直接起跑，避免多等两帧。
            if (fadeOnly) {
                anim.animateTo(1f, tween(150, easing = FastOutSlowInEasing))
            } else {
                anim.animateTo(1f, motion.morphSpring)
            }
        }
    }

    fun collapse() {
        dragging = false
        dismissing = true
        settleJob?.cancel()
        settleJob = scope.launch {
            anim.animateTo(0f, tween(motion.morphCloseMs, easing = FastOutSlowInEasing))
            settled = true
        }
    }

    /** mini player 上拉 / 播放页抓手 / 返回手势开始拖动。 */
    fun beginDrag(from: Float) {
        if (dragging) return
        dragging = true
        dismissing = false
        settled = false
        dragFraction = from.coerceIn(0f, 1f)
        settleJob?.cancel()
        dragJob?.cancel()
        dragJob = scope.launch { anim.snapTo(dragFraction) }
    }

    fun dragTo(fraction: Float) {
        if (!dragging) return
        dragFraction = fraction.coerceIn(0f, 1f)
        dragJob?.cancel()
        dragJob = scope.launch { anim.snapTo(dragFraction) }
    }

    fun dragBy(delta: Float) {
        if (!dragging) return
        dragTo(dragFraction + delta)
    }

    /** 松手：过 50% 就展开，否则收回。 */
    fun settleDrag(expand: Boolean) {
        if (!dragging) return
        dragging = false
        dismissing = !expand
        dragJob?.cancel()
        settleJob?.cancel()
        settleJob = scope.launch {
            if (expand) {
                anim.animateTo(1f, motion.morphSpring)
            } else {
                anim.animateTo(0f, tween(motion.morphCloseMs, easing = FastOutSlowInEasing))
                settled = true
            }
        }
    }
}

@Composable
fun rememberPlayerOverlayState(): PlayerOverlayState {
    val scope = rememberCoroutineScope()
    val motion = BookVoiceDesign.motion
    return remember(scope, motion) {
        PlayerOverlayState(scope, motion)
    }
}
