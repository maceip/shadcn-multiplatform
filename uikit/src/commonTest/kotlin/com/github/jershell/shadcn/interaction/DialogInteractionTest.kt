@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package com.github.jershell.shadcn.interaction
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.github.jershell.shadcn.components.dialog.Dialog
import com.github.jershell.shadcn.containers.ShadcnUI
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Composable private fun TestDialog(open: Boolean, onDismiss: () -> Unit) {
    Dialog(open = open, onOpenChange = { if (!it) onDismiss() }, showCloseButton = false) {
        Column { BasicTextField(value = "dialog", onValueChange = {}, modifier = Modifier.width(200.dp).testTag("dialog-input")); Box(Modifier.size(30.dp).testTag("dialog-button").clickable {}) }
    }
}
@Composable private fun TestHost(content: @Composable () -> Unit) { ShadcnUI { Box(Modifier.size(640.dp, 480.dp)) { content() } } }

class DialogInteractionTest {
    @Test
    fun clickingOutsideDialogDismissesIt() = runComposeUiTest {
        var open by mutableStateOf(true)
        setContent { TestHost { TestDialog(open, onDismiss = { open = false }) } }
        onNode(isRoot() and hasAnyDescendant(hasTestTag("dialog-input")))
            .performTouchInput { click(androidx.compose.ui.geometry.Offset(1f, 1f)) }
        runOnIdle { assertFalse(open) }
    }

    @Test
    fun escapeDismissesDialogWithoutInteractiveContent() = runComposeUiTest {
        var open by mutableStateOf(true)
        setContent { TestHost {
            Dialog(open, onOpenChange = { open = it }, showCloseButton = false) {
                Box(Modifier.size(100.dp).testTag("empty-panel"))
            }
        } }
        onNodeWithTag("empty-panel").performKeyInput { keyDown(Key.Escape); keyUp(Key.Escape) }
        runOnIdle { assertFalse(open) }
    }

    @Test
    fun clickingNoninteractiveDialogContentDoesNotDismiss() = runComposeUiTest {
        var open by mutableStateOf(true)
        setContent { TestHost {
            Dialog(open = open, onOpenChange = { open = it }, showCloseButton = false) {
                Box(Modifier.size(100.dp).testTag("blank-dialog-content"))
            }
        } }
        onNodeWithTag("blank-dialog-content").performTouchInput { click() }
        runOnIdle { assertTrue(open, "Clicks inside the dialog panel must not count as outside clicks") }
    }

    @Test
    fun openingDialogFocusesItsInput() = runComposeUiTest {
        var open by mutableStateOf(false)
        setContent { TestHost {
            BasicTextField(value = "editor", onValueChange = {}, modifier = Modifier.width(200.dp).testTag("editor"))
            TestDialog(open = open, onDismiss = { open = false })
        } }
        onNodeWithTag("editor").requestFocus()
        onNodeWithTag("editor").assertIsFocused()
        runOnIdle { open = true }
        onNodeWithTag("dialog-input").assertExists()
        // Native dialogs use a separate focus owner. The inactive parent retains
        // its focused child so Compose can restore it when this window closes.
        onNodeWithTag("dialog-input").assertIsFocused()
    }
    @Test
    fun dialogTabTraversalDoesNotReachBackgroundEditor() = runComposeUiTest {
        setContent { TestHost {
            BasicTextField(value = "editor", onValueChange = {}, modifier = Modifier.width(200.dp).testTag("editor"))
            TestDialog(open = true, onDismiss = {})
        } }
        onNodeWithTag("dialog-button").requestFocus()
        repeat(4) {
            onNodeWithTag("dialog-button").performKeyInput { keyDown(Key.Tab); keyUp(Key.Tab) }
            onNodeWithTag("editor").assertIsNotFocused()
        }
    }
    @Test
    fun escapeFromDialogInputDismissesDialog() = runComposeUiTest {
        var open by mutableStateOf(true)
        setContent { TestHost { TestDialog(open = open, onDismiss = { open = false }) } }
        onNodeWithTag("dialog-input").requestFocus()
        onNodeWithTag("dialog-input").performKeyInput { keyDown(Key.Escape); keyUp(Key.Escape) }
        runOnIdle { assertFalse(open, "Escape should request dismissal") }
    }
    @Test
    fun closingDialogRestoresPreviouslyFocusedEditor() = runComposeUiTest {
        var open by mutableStateOf(false)
        setContent { TestHost {
            BasicTextField(value = "editor", onValueChange = {}, modifier = Modifier.width(200.dp).testTag("editor"))
            TestDialog(open = open, onDismiss = { open = false })
        } }
        onNodeWithTag("editor").requestFocus()
        runOnIdle { open = true }
        onNodeWithTag("dialog-input").requestFocus()
        runOnIdle { open = false }
        waitForIdle()
        onNodeWithTag("editor").assertIsFocused()
    }
}
