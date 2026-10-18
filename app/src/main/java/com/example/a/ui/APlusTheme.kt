package com.example.a.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

internal val PageColor = Color(0xFF262626)
internal val InkColor = Color.White
internal val SecondaryInkColor = Color(0xFFB8B8BD)
internal val AccentColor = Color(0xFF1A8CFF)

private val DarkColors = darkColorScheme(
    primary = AccentColor,
    background = PageColor,
    surface = PageColor,
    onBackground = InkColor,
    onSurface = InkColor,
)

@Composable
fun APlusTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColors, content = content)
}
