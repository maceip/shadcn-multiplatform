package com.github.jershell.shadcn.theme

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.font.FontFamily

/**
 * The app font family of the theme builder (`Font` picker of the shadcn/ui create
 * page), resolved by the host app. `null` means the generated theme default
 * (`FontFamily.Default`).
 *
 * The app can build a [FontFamily] from its own bundled fonts and provide it
 * here, or use the SDK's offline [ProvideDepartureMonoFont]. The generated
 * `TypographyStyles` read it live at composition time, so every text style
 * follows the picked font.
 */
public val LocalShadcnFonts: ProvidableCompositionLocal<FontFamily?> =
    staticCompositionLocalOf { null }
