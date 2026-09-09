package com.grapsee.gsai.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.Window
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.grapsee.gsai.data.SettingsStore

/**
 * Accessibility wiring composition locals — provided at the theme root from
 * [SettingsStore] (snapshot state, so a Settings flip recomposes this root and
 * re-provides live). Consumed inside the shared component layer only — no
 * call sites change.
 */

/** Settings → "Screen reader hints": adds factual TalkBack hints on actionable
 *  shared components. No visuals — TalkBack reads the hints; sighted UI is
 *  byte-identical. (Component-layer alias: LocalGsScreenReaderHints.) */
val LocalScreenReaderHints = staticCompositionLocalOf { false }

/** Settings → "High contrast": the semantic GsColors constructors take the
 *  flag directly (stronger text/border raw values); this local additionally
 *  lets component-layer code react (e.g. heavier dividers). */
val LocalHighContrast = staticCompositionLocalOf { false }

/**
 * AERUO KINETIC theme root.
 *
 * Beyond the color scheme this is where the appearance + accessibility
 * Settings are actually wired into the app:
 *  - themeMode: Light / Dark / System resolves the app appearance (live);
 *  - highContrast: semantic GsColors built with high-contrast raw values;
 *  - fontScale: multiplies the system font scale for the whole tree via
 *    LocalDensity, so every sp-sized text in the app truly rescales (the
 *    semantic GsTextSet code/metadata/button roles are pre-scaled here too);
 *  - haptics: publishes the global GsHaptics gate;
 *  - reduceAnimations + reduceMotion: publishes GsMotion.reduced;
 *  - screenReaderHints + highContrast: provided as composition locals;
 *  - system bars' icon appearance follows the APP theme, not the OS theme.
 * All inputs read snapshot state, so a Settings change recomposes this root.
 */
@Composable
fun TheGsAiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val dark = when (SettingsStore.themeMode) {
        "Light" -> false
        "Dark" -> true
        else -> darkTheme
    }
    val highContrast = SettingsStore.highContrast
    val fontScale = SettingsStore.fontScale

    val gsColors = if (dark) darkGsColors(highContrast) else lightGsColors(highContrast)
    // LocalDensity scales the WHOLE tree (every sp value). The semantic text
    // roles (code/metadata/button) are not sp-resolved through Density in every
    // custom drawing context, so they are pre-scaled here as well.
    val textSet = remember(fontScale) { gsTextSet(fontScale) }

    // Settings → platform gates (see GsHaptics.kt / Motion.kt).
    SyncHapticsGate()
    GsMotion.reduced = SettingsStore.reduceAnimations || SettingsStore.reduceMotion

    val baseDensity = LocalDensity.current
    val scaledDensity = Density(
        density = baseDensity.density,
        fontScale = baseDensity.fontScale * SettingsStore.fontScale
    )

    // System bars follow the APP theme, not the OS theme — otherwise a
    // forced-light app on a dark system renders unreadable status icons.
    val view = LocalView.current
    SideEffect {
        val window = view.context.findWindow()
        if (window != null) {
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !dark
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !dark
        }
    }

    CompositionLocalProvider(
        LocalDensity provides scaledDensity,
        LocalGsColors provides gsColors,
        LocalGsTextSet provides textSet,
        LocalScreenReaderHints provides SettingsStore.screenReaderHints,
        LocalHighContrast provides highContrast,
        com.grapsee.gsai.ui.components.LocalGsScreenReaderHints provides SettingsStore.screenReaderHints,
    ) {
        MaterialTheme(
            colorScheme = gsColors.toMaterialScheme(dark),
            typography = GsTypography, // font scale flows through LocalDensity
            content = content,
        )
    }
}

private fun Context.findWindow(): Window? = when (this) {
    is Activity -> window
    is ContextWrapper -> baseContext.findWindow()
    else -> null
}

/** Non-Material text roles (code / metadata / button), scaled by fontScale. */
fun gsTextSet(fontScale: Float): GsTextSet {
    val code = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 20.sp,
    )
    val codeBlock = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 22.sp,
    )
    val metadata = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.3.sp,
    )
    val button = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 19.sp,
        letterSpacing = 0.2.sp,
    )
    if (fontScale == 1.0f) return GsTextSet(code, codeBlock, metadata, button)
    fun TextStyle.scaled(): TextStyle = copy(
        fontSize = (fontSize.value * fontScale).sp,
        lineHeight = (lineHeight.value * fontScale).sp,
    )
    return GsTextSet(code.scaled(), codeBlock.scaled(), metadata.scaled(), button.scaled())
}
