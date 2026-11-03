package com.grapsee.gsai.ui.chat

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WHY THIS TEST EXISTS
 *
 * The owner, looking at a real screenshot of his own app: "message ta tai thik
 * bhabe buja jacche na" — the sentence he had just typed was the least legible
 * thing on the chat screen. Two separate causes, both invisible to a unit test
 * that only checks "a Text with the content exists":
 *
 *   1. the ink was a fixed mid-grey (0xFF4A4750) while the bubble behind it was
 *      a 0.72–0.86 alpha lavender gradient — the bloom ran through the glyphs;
 *   2. in dark mode the bubble became a 0.54-alpha dark surface and the ink
 *      stayed that same grey, which is roughly 1.2:1 — the text was, literally,
 *      not there.
 *
 * The palette is now data ([ChatBubblePalette]) precisely so this can be
 * measured instead of eyeballed. WCAG AA for body text is 4.5:1; the bubble is
 * a short, dense, high-contrast block, so the bar here is AAA (7:1) for the
 * message itself and AA for the timestamp.
 */
class ChatBubblePaletteTest {

    private fun channel(c: Float): Double {
        val v = c.toDouble()
        return if (v <= 0.03928) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4)
    }

    private fun luminance(color: Color): Double =
        0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)

    private fun contrast(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        val hi = maxOf(la, lb)
        val lo = minOf(la, lb)
        return (hi + 0.05) / (lo + 0.05)
    }

    @Test
    fun `the reader's own sentence clears AAA in the light theme`() {
        val p = ChatBubblePalette.of(dark = false)
        val ratio = contrast(p.ink, p.top)
        assertTrue(
            "user-turn ink on its bubble is only %.2f:1 in light mode — the " +
                "sentence must be readable, not merely present".format(ratio),
            ratio >= 7.0
        )
    }

    @Test
    fun `the reader's own sentence clears AAA in the dark theme`() {
        val p = ChatBubblePalette.of(dark = true)
        val ratio = contrast(p.ink, p.top)
        assertTrue(
            "user-turn ink on its bubble is only %.2f:1 in dark mode — this is " +
                "the regression that made the text invisible".format(ratio),
            ratio >= 7.0
        )
    }

    @Test
    fun `the timestamp clears AA in both themes`() {
        for (dark in listOf(false, true)) {
            val p = ChatBubblePalette.of(dark)
            val ratio = contrast(p.meta, p.top)
            assertTrue(
                "timestamp ink is only %.2f:1 (dark=$dark)".format(ratio),
                ratio >= 4.5
            )
        }
    }

    @Test
    fun `the fill is dense enough to hold text`() {
        // Glass belongs to the container, never to the words: anything under
        // 90% lets the bloom's texture show through the glyph edges.
        for (dark in listOf(false, true)) {
            val p = ChatBubblePalette.of(dark)
            assertTrue("top fill too thin (dark=$dark)", p.top.alpha >= 0.90f)
            assertTrue("bottom fill too thin (dark=$dark)", p.bottom.alpha >= 0.90f)
        }
    }
}
