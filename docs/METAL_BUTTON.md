# Metal button

`MetalButton` ports the requested metal-fx liquid-metal button from [Jakubantalik/metal-fx](https://github.com/Jakubantalik/metal-fx/tree/be1bf89c63056521a4e8224f368768314c9006f7). The default preset is **Chromatic**, with the source's dark/light palette selected by the Compose theme. Silver and Gold are available too. The application surface and readable label use the library's theme tokens; the metallic artwork retains the source's palette values.

```kotlin
MetalButton("Upgrade to Pro", onClick = { upgrade() })

MetalButton(
    text = "Continue",
    onClick = { continueFlow() },
    preset = MetalPreset.Silver,
    paused = effectsPaused,
    active = screenVisible,
)
```

The slot overload also exposes `MetalVariant.Button`/`Circle`, glow suppression, shader scale, ring width, radius, and the source's master scale multiplier. `strength` controls artwork opacity, independently of text and interaction. Keyboard focus, Enter/Space, touch, current callbacks and disabled behavior come from `UnstyledButton`; the Canvas has no input handlers and cannot cover the control with an intercepting layer.

## What is preserved

The implementation evaluates the actual source mathematics: Ashima simplex noise, five-octave FBM, four sine waves, distortion warp, Gaussian-weighted five-stop palette, five-tap blur, gamma 1.3, edge vignette and the canonical 140-by-40 centered shader crop. All three preset pairs retain the original parameters. Rounded-rectangle/circle arc sampling controls the thin ring mask.

The wandering highlight samples 16 perimeter points, with the source luminance thresholds, three-second dwell, 1.5-second relocation fade, 1.5-second color-sampling cadence, wander smoothing, dark/light tint policy and four halo plus two catch-light layers. The inner hole masks out the glow; the external halo has the source's half-opacity mask. Paused and reduced-motion controls retain a visible static ring.

## Native rendering differences

This is a common Kotlin/Canvas implementation and adds no shader engine, browser, or platform dependency. The source renders a shared 96-pixel WebGL bitmap. We evaluate the same field at **64–512 points along the visible perimeter** and interpolate with native antialiased segments. The source SVG Gaussian blurs are approximated with 16 nested Gaussian stroke bands. CPU scalar math, native antialiasing, ring sampling, color selection at a hotspot rather than its neighboring pixel average, and the deterministic random seed mean this is **not a pixel-identical WebGL/SVG renderer**. The effect is a noise-driven liquid-metal ring, not a substituted generic gradient.

Optional reflections cast onto *neighboring DOM elements* are outside the requested single-button component and are not exposed. Hosts can supply any button content using the slot overload. The original CSS inset shadow is represented by the themed surface and subtle inset rim rather than an additional platform blur.

Rendering is capped at the source's **66 ms interval (about 15 fps)**, runs field computation on `Dispatchers.Default`, bounds sample counts, and stops its loop when disabled, paused, inactive, offscreen, zero-strength, or reduced-motion. It paints one initial static frame and refreshes it only when geometry/theme/preset changes. No repeating work occurs for static controls; disposal cancels the composition-owned job. This is intended for the requested small number of accented controls, not hundreds of simultaneous animated instances.

## Verification

`MetalMathTest` checks 18 numeric fixtures across three presets, two themes, and three positions/times against a separate JavaScript evaluation of the pinned shader, plus perimeter geometry, bounded sampling, and glow dwell. `MetalButtonInteractionTest` checks keyboard/touch callbacks and disabled interaction at zero strength. `DemoMetalButton` exercises palette changes, pause/resume, disabled state and reduced motion. Visual rasterization is an explicit approximation; numeric tests do not establish screenshot parity or physical-device performance.

The source snapshot and MIT notice are retained in `imports/metal-fx`.
