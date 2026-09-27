package com.github.jershell.shadcn.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.github.jershell.shadcn.components.promptkit.highlightCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class EditorTextThemeTest {
    @Test
    fun namedVariantsRetainActualUpstreamColors() {
        // Independent reference values from the pinned Lua palettes, not the repositories' screenshots.
        val expected = listOf(
            Triple(EditorTextThemes.SovietDark, 0xff292727, 0xff65aa88),
            Triple(EditorTextThemes.SovietLight, 0xffddd0b8, 0xff2b674d),
            Triple(EditorTextThemes.FlumeDusk, 0xff232136, 0xffa4b78a),
            Triple(EditorTextThemes.FlumeOpal, 0xfff2eff7, 0xff256f24),
            Triple(EditorTextThemes.FlumeMira, 0xff24212f, 0xff84b39f),
            Triple(EditorTextThemes.FlumeMesa, 0xfff3ede8, 0xff4b6d65),
        )
        expected.forEach { (theme, background, string) ->
            assertEquals(Color(background), theme.background, theme.id)
            assertEquals(Color(string), theme.code.string, theme.id)
            assertNotEquals(theme.background, theme.foreground, theme.id)
        }
        assertEquals(28, EditorTextThemes.SovietDark.palette.size)
        assertEquals(28, EditorTextThemes.SovietLight.palette.size)
        EditorTextThemes.all.drop(2).forEach { assertEquals(77, it.palette.size) }
    }

    @Test
    fun terminalAnsiSlotsRetainTheUpstreamOrderAndForeground() {
        EditorTextThemes.all.forEach { theme ->
            val terminal = theme.terminalColors()
            assertEquals(16, terminal.ansiColors?.size)
            assertEquals(theme.palette.getValue("fg"), terminal.foreground)
            assertEquals(theme.background, terminal.background)
            assertEquals(theme.cursor, terminal.cursor)
            assertEquals(theme.selection, terminal.selection)
        }
        assertEquals(Color(0xff1f1d1d), EditorTextThemes.SovietDark.ansiColors[0])
        assertEquals(Color(0xff292626), EditorTextThemes.SovietLight.ansiColors[0])
        assertEquals(Color(0xffef7565), EditorTextThemes.SovietDark.ansiColors[9])
        assertEquals(Color(0xff8ac8cf), EditorTextThemes.FlumeMira.ansiColors[14])
        assertEquals(Color(0xff423d47), EditorTextThemes.FlumeMesa.ansiColors[15])
        // Flume's terminal foreground intentionally differs from its editor prose color.
        assertNotEquals(EditorTextThemes.FlumeDusk.foreground, EditorTextThemes.FlumeDusk.terminalColors().foreground)
    }

    @Test
    fun sovietSyntaxRetainsDistinctNumbersBooleansAndItalicComments() {
        val code = "// comment\nval enabled = true\nval count = 42\nval message = \"Hello\""
        val theme = EditorTextThemes.SovietDark.code
        val rendered = highlightCode(code, "kotlin", theme)
        assertEquals(code, rendered.text)
        fun stylesAt(token: String) = rendered.spanStyles.filter {
            val offset = code.indexOf(token)
            it.start <= offset && it.end > offset
        }.map { it.item }
        assertTrue(stylesAt("// comment").any { it.color == Color(0xffa3a187) && it.fontStyle == FontStyle.Italic })
        assertTrue(stylesAt("val enabled").any { it.color == Color(0xffc58f9d) && it.fontWeight == FontWeight.Bold })
        assertTrue(stylesAt("true").any { it.color == Color(0xffc58f9d) })
        assertFalse(stylesAt("true").any { it.fontWeight == FontWeight.Bold })
        assertTrue(stylesAt("42").any { it.color == Color(0xffd29a69) })
        assertTrue(stylesAt("\"Hello\"").any { it.color == Color(0xff65aa88) })
    }

    @Test
    fun switchingThemeChangesRenderedSpansWithoutChangingSource() {
        val source = "// comment\nval answer = 42\nprintln(\"ready\")"
        val renderings = EditorTextThemes.all.map { highlightCode(source, "kotlin", it.code) }
        renderings.forEach { assertEquals(source, it.text) }
        assertEquals(6, renderings.map { it.spanStyles }.distinct().size)
        EditorTextThemes.all.drop(2).forEach { theme ->
            assertFalse(theme.code.commentsItalic)
            assertFalse(theme.code.keywordsBold)
        }
    }
}
