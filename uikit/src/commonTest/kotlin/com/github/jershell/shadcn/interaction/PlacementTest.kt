package com.github.jershell.shadcn.interaction

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import com.composeunstyled.AnchorSide
import com.github.jershell.shadcn.anchored.chooseAnchorSide
import kotlin.test.Test
import kotlin.test.assertEquals

class PlacementTest {
    @Test fun submenuFlipsAtWindowEdgeAndMirrorsInRtl() {
        val anchor = Rect(400f, 100f, 480f, 130f)
        val panel = IntSize(200, 100)
        val window = IntSize(500, 400)
        assertEquals(AnchorSide.Start, chooseAnchorSide(AnchorSide.End, anchor, panel, window, 4f, false))
        assertEquals(AnchorSide.End, chooseAnchorSide(AnchorSide.End, anchor, panel, window, 4f, true))
        assertEquals(AnchorSide.End, chooseAnchorSide(AnchorSide.End, anchor, IntSize(8, 100), window, 4f, false))
    }
}
