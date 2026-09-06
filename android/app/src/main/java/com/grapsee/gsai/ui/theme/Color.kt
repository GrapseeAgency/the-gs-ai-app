package com.grapsee.gsai.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * AERUO KINETIC — single source of color truth for Android.
 * "Kinetic aurora over obsidian": disciplined editorial surfaces,
 * energy reserved for motion + one aurora accent family.
 * Mirrors ios/App/Sources/Theme/DesignSystem.swift exactly.
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
    val AccentSoftLight = Color(0x1A2DD4A8)

    // Kinetic aurora gradient — ONLY for AI-active moments
    // (streaming caret, generation progress, voice waveform, primary CTA glow)
    val Aurora = listOf(Color(0xFF2DD4A8), Color(0xFF4CC3FF), Color(0xFF9D7BFF))
}
