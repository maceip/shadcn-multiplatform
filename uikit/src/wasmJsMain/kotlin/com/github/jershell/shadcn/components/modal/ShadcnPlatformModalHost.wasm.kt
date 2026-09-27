package com.github.jershell.shadcn.components.modal

import androidx.compose.runtime.Composable

@Composable
internal actual fun ShadcnPlatformModalHost(content: @Composable () -> Unit) = CanvasModalHost(content)
