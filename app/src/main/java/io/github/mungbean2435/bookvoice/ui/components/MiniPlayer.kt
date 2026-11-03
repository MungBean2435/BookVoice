package io.github.mungbean2435.bookvoice.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mungbean2435.bookvoice.data.db.Book
import io.github.mungbean2435.bookvoice.playback.PlayerState
import io.github.mungbean2435.bookvoice.playback.Speech
import io.github.mungbean2435.bookvoice.ui.morph.MorphOrigin
import io.github.mungbean2435.bookvoice.ui.components.CoverCache
import io.github.mungbean2435.bookvoice.ui.morph.cornerRadiusPx
import io.github.mungbean2435.bookvoice.ui.player.TransportGlyph
import io.github.mungbean2435.bookvoice.ui.player.TransportGlyphIcon
import io.github.mungbean2435.bookvoice.ui.theme.BookVoiceDesign
import io.github.mungbean2435.bookvoice.ui.theme.BookVoiceTheme

/**
 * 悬浮 mini player：只负责展示与播放/暂停，不接管播放状态机。
 * 宿主负责 16dp 左右边距和底部 6dp 间距。
 *
 * 封面用与书架同一份 [Book.coverPath] 和同一个内存缓存，切书立刻换图；
 * 卡片变形的起点就是左侧那格封面，所以 [onOpen] 会把起点快照一起带出去。
 */
@Composable
fun MiniPlayer(
    book: Book?,
    onOpen: (MorphOrigin?) -> Unit,
    modifier: Modifier = Modifier,
    onStart: () -> Unit = {},
    onPullStart: ((MorphOrigin?) -> Unit)? = null,
    onPull: ((Float) -> Unit)? = null,
    onPullEnd: (() -> Unit)? = null,
    onBounds: ((Rect) -> Unit)? = null
) {
    val player = remember { Speech.get() }
    val state by player.state.collectAsStateWithLifecycle()
    val progress by player.progress.collectAsStateWithLifecycle()

    // 胶囊常驻：没有书可显示时才不画（书被删掉 / 还没打开过任何书）。
    if (book == null) return
    val idle = state is PlayerState.Idle

    val tokens = BookVoiceDesign.tokens
    val colors = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val coverPath = book.coverPath?.takeIf { it.isNotBlank() }
    val hasCover = coverPath != null && CoverCache.fileExists(coverPath)

    var containerRect by remember { mutableStateOf(Rect.Zero) }
    // 显示的图和变形带走的是同一个对象，起飞瞬间不会跳。
    val cover = rememberCoverBitmap(coverPath, with(density) { 40.dp.roundToPx() })

    // 和书架入口同一套：起点是整颗胶囊的容器矩形，不带封面飞行。
    fun buildOrigin(): MorphOrigin? {
        if (containerRect.isEmpty) return null
        return MorphOrigin(
            bookId = book.id,
            title = book.title,
            cardRect = containerRect,
            cardCornerPx = tokens.miniPlayerShape.cornerRadiusPx(density),
            cardColor = colors.surfaceContainerHigh,
            coverRect = Rect.Zero,
            cover = null
        )
    }

    val fraction = progress
        ?.takeIf { it.totalChars > 0 }
        ?.let { (it.charsBefore.toFloat() / it.totalChars.toFloat()).coerceIn(0f, 1f) }
        ?: 0f

    MiniPlayerContent(
        title = book.title.ifBlank { player.bookTitle.ifBlank { "正在播放" } },
        // 没在播时显示这本书上次读到第几章，别露出别的书的章节名。
        chapter = if (idle) "第 ${book.lastChapterIdx + 1} 章" else player.chapterTitle,
        format = book.format,
        fraction = fraction,
        isPlaying = state is PlayerState.Playing,
        cover = cover,
        hasCover = hasCover,
        onContainerBounds = { rect ->
            containerRect = rect
            onBounds?.invoke(rect)
        },
        onToggle = { if (idle) onStart() else player.togglePause() },
        onOpen = { onOpen(buildOrigin()) },
        onPullStart = onPullStart?.let { start -> { start(buildOrigin()) } },
        onPull = onPull,
        onPullEnd = onPullEnd,
        modifier = modifier
    )
}

@Composable
internal fun MiniPlayerContent(
    title: String,
    chapter: String,
    format: String,
    fraction: Float,
    isPlaying: Boolean,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    cover: ImageBitmap? = null,
    hasCover: Boolean = false,
    onCoverBounds: ((Rect) -> Unit)? = null,
    onContainerBounds: ((Rect) -> Unit)? = null,
    onPullStart: (() -> Unit)? = null,
    onPull: ((Float) -> Unit)? = null,
    onPullEnd: (() -> Unit)? = null
) {
    val tokens = BookVoiceDesign.tokens
    val colors = MaterialTheme.colorScheme
    val currentOpen by rememberUpdatedState(onOpen)
    val currentPullStart by rememberUpdatedState(onPullStart)
    val currentPull by rememberUpdatedState(onPull)
    val currentPullEnd by rememberUpdatedState(onPullEnd)
    val tapModifier = Modifier.pointerInput(Unit) {
        detectTapGestures(onTap = { currentOpen() })
    }
    val pullModifier = if (onPullStart != null && onPull != null && onPullEnd != null) {
        Modifier.pointerInput(Unit) {
            detectVerticalDragGestures(
                onDragStart = { currentPullStart?.invoke() },
                onVerticalDrag = { change, amount ->
                    change.consume()
                    currentPull?.invoke(amount)
                },
                onDragEnd = { currentPullEnd?.invoke() },
                onDragCancel = { currentPullEnd?.invoke() }
            )
        }
    } else {
        Modifier
    }

    GlassSurface(
        modifier = modifier
            .fillMaxWidth()
            .height(tokens.miniPlayerHeight)
            // 整颗胶囊的窗口矩形：书架入口的变形从这里起飞。
            .then(
                if (onContainerBounds == null) {
                    Modifier
                } else {
                    Modifier.onGloballyPositioned { onContainerBounds(it.boundsInWindow()) }
                }
            ),
        shape = tokens.miniPlayerShape
    ) {
        Box(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .then(pullModifier)
                    .then(tapModifier)
                    .padding(start = 12.dp, end = 4.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(tokens.coverSmallShape)
                        .background(colors.surfaceContainerHigh)
                        .then(
                            if (onCoverBounds == null) {
                                Modifier
                            } else {
                                Modifier.onGloballyPositioned { onCoverBounds(it.boundsInWindow()) }
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (cover != null) {
                        Image(
                            bitmap = cover,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else if (!hasCover) {
                        // 确实没有封面才退到格式文字；有封面但还在解码时留中性色块。
                        Text(
                            text = format.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = chapter,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onToggle) {
                    TransportGlyphIcon(
                        glyph = if (isPlaying) TransportGlyph.Pause else TransportGlyph.Play,
                        tint = colors.onSurface,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(colors.surfaceContainerHighest)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .background(colors.primary)
                )
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 96, name = "mini player · Clean")
@Composable
private fun MiniPlayerPreview() {
    BookVoiceTheme(darkTheme = false, dynamicColor = false) {
        Box(Modifier.padding(16.dp)) {
            MiniPlayerContent(
                title = "夜航西飞",
                chapter = "第三章 · 夜航",
                format = "EPUB",
                fraction = 0.42f,
                isPlaying = true,
                onToggle = {},
                onOpen = {},
                cover = null,
                hasCover = false
            )
        }
    }
}
