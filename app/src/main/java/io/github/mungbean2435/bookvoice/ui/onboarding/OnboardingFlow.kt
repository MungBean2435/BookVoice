package io.github.mungbean2435.bookvoice.ui.onboarding

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mungbean2435.bookvoice.data.settings.Settings
import io.github.mungbean2435.bookvoice.data.settings.StartupSettings
import io.github.mungbean2435.bookvoice.playback.Speech
import io.github.mungbean2435.bookvoice.ui.AppViewModel
import io.github.mungbean2435.bookvoice.ui.theme.AppearanceSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val STEP_ENTER_MS = 360
private const val STEP_EXIT_MS = 300
private const val STEP_FADE_IN_MS = 280
private const val STEP_FADE_OUT_MS = 220

/**
 * 五步引导。状态都由 [StartupSettings] 和两个设置仓库提供，
 * 这里只负责当前步、草稿与写入失败后的停留/重试。
 */
@Composable
fun OnboardingFlow(
    vm: AppViewModel,
    initialStep: Int
) {
    val startup by StartupSettings.state.collectAsStateWithLifecycle()
    val settings by Settings.state.collectAsStateWithLifecycle()
    val appearance by AppearanceSettings.state.collectAsStateWithLifecycle()
    val books by vm.books.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val current = startup ?: return

    val language = current.language
    val strings = onboardingStrings(language)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var step by rememberSaveable {
        mutableIntStateOf(initialStep.coerceIn(1, StartupSettings.STEP_COUNT))
    }
    var saving by remember { mutableStateOf(false) }
    var showError by remember { mutableStateOf(false) }
    var retryAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    // 每一步的草稿：选择立即尝试落库，失败时保留草稿并停在当前步。
    var languageDraft by rememberSaveable { mutableStateOf(current.language) }
    var shelfDraft by rememberSaveable { mutableStateOf(current.shelfSource) }
    var skipSilenceDraft by rememberSaveable { mutableStateOf(current.skipSilence) }
    var speedDraft by rememberSaveable { mutableStateOf(settings.speed) }
    var sleepDraft by rememberSaveable { mutableStateOf(current.sleepTimerMinutes) }
    var themeDraft by rememberSaveable { mutableStateOf(settings.themeMode) }
    var paletteDraft by rememberSaveable { mutableStateOf(appearance.palette) }
    var dynamicDraft by rememberSaveable { mutableStateOf(appearance.dynamicColor) }
    var glassDraft by rememberSaveable { mutableStateOf(appearance.liquidGlass) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.import(uri)
    }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        if (tree != null) {
            scope.launch {
                val uris = withContext(Dispatchers.IO) {
                    runCatching { localBookUrisInTree(context, tree) }
                        .getOrDefault(emptyList())
                }
                if (uris.isEmpty()) {
                    Toast.makeText(context, strings.shelf.noFiles, Toast.LENGTH_SHORT).show()
                } else {
                    vm.importAll(uris)
                }
            }
        }
    }

    /**
     * 真正写入当前步骤；nextStep 等于当前步时只保存不前进。
     * 只有全部 commit 成功才返回 true。
     */
    suspend fun persistStep(target: Int, nextStep: Int): Boolean = when (target) {
        1 -> StartupSettings.saveStep(nextStep) {
            it.copy(language = languageDraft)
        }

        2 -> StartupSettings.saveStep(nextStep) {
            it.copy(shelfSource = shelfDraft)
        }

        3 -> {
            val speedOk = withContext(Dispatchers.IO) {
                Settings.commit { it.copy(speed = speedDraft) }
            }
            if (!speedOk) {
                false
            } else {
                val stepOk = StartupSettings.saveStep(nextStep) {
                    it.copy(
                        skipSilence = skipSilenceDraft,
                        sleepTimerMinutes = sleepDraft
                    )
                }
                if (stepOk) Speech.get().applySpeed(speedDraft)
                stepOk
            }
        }

        4 -> {
            val themeOk = withContext(Dispatchers.IO) {
                Settings.commit { it.copy(themeMode = themeDraft) }
            }
            val lookOk = if (!themeOk) {
                false
            } else {
                withContext(Dispatchers.IO) {
                    AppearanceSettings.commit {
                        it.copy(
                            palette = paletteDraft,
                            dynamicColor = dynamicDraft,
                            liquidGlass = glassDraft
                        )
                    }
                }
            }
            if (!lookOk) false else StartupSettings.saveStep(nextStep) { it }
        }

        else -> false
    }

    fun write(
        action: suspend () -> Boolean,
        onSuccess: () -> Unit,
        onRetry: () -> Unit
    ) {
        if (saving) return
        saving = true
        showError = false
        retryAction = null
        scope.launch {
            val ok = runCatching { action() }.getOrDefault(false)
            saving = false
            if (ok) {
                showError = false
                retryAction = null
                onSuccess()
            } else {
                showError = true
                retryAction = onRetry
            }
        }
    }

    fun saveCurrent() {
        if (step >= StartupSettings.STEP_COUNT) return
        val target = step
        write(
            action = { persistStep(target, target) },
            onSuccess = {},
            onRetry = { saveCurrent() }
        )
    }

    fun goNext() {
        if (saving) return
        if (step >= StartupSettings.STEP_COUNT) {
            // 最后一步只写 onboardingCompleted = true。
            write(
                action = { StartupSettings.completeOnboarding() },
                onSuccess = {},
                onRetry = { goNext() }
            )
            return
        }
        val target = step
        val next = step + 1
        write(
            action = { persistStep(target, next) },
            onSuccess = { step = next },
            onRetry = { goNext() }
        )
    }

    fun goBack() {
        if (saving || step <= 1) return
        val target = step - 1
        step = target
        showError = false
        retryAction = null
        // 恢复进度只是锦上添花；落库失败也不该把用户按在当前页。
        scope.launch { StartupSettings.saveStep(target) { it } }
    }

    // 保存中吞掉系统返回，避免写了一半离开；第一步不拦，按系统默认退出。
    BackHandler(enabled = saving || step > 1) {
        if (!saving) goBack()
    }

    val title = when (step) {
        1 -> strings.language.title
        2 -> strings.shelf.title
        3 -> strings.playback.title
        4 -> strings.appearance.title
        else -> strings.guide.title
    }
    val subtitle = when (step) {
        1 -> strings.language.subtitle
        2 -> strings.shelf.subtitle
        3 -> strings.playback.subtitle
        4 -> strings.appearance.subtitle
        else -> strings.guide.subtitle
    }

    OnboardingLayout(
        header = {
            OnboardingHeader(
                badge = strings.common.badge,
                title = title,
                subtitle = subtitle,
                step = step,
                total = StartupSettings.STEP_COUNT,
                strings = strings
            )
        },
        error = {
            AnimatedVisibility(visible = showError) {
                OnboardingErrorBanner(
                    message = strings.common.writeFailed,
                    retryLabel = strings.common.retry,
                    onRetry = { retryAction?.invoke() }
                )
            }
        },
        buttons = {
            OnboardingButtons(
                step = step,
                total = StartupSettings.STEP_COUNT,
                strings = strings,
                saving = saving,
                onBack = { goBack() },
                onNext = { goNext() }
            )
        },
        card = {
            AnimatedContent(
                targetState = step,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = {
                    val forward = targetState > initialState
                    val enter = slideInHorizontally(
                        animationSpec = tween(durationMillis = STEP_ENTER_MS),
                        initialOffsetX = { width ->
                            if (forward) width / 5 else -width / 5
                        }
                    ) + fadeIn(animationSpec = tween(durationMillis = STEP_FADE_IN_MS))
                    val exit = slideOutHorizontally(
                        animationSpec = tween(durationMillis = STEP_EXIT_MS),
                        targetOffsetX = { width ->
                            if (forward) -width / 5 else width / 5
                        }
                    ) + fadeOut(animationSpec = tween(durationMillis = STEP_FADE_OUT_MS))
                    enter togetherWith exit
                },
                label = "onboarding-step"
            ) { targetStep ->
                when (targetStep) {
                    1 -> LanguageStepContent(
                        strings = strings,
                        selected = languageDraft,
                        enabled = !saving,
                        onSelect = { next ->
                            languageDraft = next
                            saveCurrent()
                        }
                    )

                    2 -> ShelfStepContent(
                        strings = strings,
                        selected = shelfDraft,
                        importedCount = books.size,
                        busy = busy,
                        enabled = !saving,
                        onSelect = { next ->
                            shelfDraft = next
                            saveCurrent()
                        },
                        onPickFile = { picker.launch(arrayOf("*/*")) },
                        onPickFolder = { folderPicker.launch(null) }
                    )

                    3 -> PlaybackStepContent(
                        strings = strings,
                        skipSilence = skipSilenceDraft,
                        speed = speedDraft,
                        sleepMinutes = sleepDraft,
                        enabled = !saving,
                        onSkipSilenceChange = { next ->
                            skipSilenceDraft = next
                            saveCurrent()
                        },
                        onSpeedChange = { next ->
                            speedDraft = next
                            saveCurrent()
                        },
                        onSleepChange = { next ->
                            sleepDraft = next
                            saveCurrent()
                        }
                    )

                    4 -> AppearanceStepContent(
                        strings = strings,
                        language = language,
                        themeMode = themeDraft,
                        palette = paletteDraft,
                        dynamicColor = dynamicDraft,
                        liquidGlass = glassDraft,
                        enabled = !saving,
                        onThemeModeChange = { next ->
                            themeDraft = next
                            saveCurrent()
                        },
                        onPaletteChange = { next ->
                            // 选一个具体主题色时关掉动态取色，否则点了没反应。
                            paletteDraft = next
                            dynamicDraft = false
                            saveCurrent()
                        },
                        onDynamicColorChange = { next ->
                            dynamicDraft = next
                            saveCurrent()
                        },
                        onLiquidGlassChange = { next ->
                            glassDraft = next
                            saveCurrent()
                        }
                    )

                    else -> GuideStepContent(strings = strings)
                }
            }
        }
    )
}
