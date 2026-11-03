package io.github.mungbean2435.bookvoice.data.repo

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import io.github.mungbean2435.bookvoice.data.db.Block
import io.github.mungbean2435.bookvoice.data.db.Bookmark
import io.github.mungbean2435.bookvoice.data.db.BookmarkEntry
import io.github.mungbean2435.bookvoice.data.db.Failure
import io.github.mungbean2435.bookvoice.data.db.Book
import io.github.mungbean2435.bookvoice.data.db.Chapter
import io.github.mungbean2435.bookvoice.data.db.ChapterImage
import io.github.mungbean2435.bookvoice.data.db.Db
import io.github.mungbean2435.bookvoice.data.parse.EpubParser
import io.github.mungbean2435.bookvoice.data.parse.TextDecode
import io.github.mungbean2435.bookvoice.data.parse.TxtParser
import io.github.mungbean2435.bookvoice.tts.rules.RulePack
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID

/** 书库：导入、读取、进度、删除。所有耗时操作都在 IO 线程调用。 */
data class ImportResult(
    val title: String,
    val elapsedMs: Long,
    val copyMs: Long,
    val parseMs: Long,
    val storeMs: Long
) {
    fun breakdown(): String =
        "拷贝 %.1f / 解析 %.1f / 写库 %.1f s".format(
            java.util.Locale.US,
            copyMs / 1000.0,
            parseMs / 1000.0,
            storeMs / 1000.0
        )
}

class Library(private val context: Context) {

    fun books(): List<Book> = Db.get().books()

    fun book(bookId: String): Book? = Db.get().book(bookId)

    fun chapters(bookId: String): List<Chapter> = Db.get().chapters(bookId)

    fun blocks(bookId: String, chapterIdx: Int): List<Block> = Db.get().blocks(bookId, chapterIdx)

    fun allBlocks(bookId: String): List<Block> = Db.get().allBlocks(bookId)

    fun allImages(bookId: String): List<ChapterImage> = Db.get().allImages(bookId)

    fun images(bookId: String, chapterIdx: Int): List<ChapterImage> = Db.get().images(bookId, chapterIdx)

    fun chapterBlockCounts(bookId: String): Map<Int, Int> = Db.get().chapterBlockCounts(bookId)

    fun saveProgress(bookId: String, chapterIdx: Int, blockIdx: Int, offsetMs: Long) {
        Db.get().updateProgress(bookId, chapterIdx, blockIdx, offsetMs)
    }

    fun bookmarks(bookId: String): List<Bookmark> = Db.get().bookmarks(bookId)

    fun allBookmarks(): List<BookmarkEntry> = Db.get().allBookmarks()

    fun addBookmark(bookmark: Bookmark) = Db.get().addBookmark(bookmark)

    fun deleteBookmark(id: String) = Db.get().deleteBookmark(id)

    fun failures(bookId: String): List<Failure> = Db.get().failures(bookId)

    fun clearFailure(bookId: String, chapterIdx: Int, blockIdx: Int) =
        Db.get().clearFailure(bookId, chapterIdx, blockIdx)

    fun rulePacks(): List<RulePack> = Db.get().rulePacks()

    fun saveRulePack(pack: RulePack) = Db.get().upsertRulePack(pack)

    fun deleteRulePack(id: String) = Db.get().deleteRulePack(id)

    fun setRulePackEnabled(id: String, enabled: Boolean) = Db.get().setRulePackEnabled(id, enabled)

    fun delete(bookId: String) {
        val removed = Db.get().deleteBook(bookId) ?: return
        runCatching { File(removed.filePath).parentFile?.deleteRecursively() }
    }

    /** 导入一本书，返回书名和解析耗时。异常向上抛，由 UI 统一提示。 */
    fun import(uri: Uri): ImportResult {
        val startedAt = System.currentTimeMillis()
        var copyMs = 0L
        var parseMs = 0L
        val displayName = displayName(uri) ?: "未命名.epub"
        val fallbackTitle = displayName.substringBeforeLast('.').ifBlank { displayName }
        val id = UUID.randomUUID().toString()
        val dir = File(context.filesDir, "books/$id").apply { mkdirs() }

        val ext = displayName.substringAfterLast('.', "").lowercase()
        val safeExt = if (ext in setOf("txt", "epub")) ext else "bin"
        val src = File(dir, "source.$safeExt")
        context.contentResolver.openInputStream(uri)?.use { input ->
            src.outputStream().use { output -> input.copyTo(output) }
        } ?: throw IOException("无法打开所选文件")

        copyMs = System.currentTimeMillis() - startedAt
        val copyDoneAt = System.currentTimeMillis()

        val head = ByteArray(4)
        val read = src.inputStream().use { it.read(head) }
        val isZip = read >= 4 && head[0] == 0x50.toByte() && head[1] == 0x4B.toByte()

        val parsed = if (safeExt == "epub" || isZip) {
            EpubParser(src, File(dir, "images")).parse(fallbackTitle)
        } else {
            TxtParser.parse(TextDecode.decode(src.readBytes()), fallbackTitle)
        }

        parseMs = System.currentTimeMillis() - copyDoneAt
        val parseDoneAt = System.currentTimeMillis()

        val now = System.currentTimeMillis()
        val chapterRows = ArrayList<Chapter>(parsed.chapters.size)
        val blockRows = ArrayList<Block>()
        val imageRows = ArrayList<ChapterImage>()
        var charCount = 0

        parsed.chapters.forEachIndexed { chapterIdx, chapter ->
            chapterRows.add(Chapter(bookId = id, idx = chapterIdx, title = chapter.title))
            chapter.blocks.forEachIndexed { blockIdx, text ->
                blockRows.add(
                    Block(
                        bookId = id,
                        chapterIdx = chapterIdx,
                        idx = blockIdx,
                        text = text,
                        charCount = text.length,
                        hash = ""
                    )
                )
                charCount += text.length
            }
            // 同一位置可能有多张图，用章内序号区分（M1 修过的 UNIQUE 冲突）
            chapter.images.forEachIndexed { imageIdx, image ->
                imageRows.add(
                    ChapterImage(
                        bookId = id,
                        chapterIdx = chapterIdx,
                        idx = imageIdx,
                        afterBlockIdx = image.afterBlockIdx,
                        path = image.path
                    )
                )
            }
        }

        val format = if (safeExt == "epub" || isZip) "epub" else "txt"
        val book = Book(
            id = id,
            title = parsed.title,
            author = parsed.author,
            format = format,
            filePath = src.absolutePath,
            coverPath = parsed.coverPath,
            chapterCount = chapterRows.size,
            charCount = charCount,
            addedAt = now,
            lastOpenedAt = now,
            lastChapterIdx = 0,
            lastBlockIdx = 0,
            lastOffsetMs = 0L
        )
        Db.get().insertBook(book, chapterRows, blockRows, imageRows)
        val finishedAt = System.currentTimeMillis()
        return ImportResult(
            title = book.title,
            elapsedMs = finishedAt - startedAt,
            copyMs = copyMs,
            parseMs = parseMs,
            storeMs = finishedAt - parseDoneAt
        )
    }

    private fun displayName(uri: Uri): String? {
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (index >= 0) return cursor.getString(index)
                    }
                }
        }
        return uri.lastPathSegment
    }

    private fun sha1(text: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(text.toByteArray(Charsets.UTF_8))
        val sb = StringBuilder(digest.size * 2)
        for (byte in digest) sb.append("%02x".format(byte))
        return sb.toString()
    }
}
