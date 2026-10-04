/* Derived from metal-fx, MIT Copyright (c) 2026 Jakub Antalik. See imports/metal-fx/LICENSE. */
package com.github.jershell.shadcn.components.metalbutton

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import com.composeunstyled.UnstyledButton
import com.composeunstyled.focusRing
import com.composeunstyled.theme.Theme
import com.github.jershell.shadcn.components.button.ButtonVariant
import com.github.jershell.shadcn.components.button.LocalButtonContentColor
import com.github.jershell.shadcn.components.button.LocalButtonTextStyle
import com.github.jershell.shadcn.components.button.resolveButtonColors
import com.github.jershell.shadcn.motion.LocalShadcnMotionEnabled
import com.github.jershell.shadcn.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.*

private data class MetalRenderFrame(val geometry: MetalGeometry, val mode: MetalMode, val density: Float,
    val pixels: MetalFrame, val glow: MetalGlowFrame)
private class MetalClock { var seconds = 0.0 }

/**
 * The metal-fx plasma ring on a native accessible button. Its five-octave noise, plasma field,
 * palette, blur, gamma, vignette, crop and hotspot glow derive from the pinned MIT source.
 * Canvas approximates the source bitmap with 64..512 perimeter samples and Gaussian stroke bands.
 * [paused] freezes the current frame; [active] lets hosts stop rendering inactive screens.
 * Offscreen, disabled and reduced-motion instances never run a repeating render loop.
 * [strength] changes effect opacity only; it never makes the button label disappear.
 * [buttonVariant] optionally uses the existing Shadcn button surface/foreground tokens.
 * Null retains metal-fx's original card surface. [ButtonVariant.Default] provides a solid primary action.
 */
@Composable
fun MetalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    preset: MetalPreset = MetalPreset.Chromatic,
    variant: MetalVariant = MetalVariant.Button,
    strength: Float = 1f,
    paused: Boolean = false,
    active: Boolean = true,
    disableGlow: Boolean = false,
    shaderScale: Float = if (variant == MetalVariant.Button) 1.6f else 1.3f,
    ringWidth: Dp = if (variant == MetalVariant.Button) BaseTokens.token1 else BaseTokens.token2,
    borderRadius: Dp? = null,
    scale: Float = 1f,
    animationsEnabled: Boolean = LocalShadcnMotionEnabled.current,
    buttonVariant: ButtonVariant? = null,
    content: @Composable RowScope.() -> Unit,
) {
    require(strength.isFinite() && strength in 0f..1f)
    require(shaderScale.isFinite() && shaderScale > 0f)
    require(scale.isFinite() && scale > 0f)
    require(ringWidth.value.isFinite() && ringWidth.value > 0f)
    require(borderRadius == null || (borderRadius.value.isFinite() && borderRadius.value >= 0f))
    val density = LocalDensity.current.density
    val dark by LocalThemeIsDark.current
    val mode = remember(preset, dark) { metalMode(preset, dark) }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    val buttonColors = buttonVariant?.let { resolveButtonColors(it, hovered, pressed) }
    val surface = buttonColors?.container ?: Theme[ColorProps][if (pressed) ColorTokens.accent else ColorTokens.card]
    val foreground = buttonColors?.content ?: Theme[ColorProps][ColorTokens.cardForeground]
    val focus = Theme[ColorProps][ColorTokens.ring]
    var pixels by remember { mutableStateOf(IntSize.Zero) }
    var visible by remember { mutableStateOf(false) }
    val width = pixels.width / density
    val height = pixels.height / density
    val shape = borderRadius?.let { RoundedCornerShape(it) } ?: RoundedCornerShape(50)
    val geometry = remember(width, height, borderRadius, variant, ringWidth, shaderScale, scale) {
        val half = (min(width, height) / 2).coerceAtLeast(0f)
        val radius = (borderRadius?.value ?: half).coerceAtMost(half)
        MetalGeometry(width, height, radius, variant, (ringWidth.value * scale).coerceAtMost(half), shaderScale * scale, scale)
    }
    val clock = remember { MetalClock() }
    val glowState = remember(geometry, mode) { MetalGlowState() }
    var frame by remember { mutableStateOf<MetalRenderFrame?>(null) }
    val animate = animationsEnabled && enabled && active && visible && !paused && strength > 0f
    LaunchedEffect(geometry, mode, density, animate) {
        if (geometry.width <= 0 || geometry.height <= 0) return@LaunchedEffect
        suspend fun render() {
            val seconds = clock.seconds
            val result = withContext(Dispatchers.Default) { createMetalFrame(geometry, seconds, mode, density) }
            frame = MetalRenderFrame(geometry, mode, density, result, glowState.update(geometry, result, seconds, dark))
        }
        // On a paused mount, draw a real first frame. Toggling pause never clears that frame.
        if (frame == null || frame?.geometry != geometry || frame?.mode != mode || frame?.density != density) render()
        if (animate) {
            var previous = withFrameNanos { it }
            while (isActive) {
                delay(66) // source perfConfig.ts: 66 ms (~15 fps), never render at display refresh rate
                val now = withFrameNanos { it }
                clock.seconds += ((now - previous) / 1_000_000_000.0).coerceIn(0.0, 0.25)
                previous = now
                render()
            }
        }
    }
    // Paint the base from current layout bounds; the asynchronous effect geometry must
    // never delay a button's surface (especially in a freshly mounted platform dialog).
    Box(modifier.alpha(if (enabled) 1f else 0.5f).background(surface, shape).onGloballyPositioned {
        pixels = it.size
        val bounds = it.boundsInWindow()
        visible = bounds.width > 0f && bounds.height > 0f
    }, propagateMinConstraints = true) {
        Canvas(Modifier.matchParentSize()) {
            scale(density, density, Offset.Zero) {
                frame?.takeIf { it.geometry == geometry }?.let { current ->
                    current.pixels.ring.zipWithNext { a, b ->
                        drawLine(a.color.color(strength * mode.opacity), a.point, b.point,
                            strokeWidth = geometry.ring, cap = StrokeCap.Round)
                    }
                    if (!disableGlow && strength > 0) drawMetalGlow(current.glow, geometry, strength, dark)
                }
                // Source's subtle inset rim, separate from the animated plasma layer.
                drawRoundRect(foreground.copy(alpha = if (dark) 0.1f else 0.06f),
                    topLeft = Offset(geometry.ring / 2, geometry.ring / 2),
                    size = Size((width - geometry.ring).coerceAtLeast(0f), (height - geometry.ring).coerceAtLeast(0f)),
                    cornerRadius = CornerRadius((geometry.radius - geometry.ring / 2).coerceAtLeast(0f)),
                    style = Stroke(geometry.ring))
            }
        }
        UnstyledButton(onClick = onClick, enabled = enabled, interactionSource = interaction,
            indication = null, role = Role.Button,
            modifier = Modifier.defaultMinSize(minWidth = BaseTokens.token40, minHeight = BaseTokens.token40)
                .focusRing(interactionSource = interaction, width = Effects.boxShadowFocusRing.spread,
                    color = focus, shape = shape),
        ) {
            CompositionLocalProvider(LocalButtonContentColor provides foreground,
                LocalButtonTextStyle provides TypographyStyles.textSmMedium) {
                Row(Modifier.padding(horizontal = if (variant == MetalVariant.Button) BaseTokens.token24 else BaseTokens.token8,
                    vertical = BaseTokens.token8), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(BaseTokens.token8), content = content)
            }
        }
    }
}

@Composable
fun MetalButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, preset: MetalPreset = MetalPreset.Chromatic,
    strength: Float = 1f, paused: Boolean = false, active: Boolean = true,
    animationsEnabled: Boolean = LocalShadcnMotionEnabled.current, buttonVariant: ButtonVariant? = null) {
    MetalButton(onClick, modifier, enabled, preset, strength = strength, paused = paused, active = active,
        animationsEnabled = animationsEnabled, buttonVariant = buttonVariant) {
        BasicText(text, style = TypographyStyles.textSmMedium.copy(color = LocalButtonContentColor.current))
    }
}

private fun DrawScope.drawMetalGlow(glow: MetalGlowFrame, geometry: MetalGeometry, strength: Float, dark: Boolean) {
    val outer = Path().apply { addRoundRect(RoundRect(0f, 0f, geometry.width, geometry.height, CornerRadius(geometry.radius))) }
    val inset = geometry.ring
    val inner = Path().apply { addRoundRect(RoundRect(inset, inset, geometry.width - inset, geometry.height - inset,
        CornerRadius((geometry.radius - inset).coerceAtLeast(0f)))) }
    val globalOpacity = if (dark) 0.7f else 0.2746f
    val brightness = if (geometry.variant == MetalVariant.Circle) 0.6 else 0.78
    val saturation = if (geometry.variant == MetalVariant.Circle) 7.5 else 5.355
    val tint = if (dark) glow.tint else saturate(glow.tint, saturation, brightness)
    val extra = if (dark) glow.extraTint else saturate(glow.extraTint, saturation, brightness)
    val blend = if (dark) BlendMode.SrcOver else BlendMode.Multiply
    fun DrawScope.paint(mask: Float) {
        val opacity = glow.opacity * strength * globalOpacity * mask
        rotate(glow.angleDegrees, glow.center) {
            val half = max(1f, 7.8f * glow.ratio)
            val a = glow.center - Offset(half, 0f); val b = glow.center + Offset(half, 0f)
            gaussianLine(a, b, tint, 26.4f * geometry.scale, 8.4f * geometry.scale, opacity * 0.8f * 0.385f, blend)
            gaussianLine(a, b, tint, 15.6f * geometry.scale, 4.8f * geometry.scale, opacity * 0.8f * 0.595f, blend)
            gaussianLine(a, b, tint, 7.2f * geometry.scale, 2.1f * geometry.scale, opacity * 0.8f * 0.7f, blend)
            gaussianLine(a, b, tint, 3f * geometry.scale, 0.9f * geometry.scale, opacity * 0.8f * 0.7f, blend)
        }
        rotate(glow.angleDegrees, glow.extraCenter) {
            val half = max(0.6f, 9.13952f / 3f * glow.ratio)
            val a = glow.extraCenter - Offset(half, 0f); val b = glow.extraCenter + Offset(half, 0f)
            val coreOpacity = min(1f, glow.opacity * 3.51f * strength) * globalOpacity * mask
            gaussianLine(a, b, extra, 4f / 3 * geometry.scale, 2f / 3 * geometry.scale, coreOpacity * 0.85f, blend,
                glow.extraCenter, 13f / 3 * geometry.scale)
            gaussianLine(a, b, extra, 2f / 3 * geometry.scale, 1.35f / 3 * geometry.scale, coreOpacity, blend,
                glow.extraCenter, 13f / 3 * geometry.scale)
        }
    }
    // Source ring mask: opaque on ring, transparent in hole, 50% beyond silhouette.
    clipPath(inner, ClipOp.Difference) {
        clipPath(outer) { paint(1f) }
        clipPath(outer, ClipOp.Difference) { paint(0.5f) }
    }
}

private fun erf(value: Double): Double {
    val sign = if (value < 0) -1 else 1
    val x = abs(value); val t = 1 / (1 + 0.3275911 * x)
    return sign * (1 - (((((1.061405429 * t - 1.453152027) * t) + 1.421413741) * t - 0.284496736) * t +
        0.254829592) * t * exp(-x * x))
}

/** Sample Gaussian convolution into nested round-cap bands, portable to every Canvas backend. */
private fun DrawScope.gaussianLine(a: Offset, b: Offset, tint: MetalRgb, stroke: Float, sigma: Float,
    opacity: Float, blend: BlendMode, fadeCenter: Offset? = null, fadeRadius: Float = 0f) {
    var previous = 0f
    val maximum = stroke / 2 + 3 * sigma
    for (index in 16 downTo 1) {
        val radius = maximum * index / 16
        val blur = (0.5 * (erf((radius + stroke / 2) / (sqrt(2.0) * sigma)) -
            erf((radius - stroke / 2) / (sqrt(2.0) * sigma)))).toFloat()
        val target = (blur * opacity).coerceIn(0f, 0.999f)
        val layer = ((target - previous) / (1 - previous)).coerceAtLeast(0f)
        previous = target
        if (fadeCenter == null) drawLine(tint.color(layer), a, b, radius * 2, StrokeCap.Round, blendMode = blend)
        else drawLine(Brush.radialGradient(0f to tint.color(), 0.3f to tint.color(),
            0.65f to tint.color(0.25f), 1f to tint.color(0f), center = fadeCenter, radius = fadeRadius),
            a, b, radius * 2, StrokeCap.Round, alpha = layer, blendMode = blend)
    }
}
