@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class,
    org.jetbrains.compose.resources.ExperimentalResourceApi::class)

package com.github.jershell.shadcn.theme

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import com.github.jershell.shadcn.components.promptkit.CodeBlockCode
import com.github.jershell.shadcn.components.promptkit.Markdown
import com.github.jershell.shadcn.generated.resources.Res
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EditorTextThemeInteractionTest {
    @Test
    fun departureResourceIsTheBundledOpenTypeFace() = runTest {
        val bytes = Res.readBytes(DEPARTURE_MONO_RESOURCE_PATH)
        assertEquals(84480, bytes.size)
        assertEquals("OTTO", bytes.copyOfRange(0, 4).decodeToString())
    }

    @Test
    fun bundledFontLoadsThroughTheExistingTypographyProvider() = runComposeUiTest {
        var layout: TextLayoutResult? = null
        setContent { ProvideDepartureMonoFont { ShadcnTheme {
            BasicText("Wi0", style = TypographyStyles.textBaseRegular, onTextLayout = { layout = it })
        } } }
        waitUntil { layout != null }
        runOnIdle {
            val result = requireNotNull(layout)
            val widths = (0..2).map { result.getBoundingBox(it).width }
            assertTrue(widths.all { it > 0 })
            assertEquals(widths[0], widths[1], absoluteTolerance = 0.1f)
            assertEquals(widths[1], widths[2], absoluteTolerance = 0.1f)
        }
    }

    @Test
    fun switchingTheProviderRecolorsStandaloneAndFencedCode() = runComposeUiTest {
        var theme by mutableStateOf(EditorTextThemes.SovietDark)
        val standalone = "val standalone = 42"
        // The trailing newline in fenced source must survive extraction.
        val fenced = "val fenced = 7\n"
        setContent { ShadcnTheme { ProvideEditorTextTheme(theme) {
            Column {
                CodeBlockCode(standalone, language = "kotlin")
                Markdown("```kotlin\n${fenced}```", immediate = true)
            }
        } } }
        fun containsColor(source: String, expected: Color): Boolean =
            onNodeWithText(source).fetchSemanticsNode().config[SemanticsProperties.Text]
                .any { text -> text.spanStyles.any { it.item.color == expected } }
        waitUntil { containsColor(standalone, Color(0xffc58f9d)) && containsColor(fenced, Color(0xffc58f9d)) }
        runOnIdle { theme = EditorTextThemes.FlumeOpal }
        waitUntil { containsColor(standalone, Color(0xff7540a3)) && containsColor(fenced, Color(0xff7540a3)) }
    }
}
