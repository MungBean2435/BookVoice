package io.github.mungbean2435.bookvoice.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * 详情页底部的悬浮胶囊。
 *
 * - tab 0 配置：试听 / 删除 / 保存
 * - tab 1 模型与音色：试听 / 删除 / 小保存图标 / 添加模型与音色
 * - tab 2 不渲染（调用方判断）
 */
@Composable
fun TtsFloatingBar(
    tab: Int,
    previewing: Boolean,
    previewStatus: String?,
    isNew: Boolean,
    finishing: Boolean,
    onPreview: () -> Unit,
    onDelete: () -> Unit,
    onSave: () -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier
) {
    val modelVoiceTab = tab == 1
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(40.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        shadowElevation = 8.dp
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            if (previewStatus != null) {
                Text(
                    text = previewStatus,
                    modifier = Modifier.padding(start = 14.dp, top = 2.dp, bottom = 2.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPreview, enabled = !previewing && !finishing) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = "试听")
                }
                if (!isNew) {
                    IconButton(onClick = onDelete, enabled = !finishing) {
                        Icon(Icons.Filled.Delete, contentDescription = "删除")
                    }
                }
                if (modelVoiceTab) {
                    // 大号「保存」被「添加模型与音色」替换后，保留一个小的保存入口，
                    // 否则这个页签的模型/音色改动无法落盘。
                    IconButton(onClick = onSave, enabled = !finishing) {
                        Icon(Icons.Filled.Check, contentDescription = "保存")
                    }
                }
                Button(
                    onClick = if (modelVoiceTab) onAdd else onSave,
                    enabled = !finishing,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    Text(
                        text = if (modelVoiceTab) "添加模型与音色" else "保存",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
