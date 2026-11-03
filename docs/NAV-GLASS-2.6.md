# 浮层胶囊与液态玻璃（2.6.0）

> 工程：`bookvoice`；minSdk 26 / compileSdk 35；Compose BOM 2024.10.01（ui 1.7.5）；
> Material3。未新增依赖。

## 1. 结构

底部导航从「贴底一条」改成浮层胶囊，三个 tab：**书架 / 书签 / 设置**。

> 2.7 起选中态不再由这里的静态药丸绘制，改为可拖拽镜头，见 [TAB-LENS-2.7.md](TAB-LENS-2.7.md)；本文只保留玻璃三档与降级策略。

- 胶囊：高 64dp、左右 16dp、底部 16dp + `navigationBars` inset、50% 圆角、18dp 阴影；
  0.5dp 描边（暗色 10% 白 / 亮色 70% 白）。
- 选中态：tab 背后一枚内缩 4dp 的小胶囊，260ms 平移（`FastOutSlowInEasing`）。
- 点按：`HapticFeedbackType.TextHandleMove`；`indication = null`，没有 ripple。
- 标签：10.5sp；选中 `primary`，未选中 `onSurface` 72%。
- 内容一直画到屏幕底部，胶囊浮在内容上；列表用 `LocalFloatingChromeReserve`
  加 `contentPadding.bottom`，最后一项仍能滑到胶囊上方。
- 播放页仍是全屏场景（不显示胶囊）；mini player 仍可回播放页。书签 tab 取代了原来的
  「播放」tab 位置，「播放」不再是 tab，但仍由书架 / mini player / 书签进入。

三个 tab 的 `LazyGridState` / `LazyListState` / `TopAppBarState` 都在 `AppRoot`
的 `AppSceneStates` 里，切走由 `SaveableStateProvider` 保存，切回恢复；不重新从顶部开始。

## 2. 三档

| 档 | 条件 | 底色 | 效果 |
| --- | --- | --- | --- |
| 1 Solid | API < 31 | `surfaceContainerLow`；有自定义背景图时 alpha 0.62 | 纯色 + 0.5dp 描边 |
| 2 Blur | API 31+，默认；开关关 / 不可用 | tint 0.75 | 页面快照 28dp 模糊 |
| 3 Liquid | API 33+，开关开且设备可用 | 暗 0.48 / 亮 0.56 | 档 2 + 边缘折射 |

档 3 参数：`refraction 0.42`、`edgeBand 14dp`、`rim 1.6dp`、`rimHighlight 0.16`、
边缘回调 0.07；只让边缘窄带参与 `(1-depth)²`，中间保持普通毛玻璃。

## 3. 实时背板（不是壁纸）

`NavGlassBackdrop` 由 `AppRoot` 持有一枚 `GraphicsLayer`：

1. 页面容器 `drawWithContent` 里 `layer.record { drawContent() }`，然后原样
   `drawLayer(layer)`；
2. 胶囊通过 `onGloballyPositioned` 拿到自己在页面坐标系里的位置；
3. 胶囊内的 Canvas 平移到对应位置，`drawLayer` 同一枚页面图层；
4. 这层截图再挂 `graphicsLayer.renderEffect`：档 2 是 `createBlurEffect(28dp)`，
   档 3 是 `createChainEffect(折射着色器, 模糊)`。

所以滚动列表、翻页、主题切换时，胶囊背后是当下内容；不是一张静态壁纸。

**缓存**：`RenderEffect` 只以 `tier / 宽 / 高 / 圆角 / 模糊半径 / density` 为 key
`remember`；滚动与 tab 切换不会重建。`GraphicsLayer` 也在 `AppRoot` 长期持有，
切 tab 不重新分配。

## 4. 开关与降级

「设置 → 界面风格 → 效果 → 液态玻璃」，默认关，存 `tingshu_appearance` 的
`liquidGlass`。

可用条件（任一不满足就置灰并写明原因）：

- SDK >= 33；
- `PowerManager.isPowerSaveMode == false`（监听省电模式广播，实时恢复/置灰）；
- `ActivityManager.isLowRamDevice == false`。

渲染失败：`RuntimeShader` / `RenderEffect` 创建包在 `runCatching`，失败返回 null，
自动把开关关掉并回落档 2（不崩，Toast 说明一次）。

掉帧：档 3 时监听窗口 `FrameMetrics`（不主动请求帧）；跳过 30 帧预热后，
连续 120 帧里超过 30 帧慢于 21ms 视为掉帧，同样回落档 2。

## 5. 文件

| 文件 | 作用 |
| --- | --- |
| `ui/glass/LiquidGlassPolicy.kt` | 档位 / 可用性 / tint / 折射权重的纯函数 |
| `ui/glass/LiquidGlassAvailability.kt` | SDK、省电、低内存条件与广播监听 |
| `ui/glass/NavGlassBackdrop.kt` | 页面快照图层与坐标 |
| `ui/glass/NavGlassEffect.kt` | 28dp blur、RuntimeShader 折射、缓存、失败回调 |
| `ui/glass/LiquidGlassFrameWatcher.kt` | FrameMetrics 掉帧看门狗 |
| `ui/components/TingshuTabBar.kt` | 浮层胶囊、药丸、haptic、无 ripple |
| `ui/components/FloatingChrome.kt` | `LocalFloatingChromeReserve` |
| `ui/bookmarks/BookmarksScreen.kt` | 书签 tab |
| `data/db/Db.kt` / `Library.kt` / `Models.kt` | `allBookmarks()` 跨书查询 + `BookmarkEntry` |
| `ui/settings/AppearanceScreen.kt` | 液态玻璃开关与原因 |
| `ui/theme/AppearanceSettings.kt` | 持久化 `liquidGlass` |
| `ui/AppRoot.kt` | 三 tab 宿主、内容延伸到胶囊下、快照与档位 |

## 6. 验收对照

| 验收 | 实现 |
| --- | --- |
| 关开关 = 档 2 | `resolveGlassTier`：API 31+ / 开关关 → Blur |
| 不支持 = 灰 + 原因 | 开关 `enabled = availability.available`；副标题/说明写原因 |
| 滚动时底部跟着内容虚化 | 胶囊采样页面 `GraphicsLayer`，不是壁纸 |
| 手势条不压 tab | `navigationBarsPadding` + 底部 16dp；胶囊高 64dp，点击区 >= 48dp |
| 切 20 次不掉帧/不涨内存 | 背板与 effect 缓存，无每帧/每次切换分配；切 tab 只换场景 |

## 7. 边界

- 未新增依赖；没有 `material-icons-extended`，书签图标用本地 `ImageVector` path。
- 未改播放内核、数据库结构、TTS 协议；只新增跨书书签查询。
- 参考项目是 GPL-3.0：这里只按行为与数值重写，没有复制其源码、资源或着色器文本。
- 本机无法通过 adb 安装验证（设备上 `pm` 以应用 UID 运行、截图缺 vendor 库），
  已通过 `compileDebugKotlin` 与 `testDebugUnitTest`；真机视觉/掉帧验收需装
  `dist/听书-2.6.0.apk` 后确认。
