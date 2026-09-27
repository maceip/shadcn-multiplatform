package com.github.jershell.shadcn.components.terminal

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import com.composables.icons.lucide.ArrowDown
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.ArrowRight
import com.composables.icons.lucide.ArrowUp
import com.composables.icons.lucide.Lucide
import com.github.jershell.shadcn.components.button.Button
import com.github.jershell.shadcn.components.button.ButtonIcon
import com.github.jershell.shadcn.components.button.ButtonSize
import com.github.jershell.shadcn.components.button.ButtonText
import com.github.jershell.shadcn.components.button.ButtonVariant
import com.github.jershell.shadcn.theme.BaseTokens
import com.github.jershell.shadcn.motion.LocalShadcnMotionEnabled
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

/** xterm options supported consistently by the bundled engine. Changing these recreates the view. */
@Serializable
data class TerminalOptions(
    val fontFamily: String = "Departure Mono, monospace",
    val fontSize: Int = 14,
    val scrollback: Int = 5000,
    val cursorBlink: Boolean = true,
    val screenReaderMode: Boolean = false,
    val convertEol: Boolean = false,
) {
    init {
        require(fontSize in 6..96)
        require(scrollback in 0..100000)
        require(fontFamily.isNotBlank())
    }
}

/**
 * A real xterm.js terminal, using Android WebView, WKWebView, a browser iframe or desktop JCEF.
 * Assets are packaged with the library; no CDN or terminal server is contacted.
 *
 * [onInput] contains terminal input (including control sequences), not already-executed commands.
 * Connect it to your transport and feed output to [TerminalController.write]. [onResize] is the
 * PTY size notification. Callbacks run on the Compose dispatcher. No shell is started here.
 *
 * Desktop applications initialize [TerminalRuntime] before mounting this view. Native/browser
 * surfaces have their platform's overlay limitations; avoid drawing Compose popups over them.
 */
@Composable
fun Terminal(
    controller: TerminalController,
    modifier: Modifier = Modifier,
    options: TerminalOptions = TerminalOptions(),
    colors: TerminalColors = TerminalDefaults.colors(),
    contentDescription: String = "Terminal",
    onInput: (String) -> Unit = {},
    onBinaryInput: (ByteArray) -> Unit = {},
    onResize: (TerminalSize) -> Unit = {},
    onTitleChange: (String) -> Unit = {},
    onBell: () -> Unit = {},
    onReady: () -> Unit = {},
    onError: (String) -> Unit = {},
) {
    val currentOnInput by rememberUpdatedState(onInput)
    val currentOnBinaryInput by rememberUpdatedState(onBinaryInput)
    val currentOnResize by rememberUpdatedState(onResize)
    val currentOnTitle by rememberUpdatedState(onTitleChange)
    val currentOnBell by rememberUpdatedState(onBell)
    val currentOnReady by rememberUpdatedState(onReady)
    val currentOnError by rememberUpdatedState(onError)
    val scope = rememberCoroutineScope()
    val state by controller.state.collectAsState()
    val initialColors = remember(controller, options) { colors }
    val html by produceState<String?>(null, options, initialColors) {
        try { value = terminalHtml(options, initialColors) } catch (error: Exception) {
            if (error is CancellationException) throw error
            controller.failed(error.message ?: "Could not load terminal assets")
            currentOnError(error.message ?: "Could not load terminal assets")
        }
    }
    var dispatch by remember(controller, html) { mutableStateOf<((String) -> Unit)?>(null) }
    var pageReady by remember(controller, html) { mutableStateOf(false) }
    val acknowledgements = remember(controller, html) { Channel<Int>(Channel.UNLIMITED) }
    val motionEnabled = LocalShadcnMotionEnabled.current

    DisposableEffect(controller) { onDispose(controller::detached) }
    LaunchedEffect(dispatch, pageReady, colors) {
        if (pageReady) dispatch?.invoke(TerminalCommand("theme", theme = colors.webTheme()).toJavaScript())
    }
    LaunchedEffect(dispatch, pageReady, motionEnabled, options.cursorBlink) {
        if (pageReady) dispatch?.invoke(TerminalCommand("cursorBlink", data = (motionEnabled && options.cursorBlink).toString()).toJavaScript())
    }
    LaunchedEffect(controller, dispatch, pageReady, state.isDisposed) {
        val send = dispatch
        if (send != null && pageReady && !state.isDisposed) {
            var nextId = 0
            controller.commands.collect { command ->
                val id = nextId++
                send(command.copy(id = id).toJavaScript())
                // xterm's write callback acknowledges parsing, bounding the native/JS backlog.
                if (command.type == "write") {
                    while (acknowledgements.receive() != id) { /* Discard old acknowledgements. */ }
                }
            }
        }
    }

    if (state.isDisposed) return
    val document = html
    if (document == null) {
        BasicText(state.error ?: "Loading terminal…", modifier, style = TextStyle(color = colors.foreground))
        return
    }
    key(controller, document) {
        TerminalPlatformView(
            html = document,
            modifier = modifier.semantics { this.contentDescription = contentDescription },
            onAttach = { dispatch = it },
            onMessage = { message ->
                scope.launch {
                    val event = runCatching { terminalJson.decodeFromString<TerminalEvent>(message) }.getOrNull()
                        ?: return@launch
                    when (event.type) {
                        "ack" -> event.id?.let { acknowledgements.trySend(it) }
                        "ready", "resize" -> {
                            val size = runCatching { TerminalSize(event.columns ?: 0, event.rows ?: 0) }.getOrNull()
                                ?: return@launch
                            if (event.type == "ready") {
                                pageReady = true
                                controller.ready(size)
                                currentOnReady()
                            } else controller.resized(size)
                            currentOnResize(size)
                        }
                        "input" -> currentOnInput(event.data.orEmpty())
                        "binary" -> currentOnBinaryInput(event.data.orEmpty().map { it.code.toByte() }.toByteArray())
                        "title" -> { controller.titled(event.data.orEmpty()); currentOnTitle(event.data.orEmpty()) }
                        "bell" -> currentOnBell()
                        "error" -> {
                            pageReady = false
                            controller.failed(event.data.orEmpty())
                            currentOnError(event.data.orEmpty())
                        }
                    }
                }
            },
            onError = { message -> scope.launch {
                pageReady = false // Cancel any write awaiting an acknowledgement from a failed page.
                controller.failed(message)
                currentOnError(message)
            } },
        )
    }
}

/** Scrollable keys for mobile keyboards. Supply [keys] to customize labels/order. */
@Composable
fun TerminalExtraKeys(
    controller: TerminalController,
    modifier: Modifier = Modifier,
    keys: List<TerminalKey> = TerminalDefaults.extraKeys,
) {
    val scope = rememberCoroutineScope()
    val state by controller.state.collectAsState()
    Row(modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(BaseTokens.token4)) {
        keys.forEach { terminalKey ->
            Button(
                onClick = { scope.launch { controller.sendKey(terminalKey); controller.focus() } },
                enabled = state.isReady && !state.isDisposed,
                size = ButtonSize.Sm,
                variant = ButtonVariant.Outline,
                modifier = Modifier.semantics { contentDescription = terminalKey.name },
            ) {
                val arrow = when (terminalKey) {
                    TerminalKey.Left -> Lucide.ArrowLeft
                    TerminalKey.Down -> Lucide.ArrowDown
                    TerminalKey.Up -> Lucide.ArrowUp
                    TerminalKey.Right -> Lucide.ArrowRight
                    else -> null
                }
                if (arrow != null) ButtonIcon(imageVector = arrow, contentDescription = null)
                else ButtonText(terminalKey.label)
            }
        }
    }
}

internal expect object TerminalPlatform {
    suspend fun initialize(allowDownload: Boolean)
}

/**
 * Explicit runtime setup. Mobile/web use their installed browser and return immediately.
 * Desktop extracts a bundled JCEF artifact or downloads the pinned engine only when
 * [allowDownload] is true. Call from a coroutine; failures are propagated for an application UI.
 * The process-wide desktop engine stays alive until application exit.
 */
object TerminalRuntime {
    suspend fun initialize(allowDownload: Boolean = false) = TerminalPlatform.initialize(allowDownload)
}

@Composable
internal expect fun TerminalPlatformView(
    html: String,
    modifier: Modifier,
    onAttach: ((String) -> Unit) -> Unit,
    onMessage: (String) -> Unit,
    onError: (String) -> Unit,
)
