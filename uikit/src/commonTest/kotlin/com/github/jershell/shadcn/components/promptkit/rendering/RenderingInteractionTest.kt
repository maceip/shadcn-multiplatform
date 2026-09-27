@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package com.github.jershell.shadcn.components.promptkit

import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import com.github.jershell.shadcn.theme.ShadcnTheme
import com.mikepenz.markdown.compose.components.markdownComponents
import kotlin.test.Test
import kotlin.test.assertEquals

class RenderingInteractionTest {
    @Test
    fun previewBindsActionsUpdatesAndDisabledState() = runComposeUiTest {
        var name by mutableStateOf("Send")
        var disabled by mutableStateOf(false)
        var calls = 0
        setContent { ShadcnTheme {
            JSXPreview("<div><button onClick={submit} disabled={disabled}>{name}</button></div>",
                bindings = mapOf("name" to name, "disabled" to disabled, "submit" to { calls++ }))
        } }
        onNodeWithText("Send").performClick()
        runOnIdle { assertEquals(1, calls); name = "Stop"; disabled = true }
        onNodeWithText("Stop").assertIsNotEnabled().performTouchInput { click() }
        runOnIdle { assertEquals(1, calls) }
    }

    @Test
    fun previewCustomComponentReceivesBindingsAndChildren() = runComposeUiTest {
        setContent { ShadcnTheme {
            JSXPreview("<Notice tone={tone}>kept child</Notice>", bindings = mapOf("tone" to "info"),
                components = mapOf("Notice" to { element, children ->
                    BasicText("tone=${element.attributes["tone"]}")
                    children()
                }))
        } }
        onNodeWithText("tone=info").assertExists()
        onNodeWithText("kept child").assertExists()
    }

    @Test
    fun malformedPreviewReportsErrorAndUsesFallback() = runComposeUiTest {
        var failures = 0
        setContent { ShadcnTheme {
            JSXPreview("<div></span>", onError = { failures++ }, fallback = { BasicText("Could not preview") })
        } }
        onNodeWithText("Could not preview").assertExists()
        runOnIdle { assertEquals(1, failures) }
    }

    @Test
    fun markdownRendersBreaksAndUpdatesCustomComponentsWithoutChangingContent() = runComposeUiTest {
        var replacement by mutableStateOf("heading one")
        setContent { ShadcnTheme {
            val label = replacement
            Markdown("# Original\n\nline one\nline two", immediate = true,
                components = markdownComponents(heading1 = { BasicText(label) }))
        } }
        onNodeWithText("heading one").assertExists()
        onNodeWithText("line one\nline two").assertExists()
        runOnIdle { replacement = "heading two" }
        onNodeWithText("heading two").assertExists()
    }

    @Test
    fun imageWithoutDataKeepsAccessibleDescription() = runComposeUiTest {
        setContent { ShadcnTheme { Image(alt = "Generated landscape") } }
        onNodeWithContentDescription("Generated landscape").assertExists()
    }

    @Test
    fun markdownBlocksUnsafePlatformNavigationAndRefreshesExplicitCallbacks() = runComposeUiTest {
        val opened = mutableListOf<String>()
        var override by mutableStateOf<((String) -> Unit)?>(null)
        val platformHandler = object : UriHandler {
            override fun openUri(uri: String) { opened += "platform:$uri" }
        }
        setContent { ShadcnTheme { CompositionLocalProvider(LocalUriHandler provides platformHandler) {
            Markdown("Links", immediate = true, onLinkClick = override,
                components = markdownComponents(paragraph = {
                    val handler = LocalUriHandler.current
                    Column {
                        BasicText("Unsafe link", Modifier.clickable { handler.openUri("javascript:alert(1)") })
                        BasicText("Safe link", Modifier.clickable { handler.openUri("https://example.com") })
                    }
                }))
        } } }
        onNodeWithText("Unsafe link").performClick()
        runOnIdle { assertEquals(emptyList(), opened) }
        onNodeWithText("Safe link").performClick()
        runOnIdle {
            assertEquals(listOf("platform:https://example.com"), opened)
            override = { opened += "first:$it" }
        }
        onNodeWithText("Unsafe link").performClick()
        runOnIdle {
            assertEquals("first:javascript:alert(1)", opened.last())
            override = { opened += "latest:$it" }
        }
        onNodeWithText("Safe link").performClick()
        runOnIdle { assertEquals("latest:https://example.com", opened.last()) }
    }
}
