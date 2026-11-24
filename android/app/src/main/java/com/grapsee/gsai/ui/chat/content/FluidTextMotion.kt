package com.grapsee.gsai.ui.chat.content

import java.text.BreakIterator
import java.util.Locale

/** Pure, bounded visual clock. It never queues, truncates or delays response text. */
internal data class FluidTextBand(
    val start: Int,
    val end: Int,
    val bornNanos: Long,
    val durationNanos: Long
) {
    fun progress(now: Long): Float =
        ((now - bornNanos).toDouble() / durationNanos).coerceIn(0.0, 1.0).toFloat()

    fun opacity(now: Long): Float {
        val remaining = 1f - progress(now)
        return 1f - 0.42f * remaining * remaining * remaining
    }
}

/** Initial/mounted text is already settled. Only a genuine append earns motion. */
internal class FluidTextMotion(initial: String) {
    private var previous = initial
    private var lastArrival: Long? = null
    var bands: List<FluidTextBand> = emptyList()
        private set

    fun update(text: String, now: Long, enabled: Boolean): List<FluidTextBand> {
        if (!enabled || !text.startsWith(previous)) {
            previous = text
            lastArrival = null
            bands = emptyList()
            return bands
        }
        val alive = bands.filter { it.progress(now) < 1f }
        if (text == previous) {
            bands = alive
            return bands
        }
        val oldLength = previous.length
        previous = text
        val interval = lastArrival?.let { (now - it).coerceAtLeast(0L) / 1_000_000L }
        lastArrival = now
        val duration = (interval?.let { 100L + it / 2 } ?: 150L).coerceIn(100L, 150L) * 1_000_000L
        val boundary = BreakIterator.getCharacterInstance(Locale.ROOT).apply { setText(text) }

        // Do not animate an existing grapheme if an arriving combining mark extends it.
        fun atOrAfter(index: Int): Int {
            var pos = if (boundary.isBoundary(index)) index else boundary.following(index)
            while (pos in 1 until text.length) {
                val before = text.codePointBefore(pos)
                val after = text.codePointAt(pos)
                val type = Character.getType(after)
                if (before != 0x200D && after != 0x200D &&
                    type != Character.NON_SPACING_MARK.toInt() &&
                    type != Character.COMBINING_SPACING_MARK.toInt() &&
                    type != Character.ENCLOSING_MARK.toInt() &&
                    after !in 0x1F3FB..0x1F3FF &&
                    !(before in 0x1F1E6..0x1F1FF && after in 0x1F1E6..0x1F1FF)) break
                pos = boundary.following(pos)
            }
            return if (pos == BreakIterator.DONE) text.length else pos
        }

        val floor = atOrAfter(maxOf(oldLength, text.length - 48))
        var start = floor
        val incoming = mutableListOf<FluidTextBand>()
        while (start < text.length && incoming.size < 4) {
            val end = if (incoming.size == 3) text.length
                else atOrAfter(minOf(text.length, start + 12))
            if (end <= start) break
            incoming += FluidTextBand(start, end, now + incoming.size * 8_000_000L, duration)
            start = end
        }
        // Large chunks are readable immediately; only the last 48 UTF-16 units
        // receive a bounded, overlapping feather. Earlier ranges never replay.
        bands = (alive.filter {
            it.end > text.length - 48 && atOrAfter(it.start) == it.start && atOrAfter(it.end) == it.end
        } + incoming).takeLast(8)
        return bands
    }
}
