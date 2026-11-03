package io.github.mungbean2435.bookvoice.ui.nav

/**
 * 返回键动作。箭头、系统返回、返回手势共用 [BackPolicy.resolve] 这一个决策函数，
 * 不允许某个入口自己再写一套 pop 逻辑。
 */
enum class BackAction {
    /** 交给系统默认处理（退出 / 进后台）。 */
    None,

    /** 关掉播放页的目录浮层，回到播放页。 */
    CloseChapters,

    /** 收起全屏播放 overlay，回到打开前的 tab。 */
    CollapsePlayer,

    /** 设置子页 → 设置首页。 */
    SettingsToHome,

    /** 设置首页 / 书签页 → 书架（主页）tab，不退出 App。 */
    SettingsToShelf
}

/**
 * 返回优先级，从上到下：
 * 1. TTS 配置编辑页自己处理（它有未保存确认，且是跨 scene 的全屏页）；
 * 2. 播放页目录浮层：关掉浮层；
 * 3. 播放页 overlay：收起；
 * 4. 设置子页：回设置首页；
 * 5. 设置首页、书签页：切回书架（主页）tab，不退出 App；
 * 6. 其它：系统默认。
 *
 * 纯函数，方便单测覆盖所有组合。
 */
object BackPolicy {
    fun resolve(
        editorOpen: Boolean,
        chaptersOpen: Boolean,
        playerOverlayOpen: Boolean,
        onSettingsTab: Boolean,
        settingsPageOpen: Boolean,
        onBookmarksTab: Boolean = false
    ): BackAction = when {
        editorOpen -> BackAction.None
        chaptersOpen -> BackAction.CloseChapters
        playerOverlayOpen -> BackAction.CollapsePlayer
        onSettingsTab && settingsPageOpen -> BackAction.SettingsToHome
        onSettingsTab -> BackAction.SettingsToShelf
        onBookmarksTab -> BackAction.SettingsToShelf
        else -> BackAction.None
    }
}
