package com.github.jershell.shadcn.components.terminal

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The measured character grid, independent of the pixel dimensions of the view. */
@Serializable
data class TerminalSize(val columns: Int, val rows: Int) {
    init {
        require(columns > 0 && rows > 0) { "Terminal dimensions must be positive" }
    }
}

data class TerminalState(
    val isReady: Boolean = false,
    val size: TerminalSize? = null,
    val title: String = "",
    val error: String? = null,
    val isDisposed: Boolean = false,
)

/**
 * Connects an application-owned PTY/SSH/WebSocket transport to [Terminal].
 *
 * Send decoded UTF-8 output with [write], and send [Terminal]'s `onInput` callback to the
 * transport. Forward `onResize` to the PTY. This controller never starts a shell or network
 * connection. Use one controller per mounted terminal. Commands are ordered and bounded;
 * suspending writers receive backpressure while the terminal loads.
 *
 * A removed view loses its terminal screen. A retained controller buffers subsequent commands
 * until a new view is ready, but does not replay already-rendered output. Keep sessions mounted
 * while switching tabs, or restore output from your transport when reconnecting.
 */
class TerminalController(commandCapacity: Int = 64) {
    init { require(commandCapacity > 0) }

    private val queue = Channel<TerminalCommand>(commandCapacity)
    internal val commands = queue.receiveAsFlow()
    private val mutableState = MutableStateFlow(TerminalState())
    val state: StateFlow<TerminalState> = mutableState.asStateFlow()

    /** Output bytes must be decoded with a streaming UTF-8 decoder by the transport. */
    suspend fun write(text: String) = send(TerminalCommand("write", data = text))

    /** Non-blocking output. False means full or disposed; callers must retain/retry that data. */
    fun tryWrite(text: String): Boolean = queue.trySend(TerminalCommand("write", data = text)).isSuccess

    suspend fun clear() = send(TerminalCommand("clear"))
    suspend fun reset() = send(TerminalCommand("reset"))
    suspend fun focus() = send(TerminalCommand("focus"))
    suspend fun fit() = send(TerminalCommand("fit"))
    suspend fun resize(size: TerminalSize) = send(TerminalCommand("resize", columns = size.columns, rows = size.rows))

    /** Paste through xterm, preserving bracketed-paste mode; this produces `onInput`. */
    suspend fun paste(text: String) = send(TerminalCommand("paste", data = text))

    /** Send a terminal key, including keys unavailable on many software keyboards. */
    suspend fun sendKey(key: TerminalKey) = send(TerminalCommand("key", data = key.name))

    /** Send a control chord for A-Z, [, \\, ], ^, _, or ?. */
    suspend fun sendControl(character: Char) = send(TerminalCommand("input", data = terminalControlSequence(character)))

    /** Permanently closes this controller and releases queued output. Idempotent. */
    fun dispose() {
        mutableState.update { it.copy(isReady = false, isDisposed = true) }
        queue.cancel()
    }

    internal fun ready(size: TerminalSize) {
        mutableState.update { if (it.isDisposed) it else it.copy(isReady = true, size = size, error = null) }
    }

    internal fun resized(size: TerminalSize) { mutableState.update { it.copy(size = size) } }
    internal fun titled(title: String) { mutableState.update { it.copy(title = title) } }
    internal fun failed(message: String) { mutableState.update { it.copy(isReady = false, error = message) } }
    internal fun detached() { mutableState.update { it.copy(isReady = false) } }

    private suspend fun send(command: TerminalCommand) {
        check(!state.value.isDisposed) { "TerminalController is disposed" }
        queue.send(command)
    }
}

/** Owns/disposes a controller with this composition. Hoist a controller to retain its transport. */
@Composable
fun rememberTerminalController(): TerminalController {
    val controller = remember { TerminalController() }
    DisposableEffect(controller) { onDispose(controller::dispose) }
    return controller
}

enum class TerminalKey(val label: String, internal val sequence: String) {
    Escape("Esc", "\u001b"), Tab("Tab", "\t"),
    Up("↑", "\u001b[A"), Down("↓", "\u001b[B"),
    Right("→", "\u001b[C"), Left("←", "\u001b[D"),
    Home("Home", "\u001b[H"), End("End", "\u001b[F"),
    PageUp("PgUp", "\u001b[5~"), PageDown("PgDn", "\u001b[6~"),
    Delete("Del", "\u001b[3~"), Backspace("⌫", "\u007f"),
    Enter("Enter", "\r"), Interrupt("Ctrl+C", "\u0003"),
    EndOfFile("Ctrl+D", "\u0004"), Suspend("Ctrl+Z", "\u001a"),
}

internal fun terminalControlSequence(character: Char): String {
    val upper = character.uppercaseChar()
    require(upper in '@'..'_' || upper == '?') { "Unsupported control chord: $character" }
    return (if (upper == '?') 127 else upper.code and 31).toChar().toString()
}

@Serializable
internal data class TerminalCommand(
    val type: String,
    val data: String? = null,
    val columns: Int? = null,
    val rows: Int? = null,
    val id: Int? = null,
    val theme: Map<String, String>? = null,
)

@Serializable
internal data class TerminalEvent(
    val type: String,
    val data: String? = null,
    val columns: Int? = null,
    val rows: Int? = null,
    val id: Int? = null,
)

internal val terminalJson = Json { ignoreUnknownKeys = true }

internal fun TerminalCommand.toJavaScript(): String =
    "window.shadcnTerminal.dispatch(${terminalJson.encodeToString(this)});"
