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
 * WARM CONSUMER — SEMANTIC design tokens.
 *
 * This is the one authoritative vocabulary for the app's visual foundation.
 * Screens and components consume `GsTheme.colors.*`, `GsSpacing`, `GsRadius`,
 * `GsElevation` and `GsTheme.textStyles` — never raw `Color(0x…)` literals and
 * never the raw `Aeruo.*` registry.
 *
 * Every colour below is theme-resolved (light + dark constructors) and every
 * text/border role has a high-contrast variant. The accent family stays warm
 * peach/orange across appearances. Mirrors iOS DesignSystem.swift.
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

    // ---- Glass (the reference's signature material) --------------------------
    // The reference light is a large soft source above and slightly LEFT, in
    // every one of the source screens. That single fact is what makes these
    // surfaces read as glass rather than plastic, so the rim is split into a
    // lit end and an unlit end rather than being one uniform hairline: the
    // top-left edge catches the light, the bottom-right edge falls away.
    val glassSurface: Color,         // translucent card fill over the bloom
    val glassBorder: Color,          // warm hairline around glass (unlit reference)
    val glassHighlight: Color,       // top-edge inner light / lit end of the rim
    val glassRimShadow: Color,       // unlit end of the directional rim
    // Glass casts light through itself, so its shadow is never neutral grey:
    // it carries the warm hue of the scene and is far wider than its offset.
    val glassShadowAmbient: Color,   // warm, wide, low-contrast contact shadow
    val glassShadowSpot: Color,      // warm, diffuse cast shadow (soft source)

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

/**
 * Light appearance — blush paper, cream glass, apricot ink.
 *
 * `accentId` resolves one of the six families in [GsAccents]; every accent
 * role below is derived from it, so Appearance → Accent colour is a real
 * re-theme rather than a swatch that does nothing.
 */
fun lightGsColors(highContrast: Boolean = false, accentId: String = "apricot"): GsColors {
    val accent = GsAccents.byId(accentId)
    val textSecondary = if (highContrast) Color(0xFF5C4138) else Aeruo.InkMuted
    val textTertiary = if (highContrast) Color(0xFF74584F) else Color(0xFF9C7F75)
    val border = if (highContrast) Color(0xFFC99682) else Aeruo.OutlineLight
    return GsColors(
        appBackground = Aeruo.Paper,
        secondaryBackground = Aeruo.ContainerLowLight,
        surface = Aeruo.SurfaceLight,
        elevatedSurface = Color(0xFFFFFFFF),
        raisedSurface = Aeruo.SurfaceRaisedLight,
        inputSurface = Color(0xFFFFF7F2),
        sheetSurface = Color(0xFFFFFBF8),
        dialogSurface = Color(0xFFFFFCFA),
        navSurface = Color(0xFFFFFBF8),

        glassSurface = Color(0xCCFFFDFB),
        glassBorder = Color(0xCCF4DBD0),
        glassHighlight = Color(0xE6FFFFFF),
        glassRimShadow = Color(0x30D9A08A),
        glassShadowAmbient = Color(0x14A65A38),
        glassShadowSpot = Color(0x1FA65A38),

        textPrimary = if (highContrast) Color(0xFF1F1210) else Aeruo.Ink,
        textSecondary = textSecondary,
        textTertiary = textTertiary,
        textDisabled = Color(0xFFB9A49C),
        textPlaceholder = Color(0xFFA5897F),
        textInverted = Color(0xFFFFFFFF),
        // Links were previously ink-on-ink: a tappable line that looked
        // exactly like body copy. They now carry the accent.
        link = accent.strong,

        border = border,
        borderStrong = if (highContrast) Color(0xFFB07A64) else Color(0xFFF0C4B0),
        divider = Aeruo.DividerLight,
        selected = accent.base,
        pressedOverlay = accent.soft,
        focus = accent.base,
        scrim = Color(SCRIM_LIGHT),

        accent = accent.base,
        accentStrong = accent.strong,
        accentSoft = accent.soft,
        onAccent = accent.on,
        success = Aeruo.SuccessLight,
        successSoft = Color(0x1A2F7D57),
        warning = Aeruo.WarningLight,
        warningSoft = Color(0x1A94650F),
        error = Aeruo.DangerLight,
        errorSoft = Color(0x1AC94B4B),
        onError = Color(0xFFFFFFFF),
        info = Aeruo.InfoLight,
        infoSoft = Color(0x1A2C74C4),

        aiActive = accent.base,
        streaming = textSecondary,
        toolExecution = textSecondary,
        research = textSecondary,
        voice = accent.base,
        generation = textSecondary,

        codeSurface = Aeruo.CodeSurfaceLight,
        codeText = Aeruo.CodeTextLight,
        codeKeyword = Aeruo.SyntaxKwLight,
        codeString = Aeruo.SyntaxStrLight,
        codeComment = Aeruo.SyntaxComLight,
        codeNumber = Aeruo.SyntaxNumLight,

        aurora = listOf(accent.pale, accent.base, accent.strong),
    )
}

/** Dark appearance — warm cocoa surfaces with candlelit accents. */
fun darkGsColors(highContrast: Boolean = false, accentId: String = "apricot"): GsColors {
    val family = GsAccents.byId(accentId)
    // On espresso, the saturated base is too heavy for small text; the pale
    // sibling keeps the accent's identity while lifting contrast.
    val accent = family.pale
    val textSecondary = if (highContrast) Color(0xFFFFE3D7) else Aeruo.TextMutedDark
    val textTertiary = if (highContrast) Color(0xFFE7C6BA) else Aeruo.TextTertiaryDark
    return GsColors(
        appBackground = Aeruo.Obsidian,
        secondaryBackground = Aeruo.ContainerLowDark,
        surface = Aeruo.SurfaceDark,
        elevatedSurface = Aeruo.ContainerDark,
        raisedSurface = Aeruo.RaisedDark,
        inputSurface = Aeruo.ContainerLowDark,
        sheetSurface = Color(0xFF2B1D1A),
        dialogSurface = Color(0xFF2E1F1C),
        navSurface = Color(0xFF211613),

        glassSurface = Color(0xD92A1C19),
        glassBorder = Color(0xCC51362F),
        glassHighlight = Color(0x33FFE7DE),
        glassRimShadow = Color(0x4A150B08),
        glassShadowAmbient = Color(0x590B0504),
        glassShadowSpot = Color(0x73150A07),

        textPrimary = if (highContrast) Color(0xFFFFFFFF) else Aeruo.TextDark,
        textSecondary = textSecondary,
        textTertiary = textTertiary,
        textDisabled = Color(0xFF7E5F56),
        textPlaceholder = Color(0xFFB2958B),
        textInverted = Aeruo.Obsidian,
        link = accent,

        border = if (highContrast) Color(0xFFFFD0BE) else Aeruo.OutlineDark,
        borderStrong = if (highContrast) Color(0xFFFFB393) else Color(0xFF6A443A),
        divider = Aeruo.DividerDark,
        selected = accent,
        pressedOverlay = family.base.copy(alpha = 0.18f),
        focus = accent,
        scrim = Color(SCRIM_DARK),

        accent = family.base,
        accentStrong = accent,
        accentSoft = family.base.copy(alpha = 0.22f),
        onAccent = family.on,
        success = Aeruo.SuccessDark,
        successSoft = Color(0x244CC38A),
        warning = Aeruo.WarningDark,
        warningSoft = Color(0x24E8B45A),
        error = Aeruo.DangerDark,
        errorSoft = Color(0x24E5484D),
        onError = Color(0xFFFFFFFF),
        info = Aeruo.InfoDark,
        infoSoft = Color(0x246CB8FF),

        aiActive = accent,
        streaming = textSecondary,
        toolExecution = textSecondary,
        research = textSecondary,
        voice = accent,
        generation = textSecondary,

        codeSurface = Aeruo.CodeSurfaceDark,
        codeText = Aeruo.CodeTextDark,
        codeKeyword = Aeruo.SyntaxKwDark,
        codeString = Aeruo.SyntaxStrDark,
        codeComment = Aeruo.SyntaxComDark,
        codeNumber = Aeruo.SyntaxNumDark,

        aurora = listOf(family.pale, family.base, family.strong),
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
    val sm: Dp = 12.dp
    fun smShape(): Shape = RoundedCornerShape(sm)
    /** Medium controls: list rows, secondary buttons. */
    val md: Dp = 16.dp
    fun mdShape(): Shape = RoundedCornerShape(md)
    /** Cards and tiles. */
    val card: Dp = 20.dp
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
// Layout — the responsive foundation (STEP 2). One global content-width
// contract the whole shell builds on; screens adopt it through
// Modifier.gsContentWidth() / GsScreenScaffold as each is revisited.
// ---------------------------------------------------------------------------

object GsLayout {
    /**
     * Readable maximum content width. Phones (narrower than this) are
     * untouched; tablets, landscape and foldables center-clamp the shell's
     * content column instead of stretching text and controls edge to edge —
     * the same reading-column discipline as the benchmark AI apps.
     */
    val contentMaxWidth: Dp = 640.dp
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
