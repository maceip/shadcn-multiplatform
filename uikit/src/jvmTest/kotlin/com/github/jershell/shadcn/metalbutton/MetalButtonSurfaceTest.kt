@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.github.jershell.shadcn.metalbutton

import androidx.compose.foundation.layout.size
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.composeunstyled.theme.Theme
import com.github.jershell.shadcn.components.button.ButtonText
import com.github.jershell.shadcn.components.button.ButtonVariant
import com.github.jershell.shadcn.components.metalbutton.MetalButton
import com.github.jershell.shadcn.theme.*
import kotlin.test.*

class MetalButtonSurfaceTest {
    @Test fun primaryFillAndSlotLabelSurviveZeroEffectAndReducedMotion() {
        for (mode in listOf(Mode.Light, Mode.Dark)) {
            var primary = Color.Unspecified
            var foreground = Color.Unspecified
            val scene = ImageComposeScene(200, 80, Density(1f)) {
                ShadcnTheme(mode = mode) {
                    primary = Theme[ColorProps][ColorTokens.primary]
                    foreground = Theme[ColorProps][ColorTokens.primaryForeground]
                    MetalButton({}, Modifier.size(200.dp, 80.dp), strength = 0f,
                        animationsEnabled = false, buttonVariant = ButtonVariant.Default) { ButtonText("Run") }
                }
            }
            try {
                repeat(4) { scene.render(it * 200_000_000L).close() }
                val image = scene.render(1_000_000_000L)
                try {
                    val pixels = image.toComposeImageBitmap().toPixelMap()
                    assertEquals(primary, pixels[20, 40], "$mode: primary surface must be painted without the effect")
                    val foregroundPixels = (20 until 60).sumOf { y ->
                        (24 until 100).count { x ->
                            val pixel = pixels[x, y]
                            kotlin.math.abs(pixel.red - foreground.red) < 0.03f &&
                                kotlin.math.abs(pixel.green - foreground.green) < 0.03f &&
                                kotlin.math.abs(pixel.blue - foreground.blue) < 0.03f
                        }
                    }
                    assertTrue(foregroundPixels > 10, "$mode: ButtonText must inherit the contrasting foreground")
                } finally { image.close() }
            } finally { scene.close() }
        }
    }
}
