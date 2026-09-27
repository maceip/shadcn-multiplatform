package com.github.jershell.shadcn.components.promptkit

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontFamily
import com.composeunstyled.theme.Theme
import com.composables.icons.lucide.*
import com.github.jershell.shadcn.components.badge.Badge
import com.github.jershell.shadcn.components.button.*
import com.github.jershell.shadcn.components.icon.ShadcnIconContent
import com.github.jershell.shadcn.components.icon.toShadcnIcon
import com.github.jershell.shadcn.theme.*
import com.github.jershell.shadcn.motion.ShadcnMotionTokens
import com.github.jershell.shadcn.motion.shadcnAnimateContentSize
import com.github.jershell.shadcn.motion.shadcnTween
import kotlinx.serialization.json.*

private class PromptDisclosure(
    val open: Boolean,
    val setOpen: (Boolean) -> Unit,
    val animationsEnabled: Boolean,
)
private val LocalPromptDisclosure = staticCompositionLocalOf<PromptDisclosure?> { null }

@Composable
private fun disclosure() = checkNotNull(LocalPromptDisclosure.current) {
    "Place the trigger/content inside Reasoning, Steps or ChainOfThoughtStep."
}

@Composable
private fun DisclosureRoot(
    open: Boolean?,
    defaultOpen: Boolean,
    onOpenChange: (Boolean) -> Unit,
    modifier: Modifier,
    animationsEnabled: Boolean,
    content: @Composable ColumnScope.() -> Unit,
) {
    var internalOpen by rememberSaveable { mutableStateOf(defaultOpen) }
    val currentCallback by rememberUpdatedState(onOpenChange)
    val expanded = open ?: internalOpen
    val state = PromptDisclosure(expanded, { next ->
        if (open == null) internalOpen = next
        currentCallback(next)
    }, animationsEnabled)
    CompositionLocalProvider(LocalPromptDisclosure provides state) {
        Column(modifier, content = content)
    }
}

/** Controlled [open] takes precedence. Uncontrolled streaming opens once and closes on completion. */
@Composable
fun Reasoning(
    modifier: Modifier = Modifier,
    open: Boolean? = null,
    onOpenChange: (Boolean) -> Unit = {},
    defaultOpen: Boolean = false,
    isStreaming: Boolean = false,
    animationsEnabled: Boolean = LocalPromptKitMotionEnabled.current,
    content: @Composable ColumnScope.() -> Unit,
) {
    var internalOpen by rememberSaveable { mutableStateOf(defaultOpen) }
    var autoOpened by remember { mutableStateOf(false) }
    LaunchedEffect(isStreaming, open != null) {
        if (open == null) {
            if (isStreaming && !autoOpened) { internalOpen = true; autoOpened = true }
            else if (!isStreaming && autoOpened) { internalOpen = false; autoOpened = false }
        }
    }
    DisclosureRoot(open ?: internalOpen, defaultOpen, { next ->
        if (open == null) internalOpen = next
        onOpenChange(next)
    }, modifier, animationsEnabled, content)
}

@Composable
private fun DisclosureTrigger(
    text: String,
    modifier: Modifier,
    enabled: Boolean,
    leftIcon: (@Composable () -> Unit)?,
    swapIconOnHover: Boolean,
    content: (@Composable RowScope.() -> Unit)? = null,
) {
    val state = disclosure()
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val angle by animateFloatAsState(if (state.open) 180f else 0f,
        if (state.animationsEnabled) shadcnTween(ShadcnMotionTokens.Quick) else tween(0), label = "status chevron")
    val color = Theme[ColorProps][ColorTokens.mutedForeground]
    val chevron: @Composable () -> Unit = {
        ShadcnIconContent(Lucide.ChevronDown.toShadcnIcon(), null,
            Modifier.size(BaseTokens.token16).graphicsLayer { rotationZ = angle }, tint = color)
    }
    Button(
        onClick = { state.setOpen(!state.open) }, enabled = enabled,
        variant = ButtonVariant.Ghost, size = ButtonSize.Sm,
        modifier = modifier.hoverable(interaction).semantics {
            stateDescription = if (state.open) "Expanded" else "Collapsed"
            if (state.open) collapse { if (enabled) { state.setOpen(false); true } else false }
            else expand { if (enabled) { state.setOpen(true); true } else false }
        },
    ) {
        if (leftIcon != null) {
            if (hovered && swapIconOnHover) chevron() else leftIcon()
        }
        if (content != null) content() else ButtonText(text)
        if (leftIcon == null) chevron()
    }
}

@Composable
fun ReasoningTrigger(text: String, modifier: Modifier = Modifier, enabled: Boolean = true) =
    DisclosureTrigger(text, modifier, enabled, null, false)

@Composable
fun ReasoningContent(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) =
    DisclosureContent(modifier, content)

@Composable
fun ReasoningContent(text: String, modifier: Modifier = Modifier, markdown: Boolean = false) {
    DisclosureContent(modifier) {
        if (markdown) Markdown(content = text) else StatusText(text, muted = true)
    }
}

@Composable
private fun DisclosureContent(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    val state = disclosure()
    // Remove the subtree immediately when closed. Animate container size, so an exiting,
    // visually clipped action never remains reachable by keyboard or accessibility.
    Box(if (state.animationsEnabled) modifier.shadcnAnimateContentSize() else modifier) {
        if (state.open) Column(Modifier.padding(top = BaseTokens.token8),
            verticalArrangement = Arrangement.spacedBy(BaseTokens.token8), content = content)
    }
}

@Composable
fun Steps(
    modifier: Modifier = Modifier,
    open: Boolean? = null,
    onOpenChange: (Boolean) -> Unit = {},
    defaultOpen: Boolean = true,
    animationsEnabled: Boolean = LocalPromptKitMotionEnabled.current,
    content: @Composable ColumnScope.() -> Unit,
) = DisclosureRoot(open, defaultOpen, onOpenChange, modifier, animationsEnabled, content)

@Composable
fun StepsTrigger(text: String, modifier: Modifier = Modifier, enabled: Boolean = true,
    leftIcon: (@Composable () -> Unit)? = null, swapIconOnHover: Boolean = true) =
    DisclosureTrigger(text, modifier, enabled, leftIcon, swapIconOnHover)

@Composable
fun StepsContent(modifier: Modifier = Modifier, bar: @Composable () -> Unit = { StepsBar() },
    content: @Composable ColumnScope.() -> Unit) {
    DisclosureContent(modifier) {
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(BaseTokens.token12)) {
            bar()
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(BaseTokens.token8), content = content)
        }
    }
}

@Composable
fun StepsBar(modifier: Modifier = Modifier) = Box(modifier.width(BaseTokens.token2).fillMaxHeight()
    .background(Theme[ColorProps][ColorTokens.muted]).clearAndSetSemantics {})

@Composable
fun StepsItem(text: String, modifier: Modifier = Modifier) = StatusText(text, modifier, muted = true)

@Composable
fun StepsItem(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) =
    Column(modifier, content = content)

interface ChainOfThoughtScope {
    /** Each key owns its expansion state across insertions/reordering. */
    fun Step(key: Any, open: Boolean? = null,
        onOpenChange: (Boolean) -> Unit = {}, defaultOpen: Boolean = false,
        content: @Composable ColumnScope.() -> Unit)
}

private val LocalChainOfThoughtLast = staticCompositionLocalOf { false }

private class ChainEntry(val key: Any, val open: Boolean?, val onOpenChange: (Boolean) -> Unit,
    val defaultOpen: Boolean, val content: @Composable ColumnScope.() -> Unit)

private class ChainScope : ChainOfThoughtScope {
    val entries = mutableListOf<ChainEntry>()
    override fun Step(key: Any, open: Boolean?, onOpenChange: (Boolean) -> Unit,
        defaultOpen: Boolean, content: @Composable ColumnScope.() -> Unit) {
        require(entries.none { it.key == key }) { "ChainOfThought step keys must be unique" }
        entries += ChainEntry(key, open, onOpenChange, defaultOpen, content)
    }
}

@Composable
fun ChainOfThought(modifier: Modifier = Modifier, content: ChainOfThoughtScope.() -> Unit) {
    val scope = ChainScope().apply(content)
    Column(modifier) {
        scope.entries.forEachIndexed { index, entry ->
            key(entry.key) { ChainOfThoughtStep(isLast = index == scope.entries.lastIndex,
                open = entry.open, onOpenChange = entry.onOpenChange,
                defaultOpen = entry.defaultOpen, content = entry.content) }
        }
    }
}

@Composable
fun ChainOfThoughtStep(
    modifier: Modifier = Modifier,
    isLast: Boolean = false,
    open: Boolean? = null,
    onOpenChange: (Boolean) -> Unit = {},
    defaultOpen: Boolean = false,
    animationsEnabled: Boolean = LocalPromptKitMotionEnabled.current,
    content: @Composable ColumnScope.() -> Unit,
) {
    CompositionLocalProvider(LocalChainOfThoughtLast provides isLast) {
        DisclosureRoot(open, defaultOpen, onOpenChange, modifier, animationsEnabled) {
            content()
            if (!isLast) Box(Modifier.padding(start = BaseTokens.token8).width(BaseTokens.token1)
                .height(BaseTokens.token16).background(Theme[ColorProps][ColorTokens.primary].copy(alpha = 0.2f)))
        }
    }
}

@Composable
fun ChainOfThoughtTrigger(text: String, modifier: Modifier = Modifier, enabled: Boolean = true,
    leftIcon: (@Composable () -> Unit)? = null, swapIconOnHover: Boolean = true) =
    DisclosureTrigger(text, modifier, enabled, leftIcon, swapIconOnHover)

@Composable
fun ChainOfThoughtContent(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val last = LocalChainOfThoughtLast.current
    StepsContent(modifier, bar = {
        Box(Modifier.padding(start = BaseTokens.token8).width(BaseTokens.token1).fillMaxHeight()
            .background(Theme[ColorProps][ColorTokens.primary].copy(alpha = if (last) 0f else 0.2f))
            .clearAndSetSemantics {})
    }, content = content)
}

@Composable
fun ChainOfThoughtItem(text: String, modifier: Modifier = Modifier) = StatusText(text, modifier, muted = true)

@Composable
fun ChainOfThoughtItem(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) =
    Column(modifier, content = content)

enum class ToolState { InputStreaming, InputAvailable, OutputAvailable, OutputError }

@Immutable
data class ToolPart(
    val type: String,
    val state: ToolState,
    val input: JsonElement? = null,
    val output: JsonElement? = null,
    val toolCallId: String? = null,
    val errorText: String? = null,
)

@Immutable
data class ToolLabels(
    val processing: String = "Processing", val ready: String = "Ready",
    val completed: String = "Completed", val error: String = "Error",
    val input: String = "Input", val output: String = "Output", val callId: String = "Call ID",
)

private val statusJson = Json { prettyPrint = true }
internal fun formatToolValue(value: JsonElement): String =
    if (value is JsonPrimitive && value.isString) value.content else statusJson.encodeToString(JsonElement.serializer(), value)

/** Pure presentation of a tool result. This component never executes tools or sends data. */
@Composable
fun Tool(
    toolPart: ToolPart,
    modifier: Modifier = Modifier,
    open: Boolean? = null,
    onOpenChange: (Boolean) -> Unit = {},
    defaultOpen: Boolean = false,
    labels: ToolLabels = ToolLabels(),
    animationsEnabled: Boolean = LocalPromptKitMotionEnabled.current,
) {
    val shape = RoundedCornerShape(Theme[DimProps][DimTokens.radiusLg])
    val status = when (toolPart.state) {
        ToolState.InputStreaming -> labels.processing
        ToolState.InputAvailable -> labels.ready
        ToolState.OutputAvailable -> labels.completed
        ToolState.OutputError -> labels.error
    }
    val accent = when (toolPart.state) {
        ToolState.InputStreaming -> Theme[ColorProps][ColorTokens.chartToken1]
        ToolState.InputAvailable -> Theme[ColorProps][ColorTokens.chartToken4]
        ToolState.OutputAvailable -> Theme[ColorProps][ColorTokens.chartToken2]
        ToolState.OutputError -> Theme[ColorProps][ColorTokens.destructive]
    }
    DisclosureRoot(open, defaultOpen, onOpenChange,
        modifier.fillMaxWidth().border(Theme[DimProps][DimTokens.borderWidth], Theme[ColorProps][ColorTokens.border], shape)
            .background(Theme[ColorProps][ColorTokens.background], shape).padding(BaseTokens.token12), animationsEnabled) {
        DisclosureTrigger(toolPart.type, Modifier.fillMaxWidth(), true, null, false) {
            when (toolPart.state) {
                ToolState.InputStreaming -> CircularLoader(size = LoaderSize.Sm, animationsEnabled = animationsEnabled)
                else -> ShadcnIconContent((when (toolPart.state) {
                    ToolState.OutputAvailable -> Lucide.CircleCheck
                    ToolState.OutputError -> Lucide.CircleX
                    else -> Lucide.Settings
                }).toShadcnIcon(), null, Modifier.size(BaseTokens.token16), tint = accent)
            }
            ButtonText(toolPart.type)
            Badge(status, color = accent.copy(alpha = 0.12f), contentColor = accent,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
        DisclosureContent(Modifier.fillMaxWidth()) {
            toolPart.input?.let { input ->
                StatusText(labels.input, muted = true)
                CodeBlockCode(code = formatToolValue(input), language = "json")
            }
            toolPart.output?.let { output ->
                StatusText(labels.output, muted = true)
                Box(Modifier.heightIn(max = BaseTokens.token240).verticalScroll(rememberScrollState())) {
                    CodeBlockCode(code = formatToolValue(output), language = "json")
                }
            }
            if (toolPart.state == ToolState.OutputError && toolPart.errorText != null) {
                SelectionContainer {
                    BasicText(toolPart.errorText, Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        TypographyStyles.textSmRegular.copy(color = accent))
                }
            }
            if (toolPart.state == ToolState.InputStreaming) StatusText(labels.processing, muted = true)
            toolPart.toolCallId?.let { StatusText("${labels.callId}: $it", muted = true) }
        }
    }
}

@Composable
internal fun StatusText(text: String, modifier: Modifier = Modifier, muted: Boolean = false) =
    BasicText(text, modifier, TypographyStyles.textSmRegular.copy(color =
        Theme[ColorProps][if (muted) ColorTokens.mutedForeground else ColorTokens.foreground]))
