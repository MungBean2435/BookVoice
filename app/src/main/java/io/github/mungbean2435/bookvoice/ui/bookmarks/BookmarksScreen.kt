@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package io.github.mungbean2435.bookvoice.ui.bookmarks

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.mungbean2435.bookvoice.data.db.BookmarkEntry
import io.github.mungbean2435.bookvoice.data.settings.AppLanguage
import io.github.mungbean2435.bookvoice.ui.AppViewModel
import io.github.mungbean2435.bookvoice.ui.components.BookmarkGlyph
import io.github.mungbean2435.bookvoice.ui.components.LocalFloatingChromeReserve
import io.github.mungbean2435.bookvoice.ui.components.BookVoicePageScaffold
import io.github.mungbean2435.bookvoice.ui.nav.PageChromeState
import io.github.mungbean2435.bookvoice.ui.onboarding.LocalAppLanguage
import io.github.mungbean2435.bookvoice.ui.theme.BookVoiceDesign
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * 书签 tab：跨书列表，点一条回到「章 + 块」继续听。
 *
 * 列表位置由宿主持有的 [PageChromeState] 保存，切走再回来不回到顶部。
 * 每次进入 tab 重新读库，播放页新加的书签能立刻看到。
 */
@Composable
fun BookmarksScreen(
    vm: AppViewModel,
    state: PageChromeState,
    onOpen: (BookmarkEntry) -> Unit
) {
    val library = remember(vm) { vm.library() }
    val scope = rememberCoroutineScope()
    val tokens = BookVoiceDesign.tokens
    val reserve = LocalFloatingChromeReserve.current
    var entries by remember { mutableStateOf<List<BookmarkEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    val snackbarHostState = remember { SnackbarHostState() }

    suspend fun reload() {
        entries = withContext(Dispatchers.IO) { library.allBookmarks() }
        loading = false
    }

    LaunchedEffect(Unit) { reload() }

    BookVoicePageScaffold(
        title = if (LocalAppLanguage.current == AppLanguage.En) "Bookmarks" else "书签",
        state = state.topBar
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
            when {
                loading -> Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }

                entries.isEmpty() -> EmptyBookmarks(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                )

                else -> LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    state = state.list,
                    contentPadding = PaddingValues(
                        start = tokens.pageHorizontalPadding,
                        end = tokens.pageHorizontalPadding,
                        top = 8.dp,
                        bottom = 32.dp + reserve
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(entries, key = { it.bookmark.id }) { entry ->
                        BookmarkCard(
                            entry = entry,
                            onOpen = { onOpen(entry) },
                            onDelete = {
                                // 先从列表移除，让点击立刻有反馈；提示条保留这条书签供撤销。
                                entries = entries.filterNot { it.bookmark.id == entry.bookmark.id }
                                snackbarHostState.currentSnackbarData?.dismiss()
                                val deleting = scope.launch(Dispatchers.IO) {
                                    library.deleteBookmark(entry.bookmark.id)
                                }
                                scope.launch {
                                    val result = snackbarHostState.showSnackbar(
                                        message = "已删除书签",
                                        actionLabel = "撤销",
                                        duration = SnackbarDuration.Long
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        // 等删除落库再插回同一个 id，避免先后顺序颠倒。
                                        deleting.join()
                                        withContext(Dispatchers.IO) { library.addBookmark(entry.bookmark) }
                                        entries = (entries + entry)
                                            .sortedByDescending { it.bookmark.createdAt }
                                    }
                                }
                            }
                        )
                    }
                }
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = tokens.pageHorizontalPadding)
                    .padding(bottom = 16.dp + reserve)
            ) { data ->
                Snackbar(
                    snackbarData = data,
                    shape = tokens.cardShape,
                    containerColor = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    actionColor = MaterialTheme.colorScheme.inversePrimary
                )
            }
        }
    }
}

@Composable
private fun BookmarkCard(
    entry: BookmarkEntry,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val mark = entry.bookmark
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = BookVoiceDesign.tokens.cardShape,
        color = colors.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen)
                .padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(BookVoiceDesign.tokens.coverSmallShape)
                    .background(colors.primary.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = BookmarkGlyph,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = colors.primary
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = entry.bookTitle.ifBlank { "未知书籍" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = buildString {
                        append(entry.chapterTitle.ifBlank { "第 ${mark.chapterIdx + 1} 章" })
                        append(" · 第 ${mark.blockIdx + 1} 段")
                        if (mark.offsetMs > 0) append(" · ${formatClock(mark.offsetMs)}")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (!mark.note.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = mark.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.primary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "删除书签",
                    tint = colors.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun EmptyBookmarks(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = BookmarkGlyph,
            contentDescription = null,
            modifier = Modifier.size(44.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
        )
        Spacer(Modifier.height(12.dp))
        Text("还没有书签", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            "在听书页点「书签」或在播放页菜单里加一条，回来就能看到。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatClock(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return String.format(Locale.US, "%d:%02d", total / 60, total % 60)
}
