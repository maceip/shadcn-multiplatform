@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.github.jershell.shadcn.components.terminal

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.cinterop.readValue
import platform.CoreGraphics.CGRectZero
import platform.Foundation.NSError
import platform.Foundation.NSURL
import platform.WebKit.WKNavigation
import platform.WebKit.WKNavigationDelegateProtocol
import platform.WebKit.WKScriptMessage
import platform.WebKit.WKScriptMessageHandlerProtocol
import platform.WebKit.WKUserContentController
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.darwin.NSObject

internal actual object TerminalPlatform {
    actual suspend fun initialize(allowDownload: Boolean) = Unit
}

private class TerminalScriptHandler(private val onMessage: (String) -> Unit) : NSObject(), WKScriptMessageHandlerProtocol {
    override fun userContentController(userContentController: WKUserContentController, didReceiveScriptMessage: WKScriptMessage) {
        if (didReceiveScriptMessage.frameInfo.mainFrame) {
            (didReceiveScriptMessage.body as? String)?.let(onMessage)
        }
    }
}

@Suppress("CONFLICTING_OVERLOADS")
private class TerminalNavigationDelegate(private val onError: (String) -> Unit) : NSObject(), WKNavigationDelegateProtocol {
    @ObjCSignatureOverride
    override fun webView(webView: WKWebView, didFailProvisionalNavigation: WKNavigation?, withError: NSError) {
        onError(withError.localizedDescription)
    }

    @ObjCSignatureOverride
    override fun webView(webView: WKWebView, didFailNavigation: WKNavigation?, withError: NSError) {
        onError(withError.localizedDescription)
    }

    override fun webViewWebContentProcessDidTerminate(webView: WKWebView) {
        onError("The terminal's WebKit process terminated. Recreate the terminal view to continue.")
    }
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
    val currentOnError by rememberUpdatedState(onError)
    // WKWebView holds a weak navigation delegate; retain it for the view lifetime.
    val delegate = remember { TerminalNavigationDelegate { currentOnError(it) } }
    UIKitView(
        modifier = modifier,
        properties = UIKitInteropProperties(isNativeAccessibilityEnabled = true),
        factory = {
            val configuration = WKWebViewConfiguration()
            configuration.userContentController.addScriptMessageHandler(
                TerminalScriptHandler { currentOnMessage(it) }, name = "shadcnTerminal",
            )
            WKWebView(CGRectZero.readValue(), configuration).apply {
                navigationDelegate = delegate
                scrollView.scrollEnabled = false
                onAttach { script ->
                    evaluateJavaScript(script) { _, error ->
                        error?.let { currentOnError(it.localizedDescription) }
                    }
                }
                loadHTMLString(html, baseURL = NSURL(string = "https://terminal.invalid/"))
            }
        },
        onRelease = { webView ->
            webView.navigationDelegate = null
            webView.stopLoading()
            webView.configuration.userContentController.removeScriptMessageHandlerForName("shadcnTerminal")
            webView.loadHTMLString("", baseURL = null)
        },
    )
}
