package io.github.mungbean2435.bookvoice.data.db

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import io.github.mungbean2435.bookvoice.tts.rules.RulePack

/**
 * 极简 SQLite 封装。M1 只有导入/阅读需要的读写；clips / bookmarks / rule_packs
 * 三张表先建好，M2–M4 直接用，避免中途迁移。
 */
class Db private constructor(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE books(
                id TEXT PRIMARY KEY,
                title TEXT NOT NULL,
                author TEXT,
                format TEXT NOT NULL,
                filePath TEXT NOT NULL,
                coverPath TEXT,
                chapterCount INTEGER NOT NULL DEFAULT 0,
                charCount INTEGER NOT NULL DEFAULT 0,
                addedAt INTEGER NOT NULL,
                lastOpenedAt INTEGER NOT NULL,
                lastChapterIdx INTEGER NOT NULL DEFAULT 0,
                lastBlockIdx INTEGER NOT NULL DEFAULT 0,
                lastOffsetMs INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE chapters(
                bookId TEXT NOT NULL,
                idx INTEGER NOT NULL,
                title TEXT NOT NULL,
                volume TEXT,
                PRIMARY KEY(bookId, idx)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE blocks(
                bookId TEXT NOT NULL,
                chapterIdx INTEGER NOT NULL,
                idx INTEGER NOT NULL,
                text TEXT NOT NULL,
                charCount INTEGER NOT NULL,
                hash TEXT NOT NULL,
                PRIMARY KEY(bookId, chapterIdx, idx)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE chapter_images(
                bookId TEXT NOT NULL,
                chapterIdx INTEGER NOT NULL,
                idx INTEGER NOT NULL,
                afterBlockIdx INTEGER NOT NULL,
                path TEXT NOT NULL,
                PRIMARY KEY(bookId, chapterIdx, idx)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE failures(
                bookId TEXT NOT NULL,
                chapterIdx INTEGER NOT NULL,
                blockIdx INTEGER NOT NULL,
                text TEXT NOT NULL,
                createdAt INTEGER NOT NULL,
                PRIMARY KEY(bookId, chapterIdx, blockIdx)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE bookmarks(
                id TEXT PRIMARY KEY,
                bookId TEXT NOT NULL,
                chapterIdx INTEGER NOT NULL,
                blockIdx INTEGER NOT NULL,
                offsetMs INTEGER NOT NULL,
                note TEXT,
                createdAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE clips(
                cacheKey TEXT PRIMARY KEY,
                bookId TEXT NOT NULL,
                providerId TEXT NOT NULL,
                voiceId TEXT NOT NULL,
                path TEXT NOT NULL,
                durationMs INTEGER NOT NULL,
                bytes INTEGER NOT NULL,
                createdAt INTEGER NOT NULL,
                lastAccess INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE rule_packs(
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                version INTEGER NOT NULL,
                json TEXT NOT NULL,
                enabled INTEGER NOT NULL DEFAULT 1
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            // v1 的 chapter_images 主键是 (bookId, chapterIdx, afterBlockIdx)，
            // 同一位置出现两张图就会 UNIQUE 冲突。改成按章内序号做主键。
            db.execSQL(
                """
                CREATE TABLE chapter_images_new(
                    bookId TEXT NOT NULL,
                    chapterIdx INTEGER NOT NULL,
                    idx INTEGER NOT NULL,
                    afterBlockIdx INTEGER NOT NULL,
                    path TEXT NOT NULL,
                    PRIMARY KEY(bookId, chapterIdx, idx)
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO chapter_images_new(bookId, chapterIdx, idx, afterBlockIdx, path)
                SELECT bookId, chapterIdx, rowid, afterBlockIdx, path FROM chapter_images
                """.trimIndent()
            )
            db.execSQL("DROP TABLE chapter_images")
            db.execSQL("ALTER TABLE chapter_images_new RENAME TO chapter_images")
        }
        if (oldVersion < 3) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS failures(
                    bookId TEXT NOT NULL,
                    chapterIdx INTEGER NOT NULL,
                    blockIdx INTEGER NOT NULL,
                    text TEXT NOT NULL,
                    createdAt INTEGER NOT NULL,
                    PRIMARY KEY(bookId, chapterIdx, blockIdx)
                )
                """.trimIndent()
            )
        }
        if (oldVersion < 4) {
            // v4 起章节记录所属卷名；旧数据的 volume 为 NULL，界面按标题兜底，不强行拆分。
            db.execSQL("ALTER TABLE chapters ADD COLUMN volume TEXT")
        }
    }

    // ---------------- 写入 ----------------

    fun insertBook(
        book: Book,
        chapters: List<Chapter>,
        blocks: List<Block>,
        images: List<ChapterImage>
    ) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.insertOrThrow("books", null, book.toValues())

            db.compileStatement("INSERT INTO chapters(bookId, idx, title, volume) VALUES(?,?,?,?)").use { st ->
                chapters.forEach { chapter ->
                    st.clearBindings()
                    st.bindString(1, chapter.bookId)
                    st.bindLong(2, chapter.idx.toLong())
                    st.bindString(3, chapter.title)
                    val volume = chapter.volume
                    if (volume == null) st.bindNull(4) else st.bindString(4, volume)
                    st.executeInsert()
                }
            }

            // 一本 18MB 的书有 7 万多个段落，逐行 ContentValues 太慢，走编译语句
            db.compileStatement(
                "INSERT INTO blocks(bookId, chapterIdx, idx, text, charCount, hash) VALUES(?,?,?,?,?,?)"
            ).use { st ->
                blocks.forEach { block ->
                    st.clearBindings()
                    st.bindString(1, block.bookId)
                    st.bindLong(2, block.chapterIdx.toLong())
                    st.bindLong(3, block.idx.toLong())
                    st.bindString(4, block.text)
                    st.bindLong(5, block.charCount.toLong())
                    st.bindString(6, block.hash)
                    st.executeInsert()
                }
            }

            db.compileStatement(
                "INSERT INTO chapter_images(bookId, chapterIdx, idx, afterBlockIdx, path) VALUES(?,?,?,?,?)"
            ).use { st ->
                images.forEach { image ->
                    st.clearBindings()
                    st.bindString(1, image.bookId)
                    st.bindLong(2, image.chapterIdx.toLong())
                    st.bindLong(3, image.idx.toLong())
                    st.bindLong(4, image.afterBlockIdx.toLong())
                    st.bindString(5, image.path)
                    st.executeInsert()
                }
            }

            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun updateProgress(bookId: String, chapterIdx: Int, blockIdx: Int, offsetMs: Long) {
        writableDatabase.update(
            "books",
            ContentValues().apply {
                put("lastChapterIdx", chapterIdx)
                put("lastBlockIdx", blockIdx)
                put("lastOffsetMs", offsetMs)
                put("lastOpenedAt", System.currentTimeMillis())
            },
            "id=?",
            arrayOf(bookId)
        )
    }

    fun touchOpened(bookId: String) {
        writableDatabase.update(
            "books",
            ContentValues().apply { put("lastOpenedAt", System.currentTimeMillis()) },
            "id=?",
            arrayOf(bookId)
        )
    }

    /** 返回被删掉的书（用于清理文件），书不存在返回 null。 */
    fun deleteBook(bookId: String): Book? {
        val book = book(bookId) ?: return null
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("blocks", "bookId=?", arrayOf(bookId))
            db.delete("chapters", "bookId=?", arrayOf(bookId))
            db.delete("chapter_images", "bookId=?", arrayOf(bookId))
            db.delete("bookmarks", "bookId=?", arrayOf(bookId))
            db.delete("books", "id=?", arrayOf(bookId))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return book
    }

    // ---------------- 读取 ----------------

    fun books(): List<Book> {
        val out = ArrayList<Book>()
        readableDatabase.query("books", null, null, null, null, null, "lastOpenedAt DESC").use { c ->
            while (c.moveToNext()) out.add(c.readBook())
        }
        return out
    }

    fun book(bookId: String): Book? {
        readableDatabase.query("books", null, "id=?", arrayOf(bookId), null, null, null).use { c ->
            return if (c.moveToFirst()) c.readBook() else null
        }
    }

    fun chapters(bookId: String): List<Chapter> {
        val out = ArrayList<Chapter>()
        readableDatabase.query("chapters", null, "bookId=?", arrayOf(bookId), null, null, "idx ASC").use { c ->
            while (c.moveToNext()) {
                out.add(
                    Chapter(
                        bookId = bookId,
                        idx = c.getInt(c.getColumnIndexOrThrow("idx")),
                        title = c.getString(c.getColumnIndexOrThrow("title")),
                        volume = c.getColumnIndex("volume").let { i ->
                            if (i < 0 || c.isNull(i)) null else c.getString(i)
                        }
                    )
                )
            }
        }
        return out
    }

    fun blocks(bookId: String, chapterIdx: Int): List<Block> {
        val out = ArrayList<Block>()
        readableDatabase.query(
            "blocks", null,
            "bookId=? AND chapterIdx=?",
            arrayOf(bookId, chapterIdx.toString()),
            null, null, "idx ASC"
        ).use { c ->
            while (c.moveToNext()) {
                out.add(
                    Block(
                        bookId = bookId,
                        chapterIdx = chapterIdx,
                        idx = c.getInt(c.getColumnIndexOrThrow("idx")),
                        text = c.getString(c.getColumnIndexOrThrow("text")),
                        charCount = c.getInt(c.getColumnIndexOrThrow("charCount")),
                        hash = c.getString(c.getColumnIndexOrThrow("hash"))
                    )
                )
            }
        }
        return out
    }

    /** 全书段落（连续阅读用），按章、段顺序返回。 */
    fun allBlocks(bookId: String): List<Block> {
        val out = ArrayList<Block>()
        readableDatabase.query(
            "blocks", null,
            "bookId=?",
            arrayOf(bookId),
            null, null, "chapterIdx ASC, idx ASC"
        ).use { c ->
            while (c.moveToNext()) {
                out.add(
                    Block(
                        bookId = bookId,
                        chapterIdx = c.getInt(c.getColumnIndexOrThrow("chapterIdx")),
                        idx = c.getInt(c.getColumnIndexOrThrow("idx")),
                        text = c.getString(c.getColumnIndexOrThrow("text")),
                        charCount = c.getInt(c.getColumnIndexOrThrow("charCount")),
                        hash = c.getString(c.getColumnIndexOrThrow("hash"))
                    )
                )
            }
        }
        return out
    }

    /** 全书插图（连续阅读用），按章、插入位置、序号返回。 */
    fun allImages(bookId: String): List<ChapterImage> {
        val out = ArrayList<ChapterImage>()
        readableDatabase.query(
            "chapter_images", null,
            "bookId=?",
            arrayOf(bookId),
            null, null, "chapterIdx ASC, afterBlockIdx ASC, idx ASC"
        ).use { c ->
            while (c.moveToNext()) {
                out.add(
                    ChapterImage(
                        bookId = bookId,
                        chapterIdx = c.getInt(c.getColumnIndexOrThrow("chapterIdx")),
                        idx = c.getInt(c.getColumnIndexOrThrow("idx")),
                        afterBlockIdx = c.getInt(c.getColumnIndexOrThrow("afterBlockIdx")),
                        path = c.getString(c.getColumnIndexOrThrow("path"))
                    )
                )
            }
        }
        return out
    }

    fun images(bookId: String, chapterIdx: Int): List<ChapterImage> {
        val out = ArrayList<ChapterImage>()
        readableDatabase.query(
            "chapter_images", null,
            "bookId=? AND chapterIdx=?",
            arrayOf(bookId, chapterIdx.toString()),
            null, null, "afterBlockIdx ASC, idx ASC"
        ).use { c ->
            while (c.moveToNext()) {
                out.add(
                    ChapterImage(
                        bookId = bookId,
                        chapterIdx = chapterIdx,
                        idx = c.getInt(c.getColumnIndexOrThrow("idx")),
                        afterBlockIdx = c.getInt(c.getColumnIndexOrThrow("afterBlockIdx")),
                        path = c.getString(c.getColumnIndexOrThrow("path"))
                    )
                )
            }
        }
        return out
    }


    // ---------------- 待补（合成失败） ----------------

    fun recordFailure(bookId: String, chapterIdx: Int, blockIdx: Int, text: String) {
        writableDatabase.insertWithOnConflict(
            "failures", null,
            ContentValues().apply {
                put("bookId", bookId)
                put("chapterIdx", chapterIdx)
                put("blockIdx", blockIdx)
                put("text", text)
                put("createdAt", System.currentTimeMillis())
            },
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun failures(bookId: String): List<Failure> {
        val out = ArrayList<Failure>()
        readableDatabase.query(
            "failures", null, "bookId=?", arrayOf(bookId), null, null, "chapterIdx ASC, blockIdx ASC"
        ).use { c ->
            while (c.moveToNext()) {
                out.add(
                    Failure(
                        bookId = bookId,
                        chapterIdx = c.getInt(c.getColumnIndexOrThrow("chapterIdx")),
                        blockIdx = c.getInt(c.getColumnIndexOrThrow("blockIdx")),
                        text = c.getString(c.getColumnIndexOrThrow("text")),
                        createdAt = c.getLong(c.getColumnIndexOrThrow("createdAt"))
                    )
                )
            }
        }
        return out
    }

    fun clearFailure(bookId: String, chapterIdx: Int, blockIdx: Int) {
        writableDatabase.delete(
            "failures", "bookId=? AND chapterIdx=? AND blockIdx=?",
            arrayOf(bookId, chapterIdx.toString(), blockIdx.toString())
        )
    }

    // ---------------- 位置与字数 ----------------

    /** 每章的字数区间，用于把「全书百分比」映射成章节。 */
    fun chapterCharRanges(bookId: String): List<ChapterRange> {
        val out = ArrayList<ChapterRange>()
        readableDatabase.rawQuery(
            "SELECT chapterIdx, SUM(charCount) FROM blocks WHERE bookId=? GROUP BY chapterIdx ORDER BY chapterIdx",
            arrayOf(bookId)
        ).use { c ->
            var start = 0L
            while (c.moveToNext()) {
                val chapter = c.getInt(0)
                val chars = c.getLong(1)
                out.add(ChapterRange(chapter, start, chars))
                start += chars
            }
        }
        return out
    }

    /**
     * 每章有多少段。只扫 (bookId, chapterIdx) 前缀里的索引列、不读 text，
     * 首屏用它一次建出全书骨架行，避免把整本书正文都拉进内存。
     */
    fun chapterBlockCounts(bookId: String): Map<Int, Int> {
        val out = HashMap<Int, Int>()
        readableDatabase.rawQuery(
            "SELECT chapterIdx, COUNT(*) FROM blocks WHERE bookId=? GROUP BY chapterIdx",
            arrayOf(bookId)
        ).use { c ->
            while (c.moveToNext()) {
                out[c.getInt(0)] = c.getInt(1)
            }
        }
        return out
    }

    /** 该章第 [targetChars] 个字落在哪一段（0 基）。 */
    fun blockAtChar(bookId: String, chapterIdx: Int, targetChars: Long): Int {
        var acc = 0L
        var idx = 0
        readableDatabase.rawQuery(
            "SELECT idx, charCount FROM blocks WHERE bookId=? AND chapterIdx=? ORDER BY idx",
            arrayOf(bookId, chapterIdx.toString())
        ).use { c ->
            while (c.moveToNext()) {
                idx = c.getInt(0)
                acc += c.getLong(1)
                if (acc >= targetChars) return idx
            }
        }
        return idx
    }

    /** 当前段之前一共有多少字（用于估算已播时长）。 */
    fun charsBefore(bookId: String, chapterIdx: Int, blockIdx: Int): Long {
        readableDatabase.rawQuery(
            "SELECT SUM(charCount) FROM blocks WHERE bookId=? AND (chapterIdx < ? OR (chapterIdx = ? AND idx < ?))",
            arrayOf(bookId, chapterIdx.toString(), chapterIdx.toString(), blockIdx.toString())
        ).use { c ->
            return if (c.moveToFirst()) c.getLong(0) else 0L
        }
    }

    // ---------------- 书签 ----------------

    fun addBookmark(bookmark: Bookmark) {
        writableDatabase.insertWithOnConflict(
            "bookmarks", null,
            ContentValues().apply {
                put("id", bookmark.id)
                put("bookId", bookmark.bookId)
                put("chapterIdx", bookmark.chapterIdx)
                put("blockIdx", bookmark.blockIdx)
                put("offsetMs", bookmark.offsetMs)
                put("note", bookmark.note)
                put("createdAt", bookmark.createdAt)
            },
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun bookmarks(bookId: String): List<Bookmark> {
        val out = ArrayList<Bookmark>()
        readableDatabase.query(
            "bookmarks", null, "bookId=?", arrayOf(bookId), null, null, "chapterIdx ASC, blockIdx ASC"
        ).use { c ->
            while (c.moveToNext()) {
                out.add(
                    Bookmark(
                        id = c.getString(c.getColumnIndexOrThrow("id")),
                        bookId = bookId,
                        chapterIdx = c.getInt(c.getColumnIndexOrThrow("chapterIdx")),
                        blockIdx = c.getInt(c.getColumnIndexOrThrow("blockIdx")),
                        offsetMs = c.getLong(c.getColumnIndexOrThrow("offsetMs")),
                        note = c.getString(c.getColumnIndexOrThrow("note")),
                        createdAt = c.getLong(c.getColumnIndexOrThrow("createdAt"))
                    )
                )
            }
        }
        return out
    }

    fun deleteBookmark(id: String) {
        writableDatabase.delete("bookmarks", "id=?", arrayOf(id))
    }

    /** 书签 tab：按创建时间倒序，带书名和章节名；章节被删的空名回落为空串。 */
    fun allBookmarks(): List<BookmarkEntry> {
        val out = ArrayList<BookmarkEntry>()
        readableDatabase.rawQuery(
            """
            SELECT b.id, b.bookId, b.chapterIdx, b.blockIdx, b.offsetMs, b.note, b.createdAt,
                   COALESCE(books.title, '') AS bookTitle,
                   COALESCE(chapters.title, '') AS chapterTitle
            FROM bookmarks b
            LEFT JOIN books ON books.id = b.bookId
            LEFT JOIN chapters ON chapters.bookId = b.bookId AND chapters.idx = b.chapterIdx
            ORDER BY b.createdAt DESC
            """.trimIndent(),
            null
        ).use { c ->
            while (c.moveToNext()) {
                out.add(
                    BookmarkEntry(
                        bookmark = Bookmark(
                            id = c.getString(c.getColumnIndexOrThrow("id")),
                            bookId = c.getString(c.getColumnIndexOrThrow("bookId")),
                            chapterIdx = c.getInt(c.getColumnIndexOrThrow("chapterIdx")),
                            blockIdx = c.getInt(c.getColumnIndexOrThrow("blockIdx")),
                            offsetMs = c.getLong(c.getColumnIndexOrThrow("offsetMs")),
                            note = c.getString(c.getColumnIndexOrThrow("note")),
                            createdAt = c.getLong(c.getColumnIndexOrThrow("createdAt"))
                        ),
                        bookTitle = c.getString(c.getColumnIndexOrThrow("bookTitle")),
                        chapterTitle = c.getString(c.getColumnIndexOrThrow("chapterTitle"))
                    )
                )
            }
        }
        return out
    }

    // ---------------- 规则包 ----------------

    fun upsertRulePack(pack: RulePack) {
        writableDatabase.insertWithOnConflict(
            "rule_packs", null,
            ContentValues().apply {
                put("id", pack.id)
                put("name", pack.name)
                put("version", pack.version)
                put("json", pack.toJson())
                put("enabled", if (pack.enabled) 1 else 0)
            },
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun rulePacks(): List<RulePack> {
        val out = ArrayList<RulePack>()
        readableDatabase.query("rule_packs", null, null, null, null, null, "name ASC").use { c ->
            while (c.moveToNext()) {
                val raw = c.getString(c.getColumnIndexOrThrow("json"))
                runCatching { RulePack.fromJson(raw) }.getOrNull()?.let { pack ->
                    out.add(pack.copy(enabled = c.getInt(c.getColumnIndexOrThrow("enabled")) == 1))
                }
            }
        }
        return out
    }

    fun setRulePackEnabled(id: String, enabled: Boolean) {
        writableDatabase.update(
            "rule_packs",
            ContentValues().apply { put("enabled", if (enabled) 1 else 0) },
            "id=?",
            arrayOf(id)
        )
    }

    fun deleteRulePack(id: String) {
        writableDatabase.delete("rule_packs", "id=?", arrayOf(id))
    }

    // ---------------- 映射 ----------------

    private fun Book.toValues() = ContentValues().apply {
        put("id", id)
        put("title", title)
        put("author", author)
        put("format", format)
        put("filePath", filePath)
        put("coverPath", coverPath)
        put("chapterCount", chapterCount)
        put("charCount", charCount)
        put("addedAt", addedAt)
        put("lastOpenedAt", lastOpenedAt)
        put("lastChapterIdx", lastChapterIdx)
        put("lastBlockIdx", lastBlockIdx)
        put("lastOffsetMs", lastOffsetMs)
    }

    private fun Cursor.readBook() = Book(
        id = getString(getColumnIndexOrThrow("id")),
        title = getString(getColumnIndexOrThrow("title")),
        author = getString(getColumnIndexOrThrow("author")),
        format = getString(getColumnIndexOrThrow("format")),
        filePath = getString(getColumnIndexOrThrow("filePath")),
        coverPath = getString(getColumnIndexOrThrow("coverPath")),
        chapterCount = getInt(getColumnIndexOrThrow("chapterCount")),
        charCount = getInt(getColumnIndexOrThrow("charCount")),
        addedAt = getLong(getColumnIndexOrThrow("addedAt")),
        lastOpenedAt = getLong(getColumnIndexOrThrow("lastOpenedAt")),
        lastChapterIdx = getInt(getColumnIndexOrThrow("lastChapterIdx")),
        lastBlockIdx = getInt(getColumnIndexOrThrow("lastBlockIdx")),
        lastOffsetMs = getLong(getColumnIndexOrThrow("lastOffsetMs"))
    )

    companion object {
        private const val DB_NAME = "bookvoice.db"
        private const val DB_VERSION = 4

        @Volatile
        private var instance: Db? = null

        fun init(context: Context) {
            if (instance == null) {
                synchronized(this) {
                    if (instance == null) instance = Db(context.applicationContext)
                }
            }
        }

        fun get(): Db = instance ?: error("Db 尚未初始化：App.onCreate 里要先调用 Db.init()")
    }
}
