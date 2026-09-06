package com.grapsee.gsai.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightScheme = lightColorScheme(
    primary = Aeruo.Accent,
    onPrimary = Color.White,
    primaryContainer = Aeruo.ContainerLight,
    onPrimaryContainer = Aeruo.Ink,
    secondary = Aeruo.AccentDeep,
    onSecondary = Color.White,
    background = Aeruo.Paper,
    onBackground = Aeruo.Ink,
    surface = Aeruo.SurfaceLight,
    onSurface = Aeruo.Ink,
    surfaceVariant = Aeruo.ContainerLowLight,
    onSurfaceVariant = Aeruo.InkMuted,
    surfaceContainerLowest = Aeruo.SurfaceLight,
    surfaceContainerLow = Aeruo.ContainerLowLight,
    surfaceContainer = Aeruo.ContainerLight,
    surfaceContainerHigh = Aeruo.ContainerHighLight,
    surfaceContainerHighest = Aeruo.ContainerHighLight,
    outline = Aeruo.OutlineLight,
    outlineVariant = Aeruo.OutlineLight
)

private val DarkScheme = darkColorScheme(
    primary = Aeruo.Accent,
    onPrimary = Color(0xFF06231C),
    primaryContainer = Aeruo.ContainerDark,
    onPrimaryContainer = Aeruo.TextDark,
    secondary = Aeruo.Accent,
    onSecondary = Color(0xFF06231C),
    background = Aeruo.Obsidian,
    onBackground = Aeruo.TextDark,
    surface = Aeruo.SurfaceDark,
    onSurface = Aeruo.TextDark,
    surfaceVariant = Aeruo.RaisedDark,
    onSurfaceVariant = Aeruo.TextMutedDark,
    surfaceContainerLowest = Aeruo.Obsidian,
    surfaceContainerLow = Aeruo.ContainerLowDark,
    surfaceContainer = Aeruo.ContainerDark,
    surfaceContainerHigh = Aeruo.ContainerHighDark,
    surfaceContainerHighest = Aeruo.ContainerHighDark,
    outline = Aeruo.OutlineDark,
    outlineVariant = Aeruo.OutlineDark
)

@Composable
fun TheGsAiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        typography = GsTypography,
        content = content
    )
}
