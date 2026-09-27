package com.github.jershell.shadcn.components.promptkit

import com.github.jershell.shadcn.motion.shadcnAnimateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composeunstyled.focusRing
import com.composeunstyled.theme.Theme
import com.github.jershell.shadcn.components.button.*
import com.github.jershell.shadcn.components.tooltip.Tooltip
import com.github.jershell.shadcn.components.tooltip.TooltipText
import com.github.jershell.shadcn.theme.*

/** Draft/selection/IME state. Submission never clears the draft; the caller clears after accepting it. */
@Stable
class PromptInputState(initialValue: String = "") {
    var fieldValue by mutableStateOf(TextFieldValue(initialValue, TextRange(initialValue.length)))
        internal set
    var value: String
        get() = fieldValue.text
        set(value) { fieldValue = TextFieldValue(value, TextRange(value.length)) }
    val isComposing: Boolean get() = fieldValue.composition != null
    fun clear() { value = "" }

    fun submit(enabled: Boolean = true, isLoading: Boolean = false, onSubmit: (String) -> Unit): Boolean {
        if (!enabled || isLoading || isComposing || value.isBlank()) return false
        onSubmit(value)
        return true
    }

    companion object {
        val Saver = Saver<PromptInputState, String>(save = { it.value }, restore = { PromptInputState(it) })
    }
}

@Composable
fun rememberPromptInputState(initialValue: String = ""): PromptInputState =
    rememberSaveable(saver = PromptInputState.Saver) { PromptInputState(initialValue) }

private data class PromptInputContext(
    val state: PromptInputState, val enabled: Boolean, val isLoading: Boolean,
    val maxHeight: Dp, val focusRequester: FocusRequester,
    val registeredEditors: MutableIntState,
    val onValueChange: (String) -> Unit, val onSubmit: (String) -> Unit,
)
private val LocalPromptInput = staticCompositionLocalOf<PromptInputContext?> { null }
@Composable private fun promptInputContext(): PromptInputContext =
    checkNotNull(LocalPromptInput.current) { "Place this component inside PromptInput." }

/** Compound prompt composer. Enter submits; Shift+Enter inserts a line; IME composition never submits. */
@Composable
fun PromptInput(
    state: PromptInputState,
    onSubmit: (String) -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    disabled: Boolean = false,
    maxHeight: Dp = 240.dp,
    onValueChange: (String) -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    require(maxHeight > 0.dp) { "maxHeight must be positive" }
    val colors = resolvePromptKitColors()
    val focus = remember { FocusRequester() }
    val registeredEditors = remember { mutableIntStateOf(0) }
    val shape = RoundedCornerShape(Theme[DimProps][DimTokens.radiusXl])
    CompositionLocalProvider(LocalPromptInput provides PromptInputContext(
        state, !disabled, isLoading, maxHeight, focus, registeredEditors, onValueChange, onSubmit,
    )) {
        Column(
            modifier.fillMaxWidth().shadcnAnimateContentSize().alpha(if (disabled) .6f else 1f)
                .pointerInput(disabled) { detectTapGestures {
                    if (!disabled && registeredEditors.intValue > 0) focus.requestFocus()
                } }
                .background(colors.background, shape)
                .border(Theme[DimProps][DimTokens.borderWidth], colors.border, shape)
                .padding(TwDimensions.paddingPxToken2),
            content = content,
        )
    }
}

/** Controlled-value overload; the state overload additionally exposes selection and composition. */
@Composable
fun PromptInput(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: (String) -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    disabled: Boolean = false,
    maxHeight: Dp = 240.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val state = rememberPromptInputState(value)
    SideEffect { if (state.value != value) state.value = value }
    PromptInput(state, onSubmit, modifier, isLoading, disabled, maxHeight, onValueChange, content)
}

@Composable
fun PromptInputTextarea(
    modifier: Modifier = Modifier,
    placeholder: String = "Ask anything…",
    label: String = "Message",
    disableAutosize: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = 8,
) {
    require(minLines > 0 && maxLines >= minLines)
    val ctx = promptInputContext()
    val colors = resolvePromptKitColors()
    val interactions = remember { MutableInteractionSource() }
    DisposableEffect(ctx.focusRequester) {
        ctx.registeredEditors.intValue++
        onDispose { ctx.registeredEditors.intValue-- }
    }
    fun submit() = ctx.state.submit(ctx.enabled, ctx.isLoading, ctx.onSubmit)
    BasicTextField(
        value = ctx.state.fieldValue,
        onValueChange = { ctx.state.fieldValue = it; ctx.onValueChange(it.text) },
        enabled = ctx.enabled,
        modifier = modifier.fillMaxWidth().heightIn(max = ctx.maxHeight)
            .focusRequester(ctx.focusRequester)
            .focusRing(interactions, width = Effects.boxShadowFocusRing.spread, color = colors.primary,
                shape = RoundedCornerShape(Theme[DimProps][DimTokens.radiusMd]))
            .onPreviewKeyEvent { event ->
                if (event.key == Key.Enter && event.type == KeyEventType.KeyDown && !event.isShiftPressed &&
                    !ctx.state.isComposing) {
                    submit()
                    true
                } else false
            }
            .semantics { contentDescription = label }
            .padding(TwDimensions.paddingPxToken2),
        textStyle = TypographyStyles.textSmRegular.copy(color = colors.foreground),
        cursorBrush = SolidColor(colors.foreground),
        interactionSource = interactions,
        minLines = minLines,
        maxLines = if (disableAutosize) minLines else maxLines,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
        keyboardActions = KeyboardActions(onSend = { submit() }),
        decorationBox = { input ->
            Box {
                if (ctx.state.value.isEmpty()) BasicText(placeholder,
                    style = TypographyStyles.textSmRegular.copy(color = colors.mutedForeground))
                input()
            }
        },
    )
}

@Composable
fun PromptInputActions(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TwDimensions.paddingPxToken2),
        verticalArrangement = Arrangement.spacedBy(TwDimensions.paddingPxToken2)) { content() }
}

@Composable
fun PromptInputAction(
    tooltip: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val ctx = promptInputContext()
    Tooltip(tooltip = { TooltipText(tooltip) }, enabled = ctx.enabled && enabled, anchor = {
        Button(onClick, modifier.semantics { contentDescription = tooltip }, enabled = ctx.enabled && enabled,
            variant = ButtonVariant.Ghost, size = ButtonSize.Sm, content = content)
    })
}

/** Convenient send/stop control; stop stays available while the composer is loading. */
@Composable
fun PromptInputSubmit(modifier: Modifier = Modifier, onStop: (() -> Unit)? = null) {
    val ctx = promptInputContext()
    Button(
        onClick = { if (ctx.isLoading) onStop?.invoke() else ctx.state.submit(ctx.enabled, false, ctx.onSubmit) },
        modifier = modifier,
        enabled = ctx.enabled && if (ctx.isLoading) onStop != null else ctx.state.value.isNotBlank(),
        size = ButtonSize.Sm,
    ) { ButtonText(if (ctx.isLoading) "Stop" else "Send") }
}
