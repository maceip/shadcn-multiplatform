@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.github.jershell.shadcn.components.terminal

import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import androidx.compose.ui.graphics.Color

class TerminalControllerTest {
    @Test
    fun outputIsOrderedAndBackpressuredUntilViewIsReady() = runTest {
        val controller = TerminalController(commandCapacity = 1)
        controller.write("first")
        val second = launch { controller.write("second") }
        runCurrent()
        assertFalse(second.isCompleted)
        val commands = controller.commands.take(2).toList()
        second.join()
        assertEquals(listOf("first", "second"), commands.map { it.data })
    }

    @Test
    fun nonBlockingOutputReportsOverflowRatherThanDroppingItSilently() = runTest {
        val controller = TerminalController(commandCapacity = 1)
        assertTrue(controller.tryWrite("first"))
        assertFalse(controller.tryWrite("retry this"))
        assertEquals("first", controller.commands.first().data)
        assertTrue(controller.tryWrite("retry this"))
    }

    @Test
    fun disposalReleasesBlockedWritersAndRejectsFurtherCommands() = runTest {
        val controller = TerminalController(commandCapacity = 1)
        controller.write("queued")
        val blocked = async { runCatching { controller.write("blocked") } }
        runCurrent()
        controller.dispose()
        assertTrue(blocked.await().isFailure)
        assertFalse(controller.tryWrite("later"))
        assertTrue(controller.state.value.isDisposed)
        assertFailsWith<IllegalStateException> { controller.clear() }
        controller.dispose()
    }

    @Test
    fun pasteAndKeysGoThroughTheEngineInsteadOfBeingEchoedAsOutput() = runTest {
        val controller = TerminalController()
        controller.paste("echo hello\n")
        controller.sendKey(TerminalKey.Interrupt)
        controller.sendControl('d')
        val commands = controller.commands.take(3).toList()
        assertEquals(listOf("paste", "key", "input"), commands.map { it.type })
        assertEquals(listOf("echo hello\n", "Interrupt", "\u0004"), commands.map { it.data })
    }

    @Test
    fun resizeUsesCharacterCellsAndRejectsInvalidGrids() = runTest {
        val controller = TerminalController()
        controller.resize(TerminalSize(120, 40))
        val command = controller.commands.first()
        assertEquals(120, command.columns)
        assertEquals(40, command.rows)
        assertFailsWith<IllegalArgumentException> { TerminalSize(0, 40) }
        assertFailsWith<IllegalArgumentException> { TerminalSize(80, -1) }
    }

    @Test
    fun hostileOutputIsEncodedAsDataInTheJavaScriptBridge() {
        val output = "');alert(1);//\n</script>\u001b[31m😀"
        val script = TerminalCommand("write", output).toJavaScript()
        val json = script.removePrefix("window.shadcnTerminal.dispatch(").removeSuffix(");")
        assertEquals(output, terminalJson.parseToJsonElement(json).jsonObject["data"]!!.jsonPrimitive.content)
    }

    @Test
    fun fontConfigurationCannotCloseTheInlineJsonScript() {
        val document = terminalDocument("", "", "", "", "{\"fontFamily\":\"</script><script>attack()</script>\"}")
        assertFalse(document.contains("<script>attack()"))
        assertTrue(document.contains("\\u003c/script>"))
        assertTrue(document.contains("connect-src 'none'"))
    }

    @Test
    fun runtimeStateTracksReadinessResizeTitleAndDetach() {
        val controller = TerminalController()
        controller.ready(TerminalSize(80, 24))
        controller.resized(TerminalSize(100, 30))
        controller.titled("server")
        assertTrue(controller.state.value.isReady)
        assertEquals(TerminalSize(100, 30), controller.state.value.size)
        assertEquals("server", controller.state.value.title)
        controller.detached()
        assertFalse(controller.state.value.isReady)
    }

    @Test
    fun themeExposesAllAnsiSlotsAndRejectsTruncatedPalettes() {
        val palette = List(16) { Color(it * 0x10101 or 0xff000000.toInt()) }
        val colors = TerminalColors(Color.Black, Color.White, Color.White, Color.Gray, palette)
        assertEquals(palette[1].terminalCssColor(), colors.webTheme()["red"])
        assertEquals(palette[15].terminalCssColor(), colors.webTheme()["brightWhite"])
        assertEquals(20, colors.webTheme().size)
        assertFailsWith<IllegalArgumentException> { colors.copy(ansiColors = palette.take(15)) }
    }
}
