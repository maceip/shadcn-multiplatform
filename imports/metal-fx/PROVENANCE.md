# metal-fx reference snapshot

- Repository: https://github.com/Jakubantalik/metal-fx
- Commit: `be1bf89c63056521a4e8224f368768314c9006f7`
- License: MIT, Copyright (c) 2026 Jakub Antalik; retained in `LICENSE`.
- Source files retain original text. `glow-geometry.ts`, `renderer-core.ts`, and `renderer-loop.ts` use flattened filenames here.
- `reference-samples.mjs` is an independently executed JavaScript evaluation of the shader equations, using palettes read directly from the pinned `presets.ts`. Run `node imports/metal-fx/reference-samples.mjs` to regenerate `reference-samples.json`. These are numeric equation fixtures, not GPU-rendered screenshots.
- Native implementation: `components/metalbutton/MetalMath.kt`, `MetalGlow.kt`, and `MetalButton.kt`.
- Mapping and known rasterization differences: `docs/METAL_BUTTON.md`.
