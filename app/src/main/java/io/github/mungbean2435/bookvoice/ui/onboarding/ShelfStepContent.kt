package io.github.mungbean2435.bookvoice.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.mungbean2435.bookvoice.data.settings.AppLanguage
import io.github.mungbean2435.bookvoice.data.settings.ShelfSource
import io.github.mungbean2435.bookvoice.ui.ImportTask
import io.github.mungbean2435.bookvoice.ui.components.ImportTaskCard

@Composable
internal fun ShelfStepContent(
    strings: OnboardingStrings,
    language: AppLanguage,
    selected: ShelfSource,
    importedCount: Int,
    importTask: ImportTask?,
    enabled: Boolean,
    onSelect: (ShelfSource) -> Unit,
    onPickFile: () -> Unit,
    onPickFolder: () -> Unit,
    onCancelImport: () -> Unit,
    onRetryImport: () -> Unit,
    onDismissImport: () -> Unit
) {
    val busy = importTask?.running == true
    StepContent {
        OptionCard(
            title = strings.shelf.emptyTitle,
            description = strings.shelf.emptyDesc,
            selected = selected == ShelfSource.Empty,
            enabled = enabled,
            onClick = { onSelect(ShelfSource.Empty) }
        )
        Spacer(Modifier.height(10.dp))
        OptionCard(
            title = strings.shelf.localTitle,
            description = strings.shelf.localDesc,
            selected = selected == ShelfSource.LocalFiles,
            enabled = enabled,
            onClick = { onSelect(ShelfSource.LocalFiles) }
        )
        if (selected == ShelfSource.LocalFiles) {
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onPickFile,
                    enabled = enabled && !busy,
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                ) {
                    Text(strings.shelf.pick)
                }
                OutlinedButton(
                    onClick = onPickFolder,
                    enabled = enabled && !busy,
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                ) {
                    Text(strings.shelf.pickFolder)
                }
            }
            if (importTask != null) {
                Spacer(Modifier.height(12.dp))
                ImportTaskCard(
                    task = importTask,
                    language = language,
                    onCancel = onCancelImport,
                    onRetry = onRetryImport,
                    onDismiss = onDismissImport,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (importedCount > 0 && importTask == null) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringFormat(strings.shelf.importedFormat, importedCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        StepNote(strings.shelf.note)
    }
}

private fun stringFormat(format: String, vararg args: Any): String =
    runCatching { String.format(format, *args) }.getOrDefault(format)
