@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package com.github.jershell.shadcn.interaction

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.composeunstyled.DialogProperties
import com.github.jershell.shadcn.components.dialog.*
import com.github.jershell.shadcn.components.drawer.*
import com.github.jershell.shadcn.containers.ShadcnUI
import kotlin.test.*

class ModalIntegrationTest {
    @Test fun dismissalFlagsAreHonored() = runComposeUiTest {
        var dismissals = 0
        setContent { ShadcnUI {
            Dialog(true, { dismissals++ }, properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false), showCloseButton = false) {
                BasicTextField("dialog", {}, Modifier.testTag("input"))
            }
        } }
        onNodeWithTag("input").performKeyInput { pressKey(Key.Escape) }
        onNode(isRoot() and hasAnyDescendant(hasTestTag("input"))).performTouchInput { click(Offset(1f, 1f)) }
        runOnIdle { assertEquals(0, dismissals) }
    }

    @Test fun nestedDialogEscapeDismissesOnlyTopmostAndRestoresFocus() = runComposeUiTest {
        var child by mutableStateOf(false)
        var parent by mutableStateOf(true)
        setContent { ShadcnUI {
            Dialog(parent, { parent = it }, showCloseButton = false) {
                BasicTextField("parent", {}, Modifier.testTag("parent-input"))
                Dialog(child, { child = it }, showCloseButton = false) {
                    BasicTextField("child", {}, Modifier.testTag("child-input"))
                }
            }
        } }
        onNodeWithTag("parent-input").requestFocus()
        runOnIdle { child = true }
        onNodeWithTag("child-input").assertIsFocused().performKeyInput { pressKey(Key.Escape) }
        runOnIdle { assertFalse(child); assertTrue(parent) }
        onNodeWithTag("child-input").assertDoesNotExist()
        onNodeWithTag("parent-input").assertIsFocused()
    }

    @Test fun queuedDialogsKeepOrderAndInvokeCallbacksOnce() = runComposeUiTest {
        val manager = DialogManager()
        var confirmed = 0
        var cancelled = 0
        manager.show(DialogSpec(title = "First", confirmLabel = "Accept", onConfirm = { confirmed++ }))
        manager.show(DialogSpec(title = "Second", cancelLabel = "Dismiss", onCancel = { cancelled++ }))
        setContent { ShadcnUI { DialogHost(manager) } }
        onNodeWithText("First").assertExists()
        onNodeWithText("Second").assertDoesNotExist()
        onNodeWithText("Accept").performClick()
        mainClock.advanceTimeBy(500)
        onNodeWithText("Second").assertExists()
        onNodeWithText("Dismiss").performClick()
        mainClock.advanceTimeBy(500)
        runOnIdle { assertEquals(1, confirmed); assertEquals(1, cancelled); assertTrue(manager.entries.isEmpty()) }
    }

    @Test fun sideDrawerKeepsInsideClicksAndRestoresEditorFocus() = runComposeUiTest {
        var open by mutableStateOf(false)
        setContent { ShadcnUI {
            BasicTextField("editor", {}, Modifier.testTag("editor"))
            Drawer(open, { open = it }, side = DrawerSide.Left, showCloseButton = false) {
                BasicTextField("drawer", {}, Modifier.testTag("drawer-input"))
                Box(Modifier.size(60.dp).testTag("drawer-blank"))
            }
        } }
        onNodeWithTag("editor").requestFocus()
        runOnIdle { open = true }
        onNodeWithTag("drawer-input").assertIsFocused()
        onNodeWithTag("drawer-blank").performTouchInput { click() }
        runOnIdle { assertTrue(open) }
        onNodeWithTag("drawer-input").performKeyInput { pressKey(Key.Escape) }
        runOnIdle { assertFalse(open) }
        onNodeWithTag("drawer-input").assertDoesNotExist()
        onNodeWithTag("editor").assertIsFocused()
    }

    @Test fun emptySideDrawerRespondsToEscape() = runComposeUiTest {
        var open by mutableStateOf(true)
        setContent { ShadcnUI {
            Drawer(open, { open = it }, side = DrawerSide.Right, showCloseButton = false) {
                Box(Modifier.size(60.dp).testTag("drawer-blank"))
            }
        } }
        onNodeWithTag("drawer-blank").performKeyInput { pressKey(Key.Escape) }
        runOnIdle { assertFalse(open) }
    }
    @Test fun bottomDrawerFocusesContentAndDismissesWithEscape() = runComposeUiTest {
        var open by mutableStateOf(false)
        setContent { ShadcnUI {
            BasicTextField("editor", {}, Modifier.testTag("editor"))
            Drawer(open, { open = it }, side = DrawerSide.Bottom) {
                BasicTextField("drawer", {}, Modifier.testTag("bottom-input"))
            }
        } }
        onNodeWithTag("editor").requestFocus()
        runOnIdle { open = true }
        onNodeWithTag("bottom-input").assertIsFocused().performKeyInput { pressKey(Key.Escape) }
        runOnIdle { assertFalse(open) }
        onNodeWithTag("bottom-input").assertDoesNotExist()
        onNodeWithTag("editor").assertIsFocused()
    }

}
