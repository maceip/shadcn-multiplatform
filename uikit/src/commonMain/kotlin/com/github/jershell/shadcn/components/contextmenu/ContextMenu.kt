package com.github.jershell.shadcn.components.contextmenu
import com.github.jershell.shadcn.motion.shadcnMenuAppearance

import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.round
import com.composeunstyled.AnchorAlignment
import com.composeunstyled.AnchorSide
import com.composeunstyled.Modal
import com.composeunstyled.rememberModalState
import com.github.jershell.shadcn.anchored.FlipAnchoredFloatingContent
import com.github.jershell.shadcn.components.dropdownmenu.DropdownMenuEntryScope
import com.github.jershell.shadcn.components.dropdownmenu.DropdownMenuEntryScopeInstance
import com.github.jershell.shadcn.components.dropdownmenu.LocalDropdownMenuClose
import com.github.jershell.shadcn.components.dropdownmenu.MenuKeyboardNavigation
import com.github.jershell.shadcn.components.dropdownmenu.menuPanelStyle
import com.github.jershell.shadcn.theme.BaseTokens
import kotlin.math.roundToInt

/**
 * A context menu styled after the shadcn/ui Context Menu. The menu opens at the
 * pointer position on a right click (secondary button release). A right click while
 * the menu is open re-opens it at the new pointer position, dismissing the
 * previously shown menu. A primary click outside the panel or Escape closes the menu.
 *
 * The menu rows of the Dropdown Menu component are reused inside the panel:
 * [com.github.jershell.shadcn.components.dropdownmenu.DropdownMenuItem],
 * [com.github.jershell.shadcn.components.dropdownmenu.DropdownMenuCheckboxItem],
 * [com.github.jershell.shadcn.components.dropdownmenu.DropdownMenuRadioGroup] etc.
 *
 * Touch and stylus long-presses open the same menu and consume the opening gesture.
 *
 * @param enabled Whether the area reacts to right clicks and long presses.
 * @param modifier Modifier applied to the trigger area container.
 * @param menu Panel content; use the Dropdown Menu row composables.
 * @param content The area the context menu opens for.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun ContextMenu(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    menu: @Composable DropdownMenuEntryScope.() -> Unit,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    var open by remember { mutableStateOf(false) }
    var cursorInAnchor by remember { mutableStateOf(Offset.Zero) }
    var anchorOrigin by remember { mutableStateOf(IntOffset.Zero) }
    var anchorSize by remember { mutableStateOf(IntSize.Zero) }
    val close: () -> Unit = remember { { open = false } }

    LaunchedEffect(enabled) { if (!enabled) open = false }

    val modalState = rememberModalState()
    LaunchedEffect(open) { modalState.transitionState.targetState = open }

    CompositionLocalProvider(LocalDropdownMenuClose provides close) {
        FlipAnchoredFloatingContent(
            layer = { panelContent ->
                Modal(state = modalState, onKeyEvent = { event ->
                    if (event.type == KeyEventType.KeyDown && (event.key == Key.Escape || event.key == Key.Back)) {
                        close()
                        true
                    } else false
                }) {
                    if (open) {
                        ContextMenuScrim(
                            onSecondaryClick = { positionInWindow ->
                                val anchor = anchorOrigin
                                cursorInAnchor = Offset(
                                    x = positionInWindow.x - anchor.x,
                                    y = positionInWindow.y - anchor.y,
                                )
                            },
                            onPrimaryClick = close,
                        )
                        panelContent()
                    }
                }
            },
            content = {
                ContextMenuPanel(
                    onDismiss = close,
                    content = menu,
                )
            },
            side = AnchorSide.Bottom,
            alignment = AnchorAlignment.Start,
            sideOffset = with(density) { (cursorInAnchor.y - anchorSize.height).toDp() },
            alignmentOffset = with(density) { cursorInAnchor.x.toDp() },
            anchor = {
                Box(
                    modifier = modifier
                        .onGloballyPositioned {
                            val position = it.positionInWindow().round()
                            anchorOrigin = IntOffset(position.x, position.y)
                            anchorSize = it.size
                        }
                        .pointerInput(enabled) {
                            if (!enabled) return@pointerInput
                            awaitEachGesture {
                                // awaitFirstDown filters non-primary mouse buttons on
                                // desktop, so detect the initial transition explicitly.
                                var press = awaitPointerEvent(PointerEventPass.Initial)
                                while (press.changes.none { it.changedToDownIgnoreConsumed() }) {
                                    press = awaitPointerEvent(PointerEventPass.Initial)
                                }
                                val down = press.changes.first { it.changedToDownIgnoreConsumed() }
                                if (press.buttons.isSecondaryPressed) {
                                    val position = down.position
                                    down.consume()
                                    do {
                                        val event = awaitPointerEvent()
                                        event.changes.forEach { it.consume() }
                                    } while (event.changes.any { it.pressed })
                                    cursorInAnchor = position
                                    open = true
                                } else if (down.type != PointerType.Mouse) {
                                    val longPress = awaitLongPressOrCancellation(down.id)
                                        ?: return@awaitEachGesture
                                    cursorInAnchor = longPress.position
                                    open = true
                                    // The opening finger must not activate an item or dismiss
                                    // the newly composed scrim when it is released.
                                    do {
                                        val event = awaitPointerEvent()
                                        event.changes.forEach { it.consume() }
                                    } while (event.changes.any { it.pressed })
                                }
                            }
                        },
                ) {
                    content()
                }
            },
        )
    }
}

/**
 * The context menu panel: styled like [com.github.jershell.shadcn.components.dropdownmenu.DropdownMenuContent],
 * focusable, with ArrowUp/ArrowDown row traversal; Escape closes the menu.
 */
@Composable
private fun ContextMenuPanel(
    onDismiss: () -> Unit,
    content: @Composable DropdownMenuEntryScope.() -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    MenuKeyboardNavigation { navigation ->
        Column(
            modifier = Modifier
                .menuPanelStyle(minWidth = BaseTokens.token128)
                .shadcnMenuAppearance()
                .then(navigation)
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && (event.key == Key.Escape || event.key == Key.Tab)) {
                        onDismiss()
                        true
                    } else false
                }
                .focusRequester(focusRequester)
                .focusable(),
            content = { with(DropdownMenuEntryScopeInstance) { content() } },
        )
    }
}

/**
 * Full-window gesture layer of an open context menu: a secondary click re-opens the
 * menu at the new pointer position, a primary click closes it. Only complete
 * press→release gestures count; hover moves are ignored.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun ContextMenuScrim(
    onSecondaryClick: (Offset) -> Unit,
    onPrimaryClick: () -> Unit,
) {
    val currentSecondaryClick by rememberUpdatedState(onSecondaryClick)
    val currentPrimaryClick by rememberUpdatedState(onPrimaryClick)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitEachGesture {
                    // Wait for a press; ignore hover/scroll events.
                    var isSecondaryPress = false
                    while (true) {
                        val down = awaitPointerEvent(PointerEventPass.Initial)
                        if (down.changes.any { it.changedToDownIgnoreConsumed() }) {
                            isSecondaryPress = down.buttons.isSecondaryPressed
                            break
                        }
                    }
                    // Wait for the release, then resolve the gesture.
                    while (true) {
                        val release = awaitPointerEvent(PointerEventPass.Initial)
                        if (release.changes.any { it.pressed }) continue
                        val isSecondary = isSecondaryPress
                        val position = release.changes.firstOrNull()?.position ?: Offset.Zero
                        if (isSecondary) {
                            currentSecondaryClick(position)
                        } else {
                            currentPrimaryClick()
                        }
                        break
                    }
                }
            },
    )
}
