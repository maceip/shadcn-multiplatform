package com.github.jershell.shadcn.components.promptkit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.composeunstyled.theme.Theme
import com.github.jershell.shadcn.theme.BaseTokens
import com.github.jershell.shadcn.theme.ColorProps
import com.github.jershell.shadcn.theme.ColorTokens
import com.github.jershell.shadcn.theme.DimProps
import com.github.jershell.shadcn.theme.DimTokens
import com.github.jershell.shadcn.theme.TypographyStyles
import com.github.jershell.shadcn.theme.LocalEditorTextTheme
import com.github.jershell.shadcn.theme.shadcnMonospaceFontFamily
import dev.snipme.highlights.Highlights
import dev.snipme.highlights.model.SyntaxLanguage
import dev.snipme.highlights.model.PhraseLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A native syntax palette. Unlike Shiki theme names, this can use application/theme tokens. */
@Immutable
data class CodeBlockTheme(
    val code: Color,
    val keyword: Color,
    val string: Color,
    val literal: Color,
    val comment: Color,
    val metadata: Color = keyword,
    val punctuation: Color = code,
    val background: Color? = null,
    val boolean: Color = literal,
    val commentsItalic: Boolean = false,
    val keywordsBold: Boolean = false,
    val bracket: Color = punctuation,
    val documentation: Color = comment,
)

object CodeBlockDefaults {
    @Composable
    fun theme() = LocalEditorTextTheme.current?.code ?: CodeBlockTheme(
        code = Theme[ColorProps][ColorTokens.cardForeground],
        keyword = Theme[ColorProps][ColorTokens.primary],
        string = Theme[ColorProps][ColorTokens.chartToken2],
        literal = Theme[ColorProps][ColorTokens.chartToken1],
        comment = Theme[ColorProps][ColorTokens.mutedForeground],
        metadata = Theme[ColorProps][ColorTokens.chartToken3],
    )
}

/** Bordered, clipped code surface. Compose headers/actions using [CodeBlockGroup]. */
@Composable
fun CodeBlock(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(Theme[DimProps][DimTokens.radiusXl])
    Column(
        modifier.fillMaxWidth().clip(shape)
            .border(Theme[DimProps][DimTokens.borderWidth], Theme[ColorProps][ColorTokens.border], shape)
            .background(Theme[ColorProps][ColorTokens.card]),
        content = content,
    )
}

@Composable
fun CodeBlockGroup(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically, content = content)
}

/**
 * Selectable, horizontally scrollable syntax-highlighted source. Highlighting runs away from
 * composition and restarts when code, language or theme changes; unknown languages stay plain.
 */
@Composable
fun CodeBlockCode(
    code: String,
    modifier: Modifier = Modifier,
    language: String = "tsx",
    theme: CodeBlockTheme = CodeBlockDefaults.theme(),
    style: TextStyle = TypographyStyles.textSmRegular.copy(fontFamily = shadcnMonospaceFontFamily()),
    selectable: Boolean = true,
    softWrap: Boolean = false,
) {
    // Keying the state also prevents stale source flashing during a streaming update.
    val highlighted by androidx.compose.runtime.key(code, language, theme) {
        produceState(AnnotatedString(code), code, language, theme) {
            value = withContext(Dispatchers.Default) { highlightCode(code, language, theme) }
        }
    }
    val content: @Composable () -> Unit = {
        BasicText(highlighted, modifier.fillMaxWidth()
            .then(theme.background?.let { Modifier.background(it) } ?: Modifier)
            .then(if (softWrap) Modifier else Modifier.horizontalScroll(rememberScrollState()))
            .padding(BaseTokens.token16), style.copy(color = theme.code), softWrap = softWrap)
    }
    if (selectable) SelectionContainer { content() } else content()
}

internal fun highlightCode(code: String, language: String, theme: CodeBlockTheme): AnnotatedString {
    val normalized = when (language.lowercase().trim()) {
        "kt", "kts" -> "kotlin"
        "js", "jsx" -> "javascript"
        "ts", "tsx" -> "typescript"
        "py" -> "python"
        "sh", "bash", "zsh" -> "shell"
        "c++", "cc" -> "cpp"
        "c#", "cs" -> "csharp"
        "rs" -> "rust"
        else -> language
    }
    val syntax = SyntaxLanguage.getByName(normalized) ?: return AnnotatedString(code)
    return runCatching {
        val lexicalRegions = protectedCodeRegions(code, syntax)
        val structure = Highlights.Builder().code(maskProtectedCode(code, lexicalRegions)).language(syntax).build().getCodeStructure()
        buildAnnotatedString {
            append(code)
            fun mark(locations: Set<PhraseLocation>, style: (String) -> SpanStyle) {
                locations.forEach { location ->
                    val start = location.start.coerceIn(0, code.length)
                    val end = location.end.coerceIn(start, code.length)
                    if (start < end) addStyle(style(code.substring(start, end)), start, end)
                }
            }
            mark(structure.marks) { token ->
                SpanStyle(color = if (token in listOf("(", ")", "[", "]", "{", "}")) theme.bracket else theme.punctuation)
            }
            mark(structure.punctuations) { SpanStyle(color = theme.punctuation) }
            mark(structure.keywords) { token ->
                val isBoolean = token == "true" || token == "false" || token == "True" || token == "False"
                SpanStyle(color = if (isBoolean) theme.boolean else theme.keyword,
                    fontWeight = if (theme.keywordsBold && !isBoolean) FontWeight.Bold else null)
            }
            mark(structure.literals) { SpanStyle(color = theme.literal) }
            mark(structure.annotations) { SpanStyle(color = theme.metadata) }
            lexicalRegions.forEach { region ->
                val color = when (region.kind) {
                    CodeRegionKind.StringLiteral -> theme.string
                    CodeRegionKind.Comment -> theme.comment
                    CodeRegionKind.Documentation -> theme.documentation
                }
                addStyle(SpanStyle(color = color,
                    fontStyle = if (region.kind != CodeRegionKind.StringLiteral && theme.commentsItalic) FontStyle.Italic else null),
                    region.start, region.end)
            }
        }
    }.getOrElse { AnnotatedString(code) }
}
