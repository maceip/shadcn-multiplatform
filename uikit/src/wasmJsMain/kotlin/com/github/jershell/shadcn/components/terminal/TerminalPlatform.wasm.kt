@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, kotlin.js.ExperimentalWasmJsInterop::class)

package com.github.jershell.shadcn.components.terminal

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.HtmlElementView
import com.github.jershell.shadcn.components.modal.LocalShadcnModalLayerBlocked
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLIFrameElement
import org.w3c.dom.MessageEvent
import org.w3c.dom.events.Event

internal actual object TerminalPlatform {
    actual suspend fun initialize(allowDownload: Boolean) = Unit
}

@Composable
internal actual fun TerminalPlatformView(
    html: String,
    modifier: Modifier,
    onAttach: ((String) -> Unit) -> Unit,
    onMessage: (String) -> Unit,
    onError: (String) -> Unit,
) {
    val currentOnMessage by rememberUpdatedState(onMessage)
    val modalBlocked = LocalShadcnModalLayerBlocked.current
    val listeners = remember { mutableListOf<(Event) -> Unit>() }
    HtmlElementView(
        modifier = modifier,
        factory = {
            (document.createElement("iframe") as HTMLIFrameElement).apply {
                title = "Terminal"
                style.border = "0"
                setModalBlocked(modalBlocked)
                setAttribute("sandbox", "allow-scripts")
                val frame = this
                val handler: (Event) -> Unit = { event ->
                    val message = event as? MessageEvent
                    if (message != null && message.source == frame.contentWindow) {
                        message.data?.toString()?.let(currentOnMessage)
                    }
                }
                listeners.add(handler)
                window.addEventListener("message", handler)
                onAttach { script ->
                    // Native hosts evaluate this wrapper; the iframe only receives parsed JSON.
                    val json = script.removePrefix("window.shadcnTerminal.dispatch(").removeSuffix(");")
                    postTerminalCommand(frame, json)
                }
                srcdoc = html
            }
        },
        update = { frame -> frame.setModalBlocked(modalBlocked) },
        onRelease = { frame ->
            listeners.forEach { window.removeEventListener("message", it) }
            listeners.clear()
            frame.srcdoc = ""
        },
    )
}

private fun postTerminalCommand(frame: HTMLIFrameElement, json: String): Unit =
    js("frame.contentWindow.postMessage(json, '*')")

/** HTML surfaces overlay the Compose canvas, so covered modal layers must suppress their view. */
private fun HTMLIFrameElement.setModalBlocked(blocked: Boolean) {
    // Leave the engine mounted: transport output and acknowledgements continue while covered.
    // Empty visibility restores inheritance from Compose's clipped/offscreen wrapper.
    style.visibility = if (blocked) "hidden" else ""
    if (blocked) {
        setAttribute("inert", "")
        setAttribute("aria-hidden", "true")
    } else {
        removeAttribute("inert")
        removeAttribute("aria-hidden")
    }
}
