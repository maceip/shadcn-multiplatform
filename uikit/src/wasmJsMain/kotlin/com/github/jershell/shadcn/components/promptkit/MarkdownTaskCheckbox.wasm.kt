@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, kotlin.js.ExperimentalWasmJsInterop::class)

package com.github.jershell.shadcn.components.promptkit

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.viewinterop.HtmlElementView
import com.github.jershell.shadcn.components.checkbox.Checkbox
import com.github.jershell.shadcn.components.modal.LocalShadcnModalLayerBlocked
import kotlinx.browser.document
import org.w3c.dom.HTMLInputElement

/**
 * Compose Web 1.11 serializes toggleables as buttons and omits checked/disabled ARIA state.
 * A scoped native input provides the task marker's only accessible node. The existing Compose
 * checkbox remains its visual, so themes, dimensions and motion stay identical to other targets.
 */
@Composable
internal actual fun MarkdownTaskCheckbox(checked: Boolean, label: String, modifier: Modifier) {
    val modalBlocked = LocalShadcnModalLayerBlocked.current
    Box(modifier) {
        Box(Modifier.clearAndSetSemantics { }) {
            Checkbox(checked = checked, onCheckedChange = {}, enabled = false)
        }
        HtmlElementView(
            modifier = Modifier.matchParentSize(),
            factory = {
                (document.createElement("input") as HTMLInputElement).apply {
                    type = "checkbox"
                    disabled = true
                    tabIndex = -1
                    setAttribute("role", "checkbox")
                    setAttribute("aria-disabled", "true")
                    style.margin = "0"
                    style.opacity = "0"
                    style.setProperty("box-sizing", "border-box")
                }
            },
            update = { input ->
                input.checked = checked
                input.setAttribute("aria-checked", checked.toString())
                input.setAttribute("aria-label", label)
                // HTML surfaces sit above the canvas. Covered layers must not remain exposed to AT.
                input.style.visibility = if (modalBlocked) "hidden" else ""
                if (modalBlocked) {
                    input.setAttribute("inert", "")
                    input.setAttribute("aria-hidden", "true")
                } else {
                    input.removeAttribute("inert")
                    input.removeAttribute("aria-hidden")
                }
            },
        )
    }
}
