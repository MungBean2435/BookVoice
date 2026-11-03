package io.github.mungbean2435.bookvoice

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.mungbean2435.bookvoice.data.settings.Settings
import io.github.mungbean2435.bookvoice.data.settings.StartupSettings
import io.github.mungbean2435.bookvoice.data.settings.ThemeMode
import io.github.mungbean2435.bookvoice.ui.AppViewModel
import io.github.mungbean2435.bookvoice.ui.startup.StartupGate
import io.github.mungbean2435.bookvoice.ui.theme.BrandDarkColors
import io.github.mungbean2435.bookvoice.ui.theme.BrandLightColors
import io.github.mungbean2435.bookvoice.ui.theme.ThemeRevealHost
import io.github.mungbean2435.bookvoice.ui.theme.BookVoiceTheme

class MainActivity : ComponentActivity() {

    private var notificationRequested = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val initialDark = resolveInitialDark()
        window.setBackgroundDrawable(
            ColorDrawable(
                if (initialDark) BrandDarkColors.background.toArgb()
                else BrandLightColors.background.toArgb()
            )
        )
        enableEdgeToEdge()
        applyBarAppearance(initialDark)
        setContent {
            val systemDark = isSystemInDarkTheme()
            val themeMode by Settings.themeMode.collectAsStateWithLifecycle()
            val dark = when (themeMode) {
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
                ThemeMode.FollowSystem -> systemDark
            }
            LaunchedEffect(dark) { applyBarAppearance(dark) }
            BookVoiceTheme(darkTheme = dark) {
                ThemeRevealHost {
                    StartupGate(
                        vm = viewModel<AppViewModel>(),
                        onEnterMain = { ensureNotificationPermissionOnce() }
                    )
                }
            }
        }
    }

    private fun resolveInitialDark(): Boolean = when (Settings.state.value.themeMode) {
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
        ThemeMode.FollowSystem ->
            (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
    }

    private fun applyBarAppearance(dark: Boolean) {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }

    /**
     * 只在引导结束、主界面出现后调用一次；持久化 notificationAsked，
     * 避免每次启动都弹系统权限框。
     */
    private fun ensureNotificationPermissionOnce() {
        if (notificationRequested) return
        notificationRequested = true
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (StartupSettings.state.value?.notificationAsked == true) return
        StartupSettings.markNotificationAsked()
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
    }

    private companion object {
        private const val REQUEST_NOTIFICATIONS = 100
    }
}
