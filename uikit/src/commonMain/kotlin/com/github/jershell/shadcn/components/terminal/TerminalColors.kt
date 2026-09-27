package com.github.jershell.shadcn.components.terminal

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.composeunstyled.theme.Theme
import com.github.jershell.shadcn.theme.ColorProps
import com.github.jershell.shadcn.theme.ColorTokens

data class TerminalColors(
    val background: Color,
    val foreground: Color,
    val cursor: Color,
    val selection: Color,
    /** Standard ANSI 0-15 order; null retains xterm's default palette. */
    val ansiColors: List<Color>? = null,
) {
    init { require(ansiColors == null || ansiColors.size == 16) { "ANSI palettes must contain exactly 16 colors" } }
}

object TerminalDefaults {
    @Composable
    fun colors() = TerminalColors(
        background = Theme[ColorProps][ColorTokens.background],
        foreground = Theme[ColorProps][ColorTokens.foreground],
        cursor = Theme[ColorProps][ColorTokens.primary],
        selection = Theme[ColorProps][ColorTokens.accent],
    )

    val extraKeys = listOf(
        TerminalKey.Escape, TerminalKey.Tab, TerminalKey.Interrupt,
        TerminalKey.Left, TerminalKey.Down, TerminalKey.Up, TerminalKey.Right,
    )
}

internal fun Color.terminalCssColor(): String =
    "#" + (toArgb().toUInt() and 0x00ffffffu).toString(16).padStart(6, '0')

internal fun TerminalColors.webTheme(): Map<String, String> = buildMap {
    putAll(mapOf(
    "background" to background.terminalCssColor(),
    "foreground" to foreground.terminalCssColor(),
    "cursor" to cursor.terminalCssColor(),
    "selectionBackground" to selection.terminalCssColor(),
    ))
    val names = listOf("black", "red", "green", "yellow", "blue", "magenta", "cyan", "white",
        "brightBlack", "brightRed", "brightGreen", "brightYellow", "brightBlue", "brightMagenta", "brightCyan", "brightWhite")
    ansiColors?.forEachIndexed { index, color -> put(names[index], color.terminalCssColor()) }
}
