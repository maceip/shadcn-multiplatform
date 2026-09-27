@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package com.github.jershell.shadcn.interaction

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.github.jershell.shadcn.components.button.Button
import com.github.jershell.shadcn.components.contextmenu.ContextMenu
import com.github.jershell.shadcn.components.dropdownmenu.*
import com.github.jershell.shadcn.containers.ShadcnUI
import kotlin.test.*

class MenuInteractionTest {
    @Test fun touchLongPressStaysOpenAfterReleaseAndCanSelectAnItem() = runComposeUiTest {
        var clicks = 0
        setContent { ShadcnUI {
            ContextMenu(modifier = Modifier.size(300.dp).testTag("area"), menu = {
                DropdownMenuItem(onClick = { clicks++ }) { DropdownMenuItemText("Context action") }
            }) { BasicText("Long press") }
        } }
        onNodeWithTag("area").performTouchInput { longClick() }
        onNodeWithText("Context action").assertIsDisplayed().performClick()
        runOnIdle { assertEquals(1, clicks) }
        onNodeWithText("Context action").assertDoesNotExist()
    }

    @Test fun disabledAreaAndQuickTapDoNotOpenContextMenu() = runComposeUiTest {
        var enabled by mutableStateOf(true)
        setContent { ShadcnUI {
            ContextMenu(enabled = enabled, modifier = Modifier.size(300.dp).testTag("area"), menu = {
                DropdownMenuItem(onClick = {}) { DropdownMenuItemText("Context action") }
            }) { BasicText("Long press") }
        } }
        onNodeWithTag("area").performTouchInput { click() }
        onNodeWithText("Context action").assertDoesNotExist()
        runOnIdle { enabled = false }
        onNodeWithTag("area").performTouchInput { longClick() }
        onNodeWithText("Context action").assertDoesNotExist()
    }

    @Test fun nestedSubmenusOpenWithArrowsAndReturnFocusOneLevelAtATime() = runComposeUiTest {
        var root by mutableStateOf(false)
        var child by mutableStateOf(false)
        var grandchild by mutableStateOf(false)
        setContent { ShadcnUI {
            DropdownMenu(root, { root = it }, anchor = { Button(onClick = { root = true }) { BasicText("Menu") } }) {
                DropdownMenuContent {
                    DropdownMenuSub(child, { child = it }, trigger = { DropdownMenuItemText("Child") }) {
                        DropdownMenuItem(onClick = {}, enabled = false) { DropdownMenuItemText("Disabled") }
                        DropdownMenuSub(grandchild, { grandchild = it }, trigger = { DropdownMenuItemText("Grandchild") }) {
                            DropdownMenuItem(onClick = {}) { DropdownMenuItemText("Leaf") }
                        }
                    }
                }
            }
        } }
        onNodeWithText("Menu").performClick()
        onNode(isRoot() and hasAnyDescendant(hasText("Child"))).performKeyInput { pressKey(Key.DirectionDown) }
        onNodeWithText("Child").assertIsFocused()
        onNodeWithText("Child").performKeyInput { pressKey(Key.DirectionRight) }
        onNodeWithText("Grandchild").assertIsFocused()
        onNodeWithText("Grandchild").performKeyInput { pressKey(Key.DirectionRight) }
        onNodeWithText("Leaf").assertIsFocused()
        onNodeWithText("Leaf").performKeyInput { pressKey(Key.DirectionLeft) }
        runOnIdle { assertFalse(grandchild); assertTrue(child); assertTrue(root) }
        onNodeWithText("Grandchild").assertIsFocused()
        onNodeWithText("Grandchild").performKeyInput { pressKey(Key.Escape) }
        runOnIdle { assertFalse(child); assertTrue(root) }
        onNodeWithText("Child").assertIsFocused()
    }
    @Test fun rightClickMenuSupportsKeyboardAndRestoresFocus() = runComposeUiTest {
        setContent { ShadcnUI {
            ContextMenu(modifier = Modifier.size(300.dp).testTag("area"), menu = {
                DropdownMenuItem(onClick = {}, enabled = false) { DropdownMenuItemText("Disabled") }
                DropdownMenuItem(onClick = {}) { DropdownMenuItemText("First") }
                DropdownMenuItem(onClick = {}) { DropdownMenuItemText("Last") }
            }) { androidx.compose.foundation.text.BasicTextField("editor", {}, Modifier.testTag("editor")) }
        } }
        onNodeWithTag("editor").requestFocus()
        onNodeWithTag("area").performMouseInput { click(button = MouseButton.Secondary) }
        onNode(isRoot() and hasAnyDescendant(hasText("First"))).performKeyInput { pressKey(Key.DirectionDown) }
        onNodeWithText("First").assertIsFocused()
        onNodeWithText("First").performKeyInput { pressKey(Key.MoveEnd) }
        onNodeWithText("Last").assertIsFocused()
        onNodeWithText("Last").performKeyInput { pressKey(Key.Escape) }
        onNodeWithText("First").assertDoesNotExist()
        onNodeWithTag("editor").assertIsFocused()
    }

    @Test fun submenuUsesMirroredArrowsInRtlAndSelectionClosesAncestors() = runComposeUiTest {
        var root by mutableStateOf(false)
        var child by mutableStateOf(false)
        var selections = 0
        setContent { CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Rtl) {
            ShadcnUI {
                DropdownMenu(root, { root = it }, anchor = { Button(onClick = { root = true }) { BasicText("Menu") } }) {
                    DropdownMenuContent {
                        DropdownMenuSub(child, { child = it }, trigger = { DropdownMenuItemText("Child") }) {
                            DropdownMenuItem(onClick = { selections++ }) { DropdownMenuItemText("Leaf") }
                        }
                    }
                }
            }
        } }
        onNodeWithText("Menu").performClick()
        onNodeWithText("Child").requestFocus().performKeyInput { pressKey(Key.DirectionLeft) }
        onNodeWithText("Leaf").assertIsFocused().performKeyInput { pressKey(Key.DirectionRight) }
        runOnIdle { assertFalse(child); assertTrue(root) }
        onNodeWithText("Child").assertIsFocused().performKeyInput { pressKey(Key.DirectionLeft) }
        onNodeWithText("Leaf").performClick()
        runOnIdle { assertFalse(child); assertFalse(root); assertEquals(1, selections) }
    }

}
