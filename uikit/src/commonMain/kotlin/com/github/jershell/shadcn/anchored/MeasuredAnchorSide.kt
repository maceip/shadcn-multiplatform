package com.github.jershell.shadcn.anchored

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import com.composeunstyled.AnchorSide
import com.composeunstyled.currentWindowContainerSize

internal class MeasuredAnchorSide(
    val side: AnchorSide,
    val anchorModifier: Modifier,
    val panelModifier: Modifier,
)

/** Preserve submenu flipping while delegating modality and item behavior to Unstyled. */
@Composable
internal fun rememberMeasuredAnchorSide(preferred: AnchorSide, gap: Dp): MeasuredAnchorSide {
    var anchor by remember { mutableStateOf(Rect.Zero) }
    var panel by remember { mutableStateOf(IntSize.Zero) }
    val window = currentWindowContainerSize()
    val density = LocalDensity.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val side = with(density) {
        chooseAnchorSide(preferred, anchor, panel, IntSize(window.width.roundToPx(), window.height.roundToPx()), gap.toPx(), rtl)
    }
    return MeasuredAnchorSide(
        side,
        Modifier.onGloballyPositioned { anchor = it.boundsInWindow() },
        Modifier.onSizeChanged { panel = it },
    )
}

internal fun chooseAnchorSide(
    preferred: AnchorSide,
    anchor: Rect,
    panel: IntSize,
    window: IntSize,
    gap: Float,
    rtl: Boolean,
): AnchorSide {
    fun available(side: AnchorSide): Float = when (side) {
        AnchorSide.Top -> anchor.top - gap
        AnchorSide.Bottom -> window.height - anchor.bottom - gap
        AnchorSide.Start -> (if (rtl) window.width - anchor.right else anchor.left) - gap
        AnchorSide.End -> (if (rtl) anchor.left else window.width - anchor.right) - gap
    }
    val opposite = when (preferred) {
        AnchorSide.Top -> AnchorSide.Bottom
        AnchorSide.Bottom -> AnchorSide.Top
        AnchorSide.Start -> AnchorSide.End
        AnchorSide.End -> AnchorSide.Start
    }
    val required = if (preferred == AnchorSide.Top || preferred == AnchorSide.Bottom) panel.height else panel.width
    return if (required <= available(preferred) || available(preferred) >= available(opposite)) preferred else opposite
}
