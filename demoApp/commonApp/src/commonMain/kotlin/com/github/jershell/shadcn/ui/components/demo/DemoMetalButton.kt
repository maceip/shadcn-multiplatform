package com.github.jershell.shadcn.ui.components.demo

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.github.jershell.shadcn.components.button.*
import com.github.jershell.shadcn.components.metalbutton.*
import com.github.jershell.shadcn.components.typography.*
import com.github.jershell.shadcn.theme.BaseTokens

@Composable
fun DemoMetalButton() {
    var paused by remember { mutableStateOf(false) }
    var clicks by remember { mutableIntStateOf(0) }
    var preset by remember { mutableStateOf(MetalPreset.Chromatic) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(BaseTokens.token24)) {
        H4("Liquid metal")
        Muted("The source's plasma equations color the ring; its brightest point drives the wandering halo.")
        MetalButton("Upgrade to Pro", { clicks++ }, preset = preset, paused = paused)
        P("Activated $clicks times")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(BaseTokens.token8),
            verticalArrangement = Arrangement.spacedBy(BaseTokens.token8)) {
            MetalPreset.entries.forEach { choice ->
                Button({ preset = choice }, variant = if (choice == preset) ButtonVariant.Default else ButtonVariant.Outline,
                    size = ButtonSize.Sm) { ButtonText(choice.name) }
            }
            Button({ paused = !paused }, variant = ButtonVariant.Outline, size = ButtonSize.Sm) {
                ButtonText(if (paused) "Resume effect" else "Pause effect")
            }
        }
        MetalButton("Disabled", {}, enabled = false, preset = preset)
        MetalButton("Static reduced-motion example", { clicks++ }, preset = preset, animationsEnabled = false)
    }
}
