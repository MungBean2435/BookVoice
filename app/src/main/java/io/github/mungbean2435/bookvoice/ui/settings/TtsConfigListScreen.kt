package io.github.mungbean2435.bookvoice.ui.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import io.github.mungbean2435.bookvoice.ui.components.LocalFloatingChromeReserve

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
    var showSheet by remember { mutableStateOf(false) }
    var showTemplates by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<TtsConfig?>(null) }

    fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    fun providerName(providerId: String): String =
        ProviderRegistry.get(providerId)?.displayName ?: providerId

    val active = current.activeConfigOrNull()
    val activeProviderId = active?.providerId ?: "mimo"
    val visible = current.configs.filter { config ->
        val q = query.trim()
        q.isBlank() || listOf(
            config.name,
            providerName(config.providerId),
            config.model,
            config.voice
        ).any { it.contains(q, ignoreCase = true) }
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
                    IconButton(
                        onClick = {
                            showTemplates = false
                            showSheet = true
                        }
                    ) {
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
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 4.dp),
                style = MaterialTheme.typography.headlineMedium
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("搜索名称 / 供应商 / 模型 / 音色") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(28.dp)
            )

            if (visible.isEmpty()) {
                EmptyState(
                    hasQuery = query.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    onClear = { query = "" },
                    onAdd = {
                        showTemplates = true
                        showSheet = true
                    }
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    state = listState,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 4.dp,
                        bottom = 24.dp + LocalFloatingChromeReserve.current
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(visible, key = { it.id }) { config ->
                        ConfigCard(
                            config = config,
                            active = config.id == active?.id,
                            providerName = providerName(config.providerId),
                            onOpen = { onEdit(config.id) },
                            onActivate = {
                                Settings.update { old -> old.copy(activeConfigId = config.id) }
                                Speech.get().applySpeed(Settings.state.value.speed)
                                toast("已切换到「${config.name}」")
                            },
                            onDuplicate = {
                                val copy = TtsConfigs.duplicate(config, current.configs.map { it.name })
                                Settings.update { old -> old.copy(configs = old.configs + copy) }
                                toast("已复制为「${copy.name}」")
                            },
                            onDelete = { pendingDelete = config }
                        )
                    }
                }
            }
        }
    }

    if (showSheet) {
        ModalBottomSheet(onDismissRequest = { showSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp)
            ) {
                if (!showTemplates) {
                    Text("新建配置", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(12.dp))
                    SheetRow(
                        title = "添加配置",
                        subtitle = "使用当前供应商（${providerName(activeProviderId)}）的模板",
                        onClick = {
                            showSheet = false
                            onAdd(activeProviderId)
                        }
                    )
                    Spacer(Modifier.height(8.dp))
                    SheetRow(
                        title = "添加配置模板",
                        subtitle = "先选 MiMo / MiniMax 模板，再填字段",
                        onClick = { showTemplates = true }
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { showTemplates = false }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                        Text("添加配置模板", style = MaterialTheme.typography.titleLarge)
                    }
                    Text(
                        "选中模板后会进入详情页，字段都可以改。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    ProviderRegistry.all().forEach { provider ->
                        Card(
                            onClick = {
                                showSheet = false
                                onAdd(provider.id)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ProviderAvatar(provider.id, provider.displayName, 44.dp)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(provider.displayName, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "默认模型 ${provider.defaultConfig.model}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text("›", style = MaterialTheme.typography.titleLarge)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }

    pendingDelete?.let { config ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除配置") },
            text = {
                val deletingActive = config.id == current.activeConfigId
                val hasOther = current.configs.any { it.id != config.id }
                val suffix = when {
                    deletingActive && hasOther -> "这是当前启用的配置，删除后会切换到另一条。"
                    deletingActive -> "这是最后一条配置，删除后需要重新添加才能朗读。"
                    else -> ""
                }
                Text("确定删除「${config.name}」？$suffix")
            },
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
private fun ConfigCard(
    config: TtsConfig,
    active: Boolean,
    providerName: String,
    onOpen: () -> Unit,
    onActivate: () -> Unit,
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
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ConfigStatusChip(active)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        providerName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
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
                    if (!active) {
                        DropdownMenuItem(
                            text = { Text("设为启用") },
                            onClick = {
                                menu = false
                                onActivate()
                            }
                        )
                    }
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

@Composable
private fun SheetRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text("›", style = MaterialTheme.typography.titleLarge)
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
            if (hasQuery) "换个关键词试试。" else "从模板添加第一条，或新建一条手动填。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        if (hasQuery) {
            OutlinedButton(onClick = onClear) { Text("清除搜索") }
        } else {
            Button(onClick = onAdd) { Text("添加配置模板") }
        }
    }
}
