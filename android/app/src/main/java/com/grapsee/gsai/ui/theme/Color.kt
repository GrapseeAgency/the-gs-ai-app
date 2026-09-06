package com.grapsee.gsai.ui.theme

import androidx.compose.ui.graphics.Color

/*
 * "Premium intelligent editorial" palette.
 *
 * Light — "Paper & Ink": warm editorial paper, near-black ink, copper accent.
 * Dark  — "Deep Ink": near-black navy (never flat grey), warm off-white text,
 *         copper accent lifted for dark surfaces.
 */

// ── Light: Paper & Ink ───────────────────────────────────────────────────────
val Paper = Color(0xFFFAF9F6)           // base background (warm paper)
val PaperElevated = Color(0xFFFFFFFF)   // raised surfaces / cards on paper
val PaperShade = Color(0xFFF1EEE8)      // subtle tonal step beneath paper
val PaperBorder = Color(0xFFE2DED5)     // hairline borders on paper
val PaperDim = Color(0xFFD9D4CA)        // dimmed paper (surfaceDim)
val PaperContainerLow = Color(0xFFF4F1EB)
val PaperContainer = Color(0xFFEFECE4)
val PaperContainerHigh = Color(0xFFE9E5DD)
val PaperContainerHighest = Color(0xFFE3DFD6)
val Ink = Color(0xFF1A1D23)             // primary text / strong ink
val InkMuted = Color(0xFF6B7280)        // secondary text
val InkOutline = Color(0xFFC7C2B7)      // strong outline on paper

// ── Light + dark shared brand accents ────────────────────────────────────────
val AccentCopper = Color(0xFFB4632C)    // copper / ember accent (light)
val AccentSoft = Color(0xFFF5E4D7)      // soft copper wash (containers, chips)
val CopperFixedDim = Color(0xFFE7CBB2)  // dimmed fixed copper
val EmberDeep = Color(0xFF4A2A12)       // deep ember (text on soft copper)
val SandContainer = Color(0xFFEAE0D2)   // warm sand tertiary container
val SandDeep = Color(0xFF3F2F21)        // text on sand container

// ── Dark: Deep Ink ───────────────────────────────────────────────────────────
val InkBlack = Color(0xFF0B0E13)        // base background (deep ink, navy-black)
val SurfaceDark = Color(0xFF12161D)     // base raised surface
val SurfaceElevated = Color(0xFF1A1F28) // elevated surface
val SurfaceBrightDark = Color(0xFF262C36)
val InkContainerLowest = Color(0xFF070910)
val InkContainer = Color(0xFF161B23)
val InkContainerHighest = Color(0xFF20252F)
val SurfaceBorderDark = Color(0xFF262C36)
val InkOutlineDark = Color(0xFF39404D)
val TextPrimary = Color(0xFFF2F0EB)     // warm off-white text
val TextMuted = Color(0xFF9AA3AF)       // muted text
val AccentCopperDark = Color(0xFFC97B4A) // copper lifted for dark surfaces
val AccentSoftDark = Color(0xFF33241A)  // deep copper container (dark)
val EmberContainerDark = Color(0xFF2A2018)
val EmberSoftDark = Color(0xFFF3DFCE)   // warm text on copper containers (dark)
