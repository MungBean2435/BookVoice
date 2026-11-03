# readest 阅读界面 · 复刻规格

来源：`readest 上游快照`（Tauri + Next.js，阅读内核为 `packages/foliate-js`）。行号以该快照为准。

## 1 它是怎么渲染的

| 层 | 实现 | 出处 |
| --- | --- | --- |
| 正文 | `foliate-js` 在 iframe 里用 CSS 多栏分页，`column-width` / `column-gap` 由排版参数算出 | `packages/foliate-js/paginator.js:982` `columnize()`、`:919` `scrolled()` |
| 章节定位 | CFI（`epubcfi.js`），翻页 = 改栏位偏移 | `packages/foliate-js/epubcfi.js` |
| 覆盖层 | React 组件叠在 iframe 上：页眉 `HeaderBar.tsx`、页脚 `ProgressBar.tsx`、点击翻页 `usePagination.ts` | `src/app/reader/components/` |
| 点击事件 | iframe 内 tap → `iframe-single-click` 事件 → 外层按横向位置判翻页或切 chrome | `hooks/useIframeEvents.ts`、`utils/iframeEventHandlers.ts:74-84` |

Compose 没有等价的多栏分页，复刻走「整章测量 + 按行切页」，见 `readest-compose/ChapterPaginator.kt`。

## 2 数值（默认值）

| 项 | 值 | 出处 |
| --- | --- | --- |
| 默认字号 | 16px | `services/constants.ts:365` |
| 字号缩放范围 | 8%–120%，步进 1 | `constants.ts:1023-1025`、`413` |
| 行高 | 1.4；中文 1.6 | `constants.ts:415`、`462` |
| 段距 | 0.6em；中文 1em | `constants.ts:414`、`461` |
| 首行缩进 | 0；中文 2em | `constants.ts:418`、`460` |
| 两端对齐 | 桌面开、移动关；中文开 | `constants.ts:419`、`451`、`459` |
| 页边距 | 上 44 / 下 44 / 左 16 / 右 16 px | `constants.ts:370-374` |
| 紧凑边距 | 四边 16px | `constants.ts:375-378` |
| 栏间距 | 5% | `constants.ts:379` |
| 页眉页脚字号 | 12px；墨水屏 14px | `utils/headerFooterStyle.ts:21` |
| 点击翻页边缘区 | 屏宽 18% | `utils/turnGestureArena.ts:22` |

## 3 主题

`src/styles/themes.ts:143-232` 共 11 套：default / gray / sepia / grass / cherry / sky / solarized / gruvbox / nord / contrast / sunset，每套给 `fg`、`bg`、`primary` 三个输入，其余色阶由 `generateLightPalette` / `generateDarkPalette` 派生。色值原样抄进了 `readest-compose/ReaderTheme.kt`。

## 4 交互

- 左/右边缘 18% 起始的点击或滑动翻页，方向随 `rtl` 反转（`turnGestureArena.ts`、`usePagination.ts:259`）。
- 中间点击切换页眉页脚；`HeaderBar` 在桌面端靠 hover 显示。
- 页码/百分比来自 `progress`（`useBookProgress`），页脚在 `ProgressBar.tsx:325-373` 按 chrome 字号画。
- 翻页动画受 `animated` 开关控制（`constants.ts:452` 移动端默认开）。

## 5 这份复刻不覆盖

竖排（`vertical`/`writingMode`）、双栏（`maxColumnCount`）、断词（`hyphenation`）、图片跨页、CFI 精确续读、脚注弹层、批注层。这些在 readest 里都由 iframe + CSS 承担，Compose 需要另写。
