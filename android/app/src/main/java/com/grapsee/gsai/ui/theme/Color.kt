package com.grapsee.gsai.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * AERUO KINETIC — RAW color registry for Android.
 *
 * This file holds ONLY raw values (the physical palette). No UI code may
 * reference `Aeruo.*` directly any more — call sites consume semantic tokens
 * via `GsTheme.colors.*` (see Tokens.kt). The semantic layer maps these raw
 * values into background / text / structural / semantic / AI / code roles for
 * light and dark appearance, so a single change here re-themes the app.
 *
 * Mirrors ios/App/Sources/Theme/DesignSystem.swift.
 */
object Aeruo {
    // Light palette (paper editorial)
    val Paper = Color(0xFFF7F7F5)
    val SurfaceLight = Color(0xFFFFFFFF)
    val Ink = Color(0xFF14161A)
    val InkMuted = Color(0xFF6B7280)
    val OutlineLight = Color(0xFFE5E5E1)
    val ContainerLowLight = Color(0xFFF0F0ED)
    val ContainerLight = Color(0xFFE9E9E5)
    val ContainerHighLight = Color(0xFFE2E2DD)

    // Dark palette (obsidian)
    val Obsidian = Color(0xFF0A0D12)
    val SurfaceDark = Color(0xFF11151C)
    val RaisedDark = Color(0xFF181E28)
    val TextDark = Color(0xFFEDEFF2)
    val TextMutedDark = Color(0xFF8B93A1)
    val OutlineDark = Color(0xFF232B37)
    val ContainerLowDark = Color(0xFF141923)
    val ContainerDark = Color(0xFF1B2230)
    val ContainerHighDark = Color(0xFF232B3A)

    // Aurora accent (the single accent family)
    val Accent = Color(0xFF2DD4A8)
    val AccentDeep = Color(0xFF0FA37E)
    val AccentSoftDark = Color(0x242DD4A8) // 14% alpha
    val AccentSoftLight = Color(0x1A2DD4A8) // 10% alpha

    // Secondary raw hues (grouped, restrained — promoted from former literals)
    val Cyan = Color(0xFF4CC3FF)        // aurora 2
    val Violet = Color(0xFF9D7BFF)      // aurora 3
    val DangerLight = Color(0xFFD64545) // error on light surfaces
    val DangerDark = Color(0xFFE5484D)  // error on dark surfaces
    val SuccessLight = Color(0xFF2E7D53)
    val SuccessDark = Color(0xFF4CC38A)
    val WarningLight = Color(0xFF9A6B12)
    val WarningDark = Color(0xFFE8B45A)
    val InfoLight = Color(0xFF2E7DD1)
    val InfoDark = Color(0xFF6CB8FF)
    val AmberLight = Color(0xFF9A6B12)  // research
    val AmberDark = Color(0xFFE5B15C)

    // Code "ink well" (deliberately deeper than the app surface on both themes)
    val CodeSurfaceLight = Color(0xFFF1F1EE)
    val CodeSurfaceDark = Color(0xFF0D1117)
    val CodeTextLight = Color(0xFF24292F)
    val CodeTextDark = Color(0xFFD5DAE2)
    // Syntax roles — light and dark variants (values promoted from the former
    // hard-coded palette in ChatScreen.kt; dark set unchanged)
    val SyntaxKwLight = Color(0xFF6C3FE0)
    val SyntaxKwDark = Color(0xFFC792EA)
    val SyntaxStrLight = Color(0xFF2E7D32)
    val SyntaxStrDark = Color(0xFFC3E88D)
    val SyntaxComLight = Color(0xFF6B7C8C)
    val SyntaxComDark = Color(0xFF7E8C99)
    val SyntaxNumLight = Color(0xFFD84315)
    val SyntaxNumDark = Color(0xFFF78C6C)

    // Kinetic aurora gradient — ONLY for AI-active moments
    // (streaming caret, generation progress, voice waveform, primary CTA glow)
    val Aurora = listOf(Color(0xFF2DD4A8), Color(0xFF4CC3FF), Color(0xFF9D7BFF))
}
