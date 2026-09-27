package com.github.jershell.shadcn.ui.components.demo

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.jershell.shadcn.components.button.*
import com.github.jershell.shadcn.components.promptkit.*
import com.github.jershell.shadcn.components.typography.*
import io.github.vinceglb.filekit.name
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.*

@Composable
fun DemoPromptChat() {
    val transport = remember { ChatTransport { request -> flow {
        val prompt = request.messages.last().text
        if (prompt.equals("error", ignoreCase = true)) {
            delay(200); emit(ChatEvent.Error("Example failure. Your draft stays available to edit or retry."))
        } else {
            emit(ChatEvent.ToolUpdate(ToolPart("tool-inspect", ToolState.InputAvailable,
                input = buildJsonObject { put("prompt", prompt) }, toolCallId = "inspect-${request.messages.size}")))
            delay(450)
            emit(ChatEvent.ToolUpdate(ToolPart("tool-inspect", ToolState.OutputAvailable,
                input = buildJsonObject { put("prompt", prompt) }, output = buildJsonObject { put("mode", "local demo") },
                toolCallId = "inspect-${request.messages.size}")))
            val response = "This is a **local demonstration**, with no AI service connected.\n\nYou asked: $prompt\n\n```kotlin\nval ui = \"Compose Multiplatform\"\n```\n\nTry **Stop** during streaming, or send `error` to test recovery."
            response.chunked(4).forEach { delay(30); emit(ChatEvent.TextDelta(it)) }
        }
    } } }
    val initial = remember { listOf(
        ChatMessage("welcome", ChatRole.Assistant, "Ask a question to exercise streaming, tool updates, copy, stop, and retry."),
    ) }
    val session = rememberChatSessionState(transport, initial)
    var selected by remember { mutableStateOf("Full chat app") }
    var history by remember { mutableStateOf(listOf(ChatHistoryEntry("first", "Demo conversation", "Today"))) }
    var active by remember { mutableStateOf("first") }
    val saved = remember { mutableMapOf<String, List<ChatMessage>>() }
    var next by remember { mutableIntStateOf(1) }
    var notice by remember { mutableStateOf("") }
    var search by remember { mutableStateOf(false) }
    val draft = rememberPromptInputState()
    val sample = remember { listOf(
        ChatMessage("a", ChatRole.User, "Can this render code and tools?"),
        ChatMessage("b", ChatRole.Assistant, "Yes. **Markdown**, code fences, avatars, and accessible actions.\n\n```kotlin\nprintln(\"Hello\")\n```"),
    ) }
    val variants = listOf("Full chat app", "Full conversation", "Actions", "Avatars", "Scroll to bottom",
        "Conversation + input", "Input actions", "Suggestions", "Autocomplete", "History", "Chatbot", "Tool calling")
    fun submit(value: String) { notice = "Submitted: $value" }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        H4("Agent chat")
        P("All ten prompt-kit blocks and both chat primitives. This gallery uses a local scripted transport; connect ChatTransport to your own backend.")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            variants.forEach { name -> Button(onClick = { selected = name }, size = ButtonSize.Sm,
                variant = if (selected == name) ButtonVariant.Secondary else ButtonVariant.Outline) { ButtonText(name) } }
        }
        val viewport = Modifier.fillMaxWidth().height(560.dp)
        when (selected) {
            "Full chat app" -> FullChatApp(session, history, active, onSelectConversation = { id ->
                saved[active] = session.messages; active = id; session.replaceMessages(saved[id] ?: initial)
            }, onNewConversation = {
                saved[active] = session.messages; active = "conversation-${++next}"
                history = history + ChatHistoryEntry(active, "Conversation $next", "Today")
                session.replaceMessages(initial)
            }, modifier = viewport, onRenameConversation = { id, title ->
                history = history.map { if (it.id == id) it.copy(title = title) else it }
            }, onDeleteConversation = { id ->
                history = history.filterNot { it.id == id }; saved.remove(id)
                if (active == id) { active = history.firstOrNull()?.id.orEmpty(); session.replaceMessages(saved[active] ?: initial) }
            })
            "Full conversation" -> FullConversation(sample, viewport) { _, positive -> notice = "Feedback: $positive" }
            "Actions" -> ConversationWithActions(sample, viewport) { _, positive -> notice = "Feedback: $positive" }
            "Avatars" -> ConversationWithAvatars(sample, viewport)
            "Scroll to bottom" -> ConversationWithScrollBottom((0..15).flatMap { n -> sample.map { it.copy(id = "${it.id}-$n") } }, viewport)
            "Conversation + input" -> ConversationPromptInput(session, viewport)
            "Input actions" -> PromptInputWithActions(::submit,
                { files -> notice = "Attached: ${files.joinToString { it.name }}" }, state = draft,
                searchEnabled = search, onSearchChange = { search = it })
            "Suggestions" -> PromptInputWithSuggestions(listOf(
                PromptSuggestionCategory("Code", listOf("Review this Kotlin function", "Explain coroutines")),
                PromptSuggestionCategory("Write", listOf("Draft a release note", "Summarize the changes")),
            ), ::submit, state = draft)
            "Autocomplete" -> PromptAutocompleteHighlight(listOf("How to write Kotlin", "How to debug coroutines", "How to build for iOS"), ::submit, state = draft)
            "History" -> SidebarChatHistory(history, active, { active = it; notice = "Selected $it" },
                { history = history + ChatHistoryEntry("new-${++next}", "Conversation $next") }, Modifier.height(400.dp),
                onRename = { id, title -> history = history.map { if (it.id == id) it.copy(title = title) else it } },
                onDelete = { id -> history = history.filterNot { it.id == id } })
            "Chatbot" -> Chatbot(session, viewport)
            "Tool calling" -> ToolCallingChatbot(session, viewport)
        }
        if (notice.isNotEmpty()) P(notice)
    }
}
