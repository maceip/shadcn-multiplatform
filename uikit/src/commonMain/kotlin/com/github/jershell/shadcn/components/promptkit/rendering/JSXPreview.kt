package com.github.jershell.shadcn.components.promptkit

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.composeunstyled.theme.Theme
import com.github.jershell.shadcn.components.button.Button
import com.github.jershell.shadcn.components.button.ButtonVariant
import com.github.jershell.shadcn.components.button.LocalButtonContentColor
import com.github.jershell.shadcn.theme.BaseTokens
import com.github.jershell.shadcn.theme.ColorProps
import com.github.jershell.shadcn.theme.ColorTokens
import com.github.jershell.shadcn.theme.TypographyStyles
import com.github.jershell.shadcn.theme.shadcnMonospaceFontFamily

/** All attributes are retained and bindings resolved for application-provided native renderers. */
data class JsxElement(val name: String, val attributes: Map<String, Any?>, val children: List<JsxNode>)

typealias JsxComponentRenderer = @Composable (element: JsxElement, children: @Composable () -> Unit) -> Unit

private val LocalJsxTextStyle = staticCompositionLocalOf { TextStyle.Default }
private val LocalJsxOrderedList = staticCompositionLocalOf { false }
private val LocalJsxListPosition = staticCompositionLocalOf { 1 }
private val nativeElements = setOf("", "div", "section", "article", "main", "header", "footer", "nav",
    "span", "p", "label", "strong", "b", "em", "i", "s", "small", "h1", "h2", "h3", "h4", "h5", "h6",
    "button", "a", "img", "br", "hr", "pre", "code", "ul", "ol", "li")

/**
 * Native counterpart of prompt-kit's JSXPreview. Built-in elements use Compose; add application
 * components by name in [components]. [bindings] accepts scalars, maps, lists and callback values
 * (e.g. `onClick={save}`). Object/array literals are supported, JavaScript execution is not.
 *
 * Built-ins support text/heading/formatting, row/column layout via style.display/flexDirection,
 * numeric width/height/padding/gap/fontSize/borderRadius, hex/token colors, disabled buttons,
 * images and safe links. HTML className/CSS selectors and React components do not execute; all
 * original attributes/children remain available to a custom renderer. Use native modifiers in
 * those renderers for other layout/styling. Bad input reports [onError] and renders [fallback].
 */
@Composable
fun JSXPreview(
    jsx: String,
    modifier: Modifier = Modifier,
    isStreaming: Boolean = false,
    bindings: Map<String, Any?> = emptyMap(),
    components: Map<String, JsxComponentRenderer> = emptyMap(),
    onError: ((Throwable) -> Unit)? = null,
    fallback: @Composable (Throwable) -> Unit = { failure ->
        BasicText(jsx, Modifier.semantics { error(failure.message ?: "Invalid JSX") },
            style = TypographyStyles.textSmRegular.copy(color = Theme[ColorProps][ColorTokens.destructive]))
    },
) {
    val prepared = remember(jsx, isStreaming, bindings, components.keys.toSet()) {
        runCatching { prepareJsx(parseJsx(jsx, isStreaming), bindings, components.keys) }
    }
    val currentOnError by rememberUpdatedState(onError)
    val failure = prepared.exceptionOrNull()
    LaunchedEffect(failure) { if (failure != null) currentOnError?.invoke(failure) }
    Column(modifier) {
        if (failure != null) fallback(failure)
        else CompositionLocalProvider(LocalJsxTextStyle provides TypographyStyles.textBaseRegular
            .copy(color = Theme[ColorProps][ColorTokens.foreground])) {
            RenderNodes(prepared.getOrThrow(), components)
        }
    }
}

internal sealed interface PreparedJsxNode {
    data class Text(val text: String) : PreparedJsxNode
    data class Element(val value: JsxElement, val nodes: List<PreparedJsxNode>) : PreparedJsxNode
}

internal fun prepareJsx(nodes: List<JsxNode>, bindings: Map<String, Any?>, custom: Set<String>): List<PreparedJsxNode> =
    nodes.flatMap { node ->
        when (node) {
            is JsxNode.Text -> listOf(PreparedJsxNode.Text(node.text))
            is JsxNode.Expression -> {
                fun text(value: Any?): String = when (value) {
                    null, is Boolean -> ""
                    is String, is Number, is Char -> value.toString()
                    is List<*> -> value.joinToString("") { text(it) }
                    else -> throw IllegalArgumentException("An object/callback cannot be rendered as text")
                }
                listOf(PreparedJsxNode.Text(text(resolveJsxValue(node.value, bindings))))
            }
            is JsxNode.Element -> {
                require(node.name in nativeElements || node.name in custom) { "Unregistered component <${node.name}>" }
                val attributes = node.attributes.mapValues { resolveJsxValue(it.value, bindings) }
                if (node.name !in custom) {
                    val action = attributes["onClick"]
                    require(action == null || action is Function0<*>) { "onClick must be a bound callback" }
                    require(attributes["dangerouslySetInnerHTML"] == null) { "HTML injection is not supported" }
                    if (node.name == "a") require(safePreviewUri(attributes["href"]?.toString())) { "Unsupported link URI" }
                    if (node.name == "img") require(safeImageUri(attributes["src"]?.toString())) { "Unsupported image URI" }
                }
                listOf(PreparedJsxNode.Element(JsxElement(node.name, attributes, node.children),
                    prepareJsx(node.children, bindings, custom)))
            }
        }
    }

private fun safePreviewUri(uri: String?): Boolean = uri == null ||
    Regex("(?i)^(https?://|mailto:|tel:)[^\\s]+$").matches(uri)
private fun safeImageUri(uri: String?): Boolean = uri == null ||
    Regex("(?i)^https?://[^\\s]+$").matches(uri) ||
    Regex("(?i)^data:image/(png|jpeg|gif|webp);base64,[A-Za-z0-9+/=]+$").matches(uri)

@Composable
private fun RenderNodes(nodes: List<PreparedJsxNode>, components: Map<String, JsxComponentRenderer>) {
    var listPosition = 0
    nodes.forEach { node ->
        when (node) {
            is PreparedJsxNode.Text -> if (node.text.isNotEmpty()) BasicText(node.text, style = LocalJsxTextStyle.current)
            is PreparedJsxNode.Element -> {
                if (node.value.name == "li") listPosition++
                val children: @Composable () -> Unit = { RenderNodes(node.nodes, components) }
                CompositionLocalProvider(LocalJsxListPosition provides listPosition) {
                    components[node.value.name]?.let { renderer -> renderer(node.value, children) }
                        ?: RenderElement(node.value, children)
                }
            }
        }
    }
}

@Composable
private fun RenderElement(element: JsxElement, children: @Composable () -> Unit) {
    val attributes = element.attributes
    val style = attributes["style"] as? Map<*, *> ?: emptyMap<Any?, Any?>()
    val disabled = attributes["disabled"] == true
    val onClick = attributes["onClick"] as? Function0<*>
    var modifier: Modifier = Modifier
    (style["width"] as? Number)?.toFloat()?.takeIf { it.isFinite() && it >= 0 }?.let { modifier = modifier.width(it.dp) }
    (style["height"] as? Number)?.toFloat()?.takeIf { it.isFinite() && it >= 0 }?.let { modifier = modifier.height(it.dp) }
    if (style["width"] == "100%") modifier = modifier.fillMaxWidth()
    (style["borderRadius"] as? Number)?.toFloat()?.takeIf { it.isFinite() && it >= 0 }?.let { modifier = modifier.clip(RoundedCornerShape(it.dp)) }
    jsxColor(style["backgroundColor"] ?: style["background"])?.let { modifier = modifier.background(it) }
    (style["padding"] as? Number)?.toFloat()?.takeIf { it.isFinite() && it >= 0 }?.let { modifier = modifier.padding(it.dp) }
    attributes["aria-label"]?.toString()?.let { description -> modifier = modifier.semantics { contentDescription = description } }
    if (onClick != null && element.name != "button") modifier = modifier.clickable(enabled = !disabled) { onClick() }
    var text = LocalJsxTextStyle.current
    jsxColor(style["color"])?.let { text = text.copy(color = it) }
    (style["fontSize"] as? Number)?.toFloat()?.takeIf { it.isFinite() && it > 0 }?.let { text = text.copy(fontSize = it.sp) }
    if (style["fontWeight"] == "bold" || (style["fontWeight"] as? Number)?.toInt() == 700) text = text.copy(fontWeight = FontWeight.Bold)
    if (style["textAlign"] == "center") text = text.copy(textAlign = TextAlign.Center)
    val gap = ((style["gap"] as? Number)?.toFloat()?.takeIf { it.isFinite() && it >= 0 } ?: 0f).dp
    when (element.name) {
        "h1", "h2", "h3", "h4", "h5", "h6" -> {
            text = when (element.name) {
                "h1" -> TypographyStyles.textN3xlBold
                "h2" -> TypographyStyles.textN2xlBold
                "h3" -> TypographyStyles.textXlSemiBold
                else -> TypographyStyles.textLgSemiBold
            }.copy(color = text.color)
            modifier = modifier.semantics { heading() }
        }
        "strong", "b" -> text = text.copy(fontWeight = FontWeight.Bold)
        "em", "i" -> text = text.copy(fontStyle = FontStyle.Italic)
        "s" -> text = text.copy(textDecoration = TextDecoration.LineThrough)
        "code", "pre" -> text = text.copy(fontFamily = shadcnMonospaceFontFamily())
        "small" -> text = text.copy(fontSize = TypographyStyles.textSmRegular.fontSize)
    }
    CompositionLocalProvider(LocalJsxTextStyle provides text) {
        when (element.name) {
            "" -> children()
            "button" -> Button(onClick = { onClick?.invoke() }, modifier = modifier, enabled = !disabled,
                variant = when (attributes["variant"]) {
                    "outline" -> ButtonVariant.Outline; "secondary" -> ButtonVariant.Secondary
                    "ghost" -> ButtonVariant.Ghost; "destructive" -> ButtonVariant.Destructive
                    "link" -> ButtonVariant.Link; else -> ButtonVariant.Default
                }) {
                    CompositionLocalProvider(LocalJsxTextStyle provides text.copy(color = LocalButtonContentColor.current),
                        content = children)
                }
            "a" -> {
                val uriHandler = LocalUriHandler.current
                val href = attributes["href"]?.toString()
                FlowRow(modifier.clickable(enabled = !disabled && href != null) { href?.let(uriHandler::openUri) }) {
                    CompositionLocalProvider(LocalJsxTextStyle provides text.copy(
                        color = Theme[ColorProps][ColorTokens.primary], textDecoration = TextDecoration.Underline), content = children)
                }
            }
            "img" -> AsyncImage(attributes["src"], attributes["alt"]?.toString(), modifier)
            // A full-width zero-height item ends the current FlowRow line without adding a blank row.
            "br" -> Spacer(modifier.fillMaxWidth().height(0.dp))
            "hr" -> Spacer(modifier.fillMaxWidth().height(BaseTokens.token1).background(Theme[ColorProps][ColorTokens.border]))
            "li" -> Row(modifier, horizontalArrangement = Arrangement.spacedBy(BaseTokens.token8)) {
                BasicText(if (LocalJsxOrderedList.current) "${LocalJsxListPosition.current}." else "•", style = text)
                Column { children() }
            }
            "ul", "ol" -> CompositionLocalProvider(LocalJsxOrderedList provides (element.name == "ol")) {
                Column(modifier, verticalArrangement = Arrangement.spacedBy(gap)) { children() }
            }
            "pre" -> Column(modifier.background(Theme[ColorProps][ColorTokens.muted]).padding(BaseTokens.token16)) { children() }
            "span", "p", "strong", "b", "em", "i", "s", "small", "label", "code", "h1", "h2", "h3", "h4", "h5", "h6" ->
                FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(gap)) { children() }
            else -> if (style["display"] == "flex" && style["flexDirection"] != "column") {
                FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(gap)) { children() }
            } else Column(modifier, verticalArrangement = Arrangement.spacedBy(gap)) { children() }
        }
    }
}

@Composable
private fun jsxColor(value: Any?): Color? = when (value) {
    "foreground" -> Theme[ColorProps][ColorTokens.foreground]
    "background" -> Theme[ColorProps][ColorTokens.background]
    "primary" -> Theme[ColorProps][ColorTokens.primary]
    "muted" -> Theme[ColorProps][ColorTokens.muted]
    "muted-foreground" -> Theme[ColorProps][ColorTokens.mutedForeground]
    "destructive" -> Theme[ColorProps][ColorTokens.destructive]
    else -> (value as? String)?.takeIf { it.startsWith('#') }?.drop(1)?.let { hex ->
        val expanded = if (hex.length == 3) hex.flatMap { listOf(it, it) }.joinToString("") else hex
        if (expanded.length == 6) expanded.toLongOrNull(16)?.let { Color(it or 0xff000000L) } else null
    }
}
