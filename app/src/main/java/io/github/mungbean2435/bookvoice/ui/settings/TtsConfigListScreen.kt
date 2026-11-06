package io.github.mungbean2435.bookvoice.ui.settings

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mungbean2435.bookvoice.data.settings.Settings
import io.github.mungbean2435.bookvoice.data.settings.TtsConfig
import io.github.mungbean2435.bookvoice.data.settings.TtsConfigs
import io.github.mungbean2435.bookvoice.playback.Speech
import io.github.mungbean2435.bookvoice.tts.ProviderRegistry
import io.github.mungbean2435.bookvoice.tts.TtsProvider
import io.github.mungbean2435.bookvoice.ui.components.LocalFloatingChromeReserve

/**
 * TTS 配置管理。
 *
 * 列表只负责管理供应商、Key、模型和音色库；朗读时用哪条配置由阅读页的
 * 音色面板决定，因此这里不再有「启用 / 未启用」。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TtsConfigListScreen(
    onBack: () -> Unit,
    listState: LazyListState,
    onEdit: (String) -> Unit,
    onAdd: (templateId: String) -> Unit
) {
    val context = LocalContext.current
    val current by Settings.state.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var showAddSheet by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<TtsConfig?>(null) }

    fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    fun providerName(providerId: String): String =
        ProviderRegistry.get(providerId)?.displayName ?: providerId

    val providers = ProviderRegistry.all()
    val knownIds = providers.map { it.id }.toSet()
    val byProvider = current.configs.groupBy { it.providerId }
    val q = query.trim()

    fun matches(config: TtsConfig): Boolean =
        q.isBlank() || listOf(
            config.name,
            providerName(config.providerId),
            config.model,
            config.voice
        ).any { it.contains(q, ignoreCase = true) }

    val matchingConfigs = current.configs.filter(::matches)
    val unknownConfigs = matchingConfigs.filter { it.providerId !in knownIds }

    fun duplicate(config: TtsConfig) {
        val copy = TtsConfigs.duplicate(config, current.configs.map { it.name })
        Settings.update { old -> old.copy(configs = old.configs + copy) }
        toast("已复制为「${copy.name}」")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { showAddSheet = true }) {
                        Icon(Icons.Filled.Add, contentDescription = "添加配置")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Text(
                "TTS 配置",
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp),
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                "在阅读页的音色面板切换朗读配置；这里管理供应商、Key、模型与音色库。",
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                placeholder = { Text("搜索名称 / 供应商 / 模型 / 音色") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(28.dp)
            )

            if (q.isNotBlank() && matchingConfigs.isEmpty()) {
                EmptyState(
                    hasQuery = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    onClear = { query = "" },
                    onAdd = { showAddSheet = true }
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    state = listState,
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 4.dp,
                        bottom = 24.dp + LocalFloatingChromeReserve.current
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (q.isBlank()) {
                        providers.forEach { provider ->
                            val configs = byProvider[provider.id].orEmpty()
                            if (configs.isEmpty()) {
                                item(key = "template:${provider.id}") {
                                    ProviderTemplateCard(
                                        provider = provider,
                                        onClick = { onAdd(provider.id) }
                                    )
                                }
                            } else {
                                items(configs, key = { it.id }) { config ->
                                    ConfigCard(
                                        config = config,
                                        providerName = providerName(config.providerId),
                                        onOpen = { onEdit(config.id) },
                                        onDuplicate = { duplicate(config) },
                                        onDelete = { pendingDelete = config }
                                    )
                                }
                            }
                        }
                        if (unknownConfigs.isNotEmpty()) {
                            item(key = "unknown-header") {
                                Text(
                                    "其他配置",
                                    modifier = Modifier.padding(top = 8.dp, start = 4.dp),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            items(unknownConfigs, key = { it.id }) { config ->
                                ConfigCard(
                                    config = config,
                                    providerName = providerName(config.providerId),
                                    onOpen = { onEdit(config.id) },
                                    onDuplicate = { duplicate(config) },
                                    onDelete = { pendingDelete = config }
                                )
                            }
                        }
                    } else {
                        items(matchingConfigs, key = { it.id }) { config ->
                            ConfigCard(
                                config = config,
                                providerName = providerName(config.providerId),
                                onOpen = { onEdit(config.id) },
                                onDuplicate = { duplicate(config) },
                                onDelete = { pendingDelete = config }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddSheet) {
        AddProviderSheet(
            providers = providers,
            counts = byProvider.mapValues { it.value.size },
            onPick = { providerId ->
                showAddSheet = false
                onAdd(providerId)
            },
            onDismiss = { showAddSheet = false }
        )
    }

    pendingDelete?.let { config ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除配置") },
            text = { Text("确定删除「${config.name}」？删除后不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    val id = config.id
                    Settings.update { old ->
                        old.copy(
                            configs = old.configs.filterNot { it.id == id },
                            activeConfigId = TtsConfigs.activeIdAfterDelete(
                                old.configs,
                                old.activeConfigId,
                                id
                            )
                        )
                    }
                    Speech.get().applySpeed(Settings.state.value.speed)
                    pendingDelete = null
                    toast("已删除")
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun ProviderTemplateCard(
    provider: TtsProvider,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProviderAvatar(provider.id, provider.displayName)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    provider.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    "模板 · 默认模型 ${provider.defaultConfig.model.ifBlank { "未设置" }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                Icons.Filled.Add,
                contentDescription = "添加 ${provider.displayName} 配置",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun ConfigCard(
    config: TtsConfig,
    providerName: String,
    onOpen: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    Card(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProviderAvatar(config.providerId, config.name)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    config.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    providerName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "${config.model.ifBlank { "未填模型" }} · ${config.voice.ifBlank { "未填音色" }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "更多操作")
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("编辑") },
                        onClick = {
                            menu = false
                            onOpen()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("复制") },
                        onClick = {
                            menu = false
                            onDuplicate()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("删除") },
                        onClick = {
                            menu = false
                            onDelete()
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddProviderSheet(
    providers: List<TtsProvider>,
    counts: Map<String, Int>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Text("添加配置", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                "选择供应商模板；保存后可继续改 Key、模型和音色。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(14.dp))
            providers.chunked(2).forEach { rowProviders ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    rowProviders.forEach { provider ->
                        ProviderPickCard(
                            provider = provider,
                            count = counts[provider.id] ?: 0,
                            modifier = Modifier.weight(1f),
                            onClick = { onPick(provider.id) }
                        )
                    }
                    if (rowProviders.size == 1) {
                        Spacer(Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun ProviderPickCard(
    provider: TtsProvider,
    count: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProviderAvatar(provider.id, provider.displayName, 38.dp)
                Spacer(Modifier.width(8.dp))
                Icon(
                    Icons.Filled.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                provider.displayName,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                if (count > 0) "已有 $count 条 · 再加一条" else "默认模型 ${provider.defaultConfig.model}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun EmptyState(
    hasQuery: Boolean,
    modifier: Modifier,
    onClear: () -> Unit,
    onAdd: () -> Unit
) {
    Column(
        modifier = modifier.padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            if (hasQuery) "没有匹配的配置" else "还没有 TTS 配置",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(6.dp))
        Text(
            if (hasQuery) "换个关键词试试。" else "从供应商模板添加第一条。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        if (hasQuery) {
            OutlinedButton(onClick = onClear) { Text("清除搜索") }
        } else {
            OutlinedButton(onClick = onAdd) { Text("添加配置") }
        }
    }
}
