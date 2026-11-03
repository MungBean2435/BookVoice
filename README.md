# 书声 · BookVoice

> 风声雨声读书声，声声入耳。

**书声**是一个 Android 本地听书 App：导入 TXT / EPUB，用你配置的云端 TTS 连续朗读；也能像阅读器一样看正文和插图，点哪句就从哪句开始听。

整个界面用 Jetpack Compose 写成，包含动态取色、浮动导航胶囊、液态玻璃与可拖拽的 tab 镜头；书页与声音是这个 App 的两个主角。

<p align="center"><a href="https://github.com/MungBean2435/BookVoice/releases/latest"><img src="https://img.shields.io/github/v/release/MungBean2435/BookVoice?label=下载%20APK" alt="release"></a></p>

<p align="center"><img src="assets/icon/icon-512.png" width="96" alt="书声图标"></p>

- 版本：**3.0.0**（versionCode 59）
- 最低系统：Android 8.0（API 26），编译 SDK 35
- 许可：MIT

## 功能

**导入与阅读**

- TXT（UTF-8 / GBK 自动识别）与 EPUB（按 spine 顺序解析，支持插图）
- 书架、章节列表、正文连续滚动：全书一个列表，章与章之间不用退出
- 书签 tab 跨书聚合，点一条从原段继续，行尾左滑删除
- 三个 tab 各自保留滚动位置

**朗读**

- 云端 TTS 两家供应商：小米 MiMo（wav）与 MiniMax（mp3），支持多套配置
- 每个配置可存多个模型 / 音色，卡片切换；可从内置目录或账号在线目录一键导入，失败自动回落
- 首块 ≤150 字、后续 ≤400 字，跨段合并合成，请求量约为逐段合成的 1/4
- 边播边预取 2 块；章末提前预取下一章
- 断网不中断：进入等待（最多 10 分钟），网络恢复自动续播；单块失败跳过并记入「待补」
- 朗读规则：内置省略号、破折号、百分号、摄氏度等清理，支持导入自定义 JSON 规则包
- 句级高亮：按播放进度估算当前位置（误差约 1 秒）；点哪句从哪句开始听

**播放**

- 后台播放 + 通知栏 / 锁屏控制（播放暂停、上一段、下一段）
- 进度按段落记录，退出重进回到上次位置
- 音频按内容寻址缓存，重听不再请求；缓存上限可调（默认 512MB），按最久未使用清理
- 定时关闭（15 / 30 / 45 / 60 分钟）、跨块停顿可调

**界面**

- 动态取色，取不到时用墨蓝兜底
- 书架去卡片化、显示 EPUB 封面；当前段用中性底色 + 字重强调
- 底部浮动胶囊（书架 / 书签 / 设置），选中态 260ms 平移、点按 haptic
- 液态玻璃：设置 → 界面风格 → 效果，三档降级（边缘折射 / 毛玻璃 / 纯色）
- 可拖拽镜头：按住选中胶囊拖动切 tab，松手弹到最近的 tab
- 返回手势预览、页面变形转场、设置搜索

**数据**

- 备份导入导出：进度 + 书签 + 规则包 + 设置（**不含**书文件和 API Key）
- `allowBackup="false"`，应用数据不进系统备份

## 截图

仓库不包含实机截图（都是个人书架）。想快速感受界面：装上、导入一本 TXT，从书架点进去即可。

## 构建

需要 JDK 17 与 Android SDK（platform 35 / build-tools 35）。

```bash
export JAVA_HOME=/path/to/jdk17
export ANDROID_HOME=/path/to/android-sdk

./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest      # 单元测试
./gradlew assembleRelease        # app/build/outputs/apk/release/
```

国内网络建议走阿里云 Maven 镜像（`settings.gradle.kts` 已配好，Google / MavenCentral 作为兜底）。

**签名**：`release` 会读取仓库根目录的 `keystore.properties`；没有这个文件时自动回退 debug 签名，方便本地覆盖安装、保留数据。要分发请自己生成 keystore：

```properties
# keystore.properties（不要提交）
storeFile=/path/to/release.jks
storePassword=******
keyAlias=bookvoice
keyPassword=******
```

在 arm64 Linux 上构建会遇到 aapt2 无法执行的问题，见 [docs/BUILD-ON-DEVICE.md](docs/BUILD-ON-DEVICE.md)。

## 目录

```
app/src/main/java/io/github/mungbean2435/bookvoice/
├─ ui/         Compose 界面：书架、正文、播放、书签、设置、导航、液态玻璃
├─ data/       SQLite、TXT / EPUB 解析、书库仓储、设置、备份
├─ tts/        TTS 供应商（MiMo / MiniMax）、规则包、音色目录
└─ playback/   前台服务、ExoPlayer 播放内核、进度与缓存
```

包名 `io.github.mungbean2435.bookvoice`。从旧名「听书」（`io.github.mungbean2435.tingshu`）迁过来时 applicationId 变了，两者是独立 App：数据不会自动继承，在旧版里「备份导出」，到新版「备份导入」，进度 / 书签 / 设置可以搬过来（书文件要重新导入）。

## 隐私

- 书籍内容只在本机解析与存储，不上传服务器。
- 文字片段会发送到你**自己配置**的 TTS 供应商（MiniMax / 小米 MiMo），用你自己的 Key 计费，请阅读对应服务条款。
- TTS Key 存在本机私有目录（SharedPreferences），不进日志、不进备份、不进仓库。
- `android:allowBackup="false"`；release 签名文件与 `keystore.properties` 已在 `.gitignore` 中。

## 文档

- [项目文档](docs/PROJECT.md)：架构、数据模型、播放内核、供应商接入
- [版本历史](docs/CHANGELOG.md)
- [页面状态与统一返回](docs/NAV-STATE.md)
- [浮动胶囊与液态玻璃](docs/NAV-GLASS-2.6.md)
- [可拖拽 tab 镜头](docs/TAB-LENS-2.7.md)
- [规则包格式](docs/RULES.md)
- [arm64 环境构建](docs/BUILD-ON-DEVICE.md)

`docs/design/` 是开发过程中的设计笔记与提示词，`docs/` 里部分早期文档仍用旧名「听书」。

## 致谢

- [Jetpack Compose](https://developer.android.com/jetpack/compose)、[Media3](https://github.com/androidx/media)、[jsoup](https://jsoup.org/)
- 阅读页的部分交互思路来自开源阅读器 [Readest](https://github.com/readest/readest)，仓库中不含其代码

## 许可

[MIT](LICENSE) © 2026 MungBean2435

只用于导入你有权使用的内容；TTS 服务由各自的供应商提供，与本项目无关。

---

## English

**BookVoice / 书声** ("the sound of reading") is a local-first Android audiobook player. Import TXT or EPUB, and it reads the book aloud through a TTS provider you configure (Xiaomi MiMo or MiniMax), with continuous playback across chunks and chapters, background playback, resume-by-paragraph, and content-addressed audio cache. The UI is Jetpack Compose with dynamic color, a floating nav capsule, liquid-glass effects and a draggable tab lens.

- Books never leave the device; only text chunks go to the TTS provider you choose, billed to your own key.
- Build: JDK 17 + Android SDK 35, `./gradlew assembleDebug`.
- License: MIT.
