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

    /** The optical front travels through the shaped run, not as a chunk flash.
     * Every point starts readable and reaches full ink at the same deadline. */
    fun localProgress(now: Long, position: Float): Float =
        fluidLocalProgress(progress(now), position)

    fun opacity(now: Long, position: Float = 0f): Float =
        fluidInkOpacity(localProgress(now, position))

    fun fractionAt(offset: Int): Float =
        ((offset - start).toFloat() / (end - start).coerceAtLeast(1)).coerceIn(0f, 1f)
}

internal fun fluidLocalProgress(progress: Float, position: Float): Float =
    ((progress - 0.24f * position.coerceIn(0f, 1f)) / 0.76f).coerceIn(0f, 1f)

internal fun fluidInkOpacity(progress: Float): Float {
    val p = progress.coerceIn(0f, 1f)
    // Quintic smoothstep: continuous velocity and acceleration at both ends.
    val eased = p * p * p * (p * (p * 6f - 15f) + 10f)
    return 0.18f + 0.82f * eased
}

/** Initial/mounted text is already settled. Only a genuine append earns motion. */
internal class FluidTextMotion(initial: String) {
    private var previous = initial
    private var lastArrival: Long? = null
    private var cadenceMs = 42.0
    var bands: List<FluidTextBand> = emptyList()
        private set

    fun update(text: String, now: Long, enabled: Boolean): List<FluidTextBand> {
        if (!enabled || !text.startsWith(previous)) {
            previous = text
            lastArrival = null
            cadenceMs = 42.0
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
        lastArrival?.let {
            val sample = ((now - it) / 1_000_000.0).coerceIn(16.0, 160.0)
            cadenceMs = cadenceMs * 0.78 + sample * 0.22
        }
        lastArrival = now
        // A low-pass cadence estimator prevents packet timing from changing
        // the animation speed violently from one frame to the next.
        val duration = (175.0 + cadenceMs * 0.85).coerceIn(185.0, 265.0).toLong() * 1_000_000L
        val stagger = (cadenceMs * 0.25).coerceIn(8.0, 20.0).toLong() * 1_000_000L
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

        // Every received grapheme participates. Group a large packet into
        // a few longer waves rather than jumping over its leading characters.
        var start = atOrAfter(oldLength)
        val groupSize = maxOf(32, (text.length - start + 3) / 4)
        val incoming = mutableListOf<FluidTextBand>()
        while (start < text.length && incoming.size < 4) {
            val ideal = minOf(text.length, start + groupSize)
            // Prefer a word boundary near the target. Long/unspaced scripts
            // still use grapheme boundaries; never split an emoji or accent.
            var word = ideal
            while (word > maxOf(start + 1, ideal - 6) && word < text.length &&
                !text[word - 1].isWhitespace() && !text[word].isWhitespace()) word--
            val preferred = if (word > maxOf(start + 1, ideal - 6)) word else ideal
            val end = if (incoming.size == 3) text.length else atOrAfter(preferred)
            if (end <= start) break
            incoming += FluidTextBand(start, end, now + incoming.size * stagger, duration)
            start = end
        }
        // An in-flight range is never evicted just because another packet
        // arrives. Only its own deadline (or a genuine rewrite) settles it.
        // Cost is time-bounded by the publisher's coalescing interval, with at
        // most four ranges per received snapshot, not by response length.
        bands = alive.filter {
            atOrAfter(it.start) == it.start && atOrAfter(it.end) == it.end
        } + incoming
        return bands
    }
}
