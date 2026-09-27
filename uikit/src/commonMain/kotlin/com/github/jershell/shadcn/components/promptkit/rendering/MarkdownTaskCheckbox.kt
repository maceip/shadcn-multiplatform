package com.github.jershell.shadcn.components.promptkit

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.buildAnnotatedString
import com.github.jershell.shadcn.components.checkbox.Checkbox
import com.mikepenz.markdown.annotator.annotatorSettings
import com.mikepenz.markdown.annotator.buildMarkdownAnnotatedString
import com.mikepenz.markdown.compose.components.MarkdownComponentModel
import org.intellij.markdown.MarkdownElementTypes

/** Read-only task marker. Web uses native HTML semantics while retaining the same Compose visual. */
@Composable
internal expect fun MarkdownTaskCheckbox(checked: Boolean, label: String, modifier: Modifier = Modifier)

@Composable
internal fun NativeMarkdownTaskCheckbox(checked: Boolean, label: String, modifier: Modifier) {
    Checkbox(checked = checked, onCheckedChange = {}, enabled = false,
        modifier = modifier.semantics { contentDescription = label })
}

/** Derive the accessible name from the same inline renderer as the visible label, without markup. */
@Composable
internal fun markdownTaskLabel(model: MarkdownComponentModel): String {
    val item = generateSequence(model.node.parent) { it.parent }
        .firstOrNull { it.type == MarkdownElementTypes.LIST_ITEM }
    val paragraph = item?.children?.firstOrNull { it.type == MarkdownElementTypes.PARAGRAPH }
        ?: return "Task"
    val settings = annotatorSettings()
    val rendered = buildAnnotatedString {
        buildMarkdownAnnotatedString(model.content, paragraph, settings)
    }.text
    return normalizeMarkdownTaskLabel(rendered).ifEmpty { "Task" }
}

/** Inline code adds visual padding spaces. Accessible names use flat, single-space whitespace. */
internal fun normalizeMarkdownTaskLabel(text: String): String = buildString {
    var pendingSpace = false
    for (character in text) {
        if (character.isWhitespace()) {
            pendingSpace = isNotEmpty()
        } else {
            if (pendingSpace) append(' ')
            append(character)
            pendingSpace = false
        }
    }
}
