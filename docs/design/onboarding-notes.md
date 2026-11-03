# NeriPlayer 初次进入引导笔记

有，而且是**两段**：免责声明 → 9 步引导 → 主界面。
入口不在 Activity 里 if/else，而是一个状态机。

## 1 启动阶段机

`StartupStage`：`Loading / Disclaimer / Onboarding / Main`。
两个持久化标记决定去哪：`disclaimerAccepted`、`startupOnboardingCompleted`。
任一为 `null`（还没读到）就停在 Loading，不会闪一下主界面。

## 2 九步的顺序

`Language → Platforms → PlaybackSources → Permissions → PlaybackControls →
Lyrics → Personalize → BackupRestore → LearningGuide`

- 语言放第一：后面所有步骤的文案立刻跟着变。
- 平台账号放第二：进主界面时首页就有内容。
- 权限放账号之后：先让用户看到能干什么，再要通知/媒体权限[推断]。
- 备份恢复放在个性化之后：用户已经调好外观，此时导入配置不冲突。
- 最后一步是「上手引导」：7 张卡讲清播放页手势、歌词、歌单多选、下载、备份、一起听、队列。

## 3 外壳：双栏还是单栏

`StartupOnboardingLayout` 按宽度分：

- `maxWidth >= 600.dp`：左栏 32%（180–320dp）放标题与按钮，右栏内容卡；
- 手机：单栏，标题 → 进度 → 内容 → 按钮；
- `maxHeight < 480.dp`、`maxWidth < 720.dp` 都算紧凑，内边距从 32 降到 20。

Header 固定四件：胶囊 badge、`headlineSmall` 粗体标题、`bodyLarge` 副标题、
6dp 圆角进度条 + `3/9` 序号。
Actions 固定两枚：返回（weight 1）+ 下一步（weight 1.4）；最后一步按钮文案换成「进入应用」。

## 4 步骤切换不是默认 AnimatedContent

自己写了 `StartupOnboardingLayerHost`：

- 进入 360ms、退出 300ms；淡入 280ms、淡出 220ms；
- 支持反向：返回时按当前进度反演，不会从头再来；
- 每步一个 scene，`key(stepIndex)` 隔离状态；
- 玻璃归属在进度 0.5 处从旧步交接给新步，避免两层同时采样。

## 5 什么会卡住「下一步」

- 权限请求进行中直接锁导航（`permissionNavigationBlocked`）。
- 通知权限跳过 2 次后弹警告（`STARTUP_NOTIFICATION_PERMISSION_WARNING_ATTEMPTS = 2`）。
- 三个平台一个都没连，弹「未连接平台」警告。
- 已导入背景图、增强模糊可用但没开，弹一次增强模糊提示。
- 全局没有「跳过全部」；只有权限相关弹窗给了 skip。

## 6 完成时写什么

最后一步只写两件事：`setUiDensityScale(pendingUiScale)` 和
`setStartupOnboardingCompleted(true)`。失败就把 `finishing` 复位，停在原页。
其余偏好（主题、播放控制、歌词字号）都是每步即时写库，不是最后统一提交。

## 7 摘录

① `StartupOnboardingScreen.kt:150-160`
```kotlin
private enum class StartupStep {
    Language,
    Platforms,
    PlaybackSources,
    Permissions,
    PlaybackControls,
    Lyrics,
    Personalize,
    BackupRestore,
    LearningGuide
}
```
② `StartupOnboardingScreen.kt:165-168`
```kotlin
internal fun calculateStartupOnboardingProgress(stepIndex: Int, stepCount: Int): Float {
    if (stepCount <= 0) return 0f
    return ((stepIndex + 1).toFloat() / stepCount).coerceIn(0f, 1f)
}
```
③ `StartupStageResolver.kt:3-17`
```kotlin
internal object StartupStageResolver {
    fun resolve(
        disclaimerAccepted: Boolean?,
        startupOnboardingCompleted: Boolean?
    ): StartupStage {
        return when (disclaimerAccepted) {
            null -> StartupStage.Loading
            false -> StartupStage.Disclaimer
            true -> when (startupOnboardingCompleted) {
                null -> StartupStage.Loading
                false -> StartupStage.Onboarding
                true -> StartupStage.Main
            }
        }
    }
```
④ `StartupOnboardingScreen.kt:579-588`
```kotlin
    fun finishOnboarding() {
        if (finishing) return
        finishing = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    repo.setUiDensityScale(pendingUiScale)
                    repo.setStartupOnboardingCompleted(true)
                }
            }
```
⑤ `StartupOnboardingLayerHost.kt:34-38`
```kotlin
internal const val STARTUP_ONBOARDING_STEP_ENTER_DURATION_MS = 360
internal const val STARTUP_ONBOARDING_STEP_EXIT_DURATION_MS = 300
internal const val STARTUP_ONBOARDING_STEP_ENTER_FADE_DURATION_MS = 280
internal const val STARTUP_ONBOARDING_STEP_EXIT_FADE_DURATION_MS = 220
internal const val STARTUP_ONBOARDING_GLASS_OWNER_HANDOFF_PROGRESS = 0.5f
```

## 8 如果听书 App 要做一版

- 首启别一页塞满：拆 5–7 步，每步只做一个决定（语言、书架来源、播放偏好、外观、导入书签）。
- 语言与主题放最前，后面的 UI 才不用二次重绘。
- 进度条 + 序号必须有，用户要知道「还有几步」。
- 返回要能反演动画，不要重置进度。
- 每步的改动即时落库，最后只写「已完成」标记；失败就停在当前步。
- 权限别放在第一步，先让人觉得有用再要。
