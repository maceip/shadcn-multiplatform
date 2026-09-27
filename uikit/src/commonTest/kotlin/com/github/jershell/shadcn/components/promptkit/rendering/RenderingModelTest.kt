package com.github.jershell.shadcn.components.promptkit

import androidx.compose.ui.graphics.Color
import dev.snipme.highlights.model.SyntaxLanguage
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RenderingModelTest {
    @Test
    fun markdownLinksOnlyAllowDeclaredWebAndContactSchemes() {
        listOf("https://example.com", "HTTP://example.com", "mailto:a@example.com", "tel:+15551234567")
            .forEach { assertTrue(isSafeMarkdownUrl(it), it) }
        listOf("javascript:alert(1)", "data:text/html,test", "file:///etc/passwd", "intent://app", "https://host\npath")
            .forEach { assertFalse(isSafeMarkdownUrl(it), it) }
    }
    @Test
    fun fencesPreserveSignificantIndentationAndIncompleteLastLine() {
        assertEquals(FencedCode("    indented\n  less\n", "kotlin"),
            extractFencedCode("```kotlin\n    indented\n  less\n```"))
        assertEquals(FencedCode("  nested\n", "js"), extractFencedCode("  ```js\n    nested\n  ```"))
        assertEquals(FencedCode("tail", "text"), extractFencedCode("~~~text\ntail"))
        assertEquals("    significant\n  less\n\ttab\n", extractIndentedCode("        significant\n      less\n\t\ttab\n"))
    }
    @Test
    fun completionPreservesTrailingTextAndClosesInReverseOrder() {
        assertEquals("<div><b>Hello world</b></div>", completeJsxTag("<div><b>Hello world"))
        assertEquals("before<div>inside</div>after", completeJsxTag("before<div>inside</div>after"))
        assertEquals("plain text", completeJsxTag("plain text"))
    }

    @Test
    fun quotedGreaterThanDoesNotTerminateAttributes() {
        val element = assertIs<JsxNode.Element>(parseJsx("<div title=\"a > b\"><br />tail</div>").single())
        assertEquals(JsxValue.Literal("a > b"), element.attributes["title"])
        assertEquals(JsxNode.Text("tail"), element.children.last())
    }

    @Test
    fun streamingTruncatedTagsAndExpressionsRemainVisible() {
        val parent = assertIs<JsxNode.Element>(parseJsx("<div>hello <span title=\"par", true).single())
        assertEquals(JsxNode.Text("<span title=\"par"), parent.children.last())
        val partial = assertIs<JsxNode.Element>(parseJsx("<div>hello {person.", true).single())
        assertEquals(JsxNode.Text("{person."), partial.children.last())
    }

    @Test
    fun mismatchedTagsNeverBecomeValidBecauseOfStreaming() {
        assertFailsWith<JsxParseException> { parseJsx("<div><b>bad</div></b>", true) }
        assertFailsWith<JsxParseException> { parseJsx("<div>unfinished") }
        assertFailsWith<JsxParseException> { parseJsx("<div value=\"unterminated") }
    }

    @Test
    fun fragmentsCommentsEntitiesAndBindingsPreserveContent() {
        val parsed = parseJsx("<>A &amp; B{/* ignored */}<span>{user.name}</span>&#x1f600;</>")
        val resolved = prepareJsx(parsed, mapOf("user" to mapOf("name" to "Mina")), emptySet())
        val fragment = assertIs<PreparedJsxNode.Element>(resolved.single())
        assertEquals(PreparedJsxNode.Text("A & B"), fragment.nodes[0])
        assertEquals(PreparedJsxNode.Text("😀"), fragment.nodes.last())
        assertEquals(listOf(PreparedJsxNode.Text("Mina")), assertIs<PreparedJsxNode.Element>(fragment.nodes[1]).nodes)
    }

    @Test
    fun customComponentsReceiveAllAttributesAndNestedObjects() {
        val action = {}
        val parsed = parseJsx("<Status label={name} count={42} disabled style={{color:'primary', padding:12}} onClick={save}>tail</Status>")
        val result = assertIs<PreparedJsxNode.Element>(prepareJsx(parsed, mapOf("name" to "Ready", "save" to action), setOf("Status")).single())
        assertEquals("Ready", result.value.attributes["label"])
        assertEquals(42L, result.value.attributes["count"])
        assertEquals(true, result.value.attributes["disabled"])
        assertEquals(mapOf("color" to "primary", "padding" to 12L), result.value.attributes["style"])
        assertEquals(action, result.value.attributes["onClick"])
        assertEquals(listOf(JsxNode.Text("tail")), result.value.children)
    }

    @Test
    fun objectKeysUseExpressionGrammarWhileJsxAttributesKeepQualifiedNames() {
        val element = assertIs<JsxNode.Element>(parseJsx("<Panel data-id=\"preview\" xml:lang=\"en\" options={{layout:{gap:8},items:[{label:'one'},2], 'http:header':'kept'}} />").single())
        assertEquals(JsxValue.Literal("preview"), element.attributes["data-id"])
        assertEquals(JsxValue.Literal("en"), element.attributes["xml:lang"])
        assertEquals(mapOf("layout" to mapOf("gap" to 8L), "items" to listOf(mapOf("label" to "one"), 2L),
            "http:header" to "kept"), resolveJsxValue(element.attributes.getValue("options"), emptyMap()))
    }

    @Test
    fun bindingsDoNotEvaluateFunctionsOrReflectProperties() {
        assertFailsWith<JsxParseException> { parseJsx("<div>{run()}</div>") }
        assertFailsWith<JsxParseException> { parseJsx("<button onClick={() => run()} />") }
        assertFailsWith<IllegalArgumentException> { resolveJsxValue(JsxValue.Binding("value.constructor"), mapOf("value" to "text")) }
        assertFailsWith<IllegalArgumentException> { resolveJsxValue(JsxValue.Binding("missing"), emptyMap()) }
        assertEquals("second", resolveJsxValue(JsxValue.Binding("items.1"), mapOf("items" to listOf("first", "second"))))
    }

    @Test
    fun unregisteredElementsAndExecutableUrisFailBeforeComposition() {
        assertFailsWith<IllegalArgumentException> { prepareJsx(parseJsx("<script>alert</script>"), emptyMap(), emptySet()) }
        assertFailsWith<IllegalArgumentException> { prepareJsx(parseJsx("<a href=\"javascript:alert(1)\">bad</a>"), emptyMap(), emptySet()) }
        assertFailsWith<IllegalArgumentException> { prepareJsx(parseJsx("<button onClick=\"run\" />"), emptyMap(), emptySet()) }
    }

    @Test
    fun codeHighlightsChangeWithThemeAndNeverChangeSource() {
        val code = "fun answer() = 42\n// hello"
        val first = CodeBlockTheme(Color.Black, Color.Red, Color.Green, Color.Blue, Color.Gray)
        val second = first.copy(keyword = Color.Magenta)
        val original = highlightCode(code, "kotlin", first)
        assertEquals(code, original.text)
        assertTrue(original.spanStyles.isNotEmpty())
        assertNotEquals(original.spanStyles, highlightCode(code, "kotlin", second).spanStyles)
        assertEquals(emptyList(), highlightCode(code, "unknown-language", first).spanStyles)
    }

    @Test
    fun generatedImagePrefersBase64AndValidatesInput() {
        assertContentEquals(byteArrayOf(1, 2, 3), decodeGeneratedImage("AQID", byteArrayOf(9), "image/png"))
        assertContentEquals(byteArrayOf(9), decodeGeneratedImage(null, byteArrayOf(9), "image/jpeg"))
        assertNull(decodeGeneratedImage(null, null, "image/png"))
        assertFailsWith<IllegalArgumentException> { decodeGeneratedImage("!!!", null, "image/png") }
        assertFailsWith<IllegalArgumentException> { decodeGeneratedImage("AQID", null, "text/html") }
    }

    @Test
    fun syntaxKeepsUrlsNumbersAndEscapedQuotesInsideStrings() {
        val code = "val url = \"https://example.com/42\"; val quoted = \"say \\\"42\\\"\" // real comment"
        val palette = CodeBlockTheme(Color.Black, Color.Red, Color.Green, Color.Blue, Color.Gray)
        val rendered = highlightCode(code, "kotlin", palette)
        val strings = protectedCodeRegions(code, SyntaxLanguage.KOTLIN).filter { it.kind == CodeRegionKind.StringLiteral }
        assertEquals(2, strings.size)
        strings.forEach { region ->
            assertEquals(listOf(Color.Green), rendered.spanStyles.filter { it.start >= region.start && it.end <= region.end }.map { it.item.color })
        }
        assertEquals(code, rendered.text)
        assertEquals("// real comment", code.substring(protectedCodeRegions(code, SyntaxLanguage.KOTLIN).last().start))
    }

    @Test
    fun syntaxRecognizesLanguageCommentsAndStreamingStringTails() {
        val python = "ratio = 42 // 2 # real comment\nname = \"streamed tail"
        val regions = protectedCodeRegions(python, SyntaxLanguage.PYTHON)
        assertEquals(listOf(CodeRegionKind.Comment, CodeRegionKind.StringLiteral), regions.map { it.kind })
        assertEquals("# real comment", python.substring(regions[0].start, regions[0].end))
        assertEquals(python.length, regions.last().end)
        val masked = maskProtectedCode(python, regions)
        assertEquals(python.length, masked.length)
        assertEquals(python.indexOf('\n'), masked.indexOf('\n'))
    }

    @Test
    fun syntaxKeepsNestedKotlinCommentsTogetherAndPreservesRustLifetimes() {
        val kotlin = "/* outer /* inner */ tail */ val end = true"
        val regions = protectedCodeRegions(kotlin, SyntaxLanguage.KOTLIN)
        assertEquals(1, regions.size)
        assertEquals("/* outer /* inner */ tail */", kotlin.substring(regions.single().start, regions.single().end))
        val rust = "fn borrow<'a>(value: &'a str) { let c = 'x'; }"
        val literals = protectedCodeRegions(rust, SyntaxLanguage.RUST)
        assertEquals(listOf("'x'"), literals.map { rust.substring(it.start, it.end) })
    }
}
