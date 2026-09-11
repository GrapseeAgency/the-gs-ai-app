package com.grapsee.gsai.ui.chat.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PHASE 1 benchmark (Part 6/7 of the stabilisation brief): known-good
 * `0975be3` vs the Step-5 pre-fix regression vs the fixed tree — same
 * workload, same progressive flush feeding, host-JVM wall-clock timings.
 *
 * WHAT THIS IS: a reproducible micro-benchmark of the PARSER hot path on the
 * host JVM. WHAT THIS IS NOT: device frame timing. Compose recomposition,
 * layout, GC pauses and frame drops are NOT measured here (no hardware
 * profiling is available in this sandbox — stated honestly per the brief).
 * Per-flush parse cost is the workload the forensic audit flagged as O(document);
 * this benchmark proves whether that workload is gone.
 *
 * Parsers under test:
 *  A) STEP-4 KNOWN-GOOD  — verbatim copy of `0975be3`'s
 *     parseContentSegments/parseStreamingSegments/StreamParseCache (fence-split
 *     segments, last-segment tail reuse).
 *  B) STEP-5 PRE-FIX     — verbatim copy of the pre-fix parseStreamingBlocks
 *     (18c66a5^): `lastStart <= 0` bailout + fake `sourceStart = 0` list
 *     blocks (reproduced by zeroing list offsets like the old buildList did)
 *     + the double-shift `withShiftedSource`.
 *  C) STEP-5 FIXED       — the PRODUCTION `parseStreamingBlocks` called
 *     directly (no copy): line-run frozen boundary, real offsets everywhere.
 *
 * Reported per workload: median / p90 µs per flush, plus the GROWTH SIGNATURE
 * (median cost of the first quartile of flushes vs the last quartile). An
 * O(document) parser shows last ≫ first; an O(tail) parser shows last ≈ first.
 * Timings are printed, never asserted (except the correctness invariant).
 */
class ParserStreamingBenchmark {

    // =====================================================================
    // A) STEP-4 KNOWN-GOOD — verbatim from 0975be3 ChatScreen.kt
    // =====================================================================

    private data class Step4Segment(
        val text: String,
        val isCode: Boolean = false,
        val language: String? = null,
        val sourceEnd: Int = 0
    )

    private val step4FenceRegex = Regex("```(\\w*)\\n?([\\s\\S]*?)```")

    private fun step4ParseContentSegments(content: String): List<Step4Segment> {
        if (content.isEmpty()) return listOf(Step4Segment(content))
        val segments = mutableListOf<Step4Segment>()
        var last = 0
        for (match in step4FenceRegex.findAll(content)) {
            if (match.range.first > last) {
                segments += Step4Segment(content.substring(last, match.range.first), sourceEnd = match.range.first)
            }
            segments += Step4Segment(
                text = match.groupValues[2].trimEnd('\n'),
                isCode = true,
                language = match.groupValues[1].takeIf { it.isNotBlank() },
                sourceEnd = match.range.last + 1
            )
            last = match.range.last + 1
        }
        if (last < content.length) {
            val tail = content.substring(last)
            val open = tail.indexOf("```")
            if (open >= 0) {
                if (open > 0) segments += Step4Segment(tail.substring(0, open), sourceEnd = last + open)
                val rest = tail.substring(open + 3)
                val newline = rest.indexOf('\n')
                val language = if (newline >= 0) rest.substring(0, newline).trim().takeIf { it.isNotEmpty() } else null
                val body = if (newline >= 0) rest.substring(newline + 1).trimEnd('\n') else ""
                segments += Step4Segment(body, true, language, sourceEnd = content.length)
            } else {
                segments += Step4Segment(tail, sourceEnd = content.length)
            }
        }
        return segments
    }

    private fun step4ParseStreamingSegments(
        previous: String,
        previousSegments: List<Step4Segment>,
        content: String
    ): List<Step4Segment> {
        if (previousSegments.isEmpty() || !content.startsWith(previous)) {
            return step4ParseContentSegments(content)
        }
        val frozenEnd = if (previousSegments.size == 1) 0
        else previousSegments[previousSegments.size - 2].sourceEnd
        if (frozenEnd >= content.length) return previousSegments
        val tail = content.substring(frozenEnd)
        val tailParsed = step4ParseContentSegments(tail)
        val trimmedTail = if (
            tailParsed.size > 1 && !tailParsed.first().isCode && tailParsed.first().text.isEmpty()
        ) tailParsed.drop(1) else tailParsed
        return previousSegments.dropLast(1) + trimmedTail
    }

    private inner class Step4StreamParseCache {
        var content: String = ""
        var segments: List<Step4Segment> = emptyList()

        fun update(newContent: String, streaming: Boolean): List<Step4Segment> {
            val next = if (streaming) {
                step4ParseStreamingSegments(content, segments, newContent)
            } else {
                step4ParseContentSegments(newContent)
            }
            content = newContent
            segments = next
            return next
        }
    }

    // =====================================================================
    // B) STEP-5 PRE-FIX — verbatim from 18c66a5^ ContentBlocks.kt.
    //    The regression: list blocks (and list continuations) carried a fake
    //    `sourceStart = 0`, so the last block was a list exactly when
    //    `lastStart <= 0` fell into `parseBlocks(content)` — a full reparse
    //    on EVERY flush. Reproduced here by zeroing list offsets the way the
    //    old buildList did. Also verbatim: the non-recursive withShiftedSource
    //    and the double-shifting call site (`block.sourceStart + lastStart`).
    // =====================================================================

    private fun preFixZeroListOffsets(block: Block): Block = when (block) {
        is Block.BulletList -> block.copy(sourceStart = 0, items = block.items.map { preFixZeroItem(it) })
        is Block.OrderedList -> block.copy(sourceStart = 0, items = block.items.map { preFixZeroItem(it) })
        else -> block
    }

    private fun preFixZeroItem(item: ListItem): ListItem = item.copy(
        children = item.children.map { child ->
            when (child) {
                is Block.Paragraph -> child.copy(sourceStart = 0) // old: `Paragraph(parseInline(c), 0)`
                else -> preFixZeroListOffsets(child)
            }
        }
    )

    private fun preFixParseBlocks(content: String): List<Block> = parseBlocks(content).map { preFixZeroListOffsets(it) }

    private fun preFixParseStreamingBlocks(
        previousContent: String,
        previousBlocks: List<Block>,
        content: String
    ): List<Block> {
        if (previousBlocks.isEmpty() || !content.startsWith(previousContent)) {
            return preFixParseBlocks(content)
        }
        val lastStart = previousBlocks.last().sourceStart
        if (lastStart <= 0 || lastStart >= content.length) return preFixParseBlocks(content)
        val head = previousBlocks.dropLast(1)
        val tail = preFixParseBlocks(content.substring(lastStart)).map { block ->
            block.withPreFixShiftedSource(block.sourceStart + lastStart)
        }
        return head + tail
    }

    private fun Block.withPreFixShiftedSource(delta: Int): Block = when (this) {
        is Block.Paragraph -> copy(sourceStart = sourceStart + delta)
        is Block.Heading -> copy(sourceStart = sourceStart + delta)
        is Block.BulletList -> copy(sourceStart = sourceStart + delta)
        is Block.OrderedList -> copy(sourceStart = sourceStart + delta)
        is Block.BlockQuote -> copy(sourceStart = sourceStart + delta)
        is Block.CodeBlock -> copy(sourceStart = sourceStart + delta)
        is Block.TableBlock -> copy(sourceStart = sourceStart + delta)
        is Block.Divider -> copy(sourceStart = sourceStart + delta)
        is Block.MathBlock -> copy(sourceStart = sourceStart + delta)
        is Block.MermaidBlock -> copy(sourceStart = sourceStart + delta)
        is Block.ImageBlock -> copy(sourceStart = sourceStart + delta)
        is Block.CollapsibleBlock -> copy(sourceStart = sourceStart + delta)
        is Block.CitationsBlock -> copy(sourceStart = sourceStart + delta)
        is Block.ToolResultBlock -> copy(sourceStart = sourceStart + delta)
    }

    private inner class PreFixStreamCache {
        var content: String = ""
        var blocks: List<Block> = emptyList()

        fun update(newContent: String, streaming: Boolean): List<Block> {
            val next = if (streaming) {
                preFixParseStreamingBlocks(content, blocks, newContent)
            } else {
                preFixParseBlocks(newContent)
            }
            content = newContent
            blocks = next
            return next
        }
    }

    // =====================================================================
    // C) FIXED — production parseStreamingBlocks (direct call, no copy)
    // =====================================================================

    private inner class FixedStreamCache {
        var content: String = ""
        var blocks: List<Block> = emptyList()

        fun update(newContent: String, streaming: Boolean): List<Block> {
            val next = if (streaming) {
                parseStreamingBlocks(content, blocks, newContent)
            } else {
                parseBlocks(newContent)
            }
            content = newContent
            blocks = next
            return next
        }
    }

    // =====================================================================
    // Workloads — the six shapes required by the brief (+ mermaid fence)
    // =====================================================================

    private fun longAnswerEndingInList(paragraphs: Int, items: Int): String {
        val sb = StringBuilder()
        for (p in 1..paragraphs) {
            sb.append("Paragraph $p explains one aspect of the answer in plain prose, with enough ")
            sb.append("length that a full-document reparse per flush would be measurably wasteful ")
            sb.append("on a low-end device. Each paragraph is separated by a blank line so the ")
            sb.append("frozen boundary is well defined for the incremental parser.\n\n")
        }
        sb.append("In short:\n\n")
        for (i in 1..items) sb.append("- point $i keeps its identity while later points arrive\n")
        return sb.trimEnd().toString()
    }

    private data class Workload(val name: String, val doc: String = "")

    private val workloads = listOf(
        Workload("normalProse (short answer)"),
        Workload("longProse (long answer)"),
        Workload("listHeavy (answer ending in a list — the regression shape)"),
        Workload("mixedMarkdown (headings, lists, quote, code, table)"),
        Workload("codeHeavy (large fenced code)"),
        Workload("tableHeavy (wide table, many rows)"),
        Workload("mermaidFence")
    ).mapIndexed { index, w ->
        val doc = when (index) {
            0 -> "This is a normal short assistant answer. It explains the idea in two " +
                "sentences and stops. No rich constructs involved.\n\nThat is all."
            1 -> (1..40).joinToString("\n\n") { p ->
                "Paragraph $p of a long prose answer walks through one more detail of the " +
                    "explanation, adding context the reader needs, with several sentences " +
                    "per paragraph so the document grows to a realistic length."
            } + "\n\nClosing summary sentence for the long answer."
            2 -> longAnswerEndingInList(paragraphs = 12, items = 40)
            3 -> """
                ## Summary

                Here is what changed, and why it matters for daily use.

                - faster first paint
                - stable identity while streaming
                - no full-document work

                > Blockquotes stay quoted across the whole stream.

                ```kotlin
                val blocks = parseStreamingBlocks(prev, prevBlocks, next)
                ```

                | Aspect | Before | After |
                |--------|--------|-------|
                | per-flush | O(doc) | O(tail) |
                | identity | broken | stable |
                | keys | deep hash | sourceStart |

                A closing paragraph completes the mixed answer.
            """.trimIndent()
            4 -> buildString {
                append("The service locator is implemented like this:\n\n")
                append("```kotlin\n")
                repeat(60) { i -> appendLine("fun handler$i(x: Int): Int = x * $i + ${i * 2} // line $i") }
                append("```\n\nThat is the whole pattern, copied across modules.")
            }
            5 -> buildString {
                append("Benchmark numbers for every device tier:\n\n")
                append("| Device | Flush ms | Frozen | Notes |\n")
                append("|--------|---------:|:------:|-------|\n")
                repeat(40) { r ->
                    append("| tier-$r | ${(r % 9) + 1}.${(r * 7) % 10} | ${if (r % 2 == 0) "yes" else "no"} | row $r detail |\n")
                }
                append("\nMedians across five runs of the same workload.")
            }
            else -> """
                The architecture:

                ```mermaid
                graph TD
                  A[Stream] --> B{Fence closed?}
                  B -->|yes| C[MermaidBlock]
                  B -->|no| D[Open code]
                ```

                Diagram layout happens on finalize only.
            """.trimIndent()
        }
        Workload(w.name, doc)
    }

    // =====================================================================
    // Harness
    // =====================================================================

    private class Stats {
        val nanos = ArrayList<Long>()
        fun add(n: Long) { nanos += n }
        fun percentile(p: Double): Long {
            val sorted = nanos.sorted()
            return sorted[(p * (sorted.size - 1)).toInt().coerceIn(0, sorted.size - 1)]
        }
        fun median(): Long = percentile(0.5)
        fun mean(): Long = nanos.sum() / nanos.size.coerceAtLeast(1)
    }

    /** Streams [doc] through [update] in ~12-char deltas (≈30 Hz coalesced token arrival), timing each flush. */
    private inline fun runStream(doc: String, update: (String) -> Unit): Stats {
        val stats = Stats()
        var i = 0
        while (i < doc.length) {
            val end = minOf(doc.length, i + 12)
            val t0 = System.nanoTime()
            update(doc.substring(0, end))
            val t1 = System.nanoTime()
            stats.add(t1 - t0)
            i = end
        }
        return stats
    }

    private fun fmtUs(nanos: Long): String = "%.1f".format(nanos / 1000.0)

    @Test
    fun benchmark() {
        println()
        println("ParserStreamingBenchmark — host JVM ${System.getProperty("java.version")}, " +
            "${Runtime.getRuntime().availableProcessors()} cores")
        println("Feeding: ~12-char deltas per flush (mimics 30 Hz coalesced token arrival).")
        println()

        // One warmup + correctness pass of the FIXED parser per workload first.
        for (w in workloads) {
            val cache = FixedStreamCache()
            var i = 0
            while (i < w.doc.length) {
                val end = minOf(w.doc.length, i + 12)
                val blocks = cache.update(w.doc.substring(0, end), streaming = true)
                assertEquals(
                    "fixed parser broke stream==direct at ${end} for ${w.name}",
                    parseBlocks(w.doc.substring(0, end)), blocks
                )
                i = end
            }
        }

        val header = "%-58s %10s %10s %10s %10s %10s".format(
            "workload / parser", "flushes", "med µs", "p90 µs", "q1 µs", "q4 µs"
        )
        println(header)
        println("-".repeat(header.length))

        for (w in workloads) {
            // Warmup run (not reported).
            runStream(w.doc) { Step4StreamParseCache().let { it.content; it.update(it.content + "", false) } }

            // A) Step-4 known-good
            val a = Step4StreamParseCache()
            val statsA = runStream(w.doc) { partial -> a.update(partial, streaming = true) }

            // B) Step-5 pre-fix (the regression)
            val b = PreFixStreamCache()
            val statsB = runStream(w.doc) { partial -> b.update(partial, streaming = true) }

            // C) Step-5 fixed (production)
            val c = FixedStreamCache()
            val statsC = runStream(w.doc) { partial -> c.update(partial, streaming = true) }

            val q = { s: Stats ->
                val sorted = s.nanos.sorted()
                Pair(sorted[(0.25 * (sorted.size - 1)).toInt()], sorted[(0.75 * (sorted.size - 1)).toInt()])
            }
            val (q1a, q4a) = q(statsA); val (q1b, q4b) = q(statsB); val (q1c, q4c) = q(statsC)

            println("%-58s %10d %10s %10s %10s %10s".format(
                "  ${w.name} · step4 known-good (0975be3)", statsA.nanos.size, fmtUs(statsA.median()), fmtUs(statsA.percentile(0.9)), fmtUs(q1a), fmtUs(q4a)))
            println("%-58s %10d %10s %10s %10s %10s".format(
                "  ${w.name} · step5 PRE-FIX (regression, 18c66a5^)", statsB.nanos.size, fmtUs(statsB.median()), fmtUs(statsB.percentile(0.9)), fmtUs(q1b), fmtUs(q4b)))
            println("%-58s %10d %10s %10s %10s %10s".format(
                "  ${w.name} · step5 FIXED (production)", statsC.nanos.size, fmtUs(statsC.median()), fmtUs(statsC.percentile(0.9)), fmtUs(q1c), fmtUs(q4c)))
            println()
        }

        // Renderer key tax (pre-fix model): key(block) hashed the whole data
        // class — spans included — for EVERY top-level block on EVERY flush.
        // The fixed renderer keys on sourceStart (O(1)). Proxy measurement of
        // the data-class hashCode the old renderer paid per flush:
        val doc = longAnswerEndingInList(paragraphs = 12, items = 40)
        val blocks = parseBlocks(doc)
        var sink = 0
        val t0 = System.nanoTime()
        repeat(200) { for (b in blocks) sink += b.hashCode() }
        val keyTaxUs = (System.nanoTime() - t0) / 1000.0 / 200.0
        println("Renderer key tax (PRE-FIX key(block) deep hashCode proxy): " +
            "%.1f µs per flush for this ${blocks.size}-block document (sink=$sink)".format(keyTaxUs))
        println("Renderer key tax (FIXED streamBlockKey): O(1) — reads block.sourceStart, no traversal.")
        println()

        // Sanity — the incrementality contract, not raw µs: on the regression
        // shape (answer ending in a list), the FIXED parser must reuse the
        // frozen prefix's instances across late flushes (what lets Compose
        // skip every completed block), while the PRE-FIX parser recreated the
        // entire block list every flush (full reparse → full recomposition).
        // Raw µs on the list-tail shape stay close (the tail IS the list — a
        // documented P2 residue); the regression's real device cost was the
        // all-blocks-recreated recomposition, which this ratio captures.
        fun lateFlushReuse(update: (String) -> List<Block>, doc: String): Double {
            var previous: List<Block> = emptyList()
            var prevContent = ""
            var reused = 0
            var total = 0
            var i = doc.length * 3 / 4 // only the last quarter of the stream
            while (i < doc.length) {
                val end = minOf(doc.length, i + 12)
                val blocks = update(doc.substring(0, end))
                if (prevContent.isNotEmpty() && end > prevContent.length) {
                    val frozen = lastLineRunStart(prevContent)
                    for (b in blocks) if (b.sourceStart < frozen) {
                        total++
                        val match = previous.firstOrNull { it.sourceStart == b.sourceStart }
                        if (match != null && match === b) reused++
                    }
                }
                previous = blocks
                prevContent = doc.substring(0, end)
                i = end
            }
            return if (total == 0) 0.0 else reused.toDouble() / total
        }

        val regression = workloads[2]
        val b2 = PreFixStreamCache()
        val reusePre = lateFlushReuse({ p -> b2.update(p, streaming = true) }, regression.doc)
        val c2 = FixedStreamCache()
        val reuseFixed = lateFlushReuse({ p -> c2.update(p, streaming = true) }, regression.doc)
        println("Frozen-instance reuse in late flushes, list-tailed long answer:")
        println("  pre-fix regression : %5.1f%% of frozen blocks kept their instances".format(reusePre * 100))
        println("  fixed production   : %5.1f%% of frozen blocks kept their instances".format(reuseFixed * 100))
        assertTrue(
            "fixed parser must keep frozen instances (got %.1f%%)".format(reuseFixed * 100),
            reuseFixed > 0.9
        )
        assertTrue(
            "pre-fix parser demonstrably recreated frozen blocks (got %.1f%%)".format(reusePre * 100),
            reusePre < 0.5
        )
    }
}
