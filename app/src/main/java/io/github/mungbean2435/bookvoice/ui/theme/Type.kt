package io.github.mungbean2435.bookvoice.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * 中文优先的字阶：标题收紧、行高放宽。
 * 阅读正文不使用这里的 bodyLarge，仍然由设置里的 fontSizeSp 决定。
 */
internal val BookVoiceTypography = Typography(
    headlineMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp
    ),
    headlineSmall = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 28.sp
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    titleSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp
    )
)

/** Clean 字阶：字号梯度更平，层级主要靠字重，长文行高更松。 */
private val CleanBase = Typography()

private fun TextStyle.clean(
    size: Float,
    lineHeight: Float,
    weight: FontWeight,
    tracking: Float
): TextStyle = copy(
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    letterSpacing = tracking.em
)

internal val CleanTypography = Typography(
    displayLarge = CleanBase.displayLarge.clean(52f, 58f, FontWeight.Bold, -0.022f),
    displayMedium = CleanBase.displayMedium.clean(42f, 48f, FontWeight.Bold, -0.020f),
    displaySmall = CleanBase.displaySmall.clean(34f, 40f, FontWeight.Bold, -0.018f),
    headlineLarge = CleanBase.headlineLarge.clean(30f, 36f, FontWeight.Bold, -0.016f),
    headlineMedium = CleanBase.headlineMedium.clean(26f, 32f, FontWeight.Bold, -0.014f),
    headlineSmall = CleanBase.headlineSmall.clean(22f, 28f, FontWeight.SemiBold, -0.012f),
    titleLarge = CleanBase.titleLarge.clean(20f, 26f, FontWeight.SemiBold, -0.010f),
    titleMedium = CleanBase.titleMedium.clean(16.5f, 22f, FontWeight.SemiBold, -0.006f),
    titleSmall = CleanBase.titleSmall.clean(14.5f, 20f, FontWeight.Medium, -0.004f),
    bodyLarge = CleanBase.bodyLarge.clean(16f, 23f, FontWeight.Normal, -0.006f),
    bodyMedium = CleanBase.bodyMedium.clean(14f, 20f, FontWeight.Normal, -0.002f),
    bodySmall = CleanBase.bodySmall.clean(12.5f, 17f, FontWeight.Normal, 0f),
    labelLarge = CleanBase.labelLarge.clean(14.5f, 20f, FontWeight.SemiBold, -0.004f),
    labelMedium = CleanBase.labelMedium.clean(12.5f, 16f, FontWeight.Medium, 0f),
    labelSmall = CleanBase.labelSmall.clean(11f, 14f, FontWeight.Medium, 0.010f)
)
