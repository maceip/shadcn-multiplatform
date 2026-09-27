@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.github.jershell.shadcn.components.promptkit

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.*

class ChatSessionTest {
    @Test fun acceptedCallbackCanStartAnotherTurnWithoutLosingRetryOrCancellation() = runTest {
        var requestCount = 0
        val state = ChatSessionState(ChatTransport { flow {
            requestCount++
            if (requestCount >= 2) { delay(100); emit(ChatEvent.Error("new turn failed")) }
        } }, this)
        state.send("first") {
            state.stop()
            state.send("second")
        }
        runCurrent()
        assertTrue(state.isLoading)
        advanceUntilIdle()
        assertEquals("new turn failed", state.error)
        assertTrue(state.retry(), "The old completion must not clear the new request's retry state")
        runCurrent()
        state.stop()
        advanceUntilIdle()
        assertTrue(state.wasStopped)
        assertNull(state.error)
    }

    @Test fun orderedTextAndToolsUpdateInPlace() = runTest {
        val state = ChatSessionState(ChatTransport { flow {
            emit(ChatEvent.TextDelta("Before "))
            emit(ChatEvent.ToolUpdate(ToolPart("tool-clock", ToolState.InputAvailable, toolCallId = "clock")))
            emit(ChatEvent.TextDelta("after"))
            emit(ChatEvent.ToolUpdate(ToolPart("tool-clock", ToolState.OutputAvailable, toolCallId = "clock", output = JsonPrimitive("12:00"))))
        } }, this)
        var accepted = 0
        assertTrue(state.send("What time?") { accepted++ })
        assertFalse(state.send("Duplicate"))
        advanceUntilIdle()
        assertEquals(1, accepted)
        assertEquals(2, state.messages.size)
        val parts = state.messages.last().parts
        assertEquals(3, parts.size)
        assertEquals("Before ", (parts[0] as ChatMessagePart.Text).text)
        assertEquals(ToolState.OutputAvailable, (parts[1] as ChatMessagePart.ToolCall).tool.state)
        assertEquals("after", (parts[2] as ChatMessagePart.Text).text)
        assertFalse(state.isLoading)
        assertNull(state.error)
    }

    @Test fun failedSendPreservesDraftAndRetryDoesNotDuplicateUser() = runTest {
        var calls = 0
        val draft = PromptInputState("keep me")
        val state = ChatSessionState(ChatTransport { flow {
            calls++
            if (calls == 1) throw IllegalStateException("offline")
            emit(ChatEvent.TextDelta("Recovered"))
        } }, this)
        state.send(draft.value) { draft.clear() }
        advanceUntilIdle()
        assertEquals("keep me", draft.value)
        assertEquals("offline", state.error)
        assertTrue(state.retry())
        advanceUntilIdle()
        assertEquals("", draft.value)
        assertEquals(1, state.messages.count { it.role == ChatRole.User })
        assertEquals("Recovered", state.messages.last().text)
        assertFalse(state.retry())
    }

    @Test fun stopCancelsAndAllowsAnotherTurnWithoutStaleOutput() = runTest {
        val transport = ChatTransport { request -> flow {
            emit(ChatEvent.TextDelta(request.messages.last().text))
            delay(1000)
            emit(ChatEvent.TextDelta(" late"))
        } }
        val state = ChatSessionState(transport, this)
        state.send("one")
        runCurrent()
        state.stop()
        assertTrue(state.wasStopped)
        assertFalse(state.isLoading)
        state.send("two")
        advanceUntilIdle()
        assertEquals("one", state.messages[1].text)
        assertEquals("two late", state.messages.last().text)
        assertNull(state.error)
    }

    @Test fun resetPreventsOldReplyFromMutatingNewConversation() = runTest {
        val state = ChatSessionState(ChatTransport { flow { delay(100); emit(ChatEvent.TextDelta("old")) } }, this)
        state.send("first")
        runCurrent()
        state.replaceMessages(listOf(ChatMessage("external", ChatRole.Assistant, "new")))
        advanceUntilIdle()
        assertEquals(listOf("new"), state.messages.map { it.text })
        assertFalse(state.isLoading)
    }

    @Test fun cancelledAndDisposedSessionsRejectWork() = runTest {
        val deadScope = CoroutineScope(Job().also { it.cancel() })
        val state = ChatSessionState(ChatTransport { emptyFlow() }, deadScope)
        assertFalse(state.send("cannot run"))
        assertFalse(state.isLoading)
        val alive = ChatSessionState(ChatTransport { emptyFlow() }, this)
        alive.dispose()
        assertFalse(alive.send("disposed"))
        assertFalse(alive.retry())
    }

    @Test fun explicitErrorEventDoesNotAcceptDraft() = runTest {
        var accepted = false
        val state = ChatSessionState(ChatTransport { flowOf(ChatEvent.Error("quota")) }, this)
        state.send("question") { accepted = true }
        advanceUntilIdle()
        assertEquals("quota", state.error)
        assertFalse(accepted)
        assertFalse(state.isLoading)
    }

    @Test fun duplicateInitialIdsAndEmptySubmissionsAreRejected() = runTest {
        val message = ChatMessage("x", ChatRole.User, "x")
        assertFailsWith<IllegalArgumentException> {
            ChatSessionState(ChatTransport { emptyFlow() }, this, listOf(message, message))
        }
        val state = ChatSessionState(ChatTransport { emptyFlow() }, this)
        assertFalse(state.send(" \n "))
        assertTrue(state.messages.isEmpty())
    }
}
