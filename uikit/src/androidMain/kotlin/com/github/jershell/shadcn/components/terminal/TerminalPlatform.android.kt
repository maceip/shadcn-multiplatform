package com.github.jershell.shadcn.components.terminal

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

internal actual object TerminalPlatform {
    actual suspend fun initialize(allowDownload: Boolean) = Unit
}

private class TerminalJavaScriptBridge(private val onMessage: (String) -> Unit) {
    @JavascriptInterface fun postMessage(message: String) = onMessage(message)
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
internal actual fun TerminalPlatformView(
    html: String,
    modifier: Modifier,
    onAttach: ((String) -> Unit) -> Unit,
    onMessage: (String) -> Unit,
    onError: (String) -> Unit,
) {
    val currentOnMessage by rememberUpdatedState(onMessage)
    val currentOnError by rememberUpdatedState(onError)
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.domStorageEnabled = false
                settings.setSupportMultipleWindows(false)
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = true
                    @Deprecated("Used by older WebView engines")
                    override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean = true
                    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                        if (request.isForMainFrame) currentOnError(error.description.toString())
                    }
                }
                addJavascriptInterface(TerminalJavaScriptBridge { currentOnMessage(it) }, "AndroidTerminal")
                onAttach { script -> post { evaluateJavascript(script, null) } }
                loadDataWithBaseURL("https://terminal.invalid/", html, "text/html", "UTF-8", null)
            }
        },
        onRelease = { webView ->
            webView.stopLoading()
            webView.removeJavascriptInterface("AndroidTerminal")
            webView.loadUrl("about:blank")
            webView.destroy()
        },
    )
}
