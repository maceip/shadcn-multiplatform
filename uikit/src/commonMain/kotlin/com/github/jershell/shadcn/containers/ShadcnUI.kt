package com.github.jershell.shadcn.containers

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import com.composeunstyled.PortalHost
import com.github.jershell.shadcn.components.dialog.DialogHost
import com.github.jershell.shadcn.components.toast.ToastHost
import com.github.jershell.shadcn.theme.Mode
import com.github.jershell.shadcn.theme.ShadcnPreset
import com.github.jershell.shadcn.theme.ShadcnTheme

/**
 * Application theme, non-modal portal layer, and managed dialog/toast hosts.
 * Do not wrap this in Compose Unstyled's ModalHost: that opts back into its
 * in-app portal modals, bypassing platform focus isolation and restoration.
 */
@Composable
fun ShadcnUI(
    mode: Mode = if (isSystemInDarkTheme()) Mode.Dark else Mode.Light,
    onModeChanged: @Composable (mode: Mode) -> Unit = {},
    preset: ShadcnPreset? = null,
    content: @Composable () -> Unit
) {
    ShadcnTheme(preset = preset, mode = mode, onModeChanged = onModeChanged) {
        // Keep non-modal portals available, while allowing Unstyled modals to use
        // Compose's platform dialog layer for focus isolation and restoration.
        PortalHost {
            content()
            DialogHost()
            ToastHost()
        }
    }
}
