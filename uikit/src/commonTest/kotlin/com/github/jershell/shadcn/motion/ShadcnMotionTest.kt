@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package com.github.jershell.shadcn.motion

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ShadcnMotionTest {
    @Test
    fun reducedMotionResolvesFiniteRecipesToZeroDuration() = runComposeUiTest {
        var duration = -1
        setContent {
            CompositionLocalProvider(LocalShadcnMotionEnabled provides false) {
                val spec = shadcnTween<Float>(300)
                SideEffect { duration = spec.durationMillis }
            }
        }
        runOnIdle { assertEquals(0, duration) }
    }

    @Test
    fun nestedProviderCannotOverrideAnOuterReducedMotionPreference() = runComposeUiTest {
        var actual = true
        setContent {
            ShadcnMotion(enabled = false) {
                ShadcnMotion(enabled = true) {
                    val enabled = LocalShadcnMotionEnabled.current
                    SideEffect { actual = enabled }
                }
            }
        }
        runOnIdle { assertFalse(actual) }
    }

    @Test
    fun reducedMotionDoesNotRetainHiddenInteractiveContentForAnExitAnimation() = runComposeUiTest {
        var visible by mutableStateOf(true)
        setContent {
            CompositionLocalProvider(LocalShadcnMotionEnabled provides false) {
                ShadcnVisibility(visible) { Box(Modifier.size(40.dp).testTag("motion-panel")) }
            }
        }
        onNodeWithTag("motion-panel").assertExists()
        runOnIdle { visible = false }
        onNodeWithTag("motion-panel").assertDoesNotExist()
    }
}
