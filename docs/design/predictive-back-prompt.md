# 子页转场 + 预测性返回：给 AI 的提示词

想让 App 的页面跳转「不生硬」并支持返回手势预览，就发这一段。
先确认三件事，再动手：

1. Compose 版本是否 ≥ 1.7（`SeekableTransitionState` 在 1.7 才稳定）；
2. `activity-compose` 版本是否带 `PredictiveBackHandler`；
3. 设置子页是 NavHost 路由，还是页面内部的 `activePage` 状态——两种改法不同。

## 1 可粘的提示词

```text
你是 Android Compose 工程师。任务：让页面跳转不再是硬切，并接入预测性返回。
工程：<路径>；Compose/M3：<版本>；minSdk：<..>；导航：<Navigation-Compose / 自研>。
目标：设置首页 ↔ 设置子页；设置 ↔ 下载管理（宿主层）。

第一步：诊断，先不改代码
- 列出所有「整页替换」的地方：if (page == null) … else …、when(screen)、NavHost 的 composable。
- 标出哪些是子页（深度 +1）、哪些是同级切换。
- 确认工程是否能用到 SeekableTransitionState 和 PredictiveBackHandler。
- 给我改动清单和文件列表，等我确认。

第二步：换成 AnimatedContent
- 子页（手机）：整页横滑，spring(dampingRatio = NoBouncy, stiffness = MediumLow)。
- 子页（平板）：fade + scale，进 300ms / 0.98、退 160ms / 0.99，两条不同的 cubic-bezier。
- 两处都套 SizeTransform(clip = true)。
- 旧页必须先退：退出时长不要大于进入，避免闪白。
- 宿主层（设置 ↔ 下载管理）用抽屉：背景 scale 0.98 并下沉，新页从底部抬入，
  打开 300ms、关闭 280ms，退出用 KeepUntilTransitionsFinished。
- NavHost 默认转场和自定义转场只能留一套，不要叠加。

第三步：预测性返回
- Manifest 开 android:enableOnBackInvokedCallback="true"。
- 用 PredictiveBackHandler 收 progress；用 remember { SeekableTransitionState(target) }
  + rememberTransition 驱动同一个 AnimatedContent。
- progress 事件：transitionState.seekTo(progress, backTarget)。
- 提交：onBack(seeked = true)，调用方必须立刻把 target 设为 backTarget，不能重放。
- 取消：把 fraction 动画回 0，时长 = fraction × 总时长（可给最小 120ms），不要写死。
- 若首帧跳动：先 seekTo(0f) 再空转两帧，等布局动画注册。
- 加 in-flight 标记：转场未结束或已发起返回时忽略第二次返回。
- 用「当前返回栈条目」校验所有权：手势中途导航变了，就放弃这次 seek。
- API < 33 或系统没发进度事件时，走普通返回，不能卡住。

第四步：设置项
- 外观设置加「返回手势预览」开关，默认开。
- 关闭预览时吞掉 progress，松手补 start + complete，走普通返回动画。
- 切换开关不能重挂 NavHost。

验收
- 点击进子页：有过渡，旧页不是瞬间消失。
- 返回手势：页面跟手，能看到下层页面；松手完成或回弹。
- 连点返回 10 次不卡死；转场中切 tab 不崩。
- API < 33 设备正常返回。
- 关掉预览开关仍有正常返回动画。
- 旋转屏幕不中断转场、不丢状态。

边界：不改业务逻辑；不新增依赖（要加先说明）；每阶段可编译并给截图或录屏。
```

## 2 为什么这么写

- **必须点名 `SeekableTransitionState`**：否则 AI 十有八九自己算 fraction，
  写出一套和点击动画不共用的时间线，松手时页面会跳一下。
- **提交契约要写死**：`onBack(seeked)` 之后必须立刻切 target。
  不写清楚，AI 会让动画从 0 重放，看着像回弹。
- **取消时长按比例**：固定 300ms 会让浅滑变慢、深滑变赶。
- **in-flight 标记**：这就是「连点返回卡死」的常见来源，和之前的 bug 是同一处。
- **预览开关用桥**：直接 remount NavHost 会丢掉页面状态。

## 3 常见坑

| 现象 | 多半是 |
|---|---|
| 手势拖动没反应 | 没把 target 做成可 seek；或 progress 被 gate 吞了 |
| 松手后跳一下 | 提交时从 0 重放，没接当前 fraction |
| 返回完白屏 | 退出转场被提前移除，缺 KeepUntilTransitionsFinished |
| 连点卡死 | 重复 pop / 转场未完成又发起返回 |
| 预览开关无效 | 只改了 Compose 局部状态，没换 dispatcher |
| 平板分栏错位 | 把分栏的两栏当成两个 scene 做了深度动画 |

## 4 验收时自己看

1. 进子页时旧页有没有「退一半」。
2. 返回手势拖到一半松手，是回弹还是完成，方向对不对。
3. 拖到一半往回收，再拖一次，状态是否连续。
4. 转场中切 tab 再回来，页面是否还在。
5. 关掉预览开关，返回是否仍然顺。
