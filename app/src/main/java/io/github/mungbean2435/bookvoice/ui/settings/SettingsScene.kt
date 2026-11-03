package io.github.mungbean2435.bookvoice.ui.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.mungbean2435.bookvoice.ui.nav.SETTINGS_PHONE_SETTLE_MILLIS
import io.github.mungbean2435.bookvoice.ui.nav.SETTINGS_TABLET_ENTER_MILLIS
import io.github.mungbean2435.bookvoice.ui.nav.SettingsPagesState
import io.github.mungbean2435.bookvoice.ui.nav.rememberSeekableBackTransition
import io.github.mungbean2435.bookvoice.ui.nav.settingsPageTransform

/** 设置场景内的页面标识。首页为 `null`，子页用这里的常量。 */
object SettingsPage {
    const val Home = "settings/home"
    const val Appearance = "settings/appearance"
    const val Backup = "settings/backup"
    const val Playback = "settings/playback"

    /** 存储与缓存和其它子页一样在本场景内换页，右侧滑入、返回滑出。 */
    const val Storage = "settings/storage"
    const val Diagnostics = "settings/diagnostics"
    const val Rules = "settings/rules"
    const val TtsList = "settings/tts"

    private val all = setOf(Appearance, Backup, Playback, Storage, Diagnostics, Rules, TtsList)

    fun isKnown(page: String?): Boolean = page == null || page in all
}

/**
 * 设置场景：首页与子页之间用同一个 AnimatedContent 切换，点击与返回手势共用一条时间线。
 *
 * - 手机：整页横滑，spring(noBouncy, mediumLow)；
 * - 平板（宽度 ≥ 600dp）：淡入 + 缩放，进 300ms / 0.98、退 160ms / 0.99；
 * - 两种都套 SizeTransform(clip = true)，统一由 NavigationTransitions 提供；
 * - 返回手势按进度 seek 同一个 transition，松手提交、取消按进度回弹。
 *
 * 页面状态仍由宿主 [io.github.mungbean2435.bookvoice.ui.AppRoot] 持有：转场期间新旧两页同时在场，
 * `pageHolder.SaveableStateProvider` 保证退出中的旧页不会丢状态。
 */
@Composable
fun SettingsScene(
    page: String?,
    states: SettingsPagesState,
    pageHolder: SaveableStateHolder,
    onBack: () -> Unit,
    onOpenPage: (String) -> Unit,
    onOpenTtsEditor: (configId: String?, templateId: String) -> Unit,
    backEnabled: Boolean = true,
    previewEnabled: Boolean = true,
    onSeekedBack: () -> Unit = onBack
) {
    val pageKey = page ?: SettingsPage.Home

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 600.dp
        val transition = rememberSeekableBackTransition(
            target = pageKey,
            backTarget = if (page != null) SettingsPage.Home else null,
            backEnabled = backEnabled && page != null,
            previewEnabled = previewEnabled,
            cancelTotalMillis = if (wide) {
                SETTINGS_TABLET_ENTER_MILLIS
            } else {
                SETTINGS_PHONE_SETTLE_MILLIS
            },
            label = "settingsPage",
            settleSpecFor = {
                if (wide) {
                    tween(SETTINGS_TABLET_ENTER_MILLIS, easing = LinearEasing)
                } else {
                    // 进度按时间线性推进，位移的 spring 交给 ContentTransform；
                    // 这个名义时长只用于手势进度和取消回弹的换算。
                    tween(SETTINGS_PHONE_SETTLE_MILLIS, easing = LinearEasing)
                }
            },
            onBack = { onSeekedBack() }
        )

        transition.AnimatedContent(
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                settingsPageTransform(
                    wide = wide,
                    forward = settingsPageDepth(targetState) >= settingsPageDepth(initialState)
                )
            },
            contentAlignment = Alignment.TopStart
        ) { key ->
            pageHolder.SaveableStateProvider(key) {
                SettingsPageBody(
                    page = key,
                    states = states,
                    onBack = onBack,
                    onOpenPage = onOpenPage,
                    onOpenTtsEditor = onOpenTtsEditor
                )
            }
        }
    }
}

@Composable
private fun SettingsPageBody(
    page: String,
    states: SettingsPagesState,
    onBack: () -> Unit,
    onOpenPage: (String) -> Unit,
    onOpenTtsEditor: (configId: String?, templateId: String) -> Unit
) {
    when (page) {
        SettingsPage.Appearance -> AppearanceScreen(
            onBack = onBack,
            state = states.appearance
        )

        SettingsPage.Backup -> BackupRestoreScreen(
            onBack = onBack,
            state = states.backup
        )

        SettingsPage.Playback -> PlaybackSettingsScreen(
            onBack = onBack,
            state = states.playback
        )

        SettingsPage.Storage -> StorageSettingsScreen(
            onBack = onBack,
            state = states.storage
        )

        SettingsPage.Diagnostics -> DiagnosticsScreen(
            onBack = onBack,
            state = states.diagnostics
        )

        SettingsPage.Rules -> RulePacksScreen(
            onBack = onBack,
            listState = states.rules.list
        )

        SettingsPage.TtsList -> TtsConfigListScreen(
            onBack = onBack,
            listState = states.ttsList.list,
            onEdit = { configId -> onOpenTtsEditor(configId, "") },
            onAdd = { templateId -> onOpenTtsEditor(null, templateId) }
        )

        // 未知路由（例如旧版本存档）一律回首页，不留白屏。
        else -> SettingsScreen(
            onBack = onBack,
            state = states.home,
            onOpenRules = { onOpenPage(SettingsPage.Rules) },
            onOpenTtsConfig = { onOpenPage(SettingsPage.TtsList) },
            onOpenPlayback = { onOpenPage(SettingsPage.Playback) },
            onOpenStorage = { onOpenPage(SettingsPage.Storage) },
            onOpenDiagnostics = { onOpenPage(SettingsPage.Diagnostics) },
            onOpenAppearance = { onOpenPage(SettingsPage.Appearance) },
            onOpenBackup = { onOpenPage(SettingsPage.Backup) }
        )
    }
}

/** 设置首页 = 0，其余子页 = 1。深度变大是前进，变小是返回。 */
private fun settingsPageDepth(page: String): Int =
    if (page == SettingsPage.Home) 0 else 1
