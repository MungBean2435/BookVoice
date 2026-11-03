package io.github.mungbean2435.bookvoice.ui.components

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 浮层胶囊 + mini player 在页面内容区里占用的底部高度（不含 navigationBars inset：
 * 页面 Scaffold 已经吃掉系统栏）。页面内容一直画到屏幕底部，胶囊浮在上面；
 * 列表用这个值当 `contentPadding.bottom`，最后一项仍能滑到胶囊上方。
 * 听书全屏页为 0。
 */
val LocalFloatingChromeReserve = staticCompositionLocalOf<Dp> { 0.dp }
