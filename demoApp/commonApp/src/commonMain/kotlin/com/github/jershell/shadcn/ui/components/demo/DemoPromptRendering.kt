package com.github.jershell.shadcn.ui.components.demo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.jershell.shadcn.components.promptkit.CodeBlock
import com.github.jershell.shadcn.components.promptkit.CodeBlockCode
import com.github.jershell.shadcn.components.promptkit.Image
import com.github.jershell.shadcn.components.promptkit.JSXPreview
import com.github.jershell.shadcn.components.promptkit.Markdown
import com.github.jershell.shadcn.components.typography.H4
import com.github.jershell.shadcn.components.typography.P
import kotlin.io.encoding.Base64

// Original 16×16 RGBA gradient, generated from PNG scanlines and zlib.
private const val DemoImageBase64 = "iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAABJ0lEQVR4nBXMwYBFIQAAwIfwEUIIIYQQQgghhBBCCCGEEELIYHc6zHW+7xt/PwKRRKZQaXQGk8XmcPm+n4BAJJEpVBqdwWSxOdzfC4KAQCSRKVQancFksTnc8IIoIBBJZAqVRmcwWWwON74gCQhEEplCpdEZTBabw00vyAICkUSmUGl0BpPF5nDzC4qAQCSRKVQancFksTnc8oIqIBBJZAqVRmcwWWwOt76gCQhEEplCpdEZTBabw20v6AICkUSmUGl0BpPF5nD7C4aAQCSRKVQancFksTnc8YIpIBBJZAqVRmcwWWwOd75gCQhEEplCpdEZTBabw10v2AICkUSmUGl0BpPF5nD3C46AQCSRKVQancFksTnc84IrIBBJZAqVRmcwWWwOl39CGo8fkLNZnwAAAABJRU5ErkJggg=="

@Composable
fun DemoPromptRendering() {
    var clicks by remember { mutableIntStateOf(0) }
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        DemoSection("Markdown", "Native GFM with selectable text, tables, links, and highlighted fences.") {
            Markdown("""
                ## A streaming response
                **Strong**, *emphasis*, ~~removed~~ and `inline code`.
                A new line is preserved.

                | Platform | Status |
                | --- | --- |
                | Desktop | Ready |
                | Mobile | Ready |

                - [x] Components
                - [ ] Connect your agent

                ```kotlin
                val answer = "Hello Compose"
                ```
            """.trimIndent())
        }
        DemoSection("Code block", "Syntax colors follow the application palette.") {
            CodeBlock { CodeBlockCode("fun greet(name: String) = \"Hello, \$name\"", language = "kotlin") }
        }
        DemoSection("Native JSX preview", "Bound callbacks and registered native components; no JavaScript execution.") {
            JSXPreview("<div style={{gap:8}}><Notice label={label}>Preserved children</Notice><button onClick={increment}>Increment</button></div>",
                bindings = mapOf("label" to "Count: $clicks", "increment" to { clicks++ }),
                components = mapOf("Notice" to { element, children ->
                    BasicText(element.attributes["label"].toString()); children()
                }))
            JSXPreview("<p>Streaming keeps <strong>the trailing text", isStreaming = true)
        }
        DemoSection("Generated image", "Base64/byte data is decoded by Coil; descriptions remain accessible while waiting.") {
            val bytes = remember { Base64.Default.decode(DemoImageBase64) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Image(alt = "Original gradient from base64", base64 = DemoImageBase64, modifier = Modifier.size(96.dp))
                Image(alt = "Original gradient from bytes", uint8Array = bytes, modifier = Modifier.size(96.dp))
                Image(alt = "Waiting for generated image", modifier = Modifier.size(96.dp))
            }
        }
    }
}

@Composable
private fun DemoSection(title: String, description: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        H4(title)
        P(description)
        content()
    }
}
