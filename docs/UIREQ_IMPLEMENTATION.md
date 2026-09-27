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
- [x] Build and test JVM, Android, Wasm, and iOS compile targets; record exact evidence and runtime boundaries.

## Terminal decision

[termcn](https://github.com/shadcn-labs/termcn) builds React interfaces that run *inside* terminals using Ink/OpenTUI. It is not a terminal emulator to embed in a mobile application. The user explicitly permits xterm.js; use its existing terminal emulation, bundled offline, with native WebView/WKWebView, browser iframe, and maintained JCEF hosts. Applications supply their own PTY/SSH/network transport. Creating a UI component must not start a shell or silently download a browser.

## Validation log

Completed and verified on **2026-09-27**. [CI run 36353633235](https://github.com/maceip/shadcn-multiplatform/actions/runs/36353633235) passed every job at commit `82347f3e963509d3e0f14884c6fb4858dece0fa5`.

| Check | Verified result |
| --- | --- |
| Source inventory | All 21 families, 59 exports, 10 blocks and 2 primitives accounted for. |
| JVM behavior and interaction tests | **153 tests passed on Windows and Linux**, zero failures, errors or skips. The same 153 tests passed locally on Windows/JBR 21. |
| Desktop demo | Full assembly passed on Windows and Linux. Local Windows assembly also passed. |
| Android | Library compilation and complete demo debug APK build passed. |
| Wasm | Complete browser development executable/distribution built; strict full-Chromium gallery integration passed. |
| iOS | Library and shared demo compiled for both ARM64 devices and ARM64 simulators on macOS. |
| Terminal engine | Standalone real-Chromium xterm scenario passed, including offline assets/font, keyboard input, ANSI behavior, sizing, disposal, protocol acknowledgements and reduced motion. |

The full Wasm test exercises all six new galleries at **1280px and 390px** widths. It checks live JSX callbacks, GFM task labels/checked/disabled state, repeated phone navigation, nested dialogs, bottom drawers, terminal keyboard input/ANSI/bell, resizing, visual canvas/iframe overlap, modal isolation, background output and parser acknowledgements while hidden, session preservation and disposal. No checks are bypassed; the final runtime-error/resource-error assertion passes. CI retains the twelve gallery screenshots, runnable Wasm distribution, and both JVM JUnit/HTML reports as artifacts.

Behavioral coverage includes input/submission guards, attachment validation, ordered/reentrant/cancelled chat streams, conversation scrolling, rendering and safe links, source/status interactions, text streaming, terminal backpressure/lifecycle, metal-effect numeric fixtures, reduced motion, and modal keyboard/semantics isolation. Testing also corrected phone gallery layout, GFM duplication and accessible task state, and Compose Web's modal semantics-owner disposal defect. See [Web modal containment](WEB_MODAL_CONTAINMENT.md) for the public-API workaround and its regression tests.

Baseline before this task was **56 passing JVM tests** at `6f42392`; the extension adds 97 tests. Native adaptations and rendering limits are documented in [Prompt Kit](PROMPT_KIT.md), [MetalButton](METAL_BUTTON.md), [terminal integration](TERMINAL.md), and [motion audit](MOTION_AUDIT.md).

### Runtime boundaries

- iOS device/simulator **compilation** and the Android APK build are verified. Physical-device behavior, mobile IMEs, native attachment permissions/drop interactions and VoiceOver/TalkBack have not been manually exercised.
- Desktop interaction tests and demo assembly are verified. The desktop app/JCEF terminal was not manually launched, following the repository's agent instructions.
- Browser automation checks concrete behavior and ARIA state; it is not screen-reader certification. The scoped Markdown adapter does not replace Compose Web's accessibility bridge for every existing control.
- JSX preview is the documented native declarative subset, not a JavaScript/React runtime. MetalButton is a Canvas adaptation of the source effect, not pixel-identical WebGL/SVG rendering. Soviet/Flume palettes are preserved; syntax classification does not reproduce Neovim's full Tree-sitter/LSP behavior.
- Glyph coverage depends on the bundled/platform fonts. The default Wasm demo font does not render every emoji; applications needing those glyphs must provide a suitable fallback font.
- Applications supply their own agent transport and PTY/SSH connection. The terminal component does not start a shell. No Maven release of this fork has been published; use the source checkout.

### Reproduce

```sh
python3 scripts/verify_prompt_port.py
./gradlew :uikit:jvmTest :demoApp:desktopApp:assemble :demoApp:androidApp:assembleDebug --max-workers=2 --no-daemon --console=plain '-Dorg.gradle.jvmargs=-Xmx3g' '-Pkotlin.daemon.jvmargs=-Xmx2g'
./gradlew :demoApp:webApp:wasmJsBrowserDevelopmentExecutableDistribution --max-workers=1 --no-parallel --no-daemon --console=plain '-Dorg.gradle.jvmargs=-Xmx5g' '-Pkotlin.compiler.execution.strategy=in-process'
cd uikit/src/jvmTest/terminal
npm ci
npx playwright install chromium
npm test
npm run test:gallery
```

Use JDK 21 (Java bytecode target 17) and Android SDK `platforms;android-37.0`; on Windows use `gradlew.bat`. The workflow records the macOS iOS compile commands and Linux Chromium system dependencies. The full Wasm link uses a dedicated 5GB compiler heap; compilation of the library alone does not validate the runnable demo.
