package io.github.mungbean2435.bookvoice.ui.glass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned

/**
 * 底部导航的实时背板。
 *
 * 页面内容先录进一枚 [GraphicsLayer]，再原样画出来；胶囊自己在同样位置采样这一层，
 * 所以滚动列表、翻页、主题切换看到的都是当下内容，而不是一张背景图。
 *
 * 图层由 AppRoot 长期持有，切 tab 不会重新分配；坐标只在布局变化时更新。
 */
@Stable
class NavGlassBackdrop internal constructor(
    internal val layer: GraphicsLayer
) {
    internal var pageCoordinates: LayoutCoordinates? = null
    internal var barCoordinates: LayoutCoordinates? = null

    /** 胶囊左上角在页面快照坐标系里的位置；还没测量时返回 null（退回纯色）。 */
    fun barOffset(): Offset? {
        val page = pageCoordinates ?: return null
        val bar = barCoordinates ?: return null
        if (!page.isAttached || !bar.isAttached) return null
        return runCatching { page.localPositionOf(bar, Offset.Zero) }.getOrNull()
    }
}

@Composable
fun rememberNavGlassBackdrop(): NavGlassBackdrop {
    val layer = rememberGraphicsLayer()
    return remember(layer) { NavGlassBackdrop(layer) }
}

/**
 * 录下页面内容并立刻画出来。只挂在「显示底部胶囊」的场景上，
 * 听书全屏页没有胶囊，就不做这趟额外录制。
 */
fun Modifier.captureNavGlassBackdrop(backdrop: NavGlassBackdrop): Modifier = this
    .onGloballyPositioned { backdrop.pageCoordinates = it }
    .drawWithContent {
        backdrop.layer.record { this@drawWithContent.drawContent() }
        drawLayer(backdrop.layer)
    }
