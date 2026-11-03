package io.github.mungbean2435.bookvoice.ui.onboarding

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.mungbean2435.bookvoice.data.settings.AppLanguage

@Composable
internal fun LanguageStepContent(
    strings: OnboardingStrings,
    selected: AppLanguage,
    enabled: Boolean,
    onSelect: (AppLanguage) -> Unit
) {
    StepContent {
        OptionCard(
            title = strings.language.zh,
            description = strings.language.zhDesc,
            selected = selected == AppLanguage.Zh,
            enabled = enabled,
            onClick = { onSelect(AppLanguage.Zh) }
        )
        Spacer(Modifier.height(10.dp))
        OptionCard(
            title = strings.language.en,
            description = strings.language.enDesc,
            selected = selected == AppLanguage.En,
            enabled = enabled,
            onClick = { onSelect(AppLanguage.En) }
        )
        StepNote(strings.language.hint)
    }
}
