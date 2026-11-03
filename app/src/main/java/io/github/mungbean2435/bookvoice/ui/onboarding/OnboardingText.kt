package io.github.mungbean2435.bookvoice.ui.onboarding

import androidx.compose.runtime.staticCompositionLocalOf
import io.github.mungbean2435.bookvoice.data.settings.AppLanguage

/** 引导与主界面壳层共用的语言；切换后同一帧内重组。 */
val LocalAppLanguage = staticCompositionLocalOf { AppLanguage.Zh }

internal data class OnboardingStrings(
    val common: Common,
    val disclaimer: Disclaimer,
    val language: LanguageStep,
    val shelf: ShelfStep,
    val playback: PlaybackStep,
    val appearance: AppearanceStep,
    val guide: GuideStep
) {
    data class Common(
        val badge: String,
        val back: String,
        val next: String,
        val start: String,
        val saving: String,
        val writeFailed: String,
        val retry: String,
        val progress: String
    )

    data class Disclaimer(
        val badge: String,
        val title: String,
        val subtitle: String,
        val sections: List<Pair<String, String>>,
        val accept: String,
        val exit: String
    )

    data class LanguageStep(
        val title: String,
        val subtitle: String,
        val hint: String,
        val zh: String,
        val zhDesc: String,
        val en: String,
        val enDesc: String
    )

    data class ShelfStep(
        val title: String,
        val subtitle: String,
        val emptyTitle: String,
        val emptyDesc: String,
        val localTitle: String,
        val localDesc: String,
        val pick: String,
        val pickFolder: String,
        val noFiles: String,
        val importedFormat: String,
        val note: String
    )

    data class PlaybackStep(
        val title: String,
        val subtitle: String,
        val skipTitle: String,
        val skipDesc: String,
        val speedTitle: String,
        val speedDesc: String,
        val sleepTitle: String,
        val sleepDesc: String,
        val sleepOff: String,
        val minutesFormat: String,
        val note: String
    )

    data class AppearanceStep(
        val title: String,
        val subtitle: String,
        val themeTitle: String,
        val followSystem: String,
        val light: String,
        val dark: String,
        val paletteTitle: String,
        val dynamicTitle: String,
        val dynamicDesc: String,
        val glassTitle: String,
        val glassDesc: String,
        val glassUnavailableFormat: String,
        val note: String
    )

    data class GuideStep(
        val title: String,
        val subtitle: String,
        val cards: List<Pair<String, String>>,
        val note: String
    )
}

private val zh = OnboardingStrings(
    common = OnboardingStrings.Common(
        badge = "首次使用",
        back = "返回",
        next = "下一步",
        start = "开始使用",
        saving = "正在保存…",
        writeFailed = "保存失败，请检查存储空间后重试。",
        retry = "重试",
        progress = "%d/%d"
    ),
    disclaimer = OnboardingStrings.Disclaimer(
        badge = "使用前",
        title = "开始之前，先说清楚",
        subtitle = "书只在本机解析，但云端朗读会把当前段落发给你配置的服务商。",
        sections = listOf(
            "本地文件" to "TXT / EPUB 的解析、书架和进度都保存在本机，不会上传到我们的服务器。",
            "云端朗读" to "朗读文本会发送到你选择的 TTS 服务商；API Key 只存在本机私有目录。",
            "内容版权" to "请只导入你有权使用的内容。应用不提供在线书源，也不对内容分发负责。",
            "数据与备份" to "没有账号和云同步；卸载或清除应用数据会删除书架、进度和设置。"
        ),
        accept = "同意并继续",
        exit = "退出"
    ),
    language = OnboardingStrings.LanguageStep(
        title = "选择界面语言",
        subtitle = "后面的引导会立刻使用你选的语言，进入主界面后也会保持。",
        hint = "选择后立即切换，进入主界面后保持。",
        zh = "简体中文",
        zhDesc = "引导、设置与主界面使用中文。",
        en = "English",
        enDesc = "Guide and main shell use English; detail pages may still be Chinese."
    ),
    shelf = OnboardingStrings.ShelfStep(
        title = "建立你的书架",
        subtitle = "可以先空着，也可以现在导入一本 TXT 或 EPUB。",
        emptyTitle = "先空着",
        emptyDesc = "进入书架后再导入，不耽误使用。",
        localTitle = "导入本地文件",
        localDesc = "支持 TXT / EPUB，通过系统文件选择器读取，不需要存储权限。",
        pick = "选择文件",
        pickFolder = "选择文件夹",
        noFiles = "这个文件夹里没有 TXT / EPUB",
        importedFormat = "已导入 %d 本，可以继续选择。",
        note = "文件选择器由系统提供；引导里不会申请存储权限。"
    ),
    playback = OnboardingStrings.PlaybackStep(
        title = "调整播放习惯",
        subtitle = "倍速会写入播放设置；跳过静音和睡眠习惯先作为偏好记住。",
        skipTitle = "跳过静音",
        skipDesc = "先记住偏好，当前播放逻辑保持不变。",
        speedTitle = "默认倍速",
        speedDesc = "写入现有播放设置，下一次播放立即生效。",
        sleepTitle = "睡眠定时习惯",
        sleepDesc = "只记住常用时长，不会自动开始倒计时。",
        sleepOff = "关闭",
        minutesFormat = "%d 分钟",
        note = "倍速会落库到现有播放设置；另外两项作为引导偏好保存。"
    ),
    appearance = OnboardingStrings.AppearanceStep(
        title = "挑一个顺眼的外观",
        subtitle = "深浅色、主题色和液态玻璃会立即生效。",
        themeTitle = "深浅色",
        followSystem = "跟随系统",
        light = "浅色",
        dark = "深色",
        paletteTitle = "主题色",
        dynamicTitle = "动态取色",
        dynamicDesc = "Android 12+ 优先使用壁纸取色；关闭后使用下方主题色。",
        glassTitle = "液态玻璃",
        glassDesc = "底部导航实时模糊；不支持的设备会自动回落，不会崩。",
        glassUnavailableFormat = "当前设备不可用：%s",
        note = "外观改动会立即作用于引导和主界面，无需重启。"
    ),
    guide = OnboardingStrings.GuideStep(
        title = "几招手势，马上上手",
        subtitle = "最后看一眼常用操作，然后开始听书。",
        cards = listOf(
            "点句续听" to "在正文里点任意一句，就从那一句开始朗读。",
            "拖动底部镜头" to "按住底部的选中胶囊左右拖，松手切到最近的 tab。",
            "返回有预览" to "系统返回手势会露出下层页面；设置子页、目录、tab 都会逐级退回。",
            "边走边听" to "播放页可调倍速、定时关闭、缓存本章；锁屏和通知栏也能控制。"
        ),
        note = "开始使用后，如果还没授权通知，系统会询问一次，用于后台播放控制。"
    )
)

private val en = OnboardingStrings(
    common = OnboardingStrings.Common(
        badge = "FIRST RUN",
        back = "Back",
        next = "Next",
        start = "Start",
        saving = "Saving…",
        writeFailed = "Couldn't save. Check storage and try again.",
        retry = "Retry",
        progress = "%d/%d"
    ),
    disclaimer = OnboardingStrings.Disclaimer(
        badge = "BEFORE YOU START",
        title = "A quick word first",
        subtitle = "Books stay on this device, but cloud narration sends the current passage to your chosen provider.",
        sections = listOf(
            "Local files" to "TXT / EPUB parsing, your shelf and progress stay on this device and are never uploaded to us.",
            "Cloud narration" to "Narration text is sent to the TTS provider you configure; API keys stay in private app storage.",
            "Content rights" to "Only import content you have the right to use. The app ships no online book sources.",
            "Data & backup" to "No account, no cloud sync. Uninstalling or clearing app data removes your shelf, progress and settings."
        ),
        accept = "Agree and continue",
        exit = "Exit"
    ),
    language = OnboardingStrings.LanguageStep(
        title = "Choose your language",
        subtitle = "The rest of this guide switches immediately and the choice carries into the app.",
        hint = "Tap to switch now; the choice carries into the app.",
        zh = "简体中文",
        zhDesc = "Guide, settings and main shell use Chinese.",
        en = "English",
        enDesc = "Guide and main shell use English; detail pages may still be Chinese."
    ),
    shelf = OnboardingStrings.ShelfStep(
        title = "Set up your shelf",
        subtitle = "Start empty, or import a TXT or EPUB right now.",
        emptyTitle = "Start empty",
        emptyDesc = "Import later from the shelf whenever you like.",
        localTitle = "Import a local file",
        localDesc = "TXT / EPUB through the system picker. No storage permission needed.",
        pick = "Pick file",
        pickFolder = "Pick folder",
        noFiles = "No TXT or EPUB found in that folder",
        importedFormat = "%d imported. You can pick another.",
        note = "The picker is provided by the system; this guide never asks for storage access."
    ),
    playback = OnboardingStrings.PlaybackStep(
        title = "Tune playback",
        subtitle = "Speed is written to playback settings; skip-silence and sleep habits are remembered as preferences.",
        skipTitle = "Skip silence",
        skipDesc = "Remembers your preference; playback behaviour stays unchanged for now.",
        speedTitle = "Default speed",
        speedDesc = "Saved to the existing playback settings and used on the next play.",
        sleepTitle = "Sleep-timer habit",
        sleepDesc = "Remembers a usual duration; it never starts a timer automatically.",
        sleepOff = "Off",
        minutesFormat = "%d min",
        note = "Speed is stored in the existing playback settings; the other two are saved as guide preferences."
    ),
    appearance = OnboardingStrings.AppearanceStep(
        title = "Make it feel right",
        subtitle = "Light/dark, accent colour and liquid glass apply immediately.",
        themeTitle = "Light / dark",
        followSystem = "System",
        light = "Light",
        dark = "Dark",
        paletteTitle = "Accent colour",
        dynamicTitle = "Dynamic colour",
        dynamicDesc = "Android 12+ uses wallpaper colours; turn off to use the accent below.",
        glassTitle = "Liquid glass",
        glassDesc = "Live blur behind the bottom bar; unsupported devices fall back safely.",
        glassUnavailableFormat = "Unavailable on this device: %s",
        note = "Appearance changes apply to the guide and the app immediately, with no restart."
    ),
    guide = OnboardingStrings.GuideStep(
        title = "A few gestures to know",
        subtitle = "One last look at the everyday controls, then start listening.",
        cards = listOf(
            "Tap a sentence" to "Tap any sentence in the text to start narrating from that line.",
            "Drag the lens" to "Press and drag the selected bottom pill, then release to switch tabs.",
            "Back with a preview" to "System back reveals the page underneath; settings, chapters and tabs unwind one level at a time.",
            "Listen anywhere" to "Speed, sleep timer and chapter caching live on the player; the lock screen and notification work too."
        ),
        note = "After you start, the system may ask once for notification access for background playback."
    )
)

internal fun onboardingStrings(language: AppLanguage): OnboardingStrings =
    if (language == AppLanguage.En) en else zh

internal fun AppLanguage.isEnglish(): Boolean = this == AppLanguage.En
