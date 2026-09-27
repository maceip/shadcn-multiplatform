package com.github.jershell.shadcn.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.font.FontFamily
import com.github.jershell.shadcn.generated.resources.Res
import com.github.jershell.shadcn.generated.resources.departure_mono
import org.jetbrains.compose.resources.Font

/** Same offline font is shared by native Compose and the embedded terminal's @font-face. */
const val DEPARTURE_MONO_RESOURCE_PATH = "font/departure_mono.otf"

/** Optional monospace override, separate from the application's body font. */
val LocalShadcnMonospaceFonts = staticCompositionLocalOf<FontFamily?> { null }

/** Unmodified Departure Mono 1.500 by Helena Zhang, bundled under SIL OFL 1.1. */
@Composable
fun DepartureMonoFontFamily(): FontFamily = FontFamily(Font(Res.font.departure_mono))

@Composable
fun shadcnMonospaceFontFamily(): FontFamily = LocalShadcnMonospaceFonts.current ?: FontFamily.Monospace

/**
 * Applies the bundled font to the existing shadcn typography and to code/markdown. Place outside
 * ShadcnUI/ShadcnTheme when implicit Unstyled text should inherit it too. The original font has
 * one regular face; use 11px increments where crisp pixel alignment is desired.
 */
@Composable
fun ProvideDepartureMonoFont(content: @Composable () -> Unit) {
    val family = DepartureMonoFontFamily()
    CompositionLocalProvider(LocalShadcnFonts provides family, LocalShadcnMonospaceFonts provides family,
        content = content)
}
