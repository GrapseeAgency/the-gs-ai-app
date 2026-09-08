package com.grapsee.gsai.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.core.view.WindowCompat
import com.grapsee.gsai.data.SettingsStore

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

/**
 * Theme root. Beyond the color scheme this is where the (previously dead)
 * appearance Settings are actually wired into the app:
 *  - themeMode: Light / Dark / System overrides the system dark flag;
 *  - fontScale: multiplies the system font scale for the whole tree via
 *    LocalDensity, so every sp-sized text in the app truly rescales;
 *  - haptics: publishes the global gate consumed by GsHaptics;
 *  - reduceAnimations + reduceMotion: publishes GsMotion.reduced.
 * All four read snapshot state, so a Settings change recomposes this root.
 */
@Composable
fun TheGsAiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val resolvedDark = when (SettingsStore.themeMode) {
        "Light" -> false
        "Dark" -> true
        else -> darkTheme
    }

    // Settings → platform gates (see GsHaptics.kt / Motion.kt).
    SyncHapticsGate()
    GsMotion.reduced = SettingsStore.reduceAnimations || SettingsStore.reduceMotion

    val baseDensity = LocalDensity.current
    val scaledDensity = Density(
        density = baseDensity.density,
        fontScale = baseDensity.fontScale * SettingsStore.fontScale
    )

    MaterialTheme(
        colorScheme = if (resolvedDark) DarkScheme else LightScheme,
        typography = GsTypography,
        content = {
            CompositionLocalProvider(LocalDensity provides scaledDensity) {
                SystemBarIconAppearance(resolvedDark)
                content()
            }
        }
    )
}

/**
 * System-bar icon contrast follows the app's RESOLVED theme, not just the
 * system setting — otherwise a forced Dark theme under a light system puts
 * dark icons on the obsidian canvas. enableEdgeToEdge() (MainActivity) keeps
 * owning the transparent bars and edge-to-edge flags; this only flips icon
 * appearance, the same way the Compose template does.
 */
@Composable
private fun SystemBarIconAppearance(dark: Boolean) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
        }
    }
}
