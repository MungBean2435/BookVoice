package io.github.mungbean2435.bookvoice.data.db

/** 一本书。进度直接挂在这里，M3 的播放器每 2 秒写一次。 */
data class Book(
    val id: String,
    val title: String,
    val author: String?,
    val format: String,
    val filePath: String,
    val coverPath: String?,
    val chapterCount: Int,
    val charCount: Int,
    val addedAt: Long,
    val lastOpenedAt: Long,
    val lastChapterIdx: Int,
    val lastBlockIdx: Int,
    val lastOffsetMs: Long
)

data class Chapter(
    val bookId: String,
    val idx: Int,
    val title: String,
    /** 所属卷名；旧数据或没有卷结构的书为 null。 */
    val volume: String? = null
)

/**
 * 正文的最小存储单位：一个自然段（超过 800 字会按句切开）。
 * [hash] 是文本内容的 SHA-1，M2 起用于合成缓存 key。
 */
data class Block(
    val bookId: String,
    val chapterIdx: Int,
    val idx: Int,
    val text: String,
    val charCount: Int,
    val hash: String
)

/** 章节在全书中的字数区间。 */
data class ChapterRange(val chapterIdx: Int, val startChars: Long, val chars: Long)

/** 合成失败、已跳过的段落，之后可回补。 */
data class Failure(
    val bookId: String,
    val chapterIdx: Int,
    val blockIdx: Int,
    val text: String,
    val createdAt: Long
)

/** 书签：定位到「章 + 块」，M4 起可跳回。 */
data class Bookmark(
    val id: String,
    val bookId: String,
    val chapterIdx: Int,
    val blockIdx: Int,
    val offsetMs: Long,
    val note: String?,
    val createdAt: Long
)

/** 书签 tab 用的跨书视图：书签本体 + 书名 + 章节名。 */
data class BookmarkEntry(
    val bookmark: Bookmark,
    val bookTitle: String,
    val chapterTitle: String
)

/**
 * EPUB 插图：插在第 [afterBlockIdx] 个文本块之后。
 * [idx] 是章内序号——同一位置可以有多张图（图集、连续插图），
 * 所以主键必须是 (bookId, chapterIdx, idx)，不能用 afterBlockIdx。
 */
data class ChapterImage(
    val bookId: String,
    val chapterIdx: Int,
    val idx: Int,
    val afterBlockIdx: Int,
    val path: String
)
