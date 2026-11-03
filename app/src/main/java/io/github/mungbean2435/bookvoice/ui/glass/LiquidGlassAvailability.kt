package io.github.mungbean2435.bookvoice.ui.glass

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.PowerManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * 读取「液态玻璃」能否开启：API 33+、非省电、非低内存。
 * 省电模式变化会实时更新，退到设置页时开关能立刻恢复/置灰。
 */
@Composable
fun rememberLiquidGlassAvailability(): LiquidGlassAvailability {
    val context = LocalContext.current
    val appContext = remember(context) { context.applicationContext }
    val powerManager = remember(appContext) {
        appContext.getSystemService(PowerManager::class.java)
    }
    var powerSave by remember(powerManager) {
        mutableStateOf(powerManager?.isPowerSaveMode == true)
    }

    DisposableEffect(appContext, powerManager) {
        if (powerManager == null) return@DisposableEffect onDispose {}
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                powerSave = powerManager.isPowerSaveMode
            }
        }
        runCatching {
            ContextCompat.registerReceiver(
                appContext,
                receiver,
                IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }
        onDispose { runCatching { appContext.unregisterReceiver(receiver) } }
    }

    val lowRam = remember(appContext) {
        appContext.getSystemService(ActivityManager::class.java)?.isLowRamDevice == true
    }

    return remember(powerSave, lowRam) {
        LiquidGlassAvailability(
            sdkInt = Build.VERSION.SDK_INT,
            powerSave = powerSave,
            lowRam = lowRam
        )
    }
}
