package com.github.jershell.shadcn.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import com.github.jershell.shadcn.components.promptkit.CodeBlockTheme
import com.github.jershell.shadcn.components.terminal.TerminalColors

/**
 * Exact upstream colors, including roles the lightweight syntax lexer cannot classify (e.g. LSP
 * semantic types/properties). [palette] retains those names for editor integrations. No Neovim
 * plugin/LSP runtime is implied. Both Soviet variants and all four Flume variants are provided.
 */
@ConsistentCopyVisibility
data class EditorTextTheme internal constructor(
    val id: String,
    val displayName: String,
    val isDark: Boolean,
    val palette: Map<String, Color>,
    val background: Color,
    val foreground: Color,
    val muted: Color,
    val selection: Color,
    val cursor: Color,
    val border: Color,
    val code: CodeBlockTheme,
    val ansiColors: List<Color>,
) {
    fun terminalColors() = TerminalColors(background, palette.getValue("fg"), cursor, selection, ansiColors)
}

object EditorTextThemes {
    val SovietDark = soviet("soviet-dark", "Soviet Dark", true, EditorPaletteData.sovietDark)
    val SovietLight = soviet("soviet-light", "Soviet Light", false, EditorPaletteData.sovietLight)
    val FlumeDusk = flume("flume-dusk", "Flume Dusk", true, EditorPaletteData.flumeDusk)
    val FlumeOpal = flume("flume-opal", "Flume Opal", false, EditorPaletteData.flumeOpal)
    val FlumeMira = flume("flume-mira", "Flume Mira", true, EditorPaletteData.flumeMira)
    val FlumeMesa = flume("flume-mesa", "Flume Mesa", false, EditorPaletteData.flumeMesa)
    val all = listOf(SovietDark, SovietLight, FlumeDusk, FlumeOpal, FlumeMira, FlumeMesa)

    private fun soviet(id: String, name: String, dark: Boolean, palette: Map<String, Color>): EditorTextTheme {
        fun c(role: String) = palette.getValue(role)
        return EditorTextTheme(id, name, dark, palette, c("bg"), c("fg"), c("muted"), c("selection"), c("fg"), c("border"),
            CodeBlockTheme(code = c("fg"), keyword = c("burgundy"), string = c("string"), literal = c("ochre"),
                comment = c("comment"), metadata = c("steel"), punctuation = c("muted"), background = c("bg"),
                boolean = c("burgundy"), commentsItalic = true, keywordsBold = true),
            listOf(c(if (dark) "bg_dark" else "fg_bright"), c("red"), c("added"), c("ochre"), c("blue"), c("burgundy"), c("teal"), c("fg"),
                c("muted"), c("red_bright"), c("added_bright"), c("brass"), c("blue"), c("burgundy"), c("string"), c("fg_bright")))
    }

    private fun flume(id: String, name: String, dark: Boolean, palette: Map<String, Color>): EditorTextTheme {
        fun c(role: String) = palette.getValue(role)
        return EditorTextTheme(id, name, dark, palette, c("bg"), c("text"), c("muted"), c("black"), c("accent"), c("border"),
            CodeBlockTheme(code = c("syntax_primary"), keyword = c("syntax_keyword"), string = c("syntax_string"),
                literal = c("syntax_boolean"), comment = c("syntax_comment"), metadata = c("syntax_keyword"),
                punctuation = c("syntax_punctuation"), background = c("bg"),
                bracket = c("syntax_punctuation_bracket"), documentation = c("syntax_doc_comment")),
            listOf("black", "red", "green", "yellow", "blue", "magenta", "cyan", "white", "bright_black", "bright_red",
                "bright_green", "bright_yellow", "bright_blue", "bright_magenta", "bright_cyan", "bright_white").map(::c))
    }
}

val LocalEditorTextTheme = staticCompositionLocalOf<EditorTextTheme?> { null }

/** Colors code fences, standalone code, markdown prose and selectable editor text in this scope. */
@Composable
fun ProvideEditorTextTheme(theme: EditorTextTheme, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalEditorTextTheme provides theme,
        LocalTextSelectionColors provides TextSelectionColors(theme.cursor, theme.selection), content = content)
}

/** Plain selectable text using the active editor palette and font, useful alongside rendered code. */
@Composable
fun EditorText(text: String, modifier: Modifier = Modifier, style: TextStyle = TypographyStyles.textBaseRegular) {
    val theme = LocalEditorTextTheme.current
    SelectionContainer {
        BasicText(text, modifier.then(theme?.let { Modifier.background(it.background) } ?: Modifier),
            style = style.copy(color = theme?.foreground ?: style.color, fontFamily = shadcnMonospaceFontFamily()))
    }
}
