package io.github.mungbean2435.bookvoice.ui.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.lerp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 6 种取色风格只是预设种子色，不是 materialkolor 的 HCT 调色板。
 * 不新增依赖，所以色调由本工程自己推导；视觉上能区分即可。
 */
enum class BookVoicePalette(val label: String, val labelEn: String, val seed: Color) {
    Ink("墨蓝", "Ink", Color(0xFF1C4E7A)),
    Celadon("青瓷", "Celadon", Color(0xFF0F766E)),
    Amber("琥珀", "Amber", Color(0xFFA15C00)),
    Rose("玫瑰", "Rose", Color(0xFFB3263E)),
    Violet("紫藤", "Violet", Color(0xFF5B4BDB)),
    Graphite("石墨", "Graphite", Color(0xFF4F6070))
}

@Immutable
data class AppearanceState(
    val style: BookVoiceStyle = BookVoiceStyle.Clean,
    val dynamicColor: Boolean = true,
    val palette: BookVoicePalette = BookVoicePalette.Ink,
    /** 液态玻璃默认关；不可用或渲染失败时由渲染层回落档 2。 */
    val liquidGlass: Boolean = false,
    /** 返回手势预览：默认开；关闭后吞掉拖动进度，松手补发普通返回。 */
    val backGesturePreview: Boolean = true,
    /** 底部玻璃 / 镜头底色，ARGB；默认中性灰。 */
    val glassTint: Int = DefaultGlassTintArgb,
    /** 勾选后忽略 [glassTint]，直接跟随主题 primary。 */
    val glassTintFollowTheme: Boolean = false
)

/**
 * 外观设置独立于 TtsSettings 持久化，只改主题层，不碰播放/数据模型。
 */
object AppearanceSettings {
    private const val PREFS = "bookvoice_appearance"
    private const val KEY_STYLE = "style"
    private const val KEY_DYNAMIC = "dynamicColor"
    private const val KEY_PALETTE = "palette"
    private const val KEY_LIQUID_GLASS = "liquidGlass"
    private const val KEY_BACK_PREVIEW = "backGesturePreview"
    private const val KEY_GLASS_TINT = "glassTint"
    private const val KEY_GLASS_FOLLOW = "glassTintFollowTheme"

    private var prefs: SharedPreferences? = null
    private val _state = MutableStateFlow(AppearanceState())
    val state: StateFlow<AppearanceState> = _state.asStateFlow()

    fun init(context: Context) {
        if (prefs != null) return
        synchronized(this) {
            if (prefs != null) return
            val store = context.applicationContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            prefs = store
            _state.value = AppearanceState(
                style = store.getString(KEY_STYLE, null)
                    ?.let { runCatching { BookVoiceStyle.valueOf(it) }.getOrNull() }
                    ?: BookVoiceStyle.Clean,
                dynamicColor = store.getBoolean(KEY_DYNAMIC, true),
                palette = store.getString(KEY_PALETTE, null)
                    ?.let { runCatching { BookVoicePalette.valueOf(it) }.getOrNull() }
                    ?: BookVoicePalette.Ink,
                liquidGlass = store.getBoolean(KEY_LIQUID_GLASS, false),
                backGesturePreview = store.getBoolean(KEY_BACK_PREVIEW, true),
                glassTint = store.getInt(KEY_GLASS_TINT, DefaultGlassTintArgb),
                glassTintFollowTheme = store.getBoolean(KEY_GLASS_FOLLOW, false)
            )
        }
    }

    @Synchronized
    fun update(transform: (AppearanceState) -> AppearanceState) {
        val next = transform(_state.value)
        prefs?.edit()
            ?.putString(KEY_STYLE, next.style.name)
            ?.putBoolean(KEY_DYNAMIC, next.dynamicColor)
            ?.putString(KEY_PALETTE, next.palette.name)
            ?.putBoolean(KEY_LIQUID_GLASS, next.liquidGlass)
            ?.putBoolean(KEY_BACK_PREVIEW, next.backGesturePreview)
            ?.putInt(KEY_GLASS_TINT, next.glassTint)
            ?.putBoolean(KEY_GLASS_FOLLOW, next.glassTintFollowTheme)
            ?.apply()
        _state.value = next
    }

    /**
     * 与 [update] 同语义，但同步 commit 并返回是否真的落盘。
     * 引导里的外观步骤用它，失败时不能前进。
     */
    @Synchronized
    fun commit(transform: (AppearanceState) -> AppearanceState): Boolean {
        val next = transform(_state.value)
        val ok = prefs?.edit()
            ?.putString(KEY_STYLE, next.style.name)
            ?.putBoolean(KEY_DYNAMIC, next.dynamicColor)
            ?.putString(KEY_PALETTE, next.palette.name)
            ?.putBoolean(KEY_LIQUID_GLASS, next.liquidGlass)
            ?.putBoolean(KEY_BACK_PREVIEW, next.backGesturePreview)
            ?.putInt(KEY_GLASS_TINT, next.glassTint)
            ?.putBoolean(KEY_GLASS_FOLLOW, next.glassTintFollowTheme)
            ?.commit()
            ?: false
        if (ok) _state.value = next
        return ok
    }
}

/**
 * 由种子色推导 accent 角色；中性面仍沿用品牌色，避免 6 套色板各写一遍。
 * on* 颜色按亮度选黑白，保证至少不出现低对比的按钮字。
 */
internal fun schemeFor(palette: BookVoicePalette, dark: Boolean): ColorScheme {
    val base = if (dark) BrandDarkColors else BrandLightColors
    if (palette == BookVoicePalette.Ink) return base

    val seed = palette.seed
    val primary = if (dark) lerp(seed, Color.White, 0.35f) else seed
    val onPrimary = if (primary.luminance() > 0.5f) Color(0xFF101416) else Color.White
    val primaryContainer = if (dark) {
        lerp(seed, Color.Black, 0.55f)
    } else {
        lerp(seed, Color.White, 0.82f)
    }
    val onPrimaryContainer = if (dark) {
        lerp(seed, Color.White, 0.85f)
    } else {
        lerp(seed, Color.Black, 0.80f)
    }
    val secondary = if (dark) lerp(seed, Color.White, 0.20f) else lerp(seed, Color.Black, 0.20f)
    val onSecondary = if (secondary.luminance() > 0.5f) Color(0xFF101416) else Color.White
    val secondaryContainer = if (dark) {
        lerp(seed, Color.Black, 0.65f)
    } else {
        lerp(seed, Color.White, 0.86f)
    }
    val onSecondaryContainer = if (dark) {
        lerp(seed, Color.White, 0.90f)
    } else {
        lerp(seed, Color.Black, 0.80f)
    }

    return base.copy(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        inversePrimary = lerp(primary, Color.White, 0.35f),
        secondary = secondary,
        onSecondary = onSecondary,
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = onSecondaryContainer
    )
}
