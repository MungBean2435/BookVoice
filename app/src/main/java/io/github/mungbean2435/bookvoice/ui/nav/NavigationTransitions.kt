package io.github.mungbean2435.bookvoice.ui.nav

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.IntOffset

/**
 * 全 App 唯一的导航转场来源。
 *
 * 页面里不再写 enter/exitTransition；箭头、系统返回、可预测返回都复用同一条
 * ContentTransform，避免一层两套动画。
 *
 * 数值与 NeriPlayer 的层级节奏对齐：tab 只淡化、设置子页手机横滑、平板淡入缩放。
 * 所有转场都套 SizeTransform(clip = true)，转场中间不露父容器底色。
 */

/** tab 交叉淡化：进 180ms、退 120ms。 */
internal const val TAB_FADE_IN_MILLIS = 180
internal const val TAB_FADE_OUT_MILLIS = 120

/** 设置子页手机横滑用 spring；这个名义时长只用于可预测返回的进度换算。 */
internal const val SETTINGS_PHONE_SETTLE_MILLIS = 300

internal const val SETTINGS_TABLET_ENTER_MILLIS = 300
internal const val SETTINGS_TABLET_EXIT_MILLIS = 160

private val TabEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
private val SettingsEnterEasing = CubicBezierEasing(0f, 0f, 0f, 1f)
private val SettingsExitEasing = CubicBezierEasing(0.3f, 0f, 1f, 1f)
private const val SETTINGS_ENTER_SCALE = 0.98f
private const val SETTINGS_EXIT_SCALE = 0.99f

/** 主 tab 只做交叉淡化，不做横滑。 */
internal fun mainTabFade(): ContentTransform = ContentTransform(
    targetContentEnter = fadeIn(tween(TAB_FADE_IN_MILLIS, easing = TabEasing)),
    initialContentExit = fadeOut(tween(TAB_FADE_OUT_MILLIS, easing = TabEasing)),
    targetContentZIndex = 0f,
    sizeTransform = SizeTransform(clip = true)
)

/**
 * 设置子页转场。
 *
 * 手机：横滑 spring(noBouncy, mediumLow)；
 * 平板（≥600dp）：淡入 + 缩放，进 300ms / 0.98、退 160ms / 0.99。
 */
internal fun settingsPageTransform(wide: Boolean, forward: Boolean): ContentTransform {
    if (!wide) {
        val direction = if (forward) 1 else -1
        val spec = spring<IntOffset>(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        )
        return ContentTransform(
            targetContentEnter = slideInHorizontally(spec) { width -> direction * width },
            initialContentExit = slideOutHorizontally(spec) { width -> -direction * width },
            targetContentZIndex = 0f,
            sizeTransform = SizeTransform(clip = true)
        )
    }

    val enterSpec = tween<Float>(
        durationMillis = SETTINGS_TABLET_ENTER_MILLIS,
        easing = SettingsEnterEasing
    )
    val exitSpec = tween<Float>(
        durationMillis = SETTINGS_TABLET_EXIT_MILLIS,
        easing = SettingsExitEasing
    )
    return ContentTransform(
        targetContentEnter = fadeIn(enterSpec) + scaleIn(enterSpec, initialScale = SETTINGS_ENTER_SCALE),
        initialContentExit = fadeOut(exitSpec) + scaleOut(exitSpec, targetScale = SETTINGS_EXIT_SCALE),
        targetContentZIndex = 0f,
        sizeTransform = SizeTransform(clip = true)
    )
}

/**
 * 全屏子页（如 TTS 配置编辑）的「从右滑入 / 向右滑出」：与设置子页手机路径同一套 spring，
 * 进出方向一致，返回就是原路滑回去。
 */
internal fun pageSlideEnter(): EnterTransition =
    slideInHorizontally(
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        )
    ) { width -> width }

internal fun pageSlideExit(): ExitTransition =
    slideOutHorizontally(
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        )
    ) { width -> width }

/** 播放抽屉内子页（音色 / 定时 / 离线缓存）的横滑时长；高度同步用同一段时长，避免先滑完再跳高。 */
internal const val SHEET_PAGE_SLIDE_MILLIS = 300

/**
 * 播放抽屉内的子页转场：播放设置 ↔ 音色 / 定时 / 离线缓存。
 *
 * 抽屉只有一页宽，整页横滑会在两侧露出空底，所以只做四分之一宽的视差滑入，
 * 旧页反向让出十分之一宽；进出都带淡入淡出，高度由 SizeTransform 同步补间。
 * 缓动沿用设置子页的 [TabEasing]，进出方向由 [forward] 决定，返回就是原路滑回。
 */
internal fun playerSheetPageTransform(forward: Boolean): ContentTransform {
    val direction = if (forward) 1 else -1
    val slideSpec = tween<IntOffset>(SHEET_PAGE_SLIDE_MILLIS, easing = TabEasing)
    return ContentTransform(
        targetContentEnter = slideInHorizontally(slideSpec) { width ->
            direction * (width / 4)
        } + fadeIn(tween(durationMillis = 240, easing = TabEasing)),
        initialContentExit = slideOutHorizontally(slideSpec) { width ->
            -direction * (width / 10)
        } + fadeOut(tween(durationMillis = 160, easing = TabEasing)),
        // 新页叠在旧页上方，视差过程中不会从缝里看到底下的旧页。
        targetContentZIndex = 1f,
        sizeTransform = SizeTransform(clip = true) { _, _ ->
            tween(SHEET_PAGE_SLIDE_MILLIS, easing = TabEasing)
        }
    )
}

/**
 * 场景活跃态包装：转场中的旧页、被播放 overlay 盖住的 tab 一律不接收触摸，
 * 并从无障碍树移除，避免焦点跑到屏幕外。
 */
@Composable
fun NavScene(
    active: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .then(if (active) Modifier else Modifier.clearAndSetSemantics { })
            .pointerInput(active) {
                if (!active) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            event.changes.forEach { change -> change.consume() }
                        }
                    }
                }
            }
    ) {
        content()
    }
}
