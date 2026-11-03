# 页面状态与统一返回（2.6.0）

> 目标：设置子页返回、切 tab、旋转、进程重建之后，列表位置与大标题折叠进度都不丢；
> 箭头、系统返回、返回手势走同一套策略。

## 1. 原因

旧实现里 `AppRoot` 维护一个手写返回栈：

- 切 tab 时 `stack.clear()`，设置子页被直接丢掉；
- `SettingsScreen` 和每个子页内部各自 `rememberLazyListState()` / `rememberTopAppBarState()`，
  页面一旦被 `AnimatedContent` 换掉，状态跟着销毁，回来就回到顶部、大标题展开。

## 2. 新结构

```
AppRoot（共享宿主）
├─ scenes: AppSceneStates            ← 书架 / 书签 / 设置所有页面的 LazyListState / TopAppBarState
├─ selectedTab / settingsPage / …    ← rememberSaveable
├─ sceneHolder = rememberSaveableStateHolder()
│   └─ SaveableStateProvider(tab.name) → 当前 tab 场景
└─ 设置场景 SettingsScene
    ├─ page == null  → 设置首页（state = scenes.settings.home）
    └─ page != null  → 子页（state = scenes.settings.{page}）
```

- **状态提升**：`ui/nav/SceneStates.kt` 在 `AppRoot` 里创建每一页的
  `PageChromeState(list, topBar)`，用 `rememberSaveable(saver = LazyListState.Saver)`
  和 `rememberSaveable(saver = TopAppBarState.Saver)` 保存；页面只接收，不 remember。
- **互斥分支**：`SettingsScene` 里 `page == null` 渲染首页，否则渲染子页，不销毁宿主状态。
- **tab 状态容器**：`sceneHolder.SaveableStateProvider(selectedTab.name)` 包住每个 tab 场景，
  切走自动保存、切回恢复，等价于 NavHost 的 `saveState/restoreState`。
- **子页标识**：`settingsPage: String?` 在 `AppRoot` 里 `rememberSaveable`，切 tab 不清空，
  所以「子页 → 别的 tab → 设置」仍停在原页面。
- **跨 scene 页面**：TTS 配置编辑是全屏编辑页（自己的未保存确认），进入前记录
  `ttsList` 的 index/offset，返回时兜底 `scrollToItem`（正常因状态已提升而是 no-op）。

## 3. 统一返回

`ui/nav/BackPolicy.kt` 是纯函数，优先级从上到下：

| 当前 | 返回动作 |
| --- | --- |
| TTS 配置编辑页 | 页面自己处理（未保存确认），宿主不拦截 |
| 目录浮层 | 关闭目录 |
| 设置子页 | 回设置首页 |
| 设置首页 | 切回书架 tab，不退出 App |
| 听书页 | 回到进入前的 tab（书架 / 书签 / 设置） |
| 书架 / 书签 | 系统默认（退出 / 进后台） |

`AppRoot.dispatchBack` 是唯一入口：顶栏箭头、`PredictiveBackHandler`（系统返回 / 手势）
都调它；没有第二套 pop 逻辑。`BackGate` 在上一次返回后的 350ms 转场窗口内吞掉重复返回，
连点箭头 / 连按返回不会连续 pop。

## 4. 返回手势预览

`PredictiveBackHandler` 的 progress 驱动：

- 子页：首页垫在下层，子页右移 30% 并轻微淡出；
- 设置首页：书架垫在下层，首页右移；
- 目录浮层：听书页在下面，目录右移。

所以拖动过程中看到的是目标页的列表，不是白屏；松手才真正切换，取消则回到原位。

## 5. 文件

| 文件 | 作用 |
| --- | --- |
| `ui/AppRoot.kt` | tab 宿主、状态宿主、唯一返回入口、手势预览 |
| `ui/nav/SceneStates.kt` | 页面状态提升 + `rememberSaveable` Saver |
| `ui/nav/BackPolicy.kt` | 返回优先级纯函数 |
| `ui/nav/BackGate.kt` | 转场窗口去重 |
| `ui/settings/SettingsScene.kt` | 设置首页 / 子页互斥渲染 + 每页 SaveableStateProvider |
| `ui/components/PageScaffold.kt` | 接收宿主传入的 `TopAppBarState` |
| `ui/settings/*Screen.kt` | 接收 `PageChromeState` / `LazyListState`，不再自建 |
| `ui/shelf/ShelfScreen.kt` | 网格与折叠状态由宿主持有 |

## 6. 自测清单

1. 设置 → 播放设置：拖动语速滑块后返回，设置首页的列表 index/offset 与大标题折叠进度不变。
2. 设置 → 存储与缓存 → 顶栏箭头：回设置首页。
3. 设置首页按系统返回：切到书架，不退出。
4. 书架上按系统返回：退出 / 进后台（系统默认）。
5. 子页 → 切到书架 → 点设置：仍停在刚打开的子页。
6. 子页上连按 5 次返回：不崩、不卡死；只按策略推进。
7. 返回手势拖动：能看到下面的设置首页 / 书架列表。
8. 旋转屏幕、开发者选项「不保留活动」后重建：列表至少回到原 index/offset。
9. 书架 → 书签 → 设置各滚到中间，连续切 20 次：三个位置仍各自保留；胶囊药丸只平移，不重建场景。

## 7. 边界

- 未新增依赖（没有 navigation-compose）。
- 不改业务逻辑、数据库、播放内核。
- 听书页仍是全屏主界面（不显示底部胶囊），和 2.4/2.5 的行为一致。
- 2.6 起底部 tab 是书架 / 书签 / 设置；播放页从书架、mini player 或书签进入。
- `TtsConfigEditScreen` 仍是跨 scene 全屏页，返回确认由页面自己负责。
