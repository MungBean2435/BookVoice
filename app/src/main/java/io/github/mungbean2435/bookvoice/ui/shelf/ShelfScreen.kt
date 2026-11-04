@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package io.github.mungbean2435.bookvoice.ui.shelf

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.AnimatedVisibility
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mungbean2435.bookvoice.data.db.Book
import io.github.mungbean2435.bookvoice.ui.AppViewModel
import io.github.mungbean2435.bookvoice.ui.ImportTask
import io.github.mungbean2435.bookvoice.ui.components.ImportTaskCard
import io.github.mungbean2435.bookvoice.ui.components.rememberCoverBitmap
import io.github.mungbean2435.bookvoice.ui.components.CoverCache
import io.github.mungbean2435.bookvoice.ui.components.LocalFloatingChromeReserve
import io.github.mungbean2435.bookvoice.data.settings.AppLanguage
import io.github.mungbean2435.bookvoice.ui.components.BookVoicePageScaffold
import io.github.mungbean2435.bookvoice.ui.morph.MorphOrigin
import io.github.mungbean2435.bookvoice.ui.morph.cornerRadiusPx
import io.github.mungbean2435.bookvoice.ui.nav.ShelfSceneState
import io.github.mungbean2435.bookvoice.ui.onboarding.LocalAppLanguage
import io.github.mungbean2435.bookvoice.ui.theme.BookVoiceDesign
import io.github.mungbean2435.bookvoice.ui.theme.BookVoiceTheme

private val IMPORT_MIME = arrayOf("*/*")

@Composable
fun ShelfScreen(
    vm: AppViewModel,
    state: ShelfSceneState,
    onOpen: (Book, MorphOrigin?) -> Unit,
    onDelete: (String) -> Unit,
    onOpenSettings: () -> Unit
) {
    val books by vm.books.collectAsStateWithLifecycle()
    val importTask by vm.importTask.collectAsStateWithLifecycle()
    val importing = importTask?.running == true
    var pendingDelete by remember { mutableStateOf<Book?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.import(uri)
    }

    ShelfContent(
        books = books,
        importTask = importTask,
        gridState = state.grid,
        topBarState = state.topBar,
        onOpen = onOpen,
        onLongClick = { pendingDelete = it },
        onImport = { if (!importing) picker.launch(IMPORT_MIME) },
        onCancelImport = vm::cancelImport,
        onRetryImport = vm::retryFailedImports,
        onDismissImport = vm::dismissImportTask,
        onOpenSettings = onOpenSettings
    )

    pendingDelete?.let { book ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除《${book.title}》？") },
            text = { Text("正文、插图和缓存都会被删除，无法撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(book.id)
                    pendingDelete = null
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun ShelfContent(
    books: List<Book>,
    importTask: ImportTask?,
    gridState: LazyGridState,
    topBarState: TopAppBarState,
    onOpen: (Book, MorphOrigin?) -> Unit,
    onLongClick: (Book) -> Unit,
    onImport: () -> Unit,
    onCancelImport: () -> Unit,
    onRetryImport: () -> Unit,
    onDismissImport: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val importing = importTask?.running == true
    val language = LocalAppLanguage.current
    BookVoicePageScaffold(
        title = if (language == AppLanguage.En) "Library" else "书架",
        state = topBarState,
        actions = {
            IconButton(onClick = onOpenSettings) {
                Icon(
                    Icons.Filled.Settings,
                    contentDescription = if (language == AppLanguage.En) "Settings" else "设置"
                )
            }
        },
        floatingActionButton = {
            if (books.isNotEmpty()) {
                ShelfFab(onImport = onImport, enabled = !importing, language = language)
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            AnimatedVisibility(visible = importTask != null) {
                if (importTask != null) {
                    ImportTaskCard(
                        task = importTask,
                        language = language,
                        onCancel = onCancelImport,
                        onRetry = onRetryImport,
                        onDismiss = onDismissImport,
                        modifier = Modifier.padding(
                            start = 16.dp,
                            end = 16.dp,
                            top = 6.dp,
                            bottom = 10.dp
                        )
                    )
                }
            }
            Box(Modifier
                .fillMaxWidth()
                .weight(1f)
            ) {
                if (books.isEmpty()) {
                    EmptyShelf(onImport = onImport, enabled = !importing, language = language)
                } else {
                    ShelfGrid(
                        books = books,
                        gridState = gridState,
                        onOpen = onOpen,
                        onLongClick = onLongClick
                    )
                }
            }
        }
    }
}

@Composable
private fun ShelfGrid(
    books: List<Book>,
    gridState: LazyGridState,
    onOpen: (Book, MorphOrigin?) -> Unit,
    onLongClick: (Book) -> Unit
) {
    val tokens = BookVoiceDesign.tokens
    val chromeReserve = LocalFloatingChromeReserve.current

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // 手机固定两列；只有宽屏才让单元自己撑到 150dp 以上。
        val columns = if (maxWidth < 600.dp) {
            GridCells.Fixed(2)
        } else {
            GridCells.Adaptive(tokens.gridMinCellWidth)
        }

        LazyVerticalGrid(
            columns = columns,
            state = gridState,
            contentPadding = PaddingValues(
                start = tokens.pageHorizontalPadding,
                end = tokens.pageHorizontalPadding,
                top = 8.dp,
                bottom = 32.dp + chromeReserve
            ),
            horizontalArrangement = Arrangement.spacedBy(tokens.gridGap),
            verticalArrangement = Arrangement.spacedBy(tokens.gridGap)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                ShelfSectionHeader(count = books.size)
            }
            items(books, key = { it.id }) { book ->
                BookTile(
                    book = book,
                    onClick = { origin -> onOpen(book, origin) },
                    onLongClick = { onLongClick(book) }
                )
            }
        }
    }
}

@Composable
private fun ShelfSectionHeader(count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "全部书籍",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = "$count 本",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookTile(
    book: Book,
    onClick: (MorphOrigin?) -> Unit,
    onLongClick: () -> Unit
) {
    val tokens = BookVoiceDesign.tokens
    val density = LocalDensity.current
    val colors = MaterialTheme.colorScheme
    val coverPath = book.coverPath?.takeIf { it.isNotBlank() }
    val cover = rememberCoverBitmap(coverPath, with(density) { 160.dp.roundToPx() })
    // 起点：书架入口默认从底部胶囊的容器矩形起飞（由 AppRoot 统一采集）；
    // 本次启动还没有胶囊时才用这张书卡的封面矩形兜底。
    var coverRect by remember(book.id) {
        androidx.compose.runtime.mutableStateOf(androidx.compose.ui.geometry.Rect.Zero)
    }
    fun buildOrigin(): MorphOrigin? {
        if (coverRect.isEmpty) return null
        return MorphOrigin(
            bookId = book.id,
            title = book.title,
            cardRect = coverRect,
            cardCornerPx = tokens.coverLargeShape.cornerRadiusPx(density),
            cardColor = colors.surfaceContainerHigh,
            coverRect = coverRect,
            cover = cover
        )
    }
    val author = book.author?.takeIf { it.isNotBlank() } ?: "未知作者"
    val progress = if (book.chapterCount > 0) {
        val current = (book.lastChapterIdx + 1).coerceIn(1, book.chapterCount)
        "$current/${book.chapterCount} 章"
    } else {
        "未分章"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(tokens.cardShape)
            .combinedClickable(
                role = Role.Button,
                onClickLabel = "打开书籍",
                onLongClickLabel = "删除书籍",
                onLongClick = onLongClick,
                onClick = { onClick(buildOrigin()) }
            )
    ) {
        BookCover(
            book = book,
            cover = cover,
            hasCover = coverPath != null && CoverCache.fileExists(coverPath),
            onCoverBounds = { coverRect = it },
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = book.title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = "$author · $progress",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun BookCover(
    book: Book,
    cover: ImageBitmap?,
    hasCover: Boolean,
    onCoverBounds: ((Rect) -> Unit)? = null,
    modifier: Modifier
) {
    val tokens = BookVoiceDesign.tokens
    Box(
        modifier = modifier
            .clip(tokens.coverLargeShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .then(
                if (onCoverBounds == null) {
                    Modifier
                } else {
                    Modifier.onGloballyPositioned { onCoverBounds(it.boundsInWindow()) }
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        when {
            cover != null -> Image(
                bitmap = cover,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            // 有封面但还在解码：中性色块，不要先闪一下 EPUB 文字。
            hasCover -> Unit
            else -> Text(
                text = book.format.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EmptyShelf(onImport: () -> Unit, enabled: Boolean, language: AppLanguage) {
    val english = language == AppLanguage.En
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(if (english) "Your shelf is empty" else "书架还是空的", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                if (english) "Import a TXT or EPUB to start listening" else "导入 TXT 或 EPUB 开始听书",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onImport,
                enabled = enabled,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(if (english) "Import TXT / EPUB" else "导入 TXT / EPUB")
            }
        }
    }
}

@Composable
private fun ShelfFab(onImport: () -> Unit, enabled: Boolean, language: AppLanguage) {
    val tokens = BookVoiceDesign.tokens
    ExtendedFloatingActionButton(
        onClick = { if (enabled) onImport() },
        icon = { Icon(Icons.Filled.Add, contentDescription = null) },
        text = { Text(if (language == AppLanguage.En) "Import" else "导入") },
        shape = tokens.cardShape,
        containerColor = if (enabled) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        contentColor = if (enabled) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        elevation = FloatingActionButtonDefaults.elevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
            focusedElevation = 0.dp,
            hoveredElevation = 0.dp
        )
    )
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844, name = "书架 · Clean")
@Composable
private fun ShelfScreenPreview() {
    BookVoiceTheme(darkTheme = false, dynamicColor = false) {
        ShelfContent(
            books = previewBooks,
            importTask = null,
            gridState = rememberLazyGridState(),
            topBarState = rememberTopAppBarState(),
            onOpen = { _, _ -> },
            onLongClick = {},
            onImport = {},
            onCancelImport = {},
            onRetryImport = {},
            onDismissImport = {},
            onOpenSettings = {}
        )
    }
}

private val previewBooks = listOf(
    Book(
        id = "preview-1",
        title = "夜航西飞",
        author = "柏瑞尔·马卡姆",
        format = "EPUB",
        filePath = "",
        coverPath = null,
        chapterCount = 24,
        charCount = 128_000,
        addedAt = 0L,
        lastOpenedAt = 0L,
        lastChapterIdx = 8,
        lastBlockIdx = 0,
        lastOffsetMs = 0L
    ),
    Book(
        id = "preview-2",
        title = "山茶文具店",
        author = "小川糸",
        format = "TXT",
        filePath = "",
        coverPath = null,
        chapterCount = 18,
        charCount = 96_000,
        addedAt = 0L,
        lastOpenedAt = 0L,
        lastChapterIdx = 0,
        lastBlockIdx = 0,
        lastOffsetMs = 0L
    ),
    Book(
        id = "preview-3",
        title = "漫长的告别",
        author = "雷蒙德·钱德勒",
        format = "EPUB",
        filePath = "",
        coverPath = null,
        chapterCount = 42,
        charCount = 260_000,
        addedAt = 0L,
        lastOpenedAt = 0L,
        lastChapterIdx = 17,
        lastBlockIdx = 0,
        lastOffsetMs = 0L
    ),
    Book(
        id = "preview-4",
        title = "听书测试书",
        author = null,
        format = "TXT",
        filePath = "",
        coverPath = null,
        chapterCount = 0,
        charCount = 12_000,
        addedAt = 0L,
        lastOpenedAt = 0L,
        lastChapterIdx = 0,
        lastBlockIdx = 0,
        lastOffsetMs = 0L
    )
)
