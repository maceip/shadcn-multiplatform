package com.github.jershell.shadcn.components.promptkit

import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.style.TextDecoration
import com.composeunstyled.theme.Theme
import com.github.jershell.shadcn.theme.BaseTokens
import com.github.jershell.shadcn.theme.ColorProps
import com.github.jershell.shadcn.theme.ColorTokens
import com.github.jershell.shadcn.theme.TypographyStyles
import com.github.jershell.shadcn.theme.LocalEditorTextTheme
import com.github.jershell.shadcn.theme.shadcnMonospaceFontFamily
import com.mikepenz.markdown.coil3.Coil3ImageTransformerImpl
import com.mikepenz.markdown.compose.Markdown as CoreMarkdown
import com.mikepenz.markdown.compose.LocalMarkdownComponents
import com.mikepenz.markdown.compose.MarkdownElement
import com.mikepenz.markdown.compose.components.MarkdownComponents
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.model.DefaultMarkdownColors
import com.mikepenz.markdown.model.DefaultMarkdownTypography
import com.mikepenz.markdown.model.ImageTransformer
import com.mikepenz.markdown.model.markdownAnnotator
import com.mikepenz.markdown.model.markdownAnnotatorConfig
import com.mikepenz.markdown.model.MarkdownColors
import com.mikepenz.markdown.model.MarkdownTypography
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor

object PromptMarkdownDefaults {
    @Composable
    fun colors(): MarkdownColors = DefaultMarkdownColors(
        text = LocalEditorTextTheme.current?.foreground ?: Theme[ColorProps][ColorTokens.foreground],
        codeBackground = LocalEditorTextTheme.current?.background ?: Theme[ColorProps][ColorTokens.card],
        inlineCodeBackground = LocalEditorTextTheme.current?.selection ?: Theme[ColorProps][ColorTokens.muted],
        dividerColor = LocalEditorTextTheme.current?.border ?: Theme[ColorProps][ColorTokens.border],
        tableBackground = LocalEditorTextTheme.current?.background ?: Theme[ColorProps][ColorTokens.card],
    )

    @Composable
    fun typography(): MarkdownTypography {
        val text = TypographyStyles.textBaseRegular
        return DefaultMarkdownTypography(
            h1 = TypographyStyles.textN3xlBold, h2 = TypographyStyles.textN2xlBold,
            h3 = TypographyStyles.textXlSemiBold, h4 = TypographyStyles.textLgSemiBold,
            h5 = TypographyStyles.textBaseSemiBold, h6 = TypographyStyles.textSmSemiBold,
            text = text, paragraph = text, quote = text, ordered = text, bullet = text, list = text,
            code = TypographyStyles.textSmRegular.copy(fontFamily = shadcnMonospaceFontFamily()),
            inlineCode = TypographyStyles.textSmRegular.copy(fontFamily = shadcnMonospaceFontFamily()),
            table = text,
            textLink = TextLinkStyles(SpanStyle(color = LocalEditorTextTheme.current?.cursor ?: Theme[ColorProps][ColorTokens.primary],
                textDecoration = TextDecoration.Underline)),
        )
    }

    fun components(): MarkdownComponents = markdownComponents(
        custom = { type, model ->
            if (type.name.contains("HTML")) {
                BasicText(model.content.substring(model.node.startOffset, model.node.endOffset), style = model.typography.text)
            } else {
                // Match the core renderer's normal fallback. List markers are structural leaf nodes;
                // printing every unknown token repeats the bullet/checkbox inside the label column.
                val components = LocalMarkdownComponents.current
                model.node.children.forEach { child ->
                    MarkdownElement(child, components, model.content, includeSpacer = false)
                }
            }
        },
        checkbox = { model ->
            val marker = model.content.substring(model.node.startOffset, model.node.endOffset)
            // GFM task inputs are read-only in rendered chat text, as in react-markdown.
            MarkdownTaskCheckbox(checked = marker.contains("[x]", ignoreCase = true), label = markdownTaskLabel(model),
                modifier = Modifier.padding(top = BaseTokens.token4, end = BaseTokens.token8))
        },
        codeFence = { model ->
            val fence = extractFencedCode(model.content.substring(model.node.startOffset, model.node.endOffset))
            CodeBlock { CodeBlockCode(fence.code, language = fence.language, style = model.typography.code) }
        },
        codeBlock = { model ->
            val code = extractIndentedCode(model.content.substring(model.node.startOffset, model.node.endOffset))
            CodeBlock { CodeBlockCode(code, language = "plaintext", style = model.typography.code) }
        },
    )
}

internal data class FencedCode(val code: String, val language: String)

/** Four columns belong to the block marker; further indentation belongs to the code itself. */
internal fun extractIndentedCode(source: String): String = source.split('\n').joinToString("\n") { line ->
    var index = 0
    var columns = 0
    while (index < line.length && columns < 4 && (line[index] == ' ' || line[index] == '\t')) {
        columns += if (line[index] == '\t') 4 - columns % 4 else 1
        index++
    }
    line.substring(index)
}

/** Preserve content indentation; only CommonMark's opening-fence indentation is structural. */
internal fun extractFencedCode(source: String): FencedCode {
    val firstLineEnd = source.indexOf('\n')
    val firstLine = source.substring(0, if (firstLineEnd < 0) source.length else firstLineEnd).trimEnd('\r')
    val opening = Regex("^( {0,3})(`{3,}|~{3,})(.*)$").matchEntire(firstLine)
        ?: return FencedCode(source, "plaintext")
    val marker = opening.groupValues[2]
    val language = opening.groupValues[3].trim().split(Regex("\\s+"), limit = 2).first().ifEmpty { "plaintext" }
    var body = if (firstLineEnd < 0) "" else source.substring(firstLineEnd + 1)
    val closing = Regex("(?m)^ {0,3}${marker.first()}{${marker.length},}[ \\t]*\\r?$").findAll(body).lastOrNull()
    if (closing != null && body.substring(closing.range.last + 1).isBlank()) body = body.substring(0, closing.range.first)
    val indentation = opening.groupValues[1].length
    if (indentation > 0) body = body.split('\n').joinToString("\n") { line ->
        line.drop(line.takeWhile { it == ' ' }.length.coerceAtMost(indentation))
    }
    return FencedCode(body, language)
}

/**
 * Native GitHub-flavored markdown (tables, task lists, strikethrough, fenced code and links).
 * Newlines are rendered as breaks to match prompt-kit's remark-breaks behavior. Existing content
 * remains visible while new streaming text parses. Pass [components] to replace individual blocks.
 * Raw HTML is text, never an embedded executable document. The default link handler permits
 * http(s), mailto and tel only; [onLinkClick] opts into application-defined handling of all links.
 */
@Composable
fun Markdown(
    content: String,
    modifier: Modifier = Modifier,
    components: MarkdownComponents = remember { PromptMarkdownDefaults.components() },
    colors: MarkdownColors = PromptMarkdownDefaults.colors(),
    typography: MarkdownTypography = PromptMarkdownDefaults.typography(),
    imageTransformer: ImageTransformer = Coil3ImageTransformerImpl,
    selectable: Boolean = true,
    breaks: Boolean = true,
    immediate: Boolean = false,
    onLinkClick: ((String) -> Unit)? = null,
) {
    val editorTheme = LocalEditorTextTheme.current
    val flavour = remember { GFMFlavourDescriptor() }
    val annotator = remember(breaks) {
        markdownAnnotator(markdownAnnotatorConfig(eolAsNewLine = breaks)) { source, node ->
            if (node.type.name.contains("HTML")) {
                append(source.substring(node.startOffset, node.endOffset))
                true
            } else false
        }
    }
    val renderer: @Composable () -> Unit = {
        CoreMarkdown(content, colors, typography, modifier.then(editorTheme?.let { Modifier.background(it.background) } ?: Modifier),
            components = components, annotator = annotator,
            flavour = flavour, imageTransformer = imageTransformer, retainState = true,
            immediate = immediate, error = { BasicText(content, it, style = typography.text) })
    }
    val platformUriHandler = LocalUriHandler.current
    val latestLinkClick = rememberUpdatedState(onLinkClick)
    val safeUriHandler = remember(platformUriHandler) {
        object : UriHandler {
            override fun openUri(uri: String) {
                val override = latestLinkClick.value
                if (override != null) override(uri)
                else if (isSafeMarkdownUrl(uri)) platformUriHandler.openUri(uri)
            }
        }
    }
    CompositionLocalProvider(LocalUriHandler provides safeUriHandler) {
        if (selectable) SelectionContainer { renderer() } else renderer()
    }
}

/** Explicit app callbacks may handle other schemes. Untrusted markdown never launches them by default. */
internal fun isSafeMarkdownUrl(url: String): Boolean =
    Regex("(?i)^(https?://|mailto:|tel:)[^\\s\\p{Cntrl}]+$").matches(url)
