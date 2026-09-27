@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
package com.github.jershell.shadcn.components.promptkit

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import io.github.vinceglb.filekit.BrowserFile
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.WebFile
import kotlinx.browser.document
import org.w3c.dom.events.Event
import kotlin.js.JsAny
import kotlin.js.JsArray
import kotlin.js.toList

@JsFun("(event, left, top, right, bottom) => event.clientX >= left && event.clientX <= right && event.clientY >= top && event.clientY <= bottom")
private external fun inside(event: JsAny, left: Float, top: Float, right: Float, bottom: Float): Boolean
@JsFun("event => Array.from(event.dataTransfer?.files || [])")
private external fun droppedFiles(event: JsAny): JsArray<BrowserFile>
@JsFun("event => Array.from(event.dataTransfer?.types || []).includes('Files')")
private external fun containsFiles(event: JsAny): Boolean

@Composable
internal actual fun Modifier.promptFileDropTarget(enabled: Boolean, policy: FileUploadPolicy, onDragging: (Boolean) -> Unit,
    onFiles: (List<PlatformFile>) -> List<PlatformFile>, onError: (String) -> Unit): Modifier {
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val density = LocalDensity.current.density
    val latestEnabled by rememberUpdatedState(enabled)
    val dragging by rememberUpdatedState(onDragging)
    val receive by rememberUpdatedState(onFiles)
    DisposableEffect(Unit) {
        val over: (Event) -> Unit = { event ->
            val active = latestEnabled && containsFiles(event) &&
                inside(event, bounds.left, bounds.top, bounds.right, bounds.bottom)
            dragging(active)
            if (active) event.preventDefault()
        }
        val leave: (Event) -> Unit = { dragging(false) }
        val drop: (Event) -> Unit = { event ->
            dragging(false)
            if (latestEnabled && inside(event, bounds.left, bounds.top, bounds.right, bounds.bottom)) {
                val files = droppedFiles(event).toList().map { PlatformFile(WebFile.FileWrapper(it)) }
                if (files.isNotEmpty()) { event.preventDefault(); receive(files) }
            }
        }
        document.addEventListener("dragover", over)
        document.addEventListener("dragleave", leave)
        document.addEventListener("drop", drop)
        onDispose {
            document.removeEventListener("dragover", over)
            document.removeEventListener("dragleave", leave)
            document.removeEventListener("drop", drop)
        }
    }
    return onGloballyPositioned {
        val rect = it.boundsInWindow()
        bounds = Rect(rect.left / density, rect.top / density, rect.right / density, rect.bottom / density)
    }
}
