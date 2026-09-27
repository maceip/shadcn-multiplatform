@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package com.github.jershell.shadcn.interaction

import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.github.jershell.shadcn.components.button.Button
import com.github.jershell.shadcn.components.button.ButtonText
import com.github.jershell.shadcn.components.input.Input
import com.github.jershell.shadcn.components.slider.Slider
import com.github.jershell.shadcn.components.switch.Switch
import com.github.jershell.shadcn.theme.ShadcnTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ControlInteractionTest {
    @Test
    fun buttonActivatesByKeyboardAndUsesLatestCallback() = runComposeUiTest {
        var generation by mutableIntStateOf(1)
        val calls = mutableListOf<Int>()
        setContent { ShadcnTheme {
            val captured = generation
            Button(onClick = { calls += captured }, modifier = Modifier.testTag("button")) { ButtonText("Run") }
        } }
        onNodeWithTag("button").requestFocus()
        onNodeWithTag("button").performKeyInput { keyDown(Key.Enter); keyUp(Key.Enter) }
        runOnIdle { generation = 2 }
        onNodeWithTag("button").performKeyInput { keyDown(Key.Enter); keyUp(Key.Enter) }
        runOnIdle { assertEquals(listOf(1, 2), calls) }
    }

    @Test
    fun disabledButtonRejectsTouch() = runComposeUiTest {
        var calls = 0
        setContent { ShadcnTheme {
            Button(onClick = { calls++ }, enabled = false, modifier = Modifier.testTag("button")) { ButtonText("Run") }
        } }
        onNodeWithTag("button").assertIsNotEnabled()
        onNodeWithTag("button").performTouchInput { click() }
        runOnIdle { assertEquals(0, calls) }
    }

    @Test
    fun switchSupportsKeyboardAndExternalStateUpdates() = runComposeUiTest {
        var checked by mutableStateOf(false)
        setContent { ShadcnTheme {
            Switch(checked = checked, onCheckedChange = { checked = it }, modifier = Modifier.testTag("switch"))
        } }
        onNodeWithTag("switch").requestFocus()
        onNodeWithTag("switch").performKeyInput { keyDown(Key.Spacebar); keyUp(Key.Spacebar) }
        onNodeWithTag("switch").assertIsOn()
        runOnIdle { assertTrue(checked); checked = false }
        onNodeWithTag("switch").assertIsOff()
    }

    @Test
    fun textInputPreservesCodeTextAndExternalEdits() = runComposeUiTest {
        val state = TextFieldState()
        setContent { ShadcnTheme { Input(state = state, modifier = Modifier.width(280.dp).testTag("input")) } }
        onNodeWithTag("input").performTextInput("val x = 42")
        runOnIdle { assertEquals("val x = 42", state.text.toString()); state.edit { replace(0, length, "updated") } }
        onNodeWithTag("input").assertTextContains("updated")
    }

    @Test
    fun disabledSliderReportsDisabled() = runComposeUiTest {
        setContent { ShadcnTheme {
            Slider(value = 0.5f, onValueChange = {}, enabled = false, modifier = Modifier.width(240.dp).testTag("slider"))
        } }
        onNodeWithTag("slider").assertIsNotEnabled()
    }

    @Test
    fun disabledSliderCannotChangeThroughAccessibilityAction() = runComposeUiTest {
        var value by mutableFloatStateOf(0.5f)
        setContent { ShadcnTheme {
            Slider(value = value, onValueChange = { value = it }, enabled = false, modifier = Modifier.width(240.dp).testTag("slider"))
        } }
        val node = onNodeWithTag("slider").fetchSemanticsNode()
        val action = node.config.getOrNull(SemanticsActions.SetProgress)?.action
        runOnIdle {
            if (action != null) assertFalse(action(0.8f), "Disabled accessibility action must reject changes")
            assertEquals(0.5f, value)
        }
    }

    @Test
    fun sliderSupportsArrowKeyStepping() = runComposeUiTest {
        var value by mutableFloatStateOf(0.5f)
        setContent { ShadcnTheme {
            Slider(value = value, onValueChange = { value = it }, steps = 9, modifier = Modifier.width(240.dp).testTag("slider"))
        } }
        onNodeWithTag("slider").requestFocus()
        onNodeWithTag("slider").performKeyInput { keyDown(Key.DirectionRight); keyUp(Key.DirectionRight) }
        runOnIdle { assertEquals(0.6f, value, 0.001f) }
    }

    @Test
    fun sliderAccessibilitySnapsAndFinishesTransaction() = runComposeUiTest {
        var value by mutableFloatStateOf(0.2f)
        var finished = 0
        setContent { ShadcnTheme {
            Slider(value = value, onValueChange = { value = it }, steps = 4,
                onValueChangeFinished = { finished++ }, modifier = Modifier.width(240.dp).testTag("slider"))
        } }
        onNodeWithTag("slider").performSemanticsAction(SemanticsActions.SetProgress) { it(0.67f) }
        runOnIdle { assertEquals(0.6f, value, 0.001f); assertEquals(1, finished) }
    }
}
