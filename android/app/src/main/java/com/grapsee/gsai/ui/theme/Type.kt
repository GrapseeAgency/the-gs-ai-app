package com.grapsee.gsai.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * REFERENCE TYPE RAMP.
 *
 * One family (Poppins) across the whole product, three voices inside it.
 *
 * MEASURED, NOT ASSUMED, AND IT CUTS AGAINST THE MOCK: the sheet's greeting is
 * set in a narrower grotesque with a DOUBLE-STOREY 'g' (SF Pro / Inter shape),
 * not Poppins' single-storey one — at matched cap height "Good morning," spans
 * 159pt in the mock, 193pt in Poppins and 171pt in SF Pro. So Poppins is the
 * product's family by decision, and the mock's face is not it; the sizes and
 * weights below are still taken from the mock's metrics, but nobody should
 * claim the letterforms match. Poppins is also the only face bundled for both
 * platforms, which is what makes it the parity choice (mandate b10).
 *  · display / headline — SemiBold, tight tracking, generous line height;
 *  · body — Regular, 15sp, 23sp leading (a reading measure, not a label);
 *  · label / metadata — Medium, tracked +0.2sp so small text stays crisp.
 *
 * The old serif display tier is gone. In the reference language the page
 * title is a confident sans line sitting on the bloom — a serif headline read
 * as editorial whereas this product is a companion. Sizes are deliberately
 * compact: the reference fits a complete settings page above the fold.
 *
 * Mirrors ios/App/Sources/Theme/DesignSystem.swift.
 */
/**
 * This style with its tracking resolved to zero.
 *
 * Home's copy contract, on one platform's terms. iOS renders no letter spacing
 * at all — `Aero.responsive` maps a role to a font and stops there — so every
 * positive tracking value in the ramp below is a width Android adds and iOS
 * does not. At 12sp over the 44 characters of "Turn your ideas into beautiful
 * videos in minutes." that difference is one word, and the word fell to a third
 * line: the video card read two lines on iOS and in the sheet, three here.
 *
 * Only POSITIVE tracking is zeroed. The display tier's negative tracking narrows
 * toward the sheet's narrower grotesque (see the header), and no line break on
 * the page depends on it; that iOS does not apply it at all is recorded with the
 * measured numbers in shared-contracts/home-type-roles.json rather than here.
 */
fun TextStyle.untracked(): TextStyle = copy(letterSpacing = 0.sp)

val GsTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = GsFonts.Poppins,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 42.sp,
        letterSpacing = (-0.6).sp
    ),
    displayMedium = TextStyle(
        fontFamily = GsFonts.Poppins,
        fontWeight = FontWeight.SemiBold,
        fontSize = 30.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.5).sp
    ),
    displaySmall = TextStyle(
        fontFamily = GsFonts.Poppins,
        fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp,
        lineHeight = 33.sp,
        letterSpacing = (-0.3).sp
    ),
    headlineLarge = TextStyle(
        fontFamily = GsFonts.Poppins,
        fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp,
        lineHeight = 33.sp,
        letterSpacing = (-0.3).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = GsFonts.Poppins,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 29.sp,
        letterSpacing = (-0.2).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = GsFonts.Poppins,
        fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.1).sp
    ),
    titleLarge = TextStyle(
        fontFamily = GsFonts.Poppins,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 23.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = GsFonts.Poppins,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 21.sp,
        letterSpacing = 0.sp
    ),
    titleSmall = TextStyle(
        fontFamily = GsFonts.Poppins,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.1.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = GsFonts.Poppins,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 23.sp,
        letterSpacing = 0.1.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = GsFonts.Poppins,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp,
        letterSpacing = 0.1.sp
    ),
    bodySmall = TextStyle(
        fontFamily = GsFonts.Poppins,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 17.sp,
        letterSpacing = 0.1.sp
    ),
    labelLarge = TextStyle(
        fontFamily = GsFonts.Poppins,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 19.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = GsFonts.Poppins,
        fontWeight = FontWeight.Medium,
        fontSize = 11.5.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.2.sp
    ),
    labelSmall = TextStyle(
        fontFamily = GsFonts.Poppins,
        fontWeight = FontWeight.Medium,
        fontSize = 10.5.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.25.sp
    )
)

/** Code keeps a platform monospace face: it is a different material. */
val GsCodeFamily: FontFamily = FontFamily.Monospace
