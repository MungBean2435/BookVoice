package io.github.mungbean2435.bookvoice.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.mungbean2435.bookvoice.data.settings.AppLanguage
import io.github.mungbean2435.bookvoice.ui.ImportTask
import io.github.mungbean2435.bookvoice.ui.theme.BookVoiceDesign
import kotlinx.coroutines.delay

/**
 * 导入任务的常驻状态卡：进度、当前文件、失败项与重试都放在用户正在看的地方。
 * 导入中禁止再点导入入口；失败后不自动消失，直到用户重试或关闭。
 */
@Composable
fun ImportTaskCard(
    task: ImportTask,
    language: AppLanguage,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val english = language == AppLanguage.En
    val tokens = BookVoiceDesign.tokens
    val failedCount = task.failures.size
    val stopping = task.cancelRequested && task.running

    val seconds by produceState(0L, task.running, task.startedAt) {
        while (task.running) {
            value = ((System.currentTimeMillis() - task.startedAt) / 1000L).coerceAtLeast(0L)
            delay(1_000L)
        }
    }

    val title = when {
        stopping -> if (english) "Stopping…" else "正在停止…"
        task.running && task.total > 1 -> if (english) {
            "Importing ${task.completed}/${task.total}"
        } else {
            "正在导入 ${task.completed}/${task.total}"
        }

        task.running -> if (english) "Importing…" else "正在导入…"
        task.cancelled -> if (english) "Import stopped" else "已停止导入"
        failedCount > 0 -> if (english) {
            "$failedCount file(s) not imported"
        } else {
            "有 $failedCount 个文件未导入"
        }

        else -> if (english) "Import finished" else "导入完成"
    }

    val detail = when {
        task.running && task.currentName != null && task.total == 1 ->
            if (english) "${task.currentName} · ${seconds}s" else "《${task.currentName}》· ${seconds} 秒"

        task.running -> task.currentName ?: if (english) "Preparing…" else "准备中…"
        task.cancelled || failedCount > 0 ->
            if (english) "Imported ${task.imported} of ${task.total}" else "已导入 ${task.imported}/${task.total} 本"

        else -> null
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = tokens.cardShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 0.dp
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = title,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (detail != null) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (task.running && task.total > 1) {
                    TextButton(
                        onClick = onCancel,
                        enabled = !task.cancelRequested
                    ) {
                        Text(if (english) "Stop" else "停止")
                    }
                } else if (!task.running) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = if (english) "Dismiss" else "关闭"
                        )
                    }
                }
            }

            if (task.running) {
                Spacer(Modifier.height(8.dp))
                if (task.total > 1) {
                    LinearProgressIndicator(
                        progress = { task.completed.toFloat() / task.total },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }

            if (failedCount > 0) {
                Spacer(Modifier.height(8.dp))
                task.failures.take(3).forEach { failure ->
                    Text(
                        text = failure.name,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = failure.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                }
                if (failedCount > 3) {
                    Text(
                        text = if (english) {
                            "and ${failedCount - 3} more"
                        } else {
                            "另有 ${failedCount - 3} 个文件"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (!task.running && (failedCount > 0 || task.cancelled)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    if (failedCount > 0) {
                        TextButton(onClick = onRetry) {
                            Text(if (english) "Retry failed" else "重试失败项")
                        }
                    }
                    TextButton(onClick = onDismiss) {
                        Text(if (english) "Dismiss" else "关闭")
                    }
                }
            }
        }
    }
}
