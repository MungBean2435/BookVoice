package io.github.mungbean2435.bookvoice.ui.settings

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mungbean2435.bookvoice.data.settings.Settings
import io.github.mungbean2435.bookvoice.data.settings.TtsConfig
import io.github.mungbean2435.bookvoice.data.settings.TtsConfigs
import io.github.mungbean2435.bookvoice.playback.PlayerState
import io.github.mungbean2435.bookvoice.playback.Speech
import io.github.mungbean2435.bookvoice.tts.ProviderRegistry
import io.github.mungbean2435.bookvoice.tts.Voice

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TtsConfigEditScreen(
    configId: String?,
    templateId: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val settings by Settings.state.collectAsStateWithLifecycle()
    val playerState by Speech.get().state.collectAsStateWithLifecycle()

    val original = remember(configId) {
        configId?.let { id -> settings.configs.firstOrNull { it.id == id } }
    }
    val missing = configId != null && original == null
    if (missing) {
        LaunchedEffect(configId) {
            Toast.makeText(context, "配置不存在", Toast.LENGTH_SHORT).show()
            onBack()
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("配置不存在")
        }
        return
    }

    val isNew = original == null
    val stateKey = original?.id ?: "new-$templateId"
    val initial = remember(original?.id, templateId) {
        original ?: run {
            val provider = ProviderRegistry.get(templateId)
                ?: ProviderRegistry.get("mimo")
                ?: ProviderRegistry.all().first()
            TtsConfigs.template(
                providerId = provider.id,
                displayName = provider.displayName,
                baseUrl = provider.defaultConfig.baseUrl,
                model = provider.defaultConfig.model,
                voice = provider.defaultConfig.voice,
                existingNames = settings.configs.map { it.name }
            )
        }
    }
    val provider = remember(initial.providerId) {
        ProviderRegistry.get(initial.providerId) ?: ProviderRegistry.all().first()
    }
    val defaults = provider.defaultConfig
    val seeded = remember(initial, provider.id) {
        TtsConfigs.normalizeLibrary(
            initial.copy(
                model = initial.model.ifBlank { defaults.model },
                voice = initial.voice.ifBlank { defaults.voice }
            ),
            provider.voices()
        )
    }

    var tab by rememberSaveable(stateKey) { mutableIntStateOf(0) }
    var name by rememberSaveable(stateKey) { mutableStateOf(seeded.name) }
    var apiKey by rememberSaveable(stateKey) { mutableStateOf(seeded.apiKey) }
    var baseUrl by rememberSaveable(stateKey) {
        mutableStateOf(seeded.baseUrl.ifBlank { defaults.baseUrl })
    }
    var model by rememberSaveable(stateKey) { mutableStateOf(seeded.model) }
    var voice by rememberSaveable(stateKey) { mutableStateOf(seeded.voice) }
    val models = rememberSaveable(stateKey, saver = modelListSaver) {
        mutableStateListOf<String>().apply { addAll(seeded.models) }
    }
    val voices = rememberSaveable(stateKey, saver = voiceListSaver) {
        mutableStateListOf<Voice>().apply { addAll(seeded.voices) }
    }
    // 新建时只有列表为空才默认启用；已有正在用的配置时默认保存为「未启用」，
    // 避免加一条新配置就把正在听的配置切掉。
    val initiallyEnabled = (isNew && settings.configs.isEmpty()) ||
        (!isNew && initial.id == settings.activeConfigId)
    var enabled by rememberSaveable(stateKey) { mutableStateOf(initiallyEnabled) }
    var showKey by rememberSaveable(stateKey) { mutableStateOf(false) }
    var showCatalog by remember { mutableStateOf(false) }
    var showDiscard by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var requestingPreview by remember { mutableStateOf(false) }
    var previewSeen by remember { mutableStateOf(false) }
    // 保存/删除后已经调用 onBack；防止双击把列表页也一起弹掉。
    var finishing by remember { mutableStateOf(false) }

    val isCurrentlyActive = !isNew && settings.activeConfigId == initial.id
    val initialBaseUrl = seeded.baseUrl.ifBlank { defaults.baseUrl }
    val dirty = name != seeded.name ||
        apiKey != seeded.apiKey ||
        baseUrl != initialBaseUrl ||
        model != seeded.model ||
        voice != seeded.voice ||
        models.toList() != seeded.models ||
        voices.toList() != seeded.voices ||
        enabled != initiallyEnabled

    val previewing = requestingPreview &&
        (playerState is PlayerState.Playing || playerState is PlayerState.Buffering)
    val previewStatus = when {
        !previewing -> null
        playerState is PlayerState.Buffering -> "正在合成试听…"
        else -> "试听中…"
    }

    fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    fun buildDraft(): TtsConfig = TtsConfigs.normalizeLibrary(
        TtsConfig(
            id = initial.id,
            name = name.trim().ifBlank { provider.displayName },
            providerId = provider.id,
            apiKey = apiKey.trim(),
            baseUrl = baseUrl.trim().ifBlank { defaults.baseUrl },
            model = model.trim().ifBlank { defaults.model },
            voice = voice.trim().ifBlank { defaults.voice },
            models = models.toList(),
            voices = voices.toList()
        ),
        provider.voices()
    )

    fun removeModel(id: String) {
        models.remove(id)
        if (model == id) model = models.firstOrNull().orEmpty()
    }

    fun removeVoice(id: String) {
        voices.removeAll { it.id == id }
        if (voice == id) voice = voices.firstOrNull()?.id.orEmpty()
    }

    fun importCatalog(newModels: List<String>, newVoices: List<Voice>) {
        val addedModels = newModels.filter { it.isNotBlank() && it !in models }
        addedModels.forEach { models.add(it) }
        val addedVoices = newVoices.filter { v -> v.id.isNotBlank() && voices.none { it.id == v.id } }
        addedVoices.forEach { voices.add(it) }
        if (model.isBlank()) model = addedModels.firstOrNull() ?: models.firstOrNull().orEmpty()
        if (voice.isBlank()) voice = addedVoices.firstOrNull()?.id ?: voices.firstOrNull()?.id.orEmpty()
        showCatalog = false
        toast("已导入 ${addedModels.size} 个模型、${addedVoices.size} 个音色")
    }

    fun manualAdd(newModel: String, newVoice: String) {
        var added = 0
        if (newModel.isNotBlank() && newModel !in models) {
            models.add(newModel)
            added += 1
        }
        if (newVoice.isNotBlank() && voices.none { it.id == newVoice }) {
            voices.add(Voice(newVoice, newVoice))
            added += 1
        }
        if (model.isBlank() && newModel.isNotBlank()) model = newModel
        if (voice.isBlank() && newVoice.isNotBlank()) voice = newVoice
        if (model.isBlank()) model = models.firstOrNull().orEmpty()
        if (voice.isBlank()) voice = voices.firstOrNull()?.id.orEmpty()
        showCatalog = false
        toast(if (added > 0) "已添加" else "已经添加过")
    }

    LaunchedEffect(requestingPreview, playerState) {
        if (!requestingPreview) return@LaunchedEffect
        when (val state = playerState) {
            is PlayerState.Buffering, is PlayerState.Playing -> previewSeen = true

            is PlayerState.Error -> {
                toast("试听失败：${state.message}")
                Speech.get().clearError()
                requestingPreview = false
                previewSeen = false
            }

            PlayerState.Idle -> {
                if (previewSeen) {
                    requestingPreview = false
                    previewSeen = false
                }
            }

            else -> Unit
        }
    }

    fun requestBack() {
        if (finishing) return
        if (dirty) showDiscard = true else onBack()
    }

    fun preview() {
        if (finishing) return
        if (apiKey.trim().isBlank()) {
            toast("先填 ${provider.displayName} 的 API Key")
            return
        }
        val draft = buildDraft()
        if (provider.id == "minimax" && draft.voice.isBlank()) {
            toast("MiniMax 还需要选一个音色（可从目录导入或手动填 ID）")
            return
        }
        requestingPreview = true
        previewSeen = false
        Speech.get().speakPreview(
            text = "你好，这是听书的声音测试。",
            config = draft
        )
    }

    fun save() {
        if (finishing) return
        if (name.trim().isBlank()) {
            toast("请填写名称")
            return
        }
        finishing = true
        val config = buildDraft().copy(name = name.trim())
        Settings.update { old ->
            val configs = TtsConfigs.replace(old.configs, config)
            val active = when {
                enabled -> config.id
                old.activeConfigId.isNotBlank() && configs.any { it.id == old.activeConfigId } -> {
                    old.activeConfigId
                }

                else -> config.id
            }
            old.copy(configs = configs, activeConfigId = active)
        }
        Speech.get().applySpeed(Settings.state.value.speed)
        toast("已保存")
        onBack()
    }

    fun delete() {
        if (finishing) return
        finishing = true
        val id = initial.id
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
        toast("已删除")
        onBack()
    }

    BackHandler { requestBack() }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = { requestBack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    },
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ProviderAvatar(provider.id, name, 28.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (isNew) "新建配置" else initial.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                TabPills(selected = tab, onSelect = { tab = it })

                when (tab) {
                    0 -> Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 120.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text("是否启用", style = MaterialTheme.typography.titleMedium)
                                    TtsHint("同一时间只有一条配置用于朗读；要停用当前配置，请先在列表启用另一条。")
                                }
                                Switch(
                                    checked = enabled,
                                    onCheckedChange = { want ->
                                        when {
                                            !want && isCurrentlyActive -> toast("请先在列表启用另一条配置")
                                            !want && isNew && settings.configs.isEmpty() ->
                                                toast("至少要有一条启用的配置")

                                            else -> enabled = want
                                        }
                                    }
                                )
                            }
                        }

                        TtsFieldCard("名称") {
                            OutlinedTextField(
                                value = name,
                                onValueChange = { name = it },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )
                        }

                        TtsFieldCard("API Key") {
                            OutlinedTextField(
                                value = apiKey,
                                onValueChange = { apiKey = it },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                visualTransformation = if (showKey) {
                                    VisualTransformation.None
                                } else {
                                    PasswordVisualTransformation()
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                trailingIcon = {
                                    TextButton(onClick = { showKey = !showKey }) {
                                        Text(if (showKey) "隐藏" else "显示")
                                    }
                                }
                            )
                            Spacer(Modifier.height(6.dp))
                            TtsHint("Key 只存本机，不上传、不进备份、不打日志。")
                        }

                        TtsFieldCard("接口地址") {
                            OutlinedTextField(
                                value = baseUrl,
                                onValueChange = { baseUrl = it },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                            )
                            Spacer(Modifier.height(6.dp))
                            TtsHint(
                                if (provider.id == "minimax") {
                                    "完整地址，例如 https://api.minimax.cn/v1/t2a_v2"
                                } else {
                                    "完整地址，例如 https://api.xiaomimimo.com/v1/chat/completions"
                                }
                            )
                        }

                        TtsFieldCard("供应商模板") {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                ProviderAvatar(provider.id, provider.displayName, 36.dp)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(provider.displayName, style = MaterialTheme.typography.bodyLarge)
                                    TtsHint("音频格式 ${provider.audioFormat}")
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            TtsHint("模板创建后不可更换；需要别的模板请新建配置。")
                        }
                    }

                    1 -> ModelVoiceTab(
                        provider = provider,
                        model = model,
                        voice = voice,
                        models = models,
                        voices = voices,
                        onSelectModel = { model = it },
                        onSelectVoice = { voice = it },
                        onRemoveModel = { removeModel(it) },
                        onRemoveVoice = { removeVoice(it) },
                        modifier = Modifier.weight(1f)
                    )

                    else -> Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        TtsFieldCard("恢复模板默认值") {
                            TtsHint("只重置接口地址、模型和音色，不改名称和 API Key。")
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(onClick = {
                                baseUrl = defaults.baseUrl
                                model = defaults.model
                                voice = defaults.voice
                                if (defaults.model.isNotBlank() && defaults.model !in models) {
                                    models.add(defaults.model)
                                }
                                if (defaults.voice.isNotBlank() && voices.none { it.id == defaults.voice }) {
                                    val known = provider.voices().firstOrNull { it.id == defaults.voice }
                                    voices.add(known ?: Voice(defaults.voice, defaults.voice))
                                }
                                toast("已恢复模板默认值")
                            }) {
                                Text("恢复默认值")
                            }
                        }

                        TtsFieldCard("供应商能力") {
                            Text(
                                "${provider.displayName} · ${provider.audioFormat}",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Spacer(Modifier.height(6.dp))
                            TtsHint(
                                if (provider.id == "minimax") {
                                    "语速由服务端处理；播放端固定 1×，避免双重变速。"
                                } else {
                                    "语速由播放端处理；改音色会让旧缓存自然失效。"
                                }
                            )
                        }

                        TtsFieldCard("配置 ID") {
                            TtsHint(if (isNew) "保存后生成" else initial.id)
                        }

                        TtsFieldCard("删除") {
                            TtsHint("删除后不可恢复；如果删的是当前配置，会自动切换到剩余的第一条。")
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = { showDelete = true },
                                enabled = !isNew && !finishing
                            ) {
                                Text("删除此配置", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
        if (tab != 2) {
            TtsFloatingBar(
                tab = tab,
                previewing = previewing,
                previewStatus = previewStatus,
                isNew = isNew,
                finishing = finishing,
                onPreview = { preview() },
                onDelete = { showDelete = true },
                onSave = { save() },
                onAdd = { showCatalog = true },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            )
        }
    }


    if (showCatalog) {
        TtsCatalogSheet(
            provider = provider,
            apiKey = apiKey,
            baseUrl = baseUrl,
            existingModels = models.toSet(),
            existingVoiceIds = voices.map { it.id }.toSet(),
            onDismiss = { showCatalog = false },
            onImport = { newModels, newVoices -> importCatalog(newModels, newVoices) },
            onManualAdd = { newModel, newVoice -> manualAdd(newModel, newVoice) }
        )
    }

    if (showDiscard) {
        AlertDialog(
            onDismissRequest = { showDiscard = false },
            title = { Text("放弃修改？") },
            text = { Text("还有未保存的改动，返回后会丢失。") },
            confirmButton = {
                TextButton(onClick = {
                    showDiscard = false
                    onBack()
                }) { Text("放弃") }
            },
            dismissButton = {
                TextButton(onClick = { showDiscard = false }) { Text("继续编辑") }
            }
        )
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("删除配置") },
            text = { Text("确定删除「${name.ifBlank { initial.name }}」？删除后不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    showDelete = false
                    delete()
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun TabPills(selected: Int, onSelect: (Int) -> Unit) {
    val labels = listOf("配置", "模型与音色", "高级设置")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        labels.forEachIndexed { index, label ->
            Surface(
                onClick = { onSelect(index) },
                shape = RoundedCornerShape(50),
                color = if (selected == index) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            ) {
                Text(
                    label,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp),
                    style = MaterialTheme.typography.titleSmall,
                    color = if (selected == index) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

private val modelListSaver = listSaver<SnapshotStateList<String>, String>(
    save = { it.toList() },
    restore = { list -> mutableStateListOf<String>().apply { addAll(list) } }
)

private val voiceListSaver = listSaver<SnapshotStateList<Voice>, String>(
    save = { list -> list.map { "${it.id}\u0001${it.label}\u0001${it.hint}" } },
    restore = { list ->
        mutableStateListOf<Voice>().apply {
            addAll(list.map { raw ->
                val parts = raw.split('\u0001')
                val id = parts.getOrElse(0) { "" }
                Voice(id, parts.getOrElse(1) { id }, parts.getOrElse(2) { "" })
            })
        }
    }
)
