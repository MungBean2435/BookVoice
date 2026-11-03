# NeriPlayer UI 源码学习笔记

## 1 阅读范围

cwuom/NeriPlayer master 快照，路径相对 `moe/ouom/neriplayer/ui`。theme/ 七个文件全读；页面、组件、设置页按原文节选，行号即出处。

- theme/ 七件：NeriTheme、NeriDesign、CleanTheme、ContinuousRoundedCornerShape、CleanPressIndication、NeriShapeCompat、SystemThemeState
- 页面：HomeScreen.kt、NowPlayingScreen.kt
- 复杂组件：NowPlayingQueueSheet.kt
- 设置：SettingsScreen.kt、page/MiuixSettingsScaffold.kt、component/SettingsCommon.kt、navigation/SettingsPageTransition.kt、miuix/MiuixSettingsControls.kt、appearance/SettingsUiStyleSelector.kt、theme/reveal/AppThemeRevealCapture.kt
- 备份：component/backup/SettingsBackupRestoreSection.kt、backup/SettingsBackupTransferController.kt、backup/SettingsConfigProtectionDialogs.kt、viewmodel/BackupRestoreViewModel.kt、viewmodel/ConfigTransferViewModel.kt
- 浮层：component/playback/NeriMiniPlayer.kt、component/navigation/CleanFloatingTabBar.kt
- 玻璃：ui/effect/glass/ 全包 + assets/shaders/advanced_glass_region_mask.agsl（底部深挖见 docs/liquid-glass-notes.md）
- 引导：ui/onboarding/ + core/startup/（深挖见 docs/onboarding-notes.md）
- 旁证：NeriApp.kt、HomeScrollKeys.kt、NeteaseArtistDetailScreen.kt

## 2 页面分层

ui/theme 出配色/字体/形状/令牌；ui/component 放跨页 chrome；ui/screen/<feature> 放页面与私有子组件；ui/viewmodel 按 tab 建 VM。两种写法并存：`NeteaseArtistDetailScreen.kt:96` 建 VM 后交 `:166` 的纯数据 Content；HomeScreen、NowPlayingScreen 则单文件直读 VM / PlayerManager flow。设置页再拆一层：`MiuixSettingsScaffold.kt` 给 home/detail 两套 Scaffold，页面只提供列表内容与标题槽；换页动画在 `navigation/SettingsPageTransition.kt`；备份页把 SAF 启动器、密码/确认对话框都收进 controller，页面只读 state。

## 3 组件写法惯例

| 模式 | 证据 | 解决什么 | 前提 |
|---|---|---|---|
| 令牌对象 + CompositionLocal | ① NeriDesign.kt:90-94 `NeriDesign.tokens` | 免逐层传形状/间距 | 令牌 @Immutable，两套默认 |
| 纯函数出布局 | ② NowPlayingScreen.kt:288-290 `resolvePlaybackActionToolbarLayout` | 尺寸算法可单测 | 调用点传 maxWidth |
| 乐观重排 owner | ③ NowPlayingQueueSheet.kt:349-351 `NowPlayingQueueReorderOwner` | 外部推送不打断拖动[推断] | isDirty + commit，失败回滚 |
| 折叠头部共享 Scaffold | ④ MiuixSettingsScaffold.kt:140-145 `MiuixSettingsHomeScaffold`；`:434` `MiuixSettingsDetailScaffold` | 首页与子页同一套收起行为 | 每页各持一份 TopAppBarState（SettingsNavigationState.kt:253） |
| 子页转场挂 AnimatedContent | ⑤ SettingsPageTransition.kt:42-47、:158 `PhoneSettingsPageTransitionHost` | 换页不硬切，返回手势可按进度拖动 | 页面状态提到宿主层，手势要能 seek |
| 备份页按卡切片 | ⑥ SettingsBackupRestoreSection.kt:1455-1459 `CleanBackupActionTile`；SettingsBackupTransferController.kt:208 `settingsBackupPageItems` | 同一数据出 Clean 磁贴与 Material ListItem 两形态 | 每卡按 cardIndex 懒建 VM（github/webdav 用到才建） |

① NeriDesign.kt:90-94
```kotlin
object NeriDesign {
    val tokens: NeriDesignTokens
        @Composable
        @ReadOnlyComposable
        get() = LocalNeriDesign.current
```
② NowPlayingScreen.kt:288-290
```kotlin
internal fun resolvePlaybackActionToolbarLayout(
    availableWidth: Dp,
    preferredHorizontalPadding: Dp,
```
③ NowPlayingQueueSheet.kt:349-351
```kotlin
internal class NowPlayingQueueReorderOwner(sourceEntries: List<NowPlayingQueueEntry>) {
    val entries = mutableStateListOf<NowPlayingQueueEntry>().apply { addAll(sourceEntries) }
    var isDirty by mutableStateOf(false)
```
④ MiuixSettingsScaffold.kt:140-145
```kotlin
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        state = topAppBarState,
        // follow the finger and stay put; a release snap reads as an unwanted bounce
        snapAnimationSpec = null,
        canScroll = {
            shouldAllowCollapsingTopAppBar(
```
⑤ SettingsPageTransition.kt:42-47
```kotlin
internal const val SETTINGS_PAGE_ENTER_DURATION_MS = 300
// 旧页稍慢退场，让新页接上时画面和模糊遮罩保持连续
internal const val SETTINGS_PAGE_EXIT_DURATION_MS = 160
private const val SETTINGS_PAGE_ENTER_SCALE = 0.98f
private const val SETTINGS_PAGE_EXIT_SCALE = 0.99f
private val SettingsPageEnterEasing = CubicBezierEasing(0f, 0f, 0f, 1f)
```

⑥ SettingsBackupRestoreSection.kt:1455-1459
```kotlin
            .heightIn(min = 76.dp)
            .clip(BackupActionShape)
            .background(colors.onSurface.copy(alpha = 0.06f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
```

## 4 设计决策与落地方式

| 决策（数值 + 落地） | 证据 | 解决什么 | 前提 |
|---|---|---|---|
| 拐角延长 1.1、控制柄 0.643；Clean 形状 14/16/18/24/30dp，自绘 createOutline | ⑦ ContinuousRoundedCornerShape.kt:25-28 `CONTINUOUS_CORNER_CONTROL`、:58 `createOutline`；CleanTheme.kt:150-156 `CleanShapes` | 连续曲率，非正圆弧 | API<30 或 ≥8192px 回落 Rounded |
| 中性面 tint 0.01/0.016；选中容器 0.12/0.20；onSelection 0.12 | ⑧ CleanTheme.kt:72-84 `cleanColorScheme` | 强调色只落交互件 | 自建 CleanNeutrals，surfaceTint 透明 |
| 每边内缩 3dp、上限 6%、高亮 7%，draw 阶段缩放 | ⑨ CleanPressIndication.kt:35-37 `PressInsetDp`、:70 `draw` | 按压不触发重组/重排 | 经 LocalIndication 全局提供（NeriTheme.kt:137） |
| 主题钮 36dp 圆、图标 620ms 交叉旋转 ±56°；圆形揭示 640/560ms | ⑩ SettingsCommon.kt:257 `size(36.dp)`、:275-281 `ThemeModeActionButton`；AppThemeRevealCapture.kt:29-30 | 以按钮圆心扩散换肤，不闪屏 | 需窗口坐标与 PixelCopy 快照（4096px/18MB 上限） |
| 胶囊：mini player 高 64dp、左右 16dp、下 6dp、22dp 连续圆角、0.5dp/8% 描边；tab bar 高 64dp、边距 16/16dp | ⑪ NeriMiniPlayer.kt:875-878 `NeriMiniPlayer`、:886 描边；CleanFloatingTabBar.kt:68-71；NeriDesign.kt:73 `miniPlayerShape` | 浮层与 tab bar 齐边，不贴死屏幕边 | 形状取 tokens.miniPlayerShape；有自定义背景时降到 0.72 透明 |
| 分段条 48dp 轨道 + 4dp 内距 + 360ms 指示块；风格卡 scale 0.97→1、描边 1→2.5dp；色彩风格＝materialkolor 六个 PaletteStyle | ⑫ MiuixSettingsControls.kt:608-611 `MiuixSettingsSegmentedTabs`；SettingsUiStyleSelector.kt:118-126；SettingsThemeControls.kt:247-252 | 选项多但选中态一眼可辨 | 存字符串要 ThemeDefaults 归一，未知值回落 |
| 备份走 SAF：导出 `CreateDocument("application/json")`、导入 `OpenDocument()`；配置导出可含登录态/加密（密码 8–1024 位），导入要过密码与存储确认 | ⑬ SettingsBackupTransferController.kt:141-148；SettingsConfigProtectionDialogs.kt:48 | 不申请存储权限也能读写；敏感配置有明口 | 改语言/主题等需 Activity recreate；密码用 CharArray 传 |

⑦ ContinuousRoundedCornerShape.kt:25-28
```kotlin
internal const val CONTINUOUS_CORNER_EXTENSION = 1.1f

/** Cubic handle ratio of the miuix squircle curve. */
private const val CONTINUOUS_CORNER_CONTROL = 0.643f
```
⑧ CleanTheme.kt:77-82
```kotlin
    val tintAmount = if (isDark) CleanNeutralTintDark else CleanNeutralTintLight
    val tint = accent.primary
    fun neutral(color: Color) = lerp(color, tint, tintAmount)

    val containerLow = neutral(neutrals.containerLow)
    val selectionContainer = lerp(containerLow, accent.primary, if (isDark) 0.20f else 0.12f)
```
⑨ CleanPressIndication.kt:35-37
```kotlin
private const val PressInsetDp = 3f
private const val MaxPressShrink = 0.06f
private const val PressHighlightAlpha = 0.07f
```
⑩ SettingsCommon.kt:275-281
```kotlin
                .onGloballyPositioned { coordinates ->
                    revealStartRadiusPx = maxOf(coordinates.size.width, coordinates.size.height) / 2f
                    centerInWindow = coordinates.positionInWindow() + Offset(
                        x = coordinates.size.width / 2f,
                        y = coordinates.size.height / 2f
                    )
                }
```
⑪ NeriMiniPlayer.kt:875-878
```kotlin
            .height(if (useTabletControls) NeriMiniPlayerDefaults.TabletHeight else NeriMiniPlayerDefaults.Height)
            // aligned with the floating tab bar's side margins, with a small gap above it
            .padding(horizontal = if (useTabletControls) 0.dp else if (cleanStyle) 16.dp else 8.dp)
            .padding(bottom = if (cleanStyle && !useTabletControls) 6.dp else 0.dp)
```
⑫ MiuixSettingsControls.kt:608-611
```kotlin
            .clip(MiuixControlShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .height(48.dp)
            .padding(4.dp)
```

⑬ SettingsBackupTransferController.kt:141-148
```kotlin
    val exportPlaylist = rememberLauncherForActivityResult(
        contract = CreateDocument("application/json"),
        onResult = handler::onExportPlaylist
    )
    val importPlaylist = rememberLauncherForActivityResult(
        contract = OpenDocument(),
        onResult = handler::onImportPlaylist
    )
```

## 5 可复用写法清单

- 令牌集中成 @Immutable data class，用 `staticCompositionLocalOf` 下发（NeriDesign.kt:85）。
- 形状/颜色收成 `@Composable @ReadOnlyComposable` 函数，调用点不写 `if (isClean)`（NeriShapeCompat.kt:32）。
- 折叠头部用 M3 LargeTopAppBar 而非自绘：title 槽会组合两次，用 `LocalTextStyle.fontSize > 24f` 分辨展开/收起槽（MiuixSettingsScaffold.kt:167）。
- 转场时把非活动页 `clearAndSetSemantics` 隐藏并对指针 consume，防误触（SettingsPageTransition.kt:66-76）。
- 状态卡用 AnimatedVisibility 从顶部滑入 300ms、退 250ms；进行中用 `ListItem` + 20dp/2dp 圆环（SettingsBackupRestoreSection.kt:549-558）。
- 玻璃浮层只画 tint + 1dp 边，模糊交给整页快照的 RenderEffect + 区域遮罩；底部两件同时采样背景与内容。
- 先量文字与操作区、图吃剩余高度用 Layout，不用 weight 猜（NowPlayingScreen.kt:428）。
- 手机两列靠自定义 GridCells 兜底（HomeScrollKeys.kt:61）；激活图标色按目标色算，过渡走动画值（NeriTheme.kt:62）。
- 页面状态提到共享宿主并 `rememberSaveable`；tab 场景用 `SaveableStateProvider` 包住，切走再回来子页与滚动都在（MainTabLayerHost.kt:397、NeriApp.kt:1301-1307）。
