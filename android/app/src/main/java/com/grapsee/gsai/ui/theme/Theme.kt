package com.grapsee.gsai.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * Material 3 color schemes for the "premium intelligent editorial" direction.
 *
 * Light: warm paper surfaces, strong ink text, copper accent.
 * Dark: deep ink surfaces (near-black navy, not flat grey), warm text,
 * copper accent lifted for dark surfaces. Surface-container roles are set
 * explicitly so layered surfaces tone-step through the palette instead of
 * Material's baseline purple-tinted defaults.
 */
private val LightColors = lightColorScheme(
    primary = AccentCopper,
    onPrimary = PaperElevated,
    primaryContainer = AccentSoft,
    onPrimaryContainer = EmberDeep,
    inversePrimary = AccentSoft,
    secondary = Ink,
    onSecondary = Paper,
    secondaryContainer = PaperShade,
    onSecondaryContainer = Ink,
    tertiary = EmberDeep,
    onTertiary = Paper,
    tertiaryContainer = SandContainer,
    onTertiaryContainer = SandDeep,
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = PaperShade,
    onSurfaceVariant = InkMuted,
    surfaceTint = AccentCopper,
    inverseSurface = Ink,
    inverseOnSurface = Paper,
    outline = InkOutline,
    outlineVariant = PaperBorder,
    surfaceDim = PaperDim,
    surfaceBright = PaperElevated,
    surfaceContainerLowest = PaperElevated,
    surfaceContainerLow = PaperContainerLow,
    surfaceContainer = PaperContainer,
    surfaceContainerHigh = PaperContainerHigh,
    surfaceContainerHighest = PaperContainerHighest,
    primaryFixed = AccentSoft,
    primaryFixedDim = CopperFixedDim,
    onPrimaryFixed = EmberDeep,
    onPrimaryFixedVariant = EmberDeep
)

private val DarkColors = darkColorScheme(
    primary = AccentCopperDark,
    onPrimary = InkBlack,
    primaryContainer = AccentSoftDark,
    onPrimaryContainer = EmberSoftDark,
    inversePrimary = AccentCopper,
    secondary = TextPrimary,
    onSecondary = InkBlack,
    secondaryContainer = InkContainer,
    onSecondaryContainer = TextPrimary,
    tertiary = EmberSoftDark,
    onTertiary = InkBlack,
    tertiaryContainer = EmberContainerDark,
    onTertiaryContainer = EmberSoftDark,
    background = InkBlack,
    onBackground = TextPrimary,
    surface = InkBlack,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceDark,
    onSurfaceVariant = TextMuted,
    surfaceTint = AccentCopperDark,
    inverseSurface = TextPrimary,
    inverseOnSurface = InkBlack,
    outline = InkOutlineDark,
    outlineVariant = SurfaceBorderDark,
    surfaceDim = InkBlack,
    surfaceBright = SurfaceBrightDark,
    surfaceContainerLowest = InkContainerLowest,
    surfaceContainerLow = SurfaceDark,
    surfaceContainer = InkContainer,
    surfaceContainerHigh = SurfaceElevated,
    surfaceContainerHighest = InkContainerHighest,
    primaryFixed = AccentSoft,
    primaryFixedDim = CopperFixedDim,
    onPrimaryFixed = EmberDeep,
    onPrimaryFixedVariant = EmberDeep
)

@Composable
fun TheGsAiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = GsTypography,
        content = content
    )
}
