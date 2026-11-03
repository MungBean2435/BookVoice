package io.github.mungbean2435.bookvoice.ui.onboarding

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun GuideStepContent(
    strings: OnboardingStrings
) {
    StepContent {
        strings.guide.cards.forEachIndexed { index, card ->
            GuideCard(index = index, title = card.first, body = card.second)
            if (index != strings.guide.cards.lastIndex) {
                Spacer(Modifier.height(10.dp))
            }
        }
        StepNote(strings.guide.note)
    }
}
