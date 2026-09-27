@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.github.jershell.shadcn.ui.components.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.jershell.shadcn.components.button.Button
import com.github.jershell.shadcn.components.button.ButtonText
import com.github.jershell.shadcn.components.button.ButtonVariant
import com.github.jershell.shadcn.components.promptkit.CodeBlock
import com.github.jershell.shadcn.components.promptkit.CodeBlockCode
import com.github.jershell.shadcn.components.promptkit.Markdown
import com.github.jershell.shadcn.components.terminal.Terminal
import com.github.jershell.shadcn.components.terminal.TerminalOptions
import com.github.jershell.shadcn.components.terminal.TerminalRuntime
import com.github.jershell.shadcn.components.terminal.rememberTerminalController
import com.github.jershell.shadcn.components.typography.H4
import com.github.jershell.shadcn.components.typography.P
import com.github.jershell.shadcn.theme.EditorText
import com.github.jershell.shadcn.theme.EditorTextThemes
import com.github.jershell.shadcn.theme.ProvideDepartureMonoFont
import com.github.jershell.shadcn.theme.ProvideEditorTextTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun DemoEditorThemes() {
    var theme by remember { mutableStateOf(EditorTextThemes.SovietDark) }
    val controller = rememberTerminalController()
    val scope = rememberCoroutineScope()
    var initialized by remember { mutableStateOf(false) }
    var initializing by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        H4("Soviet, Flume & Departure Mono")
        P("Original upstream palettes, including both Soviet modes and all four Flume variants. Departure Mono 1.500 is bundled locally under the SIL Open Font License.")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            EditorTextThemes.all.forEach { choice ->
                Button(onClick = { theme = choice },
                    variant = if (theme == choice) ButtonVariant.Default else ButtonVariant.Outline) {
                    ButtonText(choice.displayName)
                }
            }
        }
        ProvideDepartureMonoFont {
            ProvideEditorTextTheme(theme) {
                Column(Modifier.fillMaxWidth().background(theme.background).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    EditorText("Departure Mono · ${theme.displayName}\nAa Bb 0123456789 → λ\n┌───────────────┐\n│ OFFLINE FONT  │\n└───────────────┘", style = TextStyle(fontSize = 22.sp))
                    CodeBlock { CodeBlockCode("// Exact upstream syntax colors\nfun greet(name: String): String {\n    val ready = true\n    val retries = 3\n    return \"Hello, \$name\"\n}", language = "kotlin") }
                    Markdown("""
                        ### Markdown shares this palette and font
                        A **strong** word, *emphasis*, and `inline code`.

                        ```kotlin
                        // Fences use the selected editor theme too
                        val complete = false
                        println("Ready")
                        ```
                    """.trimIndent())
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        theme.ansiColors.forEachIndexed { index, color ->
                            Box(Modifier.size(24.dp).background(color).semantics { contentDescription = "ANSI palette index $index" })
                        }
                    }
                }
            }
        }
        P("The terminal uses the same 16 ANSI colors and bundled font. This preview prints a sample; it does not start a shell.")
        if (!initialized) Button(onClick = { scope.launch {
            initializing = true
            failure = null
            try { TerminalRuntime.initialize(); initialized = true }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { failure = error.message ?: "Could not initialize terminal" }
            finally { initializing = false }
        } }, enabled = !initializing) { ButtonText(if (initializing) "Starting terminal…" else "Preview terminal palette") }
        failure?.let { P(it) }
        if (initialized) Terminal(controller, Modifier.fillMaxWidth().height(220.dp),
            colors = theme.terminalColors(), options = TerminalOptions(fontSize = 22),
            onError = { failure = it }, onReady = { scope.launch {
                controller.write("Departure Mono — offline ANSI palette\r\n" +
                    (0..15).joinToString("") { index -> "\u001b[${if (index < 8) 30 + index else 90 + index - 8}m▉▉ " } +
                    "\u001b[0m\r\n\u001b[32mReady\u001b[0m  0123456789  → λ\r\n")
            } })
    }
}
