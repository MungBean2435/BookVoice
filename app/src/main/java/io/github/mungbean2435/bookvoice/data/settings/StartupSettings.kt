package io.github.mungbean2435.bookvoice.data.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Locale

/** 冷启动的四个阶段；任一个持久化布尔值还没读到就停在 Loading。 */
enum class StartupStage {
    Loading,
    Disclaimer,
    Onboarding,
    Main
}

enum class AppLanguage(val tag: String) {
    Zh("zh-CN"),
    En("en");

    companion object {
        fun from(raw: String?): AppLanguage = if (raw == En.name) En else Zh
    }
}

/** 书架来源只记录用户的选择；真正的文件选择走系统 SAF，不申请存储权限。 */
enum class ShelfSource {
    Empty,
    LocalFiles
}

data class StartupState(
    val disclaimerAccepted: Boolean = false,
    val onboardingCompleted: Boolean = false,
    val language: AppLanguage = AppLanguage.Zh,
    val shelfSource: ShelfSource = ShelfSource.Empty,
    val skipSilence: Boolean = false,
    val sleepTimerMinutes: Int = 30,
    /** 1..[StartupSettings.STEP_COUNT]；只用于恢复引导进度。 */
    val onboardingStep: Int = 1,
    val notificationAsked: Boolean = false
)

/**
 * 纯函数，便于单测：null = 两个布尔值还没读完，必须继续 Loading。
 * 顺序固定为 免责 → 引导 → 主界面。
 */
fun startupStage(state: StartupState?): StartupStage = when {
    state == null -> StartupStage.Loading
    !state.disclaimerAccepted -> StartupStage.Disclaimer
    !state.onboardingCompleted -> StartupStage.Onboarding
    else -> StartupStage.Main
}

/**
 * 首启 / 引导状态。与 [Settings]、[AppearanceSettings] 分文件存：
 * 这里的两个布尔值决定启动路由，不能因为外观或 TTS 配置写坏而一起丢。
 *
 * 写入统一走 commit()，返回值代表是否真的落盘。UI 必须等 true 再前进，
 * 失败就留在当前步并提示重试。读取在 IO 线程完成，读完前 state 为 null。
 */
object StartupSettings {

    const val STEP_COUNT = 5

    private const val PREFS = "bookvoice_startup"
    private const val KEY_DISCLAIMER = "disclaimerAccepted"
    private const val KEY_ONBOARDING = "onboardingCompleted"
    private const val KEY_LANGUAGE = "language"
    private const val KEY_SHELF_SOURCE = "shelfSource"
    private const val KEY_SKIP_SILENCE = "skipSilence"
    private const val KEY_SLEEP_MINUTES = "sleepTimerMinutes"
    private const val KEY_STEP = "onboardingStep"
    private const val KEY_NOTIFICATION_ASKED = "notificationAsked"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeLock = Mutex()

    @Volatile
    private var prefs: SharedPreferences? = null

    @Volatile
    private var loading = false

    private val _state = MutableStateFlow<StartupState?>(null)

    /** null 表示两个布尔值都还没读出来。 */
    val state: StateFlow<StartupState?> = _state.asStateFlow()

    private val _loadFailed = MutableStateFlow(false)
    val loadFailed: StateFlow<Boolean> = _loadFailed.asStateFlow()

    fun init(context: Context) {
        if (prefs != null) return
        synchronized(this) {
            if (prefs != null) return
            prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            load()
        }
    }

    fun retryLoad() {
        _loadFailed.value = false
        load()
    }

    private fun load() {
        if (loading) return
        loading = true
        scope.launch {
            try {
                // 第一次读 SharedPreferences 可能碰磁盘；失败不要拿默认值冒充成功。
                repeat(3) { attempt ->
                    val loaded = runCatching {
                        withContext(Dispatchers.IO) { read(requirePrefs()) }
                    }.getOrNull()
                    if (loaded != null) {
                        _state.value = loaded
                        _loadFailed.value = false
                        return@launch
                    }
                    if (attempt < 2) delay(250)
                }
                _loadFailed.value = true
            } finally {
                loading = false
            }
        }
    }

    /** 没存过语言时跟系统走：英文系统先显示英文免责声明和引导。 */
    private fun defaultLanguage(): AppLanguage =
        if (Locale.getDefault().language.equals("en", ignoreCase = true)) {
            AppLanguage.En
        } else {
            AppLanguage.Zh
        }

    private fun read(store: SharedPreferences): StartupState = StartupState(
        disclaimerAccepted = store.getBoolean(KEY_DISCLAIMER, false),
        onboardingCompleted = store.getBoolean(KEY_ONBOARDING, false),
        language = store.getString(KEY_LANGUAGE, null)
            ?.let(AppLanguage::from)
            ?: defaultLanguage(),
        shelfSource = runCatching {
            ShelfSource.valueOf(store.getString(KEY_SHELF_SOURCE, null).orEmpty())
        }.getOrDefault(ShelfSource.Empty),
        skipSilence = store.getBoolean(KEY_SKIP_SILENCE, false),
        sleepTimerMinutes = store.getInt(KEY_SLEEP_MINUTES, 30).coerceIn(0, 120),
        onboardingStep = store.getInt(KEY_STEP, 1).coerceIn(1, STEP_COUNT),
        notificationAsked = store.getBoolean(KEY_NOTIFICATION_ASKED, false)
    )

    /**
     * 保存某一步的改动，并把恢复进度一起写进去。
     * 任何一次 commit 失败都返回 false，调用方不得前进。
     */
    suspend fun saveStep(
        step: Int,
        transform: (StartupState) -> StartupState
    ): Boolean = writeLock.withLock {
        val current = _state.value ?: return@withLock false
        val next = transform(current).copy(
            onboardingStep = step.coerceIn(1, STEP_COUNT)
        )
        val ok = withContext(Dispatchers.IO) {
            requirePrefs().edit().apply {
                putBoolean(KEY_DISCLAIMER, next.disclaimerAccepted)
                putBoolean(KEY_ONBOARDING, next.onboardingCompleted)
                putString(KEY_LANGUAGE, next.language.name)
                putString(KEY_SHELF_SOURCE, next.shelfSource.name)
                putBoolean(KEY_SKIP_SILENCE, next.skipSilence)
                putInt(KEY_SLEEP_MINUTES, next.sleepTimerMinutes.coerceIn(0, 120))
                putInt(KEY_STEP, next.onboardingStep)
                putBoolean(KEY_NOTIFICATION_ASKED, next.notificationAsked)
            }.commit()
        }
        if (ok) _state.value = normalize(next)
        ok
    }

    /** 接受免责声明：只写这一个布尔值。 */
    suspend fun acceptDisclaimer(): Boolean = writeOnly(
        build = { editor -> editor.putBoolean(KEY_DISCLAIMER, true) },
        update = { it.copy(disclaimerAccepted = true) }
    )

    /** 最后一步只写 onboardingCompleted = true，不碰其它偏好。 */
    suspend fun completeOnboarding(): Boolean = writeOnly(
        build = { editor -> editor.putBoolean(KEY_ONBOARDING, true) },
        update = { it.copy(onboardingCompleted = true, onboardingStep = STEP_COUNT) }
    )

    /** 通知权限只问一次；放在引导结束、主界面出现之后调用。 */
    fun markNotificationAsked() {
        if (_state.value?.notificationAsked == true) return
        scope.launch {
            writeOnly(
                build = { editor -> editor.putBoolean(KEY_NOTIFICATION_ASKED, true) },
                update = { it.copy(notificationAsked = true) }
            )
        }
    }

    /**
     * 只写一小块状态。update 在写锁内执行，成功后才发布新状态；
     * 返回 true 才表示真的落盘。
     */
    private suspend fun writeOnly(
        build: (SharedPreferences.Editor) -> Unit,
        update: (StartupState) -> StartupState
    ): Boolean = writeLock.withLock {
        val current = _state.value ?: return@withLock false
        val ok = withContext(Dispatchers.IO) {
            requirePrefs().edit().apply(build).commit()
        }
        if (ok) _state.value = normalize(update(current))
        ok
    }

    private fun requirePrefs(): SharedPreferences =
        prefs ?: error("StartupSettings.init() 还没调用")

    private fun normalize(state: StartupState): StartupState = state.copy(
        sleepTimerMinutes = state.sleepTimerMinutes.coerceIn(0, 120),
        onboardingStep = state.onboardingStep.coerceIn(1, STEP_COUNT)
    )
}
