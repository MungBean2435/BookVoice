# 提示词：优化「从阅读页返回书架」+ 底部液态玻璃的进场

复制整段给 AI。仓库：`bookvoice`（包名 `io.github.mungbean2435.bookvoice`）。

---

你是 Android Compose 工程师。任务：修「从阅读 overlay 收起回书架」这一段——现在胶囊和液态玻璃在动画中途消失、结束时硬出现，书架底部留白会跳一截，玻璃重新记录背板还会在进场帧掉帧。只改这一条链路。

## 开工前必读

`ui/AppRoot.kt`、`ui/player/PlayerOverlay.kt`、`ui/glass/{NavGlassBackdrop,NavGlassEffect,LiquidGlassFrameWatcher,LiquidGlassPolicy}.kt`、`ui/components/TingshuTabBar.kt`、`ui/components/FloatingChrome.kt`、`ui/shelf/ShelfScreen.kt`、`ui/nav/NavigationTransitions.kt`。读不到就问我要文件，不要猜。

## 已核实的现状（改之前先对齐，不要推翻）

| 事实 | 位置 |
| --- | --- |
| 收起/展开是同一个进度 0..1，非路由 | `PlayerOverlay.kt:61-64` |
| 展开 280ms / 收起 240ms，FastOutSlowIn | `PlayerOverlay.kt:40-41`、`:93`、`:128` |
| 抓手下滑松手按 50% 定方向 | `PlayerOverlay.kt:120-131`、`:197` |
| 系统返回手势映射到同一进度 | `PlayerOverlay.kt:227-240` |
| overlay 用 `translationY=(1-progress)*height` 移出 | `PlayerOverlay.kt:171` |
| 收起完成才把 `overlayExpanded` 设 false | `AppRoot.kt:206-217` |
| 底栏/迷你条的显隐总开关 | `AppRoot.kt:185-190` |
| 玻璃背板录制只在 `showChrome` 时挂载 | `AppRoot.kt:310-315` |
| 底栏整体 `if (showChrome)` 才组合 | `AppRoot.kt:464` |
| 玻璃掉帧看门狗也挂在 `showChrome` 上 | `AppRoot.kt:191-194` |
| 底部留白 `166dp` / `96dp`，不显示时给 0 | `AppRoot.kt:295`、`:297` |
| 书架把它加进 `contentPadding.bottom = 32.dp + reserve` | `ShelfScreen.kt:185`、`:202` |
| 背板 = 每帧把整页录进 `GraphicsLayer` 再画出来 | `NavGlassBackdrop.kt:41-54` |
| 胶囊 64dp 高、28dp 模糊、4dp 内缩、5dp 溢出 | `TingshuTabBar.kt:124-128`、`:171-172` |
| 三档：Solid / Blur(API31+) / Liquid(API33+) | `LiquidGlassPolicy.kt:12`、`:58` |
| 底色不透明度 0.75 / 暗 0.48 亮 0.56 | `LiquidGlassPolicy.kt:71` |
| 看门狗：跳 30 帧预热，120 帧里 30 帧 >21ms 降档 | `LiquidGlassFrameWatcher.kt:81-84` |

## 已定位的问题

1. **收起途中胶囊消失**：拖动时 `overlay.dragging=true` 所以 `showChrome=true`；松手后 `settleDrag` 先把 `dragging` 置 false（`PlayerOverlay.kt:122`），而 `overlayExpanded` 要等动画结束才变 false（`AppRoot.kt:206-217`）。于是 240ms 收起动画全程 `showChrome=false`，胶囊与玻璃**不画**，动画结束瞬间整块出现——跟手拖时看得见、松手就没。
2. **玻璃背板录制的重新挂载**：`captureNavGlassBackdrop` 随 `showChrome` 摘掉（`AppRoot.kt:310-315`）。返回时它重新挂上，第一帧要重录整页；胶囊若在该帧先画，采样到的是尚未录制的层 [推断]，表现为玻璃底一帧发灰/发白。
3. **书架底部留白跳变**：`reserve` 从 `0.dp` 直接切到 `96/166dp`（`AppRoot.kt:295-297`），书架 `contentPadding`（`ShelfScreen.kt:202`）在胶囊出现那一帧整段跳开，滚到底时可见内容位移 [推断：需真机看一次]。
4. **看门狗错过最卡的帧**：`enabled` 随 `showChrome` 从 false→true，`DisposableEffect` 每次重建 `HandlerThread`（`LiquidGlassFrameWatcher.kt:36-37`），且前 30 帧预热不采样（`:84`）——玻璃进场正好落在预热里。

## 要实现的改动

### P0 显隐改成连续量

5. 把二值 `showChrome` 拆成「是否允许」+「进场进度」：
   - `chromeEligible = !editorOpen && chaptersBookId == null && selectedTab in {Shelf, Bookmarks, Settings}`；
   - `chromePhase = if (chromeEligible) (1f - overlay.progress.coerceIn(0f, 1f)) else 0f`（正在拖动或动画中都会连续变化）。
   - 底栏/迷你条用 `chromePhase` 驱动 `alpha` 与 `translationY = (1f - phase) * 24.dp`，**不要**再包 `AnimatedVisibility`（会与 overlay 的 240ms 各自计时、错位）。
   - `LocalFloatingChromeReserve` 改成 `baseReserve * chromePhase`，`baseReserve` 仍是 mini 166 / 无 mini 96。
6. 背板录制条件改成 `chromeEligible && chromePhase > 0.01f`，让胶囊出现的第一帧就有已录制的层；`chromePhase == 0` 时**必须**停止录制（全展开的阅读页不做这趟额外录制，这是现有性能前提）。
7. 触摸：`chromePhase < 1f` 时底栏不接收点击（`pointerInput` 里判断或 `Modifier.alpha` 之外显式 gate），避免收起动画中点到半透明的 tab。

### P1 看门狗与线程

8. `LiquidGlassFrameWatcher` 的 `enabled` 改成只跟 `glassTier == Liquid`，不再跟 `showChrome`；把「当前是否在显示胶囊」作为第三参数传入，只在显示期统计，另加一个 `resetWarmup` 信号：胶囊从 `phase 0` 变正数时重新跳过预热并清空计数。
9. 减少 `HandlerThread` 生命周期抖动（`LiquidGlassFrameWatcher.kt:34-36`）：用 `remember` 持有线程与 listener，`DisposableEffect` 只注册/注销窗口监听。
10. 不插值模糊半径：`rememberNavGlassEffect` 的 key 保持「档位 + 尺寸 + 半径」（`NavGlassEffect.kt:30`），进场只用 alpha/位移，避免每帧重建 `RuntimeShader`。

### P2 打磨

11. 手势取消回弹：`settleDrag(true)`（`PlayerOverlay.kt:236`）时 `chromePhase` 会跟着回到 0，不要另外再写一套消失动画。
12. 返回完成时 `overlayExpanded=false` 那一帧（`AppRoot.kt:210`）不应触发任何布局重排：`reserve` 已由 `chromePhase` 连续驱动，此时它应已等于 `baseReserve`。
13. 迷你条与胶囊同一 `chromePhase`，不要给迷你条单独延时。

## 硬约束

- 不碰 `playback/`、`tts/`、`data/`；不改 `docs/` 之外的既有文档；
- 不改三档阈值、用户开关语义、tint 数值（`LiquidGlassPolicy.kt`）；降档逻辑在 API 31/33 上必须仍然正确；
- 不新增依赖、不引入新动画库；只用现有 `Animatable`/状态；
- 暂停录制只在 `chromePhase == 0f` 时生效，别为了省事全程开录制；
- 改完可编译；不做全量格式化；
- 给完整可粘贴代码，不要「示意代码」。

## 自查与验收

1. 抓手下滑返回：手指拖动时胶囊随 `progress` 淡入并上移，松手后 240ms 内**连续**，无闪、无消失；
2. 系统返回手势：同上；中途取消回弹时胶囊连续淡出；
3. 返回后书架：`contentPadding.bottom` 不再整段跳（对比返回前后 `LazyVerticalGrid` 首项 `offset` 不变）；
4. API 33 液态玻璃：返回进场首帧的胶囊背后有页面内容（截图/录屏对比，无灰底空帧）；
5. 全展开阅读页停留 10s：`LiquidGlassFrameWatcher` 不统计、背板不录制（可用日志确认 `record` 次数为 0）；
6. `./gradlew :app:assembleDebug` 通过，无新增 lint error；
7. `docs/CHANGELOG.md` 补一条。

## 工作方式

1. 先给「影响面（文件:行号）/ 方案 / 风险」三项，等我确认再改；
2. 改完按文件贴完整代码 + 一句说明；
3. 附第 1、2 条的手测结果（设备 Android 版本、玻璃档位）与 `assembleDebug` 输出末尾。
