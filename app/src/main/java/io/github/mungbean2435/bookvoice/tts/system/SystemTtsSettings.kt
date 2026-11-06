package io.github.mungbean2435.bookvoice.tts.system

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings

/**
 * 一键跳到手机的「文字转语音」设置。
 *
 * Android 没有公开的 Settings.ACTION_TTS_SETTINGS 常量，不同 ROM 用两个不同 action：
 * 原生 / 多数 ROM 是 `android.settings.TTS_SETTINGS`，旧版与部分 ROM 是
 * `com.android.settings.TTS_SETTINGS`。这里依次尝试，最后回落到无障碍设置
 * （原生把 TTS 设置放在「无障碍」下面），保证按钮永远有反馈。
 */
object SystemTtsSettings {

    /** 带引擎包名时，部分 ROM 会直接打开该引擎的语音数据页。 */
    private const val EXTRA_ENGINE = "engine"

    private val ACTIONS = listOf(
        "android.settings.TTS_SETTINGS",
        "com.android.settings.TTS_SETTINGS"
    )

    /** 部分 ROM 只保留了显式 Activity，没有对外暴露 action。 */
    private val COMPONENTS = listOf(
        ComponentName(
            "com.android.settings",
            "com.android.settings.Settings\$TextToSpeechSettingsActivity"
        ),
        ComponentName(
            "com.android.settings",
            "com.android.settings.Settings\$TtsSettingsActivity"
        )
    )

    /** @return 是否成功打开了某个设置页；false 时调用方应给出提示。 */
    fun open(context: Context, enginePackage: String? = null): Boolean {
        // 调用方可能还没初始化 TTS（状态还在检测）；直接读系统默认引擎，
        // 尽量让设置页直接定位到「首选引擎」那一项。
        val engine = enginePackage?.takeIf { it.isNotBlank() } ?: runCatching {
            Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.TTS_DEFAULT_SYNTH
            )
        }.getOrNull()
        SystemTtsLog.record(
            "设置",
            "打开系统 TTS 设置，engine=${engine.orEmpty().ifBlank { "<默认>" }}"
        )
        ACTIONS.forEach { action ->
            val intent = Intent(action).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (!engine.isNullOrBlank()) putExtra(EXTRA_ENGINE, engine)
            }
            if (!canResolve(context, intent)) {
                SystemTtsLog.record("设置", "action=$action 不可解析，跳过")
                return@forEach
            }
            if (runCatching { context.startActivity(intent) }.isSuccess) {
                SystemTtsLog.record("设置", "已用 action=$action 打开")
                return true
            }
            SystemTtsLog.record("设置", "action=$action 启动失败")
        }

        COMPONENTS.forEach { component ->
            val intent = Intent().apply {
                this.component = component
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (!engine.isNullOrBlank()) putExtra(EXTRA_ENGINE, engine)
            }
            if (runCatching { context.startActivity(intent) }.isSuccess) {
                SystemTtsLog.record("设置", "已用 ${component.flattenToShortString()} 打开")
                return true
            }
        }

        // 兜底：TTS 设置在「无障碍」里；再不行就打开系统设置首页。
        val fallbacks = listOf(
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
            Intent(Settings.ACTION_SETTINGS)
        )
        fallbacks.forEach { intent ->
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (runCatching { context.startActivity(intent) }.isSuccess) {
                SystemTtsLog.record("设置", "回落到 ${intent.action}")
                return true
            }
        }
        SystemTtsLog.record("设置", "全部入口都打不开")
        return false
    }

    /**
     * 先解析再启动：避免 ActivityNotFoundException 打断跳转序列；
     * 清单里已声明两个 action 的 <queries>，所以这里能查到系统设置。
     */
    private fun canResolve(context: Context, intent: Intent): Boolean = runCatching {
        context.packageManager.resolveActivity(intent, 0) != null
    }.getOrDefault(true)
}
