package com.github.jershell.shadcn.ui.components.demo

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.jershell.shadcn.components.button.*
import com.github.jershell.shadcn.components.terminal.*
import com.github.jershell.shadcn.components.typography.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun DemoTerminal() {
    val controller = rememberTerminalController()
    val scope = rememberCoroutineScope()
    var initialized by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    var dimensions by remember { mutableStateOf("") }
    var bells by remember { mutableIntStateOf(0) }
    val state by controller.state.collectAsState()
    fun initialize() { scope.launch {
        loading = true; failure = null
        try { TerminalRuntime.initialize(); initialized = true }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { failure = error.message ?: "Could not initialize terminal" }
        finally { loading = false }
    } }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        H4("Terminal")
        P("Real xterm.js emulation with local echo. This demonstration has no shell or remote connection. Applications connect onInput, write, and onResize to their own PTY transport.")
        if (!initialized) Button(onClick = ::initialize, enabled = !loading) {
            ButtonText(if (loading) "Starting terminal…" else "Open terminal")
        }
        failure?.let { P(it) }
        if (initialized) {
            Terminal(controller, Modifier.fillMaxWidth().height(360.dp),
                onInput = { data -> scope.launch { controller.write(data.replace("\r", "\r\n")) } },
                onResize = { dimensions = "${it.columns} columns × ${it.rows} rows" },
                onBell = { bells++ },
                onError = { failure = it },
                onReady = { scope.launch {
                    controller.write("\u001b[1;36mCompose terminal\u001b[0m\r\nLocal echo — no shell connected.\r\nUnicode: 日本語 λ →\r\n\r\n> ")
                    controller.focus()
                } })
            TerminalExtraKeys(controller)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { scope.launch { controller.clear() } }, enabled = state.isReady, variant = ButtonVariant.Outline) { ButtonText("Clear") }
                Button(onClick = { scope.launch { controller.write("\r\n\u001b[31mRed\u001b[0m · \u001b[32mGreen\u001b[0m · \u001b[1mBold\u001b[0m\u0007\r\n") } }, enabled = state.isReady,
                    variant = ButtonVariant.Outline) { ButtonText("ANSI sample") }
            }
            P("$dimensions · Bell events: $bells")
        }
    }
}
