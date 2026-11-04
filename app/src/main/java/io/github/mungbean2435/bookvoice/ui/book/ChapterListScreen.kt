@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package io.github.mungbean2435.bookvoice.ui.book

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.mungbean2435.bookvoice.data.db.Book
import io.github.mungbean2435.bookvoice.data.db.Chapter
import io.github.mungbean2435.bookvoice.ui.AppViewModel
import io.github.mungbean2435.bookvoice.ui.components.BookVoicePageScaffold
import io.github.mungbean2435.bookvoice.ui.components.rememberCoverBitmap
import io.github.mungbean2435.bookvoice.ui.theme.BookVoiceDesign
import io.github.mungbean2435.bookvoice.ui.theme.BookVoiceTheme
import io.github.mungbean2435.bookvoice.ui.theme.ContinuousRoundedCornerShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ChapterListScreen(
    vm: AppViewModel,
    bookId: String,
    currentChapter: Int,
    onBack: () -> Unit,
    onSelect: (Int) -> Unit
) {
    var book by remember(bookId) { mutableStateOf<Book?>(null) }
    var chapters by remember(bookId) { mutableStateOf<List<Chapter>>(emptyList()) }
    var loading by remember(bookId) { mutableStateOf(true) }

    LaunchedEffect(bookId) {
        val loaded = withContext(Dispatchers.IO) {
            val library = vm.library()
            library.book(bookId) to library.chapters(bookId)
        }
        book = loaded.first
        chapters = loaded.second
        loading = false
    }

    ChapterListContent(
        bookId = bookId,
        book = book,
        chapters = chapters,
        currentChapter = currentChapter,
        loading = loading,
        onSelect = onSelect,
        onBack = onBack
    )
}

@Composable
private fun ChapterListContent(
    bookId: String,
    book: Book?,
    chapters: List<Chapter>,
    currentChapter: Int,
    loading: Boolean,
    onSelect: (Int) -> Unit,
    onBack: () -> Unit
) {
    val tokens = BookVoiceDesign.tokens
    val sections = remember(chapters) { buildChapterSections(chapters) }
    var expandedVolume by rememberSaveable(bookId) { mutableStateOf("") }
    var expandedInitialized by rememberSaveable(bookId) { mutableStateOf(false) }
    val currentKey = remember(sections, currentChapter) {
        currentSectionKey(sections, currentChapter)
    }

    LaunchedEffect(sections, expandedInitialized) {
        if (!expandedInitialized && sections.isNotEmpty()) {
            expandedVolume = currentKey
                ?: sections.firstOrNull { it.volume != null }
                    ?.let { sectionKey(it, sections.indexOf(it)) }
                ?: ""
            expandedInitialized = true
        }
    }

    val rows = remember(sections, expandedVolume) {
        buildChapterRows(sections, expandedVolume)
    }
    val listState = rememberLazyListState()
    var didScrollToCurrent by rememberSaveable(bookId) { mutableStateOf(false) }

    // 等卷展开、当前章真正进入扁平行后再定位一次；用户之后自己滚动/折叠不再抢位置。
    LaunchedEffect(loading, rows, didScrollToCurrent) {
        if (loading || didScrollToCurrent) return@LaunchedEffect
        val rowIndex = rows.indexOfFirst {
            it is ChapterRow.Item && it.chapter.idx == currentChapter
        }
        if (rowIndex >= 0) {
            listState.scrollToItem(if (book != null) rowIndex + 1 else rowIndex)
            didScrollToCurrent = true
        }
    }

    val rowShape = if (tokens.isClean) {
        ContinuousRoundedCornerShape(20.dp)
    } else {
        RoundedCornerShape(20.dp)
    }

    BookVoicePageScaffold(
        title = "目录",
        onBack = onBack,
        largeTitle = false
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = PaddingValues(
                        start = tokens.pageHorizontalPadding,
                        end = tokens.pageHorizontalPadding,
                        top = 8.dp,
                        bottom = 40.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (book != null) {
                        item(key = "book-info") {
                            BookInfoHeader(book)
                            HorizontalDivider(
                                modifier = Modifier.padding(top = 12.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                        }
                    }

                    if (chapters.isEmpty()) {
                        item(key = "empty") {
                            Text(
                                "还没有章节",
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    items(rows, key = { it.key }) { row ->
                        when (row) {
                            is ChapterRow.Header -> VolumeRow(
                                title = row.volume,
                                chapterCount = row.chapterCount,
                                expanded = row.expanded,
                                shape = rowShape,
                                onClick = {
                                    expandedVolume = if (row.expanded) "" else {
                                        sections.getOrNull(row.sectionIndex)
                                            ?.let { sectionKey(it, row.sectionIndex) }
                                            ?: ""
                                    }
                                }
                            )

                            is ChapterRow.Item -> ChapterItemRow(
                                chapter = row.chapter,
                                selected = row.chapter.idx == currentChapter,
                                indented = row.indented,
                                shape = rowShape,
                                onClick = { onSelect(row.chapter.idx) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BookInfoHeader(book: Book) {
    val tokens = BookVoiceDesign.tokens
    val cover = rememberCoverBitmap(book.coverPath, 256)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (cover != null) {
            Image(
                bitmap = cover,
                contentDescription = "封面",
                modifier = Modifier
                    .size(width = 64.dp, height = 88.dp)
                    .clip(RoundedCornerShape(10.dp)),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .size(width = 64.dp, height = 88.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = book.title.take(1),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = book.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = book.author?.trim()?.takeIf { it.isNotEmpty() } ?: "未知作者",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "共 ${book.chapterCount} 章",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun VolumeRow(
    title: String,
    chapterCount: Int,
    expanded: Boolean,
    shape: androidx.compose.ui.graphics.Shape,
    onClick: () -> Unit
) {
    val tokens = BookVoiceDesign.tokens
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 0f else -90f,
        label = "volume-chevron"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = tokens.listRowMinHeight)
            .clip(shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.KeyboardArrowDown,
            contentDescription = if (expanded) "收起$title" else "展开$title",
            modifier = Modifier
                .size(20.dp)
                .graphicsLayer { rotationZ = rotation },
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "$chapterCount 章",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ChapterItemRow(
    chapter: Chapter,
    selected: Boolean,
    indented: Boolean,
    shape: androidx.compose.ui.graphics.Shape,
    onClick: () -> Unit
) {
    val tokens = BookVoiceDesign.tokens
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = tokens.listRowMinHeight)
            .clip(shape)
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.background
                }
            )
            .clickable(onClick = onClick)
            .padding(
                start = if (indented) 28.dp else 16.dp,
                end = 16.dp,
                top = 12.dp,
                bottom = 12.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = chapter.title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (selected) {
            Spacer(Modifier.width(12.dp))
            Text(
                text = "当前",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844, name = "整页目录 · 分卷")
@Composable
private fun ChapterListPreview() {
    BookVoiceTheme(darkTheme = false, dynamicColor = false) {
        ChapterListContent(
            bookId = "preview",
            book = previewBook,
            chapters = previewChapters,
            currentChapter = 5,
            loading = false,
            onSelect = {},
            onBack = {}
        )
    }
}

private val previewBook = Book(
    id = "preview",
    title = "风从海上来",
    author = "林晚",
    format = "epub",
    filePath = "",
    coverPath = null,
    chapterCount = 7,
    charCount = 0,
    addedAt = 0,
    lastOpenedAt = 0,
    lastChapterIdx = 5,
    lastBlockIdx = 0,
    lastOffsetMs = 0
)

private val previewChapters = listOf(
    Chapter("preview", 0, "序章 · 潮声", volume = "第一卷 · 离岸"),
    Chapter("preview", 1, "第一章 · 夜航", volume = "第一卷 · 离岸"),
    Chapter("preview", 2, "第二章 · 风从海上来", volume = "第一卷 · 离岸"),
    Chapter("preview", 3, "第三章 · 无人的港口", volume = "第二卷 · 归途"),
    Chapter("preview", 4, "第四章 · 灯塔", volume = "第二卷 · 归途"),
    Chapter("preview", 5, "第五章 · 长夜", volume = "第二卷 · 归途"),
    Chapter("preview", 6, "终章 · 天亮", volume = "第二卷 · 归途")
)
