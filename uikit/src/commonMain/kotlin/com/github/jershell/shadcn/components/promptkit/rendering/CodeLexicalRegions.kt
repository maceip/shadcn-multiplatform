package com.github.jershell.shadcn.components.promptkit

import dev.snipme.highlights.model.SyntaxLanguage

internal enum class CodeRegionKind { StringLiteral, Comment, Documentation }
internal data class CodeRegion(val start: Int, val end: Int, val kind: CodeRegionKind)

/**
 * Protect strings/comments before the keyword lexer runs. Highlights 1.1 locates comments before
 * strings and numbers after strings, which otherwise mistakes URLs and quoted numbers for code.
 * This is deliberately lexical: it does not evaluate template interpolation or regex literals.
 */
internal fun protectedCodeRegions(code: String, language: SyntaxLanguage): List<CodeRegion> {
    val hashComments = language in setOf(SyntaxLanguage.PYTHON, SyntaxLanguage.RUBY,
        SyntaxLanguage.PERL, SyntaxLanguage.SHELL, SyntaxLanguage.PHP)
    val slashComments = !hashComments || language == SyntaxLanguage.PHP
    val nestedComments = language in setOf(SyntaxLanguage.KOTLIN, SyntaxLanguage.RUST, SyntaxLanguage.SWIFT)
    val backtickStrings = language in setOf(SyntaxLanguage.JAVASCRIPT, SyntaxLanguage.TYPESCRIPT,
        SyntaxLanguage.GO, SyntaxLanguage.SHELL)
    val regions = mutableListOf<CodeRegion>()
    var index = 0
    while (index < code.length) {
        val start = index
        val character = code[index]
        when {
            (hashComments && character == '#' && (index == 0 || code[index - 1] != '\\')) ||
                (index == 0 && code.startsWith("#!")) ||
                (slashComments && code.startsWith("//", index)) -> {
                index = code.indexOf('\n', index).let { if (it < 0) code.length else it }
                regions += CodeRegion(start, index, if (code.startsWith("///", start)) CodeRegionKind.Documentation else CodeRegionKind.Comment)
            }
            slashComments && code.startsWith("/*", index) -> {
                index += 2
                var depth = 1
                while (index < code.length && depth > 0) {
                    when {
                        nestedComments && code.startsWith("/*", index) -> { depth++; index += 2 }
                        code.startsWith("*/", index) -> { depth--; index += 2 }
                        else -> index++
                    }
                }
                regions += CodeRegion(start, index, if (code.startsWith("/**", start)) CodeRegionKind.Documentation else CodeRegionKind.Comment)
            }
            character == '"' || character == '\'' || (backtickStrings && character == '`') -> {
                // Rust lifetimes ('a, 'static) are identifiers rather than unterminated character strings.
                if (language == SyntaxLanguage.RUST && character == '\'' &&
                    code.getOrNull(index + 1)?.let { it.isLetterOrDigit() || it == '_' } == true &&
                    code.getOrNull(index + 2) != '\'') { index++; continue }
                val triple = code.startsWith(character.toString().repeat(3), index) &&
                    (language == SyntaxLanguage.PYTHON || character == '"' && language in
                        setOf(SyntaxLanguage.KOTLIN, SyntaxLanguage.JAVA, SyntaxLanguage.SWIFT, SyntaxLanguage.CSHARP))
                val delimiter = character.toString().repeat(if (triple) 3 else 1)
                val verbatim = language == SyntaxLanguage.CSHARP && character == '"' && code.getOrNull(index - 1) == '@'
                index += delimiter.length
                while (index < code.length) {
                    when {
                        verbatim && code.startsWith("\"\"", index) -> index += 2
                        code.startsWith(delimiter, index) -> { index += delimiter.length; break }
                        !verbatim && !(triple && language == SyntaxLanguage.KOTLIN) &&
                            !(character == '`' && language == SyntaxLanguage.GO) && code[index] == '\\' ->
                            index = (index + 2).coerceAtMost(code.length)
                        else -> index++
                    }
                }
                regions += CodeRegion(start, index, CodeRegionKind.StringLiteral)
            }
            else -> index++
        }
    }
    return regions
}

internal fun maskProtectedCode(code: String, regions: List<CodeRegion>): String {
    val masked = code.toCharArray()
    regions.forEach { region ->
        for (index in region.start until region.end) if (masked[index] != '\n' && masked[index] != '\r') masked[index] = ' '
    }
    // Disable Highlights' language-agnostic comment detection (e.g. Python floor division //).
    for (index in masked.indices) if (masked[index] == '/' || masked[index] == '#') masked[index] = ' '
    return masked.concatToString()
}
