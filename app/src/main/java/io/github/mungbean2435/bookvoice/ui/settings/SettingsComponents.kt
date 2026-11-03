package io.github.mungbean2435.bookvoice.ui.settings

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.mungbean2435.bookvoice.data.settings.ThemeMode
import io.github.mungbean2435.bookvoice.ui.theme.LocalBookVoiceDarkTheme
import io.github.mungbean2435.bookvoice.ui.theme.LocalThemeRevealController
import io.github.mungbean2435.bookvoice.ui.theme.SettingsTone
import io.github.mungbean2435.bookvoice.ui.theme.BookVoiceDesign
import kotlinx.coroutines.launch

@Composable
internal fun settingsPageColor(): Color =
    if (LocalBookVoiceDarkTheme.current) {
        MaterialTheme.colorScheme.background
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }

@Composable
internal fun settingsCardColor(): Color =
    if (LocalBookVoiceDarkTheme.current) {
        MaterialTheme.colorScheme.surfaceContainerLow
    } else {
        MaterialTheme.colorScheme.surfaceContainerLowest
    }

@Composable
internal fun SettingsPageHeader(
    onBack: () -> Unit,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("设置", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.width(16.dp))
            ThemeModeButton(themeMode, onThemeModeChange)
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
internal fun SettingsDetailHeader(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
        }
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

@Composable
internal fun ThemeModeButton(mode: ThemeMode, onModeChange: (ThemeMode) -> Unit) {
    val dark = LocalBookVoiceDarkTheme.current
    val description = if (dark) "切换到浅色" else "切换到深色"
    val rotation = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val reveal = LocalThemeRevealController.current
    var origin by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = Modifier
            .size(48.dp)
            .onGloballyPositioned { coordinates ->
                val topLeft = coordinates.positionInWindow()
                origin = topLeft + Offset(
                    coordinates.size.width / 2f,
                    coordinates.size.height / 2f
                )
            }
            .clip(CircleShape)
            .clickable(role = Role.Button) {
                if (rotation.isRunning) return@clickable
                val target = if (dark) ThemeMode.Light else ThemeMode.Dark
                val angle = if (dark) -56f else 56f
                val duration = if (target == ThemeMode.Dark) 640 else 560
                val change: () -> Unit = { onModeChange(target) }

                scope.launch {
                    val controller = reveal
                    if (controller != null) {
                        controller.reveal(origin, duration, change)
                    } else {
                        change()
                    }
                }
                scope.launch {
                    // 图标交叉旋转 ±56°，总时长 620ms；换图由 Crossfade 同步完成。
                    rotation.animateTo(
                        targetValue = angle,
                        animationSpec = tween(310)
                    )
                    rotation.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(310)
                    )
                }
            }
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.size(36.dp)
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier.graphicsLayer {
                        rotationZ = rotation.value
                    },
                    contentAlignment = Alignment.Center
                ) {
                    Crossfade(
                        targetState = dark,
                        animationSpec = tween(620),
                        label = "themeModeIcon"
                    ) { isDark ->
                        SettingsGlyphIcon(
                            glyph = if (isDark) SettingsGlyph.Sun else SettingsGlyph.Moon,
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                if (mode == ThemeMode.FollowSystem) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
            }
        }
    }
}

@Composable
internal fun SettingsSearchBar(query: String, onQueryChange: (String) -> Unit) {
    val tokens = BookVoiceDesign.tokens
    val fill = if (tokens.isClean) {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
        shape = tokens.groupShape,
        color = fill
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(8.dp))
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .weight(1f)
                    .semantics { contentDescription = "搜索设置" },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { innerTextField ->
                    Box(Modifier.fillMaxWidth()) {
                        if (query.isEmpty()) {
                            Text(
                                "搜索设置",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        innerTextField()
                    }
                }
            )
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Filled.Close, contentDescription = "清除搜索")
                }
            }
        }
    }
}

@Composable
internal fun SettingsCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = BookVoiceDesign.tokens.groupShape,
        color = settingsCardColor()
    ) {
        Column(content = content)
    }
}

@Composable
private fun SettingsIconTile(glyph: SettingsGlyph, tone: SettingsTone) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(BookVoiceDesign.tokens.controlShape)
            .background(tone.container),
        contentAlignment = Alignment.Center
    ) {
        SettingsGlyphIcon(
            glyph = glyph,
            tint = Color.White,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
internal fun SettingsRow(
    title: String,
    subtitle: String?,
    glyph: SettingsGlyph,
    tone: SettingsTone,
    modifier: Modifier = Modifier,
    showChevron: Boolean = true,
    trailing: String? = null,
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
            .heightIn(min = BookVoiceDesign.tokens.listRowMinHeight)
            .then(clickModifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingsIconTile(glyph, tone)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                trailing,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (showChevron) {
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
internal fun SettingsDivider(start: Dp = 72.dp) {
    HorizontalDivider(
        modifier = Modifier.padding(start = start),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
    )
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
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
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
            .padding(horizontal = 16.dp, vertical = 8.dp),
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
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
internal fun SettingsEmptySearch() {
    Text(
        "没有找到相关设置",
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )
}
