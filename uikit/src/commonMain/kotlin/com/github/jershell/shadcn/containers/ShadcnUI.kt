package com.github.jershell.shadcn.containers

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import com.github.jershell.shadcn.components.dialog.DialogHost
import com.github.jershell.shadcn.components.toast.ToastHost
import com.github.jershell.shadcn.theme.Mode
import com.github.jershell.shadcn.theme.ShadcnPreset
import com.github.jershell.shadcn.theme.ShadcnTheme
import com.github.jershell.shadcn.motion.ShadcnMotion
import com.github.jershell.shadcn.components.modal.ShadcnPlatformModalHost

/**
 * Application theme, non-modal portal layer, and managed dialog/toast hosts.
 * Native targets use platform modals. Web uses a focus-isolated in-scene modal stack to avoid
 * the Compose 1.11.1 semantics-owner disposal defect; see docs/WEB_MODAL_CONTAINMENT.md.
 */
@Composable
fun ShadcnUI(
    mode: Mode = if (isSystemInDarkTheme()) Mode.Dark else Mode.Light,
    onModeChanged: @Composable (mode: Mode) -> Unit = {},
    preset: ShadcnPreset? = null,
    motionEnabled: Boolean? = null,
    content: @Composable () -> Unit
) {
    ShadcnTheme(preset = preset, mode = mode, onModeChanged = onModeChanged) {
        // Native platform dialogs and the Web containment share the same public component API.
        ShadcnMotion(enabled = motionEnabled) {
            ShadcnPlatformModalHost {
                content()
                DialogHost()
                ToastHost()
            }
        }
    }
}
