package com.grapsee.gsai.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * REFERENCE PALETTE — raw values only.
 *
 * The product lives on a warm, luminous peach canvas: cream glass panels,
 * apricot actions, cocoa ink. The values below were tuned against the
 * reference screens at full brightness — the background is a blush paper,
 * never white, and every card is translucent enough that the bloom behind
 * it survives as a colour cast.
 *
 * No UI code outside this package reads `Aeruo.*`. Screens consume semantic
 * roles from [GsColors], so the six selectable accent families below
 * re-theme every control without touching a call site.
 */
object Aeruo {
    // ---- light appearance ---------------------------------------------------
    // Sampled from the sheet: cream paper, neutral ink — not warm-on-warm.
    val Paper = Color(0xFFF8F1EA)
    val SurfaceLight = Color(0xFFFBF5EF)
    val SurfaceRaisedLight = Color(0xFFFFF6F0)
    val Ink = Color(0xFF1A2026)
    val InkMuted = Color(0xFF64686F)
    val InkTertiary = Color(0xFF85888F)
    val OutlineLight = Color(0xFFF2D9CF)
    val DividerLight = Color(0xFFF7E7DF)
    val ContainerLowLight = Color(0xFFFFF1EA)
    val ContainerLight = Color(0xFFFFE7DB)
    val ContainerHighLight = Color(0xFFFFD8C5)

    // ---- dark appearance (warm cocoa, never grey chrome) --------------------
    val Obsidian = Color(0xFF1C1211)
    val SurfaceDark = Color(0xFF2A1C19)
    val RaisedDark = Color(0xFF33231F)
    val TextDark = Color(0xFFFFF5F0)
    val TextMutedDark = Color(0xFFD6B8AE)
    val TextTertiaryDark = Color(0xFFAD8B80)
    val OutlineDark = Color(0xFF4B2F28)
    val DividerDark = Color(0xFF3D2722)
    val ContainerLowDark = Color(0xFF2A1C19)
    val ContainerDark = Color(0xFF35241F)
    val ContainerHighDark = Color(0xFF432D26)

    // ---- default accent (kept for the semantic constructors) ---------------
    val Accent = Color(0xFFFFAC7E)
    val AccentDeep = Color(0xFFF0743F)
    val AccentSoftDark = Color(0x33FFAC7E)
    val AccentSoftLight = Color(0x1FF0743F)

    // ---- status -------------------------------------------------------------
    val DangerLight = Color(0xFFC94B4B)
    val DangerDark = Color(0xFFF07A7A)
    val SuccessLight = Color(0xFF2F7D57)
    val SuccessDark = Color(0xFF63C79A)
    val WarningLight = Color(0xFF94650F)
    val WarningDark = Color(0xFFE8B45A)
    val InfoLight = Color(0xFF2C74C4)
    val InfoDark = Color(0xFF7CBCFF)

    // ---- code ink well ------------------------------------------------------
    val CodeSurfaceLight = Color(0xFFF6F1ED)
    val CodeSurfaceDark = Color(0xFF140F0E)
    val CodeTextLight = Color(0xFF2E2320)
    val CodeTextDark = Color(0xFFE4DAD6)
    val SyntaxKwLight = Color(0xFF8A4BC2)
    val SyntaxKwDark = Color(0xFFC792EA)
    val SyntaxStrLight = Color(0xFF2F7D57)
    val SyntaxStrDark = Color(0xFFC3E88D)
    val SyntaxComLight = Color(0xFF7A6A63)
    val SyntaxComDark = Color(0xFF9A8B84)
    val SyntaxNumLight = Color(0xFFC0562C)
    val SyntaxNumDark = Color(0xFFF78C6C)

    /** Streaming shimmer and other neutral life-signs. */
    val GrayBright = Color(0xFFF2F3F5)
    val GrayMid = Color(0xFF8B93A1)

    /** Kept for callers that still draw the legacy aurora brush. */
    val Aurora = listOf(Color(0xFFFFB98C), Color(0xFFFF945F), Color(0xFFF0743F))
}

/**
 * The six accent families offered in Appearance → Accent colour.
 *
 * Each is a complete role set, not a single hue: a selected pill needs a
 * fill, a soft tint, an ink that stays readable on it, and a "strong" value
 * for emphasis text. All six live inside the warm-reference family so no
 * choice can break the product's temperature.
 */
data class GsAccentFamily(
    val id: String,
    val label: String,
    /** Filled controls, progress, selected states. */
    val base: Color,
    /** Emphasis text on cream, pressed fill, gradient end. */
    val strong: Color,
    /** Tinted wells (chips, icon tiles, selected rows). */
    val soft: Color,
    /** Text/glyph laid directly on [base]. */
    val on: Color,
    /** A pale companion for gradients, rings and illustration. */
    val pale: Color,
)

object GsAccents {
    val Apricot = GsAccentFamily(
        id = "apricot",
        label = "Apricot",
        base = Color(0xFFF0743F),
        strong = Color(0xFFD95E2C),
        soft = Color(0x1FF0743F),
        on = Color(0xFFFFFFFF),
        pale = Color(0xFFFFB98C),
    )
    val Rose = GsAccentFamily(
        id = "rose",
        label = "Rose",
        base = Color(0xFFDD6F8C),
        strong = Color(0xFFC25573),
        soft = Color(0x1FDD6F8C),
        on = Color(0xFFFFFFFF),
        pale = Color(0xFFFFB8C9),
    )
    val Lilac = GsAccentFamily(
        id = "lilac",
        label = "Lilac",
        base = Color(0xFF8C72DC),
        strong = Color(0xFF7458C7),
        soft = Color(0x1F8C72DC),
        on = Color(0xFFFFFFFF),
        pale = Color(0xFFC9B9FF),
    )
    val Sky = GsAccentFamily(
        id = "sky",
        label = "Sky",
        base = Color(0xFF4A96D8),
        strong = Color(0xFF357CBB),
        soft = Color(0x1F4A96D8),
        on = Color(0xFFFFFFFF),
        pale = Color(0xFFA9D3F5),
    )
    val Sage = GsAccentFamily(
        id = "sage",
        label = "Sage",
        base = Color(0xFF44A07A),
        strong = Color(0xFF328563),
        soft = Color(0x1F44A07A),
        on = Color(0xFFFFFFFF),
        pale = Color(0xFFAEDCC9),
    )
    val Honey = GsAccentFamily(
        id = "honey",
        label = "Honey",
        base = Color(0xFFC98B2E),
        strong = Color(0xFFAA701F),
        soft = Color(0x1FC98B2E),
        on = Color(0xFFFFFFFF),
        pale = Color(0xFFF0CE94),
    )

    val all = listOf(Apricot, Rose, Lilac, Sky, Sage, Honey)

    fun byId(id: String?): GsAccentFamily =
        all.firstOrNull { it.id == id } ?: Apricot
}
