/* shadcn-multiplatform terminal adapter. xterm performs all terminal emulation. */
(function () {
  "use strict";
  var terminal, fitAddon, observer, disposed = false, subscriptions = [];

  function emit(event) {
    var message = JSON.stringify(event);
    if (window.AndroidTerminal) window.AndroidTerminal.postMessage(message);
    else if (window.webkit && window.webkit.messageHandlers.shadcnTerminal)
      window.webkit.messageHandlers.shadcnTerminal.postMessage(message);
    else if (window.shadcnTerminalQuery)
      window.shadcnTerminalQuery({ request: message, persistent: false });
    else if (window.parent !== window) window.parent.postMessage(message, "*");
    else window.dispatchEvent(new CustomEvent("shadcn-terminal-event", { detail: event }));
  }

  function fit() {
    if (disposed || !terminal.element || !terminal.element.isConnected) return;
    if (terminal.element.parentElement.clientWidth > 0 && terminal.element.parentElement.clientHeight > 0)
      fitAddon.fit();
  }

  function dispatch(command) {
    if (disposed || !command || typeof command.type !== "string") return;
    switch (command.type) {
      case "write": terminal.write(command.data || "", function () { emit({ type: "ack", id: command.id }); }); break;
      case "clear": terminal.clear(); break;
      case "reset": terminal.reset(); break;
      case "focus": terminal.focus(); break;
      case "theme": terminal.options.theme = command.theme; break;
      case "cursorBlink": terminal.options.cursorBlink = command.data === "true"; break;
      case "fit": fit(); break;
      case "paste": terminal.paste(command.data || ""); break;
      case "input": terminal.input(command.data || "", true); break;
      case "key":
        var keys = {Escape:"\x1b",Tab:"\t",Up:"A",Down:"B",Right:"C",Left:"D",Home:"H",End:"F",
          PageUp:"\x1b[5~",PageDown:"\x1b[6~",Delete:"\x1b[3~",Backspace:"\x7f",Enter:"\r",
          Interrupt:"\x03",EndOfFile:"\x04",Suspend:"\x1a"};
        var input = keys[command.data];
        if (/^(Up|Down|Left|Right|Home|End)$/.test(command.data))
          input = "\x1b" + (terminal.modes.applicationCursorKeysMode ? "O" : "[") + input;
        if (input !== undefined) terminal.input(input, true);
        break;
      case "resize":
        if (Number.isInteger(command.columns) && command.columns > 0 &&
            Number.isInteger(command.rows) && command.rows > 0)
          terminal.resize(command.columns, command.rows);
        break;
      case "dispose": dispose(); break;
    }
  }

  function receive(event) {
    // Only the hosting frame may send commands; terminal output is never interpreted as HTML/JS.
    if (event.source !== window.parent || event.source === window) return;
    try { dispatch(JSON.parse(event.data)); } catch (_) { /* Ignore unrelated host messages. */ }
  }

  function dispose() {
    if (disposed) return;
    disposed = true;
    if (observer) observer.disconnect();
    window.removeEventListener("message", receive);
    window.removeEventListener("pagehide", dispose);
    subscriptions.forEach(function (subscription) { subscription.dispose(); });
    if (terminal) terminal.dispose();
  }

  try {
    var options = JSON.parse(document.getElementById("terminal-options").textContent);
    terminal = new Terminal(options);
    fitAddon = new FitAddon.FitAddon();
    terminal.loadAddon(fitAddon);
    // OSC 8 URLs are application data. Do not let terminal output navigate its privileged host.
    terminal.options.linkHandler = { activate: function () {} };
    terminal.open(document.getElementById("terminal"));
    subscriptions.push(terminal.onData(function (data) { emit({ type: "input", data: data }); }));
    subscriptions.push(terminal.onBinary(function (data) { emit({ type: "binary", data: data }); }));
    subscriptions.push(terminal.onResize(function (size) {
      emit({ type: "resize", columns: size.cols, rows: size.rows });
    }));
    subscriptions.push(terminal.onTitleChange(function (title) { emit({ type: "title", data: title }); }));
    subscriptions.push(terminal.onBell(function () { emit({ type: "bell" }); }));
    window.shadcnTerminal = Object.freeze({ dispatch: dispatch, dispose: dispose });
    window.addEventListener("message", receive);
    window.addEventListener("pagehide", dispose);
    observer = new ResizeObserver(fit);
    observer.observe(document.getElementById("terminal"));
    fit();
    if (document.fonts && document.fonts.ready) document.fonts.ready.then(fit);
    emit({ type: "ready", columns: terminal.cols, rows: terminal.rows });
  } catch (error) {
    emit({ type: "error", data: String(error) });
    dispose();
  }
})();
