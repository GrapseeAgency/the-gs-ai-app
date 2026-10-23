package com.grapsee.gsai.ui.navigation.drawer

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Every number the drawer is allowed to know, in one place, so the sheet can
 * disagree with the code in exactly one file.
 *
 * `scripts/check_drawer_render.py` re-derives these from
 * `assets/reference/drawer-reference.png` on every CI run and fails if a value
 * here drifts from the derived contract (`shared-contracts/drawer-geometry.json`
 * by ±1.5 pt. The comment on each line is the sheet's own measurement, not an
 * intention.
 *
 * Why a drawer-local object rather than GsTokens: the plan forbids editing the
 * shared token files from this workstream (another agent is in them), and the
 * two neutral greys below are properties of this surface only — every other page
 * uses the warm tertiary. Keeping them here makes that difference explicit
 * instead of smuggling a cold grey into a warm palette.
 */
object DrawerMetrics {

    /** 529 px of a 673 px screen = 308.9 pt at 393. */
    const val panelRatio: Float = 0.786f

    /** The sheet's bottom-right arc fits r = 47.1 px. Top-right is square. */
    val cornerRadius = 28.dp

    /** The first section label's ink starts 25.1 pt from the panel's left. */
    val contentInset = 24.dp

    /** Search pill: 42.0 pt tall on the sheet, capsule ends. */
    val searchHeight = 42.dp
    val searchGlyph = 18.dp

    /** Hero ("New chat"): 53.7 pt tall, 20.6 pt radius, 42.3 pt orb. */
    val heroHeight = 54.dp
    val heroRadius = 20.dp
    val heroOrb = 42.dp
    val pillToHero = 15.dp          // sheet 15.2

    /** Destination rows: 44 pt discs one 45.2 pt pitch apart. */
    val disc = 44.dp
    val destPitch = 46.dp
    val discToTitle = 13.dp

    /** Recent chat rows are two lines, so they are 55.7 pt apart. */
    val recentPitch = 56.dp

    /** The row chevrons end 24.5 pt from the panel's right edge. */
    val chevron = 12.dp
    val chevronInset = 24.dp

    val wordmark = 22.sp
    val subtitle = 12.sp
    val searchText = 15.sp
    val heroLabel = 15.sp
    val sectionLabel = 10.5.sp
    val rowTitle = 13.sp
    val chatTitle = 13.sp
    val timestamp = 11.5.sp
    val footerName = 12.5.sp
    val footerSub = 11.5.sp

    /** Tracking on the section labels: +0.10 em on the sheet. */
    const val sectionTracking: Float = 0.10f

    val avatar = 36.dp
    val gear = 34.dp
    val gearGlyph = 15.dp
    val closeHit = 30.dp
    val closeGlyph = 10.dp
    val headerOrb = 56.dp

    /**
     * The dim behind the panel. Measured at 0.18–0.22 black against our own Home
     * band (the sheet's strip is 0.80 of the undimmed page), NOT the app's
     * SCRIM_LIGHT (0.50) and not Material 3's 0.32 default — both of which were
     * being composited before this pass, which is why the old drawer read as a
     * dark veil rather than a soft one.
     */
    val scrim = Color(0x33000000)

    // Measured ink. The labels are NEUTRAL grey (120,123,127) — cooler than
    // every other page's warm tertiary — and the timestamps lighter still.
    val labelInk = Color(0xFF787B7F)
    val timestampInk = Color(0xFF918C91)
    val chevronInk = Color(0xFF958D90)

    /**
     * The hero row's gradient, sampled at its two ends: warm apricot on the left,
     * the panel's own cream on the right. The gate asserts the R−B drop, so a
     * flattened gradient fails even though it still "looks fine".
     */
    val heroLeft = Color(0xFFFEE5C0)
    val heroRight = Color(0xFFF8EBE7)
    val heroOrbTop = Color(0xFFFFD8A8)
    val heroOrbBottom = Color(0xFFFEAB6C)

    val selectedFill = Color(0x1FFEA96B)
}
