package com.github.jershell.shadcn.components.modal

import androidx.compose.runtime.Composable
import com.composeunstyled.PortalHost

@Composable
internal actual fun ShadcnPlatformModalHost(content: @Composable () -> Unit) = PortalHost(content = content)
