package io.github.mungbean2435.bookvoice.ui.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.mungbean2435.bookvoice.data.db.Bookmark
import io.github.mungbean2435.bookvoice.data.db.Chapter
import io.github.mungbean2435.bookvoice.data.settings.TtsConfig
import io.github.mungbean2435.bookvoice.tts.ProviderRegistry
import io.github.mungbean2435.bookvoice.tts.Voice
import io.github.mungbean2435.bookvoice.ui.theme.ContinuousRoundedCornerShape
import io.github.mungbean2435.bookvoice.ui.theme.BookVoiceDesign
import kotlinx.coroutines.launch
import io.github.mungbean2435.bookvoice.ui.theme.BookVoiceTheme

private val QueueRowShape = 20.dp

/**
 * 播放抽屉里的页。播放设置是根页，音色 / 定时 / 离线缓存从根页横滑进来。
 * [depth] 只用来判断转场方向：从浅页进深页向右滑入，返回原路滑回。
 */
internal enum class PlayerSheetPage(val depth: Int) {
    Settings(0),
    Voice(1),
    Sleep(1),
    Offline(1)
}

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
    // 和整页目录一致：打开时定位到当前章，而不是从第 1 章滚起。
    val initialChapter = currentChapter.coerceIn(0, chapters.lastIndex.coerceAtLeast(0))
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialChapter)
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
                    state = listState,
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


/** 定时页内容：用作播放抽屉里的子页，外壳由 [io.github.mungbean2435.bookvoice.ui.player.PlaybackSettingsSheet] 提供。 */
@Composable
internal fun SleepTimerContent(
    sleepMinutes: Int,
    chapterEnd: Boolean,
    onPickMinutes: (Int) -> Unit,
    onPickChapterEnd: () -> Unit,
    onClear: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
    ) {
        SheetBackHeader(title = "设置超时", onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 600.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            SleepOption(
                label = "本章结束",
                selected = chapterEnd,
                onClick = onPickChapterEnd
            )
            listOf(1, 3, 5, 10, 20, 30, 45).forEach { minutes ->
                SleepOption(
                    label = "$minutes 分钟",
                    selected = !chapterEnd && sleepMinutes == minutes,
                    onClick = { onPickMinutes(minutes) }
                )
            }
            listOf(1, 2, 3, 4, 6, 8).forEach { hours ->
                SleepOption(
                    label = "$hours 小时",
                    selected = !chapterEnd && sleepMinutes == hours * 60,
                    onClick = { onPickMinutes(hours * 60) }
                )
            }
            if (chapterEnd || sleepMinutes > 0) {
                TextButton(
                    onClick = onClear,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                ) {
                    Text("关闭定时", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun SleepOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium
        )
        if (selected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = "已选择",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VoicePickerContent(
    configs: List<TtsConfig>,
    activeConfigId: String,
    onSelectConfig: (configId: String) -> Unit,
    onSelect: (configId: String, voiceId: String) -> Unit,
    onManage: (configId: String) -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    val initialId = configs.firstOrNull { it.id == activeConfigId }?.id
        ?: configs.firstOrNull()?.id.orEmpty()
    var selectedConfigId by remember(initialId, configs.size) { mutableStateOf(initialId) }
    var manualVoice by remember(selectedConfigId) { mutableStateOf("") }
    val selected = configs.firstOrNull { it.id == selectedConfigId }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
    ) {
            SheetBackHeader(title = "选择音色", onBack = onDismiss)
            if (configs.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("还没有 TTS 配置", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "先添加一条配置，再回来选音色。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(14.dp))
                    TextButton(onClick = onOpenSettings) { Text("去添加配置") }
                }
                return@Column
            }

            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(configs, key = { it.id }) { config ->
                    val isSelected = config.id == selectedConfigId
                    Surface(
                        onClick = {
                            selectedConfigId = config.id
                            onSelectConfig(config.id)
                        },
                        shape = RoundedCornerShape(50),
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        contentColor = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    ) {
                        Text(
                            config.name,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 12.dp)
            ) {
                val voices = selected?.let { config ->
                    config.voices.ifEmpty {
                        ProviderRegistry.get(config.providerId)?.voices().orEmpty()
                    }
                }.orEmpty()
                if (voices.isEmpty()) {
                    Text(
                        "这条配置还没有音色库，可以直接填一个音色 ID。",
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = manualVoice,
                        onValueChange = { manualVoice = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("音色 ID，例如 alloy / zh-CN-XiaoxiaoNeural") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        trailingIcon = {
                            TextButton(
                                onClick = {
                                    val id = manualVoice.trim()
                                    if (id.isNotEmpty()) {
                                        onSelect(selectedConfigId, id)
                                        manualVoice = ""
                                    }
                                },
                                enabled = manualVoice.isNotBlank()
                            ) { Text("使用") }
                        }
                    )
                } else {
                    Text(
                        "选中后立即用于朗读；语音会从这里切换。",
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (selected?.providerId == "system") {
                        Text(
                            "注意：部分系统引擎（如 OnePlus/OPPO 的 TTS Accessibility Engine）" +
                                "只认系统设置里的发音人，应用内切换会返回成功但声音不变。",
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                    voices.chunked(2).forEach { rowVoices ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            rowVoices.forEach { voice ->
                                VoiceCard(
                                    voice = voice,
                                    selected = voice.id == selected?.voice,
                                    modifier = Modifier.weight(1f),
                                    onClick = { onSelect(selectedConfigId, voice.id) }
                                )
                            }
                            if (rowVoices.size == 1) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                }

                TextButton(
                    onClick = { onManage(selectedConfigId) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                ) {
                    Text("管理音色 ›")
                }
            }
    }
}

@Composable
private fun VoiceCard(
    voice: Voice,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        border = if (selected) {
            BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 74.dp)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    voice.label.ifBlank { voice.id },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (voice.hint.isNotBlank()) {
                    Text(
                        voice.hint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (selected) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = "当前音色",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/** 离线缓存页内容：用作播放抽屉里的子页，外壳由播放抽屉提供。 */
@Composable
internal fun OfflineCacheContent(
    chapters: List<Chapter>,
    currentChapter: Int,
    cacheRunning: Boolean,
    cacheChapterIdx: Int,
    cacheProgress: Pair<Int, Int>,
    cachedChapters: Set<Int>,
    onDownload: (Int) -> Unit,
    onCancel: () -> Unit,
    onBack: () -> Unit
) {
    val tokens = BookVoiceDesign.tokens
    val rowShape = if (tokens.isClean) {
        ContinuousRoundedCornerShape(QueueRowShape)
    } else {
        RoundedCornerShape(QueueRowShape)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
    ) {
        SheetBackHeader(title = "离线音频", onBack = onBack)
        if (cacheRunning) {
            LinearProgressIndicator(
                progress = {
                    if (cacheProgress.second <= 0) 0f
                    else cacheProgress.first.toFloat() / cacheProgress.second
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            )
            Text(
                text = if (cacheProgress.second > 0) {
                    "正在缓存第 ${cacheChapterIdx + 1} 章 · ${cacheProgress.first}/${cacheProgress.second}"
                } else {
                    "正在缓存第 ${cacheChapterIdx + 1} 章…"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
            )
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
        ) {
            items(chapters, key = { it.idx }) { chapter ->
                val runningThis = cacheRunning && cacheChapterIdx == chapter.idx
                val cached = chapter.idx in cachedChapters
                val enabled = !cacheRunning || runningThis
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .alpha(if (cacheRunning && !runningThis) 0.45f else 1f)
                        .clip(rowShape)
                        .clickable(enabled = enabled && !cached) {
                            onDownload(chapter.idx)
                        }
                        .padding(start = 16.dp, end = 4.dp)
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = chapter.title.ifBlank { "第 ${chapter.idx + 1} 章" },
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = buildString {
                                append("第 ${chapter.idx + 1} 章")
                                if (chapter.idx == currentChapter) append(" · 当前播放")
                                if (cached) append(" · 已缓存")
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    when {
                        runningThis -> {
                            Text(
                                text = if (cacheProgress.second > 0) {
                                    "${cacheProgress.first}/${cacheProgress.second}"
                                } else {
                                    "…"
                                },
                                style = MaterialTheme.typography.labelSmall
                            )
                            IconButton(onClick = onCancel) {
                                Icon(Icons.Filled.Close, contentDescription = "停止缓存")
                            }
                        }

                        cached -> Icon(
                            Icons.Filled.Check,
                            contentDescription = "已缓存",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )

                        else -> IconButton(
                            onClick = { onDownload(chapter.idx) },
                            enabled = !cacheRunning
                        ) {
                            DownloadGlyph(
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
        Text(
            text = "一次缓存一章；缓存完成后可在飞行模式下朗读。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp)
        )
    }
}

@Composable
private fun DownloadGlyph(tint: Color) {
    Canvas(Modifier.size(24.dp)) {
        val stroke = 2.dp.toPx()
        val cx = size.width / 2f
        drawLine(
            tint,
            Offset(cx, 3.dp.toPx()),
            Offset(cx, 15.dp.toPx()),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            tint,
            Offset(cx - 5.dp.toPx(), 10.dp.toPx()),
            Offset(cx, 15.dp.toPx()),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            tint,
            Offset(cx + 5.dp.toPx(), 10.dp.toPx()),
            Offset(cx, 15.dp.toPx()),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            tint,
            Offset(4.dp.toPx(), 20.dp.toPx()),
            Offset(size.width - 4.dp.toPx(), 20.dp.toPx()),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun SheetBackHeader(title: String, onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.CenterStart)
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
        }
        Text(
            text = title,
            modifier = Modifier.align(Alignment.Center),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
    }
}
