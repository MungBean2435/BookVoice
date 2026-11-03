# 提示词：重构「听书」阅读界面

把下面整段复制给 AI（Claude Code / Codex / Cursor 均可）。

---

你是 Android Compose 工程师。任务：重构「听书」（包名 `io.github.mungbean2435.bookvoice`）的**正文阅读页**界面与交互。仓库就在当前工作目录。

## 开工前必读

1. `docs/PROJECT.md`（架构与目录）
2. `docs/UI-REFACTOR-2.4.md`、`docs/UI-SETTINGS-2.5.md`、`docs/NAV-STATE.md`（既有界面约定）
3. 代码：`ui/player/PlayerScreen.kt`、`ui/player/PlayerSheets.kt`、`ui/components/FloatingChrome.kt`、`ui/components/MiniPlayer.kt`、`ui/theme/`（`Tokens.kt`、`Theme.kt`、`Type.kt`、`Dimens.kt`、`AppearanceSettings.kt`）、`data/settings/Settings.kt`

读不到就停下来问我要文件，**不要凭印象猜 API**。

## 已核实的现状（以源码为准）

- 构建：Compose BOM `2024.10.01`（Compose 1.7）、Kotlin Compose 插件、minSdk 26 / compileSdk 35 / JVM 17、Gradle 8.9。
- 正文是整章滚动：`PlayerScreen.kt:338` 的 `LazyColumn`，当前朗读段用 `surfaceContainerHigh` 整块卡片包 `FocusParagraph`；点击判定在 `PlayerScreen.kt:344-372`（`awaitEachGesture` 里看 `moved` / `consumed`）。
- 阅读页目前走 `TingshuPageScaffold`（`ui/components/PageScaffold.kt`）的 `LargeTopAppBar`，滚动折叠由 `nestedScroll` 驱动。
- 设置项：`Settings.kt` 有 `fontSizeSp = 17`、`tapToRead`、`themeMode`；阅读设置面板在 `PlayerSheets.kt`。
- 依赖里**没有** `material-icons-extended`；不要为图标新增依赖。
- 主题是自建 `TingshuDesign` + 6 套色板（`AppearanceSettings.kt`），不要新造一套颜色常量。

## 目标

### P0 不可破坏

1. TTS 朗读与段落高亮同步（`currentBlock`）、自动滚动跟随、点段落跳读；
2. 书签、章节跳转、失败段落面板、迷你播放条、进度保存；
3. `fontSizeSp`、`tapToRead`、`themeMode`、液态玻璃开关行为不变；SharedPreferences 键名与默认值不变（老用户升级不能丢设置）。

### P1 交互重构（本次核心）

1. 阅读页的上下栏改成**浮层**，不再用 `LargeTopAppBar`：
   - 点屏幕中部：呼出/收起；
   - 向下滑 > 48dp：收起；向上滑：呼出；
   - 左右边缘各 18% 点击：翻页（滚动模式则滚一屏）；
   - 进场 200–300ms 位移 + 淡入，退场对称。
2. 浮层只盖在正文上，不改变正文可用高度（不要因为显隐导致重新分页/跳动）。
3. 手势互斥：浮层上的按钮点击不能冒泡成"切换浮层"；拖动超过 `touchSlop` 不算点击（沿用现有 `moved` 判断，别新写一套）。

### P2 视觉优化（用现有 token）

1. 正文左右边距、行高、段距收敛到 `TingshuDesign.tokens`，不要散落硬编码；
2. 当前段高亮从整块卡片改成更轻的方案（例如左侧 3dp 竖条 + 低透明度底色），并说明选择理由；
3. 上下浮层与状态栏/导航栏 inset 对齐，刘海与手势条区域留白；深色与 6 套色板逐一检查正文对比度 ≥ 4.5:1。

### P3 可选（时间够再做）

- 长按选词后弹自定义菜单（复制 / 划线 / 搜索 / 朗读 / 分享），只做单词选中；
- 迷你播放条与浮层联动：浮层收起时播放条停靠底部 16dp。

## 硬约束

- 不新增三方依赖；不动 `playback/`、`tts/`、`data/db/`、其他页面；
- 每次改动必须可编译；不重排无关文件、不做全量格式化；
- 颜色只用 `MaterialTheme.colorScheme` 或 `TingshuDesign`，不新增十六进制常量；
- 不写"示意代码"，给完整可粘贴的代码块。

## 工作方式

1. **先给方案再动手**：输出「影响面（文件+行号）/ 方案 / 风险」三项，等我确认；
2. 改完按文件给完整代码 + 一句话变更说明；
3. 自测：`./gradlew :app:assembleDebug`，有单测则 `./gradlew :app:testDebugUnitTest`，贴输出末尾；
4. `docs/CHANGELOG.md` 补一条。

## 验收标准（可逐条核对）

- 冷启动进阅读页：浮层默认隐藏；点中部 ≤ 300ms 出现；再点消失；下滑 48dp 收起；
- 左右边缘点击翻页（或滚动一屏），中部点击不翻页；
- 朗读中高亮与滚动跟随无回归（手跑一遍 TTS：开始、暂停、跳段）；
- 退出重进、旋转屏幕后字号与主题保持；
- 无新增 lint error；APK 体积变化 < 1%。

## 可选参考（来自 readest 的数值，非强制，别照抄结构）

页边距 上下 44dp / 左右 16dp；行高 1.4（中文 1.6）；段距 0.6em（中文 1em）；中文首行缩进 2em；页眉页脚字号 12sp；边缘翻页区屏宽 18%。
