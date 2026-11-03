package io.github.mungbean2435.bookvoice.ui.nav

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlin.math.roundToInt

/** 手势取消时最短回弹时长，避免浅滑一下弹得太生硬。 */
internal const val MIN_BACK_CANCEL_MILLIS = 120

/** 取消回弹时长：按拖动进度等比例缩短，不写死一个固定值。 */
internal fun backCancelMillis(fraction: Float, totalMillis: Int): Int =
    maxOf(MIN_BACK_CANCEL_MILLIS, (fraction.coerceIn(0f, 1f) * totalMillis).roundToInt())

/** 一次拖动中的手势快照；[segment] 用来区分「同一次拖动」与「新的拖动」。 */
private class BackGesture<S>(
    val target: S,
    val progress: Float,
    val segment: Any,
    val prime: Boolean
)

/** 手势被取消后，把进度动画回 0 的请求。 */
private class BackCancel<S>(val restoreTarget: S, val durationMillis: Int)

/**
 * 把一趟「页面切换」做成可 seek 的 Transition：
 * - 点击导航时 [target] 变化，走 [settleSpecFor] 的普通动画；
 * - 系统返回手势拖动时，进度直接 seek 到同一个 Transition，松手后从当前进度继续；
 * - 取消时按 fraction × 总时长回弹，而不是从头重放。
 *
 * 调用契约：`onBack(seeked = true)` 之后，调用方必须**立刻**把 [target] 改成当前返回目标，
 * 否则这里会把已经拖到的进度当成误操作退回去。
 *
 * @param backTarget 返回手势会去往的状态；null 表示这一层没有可返回的页。
 * @param backEnabled 是否接管返回手势（上层浮层打开时要置 false）。
 * @param previewEnabled 关闭时吞掉进度，松手后走普通返回。
 * @param cancelTotalMillis 计算取消回弹时长用的总时长。
 * @param settleSpecFor 普通导航（含松手提交）时进度本身的动画规格。
 */
@Composable
internal fun <S> rememberSeekableBackTransition(
    target: S,
    backTarget: S?,
    backEnabled: Boolean,
    previewEnabled: Boolean,
    cancelTotalMillis: Int,
    label: String,
    settleSpecFor: (S) -> FiniteAnimationSpec<Float>,
    onBack: (seeked: Boolean) -> Unit
): Transition<S> {
    val transitionState = remember { SeekableTransitionState(target) }
    val transition = rememberTransition(transitionState, label = label)

    var gesture by remember { mutableStateOf<BackGesture<S>?>(null) }
    var cancelRequest by remember { mutableStateOf<BackCancel<S>?>(null) }
    // 已经发起过一次返回：转场收尾前的第二次返回一律忽略，避免重复 pop。
    var backIssued by remember { mutableStateOf(false) }

    val currentTarget by rememberUpdatedState(target)
    val currentBackTarget by rememberUpdatedState(backTarget)
    val currentPreview by rememberUpdatedState(previewEnabled)
    val currentOnBack by rememberUpdatedState(onBack)
    val currentSettleSpec by rememberUpdatedState(settleSpecFor)

    val activeSegment = gesture?.segment

    // 拖动中：先 seek 到 0 并空转两帧，让布局动画在零进度注册，避免第一帧跳。
    // 然后用 snapshotFlow 只追最新进度，旧的 seek 直接取消。
    LaunchedEffect(activeSegment) {
        val active = gesture ?: return@LaunchedEffect
        if (active.prime) {
            transitionState.seekTo(0f, active.target)
            withFrameNanos { }
            withFrameNanos { }
        }
        snapshotFlow { gesture?.takeIf { it.segment === active.segment }?.progress }
            .filterNotNull()
            .collectLatest { progress ->
                transitionState.seekTo(progress, active.target)
            }
    }

    // 非拖动状态：把 Transition 收尾到 target；取消手势时收尾到原页面并把进度弹回 0。
    LaunchedEffect(target, activeSegment, cancelRequest != null) {
        if (gesture != null) return@LaunchedEffect
        val cancel = cancelRequest
        val settleTo = cancel?.restoreTarget ?: target
        val spec = if (cancel != null) {
            tween<Float>(durationMillis = cancel.durationMillis, easing = LinearEasing)
        } else {
            currentSettleSpec(target)
        }
        if (transitionState.currentState != settleTo ||
            transitionState.targetState != settleTo ||
            transitionState.fraction > 0f
        ) {
            transitionState.animateTo(settleTo, spec)
        }
        if (cancel != null) cancelRequest = null
        if (gesture == null) backIssued = false
    }

    PredictiveBackHandler(enabled = backEnabled) { events ->
        val backTo = currentBackTarget
        val restore = currentTarget
        val idle = !transition.isRunning &&
            transitionState.currentState == restore &&
            transitionState.targetState == restore &&
            transitionState.fraction == 0f
        // 没有可返回目标、转场未结束、或已经发起过返回：吞掉这次手势。
        if (backTo == null || backIssued || !idle) {
            events.collect { }
            return@PredictiveBackHandler
        }

        val preview = currentPreview
        val segment = Any()
        var seeked = false
        var lastProgress = 0f
        // 手势过程中导航被别处改掉（切 tab、点箭头）：放弃这次 seek。
        var owned = true
        val primeAtStart = preview

        try {
            if (preview) {
                gesture = BackGesture(backTo, 0f, segment, primeAtStart)
            }
            events.collect { event ->
                if (!preview) return@collect
                if (currentTarget != restore) {
                    owned = false
                    return@collect
                }
                lastProgress = event.progress.coerceIn(0f, 1f)
                gesture = BackGesture(backTo, lastProgress, segment, false)
                seeked = true
            }

            if (preview && seeked && owned && lastProgress > 0f && currentTarget == restore) {
                // 先让 Transition 停在最新进度上（gesture 还在，收尾 effect 不会抢），
                // 再提交导航；这样动画从这一帧接着走，而不是从 0 重放。
                transitionState.seekTo(lastProgress, backTo)
                gesture = null
                backIssued = true
                currentOnBack(true)
            } else {
                gesture = null
                currentOnBack(false)
            }
        } catch (cancelled: CancellationException) {
            val fraction = transitionState.fraction
            gesture = null
            if (seeked && fraction > 0f) {
                cancelRequest = BackCancel(
                    restoreTarget = restore,
                    durationMillis = backCancelMillis(fraction, cancelTotalMillis)
                )
            }
            throw cancelled
        }
    }

    return transition
}
