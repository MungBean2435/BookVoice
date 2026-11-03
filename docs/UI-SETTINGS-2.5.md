# 设置页「参考风格」重构（2.5.0）

> 2.5.1 更新：设置已并入 tab 场景，子页返回/切 tab 的状态保留与统一返回见 [NAV-STATE.md](NAV-STATE.md)；本文的「栈内页面、不是底部 Tab」为 2.5.0 的历史设计。

> 目标：把设置页做成参考图的视觉语言——大标题 + 圆形外观按钮 + 搜索框 + 白色大圆角卡片 + 彩色方形图标行 + 标题/说明/箭头；同时保留本项目全部设置能力。
> 代码：`bookvoice`；APK：`dist/听书-2.5.0.apk`。

---

## 1. 参考图拆解

**照搬**
- 大标题与圆形按钮的顶部关系（本项目因是栈内页面，额外保留左上返回）。
- 搜索框：灰底、圆角、放大镜、占位文案。
- 白色大圆角卡片承载行；卡片之间留白；无阴影。
- 每行：40dp 彩色圆角图标 + 标题 + 说明 + 右侧箭头；1px 分隔线从文字起点开始。
- 设置分「语音/数据/支持」三组，组内用图标和说明区分。

**适配**
- 参考图底色偏冷灰，本项目保持暖纸底：Light 页面 `surfaceContainerLow`、卡片 `surfaceContainerLowest`（白）；Dark 页面 `background`、卡片 `surfaceContainerLow`。
- 图标色收敛为 5 个装饰色（不是状态语义色），白字对比度均 ≥5.19。
- 保留返回键：Settings 是 `AppRoot` 栈内页面，不是底部 Tab。
- 搜索、外观三态、子页是真实功能，不做装饰性假入口。

**不照搬**
- 底部导航栏、迷你播放器：本项目没有对应外壳，硬加会破坏手写返回栈与播放页。
- 第三方平台登录、语言与地区：本项目没有账号/国际化体系，不造空入口。

---

## 2. 视觉规格

| 项 | 值 |
|---|---|
| 页面底色 | Light `#F4F3EF`；Dark `#101416` |
| 卡片 | Light `#FFFFFF`；Dark `#161A1C`；圆角 24dp；无阴影 |
| 搜索框 | `surfaceContainerHigh`；最小高 56dp；圆角 16dp；左右 16dp |
| 标题 | 28/36 SemiBold；返回行 48dp；外观按钮 48dp 圆形 |
| 行 | 最小高 80dp；左右 16dp；图标 40dp/圆角 12dp；图文间距 16dp；标题 16/24；说明 12/18，最多 3 行；箭头 24dp |
| 分隔线 | 1px `outlineVariant`，从 72dp 开始（图标后文字起点） |
| 装饰色 | 靛蓝 `#5B4BDB`、湖蓝 `#1D5FD1`、青绿 `#0F766E`、琥珀 `#A15C00`、玫红 `#B3263E`；白色 glyph 对比度 6.04 / 5.82 / 5.47 / 5.19 / 6.43 |
| 玻璃/渐变 | 0；仅 M3 涟漪与 DropdownMenu 默认效果 |
| 字体缩放 | 标题/行高 `heightIn` 可增长；说明最多 3 行；图标固定 40dp |

---

## 3. 信息架构

**首页 3 张卡**

1. **语音与播放**
   - TTS 配置 → 现有 TTS 配置页
   - 播放设置 → 新子页（语速 / 段落间停顿）
   - 朗读规则 → 现有规则页
2. **数据**
   - 存储与缓存 → 新子页（已用/文件数、上限、清理）
   - 导出备份 → 现有 SAF 导出
   - 导入备份 → 现有 SAF 导入
3. **支持**
   - 诊断信息 → 新子页（导入耗时、首声、命中）
   - 后台保活 → 现有应用系统设置 Intent
   - 关于听书 → About 弹窗（版本、本地优先、MIT）

**搜索**：`BasicTextField`，按标题/说明/关键词过滤；关键词覆盖 TTS、音色、语速、停顿、缓存、清理、备份、诊断、后台、版本等。无结果时显示空态。

**外观**：圆形按钮打开 3 项菜单（跟随系统 / 浅色 / 深色），带选中勾；`跟随系统` 模式按钮右上角有主色小圆点。浅色下显示月亮、深色下显示太阳（动作导向，贴合参考图）。

---

## 4. 数据与主题

- 新增 `ThemeMode` 枚举：`FollowSystem/Light/Dark`，`prefValue` 为 `system/light/dark`。
- `TtsSettings` 增加 `themeMode`，默认 `FollowSystem`；`Settings.read/update` 读写。
- `Settings` 暴露只包含外观的 `StateFlow<ThemeMode>`，`MainActivity` 只订阅它，避免语速变化重组整棵树。
- 备份导出/导入 `themeMode`；旧备份缺字段回落 `FollowSystem`。
- `TingshuTheme` 增加 `LocalTingshuDarkTheme`；设置页据此选择页面/卡片色。
- `MainActivity` 根据三态计算实际明暗；`WindowCompat` 同步状态栏/导航栏图标；启动窗口底色按实际模式设置。
- 不新增依赖；不改数据库、网络、权限、播放内核。

---

## 5. 文件变更

| 文件 | 变更 |
|---|---|
| `ui/settings/SettingsScreen.kt` | 重写：大标题/搜索/3 卡/9 行/About；新增 3 个子页回调 |
| `ui/settings/SettingsComponents.kt` | 重写：Header、SearchBar、Card、Row、IconTile、ThemeModeButton、Slider、Diagnostic、Note |
| `ui/settings/SettingsGlyphs.kt` | 新增：语音/播放/规则/存储/导出/导入/诊断/保活/关于/清理/太阳/月亮 Canvas 图标 |
| `ui/settings/SettingsDetailScreens.kt` | 新增：播放设置、存储与缓存、诊断信息子页 |
| `ui/settings/SettingsSearch.kt` | 新增：搜索匹配纯函数 |
| `ui/theme/Color.kt` | 新增 5 个装饰色 `SettingsTone` |
| `ui/theme/Theme.kt` | 新增 `LocalTingshuDarkTheme` |
| `ui/theme/Type.kt` | 新增 `headlineMedium 28/36` |
| `data/settings/ThemeMode.kt` | 新增枚举 |
| `data/settings/Settings.kt` | 新增 `themeMode` 读写与外观 StateFlow |
| `data/backup/BackupManager.kt` | 备份增加 `themeMode` |
| `MainActivity.kt` | 外观订阅、系统栏、启动底色 |
| `ui/AppRoot.kt` | 新增播放/存储/诊断路由与设置回调 |
| `app/build.gradle.kts` | 2.5.0 / versionCode 39 |
| `README.md`、`docs/PROJECT.md`、`docs/CHANGELOG.md` | 同步 |

---

## 6. 删除/合并影响表

| # | 原位置 | 动作 | 理由 | 业务风险 | 还原方式 |
|---|---|---|---|---|---|
| 1 | 设置 `TopAppBar` | 大标题 + 返回行 + 外观按钮 | 参考图用大标题和圆形按钮 | 返回键位置变化 | 恢复 `TopAppBar`，删 `SettingsPageHeader` |
| 2 | 6 个 `SectionHeader` | 合并为 3 张卡 | 参考图用卡片分组 | 组名不再显式 | 恢复 SectionHeader 与旧分组 |
| 3 | 语速/停顿 Slider | 移到「播放设置」子页 | 首页只做导航 | 调整多一步 | 两行 Slider 放回首页 |
| 4 | 缓存已用/上限/清理 | 移到「存储与缓存」子页 | 首页只做导航；清理加确认 | 清理多一步但更安全 | 三行放回首页 |
| 5 | 诊断 5 行 | 移到「诊断信息」子页 | 首页信息过密 | 多一步 | 恢复首页诊断卡 |
| 6 | 备份说明 Text | 合并进两行说明 | 参考图行内说明 | 文字略压缩 | 恢复说明段落 |
| 7 | 保活说明 | 合并进行说明 | 同上 | 说明略短 | 恢复原说明 |
| 8 | 底部滑块说明 | 移到子页 | 首页只留导航 | 无 | 恢复首页 footer |
| 9 | TTS 行 `ProviderAvatar` | 换分类图标，供应商名留在说明 | 参考图统一彩色方形图标 | 供应商视觉标识变弱 | 恢复 `ProviderAvatar` |
| 10 | 清理缓存直接执行 | 加确认弹窗 | 误触会删缓存 | 多一次确认 | 去掉确认，直接 clear |
| 11 | 无外观设置 | 新增三态外观 + 持久化 | 参考图月亮按钮；原项目只跟随系统 | 备份/系统栏新逻辑 | 删除按钮，强制跟随系统 |
| 12 | 无搜索 | 新增搜索过滤 | 参考图搜索；9 行可快速定位 | 无结果态 | 删除搜索框与过滤 |
| 13 | 无子页 | 新增播放/存储/诊断路由 | 参考图行→子页 | 返回栈 | 删除路由，回单页 |
| 14 | 无 About | 新增 About 弹窗 | 参考图有「关于」 | 版本读取失败 | 删除行/弹窗 |
| 15 | 版本 2.4.0 | 升 2.5.0 / 39 | 新增功能与界面 | 覆盖安装 | 回退版本号 |

**保留**：网络接口、权限、TTS 配置、备份语义、数据库、既有 `contentDescription`、播放逻辑、书签/待补/定时全部不动。

---

## 7. 验证

| 命令 | 结果 |
|---|---|
| `lintDebug` | 0 error；2 个既有 warning（`allowBackup`、`SpeechPlayer` 静态 Context） |
| `testDebugUnitTest` | 全绿；`SettingsSearchTest` 4/4；`ThemeModeTest` 3/3；`ThemeContrastTest` 3/3（明暗各 11 组 + 5 个图标色） |
| `assembleDebug` / `assembleRelease` | 成功；`versionCode 39` / `versionName 2.5.0` |
| APK | 8.6 MB，debug 签名，可覆盖安装 |

**真机验收（本机无 adb/模拟器）**
- [ ] 浅色 / 深色 / 跟随系统三态：页面、卡片、图标、系统栏、重启后保持
- [ ] 搜索：TTS、音色、语速、缓存、清理、备份、诊断、后台、版本；空结果；清除
- [ ] 三张卡与 9 行；三个子页进入/返回；清理缓存确认
- [ ] 备份导出/导入含外观；旧备份兼容
- [ ] 200% 字体：标题、搜索框、行、子页 Slider、About 弹窗
- [ ] TalkBack：返回、外观、搜索、每行、清理确认、About
- [ ] 动态取色换壁纸；图标色不随壁纸变

---

## 8. 已知限制

- 本机无 adb/模拟器，未做像素级截图验收；编译、单测、lint 已完成。
- 参考图底部导航/迷你播放器未复制，原因见 §1。
- 外观按钮是「菜单选择」而非单击直接切换，以支持「跟随系统」三态。
- 启动窗口底色使用品牌色；动态取色下首次启动可能有一帧品牌底色，这是启动窗口限制。
