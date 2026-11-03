package io.github.mungbean2435.bookvoice.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 两套视觉体系：Material 用常规圆角，Clean 用连续圆角。 */
enum class BookVoiceStyle {
    Material,
    Clean
}

/**
 * 页面共享的几何、间距、描边与动效参数。
 *
 * 这些值只描述外观，不承载播放/数据状态；切换风格时整份替换即可。
 */
@Immutable
data class BookVoiceTokens(
    val style: BookVoiceStyle,
    val cardShape: Shape,
    val groupShape: Shape,
    val controlShape: Shape,
    val chipShape: Shape,
    val coverSmallShape: Shape,
    val coverLargeShape: Shape,
    val dialogShape: Shape,
    val sheetShape: Shape,
    val menuShape: Shape,
    val miniPlayerShape: Shape,
    val pageHorizontalPadding: Dp,
    val sectionSpacing: Dp,
    val cardPadding: Dp,
    val gridGap: Dp,
    val gridMinCellWidth: Dp,
    val listRowMinHeight: Dp,
    val touchTargetMin: Dp,
    val miniPlayerHeight: Dp,
    val miniPlayerSidePadding: Dp,
    val miniPlayerBottomPadding: Dp,
    val miniPlayerReserve: Dp,
    val contentBottomPadding: Dp,
    val tabBarHeight: Dp,
    val tabBarSidePadding: Dp,
    val tabBarBottomPadding: Dp,
    val appBarExpandedHeight: Dp,
    val appBarCollapsedHeight: Dp,
    val segmentedTrackHeight: Dp,
    val segmentedInset: Dp,
    val backupTileMinHeight: Dp,
    val backupTileHorizontalPadding: Dp,
    val backupTileVerticalPadding: Dp,
    val hairlineAlpha: Float,
    val pressInset: Dp,
    val pressMaxShrink: Float,
    val pressHighlightAlpha: Float,
    val glassStrokeWidth: Dp,
    val glassStrokeAlpha: Float,
    val glassAlphaWithCustomBackground: Float,
    val continuousCorners: Boolean,
    val pressScaleEnabled: Boolean,
    val reading: BookVoiceReadingTokens = BookVoiceReadingTokens(),
    val motion: BookVoiceMotionTokens = BookVoiceMotionTokens()
) {
    val isClean: Boolean get() = style == BookVoiceStyle.Clean
}

/**
 * 阅读页专用几何：正文边距、行高、段距、边缘翻页区和浮层动效。
 *
 * 顶栏是固定高度的布局行：收起态显示小号章节名和右侧状态，展开态是操作行；
 * 两种状态同高，正文从它下面开始，所以显隐不改变正文可用高度，也不会滚到
 * 章节名下面。正文自身只留 8dp 顶部呼吸。
 */
@Immutable
data class BookVoiceReadingTokens(
    val horizontalPadding: Dp = 20.dp,
    val topChromeHeight: Dp = 48.dp,
    val contentTopPadding: Dp = 8.dp,
    val contentBottomPadding: Dp = 32.dp,
    val lineHeightMultiplier: Float = 1.7f,
    val paragraphSpacingEm: Float = 0.6f,
    val edgeZoneFraction: Float = 0.18f,
    val swipeThreshold: Dp = 24.dp,
    val pageTurnDurationMs: Int = 260,
    val highlightBarWidth: Dp = 3.dp,
    val highlightBarGutter: Dp = 9.dp,
    val highlightAlpha: Float = 0.06f,
    val sentenceHighlightAlpha: Float = 0.18f,
    val chromeSlideDistance: Dp = 12.dp,
    val chromeEnterDurationMs: Int = 240,
    val chromeExitDurationMs: Int = 240,
    val chromeVerticalPadding: Dp = 8.dp,
    val chromeElevation: Dp = 8.dp,
    val miniBarProgressHeight: Dp = 3.dp,
    val playbackSheetHeightFraction: Float = 0.56f
)

/**
 * 动效数值集中在这里，页面不写魔法数字。
 *
 * spring 参数按 Compose 的阻尼/刚度表示：noBouncy = 1f，
 * mediumLow = 400f。
 */
@Immutable
data class BookVoiceMotionTokens(
    val pageEnterDurationMs: Int = 300,
    val pageExitDurationMs: Int = 160,
    val pageEnterScale: Float = 0.98f,
    val pageExitScale: Float = 0.99f,
    val pageSpringDamping: Float = 1f,
    val pageSpringStiffness: Float = 400f,
    val revealExpandDurationMs: Int = 640,
    val revealCollapseDurationMs: Int = 560,
    val themeIconRotationDurationMs: Int = 620,
    val themeIconRotationDegrees: Float = 56f,
    val segmentedIndicatorDurationMs: Int = 360,
    val statusEnterDurationMs: Int = 300,
    val statusExitDurationMs: Int = 250,
    val pressInDamping: Float = 1f,
    val pressInStiffness: Float = 1400f,
    val pressOutDamping: Float = 0.62f,
    val pressOutStiffness: Float = 420f,

    // —— 容器变形（胶囊 / 书架 → 阅读页，同一个 0..1 进度）——
    val morphSpringDamping: Float = 0.9f,
    val morphSpringStiffness: Float = 260f,
    val morphCloseMs: Int = 250,
    /** 卡片长到全屏时的圆角。 */
    val morphCardCorner: Dp = 32.dp,
    /** 底层（tab 内容 + 底部 chrome）在变形中缩到的比例。 */
    val morphBaseScale: Float = 0.94f,
    /** 底层缩放时的圆角，随进度 0 → 28dp。 */
    val morphBaseCorner: Dp = 28.dp,
    /** 返回手势最多把阅读页推回 40%。 */
    val morphBackFactor: Float = 0.4f,
    /** 关闭时卡片最后 20% 先淡出，让胶囊 / 书架透出来。 */
    val morphDismissFade: Float = 0.2f
) {
    val pageEnterEasing: Easing
        get() = CubicBezierEasing(0f, 0f, 0f, 1f)

    val pageSpring: SpringSpec<Float>
        get() = spring(dampingRatio = pageSpringDamping, stiffness = pageSpringStiffness)

    val morphSpring: SpringSpec<Float>
        get() = spring(dampingRatio = morphSpringDamping, stiffness = morphSpringStiffness)

    val pressInSpring: SpringSpec<Float>
        get() = spring(dampingRatio = pressInDamping, stiffness = pressInStiffness)

    val pressOutSpring: SpringSpec<Float>
        get() = spring(dampingRatio = pressOutDamping, stiffness = pressOutStiffness)
}

internal val MaterialDesignTokens = BookVoiceTokens(
    style = BookVoiceStyle.Material,
    cardShape = RoundedCornerShape(16.dp),
    groupShape = RoundedCornerShape(24.dp),
    controlShape = RoundedCornerShape(16.dp),
    chipShape = RoundedCornerShape(12.dp),
    coverSmallShape = RoundedCornerShape(10.dp),
    coverLargeShape = RoundedCornerShape(16.dp),
    dialogShape = RoundedCornerShape(28.dp),
    sheetShape = RoundedCornerShape(
        topStart = 28.dp,
        topEnd = 28.dp,
        bottomEnd = 0.dp,
        bottomStart = 0.dp
    ),
    menuShape = RoundedCornerShape(16.dp),
    miniPlayerShape = RoundedCornerShape(22.dp),
    pageHorizontalPadding = 16.dp,
    sectionSpacing = 20.dp,
    cardPadding = 16.dp,
    gridGap = 12.dp,
    gridMinCellWidth = 150.dp,
    listRowMinHeight = 72.dp,
    touchTargetMin = 48.dp,
    miniPlayerHeight = 64.dp,
    miniPlayerSidePadding = 16.dp,
    miniPlayerBottomPadding = 6.dp,
    miniPlayerReserve = 86.dp,
    contentBottomPadding = 102.dp,
    tabBarHeight = 64.dp,
    tabBarSidePadding = 16.dp,
    tabBarBottomPadding = 16.dp,
    appBarExpandedHeight = 152.dp,
    appBarCollapsedHeight = 64.dp,
    segmentedTrackHeight = 48.dp,
    segmentedInset = 4.dp,
    backupTileMinHeight = 76.dp,
    backupTileHorizontalPadding = 14.dp,
    backupTileVerticalPadding = 12.dp,
    hairlineAlpha = 0f,
    pressInset = 3.dp,
    pressMaxShrink = 0.06f,
    pressHighlightAlpha = 0.07f,
    glassStrokeWidth = 0.5.dp,
    glassStrokeAlpha = 0.08f,
    glassAlphaWithCustomBackground = 0.72f,
    continuousCorners = false,
    pressScaleEnabled = false
)

internal val CleanDesignTokens = BookVoiceTokens(
    style = BookVoiceStyle.Clean,
    cardShape = ContinuousRoundedCornerShape(16.dp),
    groupShape = ContinuousRoundedCornerShape(24.dp),
    controlShape = CircleShape,
    chipShape = CircleShape,
    coverSmallShape = ContinuousRoundedCornerShape(10.dp),
    coverLargeShape = ContinuousRoundedCornerShape(18.dp),
    dialogShape = ContinuousRoundedCornerShape(28.dp),
    sheetShape = ContinuousRoundedCornerShape(
        topStart = 28.dp,
        topEnd = 28.dp,
        bottomEnd = 0.dp,
        bottomStart = 0.dp
    ),
    menuShape = ContinuousRoundedCornerShape(18.dp),
    miniPlayerShape = ContinuousRoundedCornerShape(22.dp),
    pageHorizontalPadding = 20.dp,
    sectionSpacing = 28.dp,
    cardPadding = 16.dp,
    gridGap = 12.dp,
    gridMinCellWidth = 150.dp,
    listRowMinHeight = 72.dp,
    touchTargetMin = 48.dp,
    miniPlayerHeight = 64.dp,
    miniPlayerSidePadding = 16.dp,
    miniPlayerBottomPadding = 6.dp,
    miniPlayerReserve = 86.dp,
    contentBottomPadding = 102.dp,
    tabBarHeight = 64.dp,
    tabBarSidePadding = 16.dp,
    tabBarBottomPadding = 16.dp,
    appBarExpandedHeight = 152.dp,
    appBarCollapsedHeight = 64.dp,
    segmentedTrackHeight = 48.dp,
    segmentedInset = 4.dp,
    backupTileMinHeight = 76.dp,
    backupTileHorizontalPadding = 14.dp,
    backupTileVerticalPadding = 12.dp,
    hairlineAlpha = 0.06f,
    pressInset = 3.dp,
    pressMaxShrink = 0.06f,
    pressHighlightAlpha = 0.07f,
    glassStrokeWidth = 0.5.dp,
    glassStrokeAlpha = 0.08f,
    glassAlphaWithCustomBackground = 0.72f,
    continuousCorners = true,
    pressScaleEnabled = true
)

/** 主题根节点下发当前风格；未包主题时先用 Material 兜底。 */
val LocalBookVoiceTokens = staticCompositionLocalOf<BookVoiceTokens> { MaterialDesignTokens }

/** 有自定义背景图时，玻璃兜底色需要降低不透明度。 */
val LocalHasCustomBackground = staticCompositionLocalOf { false }

/** 页面读取令牌的入口，避免在调用点写风格判断。 */
object BookVoiceDesign {
    val tokens: BookVoiceTokens
        @Composable
        @ReadOnlyComposable
        get() = LocalBookVoiceTokens.current

    val isClean: Boolean
        @Composable
        @ReadOnlyComposable
        get() = LocalBookVoiceTokens.current.isClean

    val reading: BookVoiceReadingTokens
        @Composable
        @ReadOnlyComposable
        get() = LocalBookVoiceTokens.current.reading

    val motion: BookVoiceMotionTokens
        @Composable
        @ReadOnlyComposable
        get() = LocalBookVoiceTokens.current.motion
}
