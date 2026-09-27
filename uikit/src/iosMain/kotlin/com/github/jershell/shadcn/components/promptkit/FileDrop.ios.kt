@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
package com.github.jershell.shadcn.components.promptkit

import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.*
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.mimeType
import io.github.vinceglb.filekit.size
import kotlinx.coroutines.*
import platform.Foundation.*

private data class DropCopy(val file: PlatformFile, val folder: String) {
    fun discard() { NSFileManager.defaultManager.removeItemAtPath(folder, null) }
}

private suspend fun copyDroppedFile(provider: NSItemProvider, policy: FileUploadPolicy): DropCopy? {
    val type = provider.registeredTypeIdentifiers.firstOrNull() as? String ?: return null
    return suspendCancellableCoroutine { continuation ->
        val progress = provider.loadFileRepresentationForTypeIdentifier(type) { url, _ ->
            // The provider URL expires when this callback returns, so validate and copy here.
            var result: DropCopy? = null
            var folder: String? = null
            try {
                if (url != null && continuation.isActive) {
                    val attrs = NSFileManager.defaultManager.attributesOfItemAtPath(url.path.orEmpty(), null)
                    val source = PlatformFile(url)
                    if (attrs?.get(NSFileType) == NSFileTypeRegular &&
                        policy.accepts(source.name, source.mimeType()?.toString(), source.size())) {
                        folder = NSTemporaryDirectory() + "prompt-kit-" + NSUUID().UUIDString + "/"
                        if (NSFileManager.defaultManager.createDirectoryAtPath(folder, true, null, null)) {
                            val destination = NSURL.fileURLWithPath(folder + (url.lastPathComponent ?: "attachment"))
                            if (NSFileManager.defaultManager.copyItemAtURL(url, destination, null)) {
                                val file = PlatformFile(destination)
                                if (policy.accepts(file.name, file.mimeType()?.toString(), file.size())) {
                                    result = DropCopy(file, folder)
                                    folder = null
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                // Invalid provider data is reported as a rejected member of the batch.
            } finally {
                folder?.let { NSFileManager.defaultManager.removeItemAtPath(it, null) }
            }
            if (continuation.isActive) {
                continuation.resume(result) { _, cancelledCopy, _ -> cancelledCopy?.discard() }
            } else result?.discard()
        }
        continuation.invokeOnCancellation { progress.cancel() }
    }
}

@Composable
internal actual fun Modifier.promptFileDropTarget(enabled: Boolean, policy: FileUploadPolicy,
    onDragging: (Boolean) -> Unit, onFiles: (List<PlatformFile>) -> List<PlatformFile>,
    onError: (String) -> Unit): Modifier {
    val latestEnabled by rememberUpdatedState(enabled)
    val latestPolicy by rememberUpdatedState(policy)
    val dragging by rememberUpdatedState(onDragging)
    val receive by rememberUpdatedState(onFiles)
    val failure by rememberUpdatedState(onError)
    val scope = rememberCoroutineScope()
    val target = remember {
        object : DragAndDropTarget {
            override fun onEntered(event: DragAndDropEvent) { if (latestEnabled) dragging(true) }
            override fun onExited(event: DragAndDropEvent) { dragging(false) }
            override fun onEnded(event: DragAndDropEvent) { dragging(false) }
            override fun onDrop(event: DragAndDropEvent): Boolean {
                dragging(false)
                if (!latestEnabled) return false
                val providers = event.items.map { it.itemProvider }
                val selectedPolicy = latestPolicy
                val limit = if (selectedPolicy.multiple) selectedPolicy.maxFiles else 1
                scope.launch {
                    val owned = mutableListOf<DropCopy>()
                    var delivered = emptyList<PlatformFile>()
                    try {
                        for (provider in providers) {
                            if (owned.size >= limit) break
                            ensureActive()
                            copyDroppedFile(provider, selectedPolicy)?.let { owned += it }
                        }
                        if (latestEnabled) {
                            if (owned.isNotEmpty()) delivered = receive(owned.map { it.file })
                            if (owned.size != providers.size) failure("Some dropped files could not be read or exceeded the allowed type, size, or count.")
                        }
                    } finally {
                        owned.filter { it.file !in delivered }.forEach { it.discard() }
                    }
                }
                return providers.isNotEmpty()
            }
        }
    }
    return dragAndDropTarget(shouldStartDragAndDrop = { latestEnabled && it.items.isNotEmpty() }, target = target)
}
