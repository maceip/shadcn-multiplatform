@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package com.github.jershell.shadcn.interaction

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import com.github.jershell.shadcn.components.button.*
import com.github.jershell.shadcn.components.dialog.Dialog
import com.github.jershell.shadcn.components.drawer.Drawer
import com.github.jershell.shadcn.components.drawer.DrawerSide
import com.github.jershell.shadcn.components.modal.CanvasModalHost
import com.github.jershell.shadcn.components.modal.LocalShadcnModalLayerBlocked
import com.github.jershell.shadcn.containers.ShadcnUI
import kotlin.test.*

class CanvasModalIntegrationTest {
    @Test fun contentUpdatesRetainFocusAndRemovingOwnerReleasesNestedLayers() = runComposeUiTest {
        var mounted by mutableStateOf(true)
        var parent by mutableStateOf(false)
        var child by mutableStateOf(false)
        var token by mutableIntStateOf(0)
        setContent { ShadcnUI(motionEnabled = false) { CanvasModalHost {
            Button({ parent = true }, Modifier.testTag("launcher")) { ButtonText("Launch") }
            if (mounted) Dialog(parent, { parent = it }) {
                Button({ child = true }, Modifier.testTag("child-launcher")) { ButtonText("Nested") }
                Dialog(child, { child = it }) {
                    BasicText("Token $token")
                    BasicTextField("Editor", {}, Modifier.testTag("stream-editor"))
                }
            }
        } } }
        onNodeWithTag("launcher").requestFocus()
        onNodeWithTag("launcher").performClick()
        onNodeWithTag("child-launcher").performClick()
        onNodeWithTag("stream-editor").requestFocus()
        repeat(10) { next ->
            runOnIdle { token = next }
            onNodeWithText("Token $next").assertExists()
            onNodeWithTag("stream-editor").assertIsFocused()
            onNodeWithTag("launcher").assertDoesNotExist()
        }
        runOnIdle { mounted = false }
        onNodeWithTag("stream-editor").assertDoesNotExist()
        onNodeWithTag("child-launcher").assertDoesNotExist()
        onNodeWithTag("launcher").assertExists().assertIsFocused()
    }

    @Test fun nestedDialogsHideCoveredSemanticsAndRestoreEachLauncher() = runComposeUiTest {
        var parent by mutableStateOf(false)
        var child by mutableStateOf(false)
        var backgroundBlocked = false
        var parentBlocked = false
        var childBlocked = true
        setContent { ShadcnUI(motionEnabled = false) { CanvasModalHost {
            val blocked = LocalShadcnModalLayerBlocked.current
            SideEffect { backgroundBlocked = blocked }
            Button({ parent = true }, Modifier.testTag("launcher")) { ButtonText("Root launcher") }
            Dialog(parent, { parent = it }) {
                val covered = LocalShadcnModalLayerBlocked.current
                SideEffect { parentBlocked = covered }
                Button({ child = true }, Modifier.testTag("child-launcher")) { ButtonText("Child launcher") }
                Dialog(child, { child = it }) {
                    val nestedCovered = LocalShadcnModalLayerBlocked.current
                    SideEffect { childBlocked = nestedCovered }
                    BasicTextField("Child", {}, Modifier.testTag("child-input"))
                }
            }
        } } }
        onNodeWithTag("launcher").requestFocus()
        onNodeWithTag("launcher").performClick()
        onNodeWithTag("launcher").assertDoesNotExist()
        onNodeWithTag("child-launcher").requestFocus()
        onNodeWithTag("child-launcher").performClick()
        onNodeWithTag("child-launcher").assertDoesNotExist()
        onNodeWithTag("child-input").assertIsFocused()
        runOnIdle { assertTrue(backgroundBlocked); assertTrue(parentBlocked); assertFalse(childBlocked) }
        onNodeWithTag("child-input").performKeyInput { pressKey(Key.Escape) }
        onNodeWithTag("child-launcher").assertExists().assertIsFocused()
        onNodeWithTag("launcher").assertDoesNotExist()
        runOnIdle { assertTrue(parent); assertFalse(child); assertFalse(parentBlocked) }
        onNodeWithTag("child-launcher").performKeyInput { pressKey(Key.Escape) }
        onNodeWithTag("launcher").assertExists().assertIsFocused()
        runOnIdle { assertFalse(parent); assertFalse(backgroundBlocked) }
        // Reopening is the browser regression that originally lost the root semantics owner.
        onNodeWithTag("launcher").performClick()
        onNodeWithTag("child-launcher").assertExists()
    }

    @Test fun forwardAndBackwardTabNeverFocusCoveredBackground() = runComposeUiTest {
        var open by mutableStateOf(false)
        var backgroundFocused = false
        setContent { ShadcnUI(motionEnabled = false) { CanvasModalHost {
            Button({ open = true }, Modifier.testTag("launcher").onFocusChanged { backgroundFocused = it.isFocused }) {
                ButtonText("Launch")
            }
            Dialog(open, { open = it }, showCloseButton = false) {
                Column {
                    Button({}, Modifier.testTag("first")) { ButtonText("First") }
                    Button({}, Modifier.testTag("last")) { ButtonText("Last") }
                }
            }
        } } }
        onNodeWithTag("launcher").requestFocus()
        onNodeWithTag("launcher").performClick()
        onNodeWithTag("last").requestFocus()
        repeat(2) {
            onNodeWithTag("last").performKeyInput { pressKey(Key.Tab) }
            runOnIdle { assertFalse(backgroundFocused) }
        }
        // The panel itself is a focus target before its controls. Wrapping must traverse
        // through it to First, and in reverse through it to Last, rather than stick there.
        onNodeWithTag("first").assertIsFocused()
        repeat(2) {
            onNodeWithTag("first").performKeyInput { keyDown(Key.ShiftLeft); pressKey(Key.Tab); keyUp(Key.ShiftLeft) }
            runOnIdle { assertFalse(backgroundFocused) }
        }
        onNodeWithTag("last").assertIsFocused()
        onNodeWithTag("first").performKeyInput { pressKey(Key.Escape) }
        onNodeWithTag("launcher").assertIsFocused()
    }

    @Test fun sideAndBottomDrawersRestoreSemanticsAndFocusAfterDismissal() = runComposeUiTest {
        var open by mutableStateOf(false)
        var side by mutableStateOf(DrawerSide.Left)
        setContent { ShadcnUI(motionEnabled = false) { CanvasModalHost {
            Button({ open = true }, Modifier.testTag("launcher")) { ButtonText("Launch") }
            Drawer(open, { open = it }, side = side) {
                BasicTextField("Drawer", {}, Modifier.testTag("drawer-input"))
            }
        } } }
        for (next in listOf(DrawerSide.Left, DrawerSide.Bottom)) {
            runOnIdle { side = next }
            onNodeWithTag("launcher").requestFocus()
            onNodeWithTag("launcher").performClick()
            onNodeWithTag("launcher").assertDoesNotExist()
            onNodeWithTag("drawer-input").assertIsFocused().performKeyInput { pressKey(Key.Escape) }
            onNodeWithTag("drawer-input").assertDoesNotExist()
            onNodeWithTag("launcher").assertExists().assertIsFocused()
            runOnIdle { assertFalse(open) }
        }
    }
}
