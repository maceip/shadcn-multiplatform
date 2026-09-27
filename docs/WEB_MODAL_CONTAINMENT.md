# Web modal accessibility containment

Compose UI 1.11.1's published `ui-wasm-js` source has a modal accessibility lifecycle defect. `webMain/androidx/compose/ui/platform/accessibility/ComposeWebSemanticsListener.kt` stores one `semanticsOwner`. Appending a dialog owner replaces the page owner; removing that owner sets the field to `null`. `syncSemanticsWithWebA11Y()` then returns without restoring the page. `CanvasLayersComposeScene` sends append/remove notifications but does not re-append the underlying owner. This leaves the visible application without its accessible controls after dismissing a platform dialog.

Primary source: [published Compose UI 1.11.1 Wasm source archive](https://repo.maven.apache.org/maven2/org/jetbrains/compose/ui/ui-wasm-js/1.11.1/ui-wasm-js-1.11.1-sources.jar). The relevant listener is at lines 125–155; the scene notifications are in `skikoMain/androidx/compose/ui/scene/CanvasLayersComposeScene.skiko.kt`. [Compose Unstyled 2.9.0](https://github.com/composablehorizons/compose-unstyled/tree/444494ddfadb0db2f634c14ec00c668283bcb89d) delegates its non-Android platform modal to `androidx.compose.ui.window.Dialog`, exposing this upstream behavior.

## Containment

`ShadcnUI` selects `CanvasModalHost` on Wasm only. Dialog, managed dialogs, side drawers and bottom drawers register in this stack, preserving their caller's composition locals and their existing Unstyled transitions. Each modal uses Unstyled's public in-scene `ModalHost`; no additional ComposeScene owner is created. Native targets keep the existing platform dialog implementation.

Only the top modal exposes semantics. Covered layers use `clearAndSetSemantics` (the affected Web renderer does not implement `hideFromAccessibility`) and deny focus entry. Tab and Shift+Tab remain in the active modal; focus is saved when covering a layer and restored after uncovering it. Nested layers restore their immediate parent, then the page. Modal content disposal releases the layer after exit motion, and removal of a composable releases its registration immediately.

Owned focusable panels save their focused child before another modal is stacked, allowing the outer frame's recursive restoration to reach the exact launcher. This happens only when stacking a modal, preserving normal forward/backward Tab wrapping. An initial hidden composition retains Unstyled's entrance animation. A pointer barrier prevents outside taps from reaching covered content, including non-dismissible dialogs.

`LocalShadcnModalLayerBlocked` reports covered root/modal layers to HTML interop. The Wasm terminal hides and inerts its iframe while covered, without destroying its engine, controller or output. An iframe inside the active modal remains available. Read-only Markdown task checkboxes apply the same containment to their HTML accessibility surface.

## Validation and removal

`CanvasModalIntegrationTest` exercises nested semantics/focus restoration, forward/backward traversal, repeat opening, both drawer forms, streaming content, owner disposal, initial hidden state and non-dismissible pointer isolation using the same shared implementation on JVM. The full Wasm browser gallery additionally verifies real DOM semantics after Escape, nested dialogs, bottom-drawer dismissal and terminal interop. These tests must remain enabled when changing this containment.

Remove the Wasm adaptation only after a released Compose version restores the owner stack and all browser regressions pass with platform dialogs. The implementation uses public APIs; it does not patch private fields or generated JavaScript symbols.
