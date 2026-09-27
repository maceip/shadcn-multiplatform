# Motion audit

The requested reference is [Transitions.dev](https://transitions.dev), inspected at
[`e2d5551656e4d3274e075d1cbd9a95af50f53225`](https://github.com/Jakubantalik/transitions.dev/tree/e2d5551656e4d3274e075d1cbd9a95af50f53225).
Relevant public references are `skills/transitions-dev/_root.css`, `01-card-resize.md`,
`05-menu-dropdown.md`, `06-modal.md`, `21-accordion.md`, and `25-checkbox-check.md`.
No third-party CSS/React recipes or complete transition collection are bundled.

The independently authored Compose implementation uses a conventional smooth-out curve
`(0.22, 1, 0.36, 1)`, 250 ms surface opening, 150 ms closing, 300 ms discrete container
resize, and 350/150 ms checkbox stroke draw/erase. Menu scale is 0.97 on entry and
0.99 on exit; dialog scale is 0.96. Existing working component motion is retained
where it already communicates the state change. No CSS/React implementation or recipe collection was translated or imported.

**License distinction:** the [site's terms](https://transitions.dev/terms.html) permit
using/modifying recipes within products, and restrict redistribution of the collection
or a substantial part as a competing library/component kit. MIT applies to its tooling,
not to the recipe collection. This repository contains its own targeted Compose code
and does not distribute that collection or claim its recipes are MIT.

## Accessibility and application control

`ShadcnUI(motionEnabled = null)` follows the platform setting; pass `false` to disable
SDK motion explicitly. A nested `ShadcnMotion(enabled = true)` cannot override an
outer disabled provider. `LocalShadcnMotionEnabled` is the shared primitive, also used
by Prompt Kit's compatibility alias.

- Android observes the animator-duration-scale setting.
- iOS observes Reduce Motion and changes to that preference.
- Wasm observes `prefers-reduced-motion` changes.
- Windows reads AWT's published menu-animation preference. AWT does not expose a
  uniform equivalent on other desktop systems; those applications must bridge their
  setting to `motionEnabled`, or expose an application preference.
- Decorative infinite loops are removed from composition when disabled, rather than
  merely giving them zero-duration repeating animations.
- Finite transitions snap to their destination. Hidden content is removed without
  an intentional exit delay. Terminal cursor blinking is disabled without rebuilding
  the terminal or losing its screen. Direct manipulation, text selection, terminal
  output, and code editing never receive decorative interpolation.

## Family coverage

| Families | Behavior |
| --- | --- |
| Button, MetalButton | Button state colors transition quietly. MetalButton owns its source-derived liquid-metal interaction and uses the shared preference. |
| Checkbox | The check path draws/erases; box fill transitions. Reduced motion snaps both. |
| Switch | Existing thumb spring retained; reduced motion uses a snap. |
| Toggle, ToggleGroup | Background and content colors transition over 150 ms. Selection semantics and callbacks remain immediate; reduced motion snaps the colors. |
| RadioGroup | The selection indicator fades/scales in and out over 150 ms; reduced motion removes the transition. Selection semantics and callbacks remain immediate. |
| DropdownMenu, Select, Calendar header menus | Origin-aware fade/scale supplied to Unstyled's panel lifecycle; both entry and exit are animated. Submenus use the measured side and RTL-aware origin. |
| ContextMenu, Menubar | Fade/scale on entry. Existing owner removes the menu immediately on close to preserve focus/dismissal and rapid hover-menu switching. |
| Popover, Combobox, Datepicker | Shared Popover content gets the origin-based entry reveal. Existing nonmodal dismissal stays immediate. Filter results update directly without per-keystroke animation. |
| Dialog | Independent Compose fade/scale entry/exit. Existing platform-modal focus isolation and restoration retained. Managed queue exit delay follows reduced motion. |
| Drawer | Existing slide/fade retained. Bottom-sheet settling uses the shared duration, and scrims/side drawers honor reduced motion. |
| Accordion, Collapsible | Height, fade and chevron transitions retained; shared reduction disables them. Accordion uses the smooth-out curve. |
| Tabs | Existing sliding indicator retained; initial layout and reduced motion snap to position. |
| Sidebar | Discrete expanded/collapsed width uses the shared 300 ms resize timing. |
| NavigationMenu | Existing chevron rotation is retained and reduced-motion aware. The custom anchored panel opens/closes immediately; it does not inherit Popover motion. |
| Carousel | Pager motion retained for navigation. Reduced motion snaps button navigation and mouse-drag settling; direct touch manipulation remains native. |
| Toast | Existing entry/exit/stack animation retained and reduced-motion aware; auto-dismiss timers still work independently of animation. |
| Progress | Existing progress interpolation retained; honors both `animate = false` and shared reduction. |
| Skeleton, Spinner | Existing pulse/rotation retained when enabled; a static accessible loading indicator when reduced. |
| Scrollbars | Existing presence fade honors reduction. Scrolling and thumb movement remain direct manipulation. |
| Slider, RangeSlider, Resizable | Values/positions stay immediate for pointer, keyboard, and assistive adjustment. Slider focus-ring color honors reduction. |
| Tooltip | Existing directional entry/exit retained with reduced-motion-aware durations. Hover delay remains an intentional interaction delay. |
| Prompt Kit Message | One-time appearance fade; streaming text updates do not animate layout. |
| Prompt Kit PromptInput | Shared discrete container-size helper; the application can disable motion for dense streaming views. |
| Prompt Kit ScrollButton, FileUpload overlay, agent/chat blocks | Shared presence helper for conditional controls/overlays. Scrolling, file drag/drop state and sending remain immediate functional operations. |
| Prompt Kit Reasoning, Tool, ChainOfThought | Disclosure motion follows the shared preference. |
| Prompt Kit Loader, Shimmer, TextShimmer, TextStream, ResponseStream | Existing component-specific motion toggles also follow the shared provider by default; streamed content and final text remain available without animation. |
| Prompt Kit code/Markdown/JSX/image/source rendering | Content rendering remains stable. Copy/action buttons inherit Button/Tooltip motion; no animated code reflow or syntax tokens. |
| Terminal | No output/reflow animation. Extra keys inherit Button motion. Cursor blink follows reduction; terminal modes and transport remain unchanged. |
| Alert, Attachment, Avatar, Badge, Breadcrumb, Card, Empty, Field, Icon, Input, Item, Kbd, Label, Pagination, Questionnaire, Separator, Table, Textarea, Typography | Static/layout/content primitives have no intrinsic show/hide state to animate. Their composed controls inherit the relevant behaviors above. App-owned conditional appearance can use `ShadcnVisibility`; discrete expansion can use `shadcnAnimateContentSize`. Text entry, table data, validation text and selection are never delayed for decoration. |

## Verification

`ShadcnMotionTest` verifies zero-duration animation specifications under reduction, nested-provider
inheritance, and removal of hidden interactive content without an exit animation.
Existing interaction regression tests still exercise dialogs, nested menus, sliders,
resizing and context menus with the default motion setting. The real xterm browser
test exercises control modes, buffer behavior, input, resize and disposal separately
from Compose styling.

Do not wrap the terminal/browser surface in Compose visibility animations that dispose
and recreate it while changing tabs. Keep the terminal mounted for persistent sessions.
