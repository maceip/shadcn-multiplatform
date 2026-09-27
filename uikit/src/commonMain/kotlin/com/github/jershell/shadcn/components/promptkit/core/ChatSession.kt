package com.github.jershell.shadcn.components.promptkit

import androidx.compose.runtime.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow

enum class ChatRole { User, Assistant, System }

sealed interface ChatMessagePart {
    data class Text(val text: String) : ChatMessagePart
    data class ToolCall(val tool: ToolPart) : ChatMessagePart
}

@Immutable
data class ChatMessage(
    val id: String,
    val role: ChatRole,
    val parts: List<ChatMessagePart>,
    val name: String = if (role == ChatRole.User) "You" else if (role == ChatRole.Assistant) "Assistant" else "System",
    val avatarUrl: String? = null,
) {
    constructor(id: String, role: ChatRole, content: String) : this(id, role, listOf(ChatMessagePart.Text(content)))
    val text: String get() = parts.filterIsInstance<ChatMessagePart.Text>().joinToString("") { it.text }
}

data class ChatRequest(val messages: List<ChatMessage>)

/** Provider adapters convert SSE/WebSocket/HTTP output to ordered events. No provider or secret is built in. */
sealed interface ChatEvent {
    data class TextDelta(val delta: String) : ChatEvent
    data class ToolUpdate(val tool: ToolPart) : ChatEvent
    data class Error(val message: String) : ChatEvent
}

fun interface ChatTransport {
    fun stream(request: ChatRequest): Flow<ChatEvent>
}

/**
 * One in-flight turn per session. Cancellation, retry and error are explicit; dispose cancels the turn.
 * Call on the UI dispatcher. Transport collection runs in [scope], and is cancelled with it.
 */
@Stable
class ChatSessionState(
    private val transport: ChatTransport,
    private val scope: CoroutineScope,
    initialMessages: List<ChatMessage> = emptyList(),
) {
    var messages by mutableStateOf(initialMessages.toList())
        private set
    var isLoading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var wasStopped by mutableStateOf(false)
        private set
    private var sequence = 0L
    private var generation = 0L
    private var running: Job? = null
    private var retryRequest: ChatRequest? = null
    private var replyId: String? = null
    private var retryAccepted: (() -> Unit)? = null
    private var disposed = false

    init { require(initialMessages.map { it.id }.distinct().size == initialMessages.size) { "Message IDs must be unique" } }

    private fun nextId(): String {
        var id: String
        do { id = "prompt-kit-${++sequence}" } while (messages.any { it.id == id })
        return id
    }

    /** Returns false for blank input or a busy session. [onAccepted] runs on the first event/successful empty reply. */
    fun send(text: String, onAccepted: () -> Unit = {}): Boolean {
        if (disposed || !scope.isActive || isLoading || text.isBlank()) return false
        val userMessage = ChatMessage(nextId(), ChatRole.User, text)
        messages = messages + userMessage
        start(ChatRequest(messages), onAccepted)
        return true
    }

    fun retry(): Boolean {
        if (disposed || !scope.isActive || isLoading) return false
        val request = retryRequest ?: return false
        messages = request.messages
        start(request, retryAccepted ?: {})
        return true
    }

    fun stop() {
        if (!isLoading) return
        ++generation
        running?.cancel()
        running = null
        isLoading = false
        wasStopped = true
    }

    fun replaceMessages(messages: List<ChatMessage>) {
        require(messages.map { it.id }.distinct().size == messages.size)
        stop()
        this.messages = messages.toList()
        error = null
        retryRequest = null
        retryAccepted = null
        replyId = null
        wasStopped = false
    }

    fun dispose() { stop(); disposed = true; retryAccepted = null }

    private fun start(request: ChatRequest, onAccepted: () -> Unit) {
        val token = ++generation
        val assistantId = nextId()
        replyId = assistantId
        retryRequest = request
        retryAccepted = onAccepted
        messages = request.messages + ChatMessage(assistantId, ChatRole.Assistant, emptyList())
        error = null
        wasStopped = false
        isLoading = true
        val task = scope.launch(start = CoroutineStart.LAZY) {
            var accepted = false
            fun accept() {
                if (!accepted) { accepted = true; retryAccepted = {}; onAccepted() }
            }
            try {
                transport.stream(request).collect { event ->
                    ensureActive()
                    if (token != generation) return@collect
                    when (event) {
                        is ChatEvent.Error -> throw ChatTransportException(event.message)
                        is ChatEvent.TextDelta -> {
                            updateReply(assistantId) { appendText(it, event.delta) }
                            accept()
                        }
                        is ChatEvent.ToolUpdate -> {
                            require(!event.tool.toolCallId.isNullOrBlank()) { "Tool events need a stable toolCallId" }
                            updateReply(assistantId) { updateTool(it, event.tool) }
                            accept()
                        }
                    }
                }
                if (token == generation) {
                    accept()
                    if (token == generation) { retryRequest = null; retryAccepted = null }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (token == generation) error = failure.message ?: "The response could not be completed."
            } finally {
                if (token == generation) { isLoading = false; running = null }
            }
        }
        running = task
        task.invokeOnCompletion {
            // Also covers cancellation before the coroutine body starts.
            if (token == generation) { isLoading = false; if (running === task) running = null }
        }
        task.start()
    }

    private fun updateReply(id: String, change: (List<ChatMessagePart>) -> List<ChatMessagePart>) {
        messages = messages.map { if (it.id == id) it.copy(parts = change(it.parts)) else it }
    }
}

private class ChatTransportException(message: String) : Exception(message)

internal fun appendText(parts: List<ChatMessagePart>, delta: String): List<ChatMessagePart> {
    if (delta.isEmpty()) return parts
    val last = parts.lastOrNull()
    return if (last is ChatMessagePart.Text) parts.dropLast(1) + ChatMessagePart.Text(last.text + delta)
    else parts + ChatMessagePart.Text(delta)
}

internal fun updateTool(parts: List<ChatMessagePart>, tool: ToolPart): List<ChatMessagePart> {
    val index = parts.indexOfFirst { it is ChatMessagePart.ToolCall && it.tool.toolCallId == tool.toolCallId }
    return if (index < 0) parts + ChatMessagePart.ToolCall(tool)
    else parts.toMutableList().apply { this[index] = ChatMessagePart.ToolCall(tool) }
}

@Composable
fun rememberChatSessionState(transport: ChatTransport, initialMessages: List<ChatMessage> = emptyList()): ChatSessionState {
    val scope = rememberCoroutineScope()
    val state = remember(transport) { ChatSessionState(transport, scope, initialMessages) }
    DisposableEffect(state) { onDispose { state.dispose() } }
    return state
}
