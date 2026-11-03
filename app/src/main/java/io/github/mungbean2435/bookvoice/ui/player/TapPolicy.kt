package io.github.mungbean2435.bookvoice.ui.player

/** 单击屏幕时该做什么。 */
enum class TapAction {
    ShowChrome,
    HideChrome,
    ReadBlock
}

/**
 * 交互规则（只描述中部）：
 * - 控制栏隐藏时，点中部唤出控制栏；
 * - 控制栏显示时，点读开启则点正文中部从该段朗读，否则点中部收起控制栏。
 *
 * 左右各 18% 由阅读页先判定为翻页，不会进入这里；见 [readingTapZone]。
 */
fun tapAction(chromeVisible: Boolean, tapToRead: Boolean, onText: Boolean): TapAction = when {
    !chromeVisible -> TapAction.ShowChrome
    onText && tapToRead -> TapAction.ReadBlock
    else -> TapAction.HideChrome
}

/**
 * 中部的点击仍走 [tapAction]；左右边缘只负责翻页，不参与显隐/点读。
 *
 * `edgeFraction` 是每侧占屏宽的比例；宽度未知（0）或比例非法时一律按中部处理，
 * 避免把一次普通点击误判成翻页。
 */
enum class ReadingTapZone {
    LeftEdge,
    Center,
    RightEdge
}

fun readingTapZone(x: Float, widthPx: Int, edgeFraction: Float = 0.18f): ReadingTapZone {
    if (widthPx <= 0) return ReadingTapZone.Center
    val fraction = edgeFraction.coerceIn(0f, 0.5f)
    if (fraction <= 0f) return ReadingTapZone.Center
    val limit = widthPx * fraction
    return when {
        x < limit -> ReadingTapZone.LeftEdge
        x > widthPx - limit -> ReadingTapZone.RightEdge
        else -> ReadingTapZone.Center
    }
}

/**
 * 上下滑都收起浮层，方向不再区分；呼出只靠点击中部。
 *
 * 阈值按屏幕像素传入（默认 24dp），超过 `touchSlop` 后即可触发，
 * 不再要求 48dp 的长距离滑动。
 */
fun chromeSwipeHides(dy: Float, thresholdPx: Float): Boolean {
    if (thresholdPx <= 0f) return false
    return kotlin.math.abs(dy) > thresholdPx
}
