package io.github.mungbean2435.bookvoice.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.util.Locale

private val SPEED_OPTIONS = listOf(0.8f, 1.0f, 1.25f, 1.5f, 2.0f)
private val SLEEP_OPTIONS = listOf(0, 15, 30, 45, 60)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PlaybackStepContent(
    strings: OnboardingStrings,
    skipSilence: Boolean,
    speed: Float,
    sleepMinutes: Int,
    enabled: Boolean,
    onSkipSilenceChange: (Boolean) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onSleepChange: (Int) -> Unit
) {
    StepContent {
        ToggleRow(
            title = strings.playback.skipTitle,
            description = strings.playback.skipDesc,
            checked = skipSilence,
            enabled = enabled,
            onCheckedChange = onSkipSilenceChange
        )
        SoftDivider()
        StepSectionLabel(strings.playback.speedTitle)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SPEED_OPTIONS.forEach { option ->
                ChoiceChip(
                    label = speedLabel(option),
                    selected = kotlin.math.abs(speed - option) < 0.001f,
                    enabled = enabled,
                    onClick = { onSpeedChange(option) }
                )
            }
        }
        StepNote(strings.playback.speedDesc)
        Spacer(Modifier.height(10.dp))
        StepSectionLabel(strings.playback.sleepTitle)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SLEEP_OPTIONS.forEach { minutes ->
                ChoiceChip(
                    label = if (minutes == 0) {
                        strings.playback.sleepOff
                    } else {
                        String.format(strings.playback.minutesFormat, minutes)
                    },
                    selected = sleepMinutes == minutes,
                    enabled = enabled,
                    onClick = { onSleepChange(minutes) }
                )
            }
        }
        StepNote(strings.playback.sleepDesc)
        StepNote(strings.playback.note)
    }
}

private fun speedLabel(value: Float): String = when {
    kotlin.math.abs(value - 1.0f) < 0.001f -> "1.0×"
    kotlin.math.abs(value - 1.25f) < 0.001f -> "1.25×"
    kotlin.math.abs(value - 1.5f) < 0.001f -> "1.5×"
    kotlin.math.abs(value - 2.0f) < 0.001f -> "2.0×"
    else -> String.format(Locale.US, "%.1f×", value)
}
