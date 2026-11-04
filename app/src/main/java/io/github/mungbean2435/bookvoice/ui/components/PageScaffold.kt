package io.github.mungbean2435.bookvoice.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow

/**
 * 统一页面壳：大标题展开、随手指收起，收起后是左上角小标题。
 *
 * [state] 由共享宿主传入（rememberSaveable 保存），页面自己不 remember；
 * 这样子页返回、切 tab、旋转屏幕后折叠进度都能恢复。
 * 不传时退回旧行为，方便 Preview 与独立使用。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookVoicePageScaffold(
    title: String,
    modifier: Modifier = Modifier,
    state: TopAppBarState = rememberTopAppBarState(),
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    /** 目录页顶部还要放书籍信息，标题栏保持紧凑，避免占掉首屏。 */
    largeTitle: Boolean = true,
    content: @Composable (PaddingValues) -> Unit
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        state = state,
        snapAnimationSpec = null
    )

    Scaffold(
        // 只有大标题栏需要把滚动交给折叠行为；紧凑顶栏不参与折叠，
        // 再把 nestedScroll 接上去会截走列表的上下滑动。
        modifier = if (largeTitle) {
            modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
        } else {
            modifier
        },
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            // 胶囊是浮层，Scaffold 不知道它占了多高；把 FAB 抬到胶囊上方。
            Box(Modifier.padding(bottom = LocalFloatingChromeReserve.current)) {
                floatingActionButton()
            }
        },
        topBar = {
            if (largeTitle) {
                LargeTopAppBar(
                    title = {
                        // 展开槽拿到的是大标题样式，收起槽是小标题样式；只换字形，不复用两套标题。
                        val expanded = LocalTextStyle.current.fontSize.value > 24f
                        Text(
                            text = title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = if (expanded) {
                                MaterialTheme.typography.headlineSmall
                            } else {
                                MaterialTheme.typography.titleMedium
                            }
                        )
                    },
                    navigationIcon = {
                        if (onBack != null) {
                            IconButton(onClick = onBack) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "返回"
                                )
                            }
                        }
                    },
                    actions = actions,
                    scrollBehavior = scrollBehavior,
                    colors = TopAppBarDefaults.largeTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        scrolledContainerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            } else {
                TopAppBar(
                    title = {
                        Text(
                            text = title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium
                        )
                    },
                    navigationIcon = {
                        if (onBack != null) {
                            IconButton(onClick = onBack) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "返回"
                                )
                            }
                        }
                    },
                    actions = actions,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        },
        content = content
    )
}
