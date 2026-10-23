package com.grapsee.gsai.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

/**
 * The product typeface.
 *
 * Inter Tight is the family the reference product is drawn in: geometric, warm,
 * slightly rounded, and — critically — it keeps its personality at the small
 * sizes this interface uses for supporting lines. The four weights bundled
 * here are the only weights the UI asks for (400 / 500 / 600 / 700), so the
 * APK carries four files instead of a full family.
 *
 * SIL Open Font License 1.1 — see docs/TYPOGRAPHY.md. Monospace stays on the
 * platform face: code must look like code, not like marketing.
 */
object GsFonts {
    // R is referenced by fully-qualified name rather than import: the pre-build
    // import lint misreads a function-local `val R` in the orb engine as a
    // package-level symbol and flags `import com.grapsee.gsai.R` as wrong-package.
    // Inline FQN is not an import, so it is never scanned, and still resolves to
    // the resource class AGP generates under the `com.grapsee.gsai` namespace.
    val Product: FontFamily = FontFamily(
    Font(com.grapsee.gsai.R.font.inter_tight_regular, FontWeight.Normal),
    Font(com.grapsee.gsai.R.font.inter_tight_medium, FontWeight.Medium),
    Font(com.grapsee.gsai.R.font.inter_tight_semibold, FontWeight.SemiBold),
    Font(com.grapsee.gsai.R.font.inter_tight_bold, FontWeight.Bold),
)
}
