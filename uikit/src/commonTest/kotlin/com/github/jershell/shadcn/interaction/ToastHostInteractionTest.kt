@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package com.github.jershell.shadcn.interaction

import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.github.jershell.shadcn.components.button.Button
import com.github.jershell.shadcn.components.button.ButtonText
import com.github.jershell.shadcn.components.toast.ToastHost
import com.github.jershell.shadcn.components.toast.ToastManager
import com.github.jershell.shadcn.motion.ShadcnMotion
import com.github.jershell.shadcn.theme.Mode
import com.github.jershell.shadcn.theme.ShadcnTheme
import kotlin.test.Test
import kotlin.test.assertEquals

class ToastHostInteractionTest {
    @Test fun emptyStackLeavesUnderlyingCornerControlClickable() = runComposeUiTest {
        mainClock.autoAdvance = false
        val manager = ToastManager()
        var calls = 0
        setContent {
            Box(Modifier.size(480.dp, 320.dp)) {
                ShadcnTheme(mode = Mode.Light) {
                    ShadcnMotion(enabled = false) {
                        Button({ calls++ }, Modifier.align(Alignment.BottomEnd).padding(40.dp)) {
                            ButtonText("Corner action")
                        }
                        ToastHost(manager = manager)
                    }
                }
            }
        }
        mainClock.advanceTimeByFrame()
        onNodeWithText("Corner action").performMouseInput { moveTo(center); click() }
        runOnIdle { assertEquals(1, calls) }
    }
}
