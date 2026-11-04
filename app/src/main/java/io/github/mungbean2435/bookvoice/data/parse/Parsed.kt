package io.github.mungbean2435.bookvoice.data.parse

/** 解析结果：与 UI/DB 解耦，解析层只产出纯数据结构。 */
data class ParsedBook(
    val title: String,
    val author: String?,
    val coverPath: String?,
    val chapters: List<ParsedChapter>
)

data class ParsedChapter(
    val title: String,
    val blocks: List<String>,
    val images: List<ParsedImage>,
    /** 所属卷名；没有卷结构的书为 null。 */
    val volume: String? = null
)

/** 插图位置：插在第 afterBlockIdx 个文本块之后（0 表示正文最前面）。 */
data class ParsedImage(
    val afterBlockIdx: Int,
    val path: String
)
