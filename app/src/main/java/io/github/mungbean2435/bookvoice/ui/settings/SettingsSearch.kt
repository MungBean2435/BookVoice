package io.github.mungbean2435.bookvoice.ui.settings

/** 设置搜索的纯函数，便于单测；空查询匹配全部。 */
internal fun matchesSettingsQuery(
    query: String,
    title: String,
    subtitle: String?,
    keywords: String = ""
): Boolean {
    val q = query.trim()
    if (q.isEmpty()) return true
    return listOf(title, subtitle.orEmpty(), keywords).any {
        it.contains(q, ignoreCase = true)
    }
}
