package com.github.jershell.shadcn.components.promptkit

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import com.composeunstyled.theme.Theme
import com.github.jershell.shadcn.theme.*
import com.github.jershell.shadcn.motion.LocalShadcnMotionEnabled
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Applications can disable decorative motion for accessibility or low-power mode. */
val LocalPromptKitMotionEnabled = LocalShadcnMotionEnabled

enum class LoaderVariant {
    Circular, Classic, Pulse, PulseDot, Dots, Typing, Wave, Bars, Terminal,
    TextBlink, TextShimmer, LoadingDots,
}
enum class LoaderSize { Sm, Md, Lg }

@Composable
private fun animationPhase(enabled: Boolean, durationMillis: Int): Float {
    if (!enabled) return 0.35f
    val transition = rememberInfiniteTransition(label = "prompt status")
    return transition.animateFloat(0f, 1f,
        infiniteRepeatable(tween(durationMillis, easing = LinearEasing)), label = "phase").value
}

/** The complete prompt-kit loader family. Reduced motion renders a static recognizable glyph. */
@Composable
fun Loader(
    modifier: Modifier = Modifier,
    variant: LoaderVariant = LoaderVariant.Circular,
    size: LoaderSize = LoaderSize.Md,
    text: String = "Thinking",
    loadingLabel: String = "Loading",
    animationsEnabled: Boolean = LocalPromptKitMotionEnabled.current,
) {
    val dimension = when (size) {
        LoaderSize.Sm -> BaseTokens.token16
        LoaderSize.Md -> BaseTokens.token20
        LoaderSize.Lg -> BaseTokens.token24
    }
    val textStyle = when (size) {
        LoaderSize.Sm -> TypographyStyles.textXsRegular
        LoaderSize.Md -> TypographyStyles.textSmRegular
        LoaderSize.Lg -> TypographyStyles.textBaseRegular
    }
    val color = Theme[ColorProps][ColorTokens.primary]
    val muted = Theme[ColorProps][ColorTokens.mutedForeground]
    val isText = variant == LoaderVariant.TextBlink || variant == LoaderVariant.TextShimmer ||
        variant == LoaderVariant.LoadingDots
    val status = modifier.clearAndSetSemantics {
        contentDescription = if (isText) text else loadingLabel
        progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
        liveRegion = LiveRegionMode.Polite
    }
    if (variant == LoaderVariant.TextShimmer) {
        TextShimmer(text, status, style = textStyle, animationsEnabled = animationsEnabled)
        return
    }
    val phase = animationPhase(animationsEnabled, when (variant) {
        LoaderVariant.Typing, LoaderVariant.Wave, LoaderVariant.Terminal -> 1000
        LoaderVariant.TextBlink -> 2000
        LoaderVariant.Pulse -> 1500
        LoaderVariant.Dots, LoaderVariant.LoadingDots -> 1400
        else -> 1200
    })
    if (variant == LoaderVariant.TextBlink) {
        BasicText(text, status, textStyle.copy(color = androidx.compose.ui.graphics.lerp(muted, color,
            if (animationsEnabled) ((cos(phase * 2 * PI) + 1) / 2).toFloat() else 1f)))
        return
    }
    if (variant == LoaderVariant.LoadingDots) {
        Row(status, verticalAlignment = Alignment.CenterVertically) {
            BasicText(text, style = textStyle.copy(color = color))
            repeat(3) { index ->
                val alpha = if (!animationsEnabled || (phase - index * 0.14f + 1) % 1f < 0.55f) 1f else 0f
                BasicText(".", style = textStyle.copy(color = color.copy(alpha = alpha)))
            }
        }
        return
    }
    Row(status, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(BaseTokens.token4)) {
        if (variant == LoaderVariant.Terminal) BasicText(">", style = textStyle.copy(color = color, fontFamily = FontFamily.Monospace))
        Canvas(Modifier.size(width = if (variant == LoaderVariant.Wave) dimension * 1.5f else dimension,
            height = dimension)) {
            val w = this.size.width
            val h = this.size.height
            val stroke = BaseTokens.token2.toPx()
            fun pulse(offset: Float): Float = ((sin((phase - offset) * 2 * PI) + 1) / 2).toFloat()
            when (variant) {
                LoaderVariant.Circular -> drawArc(color, phase * 360f, 270f, false,
                    topLeft = Offset(stroke / 2, stroke / 2), size = Size(w - stroke, h - stroke), style = Stroke(stroke, cap = StrokeCap.Round))
                LoaderVariant.Classic -> repeat(12) { index ->
                    rotate(index * 30f) {
                        drawLine(color.copy(alpha = 0.15f + 0.85f * ((phase - index / 12f + 1) % 1f)),
                            Offset(w / 2, stroke), Offset(w / 2, h * 0.28f), stroke, StrokeCap.Round)
                    }
                }
                LoaderVariant.Pulse -> drawCircle(color.copy(alpha = 0.4f + 0.4f * pulse(0f)),
                    radius = (w / 2 - stroke) * (0.85f + 0.15f * pulse(0f)), style = Stroke(stroke))
                LoaderVariant.PulseDot -> drawCircle(color.copy(alpha = 0.8f), radius = w * (0.15f + 0.1f * pulse(0f)))
                LoaderVariant.Dots, LoaderVariant.Typing -> repeat(3) { index ->
                    val amount = if (animationsEnabled) pulse(index * 0.16f) else 0.5f
                    val radius = w / 10 * if (variant == LoaderVariant.Dots) (0.8f + amount * 0.4f) else 1f
                    drawCircle(color.copy(alpha = 0.5f + amount * 0.5f), radius,
                        Offset(w * (index + 0.5f) / 3, h / 2 - if (variant == LoaderVariant.Typing) amount * stroke else 0f))
                }
                LoaderVariant.Wave, LoaderVariant.Bars -> {
                    val count = if (variant == LoaderVariant.Wave) 5 else 3
                    repeat(count) { index ->
                        val baseline = if (count == 5) (1f - kotlin.math.abs(index - 2) * 0.2f) else 1f
                        val barHeight = h * baseline * (0.5f + 0.5f * pulse(index * 0.12f))
                        val barWidth = w / (count * 2)
                        drawRoundRect(color, Offset(index * w / count + barWidth / 2, (h - barHeight) / 2),
                            Size(barWidth, barHeight), cornerRadius = androidx.compose.ui.geometry.CornerRadius(if (count == 5) barWidth else 0f))
                    }
                }
                LoaderVariant.Terminal -> drawRect(color.copy(alpha = if (!animationsEnabled || phase < 0.5f) 1f else 0f),
                    topLeft = Offset(w / 4, h / 8), size = Size(w / 2, h * 0.75f))
                else -> Unit
            }
        }
    }
}

/** Gradient stays within the text glyphs; semantic text is independent of animation. */
@Composable
fun TextShimmer(
    text: String,
    modifier: Modifier = Modifier,
    durationMillis: Int = 4000,
    spread: Float = 20f,
    style: TextStyle = TypographyStyles.textSmMedium,
    animationsEnabled: Boolean = LocalPromptKitMotionEnabled.current,
) {
    require(durationMillis > 0)
    require(spread.isFinite())
    val fraction = spread.coerceIn(5f, 45f) / 100f
    val muted = Theme[ColorProps][ColorTokens.mutedForeground]
    val foreground = Theme[ColorProps][ColorTokens.foreground]
    val phase = animationPhase(animationsEnabled, durationMillis)
    var width by remember { mutableFloatStateOf(1f) }
    val center = if (animationsEnabled) width * (2f - 3f * phase) else width / 2
    val brush = Brush.linearGradient(
        0f to muted, (0.5f - fraction) to muted, 0.5f to foreground,
        (0.5f + fraction) to muted, 1f to muted,
        start = Offset(center - width, 0f), end = Offset(center + width, 0f))
    BasicText(text, modifier.onSizeChanged { width = it.width.toFloat().coerceAtLeast(1f) },
        style.copy(brush = brush))
}

@Composable fun CircularLoader(modifier: Modifier = Modifier, size: LoaderSize = LoaderSize.Md, animationsEnabled: Boolean = LocalPromptKitMotionEnabled.current) = Loader(modifier, LoaderVariant.Circular, size, animationsEnabled = animationsEnabled)
@Composable fun ClassicLoader(modifier: Modifier = Modifier, size: LoaderSize = LoaderSize.Md, animationsEnabled: Boolean = LocalPromptKitMotionEnabled.current) = Loader(modifier, LoaderVariant.Classic, size, animationsEnabled = animationsEnabled)
@Composable fun PulseLoader(modifier: Modifier = Modifier, size: LoaderSize = LoaderSize.Md, animationsEnabled: Boolean = LocalPromptKitMotionEnabled.current) = Loader(modifier, LoaderVariant.Pulse, size, animationsEnabled = animationsEnabled)
@Composable fun PulseDotLoader(modifier: Modifier = Modifier, size: LoaderSize = LoaderSize.Md, animationsEnabled: Boolean = LocalPromptKitMotionEnabled.current) = Loader(modifier, LoaderVariant.PulseDot, size, animationsEnabled = animationsEnabled)
@Composable fun DotsLoader(modifier: Modifier = Modifier, size: LoaderSize = LoaderSize.Md, animationsEnabled: Boolean = LocalPromptKitMotionEnabled.current) = Loader(modifier, LoaderVariant.Dots, size, animationsEnabled = animationsEnabled)
@Composable fun TypingLoader(modifier: Modifier = Modifier, size: LoaderSize = LoaderSize.Md, animationsEnabled: Boolean = LocalPromptKitMotionEnabled.current) = Loader(modifier, LoaderVariant.Typing, size, animationsEnabled = animationsEnabled)
@Composable fun WaveLoader(modifier: Modifier = Modifier, size: LoaderSize = LoaderSize.Md, animationsEnabled: Boolean = LocalPromptKitMotionEnabled.current) = Loader(modifier, LoaderVariant.Wave, size, animationsEnabled = animationsEnabled)
@Composable fun BarsLoader(modifier: Modifier = Modifier, size: LoaderSize = LoaderSize.Md, animationsEnabled: Boolean = LocalPromptKitMotionEnabled.current) = Loader(modifier, LoaderVariant.Bars, size, animationsEnabled = animationsEnabled)
@Composable fun TerminalLoader(modifier: Modifier = Modifier, size: LoaderSize = LoaderSize.Md, animationsEnabled: Boolean = LocalPromptKitMotionEnabled.current) = Loader(modifier, LoaderVariant.Terminal, size, animationsEnabled = animationsEnabled)
@Composable fun TextBlinkLoader(text: String = "Thinking", modifier: Modifier = Modifier, size: LoaderSize = LoaderSize.Md, animationsEnabled: Boolean = LocalPromptKitMotionEnabled.current) = Loader(modifier, LoaderVariant.TextBlink, size, text, animationsEnabled = animationsEnabled)
@Composable fun TextShimmerLoader(text: String = "Thinking", modifier: Modifier = Modifier, size: LoaderSize = LoaderSize.Md, animationsEnabled: Boolean = LocalPromptKitMotionEnabled.current) = Loader(modifier, LoaderVariant.TextShimmer, size, text, animationsEnabled = animationsEnabled)
@Composable fun TextDotsLoader(text: String = "Thinking", modifier: Modifier = Modifier, size: LoaderSize = LoaderSize.Md, animationsEnabled: Boolean = LocalPromptKitMotionEnabled.current) = Loader(modifier, LoaderVariant.LoadingDots, size, text, animationsEnabled = animationsEnabled)
