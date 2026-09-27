package com.github.jershell.shadcn.motion

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import java.awt.Toolkit
import java.beans.PropertyChangeListener

@Composable
internal actual fun prefersReducedMotion(): Boolean {
    val toolkit = remember { Toolkit.getDefaultToolkit() }
    // AWT publishes the Windows animation preference. Other desktops can supply an explicit
    // ShadcnUI(motionEnabled = false) from their application preference/accessibility bridge.
    fun read() = toolkit.getDesktopProperty("win.menu.animate") == false
    var reduced by remember { mutableStateOf(read()) }
    DisposableEffect(toolkit) {
        val listener = PropertyChangeListener { reduced = read() }
        toolkit.addPropertyChangeListener("win.menu.animate", listener)
        onDispose { toolkit.removePropertyChangeListener("win.menu.animate", listener) }
    }
    return reduced
}
