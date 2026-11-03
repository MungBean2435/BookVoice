# 设置子页过渡 + 预测性返回笔记

两件事其实是一套：**同一个 Transition 既负责点击进入，也负责手势拖动。**

NeriPlayer 里有三层，别混：

1. `SettingsScreen` 内部：设置首页 ↔ 设置子页（`activePage`）。
2. `SettingsHostScreen`：设置 ↔ 下载管理 / 下载进度（`screenState`）。
3. 主 tab 之间：`MainTabLayerHost` + `MainTabPredictiveBackHandler`。

## 1 子页为什么不像硬切

**内部层：AnimatedContent，不是 if 换内容**

- 手机：`PhoneSettingsPageTransitionHost` 用整页横滑，spec 是 `spring(noBouncy, mediumLow)`，
  旧页 `slideOutHorizontally`、新页 `slideInHorizontally`，方向由深度比较决定。
- 平板/分栏：淡入 + 缩放。进入 300ms、0.98；退出 160ms、0.99；
  两条不同 cubic-bezier（进场 `(0,0,0,1)`、出场 `(0.3,0,1,1)`）。
  退出比进入快，是为了让新页接上时画面和玻璃遮罩连续。
- 两处都套 `SizeTransform(clip = true)`，避免转场中露出父容器。

**宿主层：抽屉式堆叠**

设置 ↔ 下载管理用的是 `advancedGlassHostNavigationTransition`：

- 打开 300ms、关闭 280ms，`FastOutSlowInEasing`；
- 背景场景 `contentScale = 0.98`、`contentTranslationYFraction = 0`（往下沉一点）；
- 新场景从底部抬入（`revealTopFraction 1 → 0`），zIndex 按 navigationDepth 递增；
- 退出场景用 `ExitTransition.KeepUntilTransitionsFinished`，不会提前被移除。

**玻璃交接**

`AdvancedGlassNavigationHandoff` 在两层同时在场时开启：转场期间只让当前层采样背景，
避免两个 layer 同时注册区域、一起闪。设置页的 300ms/160ms 差就是为这个留的余量。

## 2 预测性返回怎么接上

**开关**

Manifest 里 `android:enableOnBackInvokedCallback="true"`；Compose 侧用
`PredictiveBackHandler` 收手势事件。

**核心：SeekableTransitionState**

不是自己算 fraction，而是把 `Transition` 变成可 seek 的：

1. `remember { SeekableTransitionState(targetState) }` + `rememberTransition(...)`；
2. 手势每个 progress 事件 `seekTo(progress, backTarget)`；
3. 松手提交：调用 `onBack(seeked = true)`，调用方**必须立刻**把 target 设成 backTarget，
   让 transition 从当前 fraction 继续，而不是从 0 重放；
4. 手势取消：`settleCancelledBack` 把 fraction 动画回 0，时长 = fraction × 总时长
   （还带最小时长），所以浅滑会很快弹回，不会卡在中间。

**两个细节**

- `primePreviewAtZero`：第一次拖动前先 `seekTo(0f)` 并空转两帧，
  让布局动画在零进度注册，否则第一帧会跳。
- `segment` 标记 + `ownsHistory()`：防止手势进行中返回栈被别的导航改掉，
  也防止旧收尾动画干扰新手势。

**预览开关**

`PredictiveBackPreviewGate` 用 `navigationevent` 的 dispatcher 做了一层桥：

- 预览开：progress 原样下传；
- 预览关：吞掉 progress，松手时补发 start + complete，让页面走普通返回动画；
- 桥常驻，切换开关不会重挂 NavHost。

## 3 摘录

① `SettingsPageTransition.kt:53-63`
```kotlin
private fun settingsPageContentTransform(): ContentTransform {
    val enterSpec = tween<Float>(
        durationMillis = SETTINGS_PAGE_ENTER_DURATION_MS,
        easing = SettingsPageEnterEasing
    )
    val exitSpec = tween<Float>(
        durationMillis = SETTINGS_PAGE_EXIT_DURATION_MS,
        easing = SettingsPageExitEasing
    )
    return (fadeIn(enterSpec) + scaleIn(enterSpec, initialScale = SETTINGS_PAGE_ENTER_SCALE))
        .togetherWith(fadeOut(exitSpec) + scaleOut(exitSpec, targetScale = SETTINGS_PAGE_EXIT_SCALE))
```
② `SettingsPageTransition.kt:173-179`
```kotlin
    pageTransition.AnimatedContent(
        modifier = Modifier.fillMaxSize(),
        transitionSpec = {
            isolatedAdvancedGlassHorizontalTransition(
                forward = isForwardSettingsPageTransition(initialState, targetState)
            ).using(SizeTransform(clip = true))
        }
```
③ `HostPredictiveBackTransition.kt:92-105`
```kotlin
                    gesture = HostBackGesture(
                        backTarget, progress.coerceIn(0f, 1f), segment, primeAtZero == true
                    )
                    seeked = true
                }
            }
            gesture = null
            currentOnBack(seeked)
        } catch (cancelled: CancellationException) {
            cancelledGesture = if (seeked) HostBackCancellation(restoreTarget) else null
            gesture = null
            throw cancelled
        }
```
④ `HostPredictiveBackTransition.kt:201-222`
```kotlin
internal fun hostBackCancelDurationMillis(
    fraction: Float,
    totalDurationNanos: Long,
    minimumDurationMillis: Int = 0
): Int {
    if (fraction <= 0f || totalDurationNanos <= 0L) return 0
    val duration = (fraction * totalDurationNanos / 1_000_000L).roundToInt()
    return maxOf(duration, minimumDurationMillis)
}
```
⑤ `AdvancedGlassNavigationTransition.kt:342-345`
```kotlin
internal const val DRAWER_NAVIGATION_OPEN_DURATION_MS = 300
internal const val DRAWER_NAVIGATION_CLOSE_DURATION_MS = 280
internal const val DRAWER_BACKGROUND_SINK_FRACTION = 0f
internal const val DRAWER_RECESSED_CONTENT_SCALE = 0.98f
```
⑥ `SettingsHostScreen.kt:215-221`
```kotlin
        navigationTransition.AnimatedContent(
            transitionSpec = {
                advancedGlassHostNavigationTransition(
                    forward = targetState.navigationDepth > initialState.navigationDepth,
                    coherentFeedbackEnabled = coherentFeedbackEnabled,
                    targetContentZIndex = targetState.navigationDepth.toFloat()
                ).using(SizeTransform(clip = true))
```

## 4 移植到自己的 App

1. 子页别用 `if` 直接换内容：套 `AnimatedContent`，让旧页有 150–200ms 的退场。
2. 手机横滑、平板淡入缩放；两套都用 `SizeTransform(clip = true)`。
3. 返回手势用 `SeekableTransitionState` + `PredictiveBackHandler`，一个 transition 同时服务点击与手势。
4. 取消回弹时长按进度比例，别写死 300ms。
5. 设置里的「预测返回预览」开关用 dispatcher 桥实现，别靠重挂导航。
6. 转场中加 in-flight 标记，防止二次 pop。

注意：NeriPlayer 是 GPL-3.0，这里只记行为与参数，落地要自己重写。
