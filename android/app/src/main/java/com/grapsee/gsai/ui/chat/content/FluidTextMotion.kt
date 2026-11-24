package com.grapsee.gsai.ui.chat.content

import java.text.BreakIterator
import java.util.Locale

/** Pure, bounded visual clock. It never queues, truncates or delays response text. */
internal data class FluidTextBand(
    val start: Int,
    val end: Int,
    val bornNanos: Long,
    val durationNanos: Long,
    val phaseFrom: Float = 0f,
    val phaseTo: Float = 1f,
    val travel: Float = 0.24f
) {
    fun progress(now: Long): Float =
        ((now - bornNanos).toDouble() / durationNanos).coerceIn(0.0, 1.0).toFloat()

    /** The optical front travels through the shaped run, not as a chunk flash.
     * Every point starts readable and reaches full ink at the same deadline. */
    fun localProgress(now: Long, position: Float): Float =
        fluidLocalProgress(progress(now), phaseFrom + (phaseTo - phaseFrom) * position.coerceIn(0f, 1f), travel)

    fun opacity(now: Long, position: Float = 0f): Float =
        fluidInkOpacity(localProgress(now, position))

    fun fractionAt(offset: Int): Float =
        ((offset - start).toFloat() / (end - start).coerceAtLeast(1)).coerceIn(0f, 1f)
}

internal fun fluidLocalProgress(progress: Float, position: Float, travel: Float = 0.24f): Float =
    ((progress - travel * position.coerceIn(0f, 1f)) / (1f - travel)).coerceIn(0f, 1f)

internal fun fluidInkOpacity(progress: Float): Float {
    val p = progress.coerceIn(0f, 1f)
    // A small immediate response, followed by a soft accelerating front and
    // a zero-velocity finish. Pure quintic starts too slowly on tiny deltas.
    val q = 1f - p
    val quintic = p * p * p * (p * (p * 6f - 15f) + 10f)
    val eased = 0.22f * (1f - q * q * q) + 0.78f * quintic
    return (0.18f + 0.82f * eased).coerceIn(0.18f, 1f)
}

/** Initial/mounted text is already settled. Only a genuine append earns motion. */
internal class FluidTextMotion(initial: String) {
    private var previous = initial
    private var lastArrival: Long? = null
    private var cadenceMs = 42.0
    // This is the optical front's arrival cursor, not a text delivery queue.
    // A received range is present from the first frame, even while its
    // leading contrast is low. The cursor never runs far ahead of real time.
    private var frontUntilNanos = 0L
    var bands: List<FluidTextBand> = emptyList()
        private set

    fun update(text: String, now: Long, enabled: Boolean, sourceAppend: Boolean = false): List<FluidTextBand> {
        if (!enabled || (!sourceAppend && !text.startsWith(previous))) {
            previous = text
            lastArrival = null
            cadenceMs = 42.0
            frontUntilNanos = now
            bands = emptyList()
            return bands
        }
        var alive = bands.filter { it.progress(now) < 1f }
        if (text == previous) {
            bands = alive
            return bands
        }
        // A Markdown close can remove displayed delimiters even though the
        // actual provider source only grew. Preserve the existing ink ages.
        val oldLength: Int
        if (text.startsWith(previous)) {
            oldLength = previous.length
        } else {
            val match = reconcileFluidAppend(previous, text)
            alive = match.spans.flatMap { span ->
                alive.mapNotNull { band ->
                    val from = maxOf(span.oldStart, band.start)
                    val to = minOf(span.oldEnd, band.end)
                    if (from >= to) null else {
                        val phaseFrom = band.phaseFrom + (band.phaseTo - band.phaseFrom) * band.fractionAt(from)
                        val phaseTo = band.phaseFrom + (band.phaseTo - band.phaseFrom) * band.fractionAt(to)
                        band.copy(start = span.newStart + from - span.oldStart,
                            end = span.newStart + to - span.oldStart,
                            phaseFrom = phaseFrom, phaseTo = phaseTo)
                    }
                }
            }.sortedBy { it.start }
            oldLength = match.newTailStart
        }
        previous = text
        lastArrival?.let {
            val sample = ((now - it) / 1_000_000.0).coerceIn(16.0, 160.0)
            cadenceMs = cadenceMs * 0.78 + sample * 0.22
        }
        lastArrival = now
        // A low-pass cadence estimator prevents packet timing from changing
        // the animation speed violently from one frame to the next.
        val duration = (175.0 + cadenceMs * 0.85).coerceIn(185.0, 265.0).toLong() * 1_000_000L
        // Carry the front across packet boundaries. A burst may broaden the
        // front, but cannot accumulate a fake typing backlog.
        val horizon = now + 75_000_000L
        var front = maxOf(now, minOf(frontUntilNanos, horizon))
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
            // A long real packet has a longer spatial front, not a uniform
            // flash. Small packets still overlap without withholding content.
            val length = end - start
            val travel = (0.18f + length.coerceAtMost(120) * 0.0025f).coerceIn(0.18f, 0.48f)
            incoming += FluidTextBand(start, end, front, duration, travel = travel)
            // The next range joins while this one is still resolving. The
            // maximum optical lead is 75ms regardless of provider speed.
            val advance = (9_000_000L + length.coerceAtMost(60) * 1_000_000L).coerceAtMost(45_000_000L)
            front = minOf(horizon, front + advance)
            start = end
        }
        // An in-flight range is never evicted just because another packet
        // arrives. Only its own deadline (or a genuine rewrite) settles it.
        // Cost is time-bounded by the publisher's coalescing interval, with at
        // most four ranges per received snapshot, not by response length.
        if (incoming.isNotEmpty()) frontUntilNanos = front
        bands = alive.filter {
            atOrAfter(it.start) == it.start && atOrAfter(it.end) == it.end
        } + incoming
        return bands
    }
}

/** Exact rendered-text matches only. Source append is checked by the caller.
 * A conservative unique tail anchor bridges formatting edits without guessing
 * at replaced words, replaying history, or treating a provider reset as growth. */
internal data class FluidMatchSpan(val oldStart: Int, val oldEnd: Int, val newStart: Int)
internal data class FluidAppendMatch(val spans: List<FluidMatchSpan>, val newTailStart: Int)
internal fun reconcileFluidAppend(old: String, next: String): FluidAppendMatch {
    val oldBoundary = BreakIterator.getCharacterInstance(Locale.ROOT).apply { setText(old) }
    val newBoundary = BreakIterator.getCharacterInstance(Locale.ROOT).apply { setText(next) }
    var prefix = 0
    val limit = minOf(old.length, next.length)
    while (prefix < limit && old[prefix] == next[prefix]) prefix++
    while (prefix > 0 && (!oldBoundary.isBoundary(prefix) || !newBoundary.isBoundary(prefix))) prefix--
    val spans = mutableListOf<FluidMatchSpan>()
    if (prefix > 0) spans += FluidMatchSpan(0, prefix, 0)
    // A prefix followed only by formatting punctuation is already read.
    val syntax = "*_~`[]()"
    var oldEnd = old.length
    while (oldEnd > prefix && old.length - oldEnd < 4 && old[oldEnd - 1] in syntax) oldEnd--
    if (oldEnd == prefix) return FluidAppendMatch(spans, prefix)
    var anchor = maxOf(prefix, oldEnd - 12)
    while (anchor < oldEnd && !oldBoundary.isBoundary(anchor)) anchor++
    if (oldEnd - anchor < 3 || !oldBoundary.isBoundary(oldEnd)) return FluidAppendMatch(spans, next.length)
    val needle = old.substring(anchor, oldEnd)
    val at = next.indexOf(needle, prefix)
    if (at < 0 || next.indexOf(needle, at + 1) >= 0 ||
        !newBoundary.isBoundary(at) || !newBoundary.isBoundary(at + needle.length)) return FluidAppendMatch(spans, next.length)
    var oldStart = anchor
    var newStart = at
    while (oldStart > prefix && newStart > prefix && old[oldStart - 1] == next[newStart - 1]) {
        oldStart--; newStart--
    }
    while (oldStart < anchor && (!oldBoundary.isBoundary(oldStart) || !newBoundary.isBoundary(newStart))) {
        oldStart++; newStart++
    }
    spans += FluidMatchSpan(oldStart, oldEnd, newStart)
    return FluidAppendMatch(spans, at + needle.length)
}

/** A continuous piecewise-linear projection of the ink curve into native
 *  text space. Stops at the clamp boundaries prevent flat spots and avoid
 *  quantizing narrow optical fronts to glyph or packet boundaries. */
internal data class FluidInkStop(val position: Float, val opacity: Float)
internal fun fluidInkStops(progress: Float, phaseFrom: Float, phaseTo: Float, travel: Float): List<FluidInkStop> {
    val start = (progress - travel * phaseFrom) / (1f - travel)
    val end = (progress - travel * phaseTo) / (1f - travel)
    val delta = end - start
    val stops = mutableListOf(FluidInkStop(0f, fluidInkOpacity(start)))
    if (kotlin.math.abs(delta) > 0.000001f) {
        val interior = (0..16).mapNotNull { index ->
            val p = index / 16f
            val x = (p - start) / delta
            if (x > 0.00001f && x < 0.99999f) FluidInkStop(x, fluidInkOpacity(p)) else null
        }.sortedBy { it.position }
        stops.addAll(interior)
    }
    stops.add(FluidInkStop(1f, fluidInkOpacity(end)))
    return stops
}
