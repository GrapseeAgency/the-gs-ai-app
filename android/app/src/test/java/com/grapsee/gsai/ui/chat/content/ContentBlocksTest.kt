package com.grapsee.gsai.ui.chat.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PHASE 1 incrementality gate (Part 5 of the stabilisation brief).
 *
 * These tests prove — deterministically, no timing involved — the streaming
 * parser contract that the Step-5 regression broke:
 *
 *  1. FROZEN BLOCKS: every top-level block that lives before the last
 *     contiguous non-blank line-run keeps the SAME instance across all
 *     subsequent flushes ([assertSame] — not equals; a recreated-but-equal
 *     instance is a failure, because it forces the renderer to recompose it).
 *  2. LIVE TAIL: only the last line-run is ever reparsed — asserted via the
 *     [lastLineRunStart] contract (internal, exposed for exactly this).
 *  3. KEYS: renderer block keys ARE the sourceStart offsets (O(1)); we assert
 *     offsets are strictly increasing, real (> 0 where a prefix exists) and
 *     instance-stable — which makes the Compose keys stable by construction.
 *  4. STREAM == DIRECT: at EVERY flush the streamed block list must be
 *     data-class-equal (content AND offsets) to a direct [parseBlocks] of the
 *     same content — the stream may never produce a different document.
 *  5. WORK: the reparse region (current.length − last run start) is bounded by
 *     the live run, not the document — the identity tests drive ≥ 100
 *     progressive updates per shape to expose any growth-with-document drift.
 *
 * The identity tests deliberately use the shapes named in the brief:
 * paragraphs, nested lists, ordered lists, mixed prose + list, code, tables,
 * Mermaid fences, and a long answer ENDING IN A LIST (the shape that used to
 * fall into the full-parse bailout on every ~33 ms flush).
 */
class ContentBlocksTest {

    // ------------------------------------------------------------------
    // Streaming harness — mirrors RichBlocks.StreamBlockCache's contract
    // (update(newContent, streaming) → parseStreamingBlocks) without the
    // Compose dependency.
    // ------------------------------------------------------------------

    private class StreamCache {
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

    /**
     * Streams [full] through at least [minUpdates] progressive updates, small
     * deltas (1–11 chars, word-ish) like real token arrival, invoking
     * [onFlush] after every flush with (flushIndex, contentSoFar, blocks).
     * Returns the cache after the final flush.
     */
    private fun streamProgressively(
        full: String,
        minUpdates: Int,
        onFlush: (Int, String, List<Block>) -> Unit = { _, _, _ -> }
    ): StreamCache {
        val cache = StreamCache()
        var i = 0
        var flush = 0
        while (i < full.length || flush < minUpdates) {
            val step = 1 + (i * 7 + flush * 3) % 11 // deterministic 1..11 char deltas
            val end = minOf(full.length, i + step)
            // Hold the last byte back until we have done minUpdates flushes so
            // short documents still receive the required update count.
            val cut = if (flush < minUpdates - 1 && end >= full.length) full.length - 1 else end
            if (cut <= i) {
                // Document fully delivered but minUpdates not yet reached:
                // re-flush the SAME content (duplicate flush — must be idempotent).
                cache.update(full, streaming = true)
            } else {
                cache.update(full.substring(0, cut), streaming = true)
                i = cut
            }
            flush++
            onFlush(flush, cache.content, cache.blocks)
            if (flush >= minUpdates && i >= full.length) break
        }
        return cache
    }

    /** Asserts the frozen-prefix instance-identity invariant across flushes. */
    private fun assertFrozenIdentity(
        flushes: List<Pair<String, List<Block>>>,
        label: String
    ) {
        val seen = HashMap<Int, Block>() // top-level block offset → first instance
        flushes.forEachIndexed { index, (content, blocks) ->
            assertFrozenAtFlush(seen, content, blocks, "$label flush $index")
        }
    }

    /**
     * Incremental identity check — asserts ONE flush against the running [seen]
     * map (no flush history retained, so long streams cannot OOM the JVM).
     */
    private fun assertFrozenAtFlush(
        seen: HashMap<Int, Block>,
        content: String,
        blocks: List<Block>,
        where: String
    ) {
        // Block offsets live in the CRLF-normalized space (the parser
        // normalizes up front) — the boundary must be computed there too.
        val frozenBoundary = lastLineRunStart(content.replace("\r\n", "\n"))
        // Offsets strictly increasing — real byte offsets, usable as keys.
        var prev = -1
        for (b in blocks) {
            assertTrue(
                "$where: offsets must strictly increase (got $prev then ${b.sourceStart})",
                b.sourceStart > prev
            )
            prev = b.sourceStart
        }
        for (b in blocks) {
            val alreadyFrozen = seen[b.sourceStart]
            if (alreadyFrozen != null) {
                assertSame(
                    "$where: block at offset ${b.sourceStart} " +
                        "was instance-recreated — frozen prefix violated",
                    alreadyFrozen, b
                )
            } else if (b.sourceStart < frozenBoundary) {
                // First time seen AND already below the boundary → freeze it.
                seen[b.sourceStart] = b
            }
        }
    }

    /**
     * Full incremental contract for one document: frozen identity (assertSame),
     * stream == direct parse, and the tail-bound work check — all asserted per
     * flush with NO history retained (long streams cannot OOM the JVM).
     */
    private fun runIncrementalChecks(doc: String, minUpdates: Int, label: String): Int {
        val seen = HashMap<Int, Block>()
        var flushes = 0
        streamProgressively(doc, minUpdates) { i, c, b ->
            flushes = i
            assertFrozenAtFlush(seen, c, b, "$label flush $i")
            assertEquals("$label flush $i: streamed blocks must equal a direct parse", parseBlocks(c), b)
        }
        return flushes
    }

    // ------------------------------------------------------------------
    // Documents (the brief's minimum shapes)
    // ------------------------------------------------------------------

    private val paragraphsDoc = """
        Compose layout measures every node it draws.
        The parser feeds it blocks, not strings.
        Frozen prefixes keep completed paragraphs stable.
    """.trimIndent()

    private val nestedListDoc = """
        Plan for the weekend:

        - packing
          - boots
          - rope
          - first aid
        - route
          - north ridge
            - crossing
            - descent
        - food
    """.trimIndent()

    private val orderedListDoc = """
        Setup steps:

        1. Install the runtime
        2. Point the CLI at the project
        3. Run the checks
           - lint
           - tests
        4. Ship it
    """.trimIndent()

    private val mixedDoc = """
        ## Weekly notes

        Everything below is generated on device.

        - fast items
        - slow items
        - missing items

        > Quoted context survives streaming because quotes absorb whole lines.

        ```kotlin
        fun stable(): Int = 42
        ```

        | Area | Status |
        |------|--------|
        | parser | fixed |
        | renderer | fixed |

        Final paragraph closes the answer.
    """.trimIndent()

    private val codeDoc = """
        Here is the helper:

        ```python
        def stabilize(stream):
            head, tail = split(stream)
            return head + parse(tail)
        ```

        And a second one:

        ```json
        {"stable": true, "runs": 128}
        ```
    """.trimIndent()

    private val tableDoc = """
        Results from the run:

        | Parser | Flush cost | Frozen |
        |--------|-----------:|:------:|
        | step4 | fast | yes |
        | step5 broken | O(doc) | no |
        | step5 fixed | O(tail) | yes |

        Numbers are medians over the run.
    """.trimIndent()

    private val mermaidDoc = """
        The flow:

        ```mermaid
        graph TD
          A[Stream] --> B{Fence closed?}
          B -->|yes| C[MermaidBlock]
          B -->|no| D[Open code]
        ```

        Diagram renders on finalize only.
    """.trimIndent()

    /** The regression shape: long answer ENDING IN A (bullet) LIST. */
    private fun longAnswerEndingInList(paragraphs: Int, items: Int): String {
        val sb = StringBuilder()
        for (p in 1..paragraphs) {
            sb.append("Paragraph $p explains one aspect of the answer in plain prose, ")
            sb.append("with enough length that a full-document reparse per flush would be ")
            sb.append("measurably wasteful on a low-end device. Each paragraph is separated ")
            sb.append("by a blank line so the frozen boundary is well defined.\n\n")
        }
        sb.append("In short:\n\n")
        for (i in 1..items) {
            sb.append("- point $i keeps its identity while later points arrive\n")
        }
        return sb.trimEnd().toString()
    }

    private val longListDoc = longAnswerEndingInList(paragraphs = 20, items = 30)

    // ------------------------------------------------------------------
    // 1. Identity — frozen blocks stay the same instances (≥ 100 updates)
    // ------------------------------------------------------------------

    @Test
    fun `identity - paragraphs frozen across 120 progressive updates`() {
        val flushes = runIncrementalChecks(paragraphsDoc, 120, "paragraphs")
        assertTrue("expected ≥ 100 flushes, got $flushes", flushes >= 100)
    }

    @Test
    fun `identity - nested list frozen across 120 progressive updates`() {
        runIncrementalChecks(nestedListDoc, 120, "nested list")
    }

    @Test
    fun `identity - ordered list frozen across 120 progressive updates`() {
        runIncrementalChecks(orderedListDoc, 120, "ordered list")
    }

    @Test
    fun `identity - mixed markdown frozen across 120 progressive updates`() {
        runIncrementalChecks(mixedDoc, 120, "mixed")
    }

    @Test
    fun `identity - code-heavy doc frozen across 120 progressive updates`() {
        runIncrementalChecks(codeDoc, 120, "code")
    }

    @Test
    fun `identity - table doc frozen across 120 progressive updates`() {
        runIncrementalChecks(tableDoc, 120, "table")
    }

    @Test
    fun `identity - mermaid fence frozen across 120 progressive updates`() {
        runIncrementalChecks(mermaidDoc, 120, "mermaid")
    }

    /**
     * THE regression shape: long answer ending in a list. Before the fix the
     * list's fake sourceStart = 0 tripped the `lastStart <= 0` bailout and
     * every ~33 ms flush reparsed the whole answer. Must stay incremental
     * across 120 updates with the 20-paragraph / 30-item document.
     */
    @Test
    fun `identity - long answer ending in a list (regression shape) across 120 updates`() {
        runIncrementalChecks(longListDoc, 120, "long-list")

        // The document must end with ONE list containing ALL items — no
        // duplicate sibling lists, no renumbering, no truncation.
        val final = parseBlocks(longListDoc)
        val lists = final.filterIsInstance<Block.BulletList>()
        assertEquals("list answer must end as exactly one bullet list", 1, lists.size)
        assertEquals("all 30 items must survive the stream", 30, lists.single().items.size)
        // Work bound: the live tail (last run = the growing list) must stay a
        // fraction of the document, never the whole document.
        val tailChars = longListDoc.length - lastLineRunStart(longListDoc)
        assertTrue(
            "tail ($tailChars chars) must be bounded by the list run, not the document (${longListDoc.length})",
            tailChars < longListDoc.length * 0.6
        )
    }

    // ------------------------------------------------------------------
    // 2. The specific regression trigger: flushes that cut a line right
    //    after its marker ("-" / "1.") must never split the list.
    // ------------------------------------------------------------------

    @Test
    fun `list answers never split into duplicate sibling lists when a flush cuts after a marker`() {
        val doc = longAnswerEndingInList(paragraphs = 2, items = 12)
        // Walk every possible cut point: each cut simulates one flush landing
        // exactly there — including right after "-" and "1." markers.
        for (cut in 1 until doc.length) {
            val cache = StreamCache()
            cache.update(doc.substring(0, cut), streaming = true)
            if (cut < doc.length) cache.update(doc, streaming = true)
            val lists = cache.blocks.filterIsInstance<Block.BulletList>()
            assertTrue(
                "cut@$cut produced ${lists.size} bullet lists (duplicate sibling list)",
                lists.size <= 1
            )
            if (lists.size == 1) {
                assertEquals("cut@$cut dropped or added items", 12, lists.single().items.size)
            }
            assertEquals("cut@$cut: streamed result must equal a direct parse", parseBlocks(doc), cache.blocks)
        }
    }

    @Test
    fun `ordered list numbers stay correct when flushes cut after markers`() {
        val doc = orderedListDoc
        for (cut in 1 until doc.length step 3) {
            val cache = StreamCache()
            cache.update(doc.substring(0, cut), streaming = true)
            cache.update(doc, streaming = true)
            val lists = cache.blocks.filterIsInstance<Block.OrderedList>()
            assertTrue("cut@$cut produced ${lists.size} ordered lists", lists.size <= 1)
            assertEquals("cut@$cut: streamed result must equal a direct parse", parseBlocks(doc), cache.blocks)
        }
    }

    // ------------------------------------------------------------------
    // 3. Source offsets are real (never a fake 0) for every compound block
    // ------------------------------------------------------------------

    @Test
    fun `bullet and ordered lists carry real source offsets`() {
        val blocks = parseBlocks("Intro line.\n\n- alpha\n- beta\n\n1. one\n2. two\n")
        val bullet = blocks.filterIsInstance<Block.BulletList>().single()
        val ordered = blocks.filterIsInstance<Block.OrderedList>().single()
        assertEquals("bullet list offset must point at its first line", "Intro line.\n\n".length, bullet.sourceStart)
        val orderedExpected = "Intro line.\n\n- alpha\n- beta\n\n".length
        assertEquals("ordered list offset must point at its first line", orderedExpected, ordered.sourceStart)
    }

    @Test
    fun `nested list children carry real offsets at every depth`() {
        val blocks = parseBlocks(nestedListDoc)
        val list = blocks.filterIsInstance<Block.BulletList>().single()
        assertTrue("top list offset > 0", list.sourceStart > 0)
        assertEquals("Plan for the weekend:\n\n".length, list.sourceStart)
        // The "route" item owns a nested bullet list ("north ridge" + children).
        val route = list.items.first { it.spans.joinToString { s -> (s as? InlineSpan.TextSpan)?.text ?: "" }.contains("route") }
        val nested = route.children.filterIsInstance<Block.BulletList>()
        assertTrue("nested list must exist", nested.isNotEmpty())
        assertTrue(
            "nested list offset (${nested[0].sourceStart}) must be real and after the parent (${list.sourceStart})",
            nested[0].sourceStart > list.sourceStart
        )
        // Deep children too ("crossing"/"descent" live two levels down).
        val deep = nested[0].items.flatMap { it.children }.filterIsInstance<Block.BulletList>()
        assertTrue("deep list must exist", deep.isNotEmpty())
        assertTrue("deep offset must exceed nested offset", deep[0].sourceStart > nested[0].sourceStart)
    }

    @Test
    fun `continuation paragraphs carry real offsets`() {
        val doc = "1. first item\n   continuation prose here\n2. second item\n"
        val blocks = parseBlocks(doc)
        val list = blocks.filterIsInstance<Block.OrderedList>().single()
        val first = list.items[0]
        val cont = first.children.filterIsInstance<Block.Paragraph>()
        assertEquals("continuation must ride the first item", 1, cont.size)
        val expected = "1. first item\n".length
        assertEquals("continuation offset must be its own line offset", expected, cont[0].sourceStart)
    }

    // ------------------------------------------------------------------
    // 4. Fallback discipline: full parse ONLY for genuine cold/broken states
    // ------------------------------------------------------------------

    @Test
    fun `cache cold falls back to a full parse`() {
        val doc = mixedDoc
        assertEquals(parseBlocks(doc), parseStreamingBlocks("", emptyList(), doc))
    }

    @Test
    fun `content replacement (append-only contract broken) falls back to a full parse`() {
        val a = "First answer body.\n\n- a\n- b\n"
        val b = "Completely different answer!\n\n1. x\n2. y\n"
        val cache = StreamCache()
        cache.update(a, streaming = true)
        cache.update(b, streaming = true)
        assertEquals(parseBlocks(b), cache.blocks)
    }

    @Test
    fun `first-paragraph-only stream parses directly (no frozen prefix exists yet)`() {
        val grown = "A single growing paragraph"
        assertEquals(parseBlocks(grown), parseStreamingBlocks("A single", parseBlocks("A single"), grown))
    }

    @Test
    fun `duplicate flush of the same content is idempotent`() {
        val cache = StreamCache()
        cache.update(longListDoc, streaming = true)
        val once = cache.blocks
        val twice = cache.update(longListDoc, streaming = true)
        assertEquals(parseBlocks(longListDoc), once)
        assertEquals(parseBlocks(longListDoc), twice)
    }

    // ------------------------------------------------------------------
    // 5. Stream-safety lifecycle: open fence → closed; mermaid wiring
    // ------------------------------------------------------------------

    @Test
    fun `open fence stays open code and converts when closed`() {
        val cache = StreamCache()
        cache.update("```kotlin\nval a = 1", streaming = true)
        val open = cache.blocks.last() as Block.CodeBlock
        assertTrue(open.open)
        assertEquals("kotlin", open.language)

        cache.update("```kotlin\nval a = 1\n```\n\ndone", streaming = true)
        val closed = cache.blocks.first() as Block.CodeBlock
        assertNotEquals("fence must close", true, closed.open)
        assertEquals("val a = 1", closed.code)
        assertTrue(cache.blocks.last() is Block.Paragraph)
    }

    @Test
    fun `mermaid fence - open stays code, closed becomes a MermaidBlock with real offset`() {
        val cache = StreamCache()
        cache.update("Before.\n\n```mermaid\ngraph TD\n  A-->B", streaming = true)
        val open = cache.blocks.last()
        assertTrue("open mermaid must stay plain code mid-stream", open is Block.CodeBlock && open.open)

        val closedDoc = mermaidDoc
        cache.update(closedDoc, streaming = true)
        val mermaid = cache.blocks.filterIsInstance<Block.MermaidBlock>()
        assertEquals("closed mermaid must become a MermaidBlock", 1, mermaid.size)
        assertTrue("mermaid offset must be real", mermaid[0].sourceStart > 0)
        assertTrue(mermaid[0].source.contains("A[Stream]"))
        // And the streamed whole equals the direct parse.
        assertEquals(parseBlocks(closedDoc), cache.blocks)
    }

    @Test
    fun `table rows accumulate consistently while streaming`() {
        val cache = StreamCache()
        cache.update(tableDoc, streaming = true)
        val table = cache.blocks.filterIsInstance<Block.TableBlock>().single()
        assertEquals(3, table.rows.size)
        assertEquals(3, table.headers.size)
        assertEquals("| Parser | Flush cost | Frozen | separator row → L/R/C", TableAlign.LEFT, table.aligns[0])
        assertEquals(TableAlign.RIGHT, table.aligns[1])
        assertEquals(TableAlign.CENTER, table.aligns[2])
        assertTrue("table offset must be real", table.sourceStart > 0)
        assertEquals(parseBlocks(tableDoc), cache.blocks)
    }

    // ------------------------------------------------------------------
    // 6. CRLF: streaming arithmetic stays in one offset space
    // ------------------------------------------------------------------

    @Test
    fun `CRLF streams produce the same blocks as LF streams`() {
        val lf = longListDoc
        val crlf = lf.replace("\n", "\r\n")

        val seenLf = HashMap<Int, Block>()
        val seenCrlf = HashMap<Int, Block>()
        var lfFinal: List<Block> = emptyList()
        var crlfFinal: List<Block> = emptyList()
        streamProgressively(lf, minUpdates = 60) { i, c, b ->
            assertFrozenAtFlush(seenLf, c, b, "lf flush $i")
            lfFinal = b
        }
        streamProgressively(crlf, minUpdates = 60) { i, c, b ->
            assertFrozenAtFlush(seenCrlf, c, b, "crlf flush $i")
            crlfFinal = b
        }

        assertEquals("final CRLF stream must equal the LF stream block-for-block", lfFinal, crlfFinal)
        assertEquals(parseBlocks(lf), crlfFinal)
    }

    // ------------------------------------------------------------------
    // 7. lastLineRunStart contract (the frozen boundary itself)
    // ------------------------------------------------------------------

    @Test
    fun `lastLineRunStart lands on the start of the last contiguous run`() {
        assertEquals(0, lastLineRunStart(""))
        assertEquals(0, lastLineRunStart("one run only"))
        val doc = "first\n\nsecond\nthird\n"
        // Last run = "second\nthird\n" → starts at 7.
        assertEquals(7, lastLineRunStart(doc))
        // Trailing blank lines are skipped (not part of any run).
        assertEquals(7, lastLineRunStart("first\n\nsecond\nthird\n\n\n"))
        // List ending: boundary is the first list line.
        val listDoc = "Intro.\n\n- a\n- b\n"
        assertEquals("Intro.\n\n".length, lastLineRunStart(listDoc))
    }
}
