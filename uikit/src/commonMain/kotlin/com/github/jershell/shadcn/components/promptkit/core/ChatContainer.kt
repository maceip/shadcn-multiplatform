package com.github.jershell.shadcn.components.promptkit

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.*
import androidx.compose.ui.semantics.*
import com.github.jershell.shadcn.components.button.*
import com.github.jershell.shadcn.theme.TwDimensions
import com.github.jershell.shadcn.motion.ShadcnVisibility
import com.github.jershell.shadcn.motion.LocalShadcnMotionEnabled
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** Tracks intent separately from scroll extent so appending tokens does not steal the reader's position. */
@Stable
class ChatContainerState internal constructor(val scrollState: ScrollState) {
    var followsBottom by mutableStateOf(true)
        private set
    val isAtBottom: Boolean get() = scrollState.value >= scrollState.maxValue - 2
    fun stopFollowing() { followsBottom = false }
    internal fun reachedBottom() { followsBottom = true }
    suspend fun scrollToBottom(animated: Boolean = true) {
        followsBottom = true
        if (animated) scrollState.animateScrollTo(scrollState.maxValue)
        else scrollState.scrollTo(scrollState.maxValue)
    }
}

@Composable
fun rememberChatContainerState(): ChatContainerState {
    val scroll = rememberScrollState()
    return remember(scroll) { ChatContainerState(scroll) }
}

private val LocalChatContainer = staticCompositionLocalOf<ChatContainerState?> { null }
@Composable private fun currentChatContainer(): ChatContainerState =
    checkNotNull(LocalChatContainer.current) { "Place this component inside ChatContainerRoot." }

/** A bounded chat viewport. Give the root a height or weight; content owns its vertical scroll. */
@Composable
fun ChatContainerRoot(
    modifier: Modifier = Modifier,
    state: ChatContainerState = rememberChatContainerState(),
    label: String = "Conversation",
    content: @Composable BoxScope.() -> Unit,
) {
    LaunchedEffect(state) {
        snapshotFlow { state.scrollState.maxValue }.distinctUntilChanged().collect {
            if (state.followsBottom) state.scrollToBottom(animated = false)
        }
    }
    LaunchedEffect(state) {
        var previous = state.scrollState.value
        snapshotFlow { state.scrollState.value }.distinctUntilChanged().collect {
            if (it < previous && state.scrollState.isScrollInProgress && !state.isAtBottom) state.stopFollowing()
            previous = it
            if (state.isAtBottom) state.reachedBottom()
        }
    }
    val nestedScroll = remember(state) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y > 0) state.stopFollowing()
                return Offset.Zero
            }
        }
    }
    CompositionLocalProvider(LocalChatContainer provides state) {
        Box(modifier.nestedScroll(nestedScroll).semantics {
            contentDescription = label
            isTraversalGroup = true
        }, content = content)
    }
}

@Composable
fun ChatContainerContent(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val state = currentChatContainer()
    Column(modifier.fillMaxSize().verticalScroll(state.scrollState),
        verticalArrangement = Arrangement.spacedBy(TwDimensions.paddingPxToken4), content = content)
}

@Composable
fun ChatContainerScrollAnchor(modifier: Modifier = Modifier) {
    Spacer(modifier.fillMaxWidth().height(TwDimensions.paddingPxToken1).clearAndSetSemantics {})
}

@Composable
fun ScrollButton(modifier: Modifier = Modifier, label: String = "Scroll to latest message") {
    val state = currentChatContainer()
    val scope = rememberCoroutineScope()
    val motion = LocalShadcnMotionEnabled.current
    ShadcnVisibility(visible = !state.isAtBottom, modifier = modifier) {
        Button(onClick = { scope.launch { state.scrollToBottom(animated = motion) } }, variant = ButtonVariant.Outline,
            size = ButtonSize.Sm, modifier = Modifier.semantics { contentDescription = label }) {
            ButtonText("↓")
        }
    }
}
