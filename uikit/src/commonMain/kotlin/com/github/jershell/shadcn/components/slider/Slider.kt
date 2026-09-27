package com.github.jershell.shadcn.components.slider

import com.github.jershell.shadcn.motion.motionDurationMillis
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.composeunstyled.UnstyledSlider
import com.composeunstyled.theme.Theme
import com.github.jershell.shadcn.generated.resources.Res
import com.github.jershell.shadcn.generated.resources.range_slider_end
import com.github.jershell.shadcn.generated.resources.range_slider_start
import com.github.jershell.shadcn.theme.BaseTokens
import com.github.jershell.shadcn.theme.DimProps
import com.github.jershell.shadcn.theme.DimTokens
import com.github.jershell.shadcn.theme.Effects
import com.github.jershell.shadcn.theme.TwDimensions
import kotlin.math.abs
import org.jetbrains.compose.resources.stringResource

/** Sizes per the Figma layout: 4px track height, 10px thumb diameter. */
internal val SliderTrackSize: androidx.compose.ui.unit.Dp = BaseTokens.token4
internal val SliderThumbSize: androidx.compose.ui.unit.Dp = BaseTokens.token10

/**
 * A range input styled after the shadcn/ui Slider: a `h-1.5 rounded-full bg-muted`
 * track with a `bg-primary` filled range and a `size-4` circular thumb with a
 * white background and `ring-ring/50` hover/focus ring.
 *
 * Built on [com.composeunstyled.UnstyledSlider], which provides dragging, tap to
 * jump, keyboard stepping (arrows, Home/End, PageUp/PageDown) and progress semantics.
 *
 * @param value Current value within [valueRange].
 * @param onValueChange Called while the value changes (drag, tap, keyboard).
 * @param modifier Modifier applied to the slider root.
 * @param enabled Whether the slider is interactive; disabled sliders are dimmed.
 * @param valueRange Allowed value range.
 * @param steps Number of discrete steps between the range ends; `0` for a continuous slider.
 * @param onValueChangeFinished Called when a gesture or keyboard interaction finishes.
 * @param orientation Track orientation; vertical sliders need externally bounded height.
 */
@Composable
fun Slider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
    orientation: Orientation = Orientation.Horizontal,
) {
    val colors = resolveSliderColors()
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val borderWidth = Theme[DimProps][DimTokens.borderWidth]

    UnstyledSlider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .then(
                when (orientation) {
                    // w-full
                    Orientation.Horizontal -> Modifier.fillMaxWidth()
                    // data-[orientation=vertical]:min-h-44
                    Orientation.Vertical -> Modifier.heightIn(min = BaseTokens.token176)
                },
            )
            .alpha(if (enabled) 1f else 0.5f) // data-[disabled]:opacity-50
            .hoverable(interactionSource = interactionSource, enabled = enabled),
        enabled = enabled,
        interactionSource = interactionSource,
        valueRange = valueRange,
        steps = steps,
        onValueChangeFinished = onValueChangeFinished,
        orientation = orientation,
        track = { state ->
            SliderTrack(
                state = state,
                orientation = orientation,
                colors = colors,
            )
        },
        thumb = { state ->
            SliderThumb(
                ringVisible = isHovered || state.isPressed || state.isFocused,
                colors = colors,
                borderWidth = borderWidth,
            )
        },
    )
}

/**
 * A two-thumb range slider styled after the shadcn/ui Slider: taps and drags are
 * routed to the nearest thumb. Each thumb is independently focusable and exposes
 * progress semantics; Tab selects the other thumb and arrows adjust its value.
 *
 * The [com.composeunstyled.UnstyledSlider] primitive is single-valued, so this
 * composable implements the gesture layer on top of the same styled track and thumbs.
 *
 * @param value Current selected range within [valueRange]; `value.start <= value.endInclusive`.
 * @param onValueChange Called while the range changes.
 * @param modifier Modifier applied to the slider root.
 * @param enabled Whether the slider is interactive; disabled sliders are dimmed.
 * @param valueRange Allowed value range.
 * @param steps Number of discrete steps between the range ends; `0` for a continuous slider.
 * @param onValueChangeFinished Called when a gesture or keyboard interaction finishes.
 */
@Composable
fun RangeSlider(
    value: ClosedFloatingPointRange<Float>,
    onValueChange: (ClosedFloatingPointRange<Float>) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    require(steps >= 0) { "steps must be nonnegative" }
    require(valueRange.start.isFinite() && valueRange.endInclusive.isFinite() && valueRange.start <= valueRange.endInclusive && (valueRange.endInclusive - valueRange.start).isFinite()) {
        "valueRange must be finite and ordered"
    }
    require(value.start.isFinite() && value.endInclusive.isFinite() && value.start <= value.endInclusive) {
        "value must be finite and ordered"
    }
    val colors = resolveSliderColors()
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    var focusedThumb by remember { mutableIntStateOf(-1) }
    var dragging by remember { mutableStateOf(false) }
    val thumbFocus = remember { listOf(FocusRequester(), FocusRequester()) }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val startLabel = stringResource(Res.string.range_slider_start)
    val endLabel = stringResource(Res.string.range_slider_end)
    val borderWidth = Theme[DimProps][DimTokens.borderWidth]
    // 0 = lower (start) bound, 1 = upper (endInclusive)
    var activeThumb by remember { mutableIntStateOf(0) }
    var rootSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    // The gesture layer outlives a single composition: read the value through
    // State, otherwise the commit functions see "frozen" range bounds.
    val currentValue by rememberUpdatedState(value.start.coerceIn(valueRange)..value.endInclusive.coerceIn(valueRange))
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentOnFinished by rememberUpdatedState(onValueChangeFinished)

    val thumbRadiusPx = with(density) { (SliderThumbSize / 2).roundToPx() }
    val trackSpanPx = (rootSize.width - 2 * thumbRadiusPx).coerceAtLeast(0)
    fun fractionOf(v: Float): Float = if (valueRange.endInclusive - valueRange.start == 0f) {
        0f
    } else {
        ((v - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
    }
    fun snap(v: Float): Float {
        val coerced = v.coerceIn(valueRange.start, valueRange.endInclusive)
        if (steps == 0 || valueRange.start == valueRange.endInclusive) return coerced
        val tickCount = steps.toFloat() + 1f
        val stepSize = (valueRange.endInclusive - valueRange.start) / tickCount
        val index = ((coerced - valueRange.start) / stepSize).let { kotlin.math.floor(it + 0.5f) }
        return valueRange.start + index * stepSize
    }
    fun valueAt(xPx: Float): Float {
        val clamped = xPx.coerceIn(0f, trackSpanPx.toFloat())
        val physicalFraction = if (trackSpanPx == 0) 0f else clamped / trackSpanPx
        val fraction = if (rtl) 1f - physicalFraction else physicalFraction
        return valueRange.start + fraction * (valueRange.endInclusive - valueRange.start)
    }
    fun lowerFraction() = fractionOf(currentValue.start)
    fun upperFraction() = fractionOf(currentValue.endInclusive)

    fun commit(thumb: Int, target: Float): Boolean {
        if (!enabled || !target.isFinite()) return false
        val range = currentValue
        val next = if (thumb == 0) snap(target).coerceAtMost(range.endInclusive)..range.endInclusive
            else range.start..snap(target).coerceAtLeast(range.start)
        if (next == range) return false
        currentOnValueChange(next)
        return true
    }
    // Pointer handlers keep running through recompositions and layout changes.
    val currentCommit by rememberUpdatedState(::commit)
    val currentValueAt by rememberUpdatedState(::valueAt)
    fun thumbModifier(thumb: Int): Modifier {
        val lower = thumb == 0
        val bound = if (lower) currentValue.start else currentValue.endInclusive
        val bounds = if (lower) valueRange.start..currentValue.endInclusive else currentValue.start..valueRange.endInclusive
        fun finishChange(target: Float): Boolean = commit(thumb, target).also { changed ->
            if (changed) currentOnFinished?.invoke()
        }
        return Modifier
            .semantics {
                contentDescription = if (lower) startLabel else endLabel
                progressBarRangeInfo = ProgressBarRangeInfo(bound, bounds)
                if (!enabled) disabled()
                setProgress { finishChange(it) }
            }
            .onKeyEvent { event ->
                if (!enabled || event.type != KeyEventType.KeyDown) return@onKeyEvent false
                val span = valueRange.endInclusive - valueRange.start
                val step = if (steps > 0) span / (steps.toFloat() + 1f) else span / 100
                val target = when (event.key) {
                    Key.DirectionRight -> bound + step * (if (rtl) -1 else 1)
                    Key.DirectionLeft -> bound - step * (if (rtl) -1 else 1)
                    Key.DirectionUp -> bound + step
                    Key.DirectionDown -> bound - step
                    Key.MoveHome -> bounds.start
                    Key.MoveEnd -> bounds.endInclusive
                    else -> return@onKeyEvent false
                }
                finishChange(target)
                true
            }
            .focusRequester(thumbFocus[thumb])
            .onFocusChanged {
                if (it.isFocused) { focusedThumb = thumb; activeThumb = thumb }
                else if (focusedThumb == thumb) focusedThumb = -1
            }
            .focusable(enabled)
    }

    val gestureModifier = if (enabled) {
        Modifier.pointerInput(valueRange, steps, rtl, thumbRadiusPx) {
            awaitEachGesture {
                val down = awaitFirstDown()
                val target = currentValueAt(down.position.x - thumbRadiusPx)
                activeThumb = if (abs(target - currentValue.start) <= abs(target - currentValue.endInclusive)) 0 else 1
                thumbFocus[activeThumb].requestFocus()
                var changed = currentCommit(activeThumb, target)
                down.consume()
                dragging = true
                try {
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        if (change.isConsumed) break
                        changed = currentCommit(activeThumb, currentValueAt(change.position.x - thumbRadiusPx)) || changed
                        change.consume()
                        if (!change.pressed) {
                            if (changed) currentOnFinished?.invoke()
                            break
                        }
                    }
                } finally {
                    dragging = false
                }
            }
        }
    } else Modifier

    Box(
        modifier = modifier
            .fillMaxWidth()
            // root height = thumb height: the layers center exactly on the track line
            .height(SliderThumbSize)
            .alpha(if (enabled) 1f else 0.5f) // data-[disabled]:opacity-50
            .hoverable(interactionSource = interactionSource, enabled = enabled)
            .then(gestureModifier)
            .onSizeChanged { rootSize = it },
    ) {
        // Track with the range
        Box(
            modifier = Modifier
                .fillMaxSize(),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(BaseTokens.token6) // h-1.5
                    .clip(CircleShape) // rounded-full
                    .background(colors.track),
            ) {
                // Range between the thumbs
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(upperFraction())
                        .background(colors.range),
                )
                // Start segment is painted back with the track color
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(lowerFraction())
                        .background(colors.track),
                )
            }
        }
        // Lower thumb
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.CenterStart,
        ) {
            with(density) {
                SliderThumb(
                    ringVisible = isHovered || (dragging && activeThumb == 0) || focusedThumb == 0,
                    colors = colors,
                    borderWidth = borderWidth,
                    modifier = Modifier.offset(x = (trackSpanPx * lowerFraction()).toDp()).then(thumbModifier(0)),
                )
            }
        }
        // Upper thumb
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.CenterStart,
        ) {
            with(density) {
                SliderThumb(
                    ringVisible = isHovered || (dragging && activeThumb == 1) || focusedThumb == 1,
                    colors = colors,
                    borderWidth = borderWidth,
                    modifier = Modifier.offset(x = (trackSpanPx * upperFraction()).toDp()).then(thumbModifier(1)),
                )
            }
        }
    }
}

@Composable
private fun SliderTrack(
    state: com.composeunstyled.SliderState,
    orientation: Orientation,
    colors: SliderColors,
) {
    val trackModifier = when (orientation) {
        Orientation.Horizontal -> Modifier
            .fillMaxWidth()
            .height(SliderTrackSize)
        Orientation.Vertical -> Modifier
            .fillMaxHeight()
            .width(SliderTrackSize)
    }

    Box(
        modifier = trackModifier
            .clip(CircleShape) // rounded-full
            .background(colors.track),
        contentAlignment = when (orientation) {
            Orientation.Horizontal -> Alignment.CenterStart
            Orientation.Vertical -> Alignment.BottomCenter
        },
    ) {
        // Range: the track part filled from the start up to the thumb.
        Box(
            modifier = when (orientation) {
                Orientation.Horizontal -> Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(state.fraction)
                Orientation.Vertical -> Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(state.fraction)
            }
                .background(colors.range),
        )
    }
}

@Composable
private fun SliderThumb(
    ringVisible: Boolean,
    colors: SliderColors,
    borderWidth: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    val ringColor by animateColorAsState(
        targetValue = if (ringVisible) colors.focusRing else Color.Transparent,
        animationSpec = tween(motionDurationMillis(150)),
        label = "sliderThumbRing",
    )

    // The slot is exactly size-4: the primitive positions it edge to edge, and
    // ring-4 is drawn outside the slot bounds via drawBehind.
    Box(
        modifier = modifier
            .size(SliderThumbSize)
            .drawBehind {
                if (ringColor != Color.Transparent) {
                    // ring-4 around size-4: diameter 16 + 2 * 4
                    drawCircle(
                        color = ringColor,
                        radius = size.minDimension / 2 + BaseTokens.token4.toPx(),
                    )
                }
            }
            .shadow(
                elevation = Effects.boxShadowShadowSm.radius, // shadow-sm
                shape = CircleShape,
                clip = false,
                ambientColor = Effects.boxShadowShadowSm.color,
                spotColor = Effects.boxShadowShadowSm.color,
            )
            .clip(CircleShape)
            .background(colors.thumbBackground)
            .border(borderWidth, colors.thumbBorder, CircleShape),
    )
}
