package com.github.jershell.shadcn.components.dropdownmenu

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow

private class MenuFocusItem {
    val requester = FocusRequester()
    var focused = false
    var y = 0f
}

private val LocalMenuFocusItems = staticCompositionLocalOf<MutableList<MenuFocusItem>?> { null }

/** Include submenu anchors in navigation: upstream's direct-child metadata misses them. */
@Composable
internal fun MenuKeyboardNavigation(content: @Composable (Modifier) -> Unit) {
    val items = remember { mutableListOf<MenuFocusItem>() }
    val navigation = Modifier.onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        val ordered = items.sortedBy { it.y }
        if (ordered.isEmpty()) return@onPreviewKeyEvent false
        val current = ordered.indexOfFirst { it.focused }
        val target = when (event.key) {
            Key.DirectionDown -> if (current < 0) 0 else (current + 1) % ordered.size
            Key.DirectionUp -> if (current <= 0) ordered.lastIndex else current - 1
            Key.MoveHome -> 0
            Key.MoveEnd -> ordered.lastIndex
            else -> return@onPreviewKeyEvent false
        }
        ordered[target].requester.requestFocus()
        true
    }
    CompositionLocalProvider(LocalMenuFocusItems provides items) { content(navigation) }
}

@Composable
internal fun Modifier.menuFocusItem(enabled: Boolean): Modifier {
    val items = LocalMenuFocusItems.current ?: return this
    val item = remember { MenuFocusItem() }
    DisposableEffect(items, enabled) {
        if (enabled) items.add(item)
        onDispose { items.remove(item) }
    }
    return this.focusRequester(item.requester)
        .onFocusChanged { item.focused = it.isFocused }
        .onGloballyPositioned { item.y = it.positionInWindow().y }
}
