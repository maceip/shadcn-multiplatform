package com.github.jershell.shadcn.components.modal

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.composeunstyled.ModalHost
import com.composeunstyled.PortalHost

/** True only for a layer covered by a Web modal. HTML interop must hide/inert its native element. */
val LocalShadcnModalLayerBlocked = compositionLocalOf { false }

private val LocalCanvasModalStack = staticCompositionLocalOf<CanvasModalStack?> { null }
private val LocalCanvasModalRegistration = staticCompositionLocalOf<CanvasModalRegistration?> { null }

private class CanvasModalRegistration(context: CompositionLocalContext, val content: State<@Composable () -> Unit>) {
    val focus = FocusRequester()
    var visible by mutableStateOf(false)
    var context by mutableStateOf(context)
    var contentMounted = false
    var onContentDisposed: () -> Unit = {}
}

private class CanvasModalStack {
    val backgroundFocus = FocusRequester()
    val entries = mutableStateListOf<CanvasModalRegistration>()

    fun register(registration: CanvasModalRegistration) {
        if (registration !in entries) {
            // Save the actual launcher, including launches from another modal.
            (entries.lastOrNull()?.focus ?: backgroundFocus).saveFocusedChild()
            entries.add(registration)
        }
    }

    fun remove(registration: CanvasModalRegistration) {
        entries.remove(registration)
    }
}

/** Native targets keep platform dialogs; Web uses one semantics owner until upstream fixes disposal. */
@Composable
internal expect fun ShadcnPlatformModalHost(content: @Composable () -> Unit)

/** Public-API containment for Compose Web's lost semantics owner; also exercised by common UI tests. */
@Composable
internal fun CanvasModalHost(content: @Composable () -> Unit) {
    val stack = remember { CanvasModalStack() }
    CompositionLocalProvider(LocalCanvasModalStack provides stack) {
        Box(Modifier.fillMaxSize()) {
            CanvasModalFrame(stack.backgroundFocus, blocked = stack.entries.isNotEmpty(), modal = false) {
                // Include non-modal portals in the background layer, so tooltips/popovers are hidden too.
                PortalHost(content = content)
            }
            stack.entries.forEach { entry -> key(entry) {
                CanvasModalEntryView(entry, blocked = entry !== stack.entries.lastOrNull())
            } }
        }
    }
}

@Composable
private fun CanvasModalEntryView(entry: CanvasModalRegistration, blocked: Boolean) {
    CompositionLocalProvider(entry.context) {
        CanvasModalFrame(entry.focus, blocked, modal = true) {
            CompositionLocalProvider(LocalCanvasModalRegistration provides entry) {
                // ModalHost selects Unstyled's in-scene implementation. Each layer owns its portal
                // host, and never creates another ComposeScene semantics owner.
                ModalHost(Modifier.fillMaxSize()) { entry.content.value() }
            }
        }
    }
    LaunchedEffect(entry.visible) {
        if (!entry.visible) {
            // Opening and closing before attachment must not leave an empty blocking layer.
            // Mounted content owns its actual exit lifetime through ShadcnModalContentLifecycle.
            withFrameNanos { }
            if (!entry.contentMounted) entry.onContentDisposed()
        }
    }
}

@Composable
private fun CanvasModalFrame(focus: FocusRequester, blocked: Boolean, modal: Boolean,
    content: @Composable () -> Unit) {
    val focusManager = LocalFocusManager.current
    var wasCovered by remember { mutableStateOf(false) }
    LaunchedEffect(blocked) {
        if (blocked) wasCovered = true
        else if (wasCovered) {
            // Removal and focus properties must be applied before restoring the launcher.
            withFrameNanos { }
            if (!focus.restoreFocusedChild()) focus.requestFocus(FocusDirection.Down)
            wasCovered = false
        }
    }
    val semantics = if (blocked) Modifier.clearAndSetSemantics { } else Modifier
    CompositionLocalProvider(LocalShadcnModalLayerBlocked provides blocked) {
        Box(Modifier.fillMaxSize().then(semantics).focusRequester(focus)
            .focusProperties {
                onEnter = { if (blocked) cancelFocusChange() }
                onExit = { if (modal && !blocked) cancelFocusChange() }
            }
            .onPreviewKeyEvent { event ->
                if (modal && !blocked && event.key == Key.Tab && event.type == KeyEventType.KeyDown) {
                    val direction = if (event.isShiftPressed) FocusDirection.Previous else FocusDirection.Next
                    if (!focusManager.moveFocus(direction)) {
                        // Restart one-dimensional traversal, which reaches the last descendant on
                        // Shift+Tab. Geometric group entry can stop at a focusable panel ancestor.
                        // Covered layers reject entry, so the next target stays in this modal.
                        focusManager.clearFocus(force = true)
                        focusManager.moveFocus(direction)
                    }
                    true
                } else false
            }.focusGroup()) { content() }
    }
}

/** Hoist a modal to the Web stack while retaining its exit animation and caller's composition locals. */
@Composable
internal fun ShadcnModalLayer(visible: Boolean, content: @Composable () -> Unit) {
    val stack = LocalCanvasModalStack.current
    if (stack == null) { content(); return }
    val context = currentCompositionLocalContext
    val currentContent = rememberUpdatedState(content)
    val registration = remember(stack) { CanvasModalRegistration(context, currentContent) }
    var retained by remember { mutableStateOf(false) }
    SideEffect {
        registration.visible = visible
        registration.context = context
        registration.onContentDisposed = {
            if (!registration.visible) {
                retained = false
                stack.remove(registration)
            }
        }
        if (visible || retained) {
            retained = true
            // Content updates through its stable State holder; only membership changes invalidate
            // the host/background, so streaming in a modal does not rebuild the stack each token.
            stack.register(registration)
        }
    }
    DisposableEffect(stack, registration) {
        onDispose { stack.remove(registration) }
    }
}

/** Attach inside the underlying modal content, whose disposal signals the end of its exit motion. */
@Composable
internal fun ShadcnModalContentLifecycle() {
    val registration = LocalCanvasModalRegistration.current ?: return
    DisposableEffect(registration) {
        registration.contentMounted = true
        onDispose {
            registration.contentMounted = false
            registration.onContentDisposed()
        }
    }
}
