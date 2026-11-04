# 书声路线图（3.5–3.7）

> 当前版本：3.4.0（versionCode 72）。本文件是**计划**，不代表功能已经发布；每完成一项，同步更新本文件、[README](../README.md) 与[版本历史](CHANGELOG.md)。

## 目标与原则

书声的定位是「本地书库 + 自己配置的 TTS」：没有账号、没有服务端、没有统计 SDK；书只在本机解析和保存，只有待朗读的文字片段发送给用户选择的供应商。

后续版本继续遵守：

- **本地优先**：能离线完成的（阅读、缓存、系统 TTS、本地模型）不依赖云端；云端只承担用户明确选择的合成。
- **不换技术栈**：继续 Kotlin + Jetpack Compose + Media3 + 原生 SQLite；不为了功能引入整套新框架。
- **先状态后动画**：下载、缓存、睡眠、错误都先有真实状态，再做过渡；不用假进度。
- **向后兼容**：旧配置、旧书库、旧备份、旧缓存升级后不崩、不丢数据；数据库升级只增不删。
- **可验证**：每个阶段带单元测试、构建检查和手动路径清单；没有实测的能力不写成已支持。

## 阶段总览

| 阶段 | 状态 | 主题 |
|---|---|---|
| 3.5.0 | 计划中 | 听书控制台、系统 TTS、OpenAI 兼容、阅读排版 |
| 3.6.0 | 计划中 | 更多云端供应商、下载队列、缓存索引、安全与无障碍 |
| 3.7.0 | 计划中 | 本地神经 TTS、国内供应商、每本书设置、统计与 WebDAV |
| 长期 | 计划中 | 更多本地模型格式、同步完善、性能基线与无障碍 |

3.5.0 先解决「每天都会用到」的控制和离线入口；3.6.0 把云端与离线管理补齐；3.7.0 再上体积/维护成本更高的本地模型和同步。

---

## 3.5.0 听书控制台与基础 TTS

### 3.5.1 播放抽屉重排

**现状**：`ui/player/PlayerChrome.kt` 的 `PlaybackSettingsSheet` 是半屏 `ModalBottomSheet`，内容只有全书进度、5 个传输键和 2×2 控制格，下半屏大面积空白；自动跟随和点读散落在顶栏，顶栏 8 个目标过密。

**目标结构**（保留 `ModalBottomSheet`，高度 62%–68%，内容可滚动）：

1. 抽屉头：拖拽柄、标题「播放设置」、播放状态点（播放/暂停/缓冲/等待网络/错误）、当前配置胶囊。
2. 正在播放卡：封面、书名、章节名；点卡片打开目录。
3. 进度区：`本章 / 全书` 分段切换；滑块；左侧已播、右侧剩余；拖动时只预览，松手才 seek。
4. 传输键：上一章、上一段、播放/暂停、下一段、下一章；每键 48dp，图标下 10sp 标签。
5. 快捷控制 2×2：语速、音色/配置、定时、缓存/下载。每格显示当前值，点开对应子面板。
6. 更多控制：自动跟随开关、点读开关、阅读设置、朗读规则、待补段落（有失败时显示数量）。
7. 底部：停止播放（危险色，二次确认可选）。

**交互边界**：

- 缓冲/等待网络时播放键仍可暂停；错误状态显示重试入口，不自动循环重试。
- 拖动进度条期间暂停高亮跟随，松手后再恢复，避免旧位置覆盖新位置。
- 睡眠定时、缓存、下载都在抽屉里有明确状态，不靠 Toast 作为唯一反馈。
- 顶栏只保留返回、目录、书签、Aa、更多；点读、自动跟随移入抽屉；更多里放朗读规则、待补段落、下载、设置。

**验收**：720p 与 1080p、字体 1.0/1.5/2.0 下不溢出；单手可触达；TalkBack 能读出每个按钮的名称与状态；旋转后抽屉状态不丢。

### 3.5.2 系统 TTS 与离线音色

**目标**：不配置任何 Key 也能朗读；优先使用设备上已下载的离线音色。

**实现**：

- 新增 `tts/system/SystemTtsProvider`，实现现有 `TtsProvider`。
- 用 Android `TextToSpeech`：
  - `TextToSpeech(context, listener)` 异步初始化；初始化失败/引擎缺失时给出「打开系统 TTS 设置」入口，而不是崩溃。
  - `voices` 来自 `tts.voices`；`Voice.isNetworkConnectionRequired == false` 标记为「离线」，为 true 标记「需联网」。
  - 合成用 `synthesizeToFile(text, params, file, utteranceId)`（API 21 起标记 deprecated，但仍是系统提供的文件合成入口）；输出格式由引擎决定，通常为 WAV；用 `MediaMetadataRetriever` 读时长。
  - 合成统一按 1.0 语速，播放端用现有 `PlaybackParameters` 变速；这样缓存不因语速变化失效。
  - 引擎回调、`utteranceId`、超时和取消要正确配对；取消后迟到的回调不能覆盖新请求。
- 系统 TTS 不与会话云端共用合成锁：`SystemTtsProvider` 内部单独 `synthMutex`，避免系统引擎慢时堵住云端播放。
- 目录：内置常见语言/音色占位 + 系统实际 `voices`；在线目录接口返回空，`fromNetwork=false`。
- 错误映射：初始化失败、语言不可用、无离线音色、合成超时、文件为空，分别给可读提示；不把系统引擎的英文异常直接抛给用户。

**边界**：

- 不同厂商引擎对 `synthesizeToFile` 支持不一致；失败时提示「该音色不支持导出音频，请换一个系统音色」。
- 系统 TTS 的离线音色需要用户先在系统设置里下载；App 只负责检测和跳转，不代下载。
- 首版不承诺逐字时间戳；高亮仍按段级估算。
- 系统 TTS 与云端配置并列出现在供应商列表，默认不改变现有用户正在用的配置。

**验收**：飞行模式下用已下载的离线音色能连续朗读 3 章；未下载离线音色时提示明确；切换系统引擎后不需重启 App；试听、缓存本章、连续播放都可用。

### 3.5.3 OpenAI 兼容供应商

**目标**：用一个通用 Provider 覆盖 OpenAI 及兼容 `/v1/audio/speech` 的服务（SiliconFlow、Groq、LM Studio、GPT-SoVITS 网关等）。

**配置**：

- `baseUrl`：API 根，例如 `https://api.openai.com/v1`；请求 `POST {baseUrl}/audio/speech`。
- `apiKey`：可空（本地服务通常不需要）；非空时 `Authorization: Bearer <key>`。
- `model`：默认 `gpt-4o-mini-tts`；内置 `tts-1`、`tts-1-hd`。
- `voice`：内置 `alloy / echo / fable / onyx / nova / shimmer / ash / ballad / coral / sage / verse`，允许手填。
- 请求体：`{model, input, voice, response_format, speed}`；`response_format` 默认 `mp3`，可切 `wav`、`opus`、`aac`、`flac`、`pcm`。
- 目录：该协议没有标准音色列表接口，使用内置列表 + 用户手填；模型列表同理，允许手填。

**实现**：

- 新增 `tts/openai/OpenAiCompatibleProvider`；`supportsStreaming=false`。
- 响应按 `Content-Type` 判断扩展名；`audioFormat` 必须与缓存扩展名一致。
- 错误映射：401/403 → `Auth`；429 → `RateLimited`；400 且文本超长 → `TooLong`；5xx → `Server`；连接失败 → `Network`；空响应 → `Empty`。
- 超时、取消与现有 `synthMutex` 行为一致；Key 不进日志。

**验收**：用 OpenAI 官方端点和至少一个本地/第三方兼容端点各完成一次试听、缓存本章、连续朗读；错误 Key、断网、错误模型分别有明确提示。

### 3.5.4 阅读排版与常亮

**现状**：Aa 只调字号；阅读页没有行距、段距、边距、字体和背景选项；长读时屏幕会熄灭。

**新增设置**（持久化到 `Settings`，默认值等于当前设计常量）：

- 字号：现有 `fontSizeSp`，范围保持 14–28。
- 行距：1.2–2.0，默认 1.6。
- 段距：0–24dp，默认 12dp。
- 左右边距：12–40dp，默认 20dp。
- 字体：默认 / 衬线（系统 serif）；不内置字体文件，避免包体增大。
- 背景：跟随主题 / 纸白 / 暖黄 / 墨黑；文字色随背景保证对比度。
- 屏幕常亮：开关，默认开；只在阅读覆盖层可见时加 `FLAG_KEEP_SCREEN_ON`，退出立即清除。

**交互**：

- Aa 与抽屉里的「阅读设置」打开同一个 `ReadingSettingsSheet`；调整即时预览，关闭后保存。
- 排版变化不改变阅读位置；用 `LazyListState` 的当前段恢复。
- 提供「恢复默认」。

**验收**：字体 2.0 + 最大边距 + 暖黄背景不裁切文字；旋转/分屏后设置仍在；退出阅读页屏幕常亮标志被清除。

### 3.5.5 睡眠「播完本章」与淡出

- 定时选项：15 / 30 / 45 / 60 分钟、**播完本章**、自定义分钟。
- 「播完本章」在当前章最后一块的 `ENDED` 后停止，不再自动进入下一章；若用户手动跳章，重新按新章计算。
- 到点前 3 秒音量线性淡出到 0，再暂停/停止；淡出期间用户手动操作则取消淡出。
- 状态在抽屉和通知里显示剩余时间/「本章结束」；进程被杀后定时失效，重进不假装仍在计时。
- 单元测试覆盖：正常到点、本章结束、手动取消、跳章、暂停期间到点。

### 3.5.6 书架搜索、排序与继续听

- 书架顶部搜索框：按书名/作者过滤，输入防抖 200ms；空结果给「没有匹配的书」和清除按钮。
- 排序：最近阅读（默认）、最近导入、书名、作者；排序与搜索状态用 `rememberSaveable` 保存。
- 「继续听」卡：显示上次的书、章节、进度，点一下直接回到阅读页；没有进度时显示最近导入。
- 长按删除、导入入口保持不变；不新增侧滑删除，避免和滚动冲突。

### 3.5.7 顶栏减负

- 阅读顶栏从 8 个目标减到 5 个：返回、目录、书签、Aa、更多。
- 自动跟随、点读、朗读规则、待补段落、下载、设置收进更多/抽屉。
- 所有目标 48dp，带 `contentDescription`；小屏不换行、不重叠。

---

## 3.6.0 云端扩展与离线管理

### 3.6.1 更多云端供应商

统一扩展 `TtsProvider`：

```kotlin
data class ProviderField(
    val key: String,
    val label: String,
    val kind: FieldKind,      // TEXT / SECRET / CHOICE / NUMBER
    val required: Boolean = false,
    val placeholder: String = "",
    val choices: List<String> = emptyList()
)

interface TtsProvider {
    val fields: List<ProviderField> get() = emptyList()
    val bakesSpeed: Boolean get() = false
    // 现有成员不变
}
```

`TtsConfig` 增加 `extras: Map<String, String>`，JSON 缺省为空；旧配置直接可用。`fields` 驱动配置页动态表单，`extras` 存 region、endpoint 路径、response format 等；`secret=true` 的字段不进备份、不进日志。

**ElevenLabs**

- `baseUrl` 默认 `https://api.elevenlabs.io/v1`；`POST /text-to-speech/{voiceId}?output_format=mp3_44100_128`，header `xi-api-key`。
- body：`text`、`model_id`（默认 `eleven_multilingual_v2`）、`voice_settings`。
- 目录：`GET /voices`、`GET /models`；失败回落内置。
- 错误：401 → Auth，429 → RateLimited，422 → TooLong/参数。

**Azure Speech**

- 字段：`region`（必填）、`apiKey`；端点由 region 生成 `https://{region}.tts.speech.microsoft.com/cognitiveservices/v1`。
- header `Ocp-Apim-Subscription-Key`，`Content-Type: application/ssml+xml`，`X-Microsoft-OutputFormat: audio-24khz-48kbitrate-mono-mp3`。
- SSML 只做必要转义；语速统一 1.0，播放端变速。
- 目录：`GET /cognitiveservices/voices/list`；按 locale 过滤，中文优先。

**Google Cloud TTS**

- 字段：`apiKey`；`POST https://texttospeech.googleapis.com/v1/text:synthesize?key=...`。
- body：`input.text`、`voice.languageCode/name`、`audioConfig.audioEncoding=MP3`、`speakingRate=1.0`。
- 响应 `audioContent` base64；目录 `GET /v1/voices?key=...`。
- 配额/计费错误映射为 Quota/Auth。

**通用**：

- 每家加「测试连接」：拉目录 + 短合成「你好，这是听书的声音测试。」+ 显示延迟/格式；失败显示可操作原因。
- `ProviderAvatar` 为每家补品牌色/字母；未知供应商用中性色。
- 云端供应商统一 `bakesSpeed=false`，合成 1.0；只有确实由服务端烘焙语速的供应商设 `bakesSpeed=true`，并把语速加入缓存键。

### 3.6.2 下载队列与缓存索引

**问题**：目前只有「缓存本章」，没有整书下载、队列、按书清理；缓存文件按内容哈希命名，无法知道属于哪本书。

**数据（DB v5）**：

```sql
CREATE TABLE audio_files(
  cacheKey TEXT PRIMARY KEY,
  bookId TEXT,
  chapterIdx INTEGER,
  sizeBytes INTEGER NOT NULL,
  createdAt INTEGER NOT NULL,
  lastUsedAt INTEGER NOT NULL
);

CREATE TABLE download_queue(
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  bookId TEXT NOT NULL,
  chapterIdx INTEGER NOT NULL,
  state TEXT NOT NULL,          -- queued/running/paused/done/failed
  doneChunks INTEGER NOT NULL DEFAULT 0,
  totalChunks INTEGER NOT NULL DEFAULT 0,
  error TEXT,
  createdAt INTEGER NOT NULL,
  updatedAt INTEGER NOT NULL
);
```

**行为**：

- 入口：目录页「下载本书」、阅读页更多「下载下一章」、抽屉缓存格。
- 队列串行执行，复用 `SpeechPlayer.cacheChapter` 的合成路径；支持暂停/继续/取消、仅 Wi-Fi、存储上限。
- 前台服务 + 通知显示总进度/暂停/取消；进程被杀后队列标记暂停，重进不假装完成。
- 删除书或「删除本书音频」按索引删文件；索引里 `bookId` 为空的旧缓存归入「其他缓存」，只按最久未使用清理。
- 旧缓存首次扫描：只登记 `cacheKey/大小/最近使用`，`bookId/chapterIdx` 未知；新合成写入时补全索引。
- 存储上限沿用现有 512MB 默认值；下载前检查剩余空间，不足时明确提示。

**验收**：下载 10 章可暂停/继续/取消；杀进程后重启不重复下载；删除书后对应文件被清理；旧缓存不导致崩溃。

### 3.6.3 API Key 加密

- 新增 `data/security/SecretStore`：Android Keystore 生成 AES/GCM 密钥，密文与 IV 存 SharedPreferences；读取失败返回空并提示重新填写。
- 首次读取旧明文 Key 时迁移：写密文、删明文；迁移失败保留明文并记录诊断，不静默丢 Key。
- 备份、诊断、日志、崩溃信息都不含 Key；`allowBackup=false` 继续保留。
- 卸载/清除数据后 Key 不可恢复，设置页说明。

### 3.6.4 无障碍与大字体

- 所有图标按钮有 `contentDescription`；播放/暂停、上一章/下一章、定时、缓存状态可被 TalkBack 读出。
- 触控目标 ≥48dp；字体 2.0 时抽屉、顶栏、传输键不重叠。
- 支持系统「移除动画」：关闭页面变形/液态玻璃动效，保留状态变化。
- 对比度检查：暖黄/纸白背景下正文与次要文字达到 WCAG AA；不依赖颜色单独表达状态（错误同时有图标/文本）。
- 手势操作都有非手势替代入口（顶栏/抽屉/菜单）。

### 3.6.5 备份增强

- 备份增加：阅读排版、非 secret 的 `extras`、下载清单、每本书设置（若 3.7 已实现）。
- 仍不包含：书文件、API Key、诊断日志、缓存音频。
- 备份加 `schemaVersion`；旧备份导入时缺字段用默认值；未知字段忽略。
- 导入前显示将覆盖/合并的范围；合并逻辑保持现有「只更新本机已有书」。

### 3.6.6 通知栏章节控制

- Media3 `MediaSession` 增加「上一章」「下一章」自定义 action；通知栏/锁屏/蓝牙按键可用。
- 章边界切换时更新通知标题为「书名 · 章节名」；缓冲/等待网络显示对应状态。
- 与现有播放/暂停、上一段/下一段不冲突；没有上一章/下一章时按钮禁用。

### 3.6.7 章节内搜索

- 阅读页更多 →「本章搜索」；输入关键词，列出当前章匹配段落（前后各 20 字），点结果跳到该段并高亮 2 秒。
- 只在当前章内搜索，不建全文索引；空结果、大小写/全半角、标点差异给说明。
- 搜索状态不写入阅读进度。

---

## 3.7.0 本地与同步

### 3.7.1 本地神经 TTS 模型包

**目标**：无网络、无 Key 的离线朗读；不把大模型塞进默认 APK。

**实现**：

- 新增可选模块 `localtts`，集成 sherpa-onnx（Android AAR + native libs）；默认 release 不包含，构建时用 `-PlocalTts=true` 生成 `withLocalTts` 变体。
- 首版只支持 arm64-v8a；32 位设备显示「不支持」而不是崩溃。
- 模型包通过 SAF 导入 zip，manifest 示例：

```json
{
  "id": "vits-melo-zh-en",
  "name": "MeloTTS 中英",
  "language": "zh-CN",
  "sampleRate": 22050,
  "model": "model.onnx",
  "tokens": "tokens.txt",
  "license": "CC-BY-NC-4.0",
  "sha256": "…"
}
```

- 校验：zip 路径穿越、文件大小上限、SHA-256、必需文件、采样率；失败不落盘。
- 解压到 `filesDir/tts/models/{id}/`；设置页显示名称、语言、大小、许可证、删除按钮。
- `LocalTtsProvider` 合成 PCM/WAV 写入现有缓存；缓存键包含模型 id + 版本；`bakesSpeed=false`，播放端变速。
- 首次加载模型在 IO 线程，显示「正在加载模型…」；内存不足/模型损坏给明确错误。
- 不在仓库内置模型，不代下模型；许可证由用户确认。

**风险**：native 体积、ABI、模型许可、低端机速度。若首版性能不达标，保持「实验」标记，不阻塞其他功能。

### 3.7.2 火山 / 阿里 / 腾讯供应商

- 每家单独实现签名（HMAC/Token/日期头）与请求体，不复用 OpenAI 兼容层。
- 字段：AppId/Token/Cluster/Region 等，按各家文档放入 `extras`；密钥字段 `secret=true`。
- 先做「试听 + 单块合成」，再接入流式（若供应商支持）；错误码映射到现有 `TtsException`。
- 目录接口能拉则拉，不能拉用内置常用音色；文档里标注各家的计费/并发限制。
- 验收以真实账号完成一次试听、缓存、连续朗读；没有账号时只做静态/单测，不声称已验证。

### 3.7.3 每本书独立语速/音色

- `Settings` 增加 `bookOverrides: Map<bookId, BookVoiceOverride(speed, configId, voice)>`，JSON 持久化；不建新表。
- 打开书时优先用 override；没有则用全局设置。抽屉里改语速/音色只影响当前书，提供「应用到所有书」。
- 删除书时清理 override；备份包含非 secret 部分。

### 3.7.4 阅读统计

- 本地表 `reading_stats(date, bookId, listenedMs, readChars)`；只统计前台/播放中的真实时长，暂停不计。
- 设置页「阅读统计」：今日/本周/累计时长、按书 Top 5；可清空。
- 不上传、不接第三方统计；导出仅文本摘要。

### 3.7.5 WebDAV 同步

- 设置 → 备份与同步 → WebDAV：服务器、用户名、密码（Keystore 加密）、远程目录。
- 先做「手动上传/下载备份」，不做自动双向合并；下载前显示远端时间/大小，要求确认覆盖。
- 冲突策略：以用户选择为准；默认保留本地，先导出本地副本再覆盖。
- 失败分类：认证、网络、证书、权限、空间不足；不吞错。
- 同步内容与现有备份一致，不含书文件/Key/缓存。

---

## 数据与接口变更汇总

| 变更 | 位置 | 兼容策略 |
|---|---|---|
| `TtsConfig.extras` | `data/settings/TtsConfig.kt` | 缺省空 map；旧 JSON 直接读 |
| `TtsProvider.fields` / `bakesSpeed` | `tts/TtsProvider.kt` | 默认空/false；现有 Provider 不改也能编译 |
| 阅读排版字段 | `data/settings/Settings.kt` | 缺省用当前常量 |
| DB v5 `audio_files` / `download_queue` | `data/db/Db.kt` | `onUpgrade` 建表；旧缓存扫描登记 |
| 缓存键含 speed | `playback/AudioCache` | 仅 `bakesSpeed=true` 的供应商加；旧缓存仍可命中 |
| Key 密文 | `data/security/SecretStore` | 首次读取迁移明文；失败保留并提示 |
| 每本书设置 | `Settings.bookOverrides` | 缺省空；删除书时清理 |

## 失败与边界

- **没有网络**：系统 TTS/本地模型仍可用；云端进入等待，不跳段；下载队列暂停并提示。
- **引擎/模型不可用**：明确提示和替代入口，不崩溃、不静默回落到其他供应商。
- **Key 错/余额不足**：Auth/Quota 停止整段并提示，不无限重试。
- **下载中断**：队列状态持久化；重启后标记暂停，不假装完成。
- **缓存索引缺失**：旧文件归入「其他缓存」，不误删。
- **Keystore 失效**：要求重新输入 Key，不丢书库/进度。
- **备份版本不一致**：缺字段用默认值，未知字段忽略，不覆盖书文件。

## 测试与验收

每个阶段至少：

1. `JAVA_HOME=... ./gradlew :app:testDebugUnitTest --offline`
2. `JAVA_HOME=... ./gradlew :app:assembleDebug --offline`
3. 手动路径清单（真机或模拟器）：
   - 系统 TTS：飞行模式、离线音色、引擎切换、试听、连续 3 章。
   - 抽屉：播放/暂停/缓冲/错误/无书/单章/最后一章、字体 2.0、横屏。
   - 下载：暂停/继续/取消、杀进程、存储不足、删除书。
   - 阅读：排版极值、旋转、常亮、Aa 与抽屉同步。
   - 供应商：测试连接、错误 Key、断网、超时。
   - 无障碍：TalkBack 顺序、48dp、减少动画。
4. 发布前更新 README、本文件、CHANGELOG；不把计划项写成已支持。

## 依赖与风险

- **Android 系统 TTS**：`synthesizeToFile` 已 deprecated，且不同引擎行为不一致；需要真机矩阵，失败时给替代方案。
- **本地模型**：native 体积、ABI、模型许可和低端机性能；默认不打包，先实验。
- **国内供应商**：签名与文档差异大，必须用真实账号验证；没有账号时只能做静态实现。
- **下载队列**：后台限制/省电策略可能杀进程；用前台服务 + 持久化状态，不承诺后台无限运行。
- **WebDAV**：服务器实现差异、TLS 证书、冲突处理；先手动同步，避免自动覆盖。
- **Key 加密迁移**：迁移失败可能让用户重填 Key；提供明确提示和诊断，不静默丢数据。

## 版本与发布

- 功能阶段完成后升 minor；修复升 patch。
- 每个版本更新 `docs/CHANGELOG.md`、README 路线图状态和截图（若界面变化）。
- 本地模型功能单独构建变体，不在默认 APK 中增加 native 体积。
- 发布前确认旧配置、旧书库、旧备份可升级。
