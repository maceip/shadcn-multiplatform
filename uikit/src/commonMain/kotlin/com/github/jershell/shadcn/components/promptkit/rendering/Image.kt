package com.github.jershell.shadcn.components.promptkit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import com.composeunstyled.theme.Theme
import com.github.jershell.shadcn.theme.BaseTokens
import com.github.jershell.shadcn.theme.ColorProps
import com.github.jershell.shadcn.theme.ColorTokens
import com.github.jershell.shadcn.theme.DimProps
import com.github.jershell.shadcn.theme.DimTokens
import kotlin.io.encoding.Base64

/** Generated images can arrive as base64 or raw bytes. Base64 takes precedence, matching prompt-kit. */
@Composable
fun Image(
    alt: String,
    modifier: Modifier = Modifier,
    base64: String? = null,
    uint8Array: ByteArray? = null,
    mediaType: String = "image/png",
    alignment: Alignment = Alignment.Center,
    contentScale: ContentScale = ContentScale.Fit,
    alpha: Float = 1f,
    colorFilter: ColorFilter? = null,
    filterQuality: FilterQuality = FilterQuality.Low,
    onLoading: ((AsyncImagePainter.State.Loading) -> Unit)? = null,
    onSuccess: ((AsyncImagePainter.State.Success) -> Unit)? = null,
    onError: ((Throwable) -> Unit)? = null,
    placeholder: @Composable BoxScope.() -> Unit = {},
) {
    val decoded = remember(base64, uint8Array, mediaType) {
        runCatching { decodeGeneratedImage(base64, uint8Array, mediaType) }
    }
    val shape = RoundedCornerShape(Theme[DimProps][DimTokens.radiusMd])
    val error = decoded.exceptionOrNull()
    LaunchedEffect(error) { if (error != null) onError?.invoke(error) }
    val bytes = decoded.getOrNull()
    if (bytes == null) {
        Box(modifier.clip(shape).background(Theme[ColorProps][ColorTokens.muted])
            .defaultMinSize(BaseTokens.token24, BaseTokens.token24)
            .semantics { role = Role.Image; contentDescription = alt }, content = placeholder)
    } else {
        AsyncImage(model = bytes, contentDescription = alt, modifier = modifier.clip(shape),
            alignment = alignment, contentScale = contentScale, alpha = alpha,
            colorFilter = colorFilter, filterQuality = filterQuality,
            onLoading = onLoading, onSuccess = onSuccess,
            onError = { onError?.invoke(it.result.throwable) })
    }
}

internal fun decodeGeneratedImage(base64: String?, bytes: ByteArray?, mediaType: String): ByteArray? {
    require(mediaType.matches(Regex("image/[A-Za-z0-9.+-]+"))) { "Expected an image MIME type" }
    return if (!base64.isNullOrEmpty()) Base64.Default.decode(base64) else bytes?.takeIf { it.isNotEmpty() }
}
