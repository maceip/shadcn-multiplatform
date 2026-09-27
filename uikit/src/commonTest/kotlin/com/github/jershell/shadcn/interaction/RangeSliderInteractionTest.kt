@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package com.github.jershell.shadcn.interaction

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.github.jershell.shadcn.components.slider.RangeSlider
import com.github.jershell.shadcn.containers.ShadcnUI
import kotlin.test.*

class RangeSliderInteractionTest {
    @Test fun eachThumbIsIndependentlyKeyboardAdjustable() = runComposeUiTest {
        var value by mutableStateOf(0.2f..0.8f)
        var finishes = 0
        setContent { ShadcnUI { RangeSlider(value, { value = it }, steps = 9, onValueChangeFinished = { finishes++ }) } }
        onNodeWithContentDescription("Range start").requestFocus()
        onNodeWithContentDescription("Range start").performKeyInput { pressKey(Key.DirectionRight) }
        runOnIdle { assertEquals(0.3f, value.start, 0.001f) }
        onNodeWithContentDescription("Range start").performKeyInput { pressKey(Key.Tab) }
        onNodeWithContentDescription("Range end").assertIsFocused()
        onNodeWithContentDescription("Range end").performKeyInput { pressKey(Key.DirectionLeft) }
        runOnIdle {
            assertEquals(0.7f, value.endInclusive, 0.001f)
            assertEquals(2, finishes)
        }
    }

    @Test fun accessibilitySnapsAndCannotCrossTheOtherThumb() = runComposeUiTest {
        var value by mutableStateOf(0.2f..0.8f)
        var finishes = 0
        setContent { ShadcnUI { RangeSlider(value, { value = it }, steps = 9, onValueChangeFinished = { finishes++ }) } }
        onNodeWithContentDescription("Range start").performSemanticsAction(SemanticsActions.SetProgress) { assertTrue(it(0.37f)) }
        runOnIdle { assertEquals(0.4f, value.start, 0.001f) }
        onNodeWithContentDescription("Range start").performSemanticsAction(SemanticsActions.SetProgress) { assertTrue(it(1f)) }
        runOnIdle { assertEquals(value.endInclusive, value.start) }
        onNodeWithContentDescription("Range start").performSemanticsAction(SemanticsActions.SetProgress) { assertFalse(it(1f)) }
        runOnIdle { assertEquals(2, finishes) }
    }

    @Test fun disabledThumbsRejectAccessibilityChanges() = runComposeUiTest {
        var changes = 0
        setContent { ShadcnUI { RangeSlider(0.2f..0.8f, { changes++ }, enabled = false) } }
        for (label in listOf("Range start", "Range end")) {
            onNodeWithContentDescription(label).assertIsNotEnabled()
            onNodeWithContentDescription(label).performSemanticsAction(SemanticsActions.SetProgress) { assertFalse(it(0.5f)) }
        }
        runOnIdle { assertEquals(0, changes) }
    }

    @Test fun tappingTrackUsesLatestCallbacks() = runComposeUiTest {
        var latest by mutableStateOf(false)
        var value by mutableStateOf(0.2f..0.8f)
        var oldCalls = 0
        var newCalls = 0
        setContent { ShadcnUI {
            val callback: (ClosedFloatingPointRange<Float>) -> Unit = if (latest) ({ value = it; newCalls++ }) else ({ value = it; oldCalls++ })
            Box(Modifier.size(300.dp, 60.dp)) { RangeSlider(value, callback, Modifier.testTag("range")) }
        } }
        runOnIdle { latest = true }
        onNodeWithTag("range").performTouchInput { click(center) }
        runOnIdle { assertTrue(newCalls > 0); assertEquals(0, oldCalls) }
    }

    @Test fun degenerateSteppedRangeRemainsFinite() = runComposeUiTest {
        var calls = 0
        setContent { ShadcnUI { RangeSlider(1f..1f, { calls++ }, valueRange = 1f..1f, steps = 9) } }
        onNodeWithContentDescription("Range end").performSemanticsAction(SemanticsActions.SetProgress) { assertFalse(it(2f)) }
        runOnIdle { assertEquals(0, calls) }
    }
}
