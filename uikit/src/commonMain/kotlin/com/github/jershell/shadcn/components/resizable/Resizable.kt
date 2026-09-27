package com.github.jershell.shadcn.components.resizable

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.composeunstyled.theme.Theme
import com.github.jershell.shadcn.theme.ColorProps
import com.github.jershell.shadcn.theme.ColorTokens
import com.github.jershell.shadcn.theme.DimProps
import com.github.jershell.shadcn.theme.DimTokens

enum class ResizableOrientation {
    Horizontal,
    Vertical,
}

interface ResizablePanelGroupScope {
    fun panel(
        minSizeFraction: Float = 0.1f,
        content: @Composable BoxScope.() -> Unit,
    )

    fun handle(withHandle: Boolean = false)
}

private sealed interface ResizableEntry
private data class ResizablePanelEntry(
    val minSizeFraction: Float,
    val content: @Composable BoxScope.() -> Unit,
) : ResizableEntry
private data class ResizableHandleEntry(
    val withHandle: Boolean,
) : ResizableEntry

private class ResizablePanelGroupScopeImpl : ResizablePanelGroupScope {
    val entries = mutableListOf<ResizableEntry>()

    override fun panel(
        minSizeFraction: Float,
        content: @Composable BoxScope.() -> Unit,
    ) {
        require(minSizeFraction.isFinite() && minSizeFraction > 0f && minSizeFraction < 1f) {
            "minSizeFraction must be finite and between 0 and 1 (exclusive)"
        }
        entries += ResizablePanelEntry(
            minSizeFraction = minSizeFraction,
            content = content,
        )
    }

    override fun handle(withHandle: Boolean) {
        entries += ResizableHandleEntry(withHandle)
    }
}

/**
 * Panels separated by adjustable dividers. Arrow keys resize by 1%, Shift+arrow
 * by 10%; Home/End reach the adjacent panels' minimum sizes. Horizontal arrows
 * and dragging follow layout direction. Accessibility services can set progress.
 *
 * Minimum fractions must be finite, positive and sum to at most one. Changing
 * the panel count or minima resets the split to a feasible initial layout.
 */
@Composable
fun ResizablePanelGroup(
    modifier: Modifier = Modifier,
    orientation: ResizableOrientation = ResizableOrientation.Horizontal,
    content: ResizablePanelGroupScope.() -> Unit,
) {
    val scope = remember { ResizablePanelGroupScopeImpl() }
    scope.entries.clear()
    scope.content()

    val panels = scope.entries.filterIsInstance<ResizablePanelEntry>()
    if (panels.size < 2) return

    val minFractions = panels.map { it.minSizeFraction }
    require(minFractions.sum() <= 1f) { "Panel minimum fractions must sum to at most 1" }
    require(scope.entries.first() is ResizablePanelEntry && scope.entries.last() is ResizablePanelEntry &&
        scope.entries.zipWithNext().none { (a, b) -> a is ResizableHandleEntry && b is ResizableHandleEntry }) {
        "Each resize handle must have a panel on both sides"
    }
    val panelSizes = rememberPanelSizes(minFractions)
    val density = LocalDensity.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val handleSpacePx = with(density) { 10.dp.toPx() } * scope.entries.count { it is ResizableHandleEntry }

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val totalSizePx = (when (orientation) {
            ResizableOrientation.Horizontal -> constraints.maxWidth.toFloat().coerceAtLeast(1f)
            ResizableOrientation.Vertical -> constraints.maxHeight.toFloat().coerceAtLeast(1f)
        } - handleSpacePx).coerceAtLeast(1f)

        var panelIndex = 0
        when (orientation) {
            ResizableOrientation.Horizontal -> Row(modifier = Modifier.fillMaxSize()) {
                scope.entries.forEach { entry ->
                    when (entry) {
                        is ResizablePanelEntry -> {
                            Box(
                                modifier = Modifier
                                    .weight(panelSizes[panelIndex])
                                    .fillMaxHeight(),
                                content = entry.content,
                            )
                            panelIndex++
                        }
                        is ResizableHandleEntry -> {
                            val leftIndex = panelIndex - 1
                            val rightIndex = panelIndex
                            InternalResizableHandle(
                                orientation = orientation,
                                withHandle = entry.withHandle,
                                value = panelSizes[leftIndex],
                                valueRange = minFractions[leftIndex]..(panelSizes[leftIndex] + panelSizes[rightIndex] - minFractions[rightIndex]).coerceAtLeast(minFractions[leftIndex]),
                                onValueChange = { target ->
                                    applyResizeDelta(panelSizes, minFractions, leftIndex, rightIndex, target - panelSizes[leftIndex])
                                },
                                onDrag = { deltaPx ->
                                    applyResizeDelta(
                                        panelSizes = panelSizes,
                                        minFractions = minFractions,
                                        leftIndex = leftIndex,
                                        rightIndex = rightIndex,
                                        deltaFraction = deltaPx / totalSizePx * (if (rtl) -1f else 1f),
                                    )
                                },
                            )
                        }
                    }
                }
            }

            ResizableOrientation.Vertical -> Column(modifier = Modifier.fillMaxSize()) {
                scope.entries.forEach { entry ->
                    when (entry) {
                        is ResizablePanelEntry -> {
                            Box(
                                modifier = Modifier
                                    .weight(panelSizes[panelIndex])
                                    .fillMaxWidth(),
                                content = entry.content,
                            )
                            panelIndex++
                        }
                        is ResizableHandleEntry -> {
                            val topIndex = panelIndex - 1
                            val bottomIndex = panelIndex
                            InternalResizableHandle(
                                orientation = orientation,
                                withHandle = entry.withHandle,
                                value = panelSizes[topIndex],
                                valueRange = minFractions[topIndex]..(panelSizes[topIndex] + panelSizes[bottomIndex] - minFractions[bottomIndex]).coerceAtLeast(minFractions[topIndex]),
                                onValueChange = { target ->
                                    applyResizeDelta(panelSizes, minFractions, topIndex, bottomIndex, target - panelSizes[topIndex])
                                },
                                onDrag = { deltaPx ->
                                    applyResizeDelta(
                                        panelSizes = panelSizes,
                                        minFractions = minFractions,
                                        leftIndex = topIndex,
                                        rightIndex = bottomIndex,
                                        deltaFraction = deltaPx / totalSizePx,
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberPanelSizes(minFractions: List<Float>): SnapshotStateList<Float> = remember(minFractions) {
    // Start within every panel's constraints, including asymmetric minima.
    val remaining = (1f - minFractions.sum()).coerceAtLeast(0f) / minFractions.size
    mutableStateListOf<Float>().apply { addAll(minFractions.map { it + remaining }) }
}

private fun applyResizeDelta(
    panelSizes: SnapshotStateList<Float>,
    minFractions: List<Float>,
    leftIndex: Int,
    rightIndex: Int,
    deltaFraction: Float,
) {
    if (leftIndex !in panelSizes.indices || rightIndex !in panelSizes.indices) return
    val left = panelSizes[leftIndex]
    val right = panelSizes[rightIndex]
    val leftMin = minFractions[leftIndex]
    val rightMin = minFractions[rightIndex]

    val maxPositiveDelta = (right - rightMin).coerceAtLeast(0f)
    val maxNegativeDelta = -(left - leftMin).coerceAtLeast(0f)
    val clamped = deltaFraction.coerceIn(maxNegativeDelta, maxPositiveDelta)

    panelSizes[leftIndex] = left + clamped
    panelSizes[rightIndex] = right - clamped
}

@Composable
private fun InternalResizableHandle(
    orientation: ResizableOrientation,
    withHandle: Boolean,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    onDrag: (Float) -> Unit,
) {
    val currentOnDrag by rememberUpdatedState(onDrag)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    fun setValue(target: Float): Boolean {
        if (!target.isFinite()) return false
        val clamped = target.coerceIn(valueRange)
        if (clamped == value) return false
        onValueChange(clamped)
        return true
    }
    val border = Theme[ColorProps][ColorTokens.border]
    val grip = Theme[ColorProps][ColorTokens.mutedForeground].copy(alpha = 0.7f)
    val radius = Theme[DimProps][DimTokens.radiusFull]
    val shape = RoundedCornerShape(radius)

    val handleModifier = when (orientation) {
        ResizableOrientation.Horizontal -> Modifier
            .width(10.dp)
            .fillMaxHeight()
        ResizableOrientation.Vertical -> Modifier
            .height(10.dp)
            .fillMaxWidth()
    }
    Box(
        modifier = handleModifier
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(value, valueRange)
                setProgress { setValue(it) }
            }
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                val horizontal = orientation == ResizableOrientation.Horizontal
                val step = if (event.isShiftPressed) 0.1f else 0.01f
                val delta = when (event.key) {
                    Key.DirectionRight -> if (horizontal) step * (if (rtl) -1 else 1) else return@onKeyEvent false
                    Key.DirectionLeft -> if (horizontal) -step * (if (rtl) -1 else 1) else return@onKeyEvent false
                    Key.DirectionDown -> if (!horizontal) step else return@onKeyEvent false
                    Key.DirectionUp -> if (!horizontal) -step else return@onKeyEvent false
                    Key.MoveHome -> valueRange.start - value
                    Key.MoveEnd -> valueRange.endInclusive - value
                    else -> return@onKeyEvent false
                }
                setValue(value + delta)
                true
            }
            .focusable(interactionSource = interaction)
            .resizableHoverCursor(orientation)
            .pointerInput(orientation) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    val delta = if (orientation == ResizableOrientation.Horizontal) {
                        dragAmount.x
                    } else {
                        dragAmount.y
                    }
                    currentOnDrag(delta)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val lineModifier = when (orientation) {
            ResizableOrientation.Horizontal -> Modifier
                .width(1.dp)
                .fillMaxHeight()
            ResizableOrientation.Vertical -> Modifier
                .height(1.dp)
                .fillMaxWidth()
        }
        Box(modifier = lineModifier.background(if (focused) Theme[ColorProps][ColorTokens.ring] else border))

        if (withHandle) {
            val gripModifier = when (orientation) {
                ResizableOrientation.Horizontal -> Modifier
                    .width(6.dp)
                    .height(48.dp)
                ResizableOrientation.Vertical -> Modifier
                    .width(48.dp)
                    .height(6.dp)
            }
            Box(
                modifier = gripModifier
                    .background(grip, shape),
            )
        }
    }
}
