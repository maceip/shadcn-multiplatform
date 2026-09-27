package com.github.jershell.shadcn.components.promptkit

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.*
import com.composeunstyled.theme.Theme
import com.github.jershell.shadcn.components.avatar.*
import com.github.jershell.shadcn.components.button.*
import com.github.jershell.shadcn.components.tooltip.*
import com.github.jershell.shadcn.theme.*
import com.github.jershell.shadcn.motion.shadcnTween
import kotlinx.coroutines.delay

@Composable
fun Message(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val alpha by animateFloatAsState(if (appeared) 1f else 0f, shadcnTween(), label = "message appearance")
    Row(modifier.fillMaxWidth().alpha(alpha),
        horizontalArrangement = Arrangement.spacedBy(TwDimensions.paddingPxToken3), content = content)
}

@Composable
fun MessageAvatar(src: String?, alt: String, modifier: Modifier = Modifier, fallback: String = "", delayMs: Long = 0) {
    require(delayMs >= 0)
    var showFallback by remember(src, delayMs) { mutableStateOf(delayMs == 0L) }
    LaunchedEffect(src, delayMs) { delay(delayMs); showFallback = true }
    Avatar(model = src, contentDescription = alt, modifier = modifier, size = AvatarSize.Default,
        fallbackText = if (showFallback) fallback else null)
}

@Composable
fun MessageContent(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val colors = resolvePromptKitColors()
    Column(modifier
        .background(colors.muted, RoundedCornerShape(Theme[DimProps][DimTokens.radiusLg]))
        .padding(TwDimensions.paddingPxToken2), content = content)
}

@Composable
fun MessageContent(content: String, modifier: Modifier = Modifier, markdown: Boolean = false) {
    val colors = resolvePromptKitColors()
    MessageContent(modifier) {
        if (markdown) Markdown(content = content)
        else SelectionContainer { BasicText(content, style = TypographyStyles.textSmRegular.copy(color = colors.foreground)) }
    }
}

@Composable
fun MessageActions(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(TwDimensions.paddingPxToken2), content = content)
}

@Composable
fun MessageAction(tooltip: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    Tooltip(tooltip = { TooltipText(tooltip) }, enabled = enabled, anchor = {
        Button(onClick, modifier.semantics { contentDescription = tooltip }, enabled,
            variant = ButtonVariant.Ghost, size = ButtonSize.Sm, content = content)
    })
}
