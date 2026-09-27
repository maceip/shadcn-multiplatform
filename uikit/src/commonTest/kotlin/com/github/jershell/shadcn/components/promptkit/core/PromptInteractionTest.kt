@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
package com.github.jershell.shadcn.components.promptkit

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.github.jershell.shadcn.containers.ShadcnUI
import kotlin.test.*

class PromptInteractionTest {
    @Test fun enterSubmitsOnceAndShiftEnterInsertsNewline() = runComposeUiTest {
        val state = PromptInputState("question")
        val submissions = mutableListOf<String>()
        setContent { ShadcnUI { PromptInput(state, { submissions += it }, Modifier.width(360.dp)) {
            PromptInputTextarea(Modifier.testTag("draft"))
        } } }
        onNodeWithTag("draft").requestFocus()
        onNodeWithTag("draft").performKeyInput { keyDown(Key.Enter); keyUp(Key.Enter) }
        runOnIdle { assertEquals(listOf("question"), submissions); assertEquals("question", state.value) }
        onNodeWithTag("draft").performKeyInput {
            keyDown(Key.ShiftLeft); keyDown(Key.Enter); keyUp(Key.Enter); keyUp(Key.ShiftLeft)
        }
        runOnIdle { assertTrue(state.value.contains('\n')); assertEquals(1, submissions.size) }
    }

    @Test fun imeAndLoadingDoNotSubmit() {
        val state = PromptInputState("日本語")
        var submitted = 0
        state.fieldValue = TextFieldValue("日本語", composition = TextRange(0, 3))
        assertFalse(state.submit { submitted++ })
        state.value = "ready"
        assertFalse(state.submit(isLoading = true) { submitted++ })
        assertFalse(state.submit(enabled = false) { submitted++ })
        assertEquals(0, submitted)
    }

    @Test fun latestCallbackAndExternalValueAreUsed() = runComposeUiTest {
        var value by mutableStateOf("first")
        var generation by mutableIntStateOf(1)
        val submissions = mutableListOf<String>()
        setContent { ShadcnUI {
            val version = generation
            PromptInput(value, { value = it }, { submissions += "$version:$it" }, Modifier.width(360.dp)) {
                PromptInputTextarea(Modifier.testTag("draft"))
                PromptInputSubmit()
            }
        } }
        runOnIdle { value = "second"; generation = 2 }
        onNodeWithTag("draft").assertTextContains("second")
        onNodeWithText("Send").performClick()
        runOnIdle { assertEquals(listOf("2:second"), submissions) }
    }

    @Test fun suggestionReallyFillsDraft() = runComposeUiTest {
        val state = PromptInputState("how")
        setContent { ShadcnUI {
            PromptAutocompleteHighlight(listOf("How to compose", "Other"), {}, Modifier.width(360.dp), state)
        } }
        onNodeWithText("How to compose").performClick()
        runOnIdle { assertEquals("How to compose", state.value) }
    }

    @Test fun semanticScrollUpStopsFollowAndNewMessagesPreservePosition() = runComposeUiTest {
        lateinit var state: ChatContainerState
        var count by mutableIntStateOf(30)
        setContent { ShadcnUI {
            state = rememberChatContainerState()
            ChatContainerRoot(Modifier.size(300.dp, 180.dp), state) {
                ChatContainerContent(Modifier.testTag("chat")) {
                    repeat(count) { BasicText("Message $it", Modifier.height(40.dp)) }
                }
            }
        } }
        waitUntil { state.scrollState.maxValue > 0 && state.isAtBottom }
        onNodeWithTag("chat").performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, -300f) }
        waitUntil { !state.scrollState.isScrollInProgress }
        var oldPosition = 0
        runOnIdle { assertFalse(state.followsBottom); oldPosition = state.scrollState.value; count += 3 }
        waitForIdle()
        runOnIdle { assertEquals(oldPosition, state.scrollState.value); assertFalse(state.isAtBottom) }
    }

    @Test fun fileTypesSizesAndHighlightUseLiteralMatching() {
        val policy = FileUploadPolicy(accept = ".pdf,image/*", maxFileSize = 100)
        assertTrue(policy.accepts("A.PDF", null, 80))
        assertTrue(policy.accepts("image.bin", "image/png", 80))
        assertFalse(policy.accepts("run.exe", "application/octet-stream", 80))
        assertFalse(policy.accepts("image.png", "image/png", 101))
        assertEquals(2..4, promptHighlightRange("x [A] y", "[a]"))
        assertNull(promptHighlightRange("anything", "  "))
    }

    @Test fun rootWithoutEditorAcceptsPointerWithoutRequestingMissingFocusTarget() = runComposeUiTest {
        var showEditor by mutableStateOf(false)
        setContent { ShadcnUI {
            PromptInput(rememberPromptInputState(), {}, Modifier.width(240.dp).testTag("composer")) {
                BasicText("Optional editor")
                if (showEditor) PromptInputTextarea(Modifier.testTag("editor"))
            }
        } }
        onNodeWithTag("composer").performTouchInput { click() }
        runOnIdle { showEditor = true }
        onNodeWithTag("editor").assertExists()
        runOnIdle { showEditor = false }
        onNodeWithTag("composer").performTouchInput { click() }
    }

    @Test fun actionsWrapAtPhoneWidthAndRemainReachable() = runComposeUiTest {
        setContent { ShadcnUI {
            PromptInput(rememberPromptInputState(), {}, Modifier.width(240.dp)) {
                PromptInputActions(Modifier.testTag("actions")) {
                    repeat(4) { index ->
                        PromptInputAction("Action $index", {}, Modifier.width(100.dp).testTag("action$index")) {
                            BasicText("Action $index")
                        }
                    }
                }
            }
        } }
        val first = onNodeWithTag("action0").fetchSemanticsNode().boundsInRoot
        val last = onNodeWithTag("action3").fetchSemanticsNode().boundsInRoot
        val parent = onNodeWithTag("actions").fetchSemanticsNode().boundsInRoot
        assertTrue(last.top >= first.bottom)
        assertTrue(last.right <= parent.right && last.bottom <= parent.bottom)
        onNodeWithTag("action3").assertIsDisplayed().performClick()
    }
}
