@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package com.github.jershell.shadcn.promptkit

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.github.jershell.shadcn.components.button.*
import com.github.jershell.shadcn.components.promptkit.*
import com.github.jershell.shadcn.containers.ShadcnUI
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.*

@Composable
private fun StatusTestHost(content: @Composable () -> Unit) {
    ShadcnUI {
        CompositionLocalProvider(LocalPromptKitMotionEnabled provides false) {
            Box(Modifier.size(640.dp, 600.dp)) { content() }
        }
    }
}

class PromptStatusInteractionTest {
    @Test fun reasoningControlledStateAndCollapsedFocusSubtree() = runComposeUiTest {
        var expanded by mutableStateOf(false)
        var requests = 0
        setContent { StatusTestHost {
            Reasoning(open = expanded, onOpenChange = { requests++; expanded = it }) {
                ReasoningTrigger("Details", Modifier.testTag("trigger"))
                ReasoningContent { Button({}, Modifier.testTag("inside")) { ButtonText("Inner action") } }
            }
        } }
        onNodeWithTag("inside").assertDoesNotExist()
        onNodeWithTag("trigger").requestFocus()
        onNodeWithTag("trigger").performKeyInput { keyDown(Key.Enter); keyUp(Key.Enter) }
        onNodeWithTag("inside").assertExists()
        onNodeWithTag("trigger").performClick()
        onNodeWithTag("inside").assertDoesNotExist()
        runOnIdle { assertEquals(2, requests) }
    }

    @Test fun streamingReasoningOpensClosesAndRespectsControlledOverride() = runComposeUiTest {
        var streaming by mutableStateOf(false)
        var controlled by mutableStateOf<Boolean?>(null)
        setContent { StatusTestHost {
            Reasoning(isStreaming = streaming, open = controlled) {
                ReasoningTrigger("Reasoning")
                ReasoningContent("Visible explanation", Modifier.testTag("body"))
            }
        } }
        onNodeWithText("Visible explanation").assertDoesNotExist()
        runOnIdle { streaming = true }
        onNodeWithText("Visible explanation").assertExists()
        runOnIdle { streaming = false }
        onNodeWithText("Visible explanation").assertDoesNotExist()
        runOnIdle { controlled = false; streaming = true }
        onNodeWithText("Visible explanation").assertDoesNotExist()
    }

    @Test fun stepsHaveIndependentExpansionWithReorderedStableKeys() = runComposeUiTest {
        var reversed by mutableStateOf(false)
        setContent { StatusTestHost {
            ChainOfThought {
                (if (reversed) listOf("two", "one") else listOf("one", "two")).forEach { id ->
                    Step(id) {
                        ChainOfThoughtTrigger(id)
                        ChainOfThoughtContent { ChainOfThoughtItem("Content $id") }
                    }
                }
            }
        } }
        onNodeWithText("one").performClick()
        onNodeWithText("Content one").assertExists()
        onNodeWithText("Content two").assertDoesNotExist()
        runOnIdle { reversed = true }
        onNodeWithText("Content one").assertExists()
        onNodeWithText("Content two").assertDoesNotExist()
    }

    @Test fun toolShowsCurrentOutputAndErrorWithoutLosingExpansion() = runComposeUiTest {
        var part by mutableStateOf(ToolPart("search", ToolState.InputAvailable,
            input = JsonPrimitive("query"), toolCallId = "call-1"))
        setContent { StatusTestHost { Tool(part) } }
        onNodeWithText("search").performClick()
        onNodeWithText("Input").assertExists()
        runOnIdle { part = part.copy(state = ToolState.OutputAvailable, output = JsonPrimitive("found")) }
        onNodeWithText("Completed").assertExists()
        onNodeWithText("Output").assertExists()
        runOnIdle { part = part.copy(state = ToolState.OutputError, errorText = "Search failed") }
        onNodeWithText("Search failed").assertExists()
    }

    @Test fun feedbackKeyboardActionsUseLatestCallbacksAndSelection() = runComposeUiTest {
        var generation by mutableIntStateOf(1)
        val calls = mutableListOf<Int>()
        var selection by mutableStateOf(Feedback.None)
        setContent { StatusTestHost {
            val captured = generation
            FeedbackBar(selection = selection, onSelectionChange = { selection = it },
                onHelpful = { calls += captured })
        } }
        onNodeWithContentDescription("Helpful").requestFocus()
        onNodeWithContentDescription("Helpful").performKeyInput { keyDown(Key.Enter); keyUp(Key.Enter) }
        onNodeWithContentDescription("Helpful").assertIsSelected()
        runOnIdle { generation = 2 }
        onNodeWithContentDescription("Helpful").performClick()
        runOnIdle { assertEquals(listOf(1, 2), calls) }
    }

    @Test fun citationOpensByTouchAndOffersExplicitWebLinkAction() = runComposeUiTest {
        var opened: String? = null
        setContent { StatusTestHost {
            Source("https://example.com/reference", onOpenLink = { opened = it }) {
                SourceContent("Reference title", "A useful reference")
            }
        } }
        onNodeWithText("example.com").performTouchInput { click() }
        onNodeWithText("Reference title").assertExists()
        onNodeWithText("Open source").performClick()
        runOnIdle { assertEquals("https://example.com/reference", opened) }
    }

    @Test fun citationCollapseClosesFocusPreviewWithoutImmediatelyReopening() = runComposeUiTest {
        setContent { StatusTestHost {
            Source("https://example.com/reference", openDelayMillis = 0, closeDelayMillis = 0,
                anchor = { SourceTrigger(modifier = Modifier.testTag("citation")) }) {
                SourceContent("Focus preview", "A useful reference")
            }
        } }
        onNodeWithTag("citation").requestFocus()
        onNodeWithText("Focus preview").assertExists()
        onNodeWithTag("citation").performSemanticsAction(SemanticsActions.Collapse) { it() }
        mainClock.advanceTimeBy(500)
        onNodeWithText("Focus preview").assertDoesNotExist()
        onNodeWithTag("citation").performSemanticsAction(SemanticsActions.Expand) { it() }
        onNodeWithText("Focus preview").assertExists()
    }

    @Test fun allLoadersHaveOneStableAccessibleStatusWithMotionDisabled() = runComposeUiTest {
        setContent { StatusTestHost {
            Column { LoaderVariant.entries.forEach { variant ->
                Loader(Modifier.testTag(variant.name), variant, loadingLabel = "Busy", text = "Thinking")
            } }
        } }
        LoaderVariant.entries.forEach { variant ->
            onNodeWithTag(variant.name).assertExists()
            onNodeWithTag(variant.name).assertContentDescriptionEquals(
                if (variant in listOf(LoaderVariant.TextBlink, LoaderVariant.TextShimmer, LoaderVariant.LoadingDots)) "Thinking" else "Busy")
        }
    }

    @Test fun systemCtaAndThinkingStopAreRealActionsAndDisabledFeedbackRejectsClicks() = runComposeUiTest {
        var calls = 0
        setContent { StatusTestHost {
            Column {
                SystemMessage("Connection failed", variant = SystemMessageVariant.Error,
                    cta = SystemMessageAction("Retry", { calls++ }, ButtonVariant.Outline))
                ThinkingBar(onStop = { calls++ }, stopLabel = "Stop")
                FeedbackBar(enabled = false, onHelpful = { calls++ })
            }
        } }
        onNodeWithText("Retry").performClick()
        onNodeWithText("Stop").performClick()
        onNodeWithContentDescription("Helpful").assertIsNotEnabled().performTouchInput { click() }
        runOnIdle { assertEquals(2, calls) }
    }
}
