package com.github.jershell.shadcn.components.promptkit

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal actual fun MarkdownTaskCheckbox(checked: Boolean, label: String, modifier: Modifier) =
    NativeMarkdownTaskCheckbox(checked, label, modifier)
