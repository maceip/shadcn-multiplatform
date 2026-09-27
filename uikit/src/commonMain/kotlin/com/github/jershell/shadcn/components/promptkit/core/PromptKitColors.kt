package com.github.jershell.shadcn.components.promptkit

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.composeunstyled.theme.Theme
import com.github.jershell.shadcn.theme.ColorProps
import com.github.jershell.shadcn.theme.ColorTokens

internal data class PromptKitColors(
    val background: Color, val foreground: Color, val muted: Color,
    val mutedForeground: Color, val border: Color, val primary: Color,
)

@Composable
internal fun resolvePromptKitColors() = PromptKitColors(
    Theme[ColorProps][ColorTokens.background], Theme[ColorProps][ColorTokens.foreground],
    Theme[ColorProps][ColorTokens.secondary], Theme[ColorProps][ColorTokens.mutedForeground],
    Theme[ColorProps][ColorTokens.border], Theme[ColorProps][ColorTokens.primary],
)
