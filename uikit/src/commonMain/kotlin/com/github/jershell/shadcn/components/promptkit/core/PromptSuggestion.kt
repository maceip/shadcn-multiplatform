package com.github.jershell.shadcn.components.promptkit

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import com.github.jershell.shadcn.components.button.*
import com.github.jershell.shadcn.motion.shadcnTween
import com.github.jershell.shadcn.theme.BaseTokens
import com.github.jershell.shadcn.theme.ColorProps
import com.github.jershell.shadcn.theme.ColorTokens
import com.github.jershell.shadcn.theme.Effects
import com.github.jershell.shadcn.theme.TypographyStyles
import com.github.jershell.shadcn.theme.DimProps
import com.github.jershell.shadcn.theme.DimTokens
import com.composeunstyled.theme.Theme
import com.composeunstyled.UnstyledButton
import com.composeunstyled.focusRing

internal fun promptHighlightRange(content: String, highlight: String?): IntRange? {
    val needle = highlight?.trim().orEmpty()
    if (needle.isEmpty()) return null
    val start = content.indexOf(needle, ignoreCase = true)
    return if (start < 0) null else start until start + needle.length
}

@Composable
fun PromptSuggestion(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    highlight: String? = null,
    enabled: Boolean = true,
    variant: ButtonVariant = if (highlight.isNullOrBlank()) ButtonVariant.Outline else ButtonVariant.Ghost,
    size: ButtonSize = if (highlight.isNullOrBlank()) ButtonSize.Lg else ButtonSize.Sm,
) {
    val colors = resolvePromptKitColors()
    val highlightMode = !highlight.isNullOrBlank()
    val range = remember(text, highlight) { promptHighlightRange(text, highlight) }
    val interactions = remember { MutableInteractionSource() }
    val hovered by interactions.collectIsHoveredAsState()
    val pressed by interactions.collectIsPressedAsState()
    val focused by interactions.collectIsFocusedAsState()
    val buttonColors = resolveButtonColors(variant, hovered && enabled, pressed && enabled)
    val background by animateColorAsState(buttonColors.container, shadcnTween(150), label = "suggestion background")
    val foreground by animateColorAsState(buttonColors.content, shadcnTween(150), label = "suggestion foreground")
    val spec = size.spec()
    val shape = if (highlightMode) RoundedCornerShape(Theme[DimProps][DimTokens.radiusXl]) else CircleShape
    val border = if (focused && buttonColors.border != null) Theme[ColorProps][ColorTokens.ring] else buttonColors.border
    // A suggestion can contain a paragraph. Reuse button colors, focus and input handling,
    // but let its minimum height grow with wrapped text instead of fixing the button height.
    UnstyledButton(onClick = onClick, enabled = enabled, interactionSource = interactions,
        indication = null, role = Role.Button,
        contentAlignment = if (highlightMode) Alignment.CenterStart else Alignment.Center,
        modifier = modifier.then(if (highlightMode) Modifier.fillMaxWidth() else Modifier)
            .alpha(if (enabled) 1f else 0.5f)
            .defaultMinSize(minWidth = spec.height, minHeight = spec.height)
            .then(if (buttonColors.showShadow) Modifier.shadow(Effects.boxShadowShadowXs.radius, shape, clip = false) else Modifier)
            .clip(shape)
            .then(if (border != null) Modifier.border(Theme[DimProps][DimTokens.borderWidth], border, shape) else Modifier)
            .background(background, shape)
            .focusRing(interactionSource = interactions, width = Effects.boxShadowFocusRing.spread,
                color = resolveButtonFocusRingColor(variant), shape = shape)) {
        BasicText(buildAnnotatedString {
            append(text)
            if (range != null) addStyle(SpanStyle(color = colors.primary, fontWeight = FontWeight.Medium),
                range.first, range.last + 1)
        }, Modifier.padding(horizontal = spec.horizontalPadding, vertical = BaseTokens.token8),
            style = TypographyStyles.textSmRegular.copy(color = if (highlightMode) colors.mutedForeground else foreground,
                textDecoration = if (variant == ButtonVariant.Link && hovered && enabled) TextDecoration.Underline else TextDecoration.None))
    }
}
