"""Regenerate the exact Kotlin palette snapshot from the pinned upstream Lua inputs.

No Lua runtime is required: only the constant tables and the specific constant aliases used by
these two pinned sources are evaluated. Fail closed if the upstream table format changes.
Run from any directory: python imports/editor-themes/generate.py
"""
import json
import re
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent.parent


def colors(text):
    return dict(re.findall(r'(\w+)\s*=\s*"(#[0-9a-fA-F]{6})"', text))


def between(text, start, end):
    return text.split(start, 1)[1].split(end, 1)[0]


soviet_source = (HERE / "soviet/lua/soviet/palette.lua").read_text(encoding="utf-8")
light = colors(between(soviet_source, 'if style == "light" then', "}, overrides or {})"))
dark = colors(between(soviet_source, "  end\n", "}, overrides or {})"))
assert len(light) == len(dark) == 28, "Unexpected Soviet palette format"
palettes = {"sovietDark": dark, "sovietLight": light}

flume_source = (HERE / "flume/lua/flume/palette.lua").read_text(encoding="utf-8")
dusk = colors(between(flume_source, "M.dusk = {", "\n}"))
base_light = colors(between(flume_source, "local function make_light_palette", "\nend"))
base_light.update(bg="#f2eff7", terminal_bg="#f2eff7", element="#f2eff7", active_line="#ebe6f0",
                  surface="#ebe6f0", element_hover="#ebe6f0", surface_alt="#ddd6e3", element_active="#ddd6e3")
opal = base_light | colors(between(flume_source, "local opal_overrides = {", "\n}"))
aliases = dict(re.findall(r"colors\.(\w+) = colors\.(\w+)", between(flume_source, "local function complete_palette", "\nend")))
flume = {"Dusk": dusk, "Opal": opal}
for name in ["Mira", "Mesa"]:
    palette = colors(between(flume_source, f"M.{name.lower()} = complete_palette({{", "\n})"))
    for target, source in aliases.items():
        palette[target] = palette[source]
    flume[name] = palette

# Independently check every resolved Lua role against upstream's generated manifest.
manifest = (HERE / "flume/docs/palette-manifest.md").read_text(encoding="utf-8")
rows = re.findall(r"\| `(\w+)` \| `(#\w+)` \| `(#\w+)` \| `(#\w+)` \| `(#\w+)` \|", manifest)
for name, palette in flume.items():
    column = ["Dusk", "Opal", "Mira", "Mesa"].index(name) + 1
    expected = {row[0]: row[column] for row in rows}
    assert palette == expected, f"{name} Lua and manifest differ"
    palettes[f"flume{name}"] = palette

(HERE / "palettes.json").write_text(json.dumps(palettes, indent=2) + "\n", encoding="utf-8")
out = ["// Exact palette snapshot. Regenerate with imports/editor-themes/generate.py.",
       "// Upstream licenses and modification provenance are under imports/editor-themes.",
       "package com.github.jershell.shadcn.theme", "", "import androidx.compose.ui.graphics.Color", "",
       "internal object EditorPaletteData {"]
for name, palette in palettes.items():
    out.append(f"    val {name}: Map<String, Color> = mapOf(")
    for key, value in sorted(palette.items()):
        out.append(f'        "{key}" to Color(0xFF{value[1:].upper()}),')
    out.append("    )")
out.append("}")
target = ROOT / "uikit/src/commonMain/kotlin/com/github/jershell/shadcn/theme/EditorPaletteData.kt"
target.write_text("\n".join(out) + "\n", encoding="utf-8")
print("Verified and generated", {key: len(value) for key, value in palettes.items()})
