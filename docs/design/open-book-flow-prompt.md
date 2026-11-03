# 提示词：优化「听书」从书架打开书籍的流程

把下面整段复制给 AI（Claude Code / Codex / Cursor）。仓库：`bookvoice`。

---

你是 Android Compose 工程师。任务：优化「听书」（包名 `io.github.mungbean2435.bookvoice`）**从书架点开一本书到正文可用**这段流程。仓库在当前目录。

## 开工前必读

1. `docs/PROJECT.md` 的「架构总览」「目录结构」两节；
2. 代码：`ui/AppRoot.kt`、`ui/player/PlayerOverlay.kt`、`ui/player/PlayerScreen.kt`、`ui/player/ReaderRows.kt`、`data/repo/Library.kt`、`data/db/Db.kt`、`ui/components/FileImage.kt`、`data/settings/Metrics.kt`。

读不到就停下来问我要文件，**不要凭印象猜 API**。

## 已核实的现状（别重复实现，也别破坏）

| 机制 | 位置 |
| --- | --- |
| 点书只改状态，播放页是 overlay 不是路由 | `AppRoot.kt:196-202` |
| 展开动画 280ms，`translationY` 上滑 | `PlayerOverlay.kt:36`、`:167-174` |
| 换书/换章重置状态 | `PlayerOverlay.kt:183` |
| 一次 IO 取 book+chapters+全书 blocks+images | `PlayerScreen.kt:229-237` |
| 加载期全屏 `CircularProgressIndicator` | `PlayerScreen.kt:492` |
| 打开后滚到上次段落 | `PlayerScreen.kt:246-263` |
| `derivedStateOf` 只算可见行 | `PlayerScreen.kt:189-220` |
| 全书段落一次读进内存 | `Db.kt:322-330` |
| 封面按目标宽度采样解码 | `FileImage.kt:19-40` |

## 要实现的改动

### P0 首屏不再等整本书

1. **分章加载**：先查 `book` + `chapters` + `startChapter` 一章的 blocks/images，立即渲染；其余章在后台补齐。
   - 新增 `Db.blocksOfChapter(bookId, chapterIdx)` / `Db.imagesOfChapter(...)`，查询走现有主键前缀，不建新表；
   - 补齐时**追加**到 `rows` 尾部，`items(rows, key = ...)` 里 key 的生成规则保持稳定（现为 block.idx 字符串），不能整体替换导致列表跳位。
2. **去掉全屏转圈**：改成固定骨架——顶栏 48dp 槽（`reading.topChromeHeight`）+ 4~6 条灰块，首帧就有正文结构；`loading` 只控制骨架显隐。
3. **定位只做一次**：首章 rows 到达后立即 `scrollToItem` 到 `lastBlockIdx`；后台补齐期间如果用户已经手动拖动（`listState.isScrollInProgress` 或现有 `isDragged`），**不要再跳**。

### P1 内存与列表

4. 打开后内存里只保留当前章 + 相邻章的 `Block` 文本，其余章按需拉取；`buildRows` 支持增量输入（已有 `internal fun buildRows(blocks, images)` 在 `ReaderRows.kt:36`，不要破坏签名）。
5. 段落不复制大字符串；`DisplayRow` 保持轻量。

### P2 可观测

6. 打点：新增 `Metrics.lastOpenMs`（点书 → 首帧正文可见）与 `Metrics.lastOpenBreakdown`（查询 / 构建 rows / 首帧各多少 ms），设置页「诊断」里显示。
7. 保留现有全部机制：overlay 转场、`NavScene(active)` 屏蔽触摸、`key(bookId, chapter)`、`derivedStateOf`。

## 硬约束

- 不改 `playback/`、`tts/`；DB 若加版本必须写迁移并说明，老数据不能丢；
- 不新增三方依赖；不改 SharedPreferences 键名与默认值；
- 每次改动可编译；不做全量格式化、不重排无关文件；
- 不写「示意代码」，给完整可粘贴代码块。

## 自查与验收

1. 5000 段以上的大书：冷启动点开，**≤300ms 出现正文骨架**，全程无全屏转圈；
2. 首帧后 1.5s 内定位到上次段落，且之后不再跳动；加载中手动上滑后，后台补齐不能把视图拽回；
3. 退出重进、旋转屏幕，位置与字号/主题保持；
4. 内存对比：打开同一本书，首章就绪时 `adb shell dumpsys meminfo <pkg>` 的 Java Heap 不高于改动前；
5. `./gradlew :app:assembleDebug` 通过，无新增 lint error；
6. `docs/CHANGELOG.md` 补一条。

## 工作方式

1. 先输出「影响面（文件+行号）/ 方案 / 风险」三项，等我确认再改；
2. 改完按文件给完整代码 + 一句话说明；
3. 贴 `assembleDebug` 输出末尾与第 1、2 条的手测结果（哪个书的段落数、耗时多少）。
