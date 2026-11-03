@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package io.github.mungbean2435.bookvoice.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.mungbean2435.bookvoice.data.backup.BackupManager
import io.github.mungbean2435.bookvoice.data.settings.Settings
import io.github.mungbean2435.bookvoice.playback.Speech
import io.github.mungbean2435.bookvoice.ui.components.LocalFloatingChromeReserve
import io.github.mungbean2435.bookvoice.ui.components.BookVoicePageScaffold
import io.github.mungbean2435.bookvoice.ui.nav.PageChromeState
import io.github.mungbean2435.bookvoice.ui.nav.rememberPageChromeState
import io.github.mungbean2435.bookvoice.ui.theme.SettingsTone
import io.github.mungbean2435.bookvoice.ui.theme.BookVoiceDesign
import io.github.mungbean2435.bookvoice.ui.theme.BookVoiceTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class BackupStatus(val message: String, val failed: Boolean)

@Composable
fun BackupRestoreScreen(onBack: () -> Unit, state: PageChromeState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busyLabel by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<BackupStatus?>(null) }

    fun finish(result: Result<String>, action: String) {
        busyLabel = null
        status = result.fold(
            onSuccess = { BackupStatus(it, failed = false) },
            onFailure = { BackupStatus("${action}失败：${it.message.orEmpty()}", failed = true) }
        )
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            busyLabel = "正在导出…"
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching { BackupManager(context).export(uri) }
                }
                finish(result, "导出")
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            busyLabel = "正在导入…"
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching { BackupManager(context).import(uri) }
                }
                if (result.isSuccess) {
                    // 导入可能改了当前配置，播放端语速归属要立刻刷新。
                    Speech.get().applySpeed(Settings.state.value.speed)
                }
                finish(result, "导入")
            }
        }
    }

    BackupContent(
        busyLabel = busyLabel,
        status = status,
        onExport = { exportLauncher.launch("bookvoice-backup.json") },
        onImport = { importLauncher.launch(arrayOf("application/json")) },
        onDismissStatus = { status = null },
        onBack = onBack,
        state = state
    )
}

@Composable
private fun BackupContent(
    busyLabel: String?,
    status: BackupStatus?,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onDismissStatus: () -> Unit,
    onBack: () -> Unit,
    state: PageChromeState
) {
    val tokens = BookVoiceDesign.tokens

    BookVoicePageScaffold(title = "备份与恢复", state = state.topBar, onBack = onBack) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            state = state.list,
            contentPadding = PaddingValues(
                start = tokens.pageHorizontalPadding,
                end = tokens.pageHorizontalPadding,
                top = 8.dp,
                bottom = 32.dp + LocalFloatingChromeReserve.current
            )
        ) {
            item {
                AnimatedVisibility(
                    visible = status != null,
                    enter = slideInVertically(
                        animationSpec = tween(300),
                        initialOffsetY = { -it }
                    ) + fadeIn(tween(300)),
                    exit = slideOutVertically(
                        animationSpec = tween(250),
                        targetOffsetY = { -it }
                    ) + fadeOut(tween(250))
                ) {
                    status?.let {
                        BackupStatusCard(
                            status = it,
                            onDismiss = onDismissStatus
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(8.dp)) }
            item {
                LocalBackupCard(
                    busyLabel = busyLabel,
                    onExport = onExport,
                    onImport = onImport
                )
            }

            item { Spacer(Modifier.height(20.dp)) }
            item { SectionLabel("其他通道") }
            item {
                DisabledBackupCard(
                    title = "历史记录",
                    subtitle = "本机历史版本与回滚",
                    glyph = SettingsGlyph.Storage,
                    tone = SettingsTone.Blue
                )
            }
            item { Spacer(Modifier.height(8.dp)) }
            item {
                DisabledBackupCard(
                    title = "GitHub",
                    subtitle = "仓库同步与版本管理",
                    glyph = SettingsGlyph.BackupOut,
                    tone = SettingsTone.Violet
                )
            }
            item { Spacer(Modifier.height(8.dp)) }
            item {
                DisabledBackupCard(
                    title = "WebDAV",
                    subtitle = "自建网盘备份",
                    glyph = SettingsGlyph.BackupIn,
                    tone = SettingsTone.Amber
                )
            }
            item { Spacer(Modifier.height(8.dp)) }
            item {
                DisabledBackupCard(
                    title = "消息通道",
                    subtitle = "导出到剪贴板或聊天工具",
                    glyph = SettingsGlyph.KeepAlive,
                    tone = SettingsTone.Teal
                )
            }

            item {
                SettingsNote(
                    "历史、GitHub、WebDAV、消息通道需要新的同步/网络逻辑，本轮未接入；" +
                        "配置导出加密也未做。本地导出仍不含书文件与 API Key。"
                )
            }
        }
    }
}

@Composable
private fun LocalBackupCard(
    busyLabel: String?,
    onExport: () -> Unit,
    onImport: () -> Unit
) {
    SettingsCard {
        Column(Modifier.padding(16.dp)) {
            Text(
                "本地文件",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "导出进度、书签、规则包与设置；不含书文件与 API Key。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onExport,
                    enabled = busyLabel == null,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("导出 JSON")
                }
                OutlinedButton(
                    onClick = onImport,
                    enabled = busyLabel == null,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("导入 JSON")
                }
            }
            if (busyLabel != null) {
                Spacer(Modifier.height(8.dp))
                ListItem(
                    headlineContent = { Text(busyLabel) },
                    supportingContent = { Text("请保持页面在前台") },
                    trailingContent = {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun BackupStatusCard(status: BackupStatus, onDismiss: () -> Unit) {
    SettingsCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (status.failed) "操作失败" else "操作完成",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (status.failed) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                Text(
                    status.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    }
}

@Composable
private fun DisabledBackupCard(
    title: String,
    subtitle: String,
    glyph: SettingsGlyph,
    tone: SettingsTone
) {
    SettingsCard {
        SettingsRow(
            title = title,
            subtitle = subtitle,
            glyph = glyph,
            tone = tone,
            trailing = "未接入",
            showChevron = false,
            onClick = null
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.SemiBold
    )
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844, name = "备份与恢复 · Clean")
@Composable
private fun BackupRestorePreview() {
    BookVoiceTheme(darkTheme = false, dynamicColor = false) {
        BackupContent(
            busyLabel = null,
            status = BackupStatus("已导出 12 本书的进度与 36 个书签。", failed = false),
            onExport = {},
            onImport = {},
            onDismissStatus = {},
            onBack = {},
            state = rememberPageChromeState()
        )
    }
}
