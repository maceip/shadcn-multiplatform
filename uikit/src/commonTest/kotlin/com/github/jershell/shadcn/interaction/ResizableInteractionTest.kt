@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package com.github.jershell.shadcn.interaction

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.github.jershell.shadcn.components.resizable.*
import com.github.jershell.shadcn.containers.ShadcnUI
import kotlin.test.*

class ResizableInteractionTest {
    private val adjustable = SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress)

    @Test fun keyboardAndAccessibilityRespectAsymmetricMinimumSizes() = runComposeUiTest {
        setContent { ShadcnUI {
            ResizablePanelGroup(Modifier.size(600.dp, 300.dp)) {
                panel(minSizeFraction = 0.65f) { Box(Modifier.testTag("first")) }
                handle()
                panel(minSizeFraction = 0.2f) {}
            }
        } }
        val handle = onNode(adjustable)
        handle.requestFocus()
        handle.performKeyInput { pressKey(Key.MoveHome) }
        runOnIdle { assertEquals(0.65f, handle.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current, 0.001f) }
        handle.performKeyInput { pressKey(Key.MoveEnd) }
        runOnIdle { assertEquals(0.8f, handle.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current, 0.001f) }
        handle.performSemanticsAction(SemanticsActions.SetProgress) { assertTrue(it(0.7f)) }
        runOnIdle { assertEquals(0.7f, handle.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current, 0.001f) }
    }

    @Test fun verticalHandleRespondsToUpAndDown() = runComposeUiTest {
        setContent { ShadcnUI {
            ResizablePanelGroup(Modifier.size(300.dp), orientation = ResizableOrientation.Vertical) {
                panel {}
                handle()
                panel {}
            }
        } }
        onNode(adjustable).requestFocus()
        onNode(adjustable).performKeyInput { pressKey(Key.DirectionDown) }
        runOnIdle { assertEquals(0.51f, onNode(adjustable).fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current, 0.001f) }
    }

    @Test fun dragUsesUpdatedMinimumConstraints() = runComposeUiTest {
        var minimum by mutableStateOf(0.1f)
        setContent { ShadcnUI {
            ResizablePanelGroup(Modifier.size(600.dp, 300.dp)) {
                panel(minSizeFraction = minimum) {}
                handle()
                panel {}
            }
        } }
        runOnIdle { minimum = 0.7f }
        onNode(adjustable).performTouchInput { swipe(center, center.copy(x = center.x - 150f)) }
        runOnIdle { assertTrue(onNode(adjustable).fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current >= 0.7f) }
    }
}
