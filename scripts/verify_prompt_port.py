"""Check that every pinned prompt-kit public entry has a concrete source and gallery mapping."""
import json
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
inventory = json.loads((ROOT / "imports/prompt-kit/inventory.json").read_text(encoding="utf-8"))
base = "uikit/src/commonMain/kotlin/com/github/jershell/shadcn/components/promptkit/"
demo_base = "demoApp/commonApp/src/commonMain/kotlin/com/github/jershell/shadcn/ui/components/demo/"
mapping = {
    "prompt-input": ("core/PromptInput.kt", "DemoPromptChat"),
    "code-block": ("rendering/CodeBlock.kt", "DemoPromptRendering"),
    "markdown": ("rendering/Markdown.kt", "DemoPromptRendering"),
    "message": ("core/Message.kt", "DemoPromptChat"),
    "chat-container": ("core/ChatContainer.kt", "DemoPromptChat"),
    "scroll-button": ("core/ChatContainer.kt", "DemoPromptChat"),
    "loader": ("status/Loaders.kt", "DemoPromptStatus"),
    "prompt-suggestion": ("core/PromptSuggestion.kt", "DemoPromptChat"),
    "response-stream": ("status/ResponseStream.kt", "DemoPromptStatus"),
    "reasoning": ("status/DisclosureStatus.kt", "DemoPromptStatus"),
    "file-upload": ("core/FileUpload.kt", "DemoPromptChat"),
    "jsx-preview": ("rendering/JSXPreview.kt", "DemoPromptRendering"),
    "tool": ("status/DisclosureStatus.kt", "DemoPromptStatus"),
    "source": ("status/StatusActions.kt", "DemoPromptStatus"),
    "image": ("rendering/Image.kt", "DemoPromptRendering"),
    "steps": ("status/DisclosureStatus.kt", "DemoPromptStatus"),
    "system-message": ("status/StatusActions.kt", "DemoPromptStatus"),
    "chain-of-thought": ("status/DisclosureStatus.kt", "DemoPromptStatus"),
    "text-shimmer": ("status/Loaders.kt", "DemoPromptStatus"),
    "thinking-bar": ("status/StatusActions.kt", "DemoPromptStatus"),
    "feedback-bar": ("status/StatusActions.kt", "DemoPromptStatus"),
}
errors = []
rows = []
for component in inventory["components"]:
    name = component["name"]
    source, gallery = mapping[name]
    source_path = ROOT / base / source
    text = source_path.read_text(encoding="utf-8")
    for symbol in component["public_exports"]:
        if not re.search(r"\bfun\s+" + re.escape(symbol) + r"\s*\(", text):
            errors.append(f"Missing public component {symbol} in {source_path}")
    if not (ROOT / demo_base / (gallery + ".kt")).is_file():
        errors.append(f"Missing gallery {gallery}")
    rows.append(f"| {name} | `{source}` | {gallery} |")

blocks = "\n".join(p.read_text(encoding="utf-8") for p in (ROOT / base / "blocks").glob("*.kt"))
for block in inventory["blocks"] + inventory["primitives"]:
    for symbol in block["public_exports"]:
        if not re.search(r"\bfun\s+" + re.escape(symbol) + r"\s*\(", blocks):
            errors.append(f"Missing block/primitive {symbol}")
if errors:
    raise SystemExit("\n".join(errors))

print(f"Verified {len(mapping)} families, {sum(len(c['public_exports']) for c in inventory['components'])} exports, "
      f"{len(inventory['blocks'])} blocks and {len(inventory['primitives'])} primitives.")
if "--markdown" in __import__("sys").argv:
    print("\n| Source family | Compose implementation | Gallery |\n| --- | --- | --- |")
    print("\n".join(rows))
