package com.grapsee.gsai.ui.chat

import androidx.compose.ui.graphics.Color

/**
 * The user turn's bubble, as data.
 *
 * It lives outside the composable for one reason: legibility is a measurable
 * property, not an opinion. The reader's own sentence is the one thing on the
 * chat surface that must never be hard to read, and the previous palette broke
 * that rule twice — a fixed mid-grey ink (0xFF4A4750) on a 0.72-alpha lavender
 * gradient in light mode, and the SAME grey on a 0.54-alpha dark surface in
 * dark mode, where it vanished into the bloom.
 *
 * So the ink is now derived from the fill in each theme, and
 * `ChatBubblePaletteTest` measures the resulting WCAG contrast ratio instead of
 * trusting the eye of whoever last edited the colours.
 */
internal data class ChatBubblePalette(
    val top: Color,
    val bottom: Color,
    val ink: Color,
    val meta: Color,
) {
    companion object {
        /**
         * @param dark the APP theme (not the OS theme — the pastel surface
         *   follows the app, see `LocalGsIsDark`).
         */
        fun of(dark: Boolean): ChatBubblePalette = if (dark) {
            ChatBubblePalette(
                // A dense tint, not a veil: 0.80/0.34 over a dark sheet keeps
                // the bubble distinct from the transcript without letting the
                // bloom's texture run through the glyphs.
                top = Color(0xFF2A2636).copy(alpha = 0.92f),
                bottom = Color(0xFF3A3153).copy(alpha = 0.92f),
                ink = Color(0xFFF2EEFF),
                meta = Color(0xFFC9C2E4),
            )
        } else {
            ChatBubblePalette(
                top = Color(0xFFEDE6FF).copy(alpha = 0.96f),
                bottom = Color(0xFFDCD1FF).copy(alpha = 0.92f),
                ink = Color(0xFF2C2740),
                meta = Color(0xFF5B5473),
            )
        }
    }
}
