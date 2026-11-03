package io.github.mungbean2435.bookvoice.data.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 记住「最近打开的那本书」。
 *
 * 主页底部的胶囊常驻显示这一本：冷启动先读书架，胶囊也还在；点开哪本书，
 * 胶囊就换成哪本。只存一个 id，书被删掉时宿主查不到就当作没有。
 */
object CurrentBook {

    private const val PREFS = "bookvoice_current_book"
    private const val KEY_BOOK_ID = "bookId"

    private var prefs: SharedPreferences? = null

    private val _bookId = MutableStateFlow<String?>(null)
    val bookId: StateFlow<String?> = _bookId.asStateFlow()

    fun init(context: Context) {
        val store = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = store
        _bookId.value = store.getString(KEY_BOOK_ID, null)
    }

    /** 打开 / 朗读某本书时调用；只有真的换书才写盘。 */
    fun remember(bookId: String) {
        if (bookId.isBlank() || bookId == _bookId.value) return
        _bookId.value = bookId
        prefs?.edit()?.putString(KEY_BOOK_ID, bookId)?.apply()
    }
}
