package com.github.jershell.shadcn.components.promptkit

/** A declarative JSX value; bindings resolve only through caller-supplied maps/lists, never reflection. */
sealed interface JsxValue {
    data class Literal(val value: Any?) : JsxValue
    data class Binding(val path: String) : JsxValue
    data class ObjectValue(val fields: Map<String, JsxValue>) : JsxValue
    data class ArrayValue(val values: List<JsxValue>) : JsxValue
}

sealed interface JsxNode {
    data class Text(val text: String) : JsxNode
    data class Expression(val value: JsxValue) : JsxNode
    data class Element(
        val name: String,
        val attributes: Map<String, JsxValue>,
        val children: List<JsxNode>,
    ) : JsxNode
}

class JsxParseException(message: String, val offset: Int) : IllegalArgumentException("$message at $offset")

/**
 * Parses elements/fragments, quoted attributes, booleans, numbers, object/array literals and named
 * bindings. JavaScript calls/operators/functions/spreads are deliberately outside this native DSL.
 * Streaming accepts unfinished opening tags/expressions as literal text, and implicitly closes
 * complete open elements at EOF. It never drops trailing text or accepts mismatched closing tags.
 */
fun parseJsx(jsx: String, isStreaming: Boolean = false): List<JsxNode> =
    NativeJsxParser(jsx, isStreaming).parse()

/** Complete only fully formed open tags; preserve a truncated final tag/expression byte-for-byte. */
fun completeJsxTag(code: String): String = NativeJsxParser(code, streaming = true).complete()

fun resolveJsxValue(value: JsxValue, bindings: Map<String, Any?>): Any? = when (value) {
    is JsxValue.Literal -> value.value
    is JsxValue.ArrayValue -> value.values.map { resolveJsxValue(it, bindings) }
    is JsxValue.ObjectValue -> value.fields.mapValues { resolveJsxValue(it.value, bindings) }
    is JsxValue.Binding -> {
        val segments = value.path.split('.')
        require(segments.none { it == "__proto__" || it == "prototype" || it == "constructor" }) {
            "Reserved binding path: ${value.path}"
        }
        var current: Any? = bindings
        for (segment in segments) {
            current = when (current) {
                is Map<*, *> -> {
                    require(current.containsKey(segment)) { "Unknown binding: ${value.path}" }
                    current[segment]
                }
                is List<*> -> {
                    val index = segment.toIntOrNull()
                    require(index != null && index in current.indices) { "Unknown binding: ${value.path}" }
                    current[index]
                }
                else -> throw IllegalArgumentException("Unknown binding: ${value.path}")
            }
        }
        current
    }
}

private class IncompleteJsx : RuntimeException()

private class NativeJsxParser(private val source: String, private val streaming: Boolean) {
    private var position = 0
    private var count = 0
    private val unclosed = mutableListOf<String>()

    fun parse(): List<JsxNode> {
        require(source.length <= 1_000_000) { "JSX preview exceeds one million characters" }
        return children(null, 0)
    }

    fun complete(): String {
        parse()
        return source + unclosed.joinToString("") { if (it.isEmpty()) "</>" else "</$it>" }
    }

    private fun children(parent: String?, depth: Int): List<JsxNode> {
        if (depth > 128) fail("JSX nesting exceeds 128 levels")
        val nodes = mutableListOf<JsxNode>()
        while (position < source.length) {
            if (++count > 20_000) fail("Too many JSX nodes")
            val start = position
            try {
                when {
                    source.startsWith("</", position) -> {
                        position += 2
                        val name = if (peek() == '>') "" else name()
                        whitespace(); expect('>')
                        if (parent == null || parent != name) fail("Unexpected closing tag </$name>")
                        return nodes
                    }
                    source.startsWith("<!--", position) -> {
                        val end = source.indexOf("-->", position + 4)
                        if (end < 0) throw IncompleteJsx()
                        position = end + 3
                    }
                    source.startsWith("{/*", position) -> {
                        val end = source.indexOf("*/}", position + 3)
                        if (end < 0) throw IncompleteJsx()
                        position = end + 3
                    }
                    peek() == '<' -> {
                        position++
                        val tag = if (peek() == '>') "" else name()
                        val attributes = linkedMapOf<String, JsxValue>()
                        whitespace()
                        while (peek() != '>' && !source.startsWith("/>", position)) {
                            if (position == source.length) throw IncompleteJsx()
                            val attribute = name()
                            if (attributes.containsKey(attribute)) fail("Duplicate attribute $attribute")
                            whitespace()
                            attributes[attribute] = if (peek() == '=') {
                                position++; whitespace()
                                when (peek()) {
                                    '\'', '"' -> JsxValue.Literal(decodeEntities(quoted()))
                                    '{' -> expression()
                                    null -> throw IncompleteJsx()
                                    else -> fail("Attribute values must be quoted or bound")
                                }
                            } else JsxValue.Literal(true)
                            whitespace()
                        }
                        val selfClosing = source.startsWith("/>", position)
                        if (selfClosing) position += 2 else expect('>')
                        val nested = if (selfClosing) emptyList() else children(tag, depth + 1)
                        nodes += JsxNode.Element(tag, attributes, nested)
                    }
                    peek() == '{' -> nodes += JsxNode.Expression(expression())
                    else -> {
                        while (position < source.length && peek() != '<' && peek() != '{') position++
                        nodes += JsxNode.Text(decodeEntities(source.substring(start, position)))
                    }
                }
            } catch (_: IncompleteJsx) {
                if (!streaming) throw JsxParseException("Incomplete JSX", start)
                nodes += JsxNode.Text(source.substring(start))
                position = source.length
            }
        }
        if (parent != null) {
            if (!streaming) fail("Unclosed tag <$parent>")
            unclosed += parent
        }
        return nodes
    }

    private fun expression(): JsxValue {
        expect('{'); whitespace()
        val value = value(0)
        whitespace(); expect('}')
        return value
    }

    private fun value(depth: Int): JsxValue {
        if (depth > 128) fail("Expression nesting exceeds 128 levels")
        whitespace()
        return when (peek()) {
            null -> throw IncompleteJsx()
            '\'', '"' -> JsxValue.Literal(quoted())
            '{' -> {
                position++; whitespace()
                val fields = linkedMapOf<String, JsxValue>()
                while (peek() != '}') {
                    // ':' belongs to JSX qualified names, but separates an object key from its value.
                    val key = if (peek() == '\'' || peek() == '"') quoted() else name(qualified = false)
                    whitespace(); expect(':')
                    if (fields.containsKey(key)) fail("Duplicate object key $key")
                    fields[key] = value(depth + 1); whitespace()
                    if (peek() != ',') break
                    position++; whitespace()
                }
                expect('}'); JsxValue.ObjectValue(fields)
            }
            '[' -> {
                position++; whitespace()
                val values = mutableListOf<JsxValue>()
                while (peek() != ']') {
                    values += value(depth + 1); whitespace()
                    if (peek() != ',') break
                    position++; whitespace()
                }
                expect(']'); JsxValue.ArrayValue(values)
            }
            else -> {
                val start = position
                while (peek()?.let { it.isLetterOrDigit() || it in "_$.-+" } == true) position++
                if (start == position) fail("Expected a literal or binding")
                val text = source.substring(start, position)
                if (position == source.length) throw IncompleteJsx()
                when (text) {
                    "true" -> JsxValue.Literal(true)
                    "false" -> JsxValue.Literal(false)
                    "null", "undefined" -> JsxValue.Literal(null)
                    else -> text.toLongOrNull()?.let { JsxValue.Literal(it) }
                        ?: text.toDoubleOrNull()?.takeIf { it.isFinite() }?.let { JsxValue.Literal(it) }
                        ?: if (text.matches(Regex("[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z0-9_$]+)*"))) {
                            JsxValue.Binding(text)
                        } else fail("Unsupported expression $text")
                }
            }
        }
    }

    private fun quoted(): String {
        val quote = peek() ?: throw IncompleteJsx()
        position++
        val result = StringBuilder()
        while (position < source.length) {
            val ch = source[position++]
            if (ch == quote) return result.toString()
            if (ch == '\\') {
                val escaped = peek() ?: throw IncompleteJsx()
                position++
                result.append(when (escaped) { 'n' -> '\n'; 'r' -> '\r'; 't' -> '\t'; else -> escaped })
            } else result.append(ch)
        }
        throw IncompleteJsx()
    }

    private fun name(qualified: Boolean = true): String {
        val start = position
        if (peek() == null) throw IncompleteJsx()
        if (peek()?.let { it.isLetter() || it == '_' || it == '$' } != true) fail("Expected a name")
        position++
        val additionalCharacters = if (qualified) "_$.:-" else "_$"
        while (peek()?.let { it.isLetterOrDigit() || it in additionalCharacters } == true) position++
        return source.substring(start, position)
    }

    private fun whitespace() { while (peek()?.isWhitespace() == true) position++ }
    private fun peek(): Char? = source.getOrNull(position)
    private fun expect(ch: Char) {
        if (position == source.length) throw IncompleteJsx()
        if (source[position] != ch) fail("Expected '$ch'")
        position++
    }
    private fun fail(message: String): Nothing = throw JsxParseException(message, position)
}

private val entityPattern = Regex("&(#x[0-9a-fA-F]+|#[0-9]+|amp|lt|gt|quot|apos|nbsp);")
private fun decodeEntities(text: String): String = entityPattern.replace(text) { match ->
    when (val value = match.groupValues[1]) {
        "amp" -> "&"; "lt" -> "<"; "gt" -> ">"; "quot" -> "\""; "apos" -> "'"; "nbsp" -> "\u00a0"
        else -> {
            val code = if (value.startsWith("#x")) value.drop(2).toIntOrNull(16) else value.drop(1).toIntOrNull()
            when {
                code == null || code !in 1..0x10ffff || code in 0xd800..0xdfff -> "\uFFFD"
                code <= 0xffff -> code.toChar().toString()
                else -> ((code - 0x10000) / 0x400 + 0xd800).toChar().toString() +
                    ((code - 0x10000) % 0x400 + 0xdc00).toChar()
            }
        }
    }
}
