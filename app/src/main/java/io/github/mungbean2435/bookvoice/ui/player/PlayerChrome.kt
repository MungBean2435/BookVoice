package io.github.mungbean2435.bookvoice.ui.player

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import io.github.mungbean2435.bookvoice.ui.components.CoverCache
import io.github.mungbean2435.bookvoice.ui.components.rememberCoverBitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.mungbean2435.bookvoice.data.db.Book
import io.github.mungbean2435.bookvoice.ui.components.FileImage
import io.github.mungbean2435.bookvoice.ui.theme.BookVoiceDesign
import io.github.mungbean2435.bookvoice.ui.theme.BookVoiceTheme

/** 阅读字号范围：最小 12sp，避免 15sp 起步在小屏上仍然偏大。 */
internal const val MIN_READING_FONT_SP = 12
internal const val MAX_READING_FONT_SP = 24

internal data class PlayerChromeState(
    val fraction: Float,
    val elapsed: String,
    val remaining: String,
    val isPlaying: Boolean,
    val isBuffering: Boolean,
    val waitingNetwork: Boolean,
    val speedText: String,
    val voiceText: String,
    val configName: String,
    val sleepText: String,
    val cacheText: String,
    val cacheRunning: Boolean
)

/**
 * 展开态顶栏：只有 48dp 操作行，不再有章节名那一排（章节名只在收起态出现）。
 * 背景和底部分隔线由 PlayerScreen 的固定高度顶栏槽统一画。
 */
/**
 * 播放失败时留在正文上方的常驻提示。Toast 会消失，错误不能跟着消失；
 * 这里保留「重试」和「关闭」，由用户决定下一步。
 */
@Composable
internal fun PlayerErrorBanner(
    message: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "播放中断",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            TextButton(
                onClick = onRetry,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            ) {
                Text("重试")
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Filled.Close, contentDescription = "关闭")
            }
        }
    }
}

private val TapToReadIcon: ImageVector = ImageVector.Builder(
    name = "TapToRead",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f
).addPath(
    pathData = PathParser().parsePathString(
        "M9 11.24V7.5C9 6.12 10.12 5 11.5 5S14 6.12 14 7.5v3.74c1.21-.81 2-2.18 2-3.74" +
            "C16 5.01 13.99 3 11.5 3S7 5.01 7 7.5c0 1.56.79 2.93 2 3.74zm9.84 4.63l-4.54-2.26" +
            "c-.17-.07-.35-.11-.54-.11H13v-6c0-.83-.67-1.5-1.5-1.5S10 6.67 10 7.5v10.74l-3.43-.72" +
            "c-.08-.01-.15-.03-.24-.03-.31 0-.59.13-.79.33l-.79.8 4.94 4.94c.27.27.65.44 1.06.44" +
            "h6.79c.75 0 1.33-.55 1.44-1.28l.75-5.27c.01-.07.02-.14.02-.2 0-.62-.38-1.16-.91-1.38z"
    ).toNodes(),
    fill = SolidColor(Color.Black)
).build()

@Composable
internal fun PlayerTopBar(
    tapToRead: Boolean,
    onTapToReadChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onOpenChapters: () -> Unit,
    onOpenReadingSettings: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenMenu: () -> Unit,
    onClose: () -> Unit,
    menu: @Composable () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val height = BookVoiceDesign.reading.topChromeHeight

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
        }

        Spacer(Modifier.weight(1f))

        TextButton(
            onClick = onOpenChapters,
            modifier = Modifier
                .height(height)
                .semantics { contentDescription = "目录" },
            contentPadding = PaddingValues(horizontal = 8.dp),
            colors = ButtonDefaults.textButtonColors(
                contentColor = colors.onSurfaceVariant
            )
        ) {
            Text("目录", style = MaterialTheme.typography.labelLarge)
        }

        TextButton(
            onClick = onOpenBookmarks,
            modifier = Modifier
                .height(height)
                .semantics { contentDescription = "书签" },
            contentPadding = PaddingValues(horizontal = 8.dp),
            colors = ButtonDefaults.textButtonColors(
                contentColor = colors.onSurfaceVariant
            )
        ) {
            Text("书签", style = MaterialTheme.typography.labelLarge)
        }

        IconButton(
            onClick = { onTapToReadChange(!tapToRead) },
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                imageVector = TapToReadIcon,
                contentDescription = if (tapToRead) "关闭点读" else "开启点读",
                tint = if (tapToRead) {
                    colors.primary
                } else {
                    colors.onSurfaceVariant.copy(alpha = 0.55f)
                }
            )
        }

        Box {
            TextButton(
                onClick = onOpenReadingSettings,
                modifier = Modifier
                    .height(height)
                    .semantics { contentDescription = "阅读设置" },
                contentPadding = PaddingValues(horizontal = 8.dp),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = colors.onSurfaceVariant
                )
            ) {
                Text("Aa", style = MaterialTheme.typography.labelLarge)
            }
        }

        Box {
            IconButton(onClick = onOpenMenu) {
                Icon(Icons.Filled.MoreVert, contentDescription = "更多")
            }
            menu()
        }

        Box(
            modifier = Modifier
                .size(height)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onClose)
                .semantics { contentDescription = "收起控制栏" },
            contentAlignment = Alignment.Center
        ) {
            Text(
                "✕",
                style = MaterialTheme.typography.titleMedium,
                color = colors.onSurfaceVariant
            )
        }
    }
}

/**
 * 收起态顶栏：和展开态同高的空白行，左侧小号章节名，右侧网络/缓冲等状态。
 * 不压在正文上；正文从这个固定高度下面开始滚动。
 */
@Composable
internal fun CollapsedChapterRow(
    chapterTitle: String,
    statusText: String,
    statusIsError: Boolean,
    modifier: Modifier = Modifier,
    coverPath: String? = null,
    coverFormat: String = "",
    onCoverBounds: ((Rect) -> Unit)? = null
) {
    val height = BookVoiceDesign.reading.topChromeHeight
    val tokens = BookVoiceDesign.tokens
    val density = LocalDensity.current
    val path = coverPath?.takeIf { it.isNotBlank() }
    val hasCover = path != null && CoverCache.fileExists(path)
    // 和书架 / 胶囊共用同一份缓存，卡片变形飞到这里时像素完全一致。
    val cover = rememberCoverBitmap(path, with(density) { 40.dp.roundToPx() })

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 顶部小封面：卡片变形的落点。收起态一直在这里，正文滚到哪都可以缩回它。
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(tokens.coverSmallShape)
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
            if (cover != null) {
                Image(
                    bitmap = cover,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else if (!hasCover) {
                Text(
                    text = coverFormat.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = chapterTitle.ifBlank { "正文" },
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (statusText.isNotBlank()) {
            Spacer(Modifier.width(12.dp))
            Text(
                text = statusText,
                style = MaterialTheme.typography.labelSmall,
                color = if (statusIsError) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun PlayerBookHeader(
    book: Book,
    onOpenChapters: () -> Unit,
    onOpenBookmarks: () -> Unit,
    modifier: Modifier = Modifier,
    onCoverBounds: ((Rect) -> Unit)? = null
) {
    val tokens = BookVoiceDesign.tokens
    val targetPx = with(LocalDensity.current) { 96.dp.roundToPx() }

    // 头部被滚出屏幕（列表回收）时把锚点撤掉：变形层据此改成「跟着卡片淡出」，
    // 而不是继续飞一个已经不存在的位置。
    if (onCoverBounds != null) {
        DisposableEffect(Unit) {
            onDispose { onCoverBounds(Rect.Zero) }
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 16.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
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
            Text(
                text = book.format.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            book.coverPath?.let { path ->
                FileImage(
                    path = path,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    targetWidthPx = targetPx
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = book.title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = book.author?.takeIf { it.isNotBlank() } ?: "未知作者",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Row {
                TextButton(
                    onClick = onOpenChapters,
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Text("目录", style = MaterialTheme.typography.labelLarge)
                }
                TextButton(
                    onClick = onOpenBookmarks,
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Text("书签", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
internal fun PlayerBottomBar(
    state: PlayerChromeState,
    onSeek: (Float) -> Unit,
    onSeekFinished: () -> Unit,
    onToggle: () -> Unit,
    onPrevChapter: () -> Unit,
    onPrevBlock: () -> Unit,
    onNextBlock: () -> Unit,
    onNextChapter: () -> Unit,
    onSpeed: () -> Unit,
    onVoice: () -> Unit,
    onSleep: () -> Unit,
    onCache: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxWidth()) {
        if (state.waitingNetwork) {
            Text(
                "网络中断，等待恢复…",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
        }

        Text(
            "全书进度 · 时长为估算",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 24.dp, top = 8.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                state.elapsed,
                style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                maxLines = 1,
                modifier = Modifier.widthIn(min = 48.dp)
            )
            Slider(
                value = state.fraction,
                onValueChange = onSeek,
                onValueChangeFinished = onSeekFinished,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
                    .semantics { contentDescription = "全书进度" },
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    activeTickColor = Color.Transparent,
                    inactiveTickColor = Color.Transparent
                )
            )
            Text(
                state.remaining,
                style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                maxLines = 1,
                textAlign = TextAlign.End,
                modifier = Modifier.widthIn(min = 56.dp)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TransportButton("上一章", TransportGlyph.PreviousChapter, onPrevChapter)
            TransportButton("上一段", TransportGlyph.PreviousBlock, onPrevBlock)
            PlayButton(
                isPlaying = state.isPlaying,
                isBuffering = state.isBuffering,
                onClick = onToggle
            )
            TransportButton("下一段", TransportGlyph.NextBlock, onNextBlock)
            TransportButton("下一章", TransportGlyph.NextChapter, onNextChapter)
        }

        SecondaryControls(
            state = state,
            onSpeed = onSpeed,
            onVoice = onVoice,
            onSleep = onSleep,
            onCache = onCache
        )

        Spacer(Modifier.height(8.dp))
    }
}

/**
 * 阅读页底部悬浮胶囊：封面 + 书名/章节 + 上一段/播放/下一段/收起，
 * 最底部只读进度线。点封面或书名打开半屏播放设置。
 */
@Composable
internal fun PlayerMiniBar(
    state: PlayerChromeState,
    book: Book?,
    chapterTitle: String,
    onOpenSettings: () -> Unit,
    onClose: () -> Unit,
    onToggle: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = BookVoiceDesign.tokens
    val colors = MaterialTheme.colorScheme
    val reading = tokens.reading

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(tokens.miniPlayerHeight),
        shape = tokens.miniPlayerShape,
        color = colors.surfaceContainerLow,
        shadowElevation = reading.chromeElevation
    ) {
        Box(Modifier.fillMaxSize().clip(tokens.miniPlayerShape)) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = 8.dp,
                        end = 2.dp,
                        bottom = reading.miniBarProgressHeight
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            role = Role.Button,
                            onClickLabel = "播放设置",
                            onClick = onOpenSettings
                        )
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MiniCover(book)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = book?.title ?: "正在播放",
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = buildString {
                                append(chapterTitle.ifBlank { "正文" })
                                if (state.elapsed.isNotBlank()) {
                                    append(" · ")
                                    append(state.elapsed)
                                }
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                MiniTransportButton("上一段", TransportGlyph.PreviousBlock, onPrev)
                MiniPlayButton(state, onToggle)
                MiniTransportButton("下一段", TransportGlyph.NextBlock, onNext)
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable(role = Role.Button, onClick = onClose)
                        .semantics { contentDescription = "收起控制栏" },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "✕",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.onSurfaceVariant
                    )
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(reading.miniBarProgressHeight)
                    .background(colors.surfaceContainerHighest)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(state.fraction.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .background(colors.primary)
                )
            }
        }
    }
}

@Composable
private fun MiniCover(book: Book?) {
    val tokens = BookVoiceDesign.tokens
    val colors = MaterialTheme.colorScheme
    val targetPx = with(LocalDensity.current) { 40.dp.roundToPx() }
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(tokens.coverSmallShape)
            .background(colors.surfaceContainerHigh),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = book?.title?.take(1) ?: "书",
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant
        )
        book?.coverPath?.let { path ->
            FileImage(
                path = path,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                targetWidthPx = targetPx
            )
        }
    }
}

@Composable
private fun MiniTransportButton(
    label: String,
    glyph: TransportGlyph,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        TransportGlyphIcon(
            glyph = glyph,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun MiniPlayButton(state: PlayerChromeState, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val label = when {
        state.isBuffering -> "缓冲中"
        state.isPlaying -> "暂停"
        else -> "播放"
    }
    Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(
                    if (state.isBuffering) colors.primary.copy(alpha = 0.6f) else colors.primary
                )
                .clickable(role = Role.Button, onClick = onClick)
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center
        ) {
            TransportGlyphIcon(
                glyph = if (state.isPlaying || state.isBuffering) {
                    TransportGlyph.Pause
                } else {
                    TransportGlyph.Play
                },
                tint = colors.onPrimary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * 半屏播放设置：语速/音色/定时/缓存本章和完整传输键都在这里，
 * 不再把这一整块常驻在阅读页底部。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlaybackSettingsSheet(
    state: PlayerChromeState,
    autoFollow: Boolean,
    onResumeFollow: () -> Unit,
    onSeek: (Float) -> Unit,
    onSeekFinished: () -> Unit,
    onToggle: () -> Unit,
    onPrevChapter: () -> Unit,
    onPrevBlock: () -> Unit,
    onNextBlock: () -> Unit,
    onNextChapter: () -> Unit,
    onSpeed: () -> Unit,
    onVoice: () -> Unit,
    onSleep: () -> Unit,
    onCache: () -> Unit,
    onDismiss: () -> Unit
) {
    val tokens = BookVoiceDesign.tokens
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val sheetHeight = LocalConfiguration.current.screenHeightDp.dp *
        tokens.reading.playbackSheetHeightFraction
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = tokens.sheetShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(sheetHeight)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "播放设置",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onDismiss) { Text("完成") }
            }
            if (!autoFollow) {
                FilledTonalButton(
                    onClick = onResumeFollow,
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .heightIn(min = 48.dp)
                ) {
                    Text("回到朗读", style = MaterialTheme.typography.labelMedium)
                }
            }
            PlayerBottomBar(
                state = state,
                onSeek = onSeek,
                onSeekFinished = onSeekFinished,
                onToggle = onToggle,
                onPrevChapter = onPrevChapter,
                onPrevBlock = onPrevBlock,
                onNextBlock = onNextBlock,
                onNextChapter = onNextChapter,
                onSpeed = onSpeed,
                onVoice = onVoice,
                onSleep = onSleep,
                onCache = onCache
            )
        }
    }
}

@Composable
private fun TransportButton(
    label: String,
    glyph: TransportGlyph,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        TransportGlyphIcon(
            glyph = glyph,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun PlayButton(
    isPlaying: Boolean,
    isBuffering: Boolean,
    onClick: () -> Unit
) {
    val label = when {
        isBuffering -> "缓冲中，点按暂停"
        isPlaying -> "暂停"
        else -> "播放"
    }
    Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
        if (isBuffering) {
            val transition = rememberInfiniteTransition(label = "buffer")
            val angle by transition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
                label = "angle"
            )
            val ringColor = MaterialTheme.colorScheme.primary
            Canvas(Modifier.size(72.dp)) {
                drawArc(
                    color = ringColor.copy(alpha = 0.25f),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                )
                drawArc(
                    color = ringColor,
                    startAngle = angle,
                    sweepAngle = 270f,
                    useCenter = false,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center
        ) {
            TransportGlyphIcon(
                glyph = if (isPlaying || isBuffering) TransportGlyph.Pause else TransportGlyph.Play,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

private data class SecondaryItem(
    val label: String,
    val value: String,
    val onClick: () -> Unit
)

@Composable
private fun SecondaryControls(
    state: PlayerChromeState,
    onSpeed: () -> Unit,
    onVoice: () -> Unit,
    onSleep: () -> Unit,
    onCache: () -> Unit
) {
    val items = listOf(
        SecondaryItem("语速", state.speedText, onSpeed),
        SecondaryItem(
            if (state.configName.isBlank()) "音色" else "音色 · ${state.configName}",
            state.voiceText,
            onVoice
        ),
        SecondaryItem("定时", state.sleepText, onSleep),
        SecondaryItem(
            if (state.cacheRunning) "停止缓存" else "缓存本章",
            state.cacheText,
            onCache
        )
    )
    val twoColumns = LocalDensity.current.fontScale > 1.3f

    if (!twoColumns) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items.forEach { item ->
                SecondaryControl(item, Modifier.weight(1f))
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items.chunked(2).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    row.forEach { item ->
                        SecondaryControl(item, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun SecondaryControl(item: SecondaryItem, modifier: Modifier) {
    Column(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(role = Role.Button, onClick = item.onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            item.label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        Text(
            item.value,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReadingSettingsSheet(
    fontSizeSp: Int,
    onFontSizeChange: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
        ) {
            Text("阅读设置", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("字号", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "$fontSizeSp sp · $MIN_READING_FONT_SP–$MAX_READING_FONT_SP",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = { onFontSizeChange(fontSizeSp - 1) },
                    enabled = fontSizeSp > MIN_READING_FONT_SP,
                    modifier = Modifier.semantics { contentDescription = "减小字号" }
                ) {
                    Text("A-", style = MaterialTheme.typography.labelLarge)
                }
                Text(
                    "$fontSizeSp",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                IconButton(
                    onClick = { onFontSizeChange(fontSizeSp + 1) },
                    enabled = fontSizeSp < MAX_READING_FONT_SP,
                    modifier = Modifier.semantics { contentDescription = "增大字号" }
                ) {
                    Text("A+", style = MaterialTheme.typography.labelLarge)
                }
            }

            HorizontalDivider()

            Text(
                "点读开关在顶栏书签旁边：图标亮起时，点击正文从该段开始朗读。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 120, name = "播放顶栏 · Clean")
@Composable
private fun PlayerTopBarPreview() {
    BookVoiceTheme(darkTheme = false, dynamicColor = false) {
        Box(Modifier.background(MaterialTheme.colorScheme.background)) {
            PlayerTopBar(
                tapToRead = true,
                onTapToReadChange = {},
                onBack = {},
                onOpenChapters = {},
                onOpenReadingSettings = {},
                onOpenBookmarks = {},
                onOpenMenu = {},
                onClose = {},
                menu = {}
            )
        }
    }
}

private val previewPlayerBook = Book(
    id = "preview-player",
    title = "夜航西飞",
    author = "柏瑞尔·马卡姆",
    format = "EPUB",
    filePath = "",
    coverPath = null,
    chapterCount = 24,
    charCount = 128_000,
    addedAt = 0L,
    lastOpenedAt = 0L,
    lastChapterIdx = 2,
    lastBlockIdx = 0,
    lastOffsetMs = 0L
)

@Preview(showBackground = true, widthDp = 390, heightDp = 180, name = "播放页书卡 · Clean")
@Composable
private fun PlayerBookHeaderPreview() {
    BookVoiceTheme(darkTheme = false, dynamicColor = false) {
        Box(Modifier.background(MaterialTheme.colorScheme.background).padding(16.dp)) {
            PlayerBookHeader(
                book = previewPlayerBook,
                onOpenChapters = {},
                onOpenBookmarks = {}
            )
        }
    }
}
