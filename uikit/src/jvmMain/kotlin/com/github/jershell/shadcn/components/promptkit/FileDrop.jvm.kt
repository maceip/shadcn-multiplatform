@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
package com.github.jershell.shadcn.components.promptkit

import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.*
import io.github.vinceglb.filekit.PlatformFile
import java.awt.datatransfer.DataFlavor
import java.io.File

@Composable
internal actual fun Modifier.promptFileDropTarget(enabled: Boolean, policy: FileUploadPolicy, onDragging: (Boolean) -> Unit,
    onFiles: (List<PlatformFile>) -> List<PlatformFile>, onError: (String) -> Unit): Modifier {
    val latestEnabled by rememberUpdatedState(enabled)
    val dragging by rememberUpdatedState(onDragging)
    val receive by rememberUpdatedState(onFiles)
    val failure by rememberUpdatedState(onError)
    val target = remember {
        object : DragAndDropTarget {
            override fun onEntered(event: DragAndDropEvent) { if (latestEnabled) dragging(true) }
            override fun onExited(event: DragAndDropEvent) { dragging(false) }
            override fun onEnded(event: DragAndDropEvent) { dragging(false) }
            override fun onDrop(event: DragAndDropEvent): Boolean {
                dragging(false)
                if (!latestEnabled) return false
                val transferable = event.awtTransferable
                if (!transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) return false
                val files = try {
                    (transferable.getTransferData(DataFlavor.javaFileListFlavor) as? List<*>)
                        .orEmpty().filterIsInstance<File>().filter { it.isFile }.map { PlatformFile(it) }
                } catch (error: Exception) {
                    failure(error.message ?: "Could not read dropped files."); return false
                }
                if (files.isEmpty()) return false
                receive(files)
                return true
            }
        }
    }
    return dragAndDropTarget(shouldStartDragAndDrop = {
        latestEnabled && it.awtTransferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)
    }, target = target)
}
