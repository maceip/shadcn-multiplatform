package com.github.jershell.shadcn.components.promptkit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.composeunstyled.theme.Theme
import com.composables.icons.lucide.*
import com.github.jershell.shadcn.components.button.*
import com.github.jershell.shadcn.components.icon.ShadcnIconContent
import com.github.jershell.shadcn.components.icon.toShadcnIcon
import com.github.jershell.shadcn.components.popover.PopoverContent
import com.github.jershell.shadcn.theme.*
import io.ktor.http.Url
import kotlinx.coroutines.delay

enum class SystemMessageVariant { Action, Error, Warning }

data class SystemMessageAction(
    val label: String,
    val onClick: () -> Unit,
    val variant: ButtonVariant = ButtonVariant.Default,
    val enabled: Boolean = true,
)

/** Banner with a real named action; status is conveyed by icon/description as well as color. */
@Composable
fun SystemMessage(
    text: String,
    modifier: Modifier = Modifier,
    variant: SystemMessageVariant = SystemMessageVariant.Action,
    fill: Boolean = false,
    icon: (@Composable () -> Unit)? = null,
    isIconHidden: Boolean = false,
    cta: SystemMessageAction? = null,
    statusLabel: String = when (variant) {
        SystemMessageVariant.Action -> "Information"
        SystemMessageVariant.Error -> "Error"
        SystemMessageVariant.Warning -> "Warning"
    },
) {
    val accent = Theme[ColorProps][when (variant) {
        SystemMessageVariant.Action -> ColorTokens.foreground
        SystemMessageVariant.Error -> ColorTokens.destructive
        SystemMessageVariant.Warning -> ColorTokens.chartToken4
    }]
    val shape = RoundedCornerShape(Theme[DimProps][DimTokens.radiusLg])
    FlowRow(
        modifier.border(Theme[DimProps][DimTokens.borderWidth],
            if (variant == SystemMessageVariant.Action) Theme[ColorProps][ColorTokens.border] else accent, shape)
            .background(if (fill) accent.copy(alpha = 0.08f) else Theme[ColorProps][ColorTokens.background], shape)
            .padding(BaseTokens.token12).semantics {
                stateDescription = statusLabel
                liveRegion = LiveRegionMode.Polite
            },
        horizontalArrangement = Arrangement.spacedBy(BaseTokens.token12),
        verticalArrangement = Arrangement.spacedBy(BaseTokens.token8),
    ) {
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(BaseTokens.token12),
            verticalAlignment = Alignment.Top) {
            if (!isIconHidden) {
                if (icon != null) icon() else ShadcnIconContent((when (variant) {
                    SystemMessageVariant.Action -> Lucide.Info
                    SystemMessageVariant.Error -> Lucide.CircleAlert
                    SystemMessageVariant.Warning -> Lucide.TriangleAlert
                }).toShadcnIcon(), null, Modifier.size(BaseTokens.token16), tint = accent)
            }
            StatusText(text, Modifier.weight(1f))
        }
        cta?.let { action ->
            Button(action.onClick, enabled = action.enabled, variant = action.variant, size = ButtonSize.Sm) {
                ButtonText(action.label)
            }
        }
    }
}

@Composable
fun ThinkingBar(
    modifier: Modifier = Modifier,
    text: String = "Thinking",
    onStop: (() -> Unit)? = null,
    stopLabel: String = "Answer now",
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    animationsEnabled: Boolean = LocalPromptKitMotionEnabled.current,
) {
    FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(BaseTokens.token8)) {
        if (onClick != null) {
            Button(onClick, enabled = enabled, variant = ButtonVariant.Ghost, size = ButtonSize.Sm) {
                TextShimmer(text, animationsEnabled = animationsEnabled)
                ButtonIcon(Lucide.ChevronRight, null)
            }
        } else TextShimmer(text, Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            animationsEnabled = animationsEnabled)
        onStop?.let { stop ->
            Button(stop, enabled = enabled, variant = ButtonVariant.Link, size = ButtonSize.Sm) { ButtonText(stopLabel) }
        }
    }
}

enum class Feedback { None, Helpful, NotHelpful }

/** [selection] provides controlled selection; otherwise [defaultSelection] is remembered. */
@Composable
fun FeedbackBar(
    modifier: Modifier = Modifier,
    title: String = "Was this response helpful?",
    icon: (@Composable () -> Unit)? = null,
    selection: Feedback? = null,
    defaultSelection: Feedback = Feedback.None,
    onSelectionChange: (Feedback) -> Unit = {},
    onHelpful: () -> Unit = {},
    onNotHelpful: () -> Unit = {},
    onClose: (() -> Unit)? = null,
    enabled: Boolean = true,
    helpfulLabel: String = "Helpful",
    notHelpfulLabel: String = "Not helpful",
    closeLabel: String = "Close feedback",
) {
    var localSelection by rememberSaveable { mutableStateOf(defaultSelection) }
    val current = selection ?: localSelection
    val shape = RoundedCornerShape(Theme[DimProps][DimTokens.radiusLg])
    FlowRow(modifier.border(Theme[DimProps][DimTokens.borderWidth], Theme[ColorProps][ColorTokens.border], shape)
        .background(Theme[ColorProps][ColorTokens.background], shape).padding(BaseTokens.token12),
        horizontalArrangement = Arrangement.spacedBy(BaseTokens.token12), verticalArrangement = Arrangement.spacedBy(BaseTokens.token8)) {
        Row(Modifier.weight(1f).align(Alignment.CenterVertically),
            horizontalArrangement = Arrangement.spacedBy(BaseTokens.token8), verticalAlignment = Alignment.CenterVertically) {
            icon?.invoke()
            StatusText(title, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(BaseTokens.token4)) {
            Button({
                if (selection == null) localSelection = Feedback.Helpful
                onSelectionChange(Feedback.Helpful)
                onHelpful()
            }, Modifier.semantics { contentDescription = helpfulLabel; selected = current == Feedback.Helpful },
                enabled, ButtonVariant.Ghost, ButtonSize.IconSm) { ButtonIcon(Lucide.ThumbsUp, null) }
            Button({
                if (selection == null) localSelection = Feedback.NotHelpful
                onSelectionChange(Feedback.NotHelpful)
                onNotHelpful()
            }, Modifier.semantics { contentDescription = notHelpfulLabel; selected = current == Feedback.NotHelpful },
                enabled, ButtonVariant.Ghost, ButtonSize.IconSm) { ButtonIcon(Lucide.ThumbsDown, null) }
            onClose?.let { close ->
                Button(close, Modifier.semantics { contentDescription = closeLabel }, enabled,
                    ButtonVariant.Ghost, ButtonSize.IconSm) { ButtonIcon(Lucide.X, null) }
            }
        }
    }
}

private class SourceContext(
    val href: String,
    val domain: String,
    val enabled: Boolean,
    val expanded: Boolean,
    val toggle: () -> Unit,
    val requestOpen: (Boolean) -> Unit,
    val openLink: () -> Unit,
)
private val LocalSourceContext = staticCompositionLocalOf<SourceContext?> { null }
@Composable private fun sourceContext() = checkNotNull(LocalSourceContext.current) { "SourceTrigger/SourceContent require Source." }

internal fun sourceDomain(href: String): String = try {
    Url(href).host.removePrefix("www.").ifBlank { href }
} catch (_: Exception) { href.substringAfterLast('/').ifBlank { href } }

internal fun isSafeSourceLink(href: String): Boolean = try {
    val parsed = Url(href)
    (href.startsWith("https://", ignoreCase = true) || href.startsWith("http://", ignoreCase = true)) && parsed.host.isNotBlank()
} catch (_: Exception) { false }

/**
 * Citation with a focus/hover preview that can also be pinned open by touch or Enter.
 * The preview contains a separately named link action. Optional favicon content is caller supplied;
 * this component does not disclose source URLs to a third-party favicon service.
 * A non-modal Compose popup leaves its anchor hoverable. Pinning makes the popup keyboard
 * focusable, so its link can be reached without a pointer; Escape or outside click dismisses it.
 */
@Composable
fun Source(
    href: String,
    modifier: Modifier = Modifier,
    open: Boolean? = null,
    onOpenChange: (Boolean) -> Unit = {},
    defaultOpen: Boolean = false,
    enabled: Boolean = true,
    onOpenLink: ((String) -> Unit)? = null,
    openDelayMillis: Long = 150,
    closeDelayMillis: Long = 100,
    anchor: @Composable () -> Unit = { SourceTrigger() },
    content: @Composable () -> Unit,
) {
    require(openDelayMillis >= 0 && closeDelayMillis >= 0)
    var internalOpen by rememberSaveable(href) { mutableStateOf(defaultOpen) }
    var pinned by remember(href) { mutableStateOf(defaultOpen) }
    var dismissedWhileActive by remember(href) { mutableStateOf(false) }
    var anchorFocused by remember { mutableStateOf(false) }
    var contentFocused by remember { mutableStateOf(false) }
    val anchorInteraction = remember { MutableInteractionSource() }
    val contentInteraction = remember { MutableInteractionSource() }
    val anchorHovered by anchorInteraction.collectIsHoveredAsState()
    val contentHovered by contentInteraction.collectIsHoveredAsState()
    val callback by rememberUpdatedState(onOpenChange)
    val expanded = enabled && (open ?: internalOpen)
    val change: (Boolean) -> Unit = { next ->
        if (next != (open ?: internalOpen)) {
            if (open == null) internalOpen = next
            callback(next)
        }
    }
    val currentChange by rememberUpdatedState(change)
    val active = anchorHovered || contentHovered || anchorFocused || contentFocused
    LaunchedEffect(active, enabled, pinned, dismissedWhileActive, openDelayMillis, closeDelayMillis) {
        if (!enabled) { pinned = false; dismissedWhileActive = false; currentChange(false) }
        else if (!active) {
            // A dismissed preview stays dismissed until pointer and focus leave its region.
            dismissedWhileActive = false
            if (!pinned) { delay(closeDelayMillis); currentChange(false) }
        } else if (!pinned && !dismissedWhileActive) {
            delay(openDelayMillis)
            currentChange(true)
        }
    }
    val uriHandler = LocalUriHandler.current
    val requestOpen: (Boolean) -> Unit = { next ->
        pinned = next
        dismissedWhileActive = !next && active
        change(next)
    }
    val context = SourceContext(href, sourceDomain(href), enabled, expanded, {
        requestOpen(!pinned || !expanded)
    }, requestOpen, {
        if (enabled && isSafeSourceLink(href)) {
            if (onOpenLink != null) onOpenLink(href) else uriHandler.openUri(href)
        }
    })
    val gap = with(LocalDensity.current) { BaseTokens.token4.roundToPx() }
    val position = remember(gap) { SourcePositionProvider(gap) }
    CompositionLocalProvider(LocalSourceContext provides context) {
        Box(modifier) {
            Box(Modifier.hoverable(anchorInteraction).onFocusChanged { anchorFocused = it.hasFocus }
                .onPreviewKeyEvent {
                    if (expanded && it.key == Key.Escape && it.type == KeyEventType.KeyDown) {
                        requestOpen(false); true
                    } else false
                }.focusGroup()) { anchor() }
            if (expanded) Popup(position,
                onDismissRequest = { requestOpen(false) },
                properties = PopupProperties(focusable = pinned || open == true),
            ) {
                Box(Modifier.hoverable(contentInteraction).onFocusChanged { contentFocused = it.hasFocus }.focusGroup()) { content() }
            }
        }
    }
}

internal class SourcePositionProvider(private val gap: Int) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize,
        layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val proposedX = if (layoutDirection == LayoutDirection.Ltr) anchorBounds.left
            else anchorBounds.right - popupContentSize.width
        val below = anchorBounds.bottom + gap
        val above = anchorBounds.top - gap - popupContentSize.height
        val proposedY = if (below + popupContentSize.height <= windowSize.height || above < 0) below else above
        return IntOffset(proposedX.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0)),
            proposedY.coerceIn(0, (windowSize.height - popupContentSize.height).coerceAtLeast(0)))
    }
}

@Composable
fun SourceTrigger(label: String? = null, modifier: Modifier = Modifier, favicon: (@Composable () -> Unit)? = null) {
    val source = sourceContext()
    Button(source.toggle, modifier.semantics {
        stateDescription = if (source.expanded) "Expanded" else "Collapsed"
        if (!source.expanded) expand { if (source.enabled) { source.requestOpen(true); true } else false }
        else collapse { if (source.enabled) { source.requestOpen(false); true } else false }
    }, source.enabled, ButtonVariant.Secondary, ButtonSize.Xs,
        shape = RoundedCornerShape(Theme[DimProps][DimTokens.radiusFull])) {
        favicon?.invoke()
        ButtonText(label ?: source.domain)
    }
}

@Composable
fun SourceContent(title: String, description: String, modifier: Modifier = Modifier,
    favicon: (@Composable () -> Unit)? = null, openLabel: String = "Open source") {
    val source = sourceContext()
    PopoverContent(modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(BaseTokens.token8), verticalAlignment = Alignment.CenterVertically) {
            favicon?.invoke()
            StatusText(source.domain, muted = true)
        }
        StatusText(title)
        StatusText(description, muted = true)
        Button(source.openLink, enabled = source.enabled && isSafeSourceLink(source.href), variant = ButtonVariant.Link,
            size = ButtonSize.Sm) {
            ButtonText(openLabel)
            ButtonIcon(Lucide.ExternalLink, null)
        }
    }
}
