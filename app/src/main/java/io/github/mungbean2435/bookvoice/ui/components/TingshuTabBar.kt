package io.github.mungbean2435.bookvoice.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mungbean2435.bookvoice.data.settings.AppLanguage
import io.github.mungbean2435.bookvoice.ui.glass.GlassTier
import io.github.mungbean2435.bookvoice.ui.glass.NavGlassBackdrop
import io.github.mungbean2435.bookvoice.ui.glass.glassTintAlpha
import io.github.mungbean2435.bookvoice.ui.onboarding.LocalAppLanguage
import io.github.mungbean2435.bookvoice.ui.glass.rememberNavGlassBackdrop
import io.github.mungbean2435.bookvoice.ui.glass.rememberNavGlassEffect
import io.github.mungbean2435.bookvoice.ui.theme.DefaultGlassTintArgb
import io.github.mungbean2435.bookvoice.ui.theme.glassContentTint
import io.github.mungbean2435.bookvoice.ui.theme.LocalHasCustomBackground
import io.github.mungbean2435.bookvoice.ui.theme.LocalBookVoiceDarkTheme
import io.github.mungbean2435.bookvoice.ui.theme.BookVoiceTheme
import kotlin.math.abs

/** 书签图标走本地 path，不引 material-icons-extended。 */
internal val BookmarkGlyph: ImageVector = ImageVector.Builder(
    name = "Bookmark",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f
).apply {
    path(fill = SolidColor(Color.Black)) {
        moveTo(6.5f, 3f)
        lineTo(17.5f, 3f)
        lineTo(17.5f, 21f)
        lineTo(12f, 16.6f)
        lineTo(6.5f, 21f)
        close()
    }
}.build()

/**
 * 底部导航的 tab。播放页是全屏 overlay，不是路由；
 * 胶囊只展示书架 / 书签 / 设置三项。
 */
enum class AppTab(
    val label: String,
    val labelEn: String,
    val icon: ImageVector
) {
    Shelf("书架", "Library", Icons.AutoMirrored.Filled.List),
    Bookmarks("书签", "Bookmarks", BookmarkGlyph),
    Settings("设置", "Settings", Icons.Filled.Settings);

    fun label(language: AppLanguage): String =
        if (language == AppLanguage.En) labelEn else label

    companion object {
        val navItems: List<AppTab> = listOf(Shelf, Bookmarks, Settings)
    }
}

private val BarHeight = 64.dp
private val RowInset = 4.dp

/** containLens = false 时允许镜头溢出胶囊的幅度。 */
private val LensOverflow = 5.dp

/**
 * 浮层胶囊底部导航。
 *
 * 视觉：64dp 高、左右 16dp（宿主给）、底部 16dp + navigationBars；50% 圆角、
 * 18dp 阴影、0.5dp 白色描边。
 *
 * 选中态不再是静态胶囊，而是一枚「镜头」：按下即涨起，拖动跟手并放大下方 tab，
 * 松手弹到最近的 tab 并切换。bar 内容先录进 GraphicsLayer，绘制时把镜头区域
 * 用 ClipOp.Difference 抠掉，镜头自己画一份放大、染色后的内容，所以不会重影。
 *
 * 玻璃：档 2 时在胶囊内部采样 [backdrop] 的页面快照并 28dp 模糊；
 * 档 3 在模糊层上叠加边缘折射。RenderEffect 只随尺寸/档位重建，滚动不重建。
 *
 * [containLens] = true（默认）时镜头任何一帧都被 clamp 在 bar 的 4dp 内边距里，
 * 放不下就压 scale；false 时允许约 5dp 溢出，给「涨起」留一点空间。
 */
@Composable
fun BookVoiceTabBar(
    selected: AppTab,
    onSelect: (AppTab) -> Unit,
    tier: GlassTier,
    backdrop: NavGlassBackdrop,
    onLiquidFailure: () -> Unit,
    glassTint: Color = Color(DefaultGlassTintArgb),
    containLens: Boolean = true,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val dark = LocalBookVoiceDarkTheme.current
    val language = LocalAppLanguage.current
    val density = LocalDensity.current
    val shape = RoundedCornerShape(percent = 50)
    val hasCustomBackground = LocalHasCustomBackground.current
    val strokeColor = if (dark) {
        Color.White.copy(alpha = 0.10f)
    } else {
        Color.White.copy(alpha = 0.70f)
    }

    var barSize by remember { mutableStateOf(IntSize.Zero) }
    val cornerRadiusPx = with(density) { (BarHeight / 2).toPx() }
    val blurRadiusPx = with(density) { 28.dp.toPx() }
    val effect = rememberNavGlassEffect(
        tier = tier,
        widthPx = barSize.width,
        heightPx = barSize.height,
        cornerRadiusPx = cornerRadiusPx,
        blurRadiusPx = blurRadiusPx,
        densityScale = density.density,
        onLiquidFailure = onLiquidFailure
    )
    val hasBackdrop = tier != GlassTier.Solid && effect != null
    val tintAlpha = if (tier != GlassTier.Solid && effect == null) {
        glassTintAlpha(GlassTier.Solid, dark, hasCustomBackground)
    } else {
        glassTintAlpha(tier, dark, hasCustomBackground)
    }

    val tabs = AppTab.navItems
    val selectedIndex = tabs.indexOf(selected).coerceAtLeast(0)
    val selectionTint = glassContentTint(glassTint, dark)
    val lens = remember { TabBarLensState() }
    val barContentLayer = rememberGraphicsLayer()
    val lensClipPath = remember { Path() }
    val haptics = LocalHapticFeedback.current

    val rowInsetPx = with(density) { RowInset.toPx() }
    val lensOverflowPx = with(density) { LensOverflow.toPx() }
    // 镜头 rect 相对 bar 的最小间距：containLens 时是 4dp，否则负值 = 允许溢出。
    val lensMarginPx = if (containLens) rowInsetPx else -lensOverflowPx
    // 优先用真实测量到的 tab 宽；第一次布局前退回等分，避免镜头尺寸为 0。
    val measuredTabWidthPx = lens.bounds(selectedIndex)?.width ?: 0f
    val tabWidthPx = if (measuredTabWidthPx > 0f) {
        measuredTabWidthPx
    } else {
        ((barSize.width - rowInsetPx * 2f) / tabs.size.toFloat()).coerceAtLeast(0f)
    }
    // 静止镜头 = tab rect 四周各内缩 4dp；高度基准是 barHeight - 8dp，
    // 不再拿 barHeight 直接乘缩放倍数。
    val lensRestWidthPx = (tabWidthPx - rowInsetPx * 2f).coerceAtLeast(0f)
    val lensRestHeightPx = (barSize.height - rowInsetPx * 2f).coerceAtLeast(0f)

    // 选中 tab 或布局变化时把镜头对准；拖动中不抢，松手由手势自己弹到最近 tab。
    LaunchedEffect(selectedIndex, lens.layoutVersion) {
        val target = lens.centerOf(selectedIndex) ?: return@LaunchedEffect
        when {
            lens.center.value.isNaN() -> {
                lens.markSettled(selectedIndex)
                lens.center.snapTo(target)
            }

            lens.settledIndex != selectedIndex && !lens.dragging -> {
                lens.markSettled(selectedIndex)
                lens.settleCenter(target, LensSlideSpec)
            }

            !lens.dragging && !lens.settling && abs(lens.center.value - target) > 0.5f -> {
                lens.center.snapTo(target)
            }
        }
    }

    // tab 数量变化时清掉旧坐标。
    SideEffect { lens.trimTabs(tabs.size) }

    // 外层只负责固定高度；镜头在它外面，bar 的 shadow/clip 不会裁掉溢出。
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(BarHeight)
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .shadow(
                    elevation = 18.dp,
                    shape = shape,
                    clip = false,
                    ambientColor = Color.Black.copy(alpha = 0.06f),
                    spotColor = Color.Black.copy(alpha = if (dark) 0.30f else 0.12f)
                )
                .onGloballyPositioned {
                    backdrop.barCoordinates = it
                    lens.attachBar(it)
                }
                .onSizeChanged { barSize = it }
        ) {
            // 背景层自己裁成胶囊；外层 bar 不裁，镜头才能溢出。
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(shape)
            ) {
                // 页面快照：只让胶囊区域看到它，模糊/折射都作用在这块内容上。
                if (hasBackdrop) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .graphicsLayer {
                                renderEffect = effect
                                compositingStrategy = CompositingStrategy.Offscreen
                            }
                    ) {
                        Canvas(Modifier.matchParentSize()) {
                            val offset = backdrop.barOffset() ?: return@Canvas
                            withTransform({ translate(-offset.x, -offset.y) }) {
                                drawLayer(backdrop.layer)
                            }
                        }
                    }
                }

                // 玻璃底色和用户选的颜色轻微混合：灰色能压掉动态色的蓝，
                // 自定义色也不只出现在镜头上。
                val barTint = lerp(colors.surfaceContainerLow, glassTint, 0.18f)
                Box(
                    Modifier
                        .matchParentSize()
                        .background(barTint.copy(alpha = tintAlpha))
                )

                // 档 3 的收边高光：只亮在玻璃边缘，不压在中间内容上。
                if (tier == GlassTier.Liquid) {
                    Canvas(Modifier.matchParentSize()) {
                        val corner = CornerRadius(size.height / 2f)
                        val stroke = 1.6.dp.toPx()
                        drawRoundRect(
                            color = Color.White.copy(alpha = 0.07f),
                            cornerRadius = corner,
                            style = Stroke(width = stroke)
                        )
                        drawRoundRect(
                            brush = Brush.verticalGradient(
                                0f to Color.White.copy(alpha = 0.16f),
                                0.42f to Color.Transparent,
                                1f to Color.White.copy(alpha = 0.10f)
                            ),
                            cornerRadius = corner,
                            style = Stroke(width = stroke)
                        )
                    }
                }
            }

            // 内容层：录一份完整 tab 行，然后把镜头区域抠掉再画；镜头自己画放大版。
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(shape)
                    .drawWithContent {
                        barContentLayer.record { this@drawWithContent.drawContent() }
                        // 抠洞与镜头 overlay 共用 lensFrame：同一帧里两者的 rect 必然一致。
                        val frame = lensFrame(
                            centerX = lens.center.value,
                            barWidthPx = size.width,
                            barHeightPx = size.height,
                            restWidthPx = lensRestWidthPx,
                            restHeightPx = lensRestHeightPx,
                            press = lens.press.value,
                            velocity = lens.velocity.value,
                            marginPx = lensMarginPx
                        )
                        if (frame == null) {
                            drawLayer(barContentLayer)
                        } else {
                            val rect = frame.rect
                            // squash 时镜头 layer 被横纵不等比缩放，圆角也跟着变椭圆；
                            // 抠洞按同样的横纵半径画，才不会在圆角处露一条缝。
                            val radiusY = rect.height / 2f
                            val radiusX = if (frame.scaleY > 0f) {
                                radiusY * (frame.scaleX / frame.scaleY)
                            } else {
                                radiusY
                            }
                            lensClipPath.reset()
                            lensClipPath.addRoundRect(
                                RoundRect(rect, CornerRadius(radiusX, radiusY))
                            )
                            clipPath(lensClipPath, ClipOp.Difference) {
                                drawLayer(barContentLayer)
                            }
                        }
                    }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .tabBarLensGesture(
                            state = lens,
                            selectedIndex = { selectedIndex },
                            onSelect = { index -> onSelect(tabs[index]) },
                            onHaptic = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            },
                            edgeInsetPx = rowInsetPx
                        )
                        .padding(horizontal = RowInset),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    tabs.forEachIndexed { index, tab ->
                        val isSelected = tab == selected
                        val tint =
                            if (isSelected) selectionTint else colors.onSurface.copy(alpha = 0.72f)
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .onGloballyPositioned { lens.registerTab(index, it) }
                                .semantics(mergeDescendants = true) {
                                    role = Role.Tab
                                    this.selected = isSelected
                                }
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    role = Role.Tab
                                ) {
                                    if (!isSelected) {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onSelect(tab)
                                    }
                                },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = null,
                                modifier = Modifier.size(21.dp),
                                tint = tint
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = tab.label(language),
                                color = tint,
                                fontSize = 10.5.sp,
                                lineHeight = 12.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Box(
                Modifier
                    .matchParentSize()
                    .border(width = 0.5.dp, color = strokeColor, shape = shape)
            )
        }

        // 镜头是独立 overlay：位置 = bar 坐标 + 胶囊 rect，允许溢出 bar。
        if (lensRestWidthPx > 0f && lensRestHeightPx > 0f) {
            TabBarLens(
                state = lens,
                contentLayer = barContentLayer,
                restWidthPx = lensRestWidthPx,
                restHeightPx = lensRestHeightPx,
                barWidthPx = barSize.width.toFloat(),
                barHeightPx = barSize.height.toFloat(),
                marginPx = lensMarginPx,
                glassColor = glassTint,
                contentTint = selectionTint,
                dark = dark,
                refractionEnabled = tier == GlassTier.Liquid,
                modifier = Modifier.align(AbsoluteAlignment.TopLeft)
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 96, name = "胶囊导航 · Solid")
@Composable
private fun BookVoiceTabBarPreview() {
    val backdrop = rememberNavGlassBackdrop()
    BookVoiceTheme(darkTheme = false, dynamicColor = false) {
        BookVoiceTabBar(
            selected = AppTab.Shelf,
            onSelect = {},
            tier = GlassTier.Solid,
            backdrop = backdrop,
            onLiquidFailure = {},
            modifier = Modifier.padding(16.dp)
        )
    }
}
