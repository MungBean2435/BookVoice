package io.github.mungbean2435.bookvoice.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.mungbean2435.bookvoice.data.settings.AppLanguage
import io.github.mungbean2435.bookvoice.data.settings.ThemeMode
import io.github.mungbean2435.bookvoice.ui.glass.rememberLiquidGlassAvailability
import io.github.mungbean2435.bookvoice.ui.theme.BookVoicePalette

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AppearanceStepContent(
    strings: OnboardingStrings,
    language: AppLanguage,
    themeMode: ThemeMode,
    palette: BookVoicePalette,
    dynamicColor: Boolean,
    liquidGlass: Boolean,
    enabled: Boolean,
    onThemeModeChange: (ThemeMode) -> Unit,
    onPaletteChange: (BookVoicePalette) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onLiquidGlassChange: (Boolean) -> Unit
) {
    val glassAvailability = rememberLiquidGlassAvailability()
    val glassReason = when {
        glassAvailability.available -> null
        language == AppLanguage.En && glassAvailability.sdkInt < 33 ->
            "Requires Android 13 (API 33) or newer"
        language == AppLanguage.En && glassAvailability.powerSave ->
            "Unavailable while power saving is on"
        language == AppLanguage.En && glassAvailability.lowRam ->
            "Unavailable on low-RAM devices"
        else -> glassAvailability.reason
    }

    StepContent {
        StepSectionLabel(strings.appearance.themeTitle)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ChoiceChip(
                label = strings.appearance.followSystem,
                selected = themeMode == ThemeMode.FollowSystem,
                enabled = enabled,
                onClick = { onThemeModeChange(ThemeMode.FollowSystem) }
            )
            ChoiceChip(
                label = strings.appearance.light,
                selected = themeMode == ThemeMode.Light,
                enabled = enabled,
                onClick = { onThemeModeChange(ThemeMode.Light) }
            )
            ChoiceChip(
                label = strings.appearance.dark,
                selected = themeMode == ThemeMode.Dark,
                enabled = enabled,
                onClick = { onThemeModeChange(ThemeMode.Dark) }
            )
        }
        SoftDivider()
        StepSectionLabel(strings.appearance.paletteTitle)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            BookVoicePalette.entries.forEach { option ->
                PaletteSwatch(
                    palette = option,
                    label = if (language == AppLanguage.En) option.labelEn else option.label,
                    selected = palette == option,
                    enabled = enabled,
                    onClick = { onPaletteChange(option) }
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        ToggleRow(
            title = strings.appearance.dynamicTitle,
            description = strings.appearance.dynamicDesc,
            checked = dynamicColor,
            enabled = enabled,
            onCheckedChange = onDynamicColorChange
        )
        SoftDivider()
        ToggleRow(
            title = strings.appearance.glassTitle,
            description = strings.appearance.glassDesc,
            checked = liquidGlass,
            enabled = enabled,
            onCheckedChange = onLiquidGlassChange
        )
        if (glassReason != null) {
            StepNote(String.format(strings.appearance.glassUnavailableFormat, glassReason))
        }
        StepNote(strings.appearance.note)
    }
}
