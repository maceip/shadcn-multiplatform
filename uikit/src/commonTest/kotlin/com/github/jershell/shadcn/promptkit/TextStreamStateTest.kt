@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.github.jershell.shadcn.promptkit

import com.github.jershell.shadcn.components.promptkit.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.*
import kotlin.test.*

class TextStreamStateTest {
    private val timed = TextStreamOptions(segmentDelayMillis = 10, characterChunkSize = 1)

    @Test fun cancelledOwnerRejectsStartAndCancellationFinalizesRunningState() = runTest {
        val owner = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        var completions = 0
        val state = TextStreamState(owner, timed, { completions++ })
        state.start("abcdef")
        runCurrent()
        state.pause()
        owner.cancel()
        runCurrent()
        assertFalse(state.isRunning)
        assertFalse(state.isPaused)
        assertFalse(state.isComplete)
        assertEquals(0, completions)
        state.start("cannot run")
        assertFalse(state.isRunning)
        assertEquals("", state.displayedText)
    }

    @Test fun ownerCancellationBeforeDispatchNeverLeavesRunningStateStuck() = runTest {
        val owner = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val state = TextStreamState(owner, timed)
        state.start("queued")
        owner.cancel()
        runCurrent()
        assertFalse(state.isRunning)
        assertFalse(state.isComplete)
        assertEquals("", state.displayedText)
    }

    @Test fun stringCompletesExactlyOnceAndCanRestart() = runTest {
        var completions = 0
        val state = TextStreamState(this, timed, onComplete = { completions++ })
        state.start("abc")
        runCurrent()
        assertEquals("a", state.displayedText)
        advanceUntilIdle()
        assertEquals("abc", state.displayedText)
        assertTrue(state.isComplete)
        assertFalse(state.isRunning)
        assertEquals(1, completions)
        state.restart()
        advanceUntilIdle()
        assertEquals("abc", state.displayedText)
        assertEquals(2, completions)
    }

    @Test fun pauseFreezesStringThenResumesWithoutDuplicatingText() = runTest {
        val state = TextStreamState(this, timed)
        state.start("abcd")
        runCurrent()
        state.pause()
        advanceTimeBy(500)
        runCurrent()
        assertEquals("a", state.displayedText)
        assertTrue(state.isPaused)
        state.resume()
        advanceUntilIdle()
        assertEquals("abcd", state.displayedText)
        assertTrue(state.isComplete)
    }

    @Test fun flowPauseBackpressuresSourceAndResetCancelsCollection() = runTest {
        var emissions = 0
        var cancelled = false
        val state = TextStreamState(this, timed)
        state.start(flow {
            try {
                while (true) { emit("x"); emissions++; delay(5) }
            } finally { cancelled = true }
        })
        runCurrent()
        state.pause()
        advanceTimeBy(100)
        runCurrent()
        val pausedText = state.displayedText
        val pausedEmissions = emissions
        advanceTimeBy(100)
        runCurrent()
        assertEquals(pausedText, state.displayedText)
        assertEquals(pausedEmissions, emissions)
        state.reset()
        runCurrent()
        assertTrue(cancelled)
        assertEquals("", state.displayedText)
        assertFalse(state.isRunning)
        assertFalse(state.isComplete)
    }

    @Test fun replacingFlowPreventsOldChunksAndOldCompletion() = runTest {
        var completions = 0
        val state = TextStreamState(this, timed, onComplete = { completions++ })
        state.start(flow { emit("old"); delay(500); emit("stale") })
        runCurrent()
        state.start("new")
        advanceUntilIdle()
        assertEquals("new", state.displayedText)
        assertEquals(1, completions)
    }

    @Test fun fadeCompletesOnceAfterFadeDurationAndUsesLatestCallback() = runTest {
        var oldCalls = 0
        var newCalls = 0
        val state = TextStreamState(this, TextStreamOptions(mode = TextStreamMode.Fade,
            segmentDelayMillis = 10, fadeDurationMillis = 100), onComplete = { oldCalls++ })
        state.start("hello world")
        advanceTimeBy(35)
        runCurrent()
        assertEquals("hello world", state.displayedText)
        assertFalse(state.isComplete)
        state.updateCallbacks({ newCalls++ }, {})
        advanceUntilIdle()
        assertEquals(0, oldCalls)
        assertEquals(1, newCalls)
        assertEquals(listOf("hello", " ", "world"), state.segments.map { it.text })
    }

    @Test fun failedFlowReportsErrorWithoutFalseCompletionAndCanRecover() = runTest {
        val expected = IllegalStateException("offline")
        var failure: Throwable? = null
        var completions = 0
        val state = TextStreamState(this, timed, { completions++ }, { failure = it })
        state.start(flow { emit("partial"); throw expected })
        advanceUntilIdle()
        assertSame(expected, failure)
        assertSame(expected, state.error)
        assertFalse(state.isComplete)
        assertFalse(state.isRunning)
        assertEquals(0, completions)
        state.start("recovered")
        advanceUntilIdle()
        assertNull(state.error)
        assertEquals(1, completions)
    }

    @Test fun unicodeClustersAreNeverSplitByTypewriter() = runTest {
        val clusters = listOf("a\u0301", "\uD83D\uDC69\u200D\uD83D\uDCBB", "\uD83C\uDDFA\uD83C\uDDF8", "\uD83D\uDC4D\uD83C\uDFFD")
        val state = TextStreamState(this, timed)
        state.start(clusters.joinToString(""))
        runCurrent()
        assertEquals(clusters.first(), state.displayedText)
        advanceUntilIdle()
        assertEquals(clusters, state.segments.map { it.text })
    }

    @Test fun flowHandlesSplitSurrogatePairAndEmptyCompletion() = runTest {
        var completions = 0
        val state = TextStreamState(this, timed, { completions++ })
        state.start(flow { emit("\uD83D"); emit("\uDE00") })
        advanceUntilIdle()
        assertEquals("\uD83D\uDE00", state.displayedText)
        assertEquals(1, state.segments.size)
        state.start("")
        advanceUntilIdle()
        assertTrue(state.isComplete)
        assertEquals(2, completions)
    }

    @Test fun invalidTimingsAreRejectedAndReducedMotionFinishesImmediately() = runTest {
        assertFailsWith<IllegalArgumentException> { TextStreamOptions(speed = 0) }
        assertFailsWith<IllegalArgumentException> { TextStreamOptions(characterChunkSize = 0) }
        assertFailsWith<IllegalArgumentException> { TextStreamOptions(segmentDelayMillis = -1) }
        val state = TextStreamState(this, TextStreamOptions(animationsEnabled = false))
        state.start("ready")
        runCurrent()
        assertTrue(state.isComplete)
        assertEquals("ready", state.displayedText)
    }

    @Test fun sourceLinksAreRestrictedToWebSchemesAndToolValuesRemainReadable() {
        assertTrue(isSafeSourceLink("https://example.com/a?q=1"))
        assertFalse(isSafeSourceLink("javascript:alert(1)"))
        assertFalse(isSafeSourceLink("file:///etc/passwd"))
        assertFalse(isSafeSourceLink("/relative"))
        assertEquals("example.com", sourceDomain("https://www.example.com/a"))
        assertEquals("null", formatToolValue(JsonNull))
        assertEquals("plain", formatToolValue(JsonPrimitive("plain")))
        assertTrue(formatToolValue(buildJsonObject { put("result", 42) }).contains("42"))
    }
}
