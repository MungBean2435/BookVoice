package io.github.mungbean2435.bookvoice.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.mungbean2435.bookvoice.tts.ProviderConfig
import io.github.mungbean2435.bookvoice.tts.TtsCatalog
import io.github.mungbean2435.bookvoice.tts.TtsProvider
import io.github.mungbean2435.bookvoice.tts.Voice
import io.github.mungbean2435.bookvoice.tts.system.SystemTtsSettings

/**
 * 参考图 2 的「可用模型」底部弹层，TTS 版同时包含模型和音色。
 * 打开时用当前草稿的 Key/baseUrl 拉目录；失败自动回落内置目录。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TtsCatalogSheet(
    provider: TtsProvider,
    apiKey: String,
    baseUrl: String,
    existingModels: Set<String>,
    existingVoiceIds: Set<String>,
    onDismiss: () -> Unit,
    onImport: (List<String>, List<Voice>) -> Unit,
    onManualAdd: (String, String) -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var loading by remember { mutableStateOf(true) }
    var catalog by remember { mutableStateOf<TtsCatalog?>(null) }
    var selectedModels by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectedVoices by remember { mutableStateOf<Set<String>>(emptySet()) }
    var query by rememberSaveable { mutableStateOf("") }
    var reload by remember { mutableIntStateOf(0) }
    var showManual by remember { mutableStateOf(false) }

    LaunchedEffect(reload, provider.id, apiKey, baseUrl) {
        loading = true
        // 系统 TTS 本来就没有 Key：它必须走 fetchCatalog 才能读到引擎里的真实音色。
        val result = if (provider.requiresApiKey && apiKey.trim().isBlank()) {
            provider.builtinCatalog().copy(
                note = "未填 Key，使用内置目录；填 Key 后可自动获取账号音色"
            )
        } else {
            val request = ProviderConfig(
                apiKey = apiKey.trim(),
                baseUrl = baseUrl.trim().ifBlank { provider.defaultConfig.baseUrl }
            )
            runCatching { provider.fetchCatalog(request) }.getOrElse { error ->
                provider.builtinCatalog().copy(
                    note = "在线获取失败：${error.message ?: "网络错误"}；已使用内置目录"
                )
            }
        }
        val availableModels = result.models
            .filterNot { it in existingModels }
            .distinct()
        // 空 id 是「系统默认」占位，导入不到音色库里；这里只展示真实音色。
        val availableVoices = result.voices
            .filterNot { it.id.isBlank() || it.id in existingVoiceIds }
            .distinctBy { it.id }
        catalog = result.copy(models = availableModels, voices = availableVoices)
        selectedModels = availableModels.toSet()
        selectedVoices = availableVoices.map { it.id }.toSet()
        loading = false
    }

    val data = catalog
    val allSelected = data != null &&
        selectedModels.size == data.models.size &&
        selectedVoices.size == data.voices.size
    val selectedCount = selectedModels.size + selectedVoices.size
    val q = query.trim()
    val visibleModels = data?.models.orEmpty().filter {
        q.isBlank() || it.contains(q, ignoreCase = true)
    }
    val visibleVoices = data?.voices.orEmpty().filter {
        q.isBlank() ||
            it.id.contains(q, ignoreCase = true) ||
            it.label.contains(q, ignoreCase = true) ||
            it.hint.contains(q, ignoreCase = true)
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .navigationBarsPadding()
                .imePadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 12.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("可用模型与音色", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.weight(1f))
                if (provider.id == "system") {
                    TextButton(onClick = { SystemTtsSettings.open(context) }) {
                        Text("系统语音设置")
                    }
                }
                TextButton(
                    onClick = {
                        val current = data ?: return@TextButton
                        if (allSelected) {
                            selectedModels = emptySet()
                            selectedVoices = emptySet()
                        } else {
                            selectedModels = current.models.toSet()
                            selectedVoices = current.voices.map { it.id }.toSet()
                        }
                    },
                    enabled = data != null && (data.models.isNotEmpty() || data.voices.isNotEmpty())
                ) {
                    Text(if (allSelected) "取消全选" else "全选")
                }
            }

            if (loading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "正在获取模型和音色…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else if (data != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        data.note.ifBlank { if (data.fromNetwork) "已在线获取目录" else "使用内置目录" },
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    TextButton(onClick = { showManual = true }) { Text("手动输入") }
                    TextButton(onClick = { reload += 1 }) { Text("重试") }
                }

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    item { SectionHeader("模型", visibleModels.size) }
                    if (visibleModels.isEmpty()) {
                        item {
                            EmptyRow(
                                if (q.isNotBlank()) {
                                    "没有匹配的模型"
                                } else {
                                    "没有可添加的模型"
                                }
                            )
                        }
                    }
                    items(visibleModels, key = { "m:$it" }) { modelId ->
                        CatalogRow(
                            title = modelId,
                            subtitle = "${provider.audioFormat} · TTS",
                            selected = modelId in selectedModels,
                            onToggle = {
                                selectedModels = if (modelId in selectedModels) {
                                    selectedModels - modelId
                                } else {
                                    selectedModels + modelId
                                }
                            }
                        )
                    }

                    item { SectionHeader("音色", visibleVoices.size) }
                    if (visibleVoices.isEmpty()) {
                        item {
                            EmptyRow(
                                if (q.isNotBlank()) {
                                    "没有匹配的音色"
                                } else {
                                    "没有可添加的音色"
                                }
                            )
                        }
                    }
                    items(visibleVoices, key = { "v:${it.id}" }) { voice ->
                        CatalogRow(
                            title = voice.label,
                            subtitle = if (voice.hint.isBlank()) {
                                voice.id
                            } else {
                                "${voice.id} · ${voice.hint}"
                            },
                            selected = voice.id in selectedVoices,
                            onToggle = {
                                selectedVoices = if (voice.id in selectedVoices) {
                                    selectedVoices - voice.id
                                } else {
                                    selectedVoices + voice.id
                                }
                            }
                        )
                    }
                }

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    placeholder = { Text("输入模型/音色名称筛选") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
                Button(
                    onClick = {
                        val current = data ?: return@Button
                        val voices = current.voices.filter { it.id in selectedVoices }
                        onImport(selectedModels.toList(), voices)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                        .height(48.dp),
                    enabled = selectedCount > 0,
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Text("导入选中（$selectedCount）")
                }
            }
        }
    }

    if (showManual) {
        var modelId by remember { mutableStateOf("") }
        var voiceId by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showManual = false },
            title = { Text("手动添加") },
            text = {
                Column {
                    Text(
                        "目录里找不到的模型/音色可以手填 ID，至少填一项。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = modelId,
                        onValueChange = { modelId = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("模型 ID") },
                        singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = voiceId,
                        onValueChange = { voiceId = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("音色 ID") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showManual = false
                        onManualAdd(modelId.trim(), voiceId.trim())
                    },
                    enabled = modelId.isNotBlank() || voiceId.isNotBlank()
                ) {
                    Text("添加")
                }
            },
            dismissButton = {
                TextButton(onClick = { showManual = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun SectionHeader(title: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.width(6.dp))
        Text(
            count.toString(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun EmptyRow(text: String) {
    Text(
        text,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun CatalogRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onToggle)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(
                    if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = "已选中",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
