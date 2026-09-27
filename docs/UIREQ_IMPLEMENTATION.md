# Agent UI implementation

Requirements: the user-provided `uireq.txt`. This document tracks implementation and verification; unchecked work is not complete.

## Source decision

Selected [prompt-kit](https://github.com/ibelick/prompt-kit/tree/de80375967400aa0c6ebab9d3ba4f9258ab79fcc), pinned at `de80375967400aa0c6ebab9d3ba4f9258ab79fcc` (MIT, Julien Thibeaut). Port scope includes all 21 registry component families, ten composition blocks, and the chatbot/tool-calling primitives. Next.js documentation, package distribution tooling, and a hosted OpenAI server are not SDK components. The primitives accept application-supplied transports instead of distributing credentials or requiring one provider.

Compared [inference-sh/shadcn-registry](https://github.com/inference-sh/shadcn-registry/tree/a02a8ea75e87f70a0a13fbe8c0646762309a0eb7), revision `a02a8ea75e87f70a0a13fbe8c0646762309a0eb7`. Its core chat, uploads, tools, and task widgets consume Inference SDK contexts and backend-specific result structures. Prompt-kit's presentation/state boundary translates more directly to Compose, with less backend code to reproduce. Neither upstream checkout has an automated component test suite; this decision does not imply either is proven stable. Our port needs its own behavioral regression coverage.

Inference's public Git has a 20-node widget renderer. Its live `https://inference.sh/ui/r/widgets.json`, inspected 2026-09-27, instead imports `A2UIRenderer` and generated types without distributing those files or registering their dependencies. Payload SHA256: `c4f684e9ba50f1322354498a5ab7f7a2949a7ca01a8d9534197cc70c681f5cbc`. Its README claims MIT, but the checkout lacks a standalone license file. Prompt-kit includes its license. These are additional reasons to choose prompt-kit, beyond component count.

## Requirements and completion

- [x] Inspect and pin both source repositories; choose a backend-neutral source.
- [x] Port all 21 component families with source-to-Compose inventory.
- [x] Port all ten composition blocks and both provider-neutral primitives.
- [x] Add interactive gallery entries and regression coverage.
- [x] Implement xterm.js terminal hosts for Android, iOS, desktop, and Wasm with input, resize, lifecycle and mobile extra keys.
- [x] Implement one metal-fx button, after core components.
- [x] Implement Soviet and Flume text/code themes, after core components.
- [x] Bundle Departure Mono with its license, after core components.
- [x] Audit transitions; apply targeted native motion using the public transitions.dev reference where missing, with reduced-motion support.
- [ ] Build and test JVM, Android, Wasm, and iOS compile targets; record exact evidence and runtime boundaries.

## Terminal decision

[termcn](https://github.com/shadcn-labs/termcn) builds React interfaces that run *inside* terminals using Ink/OpenTUI. It is not a terminal emulator to embed in a mobile application. The user explicitly permits xterm.js; use its existing terminal emulation, bundled offline, with native WebView/WKWebView, browser iframe, and maintained JCEF hosts. Applications supply their own PTY/SSH/network transport. Creating a UI component must not start a shell or silently download a browser.

## Validation log

Implementation complete; final cross-platform verification is in progress. Native adaptations and rendering limits are documented in [Prompt Kit](PROMPT_KIT.md), [MetalButton](METAL_BUTTON.md), [terminal integration](TERMINAL.md), and [motion audit](MOTION_AUDIT.md).

The static inventory check passes for all 21 families, 59 exports, 10 blocks, and 2 primitives. The real Chromium/xterm scenario passes 18 assertions plus protocol waits, including offline font loading and reduced-motion cursor behavior. Initial JVM run: 140 of 142 passed; a JSX object-key parser bug and placeholder-sensitive draft assertion were fixed, with an extra parser regression added. Final results will replace this interim record.

Baseline before this task: 56 passing JVM tests; JVM/Android/Wasm builds and iOS device/simulator compiles passed in CI at commit `6f42392`.
