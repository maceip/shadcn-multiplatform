# Pinned editor palettes

`sources.json` pins the original repositories. Their unmodified palette/highlight definitions,
licenses and the Soviet NOTICE are kept alongside this file. Run `python imports/editor-themes/generate.py`
from the repository root to regenerate `palettes.json` and `EditorPaletteData.kt`.
The script evaluates Flume's palette inheritance and checks every resolved color against its upstream
palette manifest before writing Kotlin. It does not use screenshot sampling or the Soviet README's
older illustrative swatches.

The adaptation adds native Compose role mappings, selectable text, syntax palettes and ANSI terminal
palettes in `EditorTextTheme.kt`. These Kotlin files are new derived implementations; the original Lua
files are unchanged. Both Soviet modes and all four Flume variants retain all named source colors.
Soviet keyword boldness, comment italics and distinct number/boolean colors are preserved. Flume's
default syntax style has no forced bold or italics.

The syntax lexer supplies keywords, strings, numeric literals, punctuation, annotations and comments.
It does not supply Neovim Tree-sitter/LSP semantic roles, so it cannot independently color every
function, property, type or plugin-specific highlight. Those exact source colors remain available
through `EditorTextTheme.palette` for a richer editor integration. Terminal colors follow the source
ANSI ordering and, for Flume, the upstream Ghostty exports (including its separate terminal foreground).

Soviet: Apache-2.0 (`soviet/LICENSE`, `soviet/NOTICE`). Flume: MIT (`flume/LICENSE`).
Corresponding license files are also shipped in the library's Compose resources.
