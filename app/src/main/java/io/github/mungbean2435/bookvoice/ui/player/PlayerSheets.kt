package io.github.mungbean2435.bookvoice.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.mungbean2435.bookvoice.data.db.Bookmark
import io.github.mungbean2435.bookvoice.data.db.Chapter
import io.github.mungbean2435.bookvoice.ui.theme.ContinuousRoundedCornerShape
import io.github.mungbean2435.bookvoice.ui.theme.BookVoiceDesign
import io.github.mungbean2435.bookvoice.ui.theme.BookVoiceTheme

private val QueueRowShape = 20.dp

/**
 * 章节 sheet：和播放队列同一套行结构。章节顺序是书的结构，不能拖动重排，
 * 所以这里只做选择与当前章高亮，不引入假的 isDirty 提交。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChapterSheet(
    chapters: List<Chapter>,
    currentChapter: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val tokens = BookVoiceDesign.tokens
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val rowShape = if (tokens.isClean) {
        ContinuousRoundedCornerShape(QueueRowShape)
    } else {
        RoundedCornerShape(QueueRowShape)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = tokens.sheetShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            SheetHeader(title = "目录")

            if (chapters.isEmpty()) {
                Text(
                    text = "还没有章节",
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        bottom = 24.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    itemsIndexed(chapters) { index, chapter ->
                        ChapterRowContent(
                            chapter = chapter,
                            index = index,
                            selected = index == currentChapter,
                            rowShape = rowShape,
                            onClick = { onSelect(index) }
                        )
                    }
                }
            }
        }
    }
}

/** 章节行单独抽出来，方便给 sheet 做行级 Preview。 */
@Composable
private fun ChapterRowContent(
    chapter: Chapter,
    index: Int,
    selected: Boolean,
    rowShape: Shape,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = BookVoiceDesign.tokens.listRowMinHeight)
            .clip(rowShape)
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    Color.Transparent
                }
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = chapter.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
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

/**
 * 书签 sheet：同一套行结构，右侧操作收进更多菜单。
 * 行本身是「跳转」，删除在菜单里，避免列表里放一排红字。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BookmarkSheet(
    bookmarks: List<Bookmark>,
    onSelect: (Bookmark) -> Unit,
    onDelete: (Bookmark) -> Unit,
    onAdd: () -> Unit,
    onDismiss: () -> Unit
) {
    val tokens = BookVoiceDesign.tokens
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val rowShape = if (tokens.isClean) {
        ContinuousRoundedCornerShape(QueueRowShape)
    } else {
        RoundedCornerShape(QueueRowShape)
    }
    var menuFor by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = tokens.sheetShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            SheetHeader(
                title = "书签",
                actionLabel = "加书签",
                onAction = onAdd
            )

            if (bookmarks.isEmpty()) {
                Text(
                    text = "还没有书签",
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        bottom = 24.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(bookmarks, key = { it.id }) { mark ->
                        BookmarkRowContent(
                            mark = mark,
                            rowShape = rowShape,
                            menuOpen = menuFor == mark.id,
                            onMenu = { menuFor = mark.id },
                            onDismissMenu = { menuFor = null },
                            onSelect = { onSelect(mark) },
                            onDelete = {
                                menuFor = null
                                onDelete(mark)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BookmarkRowContent(
    mark: Bookmark,
    rowShape: Shape,
    menuOpen: Boolean,
    onMenu: () -> Unit,
    onDismissMenu: () -> Unit,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = BookVoiceDesign.tokens.listRowMinHeight)
                .clip(rowShape)
                .clickable(onClick = onSelect)
                .padding(start = 16.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "第 ${mark.chapterIdx + 1} 章 · 第 ${mark.blockIdx + 1} 段",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = mark.note?.takeIf { it.isNotBlank() } ?: "点按从这里继续",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onMenu) {
                Icon(Icons.Filled.MoreVert, contentDescription = "书签操作")
            }
        }

        DropdownMenu(expanded = menuOpen, onDismissRequest = onDismissMenu) {
            DropdownMenuItem(
                text = { Text("删除书签") },
                onClick = onDelete
            )
        }
    }
}

@Composable
private fun SheetHeader(
    title: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold
        )
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) {
                Text(actionLabel)
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 260, name = "目录/书签行 · Clean")
@Composable
private fun PlayerSheetRowsPreview() {
    BookVoiceTheme(darkTheme = false, dynamicColor = false) {
        val rowShape = ContinuousRoundedCornerShape(QueueRowShape)
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            ChapterRowContent(
                chapter = Chapter("preview", 0, "第三章 · 夜航"),
                index = 2,
                selected = true,
                rowShape = rowShape,
                onClick = {}
            )
            ChapterRowContent(
                chapter = Chapter("preview", 1, "第四章 · 归途"),
                index = 3,
                selected = false,
                rowShape = rowShape,
                onClick = {}
            )
            BookmarkRowContent(
                mark = Bookmark(
                    id = "preview-mark",
                    bookId = "preview",
                    chapterIdx = 2,
                    blockIdx = 8,
                    offsetMs = 0L,
                    note = "这里再听一遍",
                    createdAt = 0L
                ),
                rowShape = rowShape,
                menuOpen = false,
                onMenu = {},
                onDismissMenu = {},
                onSelect = {},
                onDelete = {}
            )
        }
    }
}
