@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package io.github.mungbean2435.bookvoice.ui.nav

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material3.TopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable

/**
 * 一个普通长列表页面的可保存状态：列表位置 + 大标题折叠进度。
 *
 * 全部由共享宿主（[AppRoot]）创建并持有，页面只接收、不 remember；
 * 这样页面被互斥分支换掉、tab 切走、旋转屏幕时状态都不会丢。
 */
@Stable
class PageChromeState(
    val list: LazyListState,
    val topBar: TopAppBarState
)

/** 书架是两列网格，单独一份。 */
@Stable
class ShelfSceneState(
    val grid: LazyGridState,
    val topBar: TopAppBarState
)

/**
 * 设置场景里每一页各自的状态。首页与子页互斥渲染，但状态都挂在宿主上，
 * 所以从子页返回时首页列表 index/offset 和折叠进度原样还在。
 */
@Stable
class SettingsPagesState(
    val home: PageChromeState,
    val appearance: PageChromeState,
    val backup: PageChromeState,
    val playback: PageChromeState,
    val storage: PageChromeState,
    val diagnostics: PageChromeState,
    val rules: PageChromeState,
    val ttsList: PageChromeState
)

/** 整个 App 的页面状态宿主。 */
@Stable
class AppSceneStates(
    val shelf: ShelfSceneState,
    val bookmarks: PageChromeState,
    val settings: SettingsPagesState
)

@Composable
fun rememberTopBarState(): TopAppBarState =
    rememberSaveable(saver = TopAppBarState.Saver) {
        TopAppBarState(
            initialHeightOffsetLimit = -Float.MAX_VALUE,
            initialHeightOffset = 0f,
            initialContentOffset = 0f
        )
    }

@Composable
fun rememberPageChromeState(): PageChromeState {
    val list = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val topBar = rememberTopBarState()
    return remember(list, topBar) { PageChromeState(list, topBar) }
}

/**
 * 在 [AppRoot] 里调用一次。每个 rememberSaveable 调用点位置固定，
 * 进程被杀后由 SavedStateRegistry 还原（至少 index/offset 与折叠进度）。
 */
@Composable
fun rememberAppSceneStates(): AppSceneStates {
    val shelf = ShelfSceneState(
        grid = rememberSaveable(saver = LazyGridState.Saver) { LazyGridState() },
        topBar = rememberTopBarState()
    )
    val bookmarks = rememberPageChromeState()
    val settings = SettingsPagesState(
        home = rememberPageChromeState(),
        appearance = rememberPageChromeState(),
        backup = rememberPageChromeState(),
        playback = rememberPageChromeState(),
        storage = rememberPageChromeState(),
        diagnostics = rememberPageChromeState(),
        rules = rememberPageChromeState(),
        ttsList = rememberPageChromeState()
    )
    return remember(shelf, bookmarks, settings) {
        AppSceneStates(shelf = shelf, bookmarks = bookmarks, settings = settings)
    }
}
