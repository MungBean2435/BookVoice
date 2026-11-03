package io.github.mungbean2435.bookvoice.ui.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.mungbean2435.bookvoice.data.backup.BackupManager
import io.github.mungbean2435.bookvoice.data.db.Db
import io.github.mungbean2435.bookvoice.data.settings.Settings
import io.github.mungbean2435.bookvoice.ui.components.LocalFloatingChromeReserve
import io.github.mungbean2435.bookvoice.tts.rules.RulePack
import io.github.mungbean2435.bookvoice.tts.rules.RulesEngine
import io.github.mungbean2435.bookvoice.tts.rules.TtsRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulePacksScreen(onBack: () -> Unit, listState: LazyListState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var packs by remember { mutableStateOf<List<RulePack>>(emptyList()) }
    var builtinEnabled by remember { mutableStateOf(Settings.state.value.builtinRulesEnabled) }
    var expanded by remember { mutableStateOf<Set<String>>(emptySet()) }

    suspend fun reload() {
        packs = withContext(Dispatchers.IO) { Db.get().rulePacks() }
    }

    LaunchedEffect(Unit) { reload() }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val result = runCatching {
                    withContext(Dispatchers.IO) { BackupManager(context).importRulePack(uri) }
                }
                Toast.makeText(
                    context,
                    result.getOrElse { "导入失败：${it.message}" },
                    Toast.LENGTH_LONG
                ).show()
                reload()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("朗读规则") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            state = listState,
            contentPadding = PaddingValues(bottom = 24.dp + LocalFloatingChromeReserve.current),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    "规则只改送去合成的文本，正文显示不变。改完规则，旧音频会自动作废重合成。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            item {
                PackCard(
                    title = RulesEngine.builtin.name,
                    subtitle = "内置 · ${RulesEngine.builtin.rules.size} 条 · 省略号/破折号/百分号/摄氏度等",
                    enabled = builtinEnabled,
                    onToggle = { on ->
                        builtinEnabled = on
                        Settings.update { it.copy(builtinRulesEnabled = on) }
                    },
                    expanded = expanded.contains("builtin"),
                    onExpand = {
                        expanded = if (expanded.contains("builtin")) expanded - "builtin" else expanded + "builtin"
                    },
                    rules = RulesEngine.builtin.rules,
                    canDelete = false,
                    onDelete = {}
                )
            }

            items(packs, key = { it.id }) { pack ->
                PackCard(
                    title = pack.name,
                    subtitle = "导入 · ${pack.rules.size} 条",
                    enabled = pack.enabled,
                    onToggle = { on ->
                        packs = packs.map { if (it.id == pack.id) it.copy(enabled = on) else it }
                        scope.launch(Dispatchers.IO) { Db.get().setRulePackEnabled(pack.id, on) }
                    },
                    expanded = expanded.contains(pack.id),
                    onExpand = {
                        expanded = if (expanded.contains(pack.id)) expanded - pack.id else expanded + pack.id
                    },
                    rules = pack.rules,
                    canDelete = true,
                    onDelete = {
                        packs = packs.filterNot { it.id == pack.id }
                        scope.launch(Dispatchers.IO) { Db.get().deleteRulePack(pack.id) }
                    }
                )
            }

            item {
                OutlinedButton(
                    onClick = { importer.launch(arrayOf("application/json", "text/plain")) },
                    modifier = Modifier.padding(vertical = 8.dp)
                ) { Text("导入规则包（JSON）") }
            }
        }
    }
}

@Composable
private fun PackCard(
    title: String,
    subtitle: String,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    expanded: Boolean,
    onExpand: () -> Unit,
    rules: List<TtsRule>,
    canDelete: Boolean,
    onDelete: () -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(
                    Modifier
                        .weight(1f)
                        .clickable { onExpand() }
                ) {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = enabled, onCheckedChange = onToggle)
            }

            if (expanded) {
                Column(Modifier.padding(top = 8.dp)) {
                    rules.forEach { rule ->
                        val valid = rule.type != "regex" || runCatching { Regex(rule.from) }.isSuccess
                        Text(
                            "${rule.from} → ${rule.to.ifEmpty { "（删除）" }}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (valid) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error
                        )
                        if (rule.note.isNotBlank()) {
                            Text(
                                rule.note,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (!valid) {
                            Text(
                                "正则语法错误，这条会被跳过",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    if (canDelete) {
                        TextButton(onClick = onDelete) { Text("删除这个规则包") }
                    }
                }
            }
        }
    }
}
