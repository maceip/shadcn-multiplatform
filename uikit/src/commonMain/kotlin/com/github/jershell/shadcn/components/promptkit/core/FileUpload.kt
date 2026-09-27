package com.github.jershell.shadcn.components.promptkit

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import com.composeunstyled.theme.Theme
import com.github.jershell.shadcn.components.button.*
import com.github.jershell.shadcn.theme.*
import com.github.jershell.shadcn.motion.ShadcnVisibility
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.mimeType
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.size
import io.github.vinceglb.filekit.dialogs.FileKitMode
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher

/** Picker hints and validation apply equally to picker results, external drops, and programmatic input. */
@Immutable
data class FileUploadPolicy(
    val multiple: Boolean = true,
    val accept: String = "",
    val maxFileSize: Long = Long.MAX_VALUE,
    val maxFiles: Int = Int.MAX_VALUE,
) {
    init { require(maxFileSize >= 0); require(maxFiles > 0) }
    fun accepts(name: String, mimeType: String?, size: Long): Boolean {
        if (size < 0 || size > maxFileSize) return false
        val filters = accept.split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        if (filters.isEmpty()) return true
        return filters.any { filter ->
            when {
                filter == "*/*" -> true
                filter.startsWith('.') -> name.endsWith(filter, ignoreCase = true)
                filter.endsWith("/*") -> mimeType?.startsWith(filter.dropLast(1), ignoreCase = true) == true
                else -> mimeType.equals(filter, ignoreCase = true)
            }
        }
    }
}

@Stable
class FileUploadState {
    var isDragging by mutableStateOf(false)
        internal set
    var lastError by mutableStateOf<String?>(null)
        internal set
}

@Composable fun rememberFileUploadState(): FileUploadState = remember { FileUploadState() }

private data class UploadContext(val state: FileUploadState, val enabled: Boolean, val launch: () -> Unit)
private val LocalFileUpload = staticCompositionLocalOf<UploadContext?> { null }

/** Native file picker plus a scoped drop zone. Files are returned lazily; no network upload is implicit. */
@Composable
fun FileUpload(
    onFilesAdded: (List<PlatformFile>) -> Unit,
    modifier: Modifier = Modifier,
    state: FileUploadState = rememberFileUploadState(),
    policy: FileUploadPolicy = FileUploadPolicy(),
    disabled: Boolean = false,
    onError: (String) -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    val latestEnabled by rememberUpdatedState(!disabled)
    val latestPolicy by rememberUpdatedState(policy)
    val latestResult by rememberUpdatedState(onFilesAdded)
    val latestError by rememberUpdatedState(onError)
    // Return delivered handles so native drop adapters can discard temporary copies that were rejected.
    val receive: (List<PlatformFile>) -> List<PlatformFile> = remember(state) { { files ->
        var delivered = emptyList<PlatformFile>()
        if (latestEnabled) {
            val limit = if (latestPolicy.multiple) latestPolicy.maxFiles else 1
            val checked = runCatching {
                files.filter { latestPolicy.accepts(it.name, it.mimeType()?.toString(), it.size()) }
            }
            checked.fold(onSuccess = { accepted ->
                if (accepted.size != files.size || accepted.size > limit) {
                    state.lastError = "Some files did not match the allowed type, size, or count."
                    latestError(state.lastError!!)
                } else state.lastError = null
                if (accepted.isNotEmpty()) {
                    delivered = accepted.take(limit)
                    latestResult(delivered)
                }
            }, onFailure = {
                state.lastError = it.message ?: "Could not read selected files."
                latestError(state.lastError!!)
            })
        }
        delivered
    } }
    val filters = policy.accept.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    val extensionFilters = filters.filter { it.startsWith('.') }
    val pickerType = when (policy.accept.trim()) {
        "image/*" -> FileKitType.Image
        "video/*" -> FileKitType.Video
        else -> FileKitType.File(extensionFilters.map { it.removePrefix(".") }.toSet()
            .takeIf { it.isNotEmpty() && extensionFilters.size == filters.size })
    }
    val pickerError: (io.github.vinceglb.filekit.dialogs.FileKitPickerException) -> Unit = {
        state.lastError = it.message ?: "Could not open the file picker."
        latestError(state.lastError!!)
    }
    val multiPicker = rememberFilePickerLauncher(type = pickerType, mode = FileKitMode.Multiple(),
        onError = pickerError, onResult = { if (it != null) receive(it) })
    val singlePicker = rememberFilePickerLauncher(type = pickerType,
        onError = pickerError, onResult = { if (it != null) receive(listOf(it)) })
    val dropModifier = Modifier.promptFileDropTarget(!disabled, policy, { state.isDragging = it }, receive) {
        state.lastError = it
        latestError(it)
    }
    LaunchedEffect(disabled) { if (disabled) state.isDragging = false }
    CompositionLocalProvider(LocalFileUpload provides UploadContext(state, !disabled) {
        if (latestEnabled) {
            if (latestPolicy.multiple) multiPicker.launch() else singlePicker.launch()
        }
    }) {
        Box(modifier.then(dropModifier), content = content)
    }
}

@Composable
fun FileUploadTrigger(modifier: Modifier = Modifier, label: String = "Attach files",
    content: @Composable RowScope.() -> Unit = { ButtonText(label) }) {
    val ctx = checkNotNull(LocalFileUpload.current) { "Place FileUploadTrigger inside FileUpload." }
    Button(ctx.launch, modifier.semantics { contentDescription = label }, enabled = ctx.enabled,
        variant = ButtonVariant.Outline, size = ButtonSize.Sm, content = content)
}

@Composable
fun FileUploadContent(modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit = {
        BasicText("Drop files here", style = TypographyStyles.textSmMedium.copy(color = resolvePromptKitColors().foreground))
    }) {
    val ctx = checkNotNull(LocalFileUpload.current) { "Place FileUploadContent inside FileUpload." }
    val colors = resolvePromptKitColors()
    val shape = RoundedCornerShape(Theme[DimProps][DimTokens.radiusLg])
    ShadcnVisibility(ctx.enabled && ctx.state.isDragging, modifier) {
        Box(Modifier.fillMaxSize().background(colors.background.copy(alpha = .9f), shape)
            .border(Theme[DimProps][DimTokens.borderWidth], colors.primary, shape)
            .semantics { liveRegion = LiveRegionMode.Polite; contentDescription = "Drop files here" },
            contentAlignment = Alignment.Center, content = content)
    }
}

@Composable
internal expect fun Modifier.promptFileDropTarget(
    enabled: Boolean, policy: FileUploadPolicy, onDragging: (Boolean) -> Unit,
    onFiles: (List<PlatformFile>) -> List<PlatformFile>,
    onError: (String) -> Unit,
): Modifier
