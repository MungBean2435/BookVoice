package io.github.mungbean2435.bookvoice.ui

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.mungbean2435.bookvoice.data.db.Book
import io.github.mungbean2435.bookvoice.data.repo.Library
import io.github.mungbean2435.bookvoice.data.settings.Metrics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/** 一个导入失败的文件；[uri] 保留下来供「重试失败项」再次提交。 */
data class ImportFailure(
    val uri: Uri,
    val name: String,
    val message: String
)

/**
 * 导入任务的可见状态。单个文件和整批文件走同一套流程：
 * 书架与引导页只读这个状态，不再各自维护进度。
 */
data class ImportTask(
    val id: Long = System.nanoTime(),
    val total: Int,
    val completed: Int = 0,
    val currentName: String? = null,
    val running: Boolean = true,
    val cancelRequested: Boolean = false,
    val cancelled: Boolean = false,
    val imported: Int = 0,
    val failures: List<ImportFailure> = emptyList(),
    val startedAt: Long = System.currentTimeMillis()
)

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val library = Library(app)

    private val _books = MutableStateFlow<List<Book>>(emptyList())
    val books: StateFlow<List<Book>> = _books.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _importTask = MutableStateFlow<ImportTask?>(null)
    val importTask: StateFlow<ImportTask?> = _importTask.asStateFlow()

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

    fun import(uri: Uri) = startImport(listOf(uri))

    /** 引导里选文件夹后批量导入；单个文件失败不打断其余文件。 */
    fun importAll(uris: List<Uri>) = startImport(uris)

    fun cancelImport() {
        _importTask.update { task ->
            if (task == null || !task.running || task.cancelRequested) task
            else task.copy(cancelRequested = true)
        }
    }

    /** 只重试失败的文件；成功导入的书不会被再次导入。 */
    fun retryFailedImports() {
        val task = _importTask.value ?: return
        if (task.running) return
        val pending = task.failures.map { it.uri }
        if (pending.isNotEmpty()) startImport(pending)
    }

    fun dismissImportTask() {
        if (_importTask.value?.running == true) return
        _importTask.value = null
    }

    private fun startImport(uris: List<Uri>) {
        if (uris.isEmpty()) return
        if (_importTask.value?.running == true) return

        viewModelScope.launch {
            _busy.value = true
            val task = ImportTask(total = uris.size)
            _importTask.value = task
            val failures = mutableListOf<ImportFailure>()
            var imported = 0
            var lastElapsedMs = 0L
            var lastTitle: String? = null
            var cancelled = false
            try {
                for (uri in uris) {
                    if (_importTask.value?.cancelRequested == true) {
                        cancelled = true
                        break
                    }
                    val name = withContext(Dispatchers.IO) { displayName(uri) }
                    _importTask.update { it?.copy(currentName = name) }
                    try {
                        val result = withContext(Dispatchers.IO) { library.import(uri) }
                        imported++
                        lastElapsedMs = result.elapsedMs
                        lastTitle = result.title
                    } catch (t: Throwable) {
                        if (t is CancellationException) throw t
                        failures += ImportFailure(
                            uri = uri,
                            name = name,
                            message = t.message ?: t.javaClass.simpleName
                        )
                    }
                    _importTask.update {
                        it?.copy(
                            completed = imported + failures.size,
                            imported = imported,
                            failures = failures.toList()
                        )
                    }
                }

                if (lastElapsedMs > 0) Metrics.lastImportMs = lastElapsedMs
                refresh()

                when {
                    cancelled && failures.isEmpty() && imported == 0 -> {
                        _importTask.value = null
                        toast("已停止导入")
                    }

                    cancelled -> {
                        markStopped(imported, failures, cancelled = true)
                        toast("已停止导入，已完成 $imported 本")
                    }

                    failures.isEmpty() -> {
                        _importTask.value = null
                        toast(
                            if (uris.size == 1 && lastTitle != null) {
                                "已导入《$lastTitle》· 用时 " +
                                    String.format(Locale.US, "%.1f", lastElapsedMs / 1000.0) + " 秒"
                            } else {
                                "已导入 $imported 本"
                            }
                        )
                    }

                    else -> markStopped(imported, failures, cancelled = false)
                }
            } finally {
                // 若期间已开始新的导入，旧任务的收尾不能覆盖新任务的状态。
                if (_importTask.value?.id == task.id) {
                    _importTask.update { it?.copy(running = false, currentName = null) }
                }
                if (_importTask.value?.running != true) _busy.value = false
            }
        }
    }

    private fun markStopped(imported: Int, failures: List<ImportFailure>, cancelled: Boolean) {
        _importTask.update {
            it?.copy(
                running = false,
                currentName = null,
                cancelled = cancelled,
                imported = imported,
                completed = imported + failures.size,
                failures = failures.toList()
            )
        }
    }

    fun delete(bookId: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { library.delete(bookId) }
            refresh()
            toast("已删除")
        }
    }

    private fun displayName(uri: Uri): String {
        val resolver = getApplication<Application>().contentResolver
        val queried = runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull()
        return queried?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
            ?: "所选文件"
    }

    private fun toast(message: String) {
        Toast.makeText(getApplication<Application>(), message, Toast.LENGTH_LONG).show()
    }
}
