package io.github.mungbean2435.bookvoice.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import io.github.mungbean2435.bookvoice.ui.theme.LocalHasCustomBackground
import io.github.mungbean2435.bookvoice.ui.theme.BookVoiceDesign

/**
 * 玻璃浮层的无模糊兜底：不透明时就是 surfaceContainerLow，
 * 有自定义背景图时降到 0.72，并保留一条极细的 onSurface 描边。
 *
 * 真正的模糊引擎不引入；视觉上只靠底色、透明度和描边分层。
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val tokens = BookVoiceDesign.tokens
    val resolvedShape = shape ?: tokens.groupShape
    val alpha = if (LocalHasCustomBackground.current) {
        tokens.glassAlphaWithCustomBackground
    } else {
        1f
    }
    val colors = MaterialTheme.colorScheme

    Box(
        modifier = modifier
            .clip(resolvedShape)
            .background(colors.surfaceContainerLow.copy(alpha = alpha))
            .border(
                width = tokens.glassStrokeWidth,
                color = colors.onSurface.copy(alpha = tokens.glassStrokeAlpha),
                shape = resolvedShape
            ),
        content = content
    )
}
