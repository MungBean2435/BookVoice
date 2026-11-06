@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package io.github.mungbean2435.bookvoice.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mungbean2435.bookvoice.data.settings.AppInfo
import io.github.mungbean2435.bookvoice.data.settings.DiagnosticsSnapshot
import io.github.mungbean2435.bookvoice.data.settings.Metrics
import io.github.mungbean2435.bookvoice.data.settings.Settings
import io.github.mungbean2435.bookvoice.data.settings.exportText
import io.github.mungbean2435.bookvoice.data.settings.formatEvent
import io.github.mungbean2435.bookvoice.data.settings.summaryText
import io.github.mungbean2435.bookvoice.playback.AudioCache
import io.github.mungbean2435.bookvoice.playback.CacheStats
import io.github.mungbean2435.bookvoice.playback.DiagnosticEvent
import io.github.mungbean2435.bookvoice.playback.DiagnosticLevel
import io.github.mungbean2435.bookvoice.playback.PlayerState
import io.github.mungbean2435.bookvoice.playback.Speech
import io.github.mungbean2435.bookvoice.tts.ProviderRegistry
import io.github.mungbean2435.bookvoice.tts.system.SystemTtsLog
import io.github.mungbean2435.bookvoice.ui.components.BookVoicePageScaffold
import io.github.mungbean2435.bookvoice.ui.components.LocalFloatingChromeReserve
import io.github.mungbean2435.bookvoice.ui.nav.PageChromeState
import io.github.mungbean2435.bookvoice.ui.theme.BookVoiceDesign
import io.github.mungbean2435.bookvoice.ui.theme.SettingsTone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
private fun SettingsDetailScaffold(
    title: String,
    onBack: () -> Unit,
    state: PageChromeState,
    content: @Composable ColumnScope.() -> Unit
) {
    val tokens = BookVoiceDesign.tokens
    val chromeReserve = LocalFloatingChromeReserve.current

    BookVoicePageScaffold(
        title = title,
        state = state.topBar,
        onBack = onBack
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            state = state.list,
            contentPadding = PaddingValues(
                start = tokens.pageHorizontalPadding,
                end = tokens.pageHorizontalPadding,
                top = 8.dp,
                bottom = 32.dp + chromeReserve
            )
        ) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    content = content
                )
            }
        }
    }
}

@Composable
fun PlaybackSettingsScreen(onBack: () -> Unit, state: PageChromeState) {
    val current by Settings.state.collectAsStateWithLifecycle()
    var speed by remember(current.speed) { mutableFloatStateOf(current.speed) }
    var gapParagraph by remember(current.gapParagraphMs) {
        mutableFloatStateOf(current.gapParagraphMs.toFloat())
    }

    fun persist() {
        Settings.update { old ->
            old.copy(
                speed = speed,
                gapParagraphMs = gapParagraph.toInt()
            )
        }
    }

    SettingsDetailScaffold(title = "播放设置", onBack = onBack, state = state) {
        SettingsCard {
            SettingsSliderRow(
                title = "语速",
                value = String.format(Locale.US, "%.2f×", speed),
                sliderValue = speed,
                valueRange = 0.5f..2f,
                onValueChange = { speed = it },
                onValueChangeFinished = {
                    persist()
                    Speech.get().applySpeed(speed)
                }
            )
            SettingsDivider(start = 16.dp)
            SettingsSliderRow(
                title = "段落间停顿",
                value = "${gapParagraph.toInt()} ms",
                supporting = "每 400 字左右一次",
                sliderValue = gapParagraph,
                valueRange = 0f..1200f,
                onValueChange = { gapParagraph = it },
                onValueChangeFinished = { persist() }
            )
        }
        SettingsNote("拖动滑块松手即保存；语速保存后立即作用于正在播放的音频。")
    }
}

@Composable
fun StorageSettingsScreen(onBack: () -> Unit, state: PageChromeState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val current by Settings.state.collectAsStateWithLifecycle()
    var cacheMb by remember { mutableLongStateOf(0L) }
    var cacheFiles by remember { mutableIntStateOf(0) }
    var cacheLimit by remember(current.cacheLimitMb) {
        mutableFloatStateOf(current.cacheLimitMb.toFloat())
    }
    var showClear by remember { mutableStateOf(false) }

    suspend fun refreshCache() {
        val cache = AudioCache(context)
        cacheMb = withContext(Dispatchers.IO) { cache.totalBytes() } / (1024 * 1024)
        cacheFiles = withContext(Dispatchers.IO) { cache.fileCount() }
    }

    LaunchedEffect(Unit) { refreshCache() }

    fun persist() {
        Settings.update { it.copy(cacheLimitMb = cacheLimit.toInt()) }
    }

    SettingsDetailScaffold(title = "存储与缓存", onBack = onBack, state = state) {
        SettingsCard {
            DiagnosticRow(
                label = "已用",
                value = "$cacheMb MB · $cacheFiles 个文件"
            )
            SettingsDivider(start = 16.dp)
            SettingsSliderRow(
                title = "缓存上限",
                value = "${cacheLimit.toInt()} MB",
                sliderValue = cacheLimit,
                valueRange = 100f..2048f,
                onValueChange = { cacheLimit = it },
                onValueChangeFinished = { persist() }
            )
            SettingsDivider(start = 16.dp)
            SettingsRow(
                title = "清理缓存",
                subtitle = "删除所有已合成音频",
                glyph = SettingsGlyph.Clear,
                tone = SettingsTone.Rose,
                showChevron = false,
                onClick = { showClear = true }
            )
        }
        SettingsNote("超出上限后按最久未使用清理；清理不影响书签与进度。")
    }

    if (showClear) {
        AlertDialog(
            onDismissRequest = { showClear = false },
            title = { Text("清理缓存？") },
            text = { Text("已合成的音频会被删除，书签和播放进度不受影响。") },
            confirmButton = {
                TextButton(onClick = {
                    showClear = false
                    scope.launch {
                        withContext(Dispatchers.IO) { AudioCache(context).clear() }
                        refreshCache()
                        Toast.makeText(context, "缓存已清理", Toast.LENGTH_LONG).show()
                    }
                }) { Text("清理") }
            },
            dismissButton = {
                TextButton(onClick = { showClear = false }) { Text("取消") }
            }
        )
    }
}

@Composable
fun DiagnosticsScreen(onBack: () -> Unit, state: PageChromeState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val speech = Speech.get()
    val diagnostics by speech.diagnostics.collectAsStateWithLifecycle()
    val playerState by speech.state.collectAsStateWithLifecycle()
    val waitingNetwork by speech.waitingNetwork.collectAsStateWithLifecycle()
    val settings by Settings.state.collectAsStateWithLifecycle()

    var cache by remember { mutableStateOf(CacheStats(0, 0)) }
    var filter by remember { mutableStateOf(DiagFilter.All) }
    var showClear by remember { mutableStateOf(false) }
    var showTtsLog by remember { mutableStateOf(false) }
    var refreshTick by remember { mutableIntStateOf(0) }
    val ttsLogLines by SystemTtsLog.lines.collectAsStateWithLifecycle()

    LaunchedEffect(refreshTick) {
        cache = withContext(Dispatchers.IO) { speech.cacheStats() }
    }

    fun currentSnapshot(cacheStats: CacheStats): DiagnosticsSnapshot = buildDiagnosticsSnapshot(
        context = context,
        diagnostics = diagnostics,
        playerState = playerState,
        waitingNetwork = waitingNetwork,
        cacheStats = cacheStats
    )

    fun copySummary() {
        val text = summaryText(currentSnapshot(cache))
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        if (clipboard == null) {
            Toast.makeText(context, "剪贴板不可用", Toast.LENGTH_SHORT).show()
            return
        }
        clipboard.setPrimaryClip(ClipData.newPlainText("BookVoice 诊断", text))
        Toast.makeText(context, "诊断摘要已复制", Toast.LENGTH_SHORT).show()
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val latestCache = speech.cacheStats()
                    val text = buildString {
                        append(
                            exportText(
                                buildDiagnosticsSnapshot(
                                    context = context,
                                    diagnostics = speech.diagnostics.value,
                                    playerState = speech.state.value,
                                    waitingNetwork = speech.waitingNetwork.value,
                                    cacheStats = latestCache
                                )
                            )
                        )
                        // 系统 TTS 的引擎返回码只有在专属日志里才看得到，一起导出。
                        append("\n\n===== 系统 TTS 调试日志 =====\n")
                        append(SystemTtsLog.text())
                    }
                    val stream = context.contentResolver.openOutputStream(uri)
                        ?: error("无法打开导出文件")
                    stream.bufferedWriter().use { it.write(text) }
                }
            }
            result.fold(
                onSuccess = {
                    Toast.makeText(context, "诊断已导出", Toast.LENGTH_SHORT).show()
                },
                onFailure = {
                    Toast.makeText(
                        context,
                        "导出失败：${it.message.orEmpty()}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            )
        }
    }

    val events = diagnostics.events.asReversed()
    val filtered = events.filter(filter::matches)
    val tokens = BookVoiceDesign.tokens
    val chromeReserve = LocalFloatingChromeReserve.current

    BookVoicePageScaffold(
        title = "诊断信息",
        state = state.topBar,
        onBack = onBack
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            state = state.list,
            contentPadding = PaddingValues(
                start = tokens.pageHorizontalPadding,
                end = tokens.pageHorizontalPadding,
                top = 8.dp,
                bottom = 32.dp + chromeReserve
            )
        ) {
            item {
                SettingsCard {
                    DiagnosticRow(
                        label = "版本",
                        value = "${AppInfo.versionName(context)} (${AppInfo.versionCode(context)})"
                    )
                    SettingsDivider(start = 16.dp)
                    DiagnosticRow(
                        label = "设备",
                        value = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
                    )
                    SettingsDivider(start = 16.dp)
                    DiagnosticRow(
                        label = "Android",
                        value = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
                    )
                    SettingsDivider(start = 16.dp)
                    DiagnosticRow(
                        label = "供应商 / 模型",
                        value = buildString {
                            val config = settings.activeConfigOrDefault()
                            val provider = runCatching {
                                ProviderRegistry.active(config.providerId)
                            }.getOrNull()
                            append(provider?.displayName ?: config.providerId)
                            append(" / ")
                            append(
                                config.model.ifBlank {
                                    provider?.defaultConfig?.model.orEmpty()
                                }.ifBlank { "—" }
                            )
                        }
                    )
                    SettingsDivider(start = 16.dp)
                    DiagnosticRow(
                        label = "音色 / 语速",
                        value = buildString {
                            val config = settings.activeConfigOrDefault()
                            val provider = runCatching {
                                ProviderRegistry.active(config.providerId)
                            }.getOrNull()
                            append(
                                config.voice.ifBlank {
                                    provider?.defaultConfig?.voice.orEmpty()
                                }.ifBlank { "—" }
                            )
                            append(" / ")
                            append(String.format(Locale.US, "%.2f×", settings.speed))
                        }
                    )
                    SettingsDivider(start = 16.dp)
                    DiagnosticRow(
                        label = "上次导入 / 打开",
                        value = buildString {
                            append(
                                if (Metrics.lastImportMs > 0) {
                                    String.format(Locale.US, "%.1fs", Metrics.lastImportMs / 1000.0)
                                } else {
                                    "—"
                                }
                            )
                            append(" / ")
                            append(if (Metrics.lastOpenMs > 0) "${Metrics.lastOpenMs}ms" else "—")
                        }
                    )
                    SettingsDivider(start = 16.dp)
                    DiagnosticRow(
                        label = "播放状态",
                        value = describePlayback(playerState)
                    )
                    SettingsDivider(start = 16.dp)
                    DiagnosticRow(
                        label = "网络",
                        value = if (speech.isNetworkAvailable()) {
                            "在线"
                        } else {
                            "离线（等待中：${if (waitingNetwork) "是" else "否"}）"
                        }
                    )
                    SettingsDivider(start = 16.dp)
                    DiagnosticRow(
                        label = "缓存",
                        value = "${cache.files} 个文件 · ${formatCacheBytes(cache.bytes)}"
                    )
                }
            }
            item { Spacer(Modifier.height(12.dp)) }
            item {
                SettingsCard {
                    Row(Modifier.fillMaxWidth()) {
                        MetricCell(
                            "首声 · 冷",
                            formatMs(diagnostics.firstSoundColdMs),
                            Modifier.weight(1f)
                        )
                        MetricCell(
                            "首声 · 热",
                            formatMs(diagnostics.firstSoundWarmMs),
                            Modifier.weight(1f)
                        )
                    }
                    Row(Modifier.fillMaxWidth()) {
                        MetricCell(
                            "流式首包",
                            formatMs(diagnostics.lastStreamTtfbMs),
                            Modifier.weight(1f)
                        )
                        MetricCell(
                            "上次合成",
                            formatMs(diagnostics.lastSynthMs),
                            Modifier.weight(1f)
                        )
                    }
                    Row(Modifier.fillMaxWidth()) {
                        MetricCell(
                            "流式回退",
                            "${diagnostics.streamFallbacks} 次",
                            Modifier.weight(1f)
                        )
                        MetricCell(
                            "流式重试",
                            "${diagnostics.streamRetries} 次",
                            Modifier.weight(1f)
                        )
                    }
                    Row(Modifier.fillMaxWidth()) {
                        MetricCell(
                            "首包超时",
                            "${diagnostics.firstPacketTimeouts} 次",
                            Modifier.weight(1f)
                        )
                        MetricCell(
                            "断流",
                            "${diagnostics.idleTimeouts} 次",
                            Modifier.weight(1f)
                        )
                    }
                    Row(Modifier.fillMaxWidth()) {
                        MetricCell(
                            "强制收尾",
                            "${diagnostics.stallForced} 次",
                            Modifier.weight(1f)
                        )
                        MetricCell(
                            "命中 / 未命中",
                            "${diagnostics.cacheHits} / ${diagnostics.cacheMisses}",
                            Modifier.weight(1f)
                        )
                    }
                    if (diagnostics.lastError.isNotBlank()) {
                        Text(
                            "上次错误：${diagnostics.lastError}",
                            modifier = Modifier.padding(16.dp, 8.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            maxLines = 3
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(12.dp)) }
            item {
                SettingsCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("系统 TTS 调试日志", style = MaterialTheme.typography.titleSmall)
                            Text(
                                if (ttsLogLines.isEmpty()) {
                                    "还没有日志；试听或播放系统 TTS 后生成"
                                } else {
                                    "${ttsLogLines.size} 条 · ${ttsLogLines.last().take(120)}"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        OutlinedButton(onClick = { showTtsLog = true }) {
                            Text("查看")
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(12.dp)) }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = { copySummary() }, modifier = Modifier.weight(1f)) {
                        Text("复制摘要")
                    }
                    OutlinedButton(
                        onClick = {
                            val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
                            exportLauncher.launch("bookvoice-diagnostics-$stamp.txt")
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("导出诊断")
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            speech.markDiagnostics()
                            Toast.makeText(context, "已记录标记", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("记录标记")
                    }
                    OutlinedButton(
                        onClick = { showClear = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("清空")
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Text(
                        "最近播放事件",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "${diagnostics.events.size} 条",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DiagFilter.entries.forEach { item ->
                        FilterChip(
                            selected = filter == item,
                            onClick = { filter = item },
                            label = { Text("${item.label} ${events.count(item::matches)}") }
                        )
                    }
                }
            }
            if (filtered.isEmpty()) {
                item {
                    Text(
                        "暂无事件，播放一段后会出现在这里",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(filtered.size) { index ->
                    EventLine(filtered[index])
                }
            }
            item {
                SettingsNote(
                    "播放事件只保存在本次运行内；系统 TTS 日志会写入本机文件，重启后仍可查看。" +
                        "复制和导出不含 API Key、服务地址、书名和正文。"
                )
            }
        }
    }

    if (showTtsLog) {
        SystemTtsLogDialog(onDismiss = { showTtsLog = false })
    }

    if (showClear) {
        AlertDialog(
            onDismissRequest = { showClear = false },
            title = { Text("清空诊断？") },
            text = { Text("会清空本次运行的事件和指标，不影响播放、缓存、书签和进度。") },
            confirmButton = {
                TextButton(onClick = {
                    showClear = false
                    speech.clearDiagnostics()
                    refreshTick += 1
                    filter = DiagFilter.All
                }) { Text("清空") }
            },
            dismissButton = {
                TextButton(onClick = { showClear = false }) { Text("取消") }
            }
        )
    }
}

private enum class DiagFilter(val label: String) {
    All("全部"),
    Playback("播放"),
    Synth("合成"),
    Network("网络"),
    Problem("问题");

    fun matches(event: DiagnosticEvent): Boolean = when (this) {
        All -> true
        Playback -> event.tag in setOf("播放", "队列", "播放器", "标记")
        Synth -> event.tag in setOf("流式", "合成", "缓存")
        Network -> event.tag == "网络"
        Problem -> event.level != DiagnosticLevel.Info
    }
}

@Composable
private fun MetricCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun EventLine(event: DiagnosticEvent) {
    val color = when (event.level) {
        DiagnosticLevel.Error -> MaterialTheme.colorScheme.error
        DiagnosticLevel.Warn -> MaterialTheme.colorScheme.tertiary
        DiagnosticLevel.Info -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        formatEvent(event),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 3.dp),
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
        color = color
    )
}

private fun describePlayback(state: PlayerState): String = when (state) {
    is PlayerState.Idle -> "空闲"
    is PlayerState.Buffering -> "缓冲中 · 第 ${state.chapterIdx + 1} 章 / 段 ${state.blockIdx}"
    is PlayerState.Playing ->
        "播放中 · 第 ${state.chapterIdx + 1} 章 · 块 ${state.chunk + 1}/${state.totalChunks} · 段 ${state.blockIdx}"
    is PlayerState.Paused -> "已暂停 · 第 ${state.chapterIdx + 1} 章 / 段 ${state.blockIdx}"
    is PlayerState.Error -> "错误：${state.message}"
}

private fun formatMs(value: Long): String = if (value > 0) "$value ms" else "—"

private fun formatCacheBytes(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
    bytes >= 1024 -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
    else -> "$bytes B"
}

private fun buildDiagnosticsSnapshot(
    context: Context,
    diagnostics: io.github.mungbean2435.bookvoice.playback.Diagnostics,
    playerState: PlayerState,
    waitingNetwork: Boolean,
    cacheStats: CacheStats
): DiagnosticsSnapshot {
    val settings = Settings.state.value
    val config = settings.activeConfigOrDefault()
    val provider = runCatching { ProviderRegistry.active(config.providerId) }.getOrNull()
    return DiagnosticsSnapshot(
        appVersion = AppInfo.versionName(context),
        appVersionCode = AppInfo.versionCode(context),
        device = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
        android = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
        abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "—",
        providerName = provider?.displayName ?: config.providerId,
        providerId = config.providerId,
        model = config.model.ifBlank { provider?.defaultConfig?.model.orEmpty() },
        voice = config.voice.ifBlank { provider?.defaultConfig?.voice.orEmpty() },
        speed = settings.speed,
        playback = describePlayback(playerState),
        online = Speech.get().isNetworkAvailable(),
        waitingNetwork = waitingNetwork,
        cacheFiles = cacheStats.files,
        cacheBytes = cacheStats.bytes,
        diagnostics = diagnostics
    )
}
