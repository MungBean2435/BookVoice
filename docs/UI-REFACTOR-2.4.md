# 听书 2.4.0 界面重构报告

> 产物：`听书-2.4.0.apk`（release，同 debug 签名可覆盖安装）
> 源码：`bookvoice`
> 验证：`lintDebug` 0 error；`testDebugUnitTest` 全绿（含 ThemeContrastTest 2/2）；`assembleDebug` / `assembleRelease` 成功。

## ① 诊断（8 条）

1. `Theme.kt` 只覆盖 9 个颜色角色，Slider 未激活轨道等仍是 M3 默认紫调，且未接动态取色。
2. `PlayerScreen.kt` 非当前段用 alpha 0.5/0.35/0.28 衰减，实测对比度 3.29/2.17/1.83（深色 4.42/2.75/2.20），低于 4.5:1。
3. `PlayerScreen.kt` 顶栏在 48dp 高的一行塞了返回/封面/书名/章节/A-/A+/点读/更多 8 个目标，书名被挤成「将…」。
4. `PlayerScreen.kt` 五个传输键是 « ‹ › » 文本符号，没有 contentDescription；播放键还用 `onSurface` 黑底，主操作没有主色。
5. `PlayerScreen.kt` 进度按全书字数与 196ms/字估算，却紧贴「第三章」标题，右侧 `-35:39:21` 容易被误读为「本章还剩 35 小时」。
6. `PlayerScreen.kt` 四个 `CompactPill` 等宽同权，音色胶囊只显示 `female-sha…` 和 `MiniMax`，连「音色」这个语义都丢了。
7. `ShelfScreen.kt` 每本书都是同一张 `Card`，导入 FAB 是浅色 tonal（像禁用态），且完全忽略 `Book.coverPath`，EPUB 封面看不到。
8. `SettingsScreen.kt` 三个 Slider 的标签、数值、轨道彼此分离，清理缓存/备份/规则/保活是散落的 `OutlinedButton`，诊断是 5 行散文本，层级只靠 `HorizontalDivider`。

### 截图与源码差异（以源码为准）

| 截图 | 源码事实 | 处理 |
|---|---|---|
| 书架 4 张卡片、看不到封面 | `ShelfScreen.BookRow` 用 `Card`，且完全忽略 `Book.coverPath` | 去卡片、补封面，格式移入元信息 |
| 无顶栏/无控制栏的阅读页 | 同一 `PlayerScreen` 的 `chromeVisible=false` 状态，不是第二个页面 | 不新增沉浸页，保留单击显隐 |
| 右侧 `-35:39:21` | `PlaybackMath` 按 196ms/字估算，`fraction` 也按全书字数计算 | 标注「全书进度 · 时长为估算」 |
| 设置只显示到「朗读规则」 | 源码下方还有「备份 / 诊断 / 后台保活」 | 按源码补全，不按截图删除 |
| 设置头像紫色 | `TtsConfigUi.ProviderAvatar` 硬编码紫/蓝/绿 | 改主题角色，随动态取色 |

## ② 方案概述

以「纸感阅读器」为方向：暖白/墨黑底、单一墨蓝主色，无阴影、无玻璃、无渐变；用字阶、留白、1px 分隔线与 `surfaceContainer` 色阶分层。书架去卡片化并补封面；听书页收顶栏、突出当前段与主色播放键；设置改分组列表、数值右对齐。动态取色优先，Android 11 以下回退固定色；语义色只留 primary/error。取舍：不做实时模糊与封面取色，换中低端机稳定帧率与 0 新依赖。

## ③ 变更影响表

| # | 原位置 | 动作 | 理由 | 业务风险 | 还原方式 |
|---|---|---|---|---|---|
| 0 | `Theme.kt` 旧 Blue/BlueLight 9 角色 | 换完整品牌色 + 动态取色 | 旧色只覆盖部分角色，Slider 等露出 M3 默认紫调 | 全局观感变化，所有页面 | 恢复旧 `lightColorScheme`/`darkColorScheme` 参数 |
| 1 | Shelf `TopAppBar` | 换自定义标题栏 | 固定 64dp 在 200% 字体下会裁切，需要大标题+本数 | 系统栏 inset 需手写 | 换回 `TopAppBar(title=Text("书架"))`，删 `ShelfHeader` |
| 2 | Shelf `Card` 行 | 删除卡片 | 四张同权卡片无主次，阴影/圆角制造噪音 | 行点击与长按范围变化 | `BookRow` 外层恢复 `Card` 与 10dp 间距 |
| 2b | Shelf 封面格式块（EPUB 文字） | 改为封面图，格式并入元信息 | 原格式块占位且完全看不到 EPUB 封面 | 无信息丢失（格式仍在 meta） | 去掉 `FileImage`，恢复格式块 |
| 3 | Shelf 浅色 FAB | 改 primary 实心、0 elevation | 浅蓝 tonal 像禁用态 | 导入入口位置不变 | 改回默认 `ExtendedFloatingActionButton` 参数 |
| 4 | Player 顶栏 `A-`/`A+`/`点读` | 合并进阅读设置 Sheet | 8 个目标挤成一行，书名被截 | 调字号由 1 tap 变 2 taps | Sheet 逻辑保留；将三个按钮加回 `PlayerTopBar` |
| 5 | Player 封面点击进目录 | 合并为书块整块点击 | 36×48 封面目标不明确且无描述 | 点封面仍可（含在书块内） | `CoverThumb` 单独加 `clickable` |
| 6 | Player 非当前段 alpha 0.5/0.35/0.28 | 删除 | 对比度不足 4.5:1 | 非当前段与当前段区分减弱 | `FocusParagraph` 加回 `alpha` 参数与 distance 计算 |
| 7 | Player « ‹ › » 文本键 | 替换为 Canvas 图标 | 无 contentDescription、字形廉价 | 自绘路径需对齐视觉 | 恢复 `TransportButton("«")` 文本实现 |
| 8 | Player 黑色播放键 | 改 primary 实心 | 主操作没有主色 | 无 | 改回 `background(onSurface)` |
| 9 | Player 四个灰底胶囊 | 删除底色，改标签+值 | 四块灰底与播放键抢焦点；音色语义丢失 | 可点击性变弱 | `CompactPill` 加回 `surfaceVariant` 背景 |
| 10 | Player 进度无「全书」语义 | 增加说明行 | 全书字数被误读为本章剩余 | 多一行 12sp 文案 | 删除说明行，恢复原左右时间 |
| 11 | Player `navigationBarsPadding()` | 删除 | Scaffold 已含底部 inset，出现双倍留白 | 个别 ROM 手势条差异 | 在底部 Column 加回该 Modifier |
| 12 | Settings 平铺 + 分割线 | 合并为 6 个分组 | 层级只靠 `HorizontalDivider` | 滚动长度略增 | 去掉 `SettingsGroup` 包裹，恢复原 Column 顺序 |
| 12b | Settings 三个 Slider 的独立标签 + 轨道 | 合并为 Slider 行（标题/数值/轨道同组） | 标签与数值分离，扫读成本高 | 无 | 拆回原 label Text + Slider |
| 13 | Settings 5 个 `OutlinedButton` | 改为整行点击 | 描边按钮散落、抢层级 | 动作可发现性略降 | 行内放回 `OutlinedButton` |
| 14 | Settings 诊断 5 行散文本 | 合并为紧凑标签值 | 密度与可扫读性 | 换行表现需验证 | 恢复 5 个 `Text` |
| 15 | `TtsConfigUi` 硬编码供应商色 | 改主题角色 | 动态取色下紫/蓝/绿冲突，语义色超 5 | 两家供应商区分变弱 | 恢复原 `Color(0x...)` 色值 |
| 16 | Player 未使用 import | 删除 | 死代码/警告 | 无 | 无需还原（import 不影响行为） |

**保留项**：网络接口、权限、TTS 配置、备份字段、`Metrics` 字段、数据库、既有 `contentDescription`（设置/书架/返回/更多等）全部未改。


## ④ 代码（按文件分组，完整可粘贴）

> 全部文件已在当前工作区编译通过（`lintDebug` 0 error、`assembleRelease` 成功）；直接覆盖同名文件即可。新增文件需一起加入工程。

### `ui/theme/Color.kt`（新增）

```kotlin
package io.github.mungbean2435.bookvoice.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * 固定品牌色兜底（Android 11 及以下，或用户关闭动态取色时使用）。
 * 墨蓝 + 暖白纸感；所有正文组合均通过 WCAG 4.5:1（见 ThemeContrastTest）。
 */
internal val BrandLightColors = lightColorScheme(
    primary = Color(0xFF1C4E7A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD5E4F2),
    onPrimaryContainer = Color(0xFF071B2B),
    inversePrimary = Color(0xFF9CCBF8),
    secondary = Color(0xFF4F6070),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD3E5F5),
    onSecondaryContainer = Color(0xFF0B1D2A),
    tertiary = Color(0xFF3F6358),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFC2E8DA),
    onTertiaryContainer = Color(0xFF06251C),
    background = Color(0xFFFAF9F6),
    onBackground = Color(0xFF191C1E),
    surface = Color(0xFFFAF9F6),
    onSurface = Color(0xFF191C1E),
    surfaceVariant = Color(0xFFDDE3E8),
    onSurfaceVariant = Color(0xFF41484D),
    surfaceTint = Color(0xFF1C4E7A),
    inverseSurface = Color(0xFF2E3133),
    inverseOnSurface = Color(0xFFEFF1F3),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    outline = Color(0xFF71787D),
    outlineVariant = Color(0xFFC1C7CC),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFAF9F6),
    surfaceDim = Color(0xFFDAD9D5),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF4F3EF),
    surfaceContainer = Color(0xFFEEEDE9),
    surfaceContainerHigh = Color(0xFFE8E7E3),
    surfaceContainerHighest = Color(0xFFE2E1DD)
)

internal val BrandDarkColors = darkColorScheme(
    primary = Color(0xFF9CCBF8),
    onPrimary = Color(0xFF003355),
    primaryContainer = Color(0xFF17466E),
    onPrimaryContainer = Color(0xFFD5E4F2),
    inversePrimary = Color(0xFF1C4E7A),
    secondary = Color(0xFFB7C8D9),
    onSecondary = Color(0xFF22323F),
    secondaryContainer = Color(0xFF384957),
    onSecondaryContainer = Color(0xFFD3E5F5),
    tertiary = Color(0xFFA6D5C4),
    onTertiary = Color(0xFF0B372C),
    tertiaryContainer = Color(0xFF274E43),
    onTertiaryContainer = Color(0xFFC2E8DA),
    background = Color(0xFF101416),
    onBackground = Color(0xFFE0E3E5),
    surface = Color(0xFF101416),
    onSurface = Color(0xFFE0E3E5),
    surfaceVariant = Color(0xFF3F484F),
    onSurfaceVariant = Color(0xFFBFC8CE),
    surfaceTint = Color(0xFF9CCBF8),
    inverseSurface = Color(0xFFE0E3E5),
    inverseOnSurface = Color(0xFF2E3133),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
    outline = Color(0xFF899399),
    outlineVariant = Color(0xFF3F484F),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF363A3C),
    surfaceDim = Color(0xFF101416),
    surfaceContainerLowest = Color(0xFF0B0F11),
    surfaceContainerLow = Color(0xFF161A1C),
    surfaceContainer = Color(0xFF1A1E20),
    surfaceContainerHigh = Color(0xFF24282A),
    surfaceContainerHighest = Color(0xFF2E3235)
)

```
### `ui/theme/Type.kt`（新增）

```kotlin
package io.github.mungbean2435.bookvoice.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 中文优先的字阶：标题收紧、行高放宽。
 * 阅读正文不使用这里的 bodyLarge，仍然由设置里的 fontSizeSp 决定。
 */
internal val TingshuTypography = Typography(
    headlineSmall = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 28.sp
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    titleSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp
    )
)

```
### `ui/theme/Dimens.kt`（新增）

```kotlin
package io.github.mungbean2435.bookvoice.ui.theme

import androidx.compose.ui.unit.dp

/** 8dp 栅格：所有布局间距取这些 token 的整数倍。 */
internal object Spacing {
    val xs = 8.dp
    val sm = 16.dp
    val md = 24.dp
    val lg = 32.dp
}

```
### `ui/theme/Theme.kt`（替换）

```kotlin
package io.github.mungbean2435.bookvoice.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val TingshuShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

/**
 * 动态取色优先（Android 12+ / API 31+），Android 11 及以下回退固定墨蓝。
 * 不读取壁纸做二次取色，避免额外的 Bitmap 解码与启动耗时。
 */
@Composable
fun TingshuTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> BrandDarkColors
        else -> BrandLightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = TingshuTypography,
        shapes = TingshuShapes,
        content = content
    )
}

```
### `ui/components/FileImage.kt`（修改）

```kotlin
package io.github.mungbean2435.bookvoice.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 最小图片加载：按目标宽度采样解码，避免把整本 EPUB 的插图撑爆内存。
 *
 * @param targetWidthPx 期望解码宽度。整页插图默认 1080；书架 56dp 缩略图传实际像素，
 * 否则会为一张小图解码 1080px 的 Bitmap。
 * 换 Coil 是 M5 之后再考虑的事。
 */
@Composable
fun FileImage(
    path: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.FillWidth,
    targetWidthPx: Int = 1080
) {
    var bitmap by remember(path, targetWidthPx) {
        mutableStateOf<ImageBitmap?>(null)
    }
    LaunchedEffect(path, targetWidthPx) {
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(path, bounds)
                val target = targetWidthPx.coerceAtLeast(1)
                var sample = 1
                while (bounds.outWidth > 0 && bounds.outWidth / (sample * 2) >= target) {
                    sample *= 2
                }
                val options = BitmapFactory.Options().apply { inSampleSize = sample }
                BitmapFactory.decodeFile(path, options)?.asImageBitmap()
            }.getOrNull()
        }
    }
    bitmap?.let {
        Image(
            bitmap = it,
            contentDescription = null,
            modifier = modifier,
            contentScale = contentScale
        )
    }
}

```
### `ui/player/PlayerGlyphs.kt`（新增）

```kotlin
package io.github.mungbean2435.bookvoice.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope

internal enum class TransportGlyph {
    PreviousChapter,
    PreviousBlock,
    Play,
    Pause,
    NextBlock,
    NextChapter
}

/**
 * 五个传输键自绘图标。不引 material-icons-extended：
 * 只要 5 个形状，没必要为此增加方法数与包体。
 */
@Composable
internal fun TransportGlyphIcon(
    glyph: TransportGlyph,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier) {
        when (glyph) {
            TransportGlyph.Play -> play(this, tint)
            TransportGlyph.Pause -> pause(this, tint)
            TransportGlyph.PreviousBlock -> triangleLeft(this, tint, 0.74f, 0.28f)
            TransportGlyph.NextBlock -> triangleRight(this, tint, 0.26f, 0.72f)
            TransportGlyph.PreviousChapter -> {
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(size.width * 0.12f, size.height * 0.16f),
                    size = Size(size.width * 0.08f, size.height * 0.68f),
                    cornerRadius = CornerRadius(2f, 2f)
                )
                triangleLeft(this, tint, 0.94f, 0.64f)
                triangleLeft(this, tint, 0.62f, 0.32f)
            }

            TransportGlyph.NextChapter -> {
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(size.width * 0.80f, size.height * 0.16f),
                    size = Size(size.width * 0.08f, size.height * 0.68f),
                    cornerRadius = CornerRadius(2f, 2f)
                )
                triangleRight(this, tint, 0.06f, 0.36f)
                triangleRight(this, tint, 0.38f, 0.68f)
            }
        }
    }
}

private fun play(scope: DrawScope, color: Color) = with(scope) {
    val path = Path().apply {
        moveTo(size.width * 0.28f, size.height * 0.14f)
        lineTo(size.width * 0.82f, size.height * 0.5f)
        lineTo(size.width * 0.28f, size.height * 0.86f)
        close()
    }
    drawPath(path, color)
}

private fun pause(scope: DrawScope, color: Color) = with(scope) {
    val barWidth = size.width * 0.13f
    val barHeight = size.height * 0.68f
    drawRoundRect(
        color = color,
        topLeft = Offset(size.width * 0.30f, size.height * 0.16f),
        size = Size(barWidth, barHeight),
        cornerRadius = CornerRadius(2f, 2f)
    )
    drawRoundRect(
        color = color,
        topLeft = Offset(size.width * 0.57f, size.height * 0.16f),
        size = Size(barWidth, barHeight),
        cornerRadius = CornerRadius(2f, 2f)
    )
}

private fun triangleLeft(scope: DrawScope, color: Color, right: Float, left: Float) = with(scope) {
    val path = Path().apply {
        moveTo(size.width * right, size.height * 0.14f)
        lineTo(size.width * left, size.height * 0.5f)
        lineTo(size.width * right, size.height * 0.86f)
        close()
    }
    drawPath(path, color)
}

private fun triangleRight(scope: DrawScope, color: Color, left: Float, right: Float) = with(scope) {
    val path = Path().apply {
        moveTo(size.width * left, size.height * 0.14f)
        lineTo(size.width * right, size.height * 0.5f)
        lineTo(size.width * left, size.height * 0.86f)
        close()
    }
    drawPath(path, color)
}

```
### `ui/player/PlayerChrome.kt`（新增）

```kotlin
package io.github.mungbean2435.bookvoice.ui.player

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.mungbean2435.bookvoice.data.db.Book
import io.github.mungbean2435.bookvoice.ui.components.FileImage

internal data class PlayerChromeState(
    val fraction: Float,
    val elapsed: String,
    val remaining: String,
    val isPlaying: Boolean,
    val isBuffering: Boolean,
    val waitingNetwork: Boolean,
    val speedText: String,
    val voiceText: String,
    val configName: String,
    val sleepText: String,
    val cacheText: String
)

@Composable
internal fun PlayerTopBar(
    book: Book?,
    chapterTitle: String,
    tapToRead: Boolean,
    onBack: () -> Unit,
    onOpenChapters: () -> Unit,
    onOpenReadingSettings: () -> Unit,
    onOpenMenu: () -> Unit,
    menu: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "书架")
        }

        Row(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable(
                    role = Role.Button,
                    onClickLabel = "打开目录",
                    onClick = onOpenChapters
                )
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BookThumb(book)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    book?.title.orEmpty(),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    chapterTitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Box {
            TextButton(
                onClick = onOpenReadingSettings,
                modifier = Modifier
                    .height(48.dp)
                    .semantics {
                        contentDescription = if (tapToRead) "阅读设置，点读已开启" else "阅读设置"
                    },
                contentPadding = PaddingValues(horizontal = 12.dp),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Text("Aa", style = MaterialTheme.typography.labelLarge)
            }
            if (tapToRead) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 8.dp, end = 8.dp)
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }

        Box {
            IconButton(onClick = onOpenMenu) {
                Icon(Icons.Filled.MoreVert, contentDescription = "更多")
            }
            menu()
        }
    }
}

@Composable
private fun BookThumb(book: Book?) {
    val targetPx = with(LocalDensity.current) { 32.dp.roundToPx() }
    Box(
        modifier = Modifier
            .size(width = 32.dp, height = 48.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center
    ) {
        Text(
            book?.title?.take(1) ?: "书",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        book?.coverPath?.let { path ->
            FileImage(
                path = path,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                targetWidthPx = targetPx
            )
        }
    }
}

@Composable
internal fun PlayerBottomBar(
    state: PlayerChromeState,
    onSeek: (Float) -> Unit,
    onSeekFinished: () -> Unit,
    onToggle: () -> Unit,
    onPrevChapter: () -> Unit,
    onPrevBlock: () -> Unit,
    onNextBlock: () -> Unit,
    onNextChapter: () -> Unit,
    onSpeed: () -> Unit,
    onVoice: () -> Unit,
    onSleep: () -> Unit,
    onCache: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(Modifier.fillMaxWidth()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

            if (state.waitingNetwork) {
                Text(
                    "网络中断，等待恢复…",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )
            }

            Text(
                "全书进度 · 时长为估算",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 24.dp, top = 8.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    state.elapsed,
                    style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                    maxLines = 1,
                    modifier = Modifier.widthIn(min = 48.dp)
                )
                Slider(
                    value = state.fraction,
                    onValueChange = onSeek,
                    onValueChangeFinished = onSeekFinished,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                        .semantics { contentDescription = "全书进度" },
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        activeTickColor = Color.Transparent,
                        inactiveTickColor = Color.Transparent
                    )
                )
                Text(
                    state.remaining,
                    style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                    maxLines = 1,
                    textAlign = TextAlign.End,
                    modifier = Modifier.widthIn(min = 56.dp)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TransportButton("上一章", TransportGlyph.PreviousChapter, onPrevChapter)
                TransportButton("上一段", TransportGlyph.PreviousBlock, onPrevBlock)
                PlayButton(
                    isPlaying = state.isPlaying,
                    isBuffering = state.isBuffering,
                    onClick = onToggle
                )
                TransportButton("下一段", TransportGlyph.NextBlock, onNextBlock)
                TransportButton("下一章", TransportGlyph.NextChapter, onNextChapter)
            }

            SecondaryControls(
                state = state,
                onSpeed = onSpeed,
                onVoice = onVoice,
                onSleep = onSleep,
                onCache = onCache
            )

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun TransportButton(
    label: String,
    glyph: TransportGlyph,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        TransportGlyphIcon(
            glyph = glyph,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun PlayButton(
    isPlaying: Boolean,
    isBuffering: Boolean,
    onClick: () -> Unit
) {
    val label = when {
        isBuffering -> "缓冲中，点按暂停"
        isPlaying -> "暂停"
        else -> "播放"
    }
    Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
        if (isBuffering) {
            val transition = rememberInfiniteTransition(label = "buffer")
            val angle by transition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
                label = "angle"
            )
            val ringColor = MaterialTheme.colorScheme.primary
            Canvas(Modifier.size(72.dp)) {
                drawArc(
                    color = ringColor.copy(alpha = 0.25f),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                )
                drawArc(
                    color = ringColor,
                    startAngle = angle,
                    sweepAngle = 270f,
                    useCenter = false,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center
        ) {
            TransportGlyphIcon(
                glyph = if (isPlaying || isBuffering) TransportGlyph.Pause else TransportGlyph.Play,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

private data class SecondaryItem(
    val label: String,
    val value: String,
    val onClick: () -> Unit
)

@Composable
private fun SecondaryControls(
    state: PlayerChromeState,
    onSpeed: () -> Unit,
    onVoice: () -> Unit,
    onSleep: () -> Unit,
    onCache: () -> Unit
) {
    val items = listOf(
        SecondaryItem("语速", state.speedText, onSpeed),
        SecondaryItem(
            if (state.configName.isBlank()) "音色" else "音色 · ${state.configName}",
            state.voiceText,
            onVoice
        ),
        SecondaryItem("定时", state.sleepText, onSleep),
        SecondaryItem("缓存本章", state.cacheText, onCache)
    )
    val twoColumns = LocalDensity.current.fontScale > 1.3f

    if (!twoColumns) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items.forEach { item ->
                SecondaryControl(item, Modifier.weight(1f))
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items.chunked(2).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    row.forEach { item ->
                        SecondaryControl(item, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun SecondaryControl(item: SecondaryItem, modifier: Modifier) {
    Column(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(role = Role.Button, onClick = item.onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            item.label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        Text(
            item.value,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReadingSettingsSheet(
    fontSizeSp: Int,
    tapToRead: Boolean,
    onFontSizeChange: (Int) -> Unit,
    onTapToReadChange: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
        ) {
            Text("阅读设置", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("字号", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "$fontSizeSp sp · 15–24",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = { onFontSizeChange(fontSizeSp - 1) },
                    enabled = fontSizeSp > 15,
                    modifier = Modifier.semantics { contentDescription = "减小字号" }
                ) {
                    Text("A-", style = MaterialTheme.typography.labelLarge)
                }
                Text(
                    "$fontSizeSp",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                IconButton(
                    onClick = { onFontSizeChange(fontSizeSp + 1) },
                    enabled = fontSizeSp < 24,
                    modifier = Modifier.semantics { contentDescription = "增大字号" }
                ) {
                    Text("A+", style = MaterialTheme.typography.labelLarge)
                }
            }

            HorizontalDivider()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("点读", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "点击正文从该段开始朗读",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = tapToRead, onCheckedChange = onTapToReadChange)
            }

            Text(
                "关闭点读后，单击正文收起或显示控制栏。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

```
### `ui/player/PlayerScreen.kt`（替换）

```kotlin
package io.github.mungbean2435.bookvoice.ui.player

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mungbean2435.bookvoice.data.db.Block
import io.github.mungbean2435.bookvoice.data.db.Book
import io.github.mungbean2435.bookvoice.data.db.Bookmark
import io.github.mungbean2435.bookvoice.data.db.Chapter
import io.github.mungbean2435.bookvoice.data.db.ChapterImage
import io.github.mungbean2435.bookvoice.data.db.Failure
import io.github.mungbean2435.bookvoice.data.settings.Settings
import io.github.mungbean2435.bookvoice.data.settings.TtsConfigs
import io.github.mungbean2435.bookvoice.playback.PlayerState
import io.github.mungbean2435.bookvoice.playback.Speech
import io.github.mungbean2435.bookvoice.tts.PlaybackMath
import io.github.mungbean2435.bookvoice.tts.Voice
import io.github.mungbean2435.bookvoice.ui.AppViewModel
import io.github.mungbean2435.bookvoice.ui.components.FileImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.UUID

/**
 * 主界面：聚焦式听书。
 * 只显示「上一段 / 当前段 / 下一段」，当前段用中性底色 + 字重强调；
 * 非当前段保持完整对比度，不再按距离衰减 alpha。
 */
private sealed interface DisplayRow {
    data class Text(val block: Block) : DisplayRow
    data class Picture(val path: String) : DisplayRow
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlayerScreen(
    vm: AppViewModel,
    bookId: String,
    startChapter: Int,
    onOpenShelf: () -> Unit,
    onOpenChapters: (Int) -> Unit,
    onOpenSettings: () -> Unit
) {
    val library = remember(vm) { vm.library() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val player = remember { Speech.get() }

    val playerState by player.state.collectAsStateWithLifecycle()
    val highlight by player.highlight.collectAsStateWithLifecycle()
    val sleepMinutes by player.sleepMinutes.collectAsStateWithLifecycle()
    val waitingNetwork by player.waitingNetwork.collectAsStateWithLifecycle()
    val progress by player.progress.collectAsStateWithLifecycle()
    val cacheProgress by player.cacheProgress.collectAsStateWithLifecycle()
    val settings by Settings.state.collectAsStateWithLifecycle()
    val activeConfig = settings.activeConfigOrDefault()

    var book by remember { mutableStateOf<Book?>(null) }
    var chapters by remember { mutableStateOf<List<Chapter>>(emptyList()) }
    var rows by remember { mutableStateOf<List<DisplayRow>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    var displayedChapter by remember { mutableIntStateOf(startChapter) }
    var followPlayback by remember { mutableStateOf(false) }

    var showBookmarks by remember { mutableStateOf(false) }
    var showFailures by remember { mutableStateOf(false) }
    var showSleep by remember { mutableStateOf(false) }
    var showSpeed by remember { mutableStateOf(false) }
    var showVoice by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showReadingSettings by remember { mutableStateOf(false) }
    var bookmarks by remember { mutableStateOf<List<Bookmark>>(emptyList()) }
    var failures by remember { mutableStateOf<List<Failure>>(emptyList()) }
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    val listState = rememberLazyListState()
    var autoFollow by remember { mutableStateOf(true) }
    var chromeVisible by remember { mutableStateOf(true) }
    var programmaticAt by remember { mutableLongStateOf(0L) }

    val playingChapter = when (val s = playerState) {
        is PlayerState.Playing -> s.chapterIdx
        is PlayerState.Paused -> s.chapterIdx
        is PlayerState.Buffering -> s.chapterIdx
        else -> -1
    }
    val playingBlock = when (val s = playerState) {
        is PlayerState.Playing -> s.blockIdx
        is PlayerState.Paused -> s.blockIdx
        is PlayerState.Buffering -> s.blockIdx
        else -> -1
    }
    val isPlaying = playerState is PlayerState.Playing
    val isBuffering = playerState is PlayerState.Buffering || waitingNetwork

    val highlightBlock = highlight
        ?.takeIf { it.chapterIdx == displayedChapter }
        ?.blockIdx
    val savedBlock = book
        ?.takeIf { it.lastChapterIdx == displayedChapter }
        ?.lastBlockIdx
        ?: 0
    val currentBlock = highlightBlock ?: when {
        playingChapter == displayedChapter && playingBlock >= 0 -> playingBlock
        else -> savedBlock
    }

    LaunchedEffect(bookId) {
        book = withContext(Dispatchers.IO) { library.book(bookId) }
        chapters = withContext(Dispatchers.IO) { library.chapters(bookId) }
        loading = false
    }

    // 播放推进到新章节时跟随；用户从目录跳去看别处时不跟随
    LaunchedEffect(playingChapter) {
        if (followPlayback && playingChapter >= 0 && playingChapter != displayedChapter) {
            displayedChapter = playingChapter
        }
    }

    LaunchedEffect(displayedChapter) {
        rows = withContext(Dispatchers.IO) {
            buildRows(
                library.blocks(bookId, displayedChapter),
                library.images(bookId, displayedChapter)
            )
        }
    }

    // 打开后先回到上次读到的那一段
    LaunchedEffect(rows, book) {
        if (rows.isEmpty()) return@LaunchedEffect
        val target = book?.lastBlockIdx ?: return@LaunchedEffect
        if (target <= 0 || book?.lastChapterIdx != displayedChapter) return@LaunchedEffect
        val index = rows.indexOfFirst { it is DisplayRow.Text && it.block.idx == target }
        if (index > 0) {
            programmaticAt = System.currentTimeMillis()
            listState.scrollToItem(index)
        }
    }

    // 手动滑动 → 暂停跟随
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            if (scrolling && System.currentTimeMillis() - programmaticAt > 400) {
                autoFollow = false
            }
        }
    }

    // 朗读推进 → 当前段居中
    LaunchedEffect(playingChapter, currentBlock, rows.size, autoFollow) {
        if (!autoFollow || playingChapter != displayedChapter) return@LaunchedEffect
        val index = rows.indexOfFirst { it is DisplayRow.Text && it.block.idx == currentBlock }
        if (index >= 0) {
            programmaticAt = System.currentTimeMillis()
            listState.animateScrollToItem(index)
        }
    }

    LaunchedEffect(playerState) {
        val state = playerState
        if (state is PlayerState.Error) {
            Toast.makeText(context, state.message, Toast.LENGTH_LONG).show()
            player.clearError()
        }
    }

    LaunchedEffect(bookId, playerState) {
        failures = withContext(Dispatchers.IO) { library.failures(bookId) }
    }

    LaunchedEffect(showBookmarks, bookId) {
        if (showBookmarks) bookmarks = withContext(Dispatchers.IO) { library.bookmarks(bookId) }
    }

    val totalChars = (book?.charCount ?: 1).toLong().coerceAtLeast(1)
    val fraction = progress
        ?.takeIf { it.bookId == bookId && it.totalChars > 0 }
        ?.let { it.charsBefore.toFloat() / it.totalChars }
        ?: 0f
    val shownFraction = if (dragging) dragValue else fraction.coerceIn(0f, 1f)

    fun startPlayback(chapter: Int, block: Int, offset: Int = 0) {
        followPlayback = true
        autoFollow = true
        displayedChapter = chapter
        player.playFrom(bookId, chapter, block, offset)
    }

    fun handleTap(onText: Boolean, block: Block?) {
        when (tapAction(chromeVisible, settings.tapToRead, onText)) {
            TapAction.ShowChrome -> chromeVisible = true
            TapAction.HideChrome -> chromeVisible = false
            TapAction.ReadBlock -> block?.let { startPlayback(displayedChapter, it.idx) }
        }
    }

    Scaffold(
        topBar = {},
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                return@Column
            }

            if (chromeVisible) {
                PlayerTopBar(
                    book = book,
                    chapterTitle = chapters.getOrNull(displayedChapter)?.title.orEmpty(),
                    tapToRead = settings.tapToRead,
                    onBack = onOpenShelf,
                    onOpenChapters = { onOpenChapters(displayedChapter) },
                    onOpenReadingSettings = { showReadingSettings = true },
                    onOpenMenu = { showMenu = true },
                    menu = {
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(text = { Text("书签") }, onClick = {
                                showMenu = false
                                showBookmarks = true
                            })
                            DropdownMenuItem(text = { Text("目录") }, onClick = {
                                showMenu = false
                                onOpenChapters(displayedChapter)
                            })
                            DropdownMenuItem(text = { Text("定时关闭") }, onClick = {
                                showMenu = false
                                showSleep = true
                            })
                            DropdownMenuItem(text = { Text("待补段落（${failures.size}）") }, onClick = {
                                showMenu = false
                                if (failures.isNotEmpty()) showFailures = true
                            })
                            DropdownMenuItem(text = { Text("设置") }, onClick = {
                                showMenu = false
                                onOpenSettings()
                            })
                        }
                    }
                )
            }

            // 正文：整章可滑动，当前段中性底色 + 字重；非当前段保持可读对比度
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(chromeVisible, settings.tapToRead) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                var moved = false
                                var consumed = false
                                var pressed: Boolean
                                do {
                                    val event = awaitPointerEvent()
                                    if (event.changes.any { it.isConsumed }) consumed = true
                                    if (event.changes.any {
                                            (it.position - it.previousPosition).getDistance() > viewConfiguration.touchSlop
                                        }
                                    ) {
                                        moved = true
                                    }
                                    pressed = event.changes.any { it.pressed }
                                } while (pressed)
                                if (!moved && !consumed) handleTap(onText = false, block = null)
                            }
                        },
                    contentPadding = PaddingValues(
                        start = 24.dp,
                        end = 24.dp,
                        top = 8.dp,
                        bottom = 24.dp
                    )
                ) {
                    items(rows, key = {
                        when (it) {
                            is DisplayRow.Text -> "t${it.block.idx}"
                            is DisplayRow.Picture -> "p${it.path}"
                        }
                    }) { row ->
                        when (row) {
                            is DisplayRow.Picture -> {
                                FileImage(
                                    path = row.path,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 260.dp)
                                        .padding(vertical = 8.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                )
                            }

                            is DisplayRow.Text -> {
                                val block = row.block
                                val isCurrent = block.idx == currentBlock
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .combinedClickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                            onClick = { handleTap(onText = true, block = block) }
                                        )
                                ) {
                                    if (isCurrent) {
                                        Box(
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 8.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(
                                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                                )
                                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                        ) {
                                            FocusParagraph(
                                                text = block.text,
                                                chapterIdx = displayedChapter,
                                                blockIdx = block.idx,
                                                highlight = highlight,
                                                fontSize = settings.fontSizeSp.sp,
                                                isCurrent = true
                                            )
                                        }
                                    } else {
                                        Box(
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                        ) {
                                            FocusParagraph(
                                                text = block.text,
                                                chapterIdx = displayedChapter,
                                                blockIdx = block.idx,
                                                highlight = highlight,
                                                fontSize = settings.fontSizeSp.sp,
                                                isCurrent = false
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (!autoFollow && playingBlock >= 0) {
                    FilledTonalButton(
                        onClick = {
                            autoFollow = true
                            scope.launch {
                                programmaticAt = System.currentTimeMillis()
                                val index = rows.indexOfFirst {
                                    it is DisplayRow.Text && it.block.idx == playingBlock
                                }
                                if (index >= 0) {
                                    listState.animateScrollToItem(index)
                                }
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                            .heightIn(min = 48.dp)
                    ) {
                        Text("回到朗读", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            if (chromeVisible) {
                PlayerBottomBar(
                    state = PlayerChromeState(
                        fraction = shownFraction,
                        elapsed = PlaybackMath.formatDuration(
                            PlaybackMath.secondsForChars((shownFraction * totalChars).toLong())
                        ),
                        remaining = "-" + PlaybackMath.formatDuration(
                            PlaybackMath.secondsForChars(((1f - shownFraction) * totalChars).toLong())
                        ),
                        isPlaying = isPlaying,
                        isBuffering = isBuffering,
                        waitingNetwork = waitingNetwork,
                        speedText = String.format(Locale.US, "%.2f×", settings.speed),
                        voiceText = activeConfig.voice.ifBlank { "未设置" },
                        configName = activeConfig.name,
                        sleepText = if (sleepMinutes > 0) "$sleepMinutes 分" else "未设置",
                        cacheText = if (cacheProgress.second > 0) {
                            "${cacheProgress.first}/${cacheProgress.second}"
                        } else {
                            "离线"
                        }
                    ),
                    onSeek = {
                        dragging = true
                        dragValue = it
                    },
                    onSeekFinished = {
                        dragging = false
                        player.seekToFraction(bookId, dragValue, totalChars)
                        followPlayback = true
                    },
                    onToggle = {
                        when (playerState) {
                            is PlayerState.Playing, is PlayerState.Paused, is PlayerState.Buffering -> {
                                followPlayback = true
                                player.togglePause()
                            }

                            else -> startPlayback(displayedChapter, currentBlock)
                        }
                    },
                    onPrevChapter = {
                        startPlayback((displayedChapter - 1).coerceAtLeast(0), 0)
                    },
                    onPrevBlock = {
                        startPlayback(displayedChapter, (currentBlock - 1).coerceAtLeast(0))
                    },
                    onNextBlock = {
                        startPlayback(displayedChapter, currentBlock + 1)
                    },
                    onNextChapter = {
                        startPlayback(displayedChapter + 1, 0)
                    },
                    onSpeed = { showSpeed = true },
                    onVoice = { showVoice = true },
                    onSleep = { showSleep = true },
                    onCache = { player.cacheChapter(bookId, displayedChapter) }
                )
            }
        }
    }

    if (showReadingSettings) {
        ReadingSettingsSheet(
            fontSizeSp = settings.fontSizeSp,
            tapToRead = settings.tapToRead,
            onFontSizeChange = { size ->
                Settings.update { it.copy(fontSizeSp = size.coerceIn(15, 24)) }
            },
            onTapToReadChange = { enabled ->
                Settings.update { it.copy(tapToRead = enabled) }
            },
            onDismiss = { showReadingSettings = false }
        )
    }

    if (showBookmarks) {
        AlertDialog(
            onDismissRequest = { showBookmarks = false },
            title = { Text("书签") },
            text = {
                Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                    if (bookmarks.isEmpty()) {
                        Text("还没有书签", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    bookmarks.forEach { mark ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "第 ${mark.chapterIdx + 1} 章 · 第 ${mark.blockIdx + 1} 段",
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        showBookmarks = false
                                        startPlayback(mark.chapterIdx, mark.blockIdx)
                                    }
                                    .padding(vertical = 10.dp)
                            )
                            TextButton(onClick = {
                                bookmarks = bookmarks.filterNot { it.id == mark.id }
                                scope.launch(Dispatchers.IO) { library.deleteBookmark(mark.id) }
                            }) { Text("删除") }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val mark = Bookmark(
                        id = UUID.randomUUID().toString(),
                        bookId = bookId,
                        chapterIdx = displayedChapter,
                        blockIdx = currentBlock,
                        offsetMs = 0,
                        note = null,
                        createdAt = System.currentTimeMillis()
                    )
                    bookmarks = (bookmarks + mark).sortedWith(
                        compareBy({ it.chapterIdx }, { it.blockIdx })
                    )
                    scope.launch(Dispatchers.IO) { library.addBookmark(mark) }
                }) { Text("加书签") }
            },
            dismissButton = {
                TextButton(onClick = { showBookmarks = false }) { Text("关闭") }
            }
        )
    }

    if (showFailures) {
        AlertDialog(
            onDismissRequest = { showFailures = false },
            title = { Text("待补段落") },
            text = {
                Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                    failures.forEach { failure ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "第 ${failure.chapterIdx + 1} 章 · 第 ${failure.blockIdx + 1} 段",
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        showFailures = false
                                        startPlayback(failure.chapterIdx, failure.blockIdx)
                                    }
                                    .padding(vertical = 10.dp)
                            )
                            TextButton(onClick = {
                                failures = failures.filterNot {
                                    it.chapterIdx == failure.chapterIdx && it.blockIdx == failure.blockIdx
                                }
                                scope.launch(Dispatchers.IO) {
                                    library.clearFailure(
                                        bookId,
                                        failure.chapterIdx,
                                        failure.blockIdx
                                    )
                                }
                            }) { Text("删除") }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showFailures = false }) { Text("关闭") }
            }
        )
    }

    if (showSleep) {
        AlertDialog(
            onDismissRequest = { showSleep = false },
            title = { Text("定时关闭") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    listOf(15, 30, 45, 60).forEach { minutes ->
                        TextButton(onClick = {
                            player.startSleepTimer(minutes)
                            showSleep = false
                        }) { Text("$minutes 分钟") }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    player.startSleepTimer(0)
                    showSleep = false
                }) { Text("取消定时") }
            }
        )
    }

    if (showSpeed) {
        var value by remember { mutableFloatStateOf(settings.speed) }
        AlertDialog(
            onDismissRequest = { showSpeed = false },
            title = { Text("语速") },
            text = {
                Column {
                    Text(String.format(Locale.US, "%.2f×", value))
                    Slider(
                        value = value,
                        onValueChange = { value = it },
                        valueRange = 0.5f..2f
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    Settings.update { it.copy(speed = value) }
                    player.applySpeed(value)
                    showSpeed = false
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { showSpeed = false }) { Text("取消") }
            }
        )
    }

    if (showVoice) {
        var value by remember(activeConfig.id, settings.configs) {
            mutableStateOf(activeConfig.voice)
        }
        AlertDialog(
            onDismissRequest = { showVoice = false },
            title = { Text("音色") },
            text = {
                Column {
                    Text(
                        "当前配置：${activeConfig.name}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (activeConfig.voices.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(activeConfig.voices, key = { it.id }) { voice ->
                                FilterChip(
                                    selected = value == voice.id,
                                    onClick = { value = voice.id },
                                    label = { Text(voice.label) }
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = value,
                        onValueChange = { value = it },
                        label = { Text("音色 ID") },
                        singleLine = true
                    )
                    Text(
                        "点上面的已保存音色，或直接填 ID；保存后会自动加入音色库。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    Settings.update { old ->
                        val voiceValue = value.trim().ifBlank { old.activeConfigOrDefault().voice }
                        if (old.configs.isEmpty()) {
                            // 配置被删光时，音色胶囊仍然应该能把输入存下来。
                            val created = old.activeConfigOrDefault().copy(
                                id = TtsConfigs.newId(),
                                voice = voiceValue,
                                voices = if (voiceValue.isBlank()) {
                                    emptyList()
                                } else {
                                    listOf(Voice(voiceValue, voiceValue))
                                }
                            )
                            old.copy(configs = listOf(created), activeConfigId = created.id)
                        } else {
                            val current = old.activeConfigOrDefault()
                            old.copy(
                                configs = old.configs.map {
                                    if (it.id == current.id) {
                                        val library = if (
                                            voiceValue.isBlank() ||
                                            current.voices.any { voice -> voice.id == voiceValue }
                                        ) {
                                            current.voices
                                        } else {
                                            current.voices + Voice(voiceValue, voiceValue)
                                        }
                                        it.copy(voice = voiceValue, voices = library)
                                    } else {
                                        it
                                    }
                                }
                            )
                        }
                    }
                    showVoice = false
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { showVoice = false }) { Text("取消") }
            }
        )
    }
}

private fun buildRows(blocks: List<Block>, images: List<ChapterImage>): List<DisplayRow> {
    val byPosition = images.groupBy { it.afterBlockIdx }
    val out = ArrayList<DisplayRow>(blocks.size + images.size)
    byPosition[0]?.forEach { out.add(DisplayRow.Picture(it.path)) }
    blocks.forEach { block ->
        out.add(DisplayRow.Text(block))
        byPosition[block.idx + 1]?.forEach { out.add(DisplayRow.Picture(it.path)) }
    }
    return out
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FocusParagraph(
    text: String,
    chapterIdx: Int,
    blockIdx: Int,
    highlight: io.github.mungbean2435.bookvoice.playback.Highlight?,
    fontSize: TextUnit,
    isCurrent: Boolean
) {
    val mark = highlight?.takeIf { it.chapterIdx == chapterIdx && it.blockIdx == blockIdx }
    val rendered = if (
        mark != null &&
        mark.start in 0 until text.length &&
        mark.end in (mark.start + 1)..text.length
    ) {
        buildAnnotatedString {
            append(text)
            addStyle(
                SpanStyle(background = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
                mark.start,
                mark.end
            )
        }
    } else {
        AnnotatedString(text)
    }

    Text(
        text = rendered,
        modifier = Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.bodyLarge.copy(
            fontSize = fontSize,
            lineHeight = (fontSize.value * 1.7f).sp
        ),
        color = if (isCurrent) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        fontWeight = if (isCurrent) FontWeight.Medium else FontWeight.Normal,
        textAlign = TextAlign.Start,
        overflow = TextOverflow.Ellipsis
    )
}

```
### `ui/shelf/ShelfScreen.kt`（替换）

```kotlin
package io.github.mungbean2435.bookvoice.ui.shelf

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mungbean2435.bookvoice.data.db.Book
import io.github.mungbean2435.bookvoice.ui.AppViewModel
import io.github.mungbean2435.bookvoice.ui.components.FileImage

private val IMPORT_MIME = arrayOf("*/*")

@Composable
fun ShelfScreen(
    vm: AppViewModel,
    onOpen: (Book) -> Unit,
    onDelete: (String) -> Unit,
    onOpenSettings: () -> Unit
) {
    val books by vm.books.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<Book?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.import(uri)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            ShelfHeader(count = books.size, onOpenSettings = onOpenSettings)
        },
        floatingActionButton = {
            if (books.isNotEmpty()) {
                ShelfFab(onImport = { picker.launch(IMPORT_MIME) })
            }
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (books.isEmpty()) {
                EmptyShelf(onImport = { picker.launch(IMPORT_MIME) })
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(
                        start = 24.dp,
                        end = 24.dp,
                        top = 8.dp,
                        bottom = 96.dp
                    )
                ) {
                    itemsIndexed(books, key = { _, book -> book.id }) { index, book ->
                        BookRow(
                            book = book,
                            onClick = { onOpen(book) },
                            onLongClick = { pendingDelete = book },
                            showDivider = index != books.lastIndex
                        )
                    }
                }
            }
            if (busy) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )
            }
        }
    }

    pendingDelete?.let { book ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除《${book.title}》？") },
            text = { Text("正文、插图和缓存都会被删除，无法撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(book.id)
                    pendingDelete = null
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun ShelfHeader(count: Int, onOpenSettings: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .heightIn(min = 64.dp)
            .padding(start = 24.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("书架", style = MaterialTheme.typography.headlineSmall)
            if (count > 0) {
                Text(
                    "$count 本",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        IconButton(onClick = onOpenSettings) {
            Icon(Icons.Filled.Settings, contentDescription = "设置")
        }
    }
}

@Composable
private fun EmptyShelf(onImport: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("书架还是空的", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                "导入 TXT 或 EPUB 开始听书",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onImport,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text("导入 TXT / EPUB")
            }
        }
    }
}

@Composable
private fun ShelfFab(onImport: () -> Unit) {
    ExtendedFloatingActionButton(
        onClick = onImport,
        icon = { Icon(Icons.Filled.Add, contentDescription = null) },
        text = { Text("导入") },
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shape = RoundedCornerShape(16.dp),
        elevation = FloatingActionButtonDefaults.elevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
            focusedElevation = 0.dp,
            hoveredElevation = 0.dp
        )
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookRow(
    book: Book,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    showDivider: Boolean
) {
    val progress = if (book.chapterCount > 0) {
        (book.lastChapterIdx + 1).toFloat() / book.chapterCount.toFloat()
    } else {
        0f
    }.coerceIn(0f, 1f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                role = Role.Button,
                onClickLabel = "打开书籍",
                onLongClickLabel = "删除书籍",
                onLongClick = onLongClick,
                onClick = onClick
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 88.dp)
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BookCover(book, Modifier.size(width = 56.dp, height = 72.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    book.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    buildString {
                        book.author?.takeIf { it.isNotBlank() }?.let { append(it).append(" · ") }
                        append(book.format.uppercase()).append(" · ")
                        append(book.chapterCount).append(" 章 · ")
                        append((progress * 100).toInt()).append('%')
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 72.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun BookCover(book: Book, modifier: Modifier) {
    val targetPx = with(LocalDensity.current) { 56.dp.roundToPx() }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center
    ) {
        Text(
            book.format.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        book.coverPath?.let { path ->
            FileImage(
                path = path,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                targetWidthPx = targetPx
            )
        }
    }
}

```
### `ui/settings/SettingsComponents.kt`（新增）

```kotlin
package io.github.mungbean2435.bookvoice.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.mungbean2435.bookvoice.ui.theme.Spacing

@Composable
internal fun SectionHeader(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(start = Spacing.xs, top = Spacing.md, bottom = Spacing.xs),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
internal fun SettingsGroup(
    modifier: Modifier = Modifier,
    color: Color? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = color ?: MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(content = content)
    }
}

@Composable
internal fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = Spacing.sm),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
    )
}

@Composable
internal fun SettingsRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    value: String? = null,
    titleColor: Color? = null,
    leading: (@Composable () -> Unit)? = null,
    showChevron: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val action = onClick
    val clickModifier = if (action != null) {
        Modifier.clickable(role = Role.Button, onClick = action)
    } else {
        Modifier
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .then(clickModifier)
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(Spacing.sm))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = titleColor ?: MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (value != null) {
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                maxLines = 2,
                modifier = Modifier.weight(1f)
            )
        }
        if (showChevron) {
            Spacer(Modifier.width(Spacing.xs))
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
internal fun SettingsSliderRow(
    title: String,
    value: String,
    sliderValue: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    supporting: String? = null
) {
    Column(Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Text(
                value,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                maxLines = 2,
                modifier = Modifier.weight(1f)
            )
        }
        if (supporting != null) {
            Text(
                supporting,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Slider(
            value = sliderValue,
            onValueChange = onValueChange,
            valueRange = valueRange,
            onValueChangeFinished = onValueChangeFinished,
            modifier = Modifier.semantics { contentDescription = title },
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent
            )
        )
    }
}

@Composable
internal fun DiagnosticRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.End,
            maxLines = 3,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
internal fun SettingsNote(text: String) {
    Text(
        text,
        modifier = Modifier.padding(horizontal = Spacing.xs, vertical = Spacing.xs),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

```
### `ui/settings/SettingsScreen.kt`（替换）

```kotlin
package io.github.mungbean2435.bookvoice.ui.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mungbean2435.bookvoice.data.backup.BackupManager
import io.github.mungbean2435.bookvoice.data.settings.Metrics
import io.github.mungbean2435.bookvoice.data.settings.Settings
import io.github.mungbean2435.bookvoice.playback.AudioCache
import io.github.mungbean2435.bookvoice.playback.Speech
import io.github.mungbean2435.bookvoice.tts.ProviderRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenRules: () -> Unit,
    onOpenTtsConfig: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val current by Settings.state.collectAsStateWithLifecycle()
    val diagnostics by Speech.get().diagnostics.collectAsStateWithLifecycle()

    var speed by remember(current.speed) { mutableFloatStateOf(current.speed) }
    var gapParagraph by remember(current.gapParagraphMs) {
        mutableFloatStateOf(current.gapParagraphMs.toFloat())
    }
    var cacheLimit by remember(current.cacheLimitMb) {
        mutableFloatStateOf(current.cacheLimitMb.toFloat())
    }

    var cacheMb by remember { mutableLongStateOf(0L) }
    var cacheFiles by remember { mutableIntStateOf(0) }

    fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    suspend fun refreshCache() {
        val cache = AudioCache(context)
        cacheMb = withContext(Dispatchers.IO) { cache.totalBytes() } / (1024 * 1024)
        cacheFiles = withContext(Dispatchers.IO) { cache.fileCount() }
    }

    LaunchedEffect(Unit) { refreshCache() }

    fun persistPlayback() {
        Settings.update { old ->
            old.copy(
                speed = speed,
                gapSentenceMs = old.gapSentenceMs,
                gapParagraphMs = gapParagraph.toInt(),
                cacheLimitMb = cacheLimit.toInt()
            )
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val result = runCatching {
                    withContext(Dispatchers.IO) { BackupManager(context).export(uri) }
                }
                toast(result.getOrElse { "导出失败：${it.message}" })
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val result = runCatching {
                    withContext(Dispatchers.IO) { BackupManager(context).import(uri) }
                }
                if (result.isSuccess) {
                    // 导入可能改了当前配置，播放端语速归属要立刻刷新。
                    Speech.get().applySpeed(Settings.state.value.speed)
                }
                toast(result.getOrElse { "导入失败：${it.message}" })
            }
        }
    }

    val active = current.activeConfigOrNull()
    val activeSummary = if (active == null) {
        "还没有配置，点这里添加"
    } else {
        val providerName = ProviderRegistry.get(active.providerId)?.displayName ?: active.providerId
        val keyHint = if (active.apiKey.isBlank()) " · 未填 Key" else ""
        "${active.name} · $providerName$keyHint"
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            SectionHeader("语音")

            SettingsGroup(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                SettingsRow(
                    title = "TTS 配置",
                    subtitle = activeSummary,
                    leading = {
                        ProviderAvatar(
                            providerId = active?.providerId ?: "mimo",
                            name = active?.name.orEmpty()
                        )
                    },
                    showChevron = true,
                    onClick = onOpenTtsConfig
                )
            }

            Spacer(Modifier.height(8.dp))

            SettingsGroup {
                SettingsSliderRow(
                    title = "语速",
                    value = String.format(Locale.US, "%.2f×", speed),
                    sliderValue = speed,
                    valueRange = 0.5f..2f,
                    onValueChange = { speed = it },
                    onValueChangeFinished = {
                        persistPlayback()
                        Speech.get().applySpeed(speed)
                    }
                )
                SettingsDivider()
                SettingsSliderRow(
                    title = "段落间停顿",
                    value = "${gapParagraph.toInt()} ms",
                    supporting = "每 400 字左右一次",
                    sliderValue = gapParagraph,
                    valueRange = 0f..1200f,
                    onValueChange = { gapParagraph = it },
                    onValueChangeFinished = { persistPlayback() }
                )
            }

            SectionHeader("缓存")

            SettingsGroup {
                SettingsRow(
                    title = "已用",
                    value = "$cacheMb MB · $cacheFiles 个文件"
                )
                SettingsDivider()
                SettingsSliderRow(
                    title = "缓存上限",
                    value = "${cacheLimit.toInt()} MB",
                    sliderValue = cacheLimit,
                    valueRange = 100f..2048f,
                    onValueChange = { cacheLimit = it },
                    onValueChangeFinished = { persistPlayback() }
                )
                SettingsDivider()
                SettingsRow(
                    title = "清理缓存",
                    titleColor = MaterialTheme.colorScheme.primary,
                    onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) { AudioCache(context).clear() }
                            refreshCache()
                            toast("缓存已清理")
                        }
                    }
                )
            }

            SectionHeader("朗读规则")

            SettingsGroup {
                Text(
                    "内置规则处理省略号、破折号、百分号、摄氏度等；也可以导入自定义规则包（JSON）。规则改变后，旧音频会自动失效重合成。",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SettingsDivider()
                SettingsRow(
                    title = "管理规则包",
                    showChevron = true,
                    onClick = onOpenRules
                )
            }

            SectionHeader("备份")

            SettingsGroup {
                Text(
                    "备份含进度、书签、规则包、设置；不含书文件，也不含 API Key。",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SettingsDivider()
                SettingsRow(
                    title = "导出备份",
                    onClick = { exportLauncher.launch("tingshu-backup.json") }
                )
                SettingsDivider()
                SettingsRow(
                    title = "导入备份",
                    onClick = { importLauncher.launch(arrayOf("application/json")) }
                )
            }

            SectionHeader("诊断")

            SettingsGroup {
                DiagnosticRow(
                    label = "上次导入",
                    value = if (Metrics.lastImportMs > 0) {
                        "${String.format(Locale.US, "%.1f", Metrics.lastImportMs / 1000.0)} s"
                    } else {
                        "—"
                    }
                )
                DiagnosticRow(
                    label = "首声 · 未缓存",
                    value = if (diagnostics.firstSoundColdMs > 0) "${diagnostics.firstSoundColdMs} ms" else "—"
                )
                DiagnosticRow(
                    label = "首声 · 已缓存",
                    value = if (diagnostics.firstSoundWarmMs > 0) "${diagnostics.firstSoundWarmMs} ms" else "—"
                )
                DiagnosticRow(
                    label = "缓存命中",
                    value = "${diagnostics.cacheHits} 次 · 未命中 ${diagnostics.cacheMisses} 次"
                )
                if (Metrics.lastBreakdown.isNotBlank()) {
                    SettingsDivider()
                    Text(
                        "上次导入拆分：${Metrics.lastBreakdown}",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            SectionHeader("后台保活")

            SettingsNote("国产系统可能锁屏后清理后台。如果播放被中断，去系统设置里允许本应用「后台运行 / 自启动 / 不受限制」。")

            SettingsGroup {
                SettingsRow(
                    title = "打开应用系统设置",
                    showChevron = true,
                    onClick = {
                        val intent = Intent(
                            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null)
                        )
                        runCatching { context.startActivity(intent) }
                    }
                )
            }

            SettingsNote("拖动滑块松手即保存；语速保存后立即作用于正在播放的音频。TTS 配置的改动在「TTS 配置」里点保存。")
        }
    }
}

```
### `ui/settings/TtsConfigUi.kt`（修改）

```kotlin
package io.github.mungbean2435.bookvoice.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 供应商没有品牌图标资源，这里用字母头像，以后要换 logo 只改这一个地方。 */
@Composable
fun ProviderAvatar(
    providerId: String,
    name: String = "",
    size: Dp = 48.dp
) {
    val (background, foreground) = when (providerId) {
        "mimo" -> MaterialTheme.colorScheme.primaryContainer to
            MaterialTheme.colorScheme.onPrimaryContainer

        "minimax" -> MaterialTheme.colorScheme.secondaryContainer to
            MaterialTheme.colorScheme.onSecondaryContainer

        else -> MaterialTheme.colorScheme.surfaceContainerHighest to
            MaterialTheme.colorScheme.onSurfaceVariant
    }
    val label = when (providerId) {
        "mimo" -> "M"
        "minimax" -> "MM"
        else -> name.take(1).ifBlank { "T" }
    }
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(background),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = foreground,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.32f).sp
        )
    }
}

/** 列表里的启用状态徽标：绿色沿用参考图，暗色下用深色底。 */
@Composable
fun ConfigStatusChip(active: Boolean, label: String? = null) {
    val background = if (active) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val foreground = if (active) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        color = background,
        contentColor = foreground,
        shape = RoundedCornerShape(50)
    ) {
        Text(
            text = label ?: if (active) "启用" else "未启用",
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
fun TagChip(
    text: String,
    container: Color? = null,
    content: Color? = null
) {
    Surface(
        color = container ?: MaterialTheme.colorScheme.surfaceVariant,
        contentColor = content ?: MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(50)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
fun TtsFieldCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
fun TtsHint(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

```
### 资源、构建与单测
#### `app/src/main/res/values/colors.xml`（新增）

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="window_background">#FAF9F6</color>
</resources>

```
#### `app/src/main/res/values-night/colors.xml`（新增）

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="window_background">#101416</color>
</resources>

```
#### `app/src/main/res/values/themes.xml`（修改）

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.Tingshu" parent="android:Theme.Material.Light.NoActionBar">
        <item name="android:windowBackground">@color/window_background</item>
        <item name="android:statusBarColor">@android:color/transparent</item>
        <item name="android:navigationBarColor">@android:color/transparent</item>
    </style>
</resources>

```
#### `app/src/main/res/values-night/themes.xml`（修改）

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.Tingshu" parent="android:Theme.Material.NoActionBar">
        <item name="android:windowBackground">@color/window_background</item>
        <item name="android:statusBarColor">@android:color/transparent</item>
        <item name="android:navigationBarColor">@android:color/transparent</item>
    </style>
</resources>

```
#### `app/build.gradle.kts`（修改（仅 versionCode / versionName））

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "io.github.mungbean2435.bookvoice"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.mungbean2435.bookvoice"
        minSdk = 26
        targetSdk = 35
        versionCode = 38
        versionName = "2.4.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jsoup:jsoup:1.18.1")
    implementation("androidx.media3:media3-exoplayer:1.5.0")
    implementation("androidx.media3:media3-common:1.5.0")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
}

```
#### `app/src/test/java/io/github/mungbean2435/bookvoice/ui/theme/ThemeContrastTest.kt`（新增）

```kotlin
package io.github.mungbean2435.bookvoice.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 明暗两套品牌色的 WCAG 对比度回归。
 * 动态取色的角色由 M3 色调板保证，这里锁死 Android 11 及以下的兜底色。
 */
class ThemeContrastTest {

    private fun ratio(foreground: Color, background: Color): Double {
        val a = foreground.luminance().toDouble() + 0.05
        val b = background.luminance().toDouble() + 0.05
        return if (a > b) a / b else b / a
    }

    private fun check(scheme: ColorScheme, label: String) {
        val pairs = listOf(
            "onPrimary/primary" to (scheme.onPrimary to scheme.primary),
            "onPrimaryContainer/primaryContainer" to
                (scheme.onPrimaryContainer to scheme.primaryContainer),
            "onSecondary/secondary" to (scheme.onSecondary to scheme.secondary),
            "onSecondaryContainer/secondaryContainer" to
                (scheme.onSecondaryContainer to scheme.secondaryContainer),
            "onSurface/surface" to (scheme.onSurface to scheme.surface),
            "onSurfaceVariant/surface" to (scheme.onSurfaceVariant to scheme.surface),
            "onSurfaceVariant/containerHighest" to
                (scheme.onSurfaceVariant to scheme.surfaceContainerHighest),
            "onSurfaceVariant/containerHigh" to
                (scheme.onSurfaceVariant to scheme.surfaceContainerHigh),
            "primary/surface" to (scheme.primary to scheme.surface),
            "onSurface/containerHigh" to (scheme.onSurface to scheme.surfaceContainerHigh),
            "error/surface" to (scheme.error to scheme.surface)
        )
        pairs.forEach { (name, pair) ->
            val value = ratio(pair.first, pair.second)
            assertTrue(
                "$label $name 对比度 $value < 4.5",
                value >= 4.5
            )
        }
    }

    @Test
    fun lightSchemeMeetsWcag() {
        check(BrandLightColors, "light")
    }

    @Test
    fun darkSchemeMeetsWcag() {
        check(BrandDarkColors, "dark")
    }
}

```

## ⑤ 依赖清单

| 库 | 用途 | 体积 | API 26 以下降级 |
|---|---|---|---|
| 无新增 | — | 0 MB，APK 不增 | minSdk 26，无 26 以下分支；动态取色在 API 31 以下回退固定品牌色 |

未采用：
- `material-icons-extended`：只需 5 个图标，release 未开 minify，包体/方法数不划算；改用 `PlayerGlyphs.kt` Canvas 自绘。
- 第三方模糊/玻璃库：API 31+ 效果才稳定，GPU 采样代价高；本方案玻璃/模糊/渐变 0 处。
- Coil / Palette：现有 `FileImage` 增加 `targetWidthPx` 就够；不做封面取色，避免额外 Bitmap 解码。

动态取色最低 API 31（`Build.VERSION.SDK_INT >= Build.VERSION_CODES.S`）；Android 11 及以下使用 `BrandLightColors` / `BrandDarkColors`，无崩溃分支。

## ⑥ 验证清单

### 已在本机执行
| 命令 | 结果 |
|---|---|
| `gradle lintDebug` | 0 error；2 个既有 warning（`allowBackup`、`SpeechPlayer` 静态 Context），非本次引入 |
| `gradle testDebugUnitTest` | 全绿；`ThemeContrastTest` 明暗各 11 组 ≥4.5，2/2 通过 |
| `gradle assembleDebug` | 成功，`app-debug.apk` |
| `gradle assembleRelease` | 成功，`听书-2.4.0.apk`（8.6 MB，versionCode 38） |

### 真机验收（需安装 APK；本机无 adb/模拟器）
- [ ] Android 12+ 动态取色：换 2 张壁纸，书架/听书页/设置明暗各看一遍
- [ ] Android 8/11 兜底：固定墨蓝，无紫调轨道
- [ ] 深色模式：正文、当前段、设置分组、断网提示对比度
- [ ] 200% 字体：书架标题、顶栏书名、四个控制 2×2、设置 Slider 行、书签/待补对话框不裁切
- [ ] 长标题 / 无封面 / 空书架 / 长按删除 / 导入进度
- [ ] 播放/暂停、上一章/段、下一章/段、进度拖动、语速/音色/定时/缓存本章
- [ ] 断网等待、待补段落、书签、目录、设置返回
- [ ] TalkBack：返回、设置、更多、播放/暂停、上一章/段、下一章/段、缓存本章
- [ ] 开发者「显示布局边界」抽查 48dp 点击区
- [ ] 列表/正文滚动无模糊与阴影重绘

### 自查
- [x] 每个删除项都有还原方式（③ 表）
- [x] 深色 + 200% 对比度/截断已做静态与单测验证，像素验收待真机
- [x] 新依赖均写明最低 SDK 与降级（无新增）
- [x] 玻璃/渐变 0 处（上限 1）
- [x] 代码完整替换、无 TODO 占位；`lintDebug` 通过

## 附：同步更新的文档
- `README.md`：版本 2.4.0、APK 路径、听书主页/控制区描述
- `docs/PROJECT.md`：7.6 时长标注、10 界面表、目录结构
- `docs/CHANGELOG.md`：2.4.0 条目
- `docs/UI-REFACTOR-2.4.md`：本报告
