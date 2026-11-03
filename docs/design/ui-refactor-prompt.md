# 给 UI / 前端 Agent 的重构提示词

目标：让听书 App 的界面长成 NeriPlayer 那套体系，而不是抄一遍它的页面。

## 0 先填这 6 个空

```
<工程路径>       例：app/src/main/java/com/xxx/reader
<主题文件>       例：ui/theme/AppTheme.kt
<页面清单>       书架、播放页、章节、书签、下载、设置
<Compose/M3版本> 例：compose-bom 2025.xx / material3 1.3.x
minSdk / targetSdk
<主色>           种子色 hex
```

## 1 完整版提示词（直接粘给 Agent）

```text
你是 Android Compose UI 工程师。任务：把现有听书 App 的界面，按参考项目
NeriPlayer 的视觉与交互体系重构。先读这两份材料再动手：

- 学习笔记：docs/ui-notes.md（已逐条核对源码，含行号）
- 现有工程：<工程路径>，主题在 <主题文件>

一、边界
1. 只改 UI 层与主题层；播放、下载、同步、数据模型的逻辑不改。
2. 不新增依赖。<Compose/M3版本>，minSdk <...>。参考项目里的玻璃/模糊引擎
   不抄，先用 GlassSurface 的 fallback 色实现：surfaceContainerLow，
   有自定义背景图时 alpha 0.72，再加 0.5dp、8% onSurface 描边。
3. 参考项目是 GPL-3.0。只照笔记里的行为与数值重写，禁止粘贴其源码、
   注释、资源与文案；命名和包结构用本工程规范。
4. 每轮只改 1–2 个文件或 1 个页面，保证可编译；先给方案再动代码。

二、目标体系（按笔记落地，数值照抄）
- 令牌：@Immutable data class（形状/间距/描边透明度/动效），
  staticCompositionLocalOf 下发；Material 与 Clean 两套默认值。
- 形状：Clean 用连续圆角（拐角延长 1.1、控制柄 0.643），
  Material 用 RoundedCornerShape；卡片 16dp、组 24dp、弹窗 28dp、
  sheet 顶部 28dp、mini player 22dp。
- 颜色：Clean 的 surface 只做 1%/1.6% 的 accent tint，选中容器
  12%（亮）/20%（暗），surfaceTint 透明；强调色只出现在可点控件上。
- 排版：标题靠字重拉层级，tracking -0.022~-0.002em，正文行高 20/23sp。
- 页面壳：LargeTopAppBar + exitUntilCollapsedScrollBehavior，
  snapAnimationSpec = null，标题收起为左上角小标题；每个子页各持一份
  TopAppBarState 与 LazyListState。
- 浮层：mini player 高 64dp、左右 16dp、下 6dp、22dp 圆角；
  底部 tab 高 64dp、边距 16/16dp。
- 动效：设置类子页用 AnimatedContent 横滑（spring noBouncy / mediumLow）
  或淡入缩放 300ms 进、160ms 退（0.98 / 0.99）；返回手势可拖动；
  主题切换用按钮圆心的圆形揭示 640ms，反向 560ms。
- 按压：每边内缩 3dp、上限 6%、高亮 7%，在 draw 阶段 scale，不重组。
- 列表：LazyVerticalGrid，手机 Clean 至少两列（150dp 单元），
  底部 padding 要含 mini player 高度。

三、听书场景映射（逐页）
- 书架：网格卡片 = 封面 1:1 + 书名（1 行）+ 作者/进度（1 行）；
  分区标题用「字重 + 22sp」，不带图标（Clean）。
- 播放页：封面 + 章节/书签入口；章节列表复刻队列 sheet：
  本地 mutableStateList + isDirty，松手才提交，失败回滚；
  行高 min 72dp、20dp 圆角。
- 书签/笔记：同款行结构，右侧操作收进更多菜单。
- 下载：进度行用 ListItem + 20dp/2dp 圆环；结果提示卡从顶部滑入。
- 设置：分组卡 + 折叠头部；子页用同一套转场；主题页做
  「界面风格（双卡片预览）/ 深浅色分段条 / 自动跟随开关 / 动态取色 /
  6 种取色风格」。
- 备份恢复：5 张卡（本地/历史/GitHub/WebDAV/消息），导出导入走 SAF，
  配置导出可加密，导入完成后按需重建 Activity。

四、交付
1. 第一轮只交：令牌表（形状/间距/排版/动效数值）+ 文件计划 + 风险点。
2. 之后逐页改，每页带 @Preview 或前后截图。
3. 最后一轮给自检表：笔记里哪些条目落地、哪些没做、为什么。

约束复述：动手前先用自己的话复述以上边界与数值，等我确认。
```

## 2 短版（只想先看方向）

```text
读 docs/ui-notes.md。按它的令牌、页面壳、浮层尺寸和转场数值，
给我听书 App 的重构方案：先出令牌表和文件计划，不写页面代码。
注意：参考项目是 GPL，只照行为重写，不粘贴源码。
```

## 3 分三轮更稳

1. 体系：令牌 + 主题 + GlassSurface fallback + 应用壳。
2. 页面：书架 → 播放页/章节 → 设置/备份。
3. 交互与验收：折叠头部、子页转场、主题揭示、按压反馈、自检表。

每轮把该页截图和新旧对比一起发，别一次要全量。

## 4 验收清单（让 Agent 自己勾）

- [ ] 页面里没有 `RoundedCornerShape(20.dp)` 这类裸形状，全走令牌
- [ ] 没有 `if (isClean)` 散落在页面，收进 neriRoundedShape 一类函数
- [ ] 子页切回来，折叠状态与滚动位置还在
- [ ] 按压时没有重组/重排（Layout Inspector 可看）
- [ ] mini player 与底部 tab 左右边距一致
- [ ] 主题切换没有闪白/闪黑
- [ ] 所有可点区域 ≥ 48dp

## 5 两个提醒

- NeriPlayer 是 GPL-3.0。你的听书 App 若闭源，别粘它的代码与资源，
  只按笔记里的行为、数值自己实现。
- 别把「所有页面一次重构」丢给 Agent。它最容易在令牌还没立住时
  就开始改页面，最后形状和间距全是散的。
