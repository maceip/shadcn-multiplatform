@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package com.github.jershell.shadcn.promptkit

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.github.jershell.shadcn.components.promptkit.*
import com.github.jershell.shadcn.containers.ShadcnUI
import com.github.jershell.shadcn.motion.LocalShadcnMotionEnabled
import kotlinx.coroutines.flow.emptyFlow
import kotlin.test.*

class PromptLayoutInteractionTest {
    @Test fun switchingConversationDoesNotCarryAnotherThreadsDraft() = runComposeUiTest {
        var selected by mutableStateOf("a")
        setContent { ShadcnUI {
            CompositionLocalProvider(LocalShadcnMotionEnabled provides false) {
                val transport = remember { ChatTransport { emptyFlow<ChatEvent>() } }
                val session = rememberChatSessionState(transport)
                FullChatApp(session, listOf(ChatHistoryEntry("a", "Thread A"), ChatHistoryEntry("b", "Thread B")),
                    selected, { selected = it }, {}, Modifier.size(320.dp, 560.dp))
            }
        } }
        onNodeWithContentDescription("Message").performTextInput("Private draft for thread A")
        onNodeWithContentDescription("Message").assertTextContains("Private draft for thread A")
        onNodeWithText("Conversation history").performClick()
        onNodeWithText("Thread B").performClick()
        onNodeWithContentDescription("Message").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        runOnIdle { assertEquals("b", selected) }
    }

    @Test fun userTextAlignsToConversationEndAndAssistantTextToStartAtPhoneWidth() = runComposeUiTest {
        setContent { ShadcnUI {
            CompositionLocalProvider(LocalShadcnMotionEnabled provides false) {
                Column(Modifier.width(320.dp)) {
                    MessageComponent(ChatMessage("user", ChatRole.User, "Short user"), Modifier.testTag("user"))
                    MessageComponent(ChatMessage("assistant", ChatRole.Assistant, "Short assistant"), Modifier.testTag("assistant"))
                }
            }
        } }
        // Assistant Markdown parses asynchronously outside Compose's idle tracking.
        // Wait for the rendered content before measuring either message's final bounds.
        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithText("Short assistant").fetchSemanticsNodes().size == 1
        }
        val user = onNodeWithTag("user").fetchSemanticsNode().boundsInRoot
        val assistant = onNodeWithTag("assistant").fetchSemanticsNode().boundsInRoot
        val userText = onNodeWithText("Short user").fetchSemanticsNode().boundsInRoot
        val assistantText = onNodeWithText("Short assistant").fetchSemanticsNode().boundsInRoot
        assertTrue(userText.center.x > user.center.x)
        assertTrue(assistantText.center.x < assistant.center.x)
        assertTrue(userText.right <= user.right && assistantText.left >= assistant.left)
    }

    @Test fun longHighlightedSuggestionGrowsAndPreservesLatestKeyboardAndDisabledBehavior() = runComposeUiTest {
        val paragraph = "Explain how this application preserves each message, keeps the editor responsive, and handles a failed connection."
        var generation by mutableIntStateOf(1)
        var enabled by mutableStateOf(true)
        val calls = mutableListOf<Int>()
        setContent { ShadcnUI {
            CompositionLocalProvider(LocalShadcnMotionEnabled provides false) {
                val captured = generation
                PromptSuggestion(paragraph, { calls += captured }, Modifier.width(180.dp).testTag("suggestion"),
                    highlight = "application", enabled = enabled)
            }
        } }
        onNodeWithTag("suggestion").assertHeightIsAtLeast(64.dp)
        onNodeWithTag("suggestion").requestFocus()
        onNodeWithTag("suggestion").performKeyInput { keyDown(Key.Enter); keyUp(Key.Enter) }
        runOnIdle { generation = 2 }
        onNodeWithTag("suggestion").performTouchInput { click() }
        runOnIdle { enabled = false }
        onNodeWithTag("suggestion").assertIsNotEnabled().performTouchInput { click() }
        runOnIdle { assertEquals(listOf(1, 2), calls) }
    }
}
