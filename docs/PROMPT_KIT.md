# Agent chat components

The `com.github.jershell.shadcn.components.promptkit` package ports every public component family in [prompt-kit at de803759](https://github.com/ibelick/prompt-kit/tree/de80375967400aa0c6ebab9d3ba4f9258ab79fcc). The pinned inventory, source contracts, defects found during assessment, and MIT notice are in `imports/prompt-kit/`. `python scripts/verify_prompt_port.py --markdown` checks all 59 runtime exports, ten composition blocks, and both primitives and prints their implementation/gallery mapping.

Use `ShadcnUI` at the application root. The demo registry exposes **Agent chat**, **AI rendering**, **Agent status**, and **Terminal**. The chat gallery demonstrates all ten composition blocks and both primitives with a clearly labeled local transport.

## Connect a backend

```kotlin
val transport = remember {
    ChatTransport { request ->
        // Return your backend's cold Flow<ChatEvent>.
        backend.respond(request.messages)
    }
}
val chat = rememberChatSessionState(transport)
Chatbot(chat, Modifier.fillMaxSize())
// ToolCallingChatbot uses the same state and renders ToolUpdate events in message order.
```

The SDK is a UI client. Your application owns authentication, provider selection, conversation storage, attachments, tool execution/approvals, and network transport. Server-side Next.js/OpenAI examples become this injected transport; credentials are never part of UI components. A tool event must have a stable `toolCallId`; updates replace its existing part without moving surrounding text. Tool execution remains on the backend. The component does not execute model-supplied commands.

`ChatSessionState` allows one in-flight request. It supports cancellation, visible errors and retry without duplicating user messages. The composer keeps its draft until the first successful event. An error before that event leaves the draft intact. It also avoids clearing a newer draft typed while the response starts. `replaceMessages` cancels the old turn before switching history; `dispose` rejects new turns.

## Composition and platform adaptations

- `PromptInput` has both state and controlled string overloads. `PromptInputTextarea` supports multiline input, bounded autosize, Enter submission, Shift+Enter, and mobile IME Send. Active IME composition never submits. Empty, disabled, and loading submissions are rejected.
- `ChatContainerRoot` needs a bounded height/weight. Its content follows new output while at the bottom and preserves the reading position after upward scrolling. Use `ScrollButton` to resume following. This compound component uses Compose scrolling instead of the browser's `use-stick-to-bottom` package; very large persisted histories should use an application-level paginated/lazy presentation.
- `FileUpload` returns FileKit `PlatformFile` handles; the caller decides whether/how to upload. Native pickers work on all supported targets. External drops are scoped to the component and share type/count/size validation. Android/iOS drop providers have temporary read permissions, so their dropped files are copied to the application cache/temporary directory before returning. Count/type/known-size checks precede copying; Android additionally bounds streamed copies. Rejected, failed, disabled and cancelled copies are removed. The application owns accepted temporary attachments after receipt and should delete them when finished. Picker cancellation is not an error.
- `ResponseStream` accepts text or `Flow<String>`, with typewriter/fade, pause/resume/reset/restart and completion/error callbacks. Disposing or replacing a stream cancels it. Reduced-motion mode renders supplied text without decorative timing.
- `Markdown` uses the Material-free Mike Penz renderer and JetBrains GFM parser. It supports tables, task lists, strikethrough, code fences, links, images, preserved line breaks, and custom renderers. Source HTML remains inert text. Code highlighting uses Highlights with a plain-text fallback for unsupported languages. Kotlin `CodeBlockTheme` color roles replace browser Shiki theme names.
- Read-only GFM task markers keep the Shadcn checkbox visual on every target. Wasm supplies a transparent native HTML checkbox as the accessible node, including its formatted label, checked state and disabled state. This contains Compose Web 1.11.1's role/state serialization defect without modifying framework internals. Covered modals hide this HTML surface; native targets use ordinary Compose semantics. This scoped adaptation does not replace the accessibility bridge for every existing library control.
- `JSXPreview` is an explicitly **native declarative adaptation**, not a JavaScript/React runtime. It parses JSX strings into real Compose views, supports nested native elements, literal/bound attributes, named callback bindings, custom registered Compose components, streaming completion, and errors/fallbacks. Arbitrary JavaScript expressions, React hooks, DOM APIs, arbitrary CSS/Tailwind classes and imported React components require a web runtime and are not executed by this native renderer. All parsed attributes are available to custom component renderers. This boundary follows from porting the library to native Compose; do not expect a React component file to work without translation.
- Source previews support hover, touch and keyboard access. Unlike upstream, they do not contact a third-party favicon service merely because a source is displayed.
- Reusable blocks take data and callbacks instead of baking in sample conversations, console-only actions or artificial response timers. Optional search/voice/more buttons appear when the application supplies their behavior. `MessageComponent` supplies working copy and optional feedback actions.

## Fidelity and fixes

Public family/export names are retained. Browser-specific contexts become Compose state/locals; className/asChild styling becomes modifiers and content slots. The source's single-line fenced-code classification, trailing JSX text loss, stale highlighting, unguarded IME Enter, disabled upload bypass, unwired example buttons, and stream cancellation/completion issues are corrected rather than reproduced. See each component's KDoc for its complete Kotlin contract.

Neither reviewed React repository supplies an automated component test suite. This port adds Kotlin state tests, Compose interaction tests, and real xterm browser tests. `docs/UIREQ_IMPLEMENTATION.md` records build evidence and remaining runtime validation boundaries.
