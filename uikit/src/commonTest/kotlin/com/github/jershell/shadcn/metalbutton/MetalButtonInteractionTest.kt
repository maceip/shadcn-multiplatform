@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package com.github.jershell.shadcn.metalbutton

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.github.jershell.shadcn.components.button.ButtonText
import com.github.jershell.shadcn.components.button.ButtonVariant
import com.github.jershell.shadcn.components.metalbutton.MetalButton
import com.github.jershell.shadcn.theme.ShadcnTheme
import kotlin.test.*

class MetalButtonInteractionTest {
    @Test fun filledSurfaceIsClickableBeyondTheLabel() = runComposeUiTest {
        var calls = 0
        setContent { ShadcnTheme {
            MetalButton({ calls++ }, Modifier.size(240.dp, 80.dp).testTag("surface"),
                buttonVariant = ButtonVariant.Default, strength = 0f, animationsEnabled = false) { ButtonText("Run") }
        } }
        onNodeWithTag("surface").performTouchInput { click(centerRight - Offset(10f, 0f)) }
        runOnIdle { assertEquals(1, calls) }
    }

    @Test fun metalLayersDoNotInterceptKeyboardTouchOrLatestCallback() = runComposeUiTest {
        var generation by mutableIntStateOf(1)
        val calls = mutableListOf<Int>()
        setContent { ShadcnTheme {
            val captured = generation
            MetalButton("Upgrade", { calls += captured }, Modifier.testTag("metal"), animationsEnabled = false)
        } }
        onNodeWithText("Upgrade").requestFocus()
        onNodeWithText("Upgrade").performKeyInput { keyDown(Key.Enter); keyUp(Key.Enter) }
        runOnIdle { generation = 2 }
        onNodeWithText("Upgrade").performTouchInput { click() }
        runOnIdle { assertEquals(listOf(1, 2), calls) }
    }

    @Test fun disabledMetalButtonRejectsActivationButKeepsLabelAtZeroStrength() = runComposeUiTest {
        var calls = 0
        setContent { ShadcnTheme {
            MetalButton("Unavailable", { calls++ }, enabled = false, strength = 0f, animationsEnabled = false)
        } }
        onNodeWithText("Unavailable").assertIsNotEnabled().assertIsDisplayed()
        onNodeWithText("Unavailable").performTouchInput { click() }
        runOnIdle { assertEquals(0, calls) }
    }
}
