package com.grapsee.gsai.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * WARM CONSUMER — RAW color registry for Android.
 *
 * This file holds ONLY raw values (the physical palette). No UI code may
 * reference `Aeruo.*` directly any more — call sites consume semantic tokens
 * via `GsTheme.colors.*` (see Tokens.kt). The semantic layer maps these raw
 * values into background / text / structural / semantic / AI / code roles for
 * light and dark appearance, so a single change here re-themes the app.
 *
 * Product-shell visual identity: warm peach surfaces, orange AI actions,
 * and soft tactile cards inspired by the reference mobile experience. Dark = black surfaces / white text / neutral grays. Light =
 * white surfaces / black text / neutral grays. The former teal/green accent
 * language is gone — "accent" is now ink-on-paper (light) and white-on-black
 * (dark). Only semantic status hues (error / success / warning / info) and
 * the code syntax palette keep colour, and they are content states, never
 * interface chrome.
 *
 * Mirrors ios/App/Sources/Theme/DesignSystem.swift.
 */
object Aeruo {
    // Light palette (paper editorial)
    val Paper = Color(0xFFFFF4EF)
    val SurfaceLight = Color(0xFFFFFBF8)
    val Ink = Color(0xFF2B1A17)
    val InkMuted = Color(0xFF8F7169)
    val OutlineLight = Color(0xFFF0D2C6)
    val ContainerLowLight = Color(0xFFFFE8DE)
    val ContainerLight = Color(0xFFFFD8C8)
    val ContainerHighLight = Color(0xFFFFC7B1)

    // Dark palette (genuinely neutral black — no blue cast)
    val Obsidian = Color(0xFF000000)
    val SurfaceDark = Color(0xFF101214)
    val RaisedDark = Color(0xFF151719)
    val TextDark = Color(0xFFF2F3F5)
    val TextMutedDark = Color(0xFF8F7169)
    val OutlineDark = Color(0xFF27292D)
    val ContainerLowDark = Color(0xFF121417)
    val ContainerDark = Color(0xFF17191C)
    val ContainerHighDark = Color(0xFF202225)

    // Accent — now MONOCHROME. In dark appearance the accent is the primary
    // text white; in light appearance it is the primary ink. There is no hue.
    val Accent = Color(0xFFFF9142)       // dark accent = primary white
    val AccentDeep = Color(0xFFF36F2C)   // light accent = ink
    val AccentSoftDark = Color(0x24FF9142) // 14% white on black
    val AccentSoftLight = Color(0x1AFF9142) // 10% ink on paper

    // Neutral motion grays (streaming shimmer, AI life-signs — no colour)
    val GrayBright = Color(0xFFF2F3F5)
    val GrayMid = Color(0xFF8B93A1)

    // Semantic status hues — the only colour the interface may still carry
    val DangerLight = Color(0xFFD64545) // error on light surfaces
    val DangerDark = Color(0xFFE5484D)  // error on dark surfaces
    val SuccessLight = Color(0xFF2E7D53)
    val SuccessDark = Color(0xFF4CC38A)
    val WarningLight = Color(0xFF9A6B12)
    val WarningDark = Color(0xFFE8B45A)
    val InfoLight = Color(0xFF2E7DD1)
    val InfoDark = Color(0xFF6CB8FF)

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

    // Kinetic life-sign gradient — ONLY for AI-active moments
    // (streaming caret, generation progress). Now a quiet WHITE→GRAY shimmer:
    // alive but colourless, per the monochrome identity.
    val Aurora = listOf(Color(0xFFFFB36D), Color(0xFFFF8847), Color(0xFFF36F2C))
}
