@file:Suppress("DEPRECATION")
package com.github.jershell.shadcn.components.promptkit

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.github.jershell.shadcn.components.button.*
import com.github.jershell.shadcn.components.dialog.*
import com.github.jershell.shadcn.theme.*

/** One message, including ordered text/tool parts, optional avatar and working copy/feedback actions. */
@Composable
fun MessageComponent(
    message: ChatMessage,
    modifier: Modifier = Modifier,
    showAvatar: Boolean = false,
    showActions: Boolean = false,
    onFeedback: ((ChatMessage, Boolean) -> Unit)? = null,
) {
    val clipboard = LocalClipboardManager.current
    val colors = resolvePromptKitColors()
    var copied by remember(message.id, message.text) { mutableStateOf(false) }
    var feedback by remember(message.id) { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(copied) { if (copied) { kotlinx.coroutines.delay(1500); copied = false } }
    Row(modifier.fillMaxWidth(), horizontalArrangement =
        if (message.role == ChatRole.User) Arrangement.End else Arrangement.Start) {
        Message(Modifier.widthIn(max = 720.dp).semantics { isTraversalGroup = true }) {
            if (showAvatar) MessageAvatar(message.avatarUrl, message.name, fallback = message.name.take(2))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TwDimensions.paddingPxToken2),
                horizontalAlignment = if (message.role == ChatRole.User) Alignment.End else Alignment.Start) {
                BasicText(message.name, style = TypographyStyles.textXsMedium.copy(color = colors.mutedForeground))
                message.parts.forEach { part ->
                    when (part) {
                        is ChatMessagePart.Text -> MessageContent(part.text, markdown = message.role != ChatRole.User)
                        is ChatMessagePart.ToolCall -> Tool(part.tool)
                    }
                }
                if (showActions && message.role == ChatRole.Assistant) MessageActions {
                    MessageAction(if (copied) "Copied" else "Copy response", onClick = {
                        clipboard.setText(AnnotatedString(message.text)); copied = true
                    }) { ButtonText(if (copied) "Copied" else "Copy") }
                    if (onFeedback != null) {
                        MessageAction("Helpful response", onClick = { feedback = true; onFeedback(message, true) },
                            modifier = Modifier.semantics { selected = feedback == true }) { ButtonText("Helpful") }
                        MessageAction("Unhelpful response", onClick = { feedback = false; onFeedback(message, false) },
                            modifier = Modifier.semantics { selected = feedback == false }) { ButtonText("Unhelpful") }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConversationView(
    messages: List<ChatMessage>, modifier: Modifier, avatars: Boolean, actions: Boolean,
    scrollButton: Boolean, onFeedback: ((ChatMessage, Boolean) -> Unit)? = null,
) {
    ChatContainerRoot(modifier) {
        ChatContainerContent(Modifier.padding(TwDimensions.paddingPxToken4)) {
            messages.forEach { message -> key(message.id) {
                MessageComponent(message, showAvatar = avatars, showActions = actions, onFeedback = onFeedback)
            } }
            ChatContainerScrollAnchor()
        }
        if (scrollButton) ScrollButton(Modifier.align(Alignment.BottomCenter).padding(TwDimensions.paddingPxToken2))
    }
}

@Composable
fun ConversationWithActions(messages: List<ChatMessage>, modifier: Modifier = Modifier,
    onFeedback: ((ChatMessage, Boolean) -> Unit)? = null) =
    ConversationView(messages, modifier, avatars = false, actions = true, scrollButton = false, onFeedback)

@Composable
fun ConversationWithAvatars(messages: List<ChatMessage>, modifier: Modifier = Modifier) =
    ConversationView(messages, modifier, avatars = true, actions = false, scrollButton = false)

@Composable
fun ConversationWithScrollBottom(messages: List<ChatMessage>, modifier: Modifier = Modifier) =
    ConversationView(messages, modifier, avatars = false, actions = false, scrollButton = true)

@Composable
fun FullConversation(messages: List<ChatMessage>, modifier: Modifier = Modifier,
    onFeedback: ((ChatMessage, Boolean) -> Unit)? = null) =
    ConversationView(messages, modifier, avatars = true, actions = true, scrollButton = true, onFeedback)

/** Complete send/stream/stop/retry composition. The caller supplies a transport through [state]. */
@Composable
fun ConversationPromptInput(
    state: ChatSessionState,
    modifier: Modifier = Modifier,
    draft: PromptInputState = rememberPromptInputState(),
    placeholder: String = "Ask anything…",
    onFeedback: ((ChatMessage, Boolean) -> Unit)? = null,
    composerActions: @Composable RowScope.() -> Unit = {},
) {
    val colors = resolvePromptKitColors()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(TwDimensions.paddingPxToken3)) {
        FullConversation(state.messages, Modifier.weight(1f).fillMaxWidth(), onFeedback)
        if (state.isLoading) BasicText("Responding…", Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            style = TypographyStyles.textSmRegular.copy(color = colors.mutedForeground))
        state.error?.let { error ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TwDimensions.paddingPxToken2)) {
                BasicText(error, Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Assertive },
                    style = TypographyStyles.textSmRegular.copy(color = colors.foreground))
                Button(onClick = { state.retry() }, size = ButtonSize.Sm, variant = ButtonVariant.Outline) {
                    ButtonText("Retry")
                }
            }
        }
        PromptInput(draft, isLoading = state.isLoading, onSubmit = { submitted ->
            state.send(submitted) { if (draft.value == submitted) draft.clear() }
        }) {
            PromptInputTextarea(placeholder = placeholder)
            PromptInputActions {
                composerActions()
                Spacer(Modifier.weight(1f))
                PromptInputSubmit(onStop = { state.stop() })
            }
        }
    }
}

/** Provider-neutral version of prompt-kit's chatbot primitive. */
@Composable
fun Chatbot(state: ChatSessionState, modifier: Modifier = Modifier,
    draft: PromptInputState = rememberPromptInputState()) = ConversationPromptInput(state, modifier, draft)

/** Tool events supplied by the backend are retained in message order and updated by toolCallId. */
@Composable
fun ToolCallingChatbot(state: ChatSessionState, modifier: Modifier = Modifier,
    draft: PromptInputState = rememberPromptInputState()) = ConversationPromptInput(state, modifier, draft)

@Immutable
data class ChatHistoryEntry(val id: String, val title: String, val group: String = "Recent", val preview: String = "")

@Composable
fun SidebarChatHistory(
    entries: List<ChatHistoryEntry>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    onNewConversation: () -> Unit,
    modifier: Modifier = Modifier,
    onRename: ((String, String) -> Unit)? = null,
    onDelete: ((String) -> Unit)? = null,
) {
    val colors = resolvePromptKitColors()
    var renameEntry by remember { mutableStateOf<ChatHistoryEntry?>(null) }
    val renameDraft = rememberPromptInputState()
    Column(modifier.background(colors.muted).padding(TwDimensions.paddingPxToken3)
        .verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(TwDimensions.paddingPxToken3)) {
        Button(onClick = onNewConversation, modifier = Modifier.fillMaxWidth(), variant = ButtonVariant.Outline) {
            ButtonText("New conversation")
        }
        entries.groupBy { it.group }.forEach { (group, conversations) ->
            BasicText(group, Modifier.semantics { heading() },
                style = TypographyStyles.textXsMedium.copy(color = colors.mutedForeground))
            conversations.forEach { entry -> key(entry.id) {
                Column {
                    Button(onClick = { onSelect(entry.id) }, modifier = Modifier.fillMaxWidth()
                        .semantics { selected = selectedId == entry.id },
                        variant = if (selectedId == entry.id) ButtonVariant.Secondary else ButtonVariant.Ghost) {
                        ButtonText(entry.title)
                    }
                    if (entry.preview.isNotEmpty()) BasicText(entry.preview, maxLines = 2,
                        style = TypographyStyles.textXsRegular.copy(color = colors.mutedForeground))
                    Row {
                        if (onRename != null) Button(onClick = { renameDraft.value = entry.title; renameEntry = entry },
                            size = ButtonSize.Xs, variant = ButtonVariant.Ghost,
                            modifier = Modifier.semantics { contentDescription = "Rename ${entry.title}" }) { ButtonText("Rename") }
                        if (onDelete != null) Button(onClick = { onDelete(entry.id) }, size = ButtonSize.Xs,
                            variant = ButtonVariant.Ghost,
                            modifier = Modifier.semantics { contentDescription = "Delete ${entry.title}" }) { ButtonText("Delete") }
                    }
                }
            } }
        }
    }
    Dialog(open = renameEntry != null, onOpenChange = { if (!it) renameEntry = null }) {
        DialogTitle("Rename conversation")
        PromptInput(renameDraft, onSubmit = { value ->
            renameEntry?.let { onRename?.invoke(it.id, value.trim()) }; renameEntry = null
        }) {
            PromptInputTextarea(label = "Conversation title", minLines = 1, maxLines = 1)
            PromptInputSubmit()
        }
    }
}

/** Responsive full application block. History is a modal on narrow windows and a sidebar on wide ones. */
@Composable
fun FullChatApp(
    state: ChatSessionState,
    history: List<ChatHistoryEntry>,
    selectedId: String?,
    onSelectConversation: (String) -> Unit,
    onNewConversation: () -> Unit,
    modifier: Modifier = Modifier,
    onRenameConversation: ((String, String) -> Unit)? = null,
    onDeleteConversation: ((String) -> Unit)? = null,
) {
    var historyOpen by remember { mutableStateOf(false) }
    BoxWithConstraints(modifier) {
        val wide = maxWidth >= 760.dp
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(TwDimensions.paddingPxToken4)) {
            if (wide) SidebarChatHistory(history, selectedId, onSelectConversation, onNewConversation,
                Modifier.width(260.dp).fillMaxHeight(), onRenameConversation, onDeleteConversation)
            Column(Modifier.weight(1f).fillMaxHeight()) {
                if (!wide) Button(onClick = { historyOpen = true }, variant = ButtonVariant.Outline) {
                    ButtonText("Conversation history")
                }
                // Draft, selection and scroll-follow intent belong to this conversation.
                key(selectedId) { ConversationPromptInput(state, Modifier.weight(1f).fillMaxWidth()) }
            }
        }
        Dialog(open = !wide && historyOpen, onOpenChange = { historyOpen = it }) {
            DialogTitle("Conversation history")
            SidebarChatHistory(history, selectedId, { onSelectConversation(it); historyOpen = false },
                { onNewConversation(); historyOpen = false }, Modifier.heightIn(max = 520.dp),
                onRenameConversation, onDeleteConversation)
        }
    }
}
