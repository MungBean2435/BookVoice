# 听书（Tingshu）项目文档

> 版本 2.9.5 · 最后更新 2026-11 · 单人项目

---

## 1. 项目是什么

一个自己用的 Android 听书 App：导入 TXT / EPUB，用云端 TTS 连续朗读，边听边看当前段落。

**设计取向**
- 交付优先：能跑通、能装、能上手机，不为架构优雅拖时间。
- 本地优先：没有账号、没有服务端、没有云同步；Key 只存在本机。
- 可换供应商：第一家 MiMo，第二家 MiniMax，接口抽象好，加第三家只写一个类。

**不是目标**
在线书源、PDF、iOS、桌面端、应用商店上架、付费订阅、多用户。

---

## 2. 功能清单

| 能力 | 状态 |
|---|---|
| 导入 TXT（UTF-8 / GBK 自动识别）、EPUB（按 spine 顺序 + 插图） | ✅ |
| 书架、章节列表、按段落记录进度、书签 | ✅ |
| 云端 TTS：MiMo / MiniMax 两家，多套配置，列表里切换启用 | ✅ |
| 模型 / 音色库：每个配置可存多个，卡片切换当前项，在线/内置目录一键导入 | ✅ |
| 跨段合并合成（对话体不卡顿）、跨章预取 | ✅ |
| 后台播放、通知栏 / 锁屏控制、定时关闭 | ✅ |
| 句级高亮（按播放进度估算）、当前段 3dp 竖条 + 淡底 | ✅ |
| 断网等待恢复、失败段落跳过并进「待补」 | ✅ |
| 音频缓存（按内容寻址）、上限清理、缓存本章 | ✅ |
| 朗读规则（内置 + 导入 JSON 规则包） | ✅ |
| 备份导入 / 导出（进度、书签、规则、设置；不含书与 Key） | ✅ |
| 流式首块（MiMo TTS，边收边播）、多角色、情感控制 | 全局流式（MiMo 每块）✅；多角色 / 情感 ❌ |

---

## 3. 技术栈与环境

| 项 | 值 |
|---|---|
| 语言 / UI | Kotlin 2.0.21 + Jetpack Compose（BOM 2024.10.01） |
| 构建 | AGP 8.7.3 + Gradle 8.9 |
| JDK | 17 |
| SDK | compileSdk 35 / targetSdk 35 / minSdk 26 |
| 播放 | Media3 ExoPlayer 1.5.0 |
| 存储 | 原生 SQLite（SQLiteOpenHelper，不引 Room） |
| HTML 解析 | jsoup 1.18.1 |
| 依赖策略 | 只加必要依赖，每加一个都要能一句话说清理由 |

**本机构建注意**：这台开发机是 aarch64，Android SDK 自带的 `aapt2` 是 x86_64 二进制，直接跑会报 `AAPT2 Daemon startup failed`。解决办法是用 `qemu-user-static` 包一层，详见 [BUILD-ON-DEVICE.md](BUILD-ON-DEVICE.md)。

---

## 4. 架构总览

```
┌──────────────────────────── UI（Compose） ────────────────────────────┐
│ ShelfScreen    PlayerScreen    ChapterListScreen                      │
│ SettingsScreen RulePacksScreen TtsConfigListScreen TtsConfigEditScreen │
│            （AppRoot：书架/书签/设置 tab 容器 + 设置子页）              │
└───────────────┬───────────────────────────────────────────────────────┘
                │ StateFlow / suspend 调用
┌───────────────▼─────────────┐   ┌────────────────────────────────────┐
│ playback                    │   │ tts                                │
│  SpeechPlayer（单例，状态机）│──▶│  ProviderRegistry → TtsProvider     │
│  PlaybackService（前台服务） │   │    MimoProvider / MiniMaxProvider   │
│  AudioCache（内容寻址缓存）  │   │  ChunkPlanner（跨段合并 + 位置映射） │
└───────────────┬─────────────┘   │  RulesEngine（文本规整）            │
                │                 └────────────────────────────────────┘
┌───────────────▼───────────────────────────────────────────────────────┐
│ data                                                                  │
│  Db（SQLite，8 张表）  Library（导入/读取/进度）  Settings（含 TTS 配置） │
│  parse（TxtParser / EpubParser / TextSplit / TextDecode）               │
│  backup（BackupManager）                                                │
└───────────────────────────────────────────────────────────────────────┘
```

**一次播放的数据流**

```
Library.blocks(章) ──▶ ChunkPlanner.plan(跨段合并, 规则规整) ──▶ 队列
      │                                                        │
      │  流式供应商：每块 streamPcm ──▶ PCM 缓冲 ──▶ ExoPlayer 边收边播 ──▶ tick
      │  整块供应商：查缓存 ──命中──▶ 文件 ──▶ ExoPlayer 播放 ──▶ tick
      │              └─未命中─▶ synthesize ──▶ 写缓存 ──▶ 文件 ──┘
      └── 整块供应商才预取：当前 +3 块；章末再预取下一章头 2 块
```

**线程模型**
- UI 状态、`SpeechPlayer` 的协程跑在主线程（`Dispatchers.Main.immediate`）。
- 网络、数据库、文件读写全部 `Dispatchers.IO`。
- 合成用 `Mutex` 串行化，避免并发打爆额度，也保证预取顺序正确。

---

## 5. 目录结构

```
app/src/main/java/io/github/mungbean2435/bookvoice/
├─ App.kt / MainActivity.kt      应用入口，初始化 Db/Settings/Speech
├─ ui/
│  ├─ AppRoot.kt                 tab 宿主 + 页面状态宿主 + 统一返回入口
│  ├─ nav/                       返回策略 / 转场闸门 / 可保存的页面状态
│  ├─ settings/SettingsScene.kt  设置首页与子页互斥渲染（状态在宿主）
│  ├─ AppViewModel.kt            书架状态、导入、删除
│  ├─ shelf/ShelfScreen.kt       书架
│  ├─ book/ChapterListScreen.kt  目录
│  ├─ player/PlayerScreen.kt     主界面（听书页）
│  ├─ player/PlayerChrome.kt     顶栏 / 底部控制 / 阅读设置 Sheet
│  ├─ player/PlayerGlyphs.kt     Canvas 传输键图标
│  ├─ player/TapPolicy.kt        单击行为的纯函数规则
│  ├─ settings/…                 设置、规则包、TTS 配置列表 / 详情
│  ├─ settings/SettingsComponents.kt 卡片 / 行 / 搜索 / 外观按钮
│  ├─ settings/SettingsGlyphs.kt     彩色图标 Canvas
│  ├─ settings/SettingsDetailScreens.kt 播放 / 存储 / 诊断子页
│  ├─ settings/SettingsSearch.kt     搜索匹配纯函数
│  ├─ components/TingshuTabBar.kt 浮层导航 + 可拖拽镜头
│  ├─ components/TabBarLens*.kt   镜头几何 / 手势 / 合成 / 折射
│  ├─ components/FileImage.kt    最小图片加载（按尺寸采样解码）
│  └─ theme/                     Color / Type / Dimens / Theme（动态取色）
├─ playback/
│  ├─ SpeechPlayer.kt            播放内核：队列、预取、缓存、进度、高亮、定时
│  ├─ PlaybackService.kt         前台服务 + MediaSession + 通知栏
│  └─ AudioCache.kt              缓存文件管理
├─ tts/
│  ├─ TtsProvider.kt             SPI：ProviderConfig / SynthRequest / SynthResult / 异常
│  ├─ ProviderRegistry.kt        供应商注册表
│  ├─ ChunkPlanner.kt            跨段合并 + 块内位置映射
│  ├─ PlaybackMath.kt            时长估算与格式化
│  ├─ SentenceSplitter.kt        句子区间
│  ├─ mimo/MimoProvider.kt
│  ├─ minimax/MiniMaxProvider.kt
│  └─ rules/                     规则包模型 + 规则引擎
└─ data/
   ├─ db/                        Db + 数据类
   ├─ parse/                     TXT / EPUB 解析
   ├─ repo/Library.kt            书库门面
   ├─ settings/Settings.kt       设置（多套 TTS 配置、旧字段迁移、外观模式）
   ├─ settings/ThemeMode.kt      跟随系统 / 浅色 / 深色
   ├─ settings/Metrics.kt        诊断指标（内存）
   └─ backup/BackupManager.kt
```

---

## 6. 数据模型

数据库 `tingshu.db`，版本 3。所有表都在 `Db.onCreate` 建好，`onUpgrade` 做就地迁移。

| 表 | 主键 | 用途 |
|---|---|---|
| `books` | id | 一本书：标题、格式、文件路径、封面、字数、进度（章/段） |
| `chapters` | (bookId, idx) | 章节标题 |
| `blocks` | (bookId, chapterIdx, idx) | 正文段落（≤800 字，超长按句切），TTS 的最小单位 |
| `chapter_images` | (bookId, chapterIdx, idx) | 插图，`afterBlockIdx` 决定插在第几段之后 |
| `bookmarks` | id | 书签（章 + 段） |
| `failures` | (bookId, chapterIdx, blockIdx) | 合成失败被跳过的段落（待补） |
| `rule_packs` | id | 导入的规则包（JSON） |
| `clips` | cacheKey | 预留：缓存索引，当前缓存靠文件名寻址，未写入 |

**进度**：存在 `books.lastChapterIdx / lastBlockIdx`，播放器每跨过一段写一次；不播放时滚动正文也会写（防抖 700ms）。

**缓存布局**：`files/audio/<sha1>.<ext>`，key 组成：
`sha1(providerId | model | voice | 音频格式 | 规则指纹 | 文本)`。改规则、换音色、换供应商都会自然失效。

---

## 7. 播放内核

### 7.1 为什么跨段合并
对话体小说一段只有十几个字。早先「一段一次请求」时，合成 2 秒、播放 5 秒，永远追不上，段与段之间全是空档。

现在由 `ChunkPlanner` 把连续段落合并成合成块：
- 首块 ≤ **150 字**（抢首声）；
- 后续每块目标 **400 字**，单片上限 **600 字**（超长段落按句切）；
- 块内保留 `spans`，能把播放位置映射回「第几段第几个字」。

效果：请求量约为逐段方案的 1/4，段间空档基本消失。

### 7.2 队列与预取
- **流式供应商（MiMo）**：每块都边收边播，**不自动预取**。预取会抢合成锁，网络抖动时曾把播放队列堵住最长 10 分钟（表现就是「读几句后不读了」）。
- **整块供应商（MiniMax 等）**：预取窗口为当前块之后 **3 块**（约 3–4 分钟音频）；章末剩余 2 块时提前合成下一章头 2 块。
- 合成串行（Mutex），但**退避等待在锁外**：每次拿锁只发一次请求，失败后释放锁再等，避免预取协程抱着锁睡觉。
- 预取失败最多等 30 秒就停，不把后面的块全撞到限流上。

### 7.3 停顿
- 块与块之间用「段落间停顿」（默认 300ms，可调 0–1200ms）。
- 块内不再人为插停顿，靠标点自然停顿；界面上已移除无意义的「句间停顿」。

### 7.4 缓存
- 一个合成块一个文件，扩展名由供应商决定（MiMo `.wav`、MiniMax `.mp3`）。
- 默认上限 512MB（可调 100–2048），超限按最久未修改删到 80%。
- 「缓存本章」= 把本章所有块合成进缓存，已有命中直接跳过，界面显示进度 `n/m`。
- 流式供应商当前块开始后会在后台预合成下一块（流式 PCM 写入缓存），下一块直接读文件播放，缩短块间空档。

### 7.5 重试与失败
| 情况 | 行为 |
|---|---|
| 网络错误 / 限流(429) | 退避重试（2/4/8/16/15s），播放最多等 10 分钟，试听最多 20 秒；预取最多等 30 秒 |
| 流式首包 10 秒未到 | 不立刻退回整块，先重试流式（最多 2 次，间隔 1.5 秒）；离线时先等网络恢复 |
| 流式中途 12 秒无新数据 | 先查网络：离线则停在当前块等网络恢复，恢复后整块重播本块（已播部分会重复，但不丢字）；在线则结束本块、记入「待补」、继续下一块 |
| 流式播放 3 秒不前进且合成已结束 | 强制停止本块并继续下一块（部分设备不会收到 `STATE_ENDED`） |
| Key 无效 / 超长 / 服务端业务错误 | **立刻失败**，不重试（避免把配置错误当成网络抖动） |
| 单块彻底失败 | 跳过该块、记入「待补」、整本书继续 |
| 等待网络 | 界面显示「网络中断，等待恢复…」，恢复后自动续播 |

### 7.6 进度、高亮、时间
- 高亮：每 200ms 用 `当前播放位置 / 块时长` 换算到块内字符，再从**段落开头连续填充**到当前位置。
  - 不按标点切句——标点只影响朗读停顿，不参与分割。
  - 仍然是**估算**，位置按比例推算，误差约 1 秒。
- 时长：没有真实全书时长，用 **≈196ms/字** 估算（MiMo 实测值）；界面标注「全书进度 · 时长为估算」，剩余时间是全书余量而非本章。
- 睡眠定时：15/30/45/60 分钟，到点自动 `stop()`。

### 7.7 前台服务
- `PlaybackService` 是 `foregroundServiceType="mediaPlayback"` 的前台服务，负责保活、通知栏、`MediaSession`。
- 通知栏提供：上一段 / 播放暂停 / 下一段；锁屏与蓝牙按键通过 MediaSession 回调。

---

## 8. 供应商接入

### 8.1 SPI

```kotlin
interface TtsProvider {
    val id: String                 // "mimo" / "minimax"
    val displayName: String
    val audioFormat: String        // 决定缓存扩展名
    val defaultConfig: ProviderConfig
    fun voices(): List<Voice>
    fun builtinCatalog(): TtsCatalog
    suspend fun fetchCatalog(config: ProviderConfig): TtsCatalog
    suspend fun synthesize(request: SynthRequest): SynthResult
}
```

2.1.0 起用户配置是 `TtsConfig` 列表（id / name / providerId / apiKey / baseUrl / model / voice），存 SharedPreferences 的 `configs` + `activeConfigId`。同一供应商可以有多条，只有 `activeConfigId` 那一条用于朗读；`ProviderConfig` 只作为供应商默认值和合成请求载体。2.0.x 的 `providers` / `activeProvider` 会自动迁移成配置列表，旧扁平字段先迁移成 `providers["mimo"]` 再进入新结构，Key 不丢。

2.2.0 起每条配置还带 `models: List<String>` 和 `voices: List<Voice>`（`Voice.id/label/hint`），`model` / `voice` 是当前选中值，`SpeechPlayer` 仍只读这两个值。JSON 编解码统一走 `TtsConfigJson`，解码时新字段缺失会从 `model` / `voice` 单值回退；`TtsConfigs.normalizeLibrary` 负责去重、把选中项补进库、选中失效时回落。备份含库字段、不含 Key。

### 8.2 小米 MiMo（当前默认）

- 端点：`POST https://api.xiaomimimo.com/v1/chat/completions`
- 鉴权：请求头 `api-key: <KEY>`
- 请求体：
  ```json
  {"model":"mimo-v2.5-tts",
   "messages":[{"role":"assistant","content":"文本"}],
   "audio":{"format":"wav","voice":"mimo_default"},
   "stream":false}
  ```
- 响应：`choices[0].message.audio.data`（base64 wav）、`choices[0].finish_reason`
- 限制：输入与音频共用 8192 token 上下文；约 4000–4500 字后音频会被压缩，5000 字以上直接截断且返回 400（不告诉你是超长）。所以块上限压在 600 字。
- 语速：请求里不带，由播放端变速。
- **流式（3.1.0 起；3.2.0 起每块都走）**：同一端点、同一个 body，改 `"stream":true` 且 `"audio":{"format":"pcm16"}`，`Accept: text/event-stream`；SSE 每行 `data: {...}`，音频在 `choices[0].delta.audio.data`（base64），`[DONE]` 结束。返回的是 **24 kHz / 单声道 / 16-bit LE PCM**（48000 B/s），不带 WAV 头，需要自己拼（流式时 data size 写 0xFFFFFFFF，读到 EOF 由播放器收尾）。
- 实测：40 字首个音频 delta ≈0.8–1.1 s，整段 40 字合成 2.25 s；非流式 150 字要 ~8.3 s。边收边播后首声从 8 s 级降到 1 s 级；`readTimeout` 流式用 20 s；取消/切书时主动 `disconnect()` 打断阻塞读。
- 取消：`streamPcm` 在协程取消时 `disconnect()` 连接，避免阻塞中的 `readLine` 拖住合成锁。

### 8.3 MiniMax（T2A v2）

- 端点：`POST https://api.minimax.cn/v1/t2a_v2`（国际站 `https://api.minimax.io/v1/t2a_v2`）
- 鉴权：`Authorization: Bearer <KEY>`
- 请求体：
  ```json
  {"model":"speech-2.8-hd","text":"文本","stream":false,"output_format":"hex",
   "voice_setting":{"voice_id":"…","speed":1.0,"vol":1,"pitch":0},
   "audio_setting":{"sample_rate":32000,"bitrate":128000,"format":"mp3","channel":1}}
  ```
- 响应：`data.audio` 是 **hex 编码**（不是 base64），`extra_info.audio_length` 是毫秒时长
- **HTTP 200 也可能是失败**：必须检查 `base_resp.status_code`，错误信息在 `status_msg`
- 输出格式只有 `mp3 / pcm / flac`，没有 wav，所以选 mp3
- 语速由服务端处理，播放端不再叠加变速（否则双重变速）
- 单次文本 < 10,000 字；音色 ID 需要用户从控制台复制，或从「添加模型与音色」在线获取
- 新版 speech-2.8 模型会拒绝旧 speech-01 音色 ID（如 `female-shaonv`，报 `voice id not exist`）；内置目录使用 `Chinese (Mandarin)_*` 现代音色

### 8.4 模型 / 音色目录

- **MiniMax 音色**：`POST {origin}/v1/get_voice`，Bearer，body `{"voice_type":"all"}`；GET 形式是 404，GroupId 非必填。响应 `system_voice` / `voice_cloning` / `voice_generation`，先检查 `base_resp.status_code`。
- **MiniMax 模型**：`GET {origin}/v1/models`，Bearer，过滤 `^speech`；失败时用内置 `speech-2.8-hd`、`speech-2.8-turbo`、`speech-2.6-hd`、`speech-2.6-turbo`。
- **MiMo 音色**：官方预置 9 个（`mimo_default`、冰糖、茉莉、苏打、白桦、Mia、Chloe、Milo、Dean），没有公开列表接口，直接内置。
- **MiMo 模型**：`GET {origin}/v1/models` best-effort，只接受 `mimo-v2.5-tts`（排除 `voiceclone` / `voicedesign`，这两个需要不同的请求协议）。
- 目录接口失败、无 Key、超时都回落到内置目录并带 note；UI 可重试，也可手动填模型/音色 ID。

### 8.5 加第三家（例如 GPT-SoVITS）

1. 新建 `tts/xxx/XxxProvider.kt`，实现 `TtsProvider`；
2. 在 `ProviderRegistry.providers` 里注册一行；
3. 「添加配置模板」会自动出现这家（模板列表从注册表读）；
4. 如果返回格式不是 wav/mp3，`audioFormat` 填对应后缀即可，缓存会自动适配。

---

## 9. 文本处理

### 9.1 TXT
- 编码探测：BOM → 严格 UTF-8 → 严格 GBK → UTF-8 兜底；
- 章节标题正则：`第X章/节/回/卷/部/篇`、序章、楔子、番外等，行长 ≤48 才认；
- 第一个标题前的内容成为「前言」章。

### 9.2 EPUB
- `META-INF/container.xml` → OPF → manifest/spine；
- 逐章解析 XHTML：取块级标签文本，`<img>` 解压到 `files/books/<id>/images/`；
- 相对路径支持 `../`；图片位置记录为「第几段之后」；
- 只提取正文真正引用的图，未被引用的不会进列表。

### 9.3 朗读规则
- 内置 10 条：省略号、破折号、重复标点、百分号、摄氏度、km、kg、空白归一；
- 自定义规则包是 JSON：`{"id","name","version","rules":[{"type":"literal|regex","from","to","note"}]}`；
- 规则只作用于「送 TTS 的副本」，正文显示不变；
- 规则指纹参与缓存 key，改规则后旧音频自动失效；
- 正则写错不会崩，该条被跳过，规则页会标红提示。

---

## 10. 界面

| 页面 | 说明 |
|---|---|
| 书架 | 导入（SAF）、最近打开排序、长按删除、右上进设置；无卡片容器，显示 EPUB 封面（缺失回退格式块） |
| 书签 | 跨书书签列表、点一条从原段继续、删除；无书签时有引导空态 |
| 听书页（主界面） | 顶部固定 48dp 槽位：收起态是空白行，左侧小号章节名、右侧网络/缓冲/缓存状态；展开态是操作行（返回 / 目录 / 书签 / Aa / 更多 / 收起），底部 0.5dp 分隔线，没有章节名大行。正文从槽位下方开始，滚动不会压到章节名。全书放在同一个连续滚动列表里，章与章之间留 28dp 空白，手动滑动可直接从本章末尾滑进下一章；只有插图的章节也作为正文的一部分显示，不再被播放器跳过。左上角章节名跟随第一行可见内容。底部迷你播放条：封面 / 书名章节 / 上一段 / 播放 / 下一段 / 收起 / 蓝色进度线；点中部显隐，上下滑 >24dp 收起，左右 18% 翻页；正文可滑动、当前段 3dp 竖条 + 淡底、非当前段完整对比度 |
| 播放设置（半屏） | 点迷你播放条的封面或书名打开 BottomSheet：完整进度条、五个传输键、语速/音色/定时/缓存本章；不是设置子页 |
| 目录 | 章节列表，点选跳转 |
| 底部导航 | 浮层胶囊（书架 / 书签 / 设置）；选中态是可拖拽镜头：按下涨起、拖动跟手、放大下方 tab、跨 tab haptic，松手弹到最近 tab；液态玻璃开启时边缘折射；轻点仍走 tab click；选中镜头颜色默认中性灰，可跟随主题或自定义 |
| 设置 | 大标题 + 外观三态（跟随系统/浅色/深色）+ 搜索；3 张卡片：语音与播放（TTS / 播放设置 / 规则）、数据（存储缓存 / 导出 / 导入）、支持（诊断 / 保活 / 关于）；子页返回 / 切 tab 后保留滚动位置与折叠状态 |
| 播放设置 / 存储缓存 / 诊断 | 设置子页：语速与停顿 Slider；缓存用量、上限与清理（确认）；诊断页带设备/配置/播放现场概览、筛选事件流、复制摘要、导出 txt、打标记与清空 |
| TTS 配置列表 | 搜索、配置卡片（启用状态 / 供应商 / 模型 / 音色）、右上角 +（添加配置 / 添加配置模板）、复制、删除 |
| TTS 配置详情 | 配置 / 模型与音色 / 高级设置三栏；是否启用、名称、API Key、接口地址、供应商模板；底部为悬浮胶囊：配置页试听/删除/保存，模型与音色页试听/删除/小保存/添加模型与音色，高级设置页不显示；未保存返回确认 |
| 模型与音色 | 模型、音色两个库；卡片显示名称、能力/来源标签、当前项、⋮（设为当前/删除）；添加动作在底部悬浮胶囊 |
| 可用目录 | 全高 BottomSheet：模型/音色分区、全选/取消全选、勾选、底部筛选、导入选中；在线失败回落内置，可手动输入 |
| 规则包 | 内置包与导入包的展示、启停、展开看规则、删除 |

**返回规则**（`BackPolicy` + `BackGate`）
- 设置子页 → 设置首页；设置首页 → 书架 tab；听书页 → 进入前的 tab（书架 / 书签 / 设置）；书架 / 书签 → 系统默认；
- 顶栏箭头、系统返回、返回手势共用同一入口；转场窗口内重复返回只处理一次；
- 目录浮层优先关浮层；TTS 配置编辑页的未保存确认由页面自己处理；
- 冷启动停在书架 tab，不自动打开最近一本书。

**交互规则**（`TapPolicy` + 阅读页手势）
- 左右边缘各 18%：翻页（滚动模式滚一屏），中部点击不翻页；
- 控制栏隐藏时：点中部 → 唤出控制栏；
- 控制栏显示 + 点读开：点正文中部 → 从该段开头朗读；点空白中部 → 收起；
- 控制栏显示 + 点读关：点中部 → 收起；
- 上滑、下滑超过 24dp → 收起；不再用上滑呼出（呼出只走点击中部）。

---

## 11. 构建与发布

```bash
export JAVA_HOME=/path/to/jdk17
export ANDROID_HOME=/path/to/android-sdk
gradle assembleDebug     # app/build/outputs/apk/debug/app-debug.apk
gradle assembleRelease   # app/build/outputs/apk/release/app-release.apk
gradle testDebugUnitTest # 全部单测
```

- 当前 release 复用 debug keystore 签名，这样能覆盖安装、保留数据；对外分发应换成自己的 keystore。
- 版本号规则：修 bug 升 patch，加功能升 minor，重构/换架构升 major。
- 产物习惯放到 `dist/`。

---

## 12. 测试

**单元测试（46 个）**

| 测试类 | 覆盖 |
|---|---|
| TxtParserTest | 章节切分、前言、BOM、无标题兜底、超长段不丢字 |
| TextDecodeTest | UTF-8 / GBK / BOM |
| EpubParserTest | 元数据、章节、插图、`../` 路径、真实书解析 |
| ChunkPlannerTest | 合并短段、首块上限、不丢字、位置映射、偏移起播 |
| PlaybackMathTest | 字数→秒、时间格式化 |
| SentenceSplitterTest | 句子区间 |
| RulesEngineTest | 内置规则、正则、坏正则、指纹变化 |
| MiniMaxHexTest | hex 解码与非法输入 |
| ProviderRegistryTest | 注册表与格式声明 |
| TapPolicyTest | 三种点击状态组合 |

**手测清单**
导入大文件（18.5MB / 120 章）→ 播放 → 锁屏 → 通知栏控制 → 飞行模式 20 秒恢复 → 跨章连续 → 长按/点读 → 缓存本章 → 切换供应商试听 → 备份往返。

---

## 13. 已知限制

1. **时间是估算**（196ms/字），不是真实音频总时长。
2. **高亮是估算**，长段落误差 1–2 秒；省略号会被当成句子边界。
3. **MiMo 每块都走流式**（SSE PCM，边收边播，不自动预取）；MiniMax 等其他供应商仍是整块合成 + 文件播放，保留 3 块预取。
4. **MiniMax 音色/模型可在线获取**；MiMo 音色只有官方内置 9 个，没有列表接口，模型接口为 best-effort。
5. **国产 ROM 可能清后台**，需要用户给「后台运行/自启动」权限。
6. **`clips` 表预留未用**，缓存清理靠扫描文件。
7. 多角色、情感、云同步都未实现。

---

## 14. 路线图

- P1：大文件/长时播放实测、句级高亮精度、后台保活引导。
- P2：GPT-SoVITS 本地接入（局域网 http）、多角色、情感参数。
- 明确不做：在线书源、PDF、iOS、商店上架。

---

## 15. 常见问题

**Q：MiniMax 试听没声音/报错？**
先确认端点（国内 `.cn`）、Key、`voice_id` 三项。错误提示里会带 `status_msg`，照着改即可。

**Q：Key 存哪？安全吗？**
私有 SharedPreferences，`allowBackup=false`，不进备份、不打日志。备份文件里只有端点/模型/音色。

**Q：为什么换供应商后同一段要重新合成？**
缓存 key 含供应商、模型、音色、格式，避免串音。旧文件会随缓存上限被清理。

**Q：备份能换手机吗？**
只能恢复进度/书签/规则/设置，书文件要重新导入（设计如此，避免备份文件巨大）。
