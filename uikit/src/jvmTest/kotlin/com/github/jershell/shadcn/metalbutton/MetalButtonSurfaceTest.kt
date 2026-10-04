@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.github.jershell.shadcn.metalbutton

import androidx.compose.foundation.layout.size
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.composeunstyled.theme.Theme
import com.github.jershell.shadcn.components.button.ButtonText
import com.github.jershell.shadcn.components.button.ButtonVariant
import com.github.jershell.shadcn.components.button.LocalButtonContentColor
import com.github.jershell.shadcn.components.metalbutton.MetalButton
import com.github.jershell.shadcn.theme.*
import kotlin.test.*

class MetalButtonSurfaceTest {
    @Test fun primaryFillAndSlotLabelSurviveZeroEffectAndReducedMotion() {
        for (mode in listOf(Mode.Light, Mode.Dark)) {
            var primary = Color.Unspecified
            var foreground = Color.Unspecified
            var slotForeground = Color.Unspecified
            val scene = ImageComposeScene(200, 80, Density(1f)) {
                ShadcnTheme(mode = mode) {
                    primary = Theme[ColorProps][ColorTokens.primary]
                    foreground = Theme[ColorProps][ColorTokens.primaryForeground]
                    MetalButton({}, Modifier.size(200.dp, 80.dp), strength = 0f,
                        animationsEnabled = false, buttonVariant = ButtonVariant.Default) {
                        slotForeground = LocalButtonContentColor.current
                        ButtonText("Run")
                    }
                }
            }
            try {
                repeat(4) { scene.render(it * 200_000_000L).close() }
                val image = scene.render(1_000_000_000L)
                try {
                    val pixels = image.toComposeImageBitmap().toPixelMap()
                    assertEquals(primary, pixels[20, 40], "$mode: primary surface must be painted without the effect")
                    assertEquals(foreground, slotForeground, "$mode: slot must receive the primary foreground")
                    // Glyph coverage differs across system font backends. Check visible contrast,
                    // rather than requiring nearly opaque pixels at a particular hinting weight.
                    val surfaceLuminance = primary.luminance()
                    val foregroundPixels = (20 until 60).sumOf { y ->
                        (24 until 180).count { x ->
                            val luminance = pixels[x, y].luminance()
                            (maxOf(luminance, surfaceLuminance) + 0.05f) /
                                (minOf(luminance, surfaceLuminance) + 0.05f) >= 4.5f
                        }
                    }
                    assertTrue(foregroundPixels > 10, "$mode: ButtonText must inherit the contrasting foreground")
                } finally { image.close() }
            } finally { scene.close() }
        }
    }
}
