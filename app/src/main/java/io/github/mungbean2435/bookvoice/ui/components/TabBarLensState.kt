package io.github.mungbean2435.bookvoice.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.LayoutCoordinates
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

/** 越界最多能被拖出的距离：bar 宽度的 6%。 */
internal const val LensRubberBandFraction = 0.06f

/** 拖动松手后的回弹：略微过冲，读起来像液体落回槽里。 */
internal val LensSettleSpec: AnimationSpec<Float> =
    spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow)

/** 普通选中切换的滑动。 */
internal val LensSlideSpec: AnimationSpec<Float> = tween(260, easing = FastOutSlowInEasing)

/** 按下即涨起，不等长按。 */
private val PressInSpec: AnimationSpec<Float> =
    spring(dampingRatio = 1f, stiffness = 1000f)

private val PressOutSpec: AnimationSpec<Float> =
    spring(dampingRatio = 0.82f, stiffness = 420f)

/**
 * 底部导航的几何 + 手势状态。
 *
 * 每个 tab 的坐标在 bar 本地坐标系里注册（[registerTab]），镜头中心是一枚 [Animatable]：
 * 选中 tab 改变时滑过去，手指拖动时直接跟手，松手弹到最近的 tab。
 *
 * 这里只持有状态，不负责绘制；视觉层从 [center]、[press]、[velocity] 读值。
 */
@Stable
internal class TabBarLensState {
    internal var bar: LayoutCoordinates? = null
        private set

    private val tabCoordinates = HashMap<Int, LayoutCoordinates>()
    private val lastCenters = HashMap<Int, Float>()

    /** tab 出现或中心移动时自增，驱动「重新对准选中 tab」。 */
    var layoutVersion by mutableIntStateOf(0)
        private set

    /** 镜头中心的 bar 本地 x 坐标（px）。未测量时为 NaN。 */
    internal val center = Animatable(Float.NaN)

    /** 按下进度 0..1；驱动涨起、放大、阴影和折射强度。 */
    internal val press = Animatable(0f)

    /** 手指横向速度（px/s），松手后自然衰减，驱动 squash。 */
    internal val velocity = Animatable(0f)

    var dragging by mutableStateOf(false)
        private set

    /** 松手回弹 / 点选滑动正在进行，避免 LaunchedEffect 再插一脚。 */
    var settling by mutableStateOf(false)
        private set

    var hoveredIndex by mutableIntStateOf(-1)
        private set

    /** 上一次已经落位的 tab；用于区分「主动切 tab」和「只是重新布局」。 */
    var settledIndex = -1
        private set

    fun attachBar(coordinates: LayoutCoordinates) {
        val previousSize = bar?.size
        bar = coordinates
        if (previousSize != coordinates.size) layoutVersion++
    }

    fun registerTab(index: Int, coordinates: LayoutCoordinates) {
        tabCoordinates[index] = coordinates
        val current = bounds(index)?.center?.x ?: return
        if (lastCenters[index] != current) {
            lastCenters[index] = current
            layoutVersion++
        }
    }

    /** tab 数量变少时忘掉旧坐标，避免镜头被不存在的 tab 拖住。 */
    fun trimTabs(count: Int) {
        if (tabCoordinates.keys.none { it >= count }) return
        tabCoordinates.keys.removeAll { it >= count }
        lastCenters.keys.removeAll { it >= count }
        layoutVersion++
    }

    /** tab 在 bar 本地坐标系里的矩形；上层平移/缩放不会影响它。 */
    fun bounds(index: Int): Rect? {
        val host = bar ?: return null
        val tab = tabCoordinates[index] ?: return null
        if (!host.isAttached || !tab.isAttached) return null
        return runCatching { host.localBoundingBoxOf(tab, clipBounds = false) }.getOrNull()
    }

    fun centerOf(index: Int): Float? = bounds(index)?.center?.x

    /**
     * 首尾 tab 的中心，镜头横向活动的范围；[insetPx] 从两端各扣掉，
     * 让镜头在首/尾 tab 停下时也给胶囊留 4dp 内边距。
     */
    fun travelRange(insetPx: Float = 0f): ClosedFloatingPointRange<Float>? {
        val centers = tabCoordinates.keys.mapNotNull(::centerOf)
        if (centers.isEmpty()) return null
        val first = centers.min()
        val last = centers.max()
        val start = first + insetPx
        val end = last - insetPx
        // 极端窄 bar 下扣完会反转，退回原始范围。
        return if (start <= end) start..end else first..last
    }

    /** 离 [x] 最近的 tab；没有任何 tab 时返回 -1。 */
    fun nearestIndex(x: Float): Int {
        val centers = tabCoordinates.keys.mapNotNull { index ->
            centerOf(index)?.let { index to it }
        }
        return nearestIndexIn(centers, x)
    }

    /** 指针位置换算成带橡皮筋的镜头中心；[insetPx] 是活动范围两端要扣掉的内边距。 */
    fun rubberBandX(x: Float, barWidth: Float, insetPx: Float = 0f): Float {
        val range = travelRange(insetPx) ?: return x
        return rubberBandXIn(x, range.start, range.endInclusive, barWidth)
    }

    fun startDrag(hovered: Int) {
        dragging = true
        hoveredIndex = hovered
    }

    fun updateHovered(index: Int) {
        hoveredIndex = index
    }

    fun endDrag() {
        dragging = false
        hoveredIndex = -1
    }

    fun markSettled(index: Int) {
        settledIndex = index
    }

    /** 在 launch 回弹之前同步置位，避免 LaunchedEffect 抢先 snap 掉回弹。 */
    fun beginSettle() {
        settling = true
    }

    suspend fun holdPress() {
        press.animateTo(1f, PressInSpec)
    }

    suspend fun releasePress() {
        press.animateTo(0f, PressOutSpec)
    }

    suspend fun snapCenter(x: Float) {
        center.snapTo(x)
    }

    suspend fun settleCenter(target: Float, spec: AnimationSpec<Float> = LensSettleSpec) {
        settling = true
        try {
            center.animateTo(target, spec)
        } finally {
            settling = false
        }
    }

    /** 拖拽过程中手指每动一次就刷新速度，停下后自己衰减。 */
    suspend fun trackVelocity(vx: Float) {
        velocity.animateTo(vx, spring(dampingRatio = 1f, stiffness = 1400f))
        velocity.animateTo(0f, spring(dampingRatio = 0.9f, stiffness = 260f))
    }

    suspend fun relaxVelocity() {
        velocity.animateTo(0f, spring(dampingRatio = 0.5f, stiffness = 300f))
    }
}

/** 纯函数，便于单元测试：越界时最多拉出 bar 宽度的 6%。 */
internal fun rubberBandXIn(x: Float, start: Float, end: Float, barWidth: Float): Float {
    if (start > end) return x
    val overshoot = when {
        x < start -> x - start
        x > end -> x - end
        else -> return x
    }
    val limit = (barWidth * LensRubberBandFraction).coerceAtLeast(1f)
    val damped = limit * (1f - 1f / (abs(overshoot) / limit + 1f))
    val edge = if (overshoot < 0f) start else end
    return edge + damped * sign(overshoot)
}

/** 纯函数：最近中心。传入 (index, centerX) 而不是列表下标，避免调用方再映射一次。 */
internal fun nearestIndexIn(centers: List<Pair<Int, Float>>, x: Float): Int {
    if (centers.isEmpty()) return -1
    var bestIndex = centers[0].first
    var bestDistance = abs(centers[0].second - x)
    for (i in 1 until centers.size) {
        val distance = abs(centers[i].second - x)
        if (distance < bestDistance) {
            bestDistance = distance
            bestIndex = centers[i].first
        }
    }
    return bestIndex
}

/**
 * 按下拖动镜头的手势。挂在 tab 行上：
 * - down 立即涨起，不等长按；
 * - 横向超过 touchSlop 才算拖动，并在 Initial pass 消费事件，取消 tab 自己的点击；
 * - 普通轻点不消费，clickable 仍然收到 up 并切 tab；
 * - 拖动跨过 tab 中心时 haptic 一次；
 * - 松手落到最近的 tab 并回调切换。
 */
internal fun Modifier.tabBarLensGesture(
    state: TabBarLensState,
    selectedIndex: () -> Int,
    onSelect: (Int) -> Unit,
    onHaptic: () -> Unit,
    edgeInsetPx: Float
): Modifier = composed {
    val scope = rememberCoroutineScope()
    val latestSelected by rememberUpdatedState(selectedIndex)
    val latestSelect by rememberUpdatedState(onSelect)
    val latestHaptic by rememberUpdatedState(onHaptic)

    pointerInput(state, edgeInsetPx) {
        awaitEachGesture {
            val down = awaitFirstDown(
                requireUnconsumed = false,
                pass = PointerEventPass.Initial
            )
            scope.launch { state.holdPress() }
            val tracker = VelocityTracker()
            tracker.addPosition(down.uptimeMillis, down.position)

            var dragging = false
            var last = down
            var velocityJob: Job? = null

            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) {
                    last = change
                    if (dragging) change.consume()
                    break
                }

                val dx = change.position.x - down.position.x
                val dy = change.position.y - down.position.y
                if (!dragging &&
                    abs(dx) > viewConfiguration.touchSlop &&
                    abs(dx) > abs(dy)
                ) {
                    dragging = true
                    state.startDrag(state.nearestIndex(change.position.x))
                    scope.launch {
                        state.snapCenter(state.rubberBandX(change.position.x, size.width.toFloat(), edgeInsetPx))
                    }
                }

                if (dragging) {
                    change.consume()
                    tracker.addPosition(change.uptimeMillis, change.position)
                    scope.launch {
                        state.snapCenter(state.rubberBandX(change.position.x, size.width.toFloat(), edgeInsetPx))
                    }

                    val vx = tracker.calculateVelocity().x
                    velocityJob?.cancel()
                    velocityJob = scope.launch { state.trackVelocity(vx) }

                    val index = state.nearestIndex(change.position.x)
                    if (index >= 0 && index != state.hoveredIndex) {
                        state.updateHovered(index)
                        latestHaptic()
                    }
                }
                last = change
            }

            scope.launch { state.releasePress() }
            velocityJob?.cancel()
            scope.launch { state.relaxVelocity() }

            if (dragging) {
                val index = state.nearestIndex(last.position.x)
                state.endDrag()
                if (index >= 0) {
                    val target = state.centerOf(index)
                    state.markSettled(index)
                    if (target != null) {
                        state.beginSettle()
                        scope.launch { state.settleCenter(target) }
                    }
                    if (index != latestSelected()) latestSelect(index)
                }
            }
        }
    }
}
