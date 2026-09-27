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
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import com.github.jershell.shadcn.theme.ShadcnTheme
import com.mikepenz.markdown.compose.components.markdownComponents
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
    fun markdownTasksRenderOneReadonlyCheckboxBesideEachFormattedLabel() = runComposeUiTest {
        setContent { ShadcnTheme {
            Markdown("- [x] **Completed**\n- [ ] Pending\n- [X] Uppercase\n- Ordinary item", immediate = true)
        } }
        val checkboxes = onAllNodes(isToggleable())
        checkboxes.assertCountEquals(3)
        checkboxes[0].assertIsOn().assertIsNotEnabled()
        checkboxes[1].assertIsOff().assertIsNotEnabled()
        checkboxes[2].assertIsOn().assertIsNotEnabled()
        listOf("Completed", "Pending", "Uppercase").forEachIndexed { index, label ->
            onAllNodesWithText(label).assertCountEquals(1)
            onNodeWithContentDescription(label).assertIsNotEnabled()
            val indicator = checkboxes[index].fetchSemanticsNode().boundsInRoot
            val text = onNodeWithText(label).fetchSemanticsNode().boundsInRoot
            assertTrue(indicator.right <= text.left, "$label must sit beside its checkbox")
            assertTrue(indicator.top < text.bottom && text.top < indicator.bottom,
                "$label and its checkbox must occupy the same row")
        }
        onNodeWithText("Ordinary item").assertExists()
        listOf("[x]", "[X]", "[ ]", "-").forEach { onAllNodesWithText(it).assertCountEquals(0) }
    }

    @Test
    fun markdownTaskAccessibleNameUsesFormattedLabelAndUpdatesWithStreamingContent() = runComposeUiTest {
        var source by mutableStateOf("- [ ] **Bold** [linked](https://example.com) `inline`")
        setContent { ShadcnTheme { Markdown(source, immediate = true) } }
        val actualDescription = onAllNodes(isToggleable()).fetchSemanticsNodes().single()
            .config[SemanticsProperties.ContentDescription]
        assertEquals(listOf("Bold linked inline"), actualDescription,
            "Task accessible name contains formatting padding; code points: " +
                actualDescription.joinToString { label -> label.map { "U+" + it.code.toString(16).padStart(4, '0') }.joinToString(" ") })
        onNodeWithContentDescription("Bold linked inline").assertIsOff().assertIsNotEnabled()
        runOnIdle { source = "- [x] **Finished**" }
        onNodeWithContentDescription("Bold linked inline").assertDoesNotExist()
        onNodeWithContentDescription("Finished").assertIsOn().assertIsNotEnabled()
        onAllNodesWithText("Finished").assertCountEquals(1)
    }

    @Test
    fun markdownListFallbackPreservesNestedItemsOrderedStartsAndLiteralHtml() = runComposeUiTest {
        setContent { ShadcnTheme {
            Markdown("- [x] Parent\n  - Nested label\n\n3. Third\n4. Fourth\n\n<div>literal HTML</div>", immediate = true)
        } }
        onAllNodes(isToggleable()).assertCountEquals(1)
        listOf("Parent", "Nested label", "Third", "Fourth", "<div>literal HTML</div>")
            .forEach { onNodeWithText(it).assertExists() }
        listOf("3. ", "4. ").forEach { onNodeWithText(it).assertExists() }
        onAllNodesWithText("[x]").assertCountEquals(0)
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
