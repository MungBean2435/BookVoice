@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package io.github.mungbean2435.bookvoice.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.TopAppBarState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mungbean2435.bookvoice.data.settings.AppInfo
import io.github.mungbean2435.bookvoice.data.settings.AppLanguage
import io.github.mungbean2435.bookvoice.data.settings.Settings
import io.github.mungbean2435.bookvoice.data.settings.ThemeMode
import io.github.mungbean2435.bookvoice.playback.AudioCache
import io.github.mungbean2435.bookvoice.tts.ProviderRegistry
import io.github.mungbean2435.bookvoice.ui.components.LocalFloatingChromeReserve
import io.github.mungbean2435.bookvoice.ui.components.BookVoicePageScaffold
import io.github.mungbean2435.bookvoice.ui.nav.PageChromeState
import io.github.mungbean2435.bookvoice.ui.nav.rememberPageChromeState
import io.github.mungbean2435.bookvoice.ui.onboarding.LocalAppLanguage
import io.github.mungbean2435.bookvoice.ui.theme.AppearanceSettings
import io.github.mungbean2435.bookvoice.ui.theme.SettingsTone
import io.github.mungbean2435.bookvoice.ui.theme.BookVoiceDesign
import io.github.mungbean2435.bookvoice.ui.theme.BookVoiceStyle
import io.github.mungbean2435.bookvoice.ui.theme.BookVoiceTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

private data class SettingsRowSpec(
    val title: String,
    val subtitle: String?,
    val glyph: SettingsGlyph,
    val tone: SettingsTone,
    val keywords: String,
    val onClick: () -> Unit
)

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    state: PageChromeState,
    onOpenRules: () -> Unit,
    onOpenTtsConfig: () -> Unit,
    onOpenPlayback: () -> Unit,
    onOpenStorage: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenAppearance: () -> Unit = {},
    onOpenBackup: () -> Unit = {}
) {
    val context = LocalContext.current
    val current by Settings.state.collectAsStateWithLifecycle()
    val appearance by AppearanceSettings.state.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var showAbout by remember { mutableStateOf(false) }

    var cacheMb by remember { mutableLongStateOf(0L) }
    var cacheFiles by remember { mutableIntStateOf(0) }

    suspend fun refreshCache() {
        val cache = AudioCache(context)
        cacheMb = withContext(Dispatchers.IO) { cache.totalBytes() } / (1024 * 1024)
        cacheFiles = withContext(Dispatchers.IO) { cache.fileCount() }
    }

    LaunchedEffect(Unit) { refreshCache() }

    val active = current.activeConfigOrNull()
    val activeSummary = if (active == null) {
        "还没有配置，点这里添加"
    } else {
        val providerName = ProviderRegistry.get(active.providerId)?.displayName ?: active.providerId
        val keyHint = if (active.apiKey.isBlank()) " · 未填 Key" else ""
        "${active.name} · $providerName$keyHint"
    }
    val versionName = remember(context) { AppInfo.versionName(context) }

    val groups = listOf(
        listOf(
            SettingsRowSpec(
                title = "界面风格",
                subtitle = buildString {
                    append(if (appearance.style == BookVoiceStyle.Clean) "Clean" else "Material")
                    append(" · ")
                    append(if (appearance.dynamicColor) "动态取色" else appearance.palette.label)
                },
                glyph = SettingsGlyph.Sun,
                tone = SettingsTone.Blue,
                keywords = "界面 风格 主题 颜色 深浅 取色 动态 外观 液态 玻璃 模糊 折射 毛玻璃",
                onClick = onOpenAppearance
            )
        ),
        listOf(
            SettingsRowSpec(
                title = "TTS 配置",
                subtitle = activeSummary,
                glyph = SettingsGlyph.Voice,
                tone = SettingsTone.Violet,
                keywords = "语音 音色 模型 供应商 TTS 云端 配置",
                onClick = onOpenTtsConfig
            ),
            SettingsRowSpec(
                title = "播放设置",
                subtitle = "语速 ${String.format(Locale.US, "%.2f×", current.speed)} · " +
                    "段落间停顿 ${current.gapParagraphMs} ms",
                glyph = SettingsGlyph.Playback,
                tone = SettingsTone.Blue,
                keywords = "语速 停顿 播放 朗读 速度 声音",
                onClick = onOpenPlayback
            ),
            SettingsRowSpec(
                title = "朗读规则",
                subtitle = "省略号、破折号、百分号与自定义 JSON 规则包",
                glyph = SettingsGlyph.Rules,
                tone = SettingsTone.Teal,
                keywords = "规则 标点 省略号 破折号 百分号 JSON 规则包",
                onClick = onOpenRules
            )
        ),
        listOf(
            SettingsRowSpec(
                title = "存储与缓存",
                subtitle = "已用 $cacheMb MB · $cacheFiles 个文件 · 上限 ${current.cacheLimitMb} MB",
                glyph = SettingsGlyph.Storage,
                tone = SettingsTone.Amber,
                keywords = "缓存 存储 空间 清理 上限 用量 文件",
                onClick = onOpenStorage
            ),
            SettingsRowSpec(
                title = "备份与恢复",
                subtitle = "本地 JSON；历史、GitHub、WebDAV、消息通道未接入",
                glyph = SettingsGlyph.BackupOut,
                tone = SettingsTone.Rose,
                keywords = "备份 导出 导入 恢复 数据 JSON 加密 WebDAV GitHub 消息",
                onClick = onOpenBackup
            )
        ),
        listOf(
            SettingsRowSpec(
                title = "诊断信息",
                subtitle = "导入耗时、首声与缓存命中",
                glyph = SettingsGlyph.Diagnostics,
                tone = SettingsTone.Violet,
                keywords = "诊断 首声 导入耗时 缓存命中 性能",
                onClick = onOpenDiagnostics
            ),
            SettingsRowSpec(
                title = "后台保活",
                subtitle = "锁屏被清理？去系统设置允许后台运行 / 自启动",
                glyph = SettingsGlyph.KeepAlive,
                tone = SettingsTone.Amber,
                keywords = "后台 保活 自启动 电池 通知 锁屏",
                onClick = {
                    val intent = Intent(
                        android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null)
                    )
                    runCatching { context.startActivity(intent) }
                }
            ),
            SettingsRowSpec(
                title = "关于书声",
                subtitle = "版本 $versionName · 本地优先 · MIT",
                glyph = SettingsGlyph.About,
                tone = SettingsTone.Teal,
                keywords = "关于 版本 许可 MIT 隐私",
                onClick = { showAbout = true }
            )
        )
    )

    val visibleGroups = groups
        .map { group ->
            group.filter {
                matchesSettingsQuery(query, it.title, it.subtitle, it.keywords)
            }
        }
        .filter { it.isNotEmpty() }

    SettingsContent(
        themeMode = current.themeMode,
        query = query,
        onQueryChange = { query = it },
        groups = visibleGroups,
        onBack = onBack,
        listState = state.list,
        topBarState = state.topBar,
        onThemeModeChange = { mode ->
            Settings.update { it.copy(themeMode = mode) }
        }
    )

    if (showAbout) {
        AlertDialog(
            onDismissRequest = { showAbout = false },
            title = { Text("关于书声") },
            text = {
                Column {
                    Text("版本 $versionName")
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "本地优先：书籍、进度与设置只存在本机，不上传内容。",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "MIT License",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showAbout = false }) { Text("关闭") }
            }
        )
    }
}

@Composable
private fun SettingsContent(
    themeMode: ThemeMode,
    query: String,
    onQueryChange: (String) -> Unit,
    groups: List<List<SettingsRowSpec>>,
    onBack: () -> Unit,
    listState: LazyListState,
    topBarState: TopAppBarState,
    onThemeModeChange: (ThemeMode) -> Unit
) {
    val tokens = BookVoiceDesign.tokens
    val chromeReserve = LocalFloatingChromeReserve.current
    val language = LocalAppLanguage.current

    BookVoicePageScaffold(
        title = if (language == AppLanguage.En) "Settings" else "设置",
        state = topBarState,
        onBack = onBack,
        actions = { ThemeModeButton(themeMode, onThemeModeChange) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
            state = listState,
            contentPadding = PaddingValues(
                start = tokens.pageHorizontalPadding,
                end = tokens.pageHorizontalPadding,
                top = 8.dp,
                bottom = 32.dp + chromeReserve
            )
        ) {
            item {
                SettingsSearchBar(
                    query = query,
                    onQueryChange = onQueryChange
                )
            }

            if (query.isNotBlank() && groups.isEmpty()) {
                item { SettingsEmptySearch() }
            }

            groups.forEach { rows ->
                item { Spacer(Modifier.height(tokens.sectionSpacing)) }
                item {
                    SettingsCard {
                        rows.forEachIndexed { index, row ->
                            SettingsRow(
                                title = row.title,
                                subtitle = row.subtitle,
                                glyph = row.glyph,
                                tone = row.tone,
                                onClick = row.onClick
                            )
                            if (index != rows.lastIndex) {
                                SettingsDivider()
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}


@Preview(showBackground = true, widthDp = 390, heightDp = 844, name = "设置 · Clean")
@Composable
private fun SettingsScreenPreview() {
    BookVoiceTheme(darkTheme = false, dynamicColor = false) {
        SettingsContent(
            themeMode = ThemeMode.FollowSystem,
            query = "",
            onQueryChange = {},
            groups = previewSettingsGroups,
            onBack = {},
            listState = rememberLazyListState(),
            topBarState = rememberPageChromeState().topBar,
            onThemeModeChange = {}
        )
    }
}

private val previewSettingsGroups = listOf(
    listOf(
        SettingsRowSpec(
            title = "界面风格",
            subtitle = "Clean · 动态取色",
            glyph = SettingsGlyph.Sun,
            tone = SettingsTone.Blue,
            keywords = "",
            onClick = {}
        )
    ),
    listOf(
        SettingsRowSpec(
            title = "TTS 配置",
            subtitle = "MiMo · 未填 Key",
            glyph = SettingsGlyph.Voice,
            tone = SettingsTone.Violet,
            keywords = "",
            onClick = {}
        ),
        SettingsRowSpec(
            title = "播放设置",
            subtitle = "语速 1.00× · 段落间停顿 300 ms",
            glyph = SettingsGlyph.Playback,
            tone = SettingsTone.Blue,
            keywords = "",
            onClick = {}
        ),
        SettingsRowSpec(
            title = "朗读规则",
            subtitle = "省略号、破折号与自定义 JSON 规则包",
            glyph = SettingsGlyph.Rules,
            tone = SettingsTone.Teal,
            keywords = "",
            onClick = {}
        )
    ),
    listOf(
        SettingsRowSpec(
            title = "存储与缓存",
            subtitle = "已用 128 MB · 42 个文件 · 上限 512 MB",
            glyph = SettingsGlyph.Storage,
            tone = SettingsTone.Amber,
            keywords = "",
            onClick = {}
        ),
        SettingsRowSpec(
            title = "备份与恢复",
            subtitle = "本地 JSON；历史、GitHub、WebDAV、消息通道未接入",
            glyph = SettingsGlyph.BackupOut,
            tone = SettingsTone.Rose,
            keywords = "",
            onClick = {}
        )
    ),
    listOf(
        SettingsRowSpec(
            title = "诊断信息",
            subtitle = "导入耗时、首声与缓存命中",
            glyph = SettingsGlyph.Diagnostics,
            tone = SettingsTone.Violet,
            keywords = "",
            onClick = {}
        ),
        SettingsRowSpec(
            title = "关于书声",
            subtitle = "版本 2.5.0 · 本地优先 · MIT",
            glyph = SettingsGlyph.About,
            tone = SettingsTone.Teal,
            keywords = "",
            onClick = {}
        )
    )
)
