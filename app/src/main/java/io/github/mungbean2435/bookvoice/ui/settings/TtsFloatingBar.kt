package io.github.mungbean2435.bookvoice.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.mungbean2435.bookvoice.ui.components.GlassSurface
import io.github.mungbean2435.bookvoice.ui.theme.BookVoiceDesign

/** 试听在底部条上占哪个位置：空闲时是 ▶，一旦开始就原位换成停止。 */
enum class PreviewUiState { Idle, Composing, Playing, Failed }

/**
 * 详情页底部的悬浮胶囊。
 *
 * 外观跟 MiniPlayer / 底部导航同一套：中性底 + 0.5dp 细描边 + 连续圆角、无投影。
 * 主按钮在「保存」时用实心主色；「完成」「添加模型与音色」用浅色 primaryContainer，
 * 保证和玻璃底有明显层次，不再和条形底几乎同色。
 *
 * 状态规则：
 * - 试听一旦开始（合成中 / 播放中）左侧原位变「停止」，不再禁用，随时可中止；
 * - 试听状态文字占用一条高度随内容收缩的行，失败时停在错误色上并保留重试入口；
 * - 主按钮文案随改动状态变化：有改动是「保存」，没有改动是「完成」。
 *
 * - tab 0 配置：试听 / 删除 / 保存
 * - tab 1 模型与音色：试听 / 删除 / 小保存图标 / 添加模型与音色
 * - tab 2 高级设置：试听 / 保存（删除在页内已有入口）
 */
@Composable
fun TtsFloatingBar(
    tab: Int,
    previewState: PreviewUiState,
    previewStatus: String?,
    isNew: Boolean,
    dirty: Boolean,
    finishing: Boolean,
    showDelete: Boolean,
    onPreview: () -> Unit,
    onStopPreview: () -> Unit,
    onDelete: () -> Unit,
    onSave: () -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier
) {
    val modelVoiceTab = tab == 1
    val tokens = BookVoiceDesign.tokens
    val colors = MaterialTheme.colorScheme

    val failed = previewState == PreviewUiState.Failed
    // 只有「真的要写盘」时主按钮才是实心主色：
    // 「完成」没有可保存的内容，「添加模型与音色」只是打开目录浮层，都不抢主色，
    // 但要用浅色块 + 描边和玻璃底拉开差距，否则看起来不像按钮。
    val committing = !modelVoiceTab && (isNew || dirty)
    val primaryLabel = when {
        modelVoiceTab -> "添加模型与音色"
        committing -> "保存"
        else -> "完成"
    }
    val primaryColors = if (committing) {
        ButtonDefaults.buttonColors()
    } else {
        ButtonDefaults.buttonColors(
            containerColor = colors.primaryContainer,
            contentColor = colors.onPrimaryContainer,
            disabledContainerColor = colors.primaryContainer.copy(alpha = 0.55f),
            disabledContentColor = colors.onPrimaryContainer.copy(alpha = 0.6f)
        )
    }
    // 浅色块 + 实色描边，动态取色把 primaryContainer 染得很淡时也能看出边界。
    val primaryBorder = if (committing) {
        null
    } else {
        BorderStroke(1.dp, colors.outline)
    }

    GlassSurface(
        modifier = modifier.fillMaxWidth(),
        shape = tokens.groupShape
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            // 状态行只在试听期间出现，用同一条渐变展开/收起，避免整条高度硬跳。
            AnimatedVisibility(
                visible = previewStatus != null,
                enter = fadeIn(tween(160)) + expandVertically(
                    animationSpec = tween(180, easing = FastOutSlowInEasing)
                ),
                exit = fadeOut(tween(120)) + shrinkVertically(
                    animationSpec = tween(160, easing = FastOutSlowInEasing)
                )
            ) {
                Text(
                    text = previewStatus.orEmpty(),
                    modifier = Modifier.padding(start = 14.dp, top = 2.dp, bottom = 2.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (failed) colors.error else colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (previewState == PreviewUiState.Composing ||
                    previewState == PreviewUiState.Playing
                ) {
                    // 自绘方块停止键：项目不引 material-icons-extended，传输键也是自绘的。
                    IconButton(
                        onClick = onStopPreview,
                        enabled = !finishing,
                        modifier = Modifier.semantics { contentDescription = "停止试听" }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(colors.onSurface)
                        )
                    }
                } else {
                    IconButton(onClick = onPreview, enabled = !finishing) {
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = "试听",
                            tint = colors.onSurface
                        )
                    }
                }
                if (showDelete && !isNew) {
                    IconButton(onClick = onDelete, enabled = !finishing) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "删除",
                            tint = colors.onSurfaceVariant
                        )
                    }
                }
                if (modelVoiceTab) {
                    // 大号「保存」被「添加模型与音色」替换后，保留一个小的保存入口，
                    // 否则这个页签的模型/音色改动无法落盘。有未保存改动时点亮。
                    IconButton(onClick = onSave, enabled = !finishing) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = "保存",
                            tint = if (dirty) colors.primary else colors.onSurfaceVariant
                        )
                    }
                }
                Button(
                    onClick = if (modelVoiceTab) onAdd else onSave,
                    enabled = !finishing,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = tokens.controlShape,
                    colors = primaryColors,
                    border = primaryBorder,
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    Text(
                        text = primaryLabel,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
