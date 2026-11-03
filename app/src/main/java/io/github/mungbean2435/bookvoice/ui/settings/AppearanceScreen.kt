@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package io.github.mungbean2435.bookvoice.ui.settings

import android.os.Build
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mungbean2435.bookvoice.data.settings.Settings
import io.github.mungbean2435.bookvoice.data.settings.ThemeMode
import io.github.mungbean2435.bookvoice.ui.components.LocalFloatingChromeReserve
import io.github.mungbean2435.bookvoice.ui.components.BookVoicePageScaffold
import io.github.mungbean2435.bookvoice.ui.glass.LiquidGlassAvailability
import io.github.mungbean2435.bookvoice.ui.glass.rememberLiquidGlassAvailability
import io.github.mungbean2435.bookvoice.ui.nav.PageChromeState
import io.github.mungbean2435.bookvoice.ui.nav.rememberPageChromeState
import io.github.mungbean2435.bookvoice.ui.theme.AppearanceSettings
import io.github.mungbean2435.bookvoice.ui.theme.DefaultGlassTintArgb
import io.github.mungbean2435.bookvoice.ui.theme.AppearanceState
import io.github.mungbean2435.bookvoice.ui.theme.ContinuousRoundedCornerShape
import io.github.mungbean2435.bookvoice.ui.theme.LocalBookVoiceDarkTheme
import io.github.mungbean2435.bookvoice.ui.theme.BookVoiceDesign
import io.github.mungbean2435.bookvoice.ui.theme.BookVoicePalette
import io.github.mungbean2435.bookvoice.ui.theme.BookVoiceStyle
import io.github.mungbean2435.bookvoice.ui.theme.glassColorHex
import io.github.mungbean2435.bookvoice.ui.theme.parseGlassColor
import io.github.mungbean2435.bookvoice.ui.theme.resolvedGlassTint
import io.github.mungbean2435.bookvoice.ui.theme.BookVoiceTheme

@Composable
fun AppearanceScreen(onBack: () -> Unit, state: PageChromeState) {
    val appearance by AppearanceSettings.state.collectAsStateWithLifecycle()
    val current by Settings.state.collectAsStateWithLifecycle()
    val dark = LocalBookVoiceDarkTheme.current
    val tokens = BookVoiceDesign.tokens
    val chromeReserve = LocalFloatingChromeReserve.current
    val glassAvailability = rememberLiquidGlassAvailability()
    var glassPickerOpen by remember { mutableStateOf(false) }

    BookVoicePageScaffold(title = "界面风格", state = state.topBar, onBack = onBack) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            state = state.list,
            contentPadding = PaddingValues(
                start = tokens.pageHorizontalPadding,
                end = tokens.pageHorizontalPadding,
                top = 8.dp,
                bottom = 32.dp + chromeReserve
            )
        ) {
            item { SectionLabel("界面风格") }
            item {
                StylePair(
                    selected = appearance.style,
                    onSelect = { style ->
                        AppearanceSettings.update { it.copy(style = style) }
                    }
                )
            }

            item { Spacer(Modifier.height(24.dp)) }
            item { SectionLabel("深浅色") }
            item {
                SettingsCard {
                    FollowSystemRow(
                        following = current.themeMode == ThemeMode.FollowSystem,
                        onChange = { follow ->
                            Settings.update {
                                it.copy(
                                    themeMode = if (follow) {
                                        ThemeMode.FollowSystem
                                    } else if (dark) {
                                        ThemeMode.Dark
                                    } else {
                                        ThemeMode.Light
                                    }
                                )
                            }
                        }
                    )
                    SettingsDivider(start = 16.dp)
                    DarkModeSegments(
                        selectedDark = dark,
                        onSelect = { isDark ->
                            Settings.update {
                                it.copy(themeMode = if (isDark) ThemeMode.Dark else ThemeMode.Light)
                            }
                        }
                    )
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
            item { SectionLabel("取色") }
            item {
                SettingsCard {
                    DynamicColorRow(appearance = appearance)
                    SettingsDivider(start = 16.dp)
                    PaletteGrid(appearance = appearance)
                }
            }
            item {
                SettingsNote(
                    "6 种取色是预设种子色的近似方案，不是 HCT 调色板；" +
                        "动态取色开启时会优先使用系统壁纸色。"
                )
            }

            item { Spacer(Modifier.height(24.dp)) }
            item { SectionLabel("效果") }
            item {
                SettingsCard {
                    LiquidGlassRow(
                        enabled = appearance.liquidGlass,
                        availability = glassAvailability,
                        onChange = { on ->
                            AppearanceSettings.update { it.copy(liquidGlass = on) }
                        }
                    )
                    SettingsDivider(start = 16.dp)
                    GlassTintRow(
                        appearance = appearance,
                        onClick = { glassPickerOpen = true }
                    )
                }
            }
            item {
                SettingsNote(
                    if (glassAvailability.available) {
                        "底部导航实时采样页面内容做 28dp 模糊；开启后叠加 14dp 边缘折射，" +
                            "列表滚到胶囊下方时也会跟着虚化。\n" +
                            "「镜头颜色」同时作用于底栏玻璃、选中镜头底色和镜头内图标/文字，图标会自动调成同色系高对比色。"
                    } else {
                        "当前不可用：${glassAvailability.reason}。底部导航保持" +
                            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) "档 2 毛玻璃" else "档 1 纯色") +
                            "。\n「镜头颜色」对当前档位同样生效。"
                    }
                )
            }

            item { Spacer(Modifier.height(24.dp)) }
            item { SectionLabel("返回手势") }
            item {
                SettingsCard {
                    BackGesturePreviewRow(
                        enabled = appearance.backGesturePreview,
                        onChange = { on ->
                            AppearanceSettings.update { it.copy(backGesturePreview = on) }
                        }
                    )
                }
            }
            item {
                SettingsNote(
                    "开启后系统返回手势可以按进度拖动页面，能看到下层页面；" +
                        "关闭后吞掉拖动进度，松手仍走完整的普通返回动画。"
                )
            }
        }
    }

    if (glassPickerOpen) {
        GlassColorDialog(
            appearance = appearance,
            onDismiss = { glassPickerOpen = false }
        )
    }
}

@Composable
private fun GlassTintRow(
    appearance: AppearanceState,
    onClick: () -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary
    val color = resolvedGlassTint(appearance.glassTintFollowTheme, appearance.glassTint, primary)
    val detail = if (appearance.glassTintFollowTheme) {
        "跟随主题强调色"
    } else {
        val preset = GlassPresets.firstOrNull { it.color.toArgb() == appearance.glassTint }
        if (preset == null) {
            glassColorHex(appearance.glassTint)
        } else {
            "${preset.label} · ${glassColorHex(appearance.glassTint)}"
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = BookVoiceDesign.tokens.listRowMinHeight)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("镜头颜色", style = MaterialTheme.typography.titleMedium)
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(color)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "更改",
            style = MaterialTheme.typography.labelLarge,
            color = primary
        )
    }
}

@Composable
private fun GlassColorDialog(
    appearance: AppearanceState,
    onDismiss: () -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    var hex by remember { mutableStateOf(glassColorHex(appearance.glassTint)) }
    var invalid by remember { mutableStateOf(false) }

    val applyColor: (Int) -> Unit = { argb ->
        AppearanceSettings.update { it.copy(glassTint = argb, glassTintFollowTheme = false) }
        onDismiss()
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                Text("镜头颜色", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(4.dp))
                Text(
                    "同时改底栏玻璃、选中镜头底色和镜头内的图标/文字；默认灰色，图标会自动加深或提亮保证对比。",
                    style = MaterialTheme.typography.bodySmall,
                    color = muted
                )
                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            AppearanceSettings.update { it.copy(glassTintFollowTheme = true) }
                            onDismiss()
                        }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(primary)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "跟随主题强调色",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall
                    )
                    RadioButton(
                        selected = appearance.glassTintFollowTheme,
                        onClick = null
                    )
                }

                Spacer(Modifier.height(12.dp))
                GlassPresets.chunked(3).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        row.forEach { preset ->
                            GlassSwatch(
                                modifier = Modifier.weight(1f),
                                preset = preset,
                                selected = !appearance.glassTintFollowTheme &&
                                    appearance.glassTint == preset.color.toArgb(),
                                onClick = { applyColor(preset.color.toArgb()) }
                            )
                        }
                        repeat(3 - row.size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = hex,
                    onValueChange = {
                        hex = it
                        invalid = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("自定义 #RRGGBB") },
                    singleLine = true,
                    isError = invalid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii)
                )
                if (invalid) {
                    Text(
                        "请输入 6 位十六进制，例如 #8F949B",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val parsed = parseGlassColor(hex)
                            if (parsed == null) {
                                invalid = true
                            } else {
                                applyColor(parsed.toArgb())
                            }
                        }
                    ) { Text("应用") }
                }
            }
        }
    }
}

@Composable
private fun GlassSwatch(
    modifier: Modifier,
    preset: GlassPreset,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(preset.color)
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    },
                    shape = CircleShape
                )
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = preset.label,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1
        )
    }
}

private data class GlassPreset(val label: String, val color: Color)

private val GlassPresets = listOf(
    GlassPreset("灰", Color(DefaultGlassTintArgb)),
    GlassPreset("银", Color(0xFFC7CBD1)),
    GlassPreset("白", Color(0xFFF4F5F7)),
    GlassPreset("青", Color(0xFF4C8C88)),
    GlassPreset("琥珀", Color(0xFFB07A2A)),
    GlassPreset("玫瑰", Color(0xFFB65C6D))
)

@Composable
private fun BackGesturePreviewRow(
    enabled: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = BookVoiceDesign.tokens.listRowMinHeight)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("返回手势预览", style = MaterialTheme.typography.titleMedium)
            Text(
                if (enabled) "拖动时页面跟手，松手完成或回弹" else "已关闭：仍然是普通返回动画",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = enabled,
            onCheckedChange = onChange
        )
    }
}

@Composable
private fun LiquidGlassRow(
    enabled: Boolean,
    availability: LiquidGlassAvailability,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = BookVoiceDesign.tokens.listRowMinHeight)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("液态玻璃", style = MaterialTheme.typography.titleMedium)
            Text(
                when {
                    availability.available && enabled -> "边缘折射已开启 · Android 13+"
                    availability.available -> "默认关闭 · 当前是档 2 毛玻璃"
                    else -> availability.reason.orEmpty()
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = enabled,
            enabled = availability.available,
            onCheckedChange = onChange
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun StylePair(
    selected: BookVoiceStyle,
    onSelect: (BookVoiceStyle) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StyleCard(
            modifier = Modifier.weight(1f),
            style = BookVoiceStyle.Material,
            selected = selected == BookVoiceStyle.Material,
            onClick = { onSelect(BookVoiceStyle.Material) }
        )
        StyleCard(
            modifier = Modifier.weight(1f),
            style = BookVoiceStyle.Clean,
            selected = selected == BookVoiceStyle.Clean,
            onClick = { onSelect(BookVoiceStyle.Clean) }
        )
    }
}

@Composable
private fun StyleCard(
    modifier: Modifier,
    style: BookVoiceStyle,
    selected: Boolean,
    onClick: () -> Unit
) {
    val shape = if (style == BookVoiceStyle.Clean) {
        ContinuousRoundedCornerShape(16.dp)
    } else {
        RoundedCornerShape(16.dp)
    }
    val scale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.97f,
        animationSpec = tween(200),
        label = "styleCardScale"
    )
    val stroke by animateDpAsState(
        targetValue = if (selected) 2.5.dp else 1.dp,
        animationSpec = tween(200),
        label = "styleCardStroke"
    )
    val colors = MaterialTheme.colorScheme

    Column(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(colors.surfaceContainerLow)
            .border(
                width = stroke,
                color = if (selected) colors.primary else colors.outlineVariant,
                shape = shape
            )
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(shape)
                .background(colors.surfaceContainerHigh)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(colors.primary)
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 10.dp, bottom = 12.dp)
                    .size(width = 62.dp, height = 6.dp)
                    .clip(CircleShape)
                    .background(colors.onSurfaceVariant.copy(alpha = 0.35f))
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = if (style == BookVoiceStyle.Clean) "Clean" else "Material",
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = if (style == BookVoiceStyle.Clean) "连续圆角" else "常规圆角",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun FollowSystemRow(
    following: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = BookVoiceDesign.tokens.listRowMinHeight)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("跟随系统", style = MaterialTheme.typography.titleMedium)
            Text(
                "系统进入深色时自动切换",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = following, onCheckedChange = onChange)
    }
}

@Composable
private fun DarkModeSegments(
    selectedDark: Boolean,
    onSelect: (Boolean) -> Unit
) {
    val tokens = BookVoiceDesign.tokens
    val options = listOf(false to "浅色", true to "深色")

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .height(tokens.segmentedTrackHeight)
            .clip(tokens.controlShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {
        val innerWidth = maxWidth - tokens.segmentedInset * 2
        val itemWidth = innerWidth / 2
        val target = if (selectedDark) itemWidth else 0.dp
        val indicatorOffset by animateDpAsState(
            targetValue = target,
            animationSpec = tween(tokens.motion.segmentedIndicatorDurationMs),
            label = "darkModeIndicator"
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(tokens.segmentedInset)
        ) {
            Box(
                modifier = Modifier
                    .offset(x = indicatorOffset)
                    .width(itemWidth)
                    .fillMaxHeight()
                    .clip(tokens.controlShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            )
            Row(Modifier.fillMaxSize()) {
                options.forEach { (isDark, label) ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(tokens.controlShape)
                            .clickable { onSelect(isDark) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (selectedDark == isDark) {
                                FontWeight.SemiBold
                            } else {
                                FontWeight.Normal
                            },
                            color = if (selectedDark == isDark) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DynamicColorRow(appearance: AppearanceState) {
    val supported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = BookVoiceDesign.tokens.listRowMinHeight)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("动态取色", style = MaterialTheme.typography.titleMedium)
            Text(
                if (supported) "使用系统壁纸色，关闭后使用下方取色风格" else "需要 Android 12 及以上",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = appearance.dynamicColor && supported,
            enabled = supported,
            onCheckedChange = { enabled ->
                AppearanceSettings.update { it.copy(dynamicColor = enabled) }
            }
        )
    }
}

@Composable
private fun PaletteGrid(appearance: AppearanceState) {
    val tokens = BookVoiceDesign.tokens
    val dynamicActive = appearance.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    Column(Modifier.padding(16.dp)) {
        BookVoicePalette.entries.chunked(3).forEachIndexed { rowIndex, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { palette ->
                    val selected = appearance.palette == palette
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp)
                            .clip(tokens.chipShape)
                            .background(
                                if (selected) {
                                    MaterialTheme.colorScheme.secondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                }
                            )
                            .alpha(if (dynamicActive) 0.45f else 1f)
                            .clickable(enabled = !dynamicActive) {
                                AppearanceSettings.update { it.copy(palette = palette) }
                            }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(palette.seed)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = palette.label,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1
                        )
                    }
                }
                repeat(3 - row.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
            if (rowIndex != BookVoicePalette.entries.chunked(3).lastIndex) {
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844, name = "界面风格 · Clean")
@Composable
private fun AppearanceScreenPreview() {
    BookVoiceTheme(darkTheme = false, dynamicColor = false) {
        AppearanceScreen(onBack = {}, state = rememberPageChromeState())
    }
}
