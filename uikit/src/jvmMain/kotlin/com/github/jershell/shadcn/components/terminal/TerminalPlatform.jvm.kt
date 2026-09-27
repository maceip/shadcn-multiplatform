package com.github.jershell.shadcn.components.terminal

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.friwi.jcefmaven.CefAppBuilder
import me.friwi.jcefmaven.CefBuildInfo
import me.friwi.jcefmaven.EnumPlatform
import me.friwi.jcefmaven.impl.step.check.CefInstallationChecker
import me.friwi.jcefmaven.impl.step.fetch.PackageClasspathStreamer
import org.cef.CefApp
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.browser.CefMessageRouter
import org.cef.callback.CefQueryCallback
import org.cef.handler.CefLoadHandler
import org.cef.handler.CefLoadHandlerAdapter
import org.cef.handler.CefMessageRouterHandlerAdapter
import org.cef.handler.CefRequestHandlerAdapter
import org.cef.network.CefRequest
import java.io.File
import java.util.Base64

internal actual object TerminalPlatform {
    private val mutex = Mutex()
    var app by mutableStateOf<CefApp?>(null)
        private set

    actual suspend fun initialize(allowDownload: Boolean) {
        mutex.withLock {
            if (app != null) return
            app = withContext(Dispatchers.IO) {
                val build = CefBuildInfo.fromClasspath()
                val platform = EnumPlatform.getCurrentPlatform()
                val installation = File(System.getProperty("user.home"), ".cache/shadcn-terminal/${build.releaseTag}/${platform.identifier}")
                if (!allowDownload && !CefInstallationChecker.checkInstallation(installation)) {
                    val bundled = PackageClasspathStreamer.streamNatives(build, platform)
                    check(bundled != null) {
                        "Desktop terminal engine is not bundled. Add the matching jcef-natives-${platform.identifier} runtime artifact, or call TerminalRuntime.initialize(allowDownload = true)."
                    }
                    bundled.close()
                }
                CefAppBuilder().apply {
                    setInstallDir(installation)
                    cefSettings.windowless_rendering_enabled = false
                }.build()
            }
        }
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
    val app = TerminalPlatform.app
    if (app == null) {
        BasicText("Initialize TerminalRuntime before opening the desktop terminal.", modifier)
        return
    }
    val currentOnMessage by rememberUpdatedState(onMessage)
    val currentOnError by rememberUpdatedState(onError)
    val client = remember(app) { app.createClient() }
    val router = remember(client) {
        CefMessageRouter.create(CefMessageRouter.CefMessageRouterConfig("shadcnTerminalQuery", "shadcnTerminalCancel")).apply {
            addHandler(object : CefMessageRouterHandlerAdapter() {
                override fun onQuery(
                    browser: CefBrowser, frame: CefFrame, queryId: Long, request: String,
                    persistent: Boolean, callback: CefQueryCallback,
                ): Boolean {
                    if (!frame.isMain) return false
                    currentOnMessage(request)
                    callback.success("")
                    return true
                }
            }, true)
            client.addMessageRouter(this)
        }
    }
    val browser = remember(client, html) {
        client.addRequestHandler(object : CefRequestHandlerAdapter() {
            override fun onBeforeBrowse(
                browser: CefBrowser, frame: CefFrame, request: CefRequest,
                userGesture: Boolean, isRedirect: Boolean,
            ): Boolean = !request.url.startsWith("data:text/html") && request.url != "about:blank"

            override fun onOpenURLFromTab(browser: CefBrowser, frame: CefFrame, targetUrl: String, userGesture: Boolean) = true
        })
        client.addLoadHandler(object : CefLoadHandlerAdapter() {
            override fun onLoadError(
                browser: CefBrowser, frame: CefFrame, errorCode: CefLoadHandler.ErrorCode,
                errorText: String, failedUrl: String,
            ) {
                if (frame.isMain && errorCode != CefLoadHandler.ErrorCode.ERR_ABORTED) currentOnError(errorText)
            }
        })
        val data = Base64.getEncoder().encodeToString(html.toByteArray(Charsets.UTF_8))
        client.createBrowser("data:text/html;charset=utf-8;base64,$data", false, false)
    }
    SwingPanel(
        modifier = modifier,
        factory = {
            onAttach { script -> browser.executeJavaScript(script, "about:blank", 0) }
            browser.uiComponent
        },
    )
    DisposableEffect(browser, router) {
        onDispose {
            browser.close(true)
            client.removeMessageRouter(router)
            router.dispose()
            client.dispose()
        }
    }
}
