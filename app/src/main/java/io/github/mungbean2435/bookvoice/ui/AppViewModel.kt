package io.github.mungbean2435.bookvoice.ui

import android.app.Application
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.mungbean2435.bookvoice.data.db.Book
import io.github.mungbean2435.bookvoice.data.repo.Library
import io.github.mungbean2435.bookvoice.data.settings.Metrics
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val library = Library(app)

    private val _books = MutableStateFlow<List<Book>>(emptyList())
    val books: StateFlow<List<Book>> = _books.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val ready = CompletableDeferred<Unit>()

    init {
        refresh()
    }

    /** 首次加载完成后返回，用于决定冷启动进书架还是听书页。 */
    suspend fun awaitReady() = ready.await()

    fun library(): Library = library

    fun refresh() {
        viewModelScope.launch {
            _books.value = withContext(Dispatchers.IO) { library.books() }
            if (!ready.isCompleted) ready.complete(Unit)
        }
    }

    fun import(uri: Uri) {
        viewModelScope.launch {
            _busy.value = true
            try {
                val result = withContext(Dispatchers.IO) { library.import(uri) }
                Metrics.lastImportMs = result.elapsedMs
                refresh()
                toast("已导入《${result.title}》· 用时 ${String.format(java.util.Locale.US, "%.1f", result.elapsedMs / 1000.0)} 秒")
            } catch (t: Throwable) {
                toast("导入失败：${t.message ?: t.javaClass.simpleName}")
            } finally {
                _busy.value = false
            }
        }
    }

    /**
     * 引导里选文件夹后批量导入。逐个导入，避免 SQLite 写入与解析互相抢资源；
     * 单个文件失败不打断其余文件。
     */
    fun importAll(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _busy.value = true
            var imported = 0
            var failed = 0
            var lastElapsedMs = 0L
            try {
                withContext(Dispatchers.IO) {
                    uris.forEach { uri ->
                        runCatching { library.import(uri) }
                            .onSuccess {
                                imported++
                                lastElapsedMs = it.elapsedMs
                            }
                            .onFailure { failed++ }
                    }
                }
                if (lastElapsedMs > 0) Metrics.lastImportMs = lastElapsedMs
                refresh()
                val message = when {
                    imported == 0 -> "导入失败：没有可导入的 TXT / EPUB"
                    failed > 0 -> "已导入 $imported 本，$failed 本失败"
                    else -> "已导入 $imported 本"
                }
                toast(message)
            } finally {
                _busy.value = false
            }
        }
    }

    fun delete(bookId: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { library.delete(bookId) }
            refresh()
            toast("已删除")
        }
    }

    private fun toast(message: String) {
        Toast.makeText(getApplication<Application>(), message, Toast.LENGTH_LONG).show()
    }
}
