@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package io.github.mungbean2435.bookvoice.ui.book

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.mungbean2435.bookvoice.data.db.Chapter
import io.github.mungbean2435.bookvoice.ui.AppViewModel
import io.github.mungbean2435.bookvoice.ui.components.BookVoicePageScaffold
import io.github.mungbean2435.bookvoice.ui.theme.ContinuousRoundedCornerShape
import io.github.mungbean2435.bookvoice.ui.theme.BookVoiceDesign
import io.github.mungbean2435.bookvoice.ui.theme.BookVoiceTheme
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
    var chapters by remember { mutableStateOf<List<Chapter>>(emptyList()) }

    LaunchedEffect(bookId) {
        chapters = withContext(Dispatchers.IO) { vm.library().chapters(bookId) }
    }

    ChapterListContent(
        chapters = chapters,
        currentChapter = currentChapter,
        onSelect = onSelect,
        onBack = onBack
    )
}

@Composable
private fun ChapterListContent(
    chapters: List<Chapter>,
    currentChapter: Int,
    onSelect: (Int) -> Unit,
    onBack: () -> Unit
) {
    val tokens = BookVoiceDesign.tokens
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = currentChapter.coerceAtLeast(0)
    )
    val rowShape = if (tokens.isClean) {
        ContinuousRoundedCornerShape(20.dp)
    } else {
        RoundedCornerShape(20.dp)
    }

    BookVoicePageScaffold(title = "目录", onBack = onBack) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            state = listState,
            contentPadding = PaddingValues(
                start = tokens.pageHorizontalPadding,
                end = tokens.pageHorizontalPadding,
                top = 8.dp,
                bottom = 32.dp
            ),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (chapters.isEmpty()) {
                item {
                    Text(
                        "还没有章节",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            itemsIndexed(chapters) { index, chapter ->
                val selected = index == currentChapter
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = tokens.listRowMinHeight)
                        .clip(rowShape)
                        .background(
                            if (selected) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                MaterialTheme.colorScheme.background
                            }
                        )
                        .clickable { onSelect(index) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = chapter.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = if (selected) {
                                FontWeight.SemiBold
                            } else {
                                FontWeight.Normal
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "第 ${index + 1} 章",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (selected) {
                        Text(
                            text = "当前",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844, name = "整页目录 · Clean")
@Composable
private fun ChapterListPreview() {
    BookVoiceTheme(darkTheme = false, dynamicColor = false) {
        ChapterListContent(
            chapters = previewChapters,
            currentChapter = 2,
            onSelect = {},
            onBack = {}
        )
    }
}

private val previewChapters = listOf(
    Chapter("preview", 0, "第一章 · 夜航"),
    Chapter("preview", 1, "第二章 · 风从海上来"),
    Chapter("preview", 2, "第三章 · 无人的港口"),
    Chapter("preview", 3, "第四章 · 归途")
)
