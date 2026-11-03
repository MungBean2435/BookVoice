# 可拖拽镜头、边缘折射与镜头颜色（2.7 / 2.8）

> 工程：`bookvoice`；minSdk 26 / compileSdk 35；Compose BOM 2024.10.01（ui 1.7.5）。
> 未新增依赖。NeriPlayer 是 GPL-3.0：这里只按行为、数值和数学描述重写，没有复制其源码或 shader 文本。

## 1. 目标

2.6 的选中态是静态胶囊：只能按等分位置平移，没有按下态、不能跟手、图标不放大。
2.7 把它换成 NeriPlayer 式的「镜头」：

- 按住 tab bar 立即涨起，拖动跟手，镜头下方 tab 被放大并染成 primary；
- 经过别的 tab 给一次 haptic，松手弹到最近 tab 并切换；
- 普通轻点仍走 tab 自己的 `clickable`，不误触发拖动；
- 液态玻璃开启且 API 33+ 时，镜头边缘带折射；不支持时只少折射，其余照常。

## 2. 文件

| 文件 | 作用 |
| --- | --- |
| `ui/components/TabBarLensState.kt` | bar 本地几何、镜头 `Animatable`、手势状态机、橡皮筋 |
| `ui/components/TabBarLensMath.kt` | 按下缩放、放大倍率、squash、镜头可视矩形（纯函数） |
| `ui/components/TabBarLens.kt` | 镜头 overlay：底色、放大内容、SrcIn 染色、阴影/内阴影/高光 |
| `ui/components/TabLensRefraction.kt` | API 33+ RuntimeShader 边缘折射，失败返回 null |
| `ui/components/TingshuTabBar.kt` | 录制 bar 内容、ClipOp.Difference 抠洞、把镜头叠在最上层 |
| `ui/theme/GlassTint.kt` | 玻璃色解析、`#RRGGBB` 格式化、跟随主题/固定色 |
| `ui/settings/AppearanceScreen.kt` | 「镜头颜色」入口、预设 + 自定义对话框 |
| `ui/theme/AppearanceSettings.kt` | 持久化 `glassTint` / `glassTintFollowTheme` |
| `app/src/test/.../TabBarLensMathTest.kt` | 10 条几何/上限测试 |
| `app/src/test/.../GlassTintTest.kt` | 9 条颜色解析/格式化/对比度测试 |

## 3. 第一阶段：几何与手势

**注册坐标**：每个 tab 在 `onGloballyPositioned` 里 `lens.registerTab(index, it)`；
`TabBarLensState.bounds(index)` 用 `bar.localBoundingBoxOf(tab, clipBounds = false)` 取
bar 本地矩形，上层平移/缩放不影响。

**镜头中心**：`Animatable(Float.NaN)`。首次布局 `snapTo` 选中 tab 中心；点选、布局变化时
`animateTo`（260ms）；拖动时每 move `snapTo`，跟手不插值。

**手势状态机**（`awaitEachGesture`，全部在 `PointerEventPass.Initial`）：

1. `awaitFirstDown(requireUnconsumed = false)`；down 立即 `press.animateTo(1f)`，不等长按。
2. 横向位移 `> touchSlop` 且横向大于纵向才进入拖动；进入后 `change.consume()`，
   子 tab 的 tap 手势因为消费而取消。
3. 横向位置 `rubberBandXIn`：限制在首尾 tab 中心之间，越界用
   `limit = barWidth * 0.06` 的反比例阻尼，最多拉出 6%。
4. 每个 move `VelocityTracker.addPosition`，速度写入 `velocity`，180ms 内衰减到 0。
5. 跨过 tab 中心（最近 tab index 变化）`TextHandleMove` haptic 一次。
6. 松手：按最后位置找最近 tab，`markSettled(index)` + `beginSettle()` 后
   `spring(0.62, MediumLow)` 弹到中心；换 tab 时 `onSelect(index)`。按压进度同时回 0。

**轻点**：down 不消费，move 未超过 slop 也不消费，up 交给 tab 的 `clickable`；
所以「点哪个切哪个」还是原来的业务回调，不去改 `AppRoot.selectTab`。

## 4. 第二阶段：镜头外观

**录制 + 抠洞**：tab 行被包在 `drawWithContent` 里：

```kotlin
barContentLayer.record { drawContent() }
// 镜头 rect 用 bar 本地坐标算，和下面 overlay 完全同一套
clipPath(path, ClipOp.Difference) { drawLayer(barContentLayer) }
```

bar 本身在 2.6 是 `clip(shape)`，2.7 去掉；背景层单独裁成胶囊，内容层只抠镜头区域，
镜头 overlay 是 bar 的兄弟节点，所以可以溢出 bar（最大约 5.5dp 上下）。

**镜头尺寸**：静止宽 = 实测 tab 宽 - 8dp（左右各内缩 4dp），高 = bar 高 - 8dp。
布局尺寸固定，涨起/压扁/位移全走 `graphicsLayer`，不触发重新测量：

- `scale = 1 + 0.34 * press`；
- 内部内容 `magnification = 1 + 0.2 * press`，以镜头中心为轴；
- 因为外层也缩放，内容变换用 `magnification / scale` 抵消，视觉结果就是 1.2×。

**染色**：镜头内容画进 `saveLayer(Rect, tintPaint)`，`ColorFilter.tint(primary, SrcIn)`，
保留 alpha、替换颜色；所以镜头扫过未选中 tab 时，图标/文字会瞬间变 primary。

**阴影 / 内阴影 / 高光**：

- 外层 `shadowElevation = 14dp * press`；
- 内阴影用 8dp `BlurMaskFilter`、15% 黑，`alpha` 随按下进度；
- 顶部 1px 高光用竖直渐变描边，底部留一点反弹光；
- 内阴影的 Paint 与 BlurMaskFilter 只创建一次，按帧只改 color，避免每次拖动都分配。

**Squash**：`normalized = |v| / (width * 12)`，
`stretch = min(normalized * 0.6, 0.14)`，`squash = min(normalized * 0.12, 0.06)`；
松手后速度 spring 回 0，镜头从「拉长」自然回落。

## 5. 第三阶段：边缘折射

仅在 `tier == GlassTier.Liquid` 时启用（已经包含 API 33+、用户开关、非省电、非低内存、
未回落四个条件）。镜头内容 Canvas 的 `graphicsLayer.renderEffect` 挂
`RenderEffect.createRuntimeShaderEffect(shader, "content")`。

shader：

```glsl
dist  = roundedRectSdf(p - center)
depth = clamp(-dist / band, 0, 1)
edge  = (1 - depth)²
normal = normalize(gradient(dist))
sample = p - normal * strength * edge
```

- `strength = 4dp * press`，`band = 12dp`；镜头内容层是静止尺寸、外层再缩放，
  所以 uniform 会按当前 `scale` 折算，视觉上的折射带和位移保持 4dp / 12dp；
- 中间 `depth = 1` → `edge = 0`，完全不偏移，仍然只是放大 + 染色；
- 边缘 `edge = 1`，采样点向内吸，形成一圈折射；
- 额外给 R/B 通道 ±0.04 的微小偏移，避免边缘像贴纸；
- `update()` 只改 uniform，不重建 RenderEffect；只有镜头尺寸变化（旋转/分屏）才重建。

**降级**：`Build.VERSION.SDK_INT < 33`、开关关、shader 编译/创建失败（`runCatching`）
都返回 null；镜头照常涨起、放大、染色、带阴影，只是没有折射。不弹 Toast，不写失败标记
（渲染失败由 2.6 的液态玻璃看门狗负责，镜头层不重复处理）。

## 6. 镜头颜色（2.8）

2.8 把「选中镜头只能是主题色」改成可配置：

- 入口：设置 → 界面风格 → 效果 →「镜头颜色」。
- 默认中性灰 `#8E8E8E`，预设灰 / 银 / 白 / 青 / 琥珀 / 玫瑰，可输入 `#RRGGBB`，
  也可以切回「跟随主题强调色」。
- 底栏玻璃：`lerp(surfaceContainerLow, 所选颜色, 18%)`，再按原档位 alpha 叠上去；
  档 1 / 2 / 3 都生效。
- 镜头：底色使用所选颜色（alpha 按浅色 0.14/0.26、深色 0.20/0.34 随按下变化）；
  镜头内图标/文字、选中 tab 使用同色系高对比色：浅色主题自动压深，深色主题自动提亮。
- 持久化：`tingshu_appearance` 的 `glassTint` / `glassTintFollowTheme`；旧安装没有这两个键时
  默认灰色，不崩。
- 颜色不依赖液态玻璃开关：关掉折射后，灰玻璃、涨起、放大和阴影仍然在。

**选中态怎么区分**：镜头本身有底色、阴影、涨起和字号/字重；灰玻璃下选中态靠镜头形状和抬升，
图标则用同色系深灰/浅灰，不再靠蓝色。想要回主题色，选「跟随主题强调色」。

## 7. 验收对照

| 验收 | 实现 |
| --- | --- |
| 按住拖动：镜头跟手、放大下方图标、经过即切 | `snapTo(pointer)`、1.2× 内容、松手 `nearestIndex` + `onSelect` |
| 松手回弹到最近 tab | `spring(0.62, MediumLow)` + `markSettled` |
| 轻点仍切 tab、不误触发拖动 | 未过 slop 不消费 down/move，up 交给 `clickable` |
| 关液态玻璃仍有涨起+放大+阴影 | 折射与外观解耦，`refractionEnabled` 只控 shader |
| API < 33 不崩 | 折射类只在 `SDK_INT >= 33` 实例化，失败返回 null |
| 连续快速拖动 20 次不掉帧 | 镜头固定布局、只改 graphicsLayer；Paint/BlurMaskFilter 复用；shader 只改 uniform |

## 8. 验证与限制

- 每个阶段完成后执行过 `compileDebugKotlin`；最终 `testDebugUnitTest` 107/107、
  `lintDebug` 0 error、`assembleRelease` 通过。
- 本机是 Android 手机上的 proot 环境，`adb` / `pm` 不能以应用 UID 安装 APK，也没有可用的
  录屏管线，所以 **没有真机拖动录屏**。安装 `听书-2.7.0.apk` 后需要人工确认：
  1. 按住拖动是否跟手、松手落点是否准；
  2. 轻点是否仍只切一次、没有镜头卡住；
  3. 开/关液态玻璃，折射是否只在镜头边缘；
  4. 快速来回拖 20 次是否掉帧。
- 真机若发现镜头内容有 1px 接缝，优先调大 `ClipOp.Difference` 的抠洞 rect 1px，
  不要给 bar 重新加 `clip`。

## 9. 边界

- 没有改 tab 的业务跳转：仍然只调 `onSelect(tab)`，由 `AppRoot.selectTab` 决定场景。
- 没有新增依赖；没有改播放、数据库、TTS。
- 新增外观设置 `glassTint` / `glassTintFollowTheme`，只影响底栏和镜头底色。
- 2.6 的三档玻璃、页面快照、液态开关与回落策略保持不变。
- 摄像头/相册等权限无关。

## 10. 镜头收边（修复按下溢出胶囊）

**现象**：静止镜头高 = `barHeight - 8dp`，按下时外层再乘 1.34，上下各多出约 5.5dp；
首/尾 tab 横向还会多出约 9dp；14dp 的 `graphicsLayer` 外阴影也一起漏到胶囊外。

**修法**（不改 tab 跳转与手势判定）：

- 几何统一到 `TabBarLensMath.lensFrame(centerX, barWidth, barHeight, restW, restH, press, velocity, margin)`：
  先按按下进度和速度算出目标缩放，压到可用区域（`margin` 默认 4dp）后再按 rect 半宽/半高
  把中心 clamp 进 bar。`containLens = false` 时 `margin = -5dp`，只允许约 5dp 溢出。
- 缩放基准是镜头自己的静止宽高（tab rect 四周各内缩 4dp），不再缩放带 padding / 边距的外层容器。
- 高度基准保持 `barHeight - 8dp`。因为四边还要各留 4dp，`containLens = true` 时可用高度正好
  等于静止高度，按下的放大被压回 1×；视觉放大来自镜头内容 1.2×、内容上浮 1.5dp、内阴影与内侧亮边。
- 镜头 layer 用 `LensCapsuleShape` 裁剪（圆角半径 = 高度 / 2）；去掉 14dp `shadowElevation`，
  不再向外投阴影。
- 横向中心仍限制在首尾 tab 中心之间、两端各扣 4dp（`travelRange(insetPx)` + 原有橡皮筋）；
  `lensFrame` 的 rect clamp 是最后一道保险。
- bar 内容层抠洞与镜头 overlay 共用同一个 `lensFrame`；squash 时抠洞圆角按横纵缩放折成椭圆，
  圆角处不会露缝。
- 折射 shader 的采样点 clamp 在镜头画布内，边缘不回采到画布外。

**验证**：`TabBarLensMathTest` 覆盖首/中/尾 tab × 按下 0..1 × 速度极值，断言 rect 始终在各边
4dp 内；`containLens = false` 时断言恰好 5dp 溢出。本机没有 adb / 模拟器，未做真机录屏。
