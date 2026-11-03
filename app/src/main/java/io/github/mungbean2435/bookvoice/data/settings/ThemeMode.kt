package io.github.mungbean2435.bookvoice.data.settings

/** 外观模式：跟随系统 / 浅色 / 深色。存 prefs 与备份时用 [prefValue]。 */
enum class ThemeMode(val prefValue: String) {
    FollowSystem("system"),
    Light("light"),
    Dark("dark");

    companion object {
        fun from(value: String?): ThemeMode =
            entries.firstOrNull { it.prefValue == value } ?: FollowSystem
    }
}
