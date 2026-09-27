# Embedded terminal

`com.github.jershell.shadcn.components.terminal` embeds pinned xterm.js 6.0.0 and addon-fit 0.11.0. JavaScript, CSS, licenses and integrity metadata are bundled under `uikit/src/commonMain/composeResources/files/terminal/`; no CDN is used. This is an emulator/view. Your application supplies a PTY, SSH or WebSocket transport.

```kotlin
val controller = rememberTerminalController()
val scope = rememberCoroutineScope()
// Before showing a desktop terminal, explicitly initialize the bundled engine:
LaunchedEffect(Unit) { TerminalRuntime.initialize() }

Terminal(
    controller = controller,
    modifier = Modifier.fillMaxSize(),
    onInput = { data -> scope.launch { connection.sendInput(data) } },
    onResize = { size -> scope.launch { connection.resize(size.columns, size.rows) } },
    onError = { message -> showConnectionError(message) },
)
LaunchedEffect(connection) {
    connection.decodedOutput.collect { controller.write(it) }
}
// Optional on mobile:
TerminalExtraKeys(controller)
```

In production, show a loading/error state around initialization and mount the view after it succeeds; `DemoTerminal` demonstrates this. `initialize()` is a no-op on Android/iOS/browser, which already supply their web engine. The desktop initializer extracts the bundled engine on a background dispatcher. It refuses a network download unless the caller explicitly passes `allowDownload = true`.

`write` suspends when the bounded command queue fills. The bridge waits for xterm's parser acknowledgement, preventing unlimited native-to-JavaScript accumulation. `tryWrite` returns false when full/disposed; keep and retry that output instead of dropping it. Feed a streaming UTF-8 decoder from byte transports so multibyte characters split across packets remain intact. The view emits binary input separately if your transport needs it.

`paste` goes through xterm's bracketed-paste handling. `sendKey` and `sendControl` expose extra keyboard commands without implementing a second terminal emulator. `clear`, `reset`, `fit`, `resize`, `focus` and `dispose` are explicit controller actions. Preserve a single controller per mounted terminal. Unmounting destroys its screen; a retained controller buffers new commands but does not replay already rendered output. Applications restore remote sessions/transcripts as needed.

## Hosts and desktop packaging

| Target | Host |
| --- | --- |
| Android | Platform WebView |
| iOS | WKWebView |
| Wasm | Isolated iframe inside Compose's HTML interop |
| Desktop JVM | JCEF through maintained `me.friwi:jcefmaven:146.0.10`, embedded with SwingPanel |

The desktop demo bundles the current OS/architecture engine. SDK consumers add one matching runtime artifact; the large browser binary is not forced into Android, iOS or Wasm applications:

```kotlin
runtimeOnly("me.friwi:jcef-natives-windows-amd64:jcef-d3de827+cef-146.0.10+g8219561+chromium-146.0.7680.179")
```

Use `windows-arm64`, `linux-amd64`, `linux-arm64`, `macosx-amd64`, or `macosx-arm64` as appropriate. JCEF's Windows ARM64 build requires native-window rendering, which this host uses. The repository version catalog owns the pinned version. Desktop packaging includes the Java desktop/management/unsupported/security-auth modules. macOS needs `--add-opens` for `java.desktop/sun.awt`, `java.desktop/sun.lwawt`, and `java.desktop/sun.lwawt.macosx`, each to `ALL-UNNAMED`; see the desktop demo Gradle setup. The process owns the shared engine until exit.

These embedded native/browser surfaces have platform interop/overlay restrictions. Place transient Compose popups outside the terminal bounds or use a layout that avoids overlapping them. Do not assume a native WebView and a Compose Canvas layer have identical stacking, selection or accessibility behavior. `screenReaderMode` is available through `TerminalOptions`.

Soviet/Flume palettes expose `terminalColors()` with all sixteen ANSI colors. Departure Mono can be embedded by selecting its font family in `TerminalOptions`; the font is included offline under its own OFL license. Theme changes update xterm; changing structural options recreates the view and its screen.

## Verification

Run `npm ci`, `npx playwright install chromium`, then `npm test` in `uikit/src/jvmTest/terminal`. This runs the vendored engine in a real browser, checking ANSI/cursor handling, alternate screen, Unicode, control/application keys, bracketed paste, resize, title/bell events, injection isolation, zero network requests and disposal. Common Kotlin tests cover controller ordering, backpressure and lifecycle. These tests do not replace physical-device keyboard/WebView checks or native JCEF runtime checks. Build evidence and those boundaries are recorded in `docs/UIREQ_IMPLEMENTATION.md`.
