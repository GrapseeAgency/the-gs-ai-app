package com.grapsee.gsai.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * AERUO KINETIC — SEMANTIC design tokens.
 *
 * This is the one authoritative vocabulary for the app's visual foundation.
 * Screens and components consume `GsTheme.colors.*`, `GsSpacing`, `GsRadius`,
 * `GsElevation` and `GsTheme.textStyles` — never raw `Color(0x…)` literals and
 * never the raw `Aeruo.*` registry.
 *
 * Every colour below is theme-resolved (light + dark constructors) and every
 * text/border role has a high-contrast variant. Mirrors iOS DesignSystem.swift.
 */
@Immutable
data class GsColors(
    // ---- Background hierarchy ------------------------------------------------
    val appBackground: Color,        // window canvas
    val secondaryBackground: Color,  // grouped/under-page canvas
    val surface: Color,              // standard content surface (cards on canvas)
    val elevatedSurface: Color,      // above-surface content (popovers, elevated cards)
    val raisedSurface: Color,        // raised controls: pills, tiles, filled buttons
    val inputSurface: Color,         // text fields, search bars, composer field
    val sheetSurface: Color,         // bottom sheets
    val dialogSurface: Color,        // dialogs / confirmations
    val navSurface: Color,           // drawer / navigation chrome

    // ---- Text hierarchy ------------------------------------------------------
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val textDisabled: Color,
    val textPlaceholder: Color,
    val textInverted: Color,         // text on accent/dark imagery/scrims
    val link: Color,

    // ---- Structural ----------------------------------------------------------
    val border: Color,
    val borderStrong: Color,
    val divider: Color,
    val selected: Color,             // selection indicator
    val pressedOverlay: Color,       // pressed/hover overlay on any surface
    val focus: Color,                // focus ring
    val scrim: Color,                // modal scrim over content

    // ---- Semantic ------------------------------------------------------------
    val accent: Color,
    val accentStrong: Color,
    val accentSoft: Color,
    val onAccent: Color,
    val success: Color,
    val successSoft: Color,
    val warning: Color,
    val warningSoft: Color,
    val error: Color,
    val errorSoft: Color,
    val onError: Color,
    val info: Color,
    val infoSoft: Color,

    // ---- AI-specific ---------------------------------------------------------
    val aiActive: Color,             // model alive: orb, streaming dot
    val streaming: Color,            // text streaming indicator
    val toolExecution: Color,        // tool/function call states
    val research: Color,             // research mode / deep work
    val voice: Color,                // voice/dictation states
    val generation: Color,           // image/content generation

    // ---- Code ---------------------------------------------------------------
    val codeSurface: Color,
    val codeText: Color,
    val codeKeyword: Color,
    val codeString: Color,
    val codeComment: Color,
    val codeNumber: Color,

    // ---- Aurora -------------------------------------------------------------
    val aurora: List<Color>,
)

private const val SCRIM_LIGHT = 0x80000000L // 50% black
private const val SCRIM_DARK = 0x99000000L  // 60% black

/** Light appearance — deliberate paper-editorial theme, not "default white". */
fun lightGsColors(highContrast: Boolean = false): GsColors {
    val textSecondary = if (highContrast) Color(0xFF3D4450) else Color(0xFF5B6472)
    val border = if (highContrast) Color(0xFFB9B9B2) else Color(0xFFE5E5E1)
    return GsColors(
        appBackground = Aeruo.Paper,
        secondaryBackground = Aeruo.ContainerLowLight,
        surface = Aeruo.SurfaceLight,
        elevatedSurface = Aeruo.SurfaceLight,
        raisedSurface = Aeruo.ContainerLowLight,
        inputSurface = Aeruo.ContainerLowLight,
        sheetSurface = Aeruo.SurfaceLight,
        dialogSurface = Aeruo.SurfaceLight,
        navSurface = Aeruo.SurfaceLight,

        textPrimary = if (highContrast) Color(0xFF000000) else Aeruo.Ink,
        textSecondary = textSecondary,
        textTertiary = Color(0xFF8A919D),
        textDisabled = Color(0xFFC0C4CB),
        textPlaceholder = Color(0xFF9AA1AC),
        textInverted = Color(0xFFFFFFFF),
        link = Color(0xFF0A6E56),

        border = border,
        borderStrong = if (highContrast) Color(0xFF8F8F87) else Color(0xFFD2D2CC),
        divider = Color(0xFFE8E8E4),
        selected = Aeruo.AccentDeep,
        pressedOverlay = Color(0x0F14161A), // 6% ink
        focus = Aeruo.AccentDeep,
        scrim = Color(SCRIM_LIGHT),

        accent = Aeruo.AccentDeep,          // accent readable on paper
        accentStrong = Color(0xFF0A6E56),
        accentSoft = Aeruo.AccentSoftLight,
        onAccent = Color(0xFFFFFFFF),
        success = Aeruo.SuccessLight,
        successSoft = Color(0x1A2E7D53),
        warning = Aeruo.WarningLight,
        warningSoft = Color(0x1A9A6B12),
        error = Aeruo.DangerLight,
        errorSoft = Color(0x1AD64545),
        onError = Color(0xFFFFFFFF),
        info = Aeruo.InfoLight,
        infoSoft = Color(0x1A2E7DD1),

        aiActive = Aeruo.AccentDeep,
        streaming = Color(0xFF1E7FCB),      // cyan tuned for paper
        toolExecution = Color(0xFF6E5BC8),  // violet tuned for paper
        research = Aeruo.AmberLight,
        voice = Aeruo.AccentDeep,
        generation = Color(0xFF6E5BC8),

        codeSurface = Aeruo.CodeSurfaceLight,
        codeText = Aeruo.CodeTextLight,
        codeKeyword = Aeruo.SyntaxKwLight,
        codeString = Aeruo.SyntaxStrLight,
        codeComment = Aeruo.SyntaxComLight,
        codeNumber = Aeruo.SyntaxNumLight,

        aurora = Aeruo.Aurora,
    )
}

/** Dark appearance — the obsidian identity. */
fun darkGsColors(highContrast: Boolean = false): GsColors {
    val textSecondary = if (highContrast) Color(0xFFB4BCC9) else Color(0xFF8B93A1)
    return GsColors(
        appBackground = Aeruo.Obsidian,
        secondaryBackground = Color(0xFF10141A),
        surface = Aeruo.SurfaceDark,
        elevatedSurface = Aeruo.ContainerDark,
        raisedSurface = Aeruo.RaisedDark,
        inputSurface = Aeruo.ContainerLowDark,
        sheetSurface = Aeruo.SurfaceDark,
        dialogSurface = Aeruo.RaisedDark,
        navSurface = Aeruo.Obsidian,

        textPrimary = if (highContrast) Color(0xFFFFFFFF) else Aeruo.TextDark,
        textSecondary = textSecondary,
        textTertiary = Color(0xFF6E7787),
        textDisabled = Color(0xFF4A5261),
        textPlaceholder = Color(0xFF6E7787),
        textInverted = Color(0xFF14161A),
        link = Aeruo.Accent,

        border = if (highContrast) Color(0xFF39445A) else Aeruo.OutlineDark,
        borderStrong = if (highContrast) Color(0xFF4E5C78) else Color(0xFF2E3846),
        divider = Color(0xFF1D2430),
        selected = Aeruo.Accent,
        pressedOverlay = Color(0x14EDEFF2), // 8% paper
        focus = Aeruo.Accent,
        scrim = Color(SCRIM_DARK),

        accent = Aeruo.Accent,
        accentStrong = Color(0xFF5BE2C1),   // brighter step for dark surfaces
        accentSoft = Aeruo.AccentSoftDark,
        onAccent = Color(0xFF06231C),
        success = Aeruo.SuccessDark,
        successSoft = Color(0x244CC38A),
        warning = Aeruo.WarningDark,
        warningSoft = Color(0x24E8B45A),
        error = Aeruo.DangerDark,
        errorSoft = Color(0x24E5484D),
        onError = Color(0xFFFFFFFF),
        info = Aeruo.InfoDark,
        infoSoft = Color(0x246CB8FF),

        aiActive = Aeruo.Accent,
        streaming = Aeruo.Cyan,
        toolExecution = Aeruo.Violet,
        research = Aeruo.AmberDark,
        voice = Aeruo.Accent,
        generation = Aeruo.Violet,

        codeSurface = Aeruo.CodeSurfaceDark,
        codeText = Aeruo.CodeTextDark,
        codeKeyword = Aeruo.SyntaxKwDark,
        codeString = Aeruo.SyntaxStrDark,
        codeComment = Aeruo.SyntaxComDark,
        codeNumber = Aeruo.SyntaxNumDark,

        aurora = Aeruo.Aurora,
    )
}

/** Access from anywhere: `GsTheme.colors.raisedSurface`. */
object GsTheme {
    val colors: GsColors
        @Composable
        @androidx.compose.runtime.ReadOnlyComposable
        get() = LocalGsColors.current

    val textStyles: GsTextSet
        @Composable
        @androidx.compose.runtime.ReadOnlyComposable
        get() = LocalGsTextSet.current
}

val LocalGsColors = staticCompositionLocalOf { lightGsColors() }

// ---------------------------------------------------------------------------
// Spacing — the structural scale. 4 / 8 / 16 / 24 / 32 are the foundation;
// `gap` and `control` are promoted one-offs (icon→label, row inner padding).
// ---------------------------------------------------------------------------

object GsSpacing {
    /** Hairline gaps: glyph ↔ label. */
    val xs: Dp = 4.dp
    /** Intra-component spacing. */
    val s: Dp = 8.dp
    /** Dense inner gaps: icon→label, row item spacing. */
    val gap: Dp = 12.dp
    /** Inner horizontal padding of list rows, chips, inputs. */
    val control: Dp = 14.dp
    /** Screen edge → content; card padding; standard gaps. */
    val m: Dp = 16.dp
    /** Section → section; sheet padding. */
    val l: Dp = 24.dp
    /** Major separation; empty-state breathing room. */
    val xl: Dp = 32.dp
}

// ---------------------------------------------------------------------------
// Radii — five deliberate levels + full pill. Pills are reserved for chips,
// compact filters, status indicators and selected modes.
// ---------------------------------------------------------------------------

object GsRadius {
    /** Small controls: banners, skeletons, mini-surfaces. */
    val sm: Dp = 10.dp
    fun smShape(): Shape = RoundedCornerShape(sm)
    /** Medium controls: list rows, secondary buttons. */
    val md: Dp = 14.dp
    fun mdShape(): Shape = RoundedCornerShape(md)
    /** Cards and tiles. */
    val card: Dp = 16.dp
    fun cardShape(): Shape = RoundedCornerShape(card)
    /** Sheets, dialogs and other large surfaces. */
    val sheet: Dp = 24.dp
    fun sheetShape(): Shape = RoundedCornerShape(topStart = sheet, topEnd = sheet)
    /** Composer / input bars — the deliberate large control radius. */
    val input: Dp = 26.dp
    fun inputShape(): Shape = RoundedCornerShape(input)
    /** Full pill: chips, status indicators, selected modes ONLY. */
    const val pillDp: Int = 999
    val pill: Shape = RoundedCornerShape(999.dp)
    val circle: Shape = CircleShape
}

// ---------------------------------------------------------------------------
// Elevation — border-led system. Flat (border only) is the default; elevated
// adds one soft shadow step; floating is reserved for true overlays.
// ---------------------------------------------------------------------------

object GsElevation {
    val flat: Dp = 0.dp
    val elevated: Dp = 2.dp
    val floating: Dp = 8.dp
}

// ---------------------------------------------------------------------------
// Text roles beyond the Material slots (code, metadata, button).
// ---------------------------------------------------------------------------

@Immutable
data class GsTextSet(
    /** Inline/block code — its own readable system, never interface type. */
    val code: androidx.compose.ui.text.TextStyle,
    /** Code block body (slightly larger). */
    val codeBlock: androidx.compose.ui.text.TextStyle,
    /** Timestamps, counts, file sizes. */
    val metadata: androidx.compose.ui.text.TextStyle,
    /** Filled/outlined button label. */
    val button: androidx.compose.ui.text.TextStyle,
)

val LocalGsTextSet = staticCompositionLocalOf {
    GsTextSet(
        code = androidx.compose.ui.text.TextStyle(
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Normal,
            fontSize = androidx.compose.ui.unit.TextUnit.Unspecified,
        ),
        codeBlock = androidx.compose.ui.text.TextStyle(),
        metadata = androidx.compose.ui.text.TextStyle(),
        button = androidx.compose.ui.text.TextStyle(),
    )
}

/** Maps the semantic palette onto Material3 slots so M3 components follow the system. */
internal fun GsColors.toMaterialScheme(dark: Boolean): ColorScheme {
    val onErrorContainer = if (dark) Color(0xFFFFD9D9) else Color(0xFF7A1E1A)
    return if (dark) {
        darkColorScheme(
            primary = accent,
            onPrimary = onAccent,
            primaryContainer = accentSoft,
            onPrimaryContainer = accentStrong,
            secondary = accentStrong,
            onSecondary = onAccent,
            secondaryContainer = accentSoft,
            onSecondaryContainer = textPrimary,
            tertiary = streaming,
            onTertiary = onAccent,
            background = appBackground,
            onBackground = textPrimary,
            surface = surface,
            onSurface = textPrimary,
            surfaceVariant = secondaryBackground,
            onSurfaceVariant = textSecondary,
            surfaceContainerLowest = appBackground,
            surfaceContainerLow = inputSurface,
            surfaceContainer = raisedSurface,
            surfaceContainerHigh = elevatedSurface,
            surfaceContainerHighest = elevatedSurface,
            outline = border,
            outlineVariant = divider,
            error = error,
            onError = onError,
            errorContainer = errorSoft,
            onErrorContainer = onErrorContainer,
            scrim = scrim,
        )
    } else {
        lightColorScheme(
            primary = accent,
            onPrimary = onAccent,
            primaryContainer = accentSoft,
            onPrimaryContainer = accentStrong,
            secondary = accentStrong,
            onSecondary = onAccent,
            secondaryContainer = accentSoft,
            onSecondaryContainer = textPrimary,
            tertiary = streaming,
            onTertiary = onAccent,
            background = appBackground,
            onBackground = textPrimary,
            surface = surface,
            onSurface = textPrimary,
            surfaceVariant = secondaryBackground,
            onSurfaceVariant = textSecondary,
            surfaceContainerLowest = surface,
            surfaceContainerLow = inputSurface,
            surfaceContainer = raisedSurface,
            surfaceContainerHigh = elevatedSurface,
            surfaceContainerHighest = elevatedSurface,
            outline = border,
            outlineVariant = divider,
            error = error,
            onError = onError,
            errorContainer = errorSoft,
            onErrorContainer = onErrorContainer,
            scrim = scrim,
        )
    }
}
