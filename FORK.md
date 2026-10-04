# Fork maintenance notes

This fork starts from upstream `6ce110d5ff63230d4929ce1e7d2bcaad04a4b521` (1.0.2-dev). Compose Unstyled stays pinned to **2.9.0**. The changes repair integration and custom-component behavior; they do not replace the underlying dependency. The subsequent agent-interface extension is documented below.

## Behavior changes

- `ShadcnUI` uses `PortalHost` for non-modal content and lets Unstyled use Compose platform dialogs on native targets. Wasm uses a focus-isolated in-scene stack to contain a Compose Web semantics-owner disposal defect; see [Web modal containment](docs/WEB_MODAL_CONTAINMENT.md). Direct dialogs and the managed FIFO host share `DialogPanel`, including inside-click consumption and modal animation lifecycle. Initial control focus, an empty-panel fallback, focus isolation/restoration, nested dialogs and dismissal flags have regression tests. Side drawers use the same panel primitive; their close buttons and edge borders stay within the drawer surface. Bottom drawers explicitly focus their content and handle Escape/back.
- Dialog queue callbacks run once when exiting. Reentrant cancellation/clear calls cannot recursively invoke cancellation or discard a newly queued dialog.
- Submenus open with the forward horizontal arrow and close one level with the reverse arrow or Escape, returning focus to their trigger. Arrows mirror in RTL. Menu navigation includes submenu anchors, skips disabled items, wraps, and supports Home/End. Selecting a nested action closes its ancestors. A measured side selector preserves submenu flipping at window edges.
- Context menus support touch/stylus long-press as well as secondary mouse clicks. The opening release is consumed. Platform modals isolate focus, and the menu supports keyboard navigation and dismissal.
- Resize handles are focusable, show focus, expose adjustable progress, support arrows/Shift+arrows/Home/End and honor RTL. Initial splits satisfy asymmetric minimum sizes; drag handlers read updated constraints. Invalid minima fail with a clear error instead of failing inside a later drag. Changing minima resets the split.
- Range sliders expose a separately focusable, labeled progress control for each thumb. Accessibility and keyboard changes obey snapping, disabled state and the other thumb's bounds. Pointer callbacks stay current after recomposition; taps move the nearest thumb. Degenerate ranges remain finite.

## Integration

Use `ShadcnUI` as the application's theme/host. **Do not put an external Unstyled `ModalHost` around it**: doing so overrides the native platform modal path and bypasses its focus isolation. `ShadcnUI` selects the tested Web containment internally. `PortalHost` remains appropriate for non-modal portals. Dialogs and drawers provide a portal host within their layer for nested popovers/tooltips.

The source package names and upstream Maven coordinates are unchanged. No release of this fork has been published. Build/use this checkout as a source dependency; the upstream Maven artifact does not include these changes.

## Validation

The permanent regression tests are in `uikit/src/commonTest/kotlin/com/github/jershell/shadcn/interaction/`. They exercise actual Compose controls using key, touch and mouse injection and semantics actions, plus queue and placement tests. Existing theme/pagination tests remain enabled.

```sh
./gradlew :uikit:jvmTest :uikit:compileAndroidMain :uikit:compileKotlinWasmJs :demoApp:desktopApp:assemble --max-workers=2 --no-daemon --console=plain
```

Use JDK 21 with Java 17 bytecode targets. Android requires SDK platform 37 (`platforms;android-37.0` in sdkmanager). On Windows use `gradlew.bat`; this workspace uses Android Studio's JBR. CI runs JVM interaction tests and desktop demo compilation on Linux/Windows, Android/Wasm compilation on Linux, and iOS ARM64/simulator compilation on macOS, with JUnit artifacts retained.

Local verification on 2026-09-27 (Windows, JBR 21): **56 tests passed, zero failures/skips** (19 existing tests plus 37 added); Android and Wasm library compilation and the desktop demo assembly completed successfully. Existing AGP/SDK compatibility and deprecated-API warnings remain; dependencies were not upgraded as part of this fix.

These checks do not constitute physical-device, IME, screen-reader, browser-runtime or iOS interaction certification. The desktop demo is compiled, not manually launched. Keep the new tests when upgrading Compose or Unstyled.

## Still open

See `BACKLOG.md` for remaining work, including general Tooltip/DropdownMenu/Select/Calendar collision flipping, submenu hover intent, and other component features. This fork now embeds xterm.js as its terminal engine; a full code editor remains application-level work. No upstream contribution or package publication is performed automatically.

## Agent-interface extension

The user-requested extension adds the complete Prompt Kit component-family/export inventory, composition blocks, provider-neutral chat primitives, xterm.js platform hosts, metal-fx button, Soviet/Flume text themes, Departure Mono, and a shared motion policy. [UIREQ_IMPLEMENTATION](docs/UIREQ_IMPLEMENTATION.md) is the completion and validation record for this extension; the 56-test result above describes the earlier interaction-fix baseline only.

New dependencies are pinned centrally: Material-free Markdown renderer 0.44.0, Highlights 1.1.0, FileKit 0.15.0, and JCEF 146.0.10 on desktop. Markdown and FileKit are pinned to releases compatible with Compose 1.11.1; Coil remains 3.5.0 and Compose Unstyled remains 2.9.0. Native browser engines are platform-specific; desktop consumers choose a matching bundled JCEF artifact. No remote inference service or terminal shell is started by the SDK.

Source snapshots, inventories, source/theme/font licenses and rendering boundaries are retained under `imports/` and `docs/`. Runtime resources include required notices alongside the offline assets. React component files cannot be installed directly into Compose; the JSX preview is deliberately a native declarative renderer with documented supported bindings.

## Material removal

The demo-only HDCharts dependency and its integration are removed because the library requires Material. The unused ripple-indication dependency is also removed because its binaries reference legacy Material ripple APIs. Both the demo and SDK exclude Material artifacts brought in by Compose Desktop. The Android demo uses a minimal platform window theme; ShadcnUI owns its rendered surfaces.
