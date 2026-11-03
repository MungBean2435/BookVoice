package io.github.mungbean2435.bookvoice.ui.startup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mungbean2435.bookvoice.data.settings.AppLanguage
import io.github.mungbean2435.bookvoice.data.settings.StartupSettings
import io.github.mungbean2435.bookvoice.data.settings.StartupStage
import io.github.mungbean2435.bookvoice.data.settings.startupStage
import io.github.mungbean2435.bookvoice.ui.AppRoot
import io.github.mungbean2435.bookvoice.ui.AppViewModel
import io.github.mungbean2435.bookvoice.ui.onboarding.DisclaimerScreen
import io.github.mungbean2435.bookvoice.ui.onboarding.LocalAppLanguage
import io.github.mungbean2435.bookvoice.ui.onboarding.OnboardingFlow

/**
 * 唯一启动入口：Loading → 免责 → 引导 → 主界面。
 * 两个布尔值读出来之前只画 Loading，主界面不会先闪一下。
 */
@Composable
fun StartupGate(
    vm: AppViewModel,
    onEnterMain: () -> Unit
) {
    val state by StartupSettings.state.collectAsStateWithLifecycle()
    val loadFailed by StartupSettings.loadFailed.collectAsStateWithLifecycle()
    val language = state?.language ?: AppLanguage.Zh

    CompositionLocalProvider(LocalAppLanguage provides language) {
        val loaded = state
        when {
            loaded == null -> StartupLoading(
                failed = loadFailed,
                onRetry = { StartupSettings.retryLoad() }
            )

            else -> when (startupStage(loaded)) {
                StartupStage.Loading -> StartupLoading(
                    failed = false,
                    onRetry = { StartupSettings.retryLoad() }
                )

                StartupStage.Disclaimer -> DisclaimerScreen(language = loaded.language)

                StartupStage.Onboarding -> OnboardingFlow(
                    vm = vm,
                    initialStep = loaded.onboardingStep
                )

                StartupStage.Main -> {
                    // 权限申请放在引导结束之后，避免首启还没解释就弹系统框。
                    LaunchedEffect(Unit) { onEnterMain() }
                    AppRoot(vm = vm)
                }
            }
        }
    }
}

@Composable
private fun StartupLoading(
    failed: Boolean,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                modifier = Modifier.size(64.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "听",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            if (failed) {
                Text(
                    text = "读取设置失败",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "还没有读到启动状态，先不进入主界面。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(2.dp))
                Button(onClick = onRetry) { Text("重试") }
            } else {
                CircularProgressIndicator()
                Text(
                    text = "正在准备…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
