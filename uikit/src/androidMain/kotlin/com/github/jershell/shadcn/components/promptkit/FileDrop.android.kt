package com.github.jershell.shadcn.components.promptkit

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.provider.OpenableColumns
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.*
import androidx.compose.ui.platform.LocalContext
import io.github.vinceglb.filekit.AndroidFile
import io.github.vinceglb.filekit.PlatformFile
import kotlinx.coroutines.*
import java.io.File
import java.util.UUID

private tailrec fun Context.dropActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.dropActivity()
    else -> null
}

@Composable
internal actual fun Modifier.promptFileDropTarget(enabled: Boolean, policy: FileUploadPolicy, onDragging: (Boolean) -> Unit,
    onFiles: (List<PlatformFile>) -> List<PlatformFile>, onError: (String) -> Unit): Modifier {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val latestEnabled by rememberUpdatedState(enabled)
    val latestPolicy by rememberUpdatedState(policy)
    val dragging by rememberUpdatedState(onDragging)
    val receive by rememberUpdatedState(onFiles)
    val failure by rememberUpdatedState(onError)
    val target = remember(context) {
        object : DragAndDropTarget {
            override fun onEntered(event: DragAndDropEvent) { if (latestEnabled) dragging(true) }
            override fun onExited(event: DragAndDropEvent) { dragging(false) }
            override fun onEnded(event: DragAndDropEvent) { dragging(false) }
            override fun onDrop(event: DragAndDropEvent): Boolean {
                dragging(false)
                if (!latestEnabled) return false
                val native = event.toAndroidDragEvent()
                val clip = native.clipData ?: return false
                val uris = (0 until clip.itemCount).mapNotNull { clip.getItemAt(it).uri }
                if (uris.isEmpty()) return false
                val permission = if (Build.VERSION.SDK_INT >= 24) context.dropActivity()
                    ?.requestDragAndDropPermissions(native) else null
                val selectedPolicy = latestPolicy
                val limit = if (selectedPolicy.multiple) selectedPolicy.maxFiles else 1
                val job = scope.launch {
                    val owned = mutableListOf<Pair<PlatformFile, File>>()
                    var delivered = emptyList<PlatformFile>()
                    try {
                        var rejected = uris.size > limit
                        withContext(Dispatchers.IO) {
                            for (uri in uris) {
                                if (owned.size >= limit) break
                                ensureActive()
                                var output: File? = null
                                try {
                                    var name = "attachment"
                                    var knownSize: Long? = null
                                    context.contentResolver.query(uri,
                                        arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                                        null, null, null)?.use { cursor ->
                                        if (cursor.moveToFirst()) {
                                            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                                            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                                            if (nameIndex >= 0 && !cursor.isNull(nameIndex)) name = cursor.getString(nameIndex)
                                            if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) knownSize = cursor.getLong(sizeIndex)
                                        }
                                    }
                                    val mime = context.contentResolver.getType(uri)
                                    if (!selectedPolicy.accepts(name, mime, knownSize ?: 0)) {
                                        rejected = true
                                        continue
                                    }
                                    val dir = File(context.cacheDir, "prompt-kit/${UUID.randomUUID()}")
                                    check(dir.mkdirs()) { "Cannot create attachment cache" }
                                    val destination = File(dir, File(name).name.takeUnless { it == "." || it == ".." || it.isBlank() } ?: "attachment")
                                    output = destination
                                    context.contentResolver.openInputStream(uri)?.use { input ->
                                        destination.outputStream().use { stream ->
                                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                                            var total = 0L
                                            while (true) {
                                                ensureActive()
                                                val count = input.read(buffer)
                                                if (count < 0) break
                                                check(count.toLong() <= selectedPolicy.maxFileSize - total) { "Dropped file exceeds the size limit" }
                                                stream.write(buffer, 0, count)
                                                total += count
                                            }
                                        }
                                    } ?: error("Cannot open dropped file")
                                    owned += PlatformFile(AndroidFile.FileWrapper(destination)) to destination
                                    output = null // ownership transferred to the batch
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (_: Exception) {
                                    rejected = true
                                } finally {
                                    output?.let { it.delete(); it.parentFile?.delete() }
                                }
                            }
                        }
                        if (latestEnabled) {
                            if (owned.isNotEmpty()) delivered = receive(owned.map { it.first })
                            if (rejected) failure("Some dropped files could not be read or exceeded the allowed type, size, or count.")
                        }
                    } finally {
                        owned.filter { it.first !in delivered }.forEach { (_, file) ->
                            file.delete(); file.parentFile?.delete()
                        }
                    }
                }
                job.invokeOnCompletion { if (Build.VERSION.SDK_INT >= 24) permission?.release() }
                return true
            }
        }
    }
    return dragAndDropTarget(shouldStartDragAndDrop = { latestEnabled }, target = target)
}
