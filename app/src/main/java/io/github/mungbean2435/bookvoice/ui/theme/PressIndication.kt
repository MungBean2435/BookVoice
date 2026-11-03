package io.github.mungbean2435.bookvoice.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import kotlinx.coroutines.launch

/**
 * Clean 风格的按压反馈：按下时内容向内收，松手回弹，并叠一层很淡的高亮。
 *
 * 缩放与高亮都发生在绘制阶段，只触发重绘，不触发重组或重新测量。
 */
internal class BookVoicePressIndication(
    private val tokens: BookVoiceTokens,
    private val highlightColor: State<Color>
) : IndicationNodeFactory {

    override fun create(interactionSource: InteractionSource): DelegatableNode =
        BookVoicePressIndicationNode(interactionSource, tokens, highlightColor)

    override fun equals(other: Any?): Boolean =
        other is BookVoicePressIndication &&
            other.tokens == tokens &&
            other.highlightColor == highlightColor

    override fun hashCode(): Int = 31 * tokens.hashCode() + highlightColor.hashCode()
}

private class BookVoicePressIndicationNode(
    private val interactionSource: InteractionSource,
    private val tokens: BookVoiceTokens,
    private val highlightColor: State<Color>
) : Modifier.Node(), DrawModifierNode {

    private val pressProgress = Animatable(0f)

    override fun onAttach() {
        coroutineScope.launch {
            var activePresses = 0
            interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> activePresses++
                    is PressInteraction.Release,
                    is PressInteraction.Cancel -> activePresses = (activePresses - 1).coerceAtLeast(0)
                    else -> return@collect
                }
                val target = if (activePresses > 0) 1f else 0f
                launch {
                    pressProgress.animateTo(
                        targetValue = target,
                        animationSpec = if (target > 0f) {
                            tokens.motion.pressInSpring
                        } else {
                            tokens.motion.pressOutSpring
                        }
                    ) {
                        invalidateDraw()
                    }
                }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        val progress = pressProgress.value
        if (progress <= 0.001f) {
            drawContent()
            return
        }
        val longestSide = maxOf(size.width, size.height).coerceAtLeast(1f)
        val insetPx = tokens.pressInset.toPx() * 2f
        val shrink = (insetPx / longestSide).coerceAtMost(tokens.pressMaxShrink)
        val scaleFactor = 1f - shrink * progress
        scale(scaleFactor) {
            this@draw.drawContent()
            val alpha = (tokens.pressHighlightAlpha * progress)
                .coerceIn(0f, tokens.pressHighlightAlpha)
            if (alpha > 0f) {
                drawRect(highlightColor.value.copy(alpha = alpha))
            }
        }
    }
}
