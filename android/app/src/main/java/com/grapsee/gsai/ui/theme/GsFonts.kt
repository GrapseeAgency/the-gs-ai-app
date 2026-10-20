package com.grapsee.gsai.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.grapsee.gsai.R

/**
 * The product typeface.
 *
 * Poppins is the family the reference product is drawn in: geometric, warm,
 * slightly rounded, and — critically — it keeps its personality at the small
 * sizes this interface uses for supporting lines. The four weights bundled
 * here are the only weights the UI asks for (400 / 500 / 600 / 700), so the
 * APK carries four files instead of a full family.
 *
 * SIL Open Font License 1.1 — see docs/TYPOGRAPHY.md. Monospace stays on the
 * platform face: code must look like code, not like marketing.
 */
object GsFonts {
    val Poppins: FontFamily = FontFamily(
        Font(R.font.poppins_regular, FontWeight.Normal),
        Font(R.font.poppins_medium, FontWeight.Medium),
        Font(R.font.poppins_semibold, FontWeight.SemiBold),
        Font(R.font.poppins_bold, FontWeight.Bold),
    )
}
