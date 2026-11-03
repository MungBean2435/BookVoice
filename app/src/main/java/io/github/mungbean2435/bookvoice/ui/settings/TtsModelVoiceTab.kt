package io.github.mungbean2435.bookvoice.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mungbean2435.bookvoice.tts.TtsProvider
import io.github.mungbean2435.bookvoice.tts.Voice

/**
 * 「模型与音色」页：模型、音色两个库，点卡片设为当前；添加动作在底部悬浮栏。
 */
@Composable
fun ModelVoiceTab(
    provider: TtsProvider,
    model: String,
    voice: String,
    models: List<String>,
    voices: List<Voice>,
    onSelectModel: (String) -> Unit,
    onSelectVoice: (String) -> Unit,
    onRemoveModel: (String) -> Unit,
    onRemoveVoice: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { SectionTitle("模型", models.size) }
        if (models.isEmpty()) {
            item { EmptyLibrary("还没有模型，点下面添加") }
        }
        items(models, key = { "m:$it" }) { id ->
            ModelCard(
                provider = provider,
                modelId = id,
                current = id == model,
                onSelect = { onSelectModel(id) },
                onRemove = { onRemoveModel(id) }
            )
        }
        item {
            Spacer(Modifier.height(6.dp))
            SectionTitle("音色", voices.size)
        }
        if (voices.isEmpty()) {
            item { EmptyLibrary("还没有音色，点下面添加") }
        }
        items(voices, key = { "v:${it.id}" }) { item ->
            VoiceCard(
                voice = item,
                current = item.id == voice,
                onSelect = { onSelectVoice(item.id) },
                onRemove = { onRemoveVoice(item.id) }
            )
        }
    }
}

@Composable
private fun SectionTitle(title: String, count: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.width(6.dp))
        Text(
            count.toString(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun EmptyLibrary(text: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Text(
            text,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ModelCard(
    provider: TtsProvider,
    modelId: String,
    current: Boolean,
    onSelect: () -> Unit,
    onRemove: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    Card(
        onClick = onSelect,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (current) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = if (current) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProviderAvatar(provider.id, provider.displayName, 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    modelId,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TagChip("TTS")
                    TagChip(provider.audioFormat)
                    TagChip(modelHint(provider.id, modelId))
                    if (current) ConfigStatusChip(true, label = "当前")
                }
            }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "模型操作")
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (!current) {
                        DropdownMenuItem(
                            text = { Text("设为当前") },
                            onClick = {
                                menu = false
                                onSelect()
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("删除") },
                        onClick = {
                            menu = false
                            onRemove()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun VoiceCard(
    voice: Voice,
    current: Boolean,
    onSelect: () -> Unit,
    onRemove: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    Card(
        onClick = onSelect,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (current) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = if (current) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    voice.label.take(1).ifBlank { "音" },
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    voice.label,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    voice.id,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (voice.hint.isNotBlank()) TagChip(voice.hint)
                    if (current) ConfigStatusChip(true, label = "当前")
                }
            }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "音色操作")
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (!current) {
                        DropdownMenuItem(
                            text = { Text("设为当前") },
                            onClick = {
                                menu = false
                                onSelect()
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("删除") },
                        onClick = {
                            menu = false
                            onRemove()
                        }
                    )
                }
            }
        }
    }
}

private fun modelHint(providerId: String, modelId: String): String = when {
    providerId == "minimax" && modelId.contains("turbo") -> "极速"
    providerId == "minimax" && modelId.contains("hd") -> "高清"
    modelId.contains("tts") -> "预置音色"
    else -> "语音"
}
