@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package io.github.mungbean2435.bookvoice.ui.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mungbean2435.bookvoice.data.settings.Metrics
import io.github.mungbean2435.bookvoice.data.settings.Settings
import io.github.mungbean2435.bookvoice.playback.AudioCache
import io.github.mungbean2435.bookvoice.playback.Speech
import io.github.mungbean2435.bookvoice.ui.components.LocalFloatingChromeReserve
import io.github.mungbean2435.bookvoice.ui.components.BookVoicePageScaffold
import io.github.mungbean2435.bookvoice.ui.nav.PageChromeState
import io.github.mungbean2435.bookvoice.ui.theme.SettingsTone
import io.github.mungbean2435.bookvoice.ui.theme.BookVoiceDesign
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    val diagnostics by Speech.get().diagnostics.collectAsStateWithLifecycle()

    SettingsDetailScaffold(title = "诊断信息", onBack = onBack, state = state) {
        SettingsCard {
            DiagnosticRow(
                label = "上次导入",
                value = if (Metrics.lastImportMs > 0) {
                    "${String.format(Locale.US, "%.1f", Metrics.lastImportMs / 1000.0)} s"
                } else {
                    "—"
                }
            )
            if (Metrics.lastBreakdown.isNotBlank()) {
                SettingsDivider(start = 16.dp)
                Text(
                    "上次导入拆分：${Metrics.lastBreakdown}",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            SettingsDivider(start = 16.dp)
            DiagnosticRow(
                label = "上次打开 · 首帧",
                value = if (Metrics.lastOpenMs > 0) {
                    "${Metrics.lastOpenMs} ms"
                } else {
                    "—"
                }
            )
            if (Metrics.lastOpenBreakdown.isNotBlank()) {
                Text(
                    "上次打开拆分：${Metrics.lastOpenBreakdown}",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            SettingsDivider(start = 16.dp)
            DiagnosticRow(
                label = "首声 · 未缓存",
                value = if (diagnostics.firstSoundColdMs > 0) {
                    "${diagnostics.firstSoundColdMs} ms"
                } else {
                    "—"
                }
            )
            SettingsDivider(start = 16.dp)
            DiagnosticRow(
                label = "首声 · 已缓存",
                value = if (diagnostics.firstSoundWarmMs > 0) {
                    "${diagnostics.firstSoundWarmMs} ms"
                } else {
                    "—"
                }
            )
            SettingsDivider(start = 16.dp)
            DiagnosticRow(
                label = "缓存命中",
                value = "${diagnostics.cacheHits} 次 · 未命中 ${diagnostics.cacheMisses} 次"
            )
        }
        SettingsNote("诊断数值只保存在本次运行内，退出应用后清零。")
    }
}
