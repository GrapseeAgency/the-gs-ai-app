package com.grapsee.gsai.ui.chat.content

/**
 * STEP 5 — the message → ordered-content-blocks model.
 *
 * A message is no longer "one formatted string": [parseBlocks] turns assistant
 * text into an ordered list of typed [Block]s, and the renderer in RichBlocks.kt
 * draws each block. The model is platform-equivalent with the iOS twin
 * (Features/Chats/Content/ContentBlocks.swift) — same families, same grammar.
 *
 * Stream safety contract: only COMPLETE constructs become rich blocks. A
 * trailing open construct stays its best stable representation (open fence →
 * open CodeBlock, incomplete table → rows-so-far, dangling emphasis → literal
 * text), so mid-stream layouts never thrash. [parseStreamingBlocks] reuses the
 * frozen prefix of the previous parse and re-parses only from the last block's
 * [Block.sourceStart] — the incremental successor of the Step-4 segment cache.
 */

// ---------------------------------------------------------------------------
// Inline spans
// ---------------------------------------------------------------------------

sealed interface InlineSpan {
    data class TextSpan(val text: String) : InlineSpan
    data class BoldSpan(val children: List<InlineSpan>) : InlineSpan
    data class ItalicSpan(val children: List<InlineSpan>) : InlineSpan
    data class StrikeSpan(val children: List<InlineSpan>) : InlineSpan
    data class CodeSpan(val code: String) : InlineSpan
    data class LinkSpan(val text: String, val url: String) : InlineSpan

    /** Inline math source — rendered by MathText.kt, literal fallback on failure. */
    data class MathSpan(val latex: String) : InlineSpan
}

enum class TableAlign { LEFT, CENTER, RIGHT }

data class ListItem(
    val number: Int?,
    val spans: List<InlineSpan>,
    val children: List<Block>
)

// ---------------------------------------------------------------------------
// Blocks
// ---------------------------------------------------------------------------

sealed interface Block {
    val sourceStart: Int

    data class Paragraph(val spans: List<InlineSpan>, override val sourceStart: Int) : Block
    data class Heading(val level: Int, val spans: List<InlineSpan>, override val sourceStart: Int) : Block
    data class BulletList(val items: List<ListItem>, override val sourceStart: Int) : Block
    data class OrderedList(val start: Int, val items: List<ListItem>, override val sourceStart: Int) : Block
    data class BlockQuote(val blocks: List<Block>, override val sourceStart: Int) : Block

    /** [open] = the closing fence hasn't arrived yet (mid-stream). */
    data class CodeBlock(
        val language: String?,
        val code: String,
        val open: Boolean,
        override val sourceStart: Int
    ) : Block

    data class TableBlock(
        val headers: List<List<InlineSpan>>,
        val aligns: List<TableAlign>,
        val rows: List<List<List<InlineSpan>>>,
        override val sourceStart: Int
    ) : Block

    data class Divider(override val sourceStart: Int) : Block
    data class MathBlock(val latex: String, override val sourceStart: Int) : Block

    /** Fenced mermaid source — diagram parsing/rendering happens on finalize only. */
    data class MermaidBlock(val source: String, override val sourceStart: Int) : Block
    data class ImageBlock(val url: String, val alt: String, override val sourceStart: Int) : Block
    data class CollapsibleBlock(
        val summary: String,
        val blocks: List<Block>,
        override val sourceStart: Int
    ) : Block

    // --- structured seams (rendered only when the backend really provides data) ---

    data class CitationsBlock(val citations: List<CitationEntry>, override val sourceStart: Int) : Block
    data class ToolResultBlock(val entry: ToolResultEntry, override val sourceStart: Int) : Block
}

data class CitationEntry(
    val number: Int,
    val title: String,
    val domain: String,
    val snippet: String
)

data class ToolResultEntry(
    val kind: String,
    val status: String,
    val title: String,
    val detail: String
)

data class RichMetadata(
    val citations: List<CitationEntry> = emptyList(),
    val toolResults: List<ToolResultEntry> = emptyList()
)

// ---------------------------------------------------------------------------
// Parser
// ---------------------------------------------------------------------------

private data class Line(val text: String, val offset: Int)

fun parseBlocks(content: String, metadata: RichMetadata? = null): List<Block> {
    val normalized = content.replace("\r\n", "\n")
    val lines = toLines(normalized)
    val blocks = parseCore(lines)
    val toolBlocks = metadata?.toolResults?.map { Block.ToolResultBlock(it, 0) } ?: emptyList()
    val citations = metadata?.citations.orEmpty()
    val citationBlock = if (citations.isEmpty()) emptyList() else listOf(Block.CitationsBlock(citations, 0))
    return toolBlocks + blocks + citationBlock
}

private fun toLines(text: String): List<Line> {
    val out = ArrayList<Line>()
    var offset = 0
    for (raw in text.split('\n')) {
        out += Line(raw, offset)
        offset += raw.length + 1
    }
    return out
}

private val headingLineRegex = Regex("^(#{1,6})\\s+(.+?)\\s*#*\\s*$")
private val hrLineRegex = Regex("^ {0,3}((\\* *){3,}|(- *){3,}|(_ *){3,})$")
private val bulletItemRegex = Regex("^(\\s*)([-*+])\\s+(.*)$")
private val orderedItemRegex = Regex("^(\\s*)(\\d{1,9})[.)]\\s+(.*)$")
private val quoteLineRegex = Regex("^\\s{0,3}>\\s?(.*)$")
private val imageOnlyRegex = Regex("^!\\[([^\\]]*)\\]\\(([^)\\s]+)(?:\\s+\"[^\"]*\")?\\)\\s*$")
private val detailsOpenRegex = Regex("^\\s*<details[^>]*>\\s*$")
private val detailsInlineRegex = Regex("^\\s*<details[^>]*>\\s*<summary>(.*?)</summary>\\s*$")
private val summaryRegex = Regex("^\\s*<summary>(.*?)</summary>\\s*$")
private val detailsCloseRegex = Regex("^\\s*</details>\\s*$")

/** A table separator candidate: only pipes, dashes, colons and spaces. */
private fun isTableSeparator(text: String): Boolean {
    val t = text.trim()
    if (t.isEmpty() || !t.contains('|') || !t.contains('-')) return false
    if (!t.all { it == '|' || it == '-' || it == ':' || it == ' ' || it == '\t' }) return false
    return t.count { it == '-' } >= 3
}

private fun splitCells(text: String): List<String> {
    val t = text.trim()
    val body = if (t.startsWith("|")) t.substring(1) else t
    val last = if (body.endsWith("|") && !body.endsWith("\\|")) body.dropLast(1) else body
    val cells = mutableListOf<String>()
    val current = StringBuilder()
    var escaped = false
    for (ch in last) {
        when {
            escaped -> {
                if (ch == '|') current.append('|') else { current.append('\\'); current.append(ch) }
                escaped = false
            }
            ch == '\\' -> escaped = true
            ch == '|' -> { cells += current.toString().trim(); current.clear() }
            else -> current.append(ch)
        }
    }
    if (escaped) current.append('\\')
    cells += current.toString().trim()
    return cells
}

private fun parseCore(lines: List<Line>): List<Block> {
    val blocks = mutableListOf<Block>()
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        val text = line.text
        val trimmed = text.trimEnd()
        when {
            text.isBlank() -> { i++; continue }

            trimmed.trimStart().startsWith("```") -> {
                val (block, next) = parseFence(lines, i)
                blocks += block
                i = next
            }

            trimmed.trimStart().startsWith("$$") -> {
                i = parseMathBlock(lines, i, blocks)
            }

            headingLineRegex.matchEntire(trimmed) != null -> {
                val m = headingLineRegex.matchEntire(trimmed)!!
                blocks += Block.Heading(
                    level = m.groupValues[1].length,
                    spans = parseInline(m.groupValues[2]),
                    sourceStart = line.offset
                )
                i++
            }

            hrLineRegex.matchEntire(trimmed) != null -> {
                blocks += Block.Divider(line.offset)
                i++
            }

            text.contains('|') && i + 1 < lines.size && isTableSeparator(lines[i + 1].text) -> {
                val (block, next) = parseTable(lines, i)
                blocks += block
                i = next
            }

            quoteLineRegex.matchEntire(text) != null -> {
                val innerLines = mutableListOf<Line>()
                while (i < lines.size) {
                    val m = quoteLineRegex.matchEntire(lines[i].text) ?: break
                    innerLines += Line(m.groupValues[1], lines[i].offset)
                    i++
                }
                blocks += Block.BlockQuote(parseCore(innerLines), line.offset)
            }

            detailsOpenRegex.matchEntire(trimmed) != null ||
                detailsInlineRegex.matchEntire(trimmed) != null -> {
                val (block, next) = parseDetails(lines, i)
                blocks += block
                i = next
            }

            bulletItemRegex.matchEntire(text) != null || orderedItemRegex.matchEntire(text) != null -> {
                val (listBlocks, next) = parseList(lines, i)
                blocks += listBlocks
                i = next
            }

            imageOnlyRegex.matchEntire(text.trim()) != null -> {
                val m = imageOnlyRegex.matchEntire(text.trim())!!
                blocks += Block.ImageBlock(
                    url = m.groupValues[2],
                    alt = m.groupValues[1],
                    sourceStart = line.offset
                )
                i++
            }

            else -> {
                val paragraph = StringBuilder()
                val start = line.offset
                while (i < lines.size && isProse(lines, i)) {
                    if (paragraph.isNotEmpty()) paragraph.append('\n')
                    paragraph.append(lines[i].text.trimEnd())
                    i++
                }
                blocks += Block.Paragraph(parseInline(paragraph.toString()), start)
            }
        }
    }
    return blocks
}

/** A line continues the current paragraph unless it is blank or opens a block. */
private fun isProse(lines: List<Line>, index: Int): Boolean {
    val text = lines[index].text
    if (text.isBlank()) return false
    val t = text.trimEnd()
    if (t.trimStart().startsWith("```")) return false
    if (t.trimStart().startsWith("$$")) return false
    if (headingLineRegex.matchEntire(t) != null) return false
    if (hrLineRegex.matchEntire(t) != null) return false
    if (quoteLineRegex.matchEntire(t) != null) return false
    if (bulletItemRegex.matchEntire(t) != null || orderedItemRegex.matchEntire(t) != null) return false
    if (detailsOpenRegex.matchEntire(t) != null || detailsInlineRegex.matchEntire(t) != null) return false
    // A table opener also ends the paragraph (header + separator follow).
    if (t.contains('|') && index + 1 < lines.size && isTableSeparator(lines[index + 1].text)) return false
    return true
}

private fun parseFence(lines: List<Line>, start: Int): Pair<Block, Int> {
    val opener = lines[start].text.trimStart().removePrefix("```").trim()
    val language = opener.split(Regex("\\s+")).firstOrNull { it.isNotBlank() }
    val body = StringBuilder()
    var i = start + 1
    while (i < lines.size) {
        val trimmed = lines[i].text.trim()
        if (trimmed.startsWith("```") && trimmed.removePrefix("```").isBlank()) {
            return Pair(
                fenceToBlock(
                    language = language,
                    code = body.toString().trimEnd('\n'),
                    open = false,
                    offset = lines[start].offset
                ),
                i + 1
            )
        }
        if (body.isNotEmpty()) body.append('\n')
        body.append(lines[i].text)
        i++
    }
    // Unterminated trailing fence — mid-stream: render what arrived as open code.
    return Pair(
        Block.CodeBlock(
            language = language,
            code = body.toString().trimEnd('\n'),
            open = true,
            sourceStart = lines[start].offset
        ),
        lines.size
    )
}

/** A closed fence in the mermaid language is a first-class diagram block; an
 *  open one stays plain code so the stream never lays out a diagram mid-flight. */
private fun fenceToBlock(language: String?, code: String, open: Boolean, offset: Int): Block =
    if (language.equals("mermaid", ignoreCase = true) && !open) {
        Block.MermaidBlock(code, offset)
    } else {
        Block.CodeBlock(language, code, open, offset)
    }

private fun parseMathBlock(lines: List<Line>, start: Int, blocks: MutableList<Block>): Int {
    val first = lines[start].text.trimStart()
    val rest = first.substring(2)
    val body = StringBuilder()
    var closed = false
    var i = start + 1
    if (rest.contains("$$")) {
        body.append(rest.substringBefore("$$"))
        closed = true
    } else {
        if (rest.isNotBlank()) body.append(rest.trim())
        while (i < lines.size) {
            val l = lines[i].text
            if (l.contains("$$")) {
                val before = l.substringBefore("$$")
                if (body.isNotEmpty() && before.isNotBlank()) body.append('\n')
                body.append(before.trim())
                closed = true
                i++
                break
            }
            if (body.isNotEmpty()) body.append('\n')
            body.append(l.trim())
            i++
        }
    }
    val latex = body.toString().trim()
    if (latex.isNotEmpty()) blocks += Block.MathBlock(latex, lines[start].offset)
    return if (closed) i else lines.size
}

private fun parseTable(lines: List<Line>, start: Int): Pair<Block, Int> {
    val headerCells = splitCells(lines[start].text)
    val alignCells = splitCells(lines[start + 1].text)
    val columnCount = maxOf(headerCells.size, alignCells.size)
    val headers = (0 until columnCount).map { c -> parseInline(headerCells.getOrElse(c) { "" }) }
    val aligns = (0 until columnCount).map { c ->
        val cell = alignCells.getOrElse(c) { "" }.trim()
        val left = cell.startsWith(":")
        val right = cell.endsWith(":")
        when {
            left && right -> TableAlign.CENTER
            right -> TableAlign.RIGHT
            else -> TableAlign.LEFT
        }
    }
    val rows = mutableListOf<List<List<InlineSpan>>>()
    var i = start + 2
    while (i < lines.size) {
        val t = lines[i].text
        if (t.isBlank() || !t.contains('|')) break
        if (isTableSeparator(t)) { i++; continue }
        val cells = splitCells(t)
        rows += (0 until columnCount).map { c -> parseInline(cells.getOrElse(c) { "" }) }
        i++
    }
    return Pair(Block.TableBlock(headers, aligns, rows, lines[start].offset), i)
}

private data class ListRecord(
    val level: Int,
    val ordered: Boolean,
    val number: Int,
    val text: String,
    val offset: Int,
    val continuation: MutableList<ContinuationLine> = mutableListOf()
)

/** A continuation line riding a list item — kept with its real source offset. */
private data class ContinuationLine(val offset: Int, val text: String)

private fun parseList(lines: List<Line>, start: Int): Pair<List<Block>, Int> {
    val records = mutableListOf<ListRecord>()
    var i = start
    while (i < lines.size) {
        val text = lines[i].text
        val bullet = bulletItemRegex.matchEntire(text)
        val ordered = orderedItemRegex.matchEntire(text)
        when {
            bullet != null -> {
                val indent = bullet.groupValues[1].count { it == ' ' } + bullet.groupValues[1].count { it == '\t' } * 2
                records += ListRecord(indent / 2, false, 0, bullet.groupValues[3], lines[i].offset)
                i++
            }
            ordered != null -> {
                val indent = ordered.groupValues[1].count { it == ' ' } + ordered.groupValues[1].count { it == '\t' } * 2
                records += ListRecord(indent / 2, true, ordered.groupValues[2].toInt(), ordered.groupValues[3], lines[i].offset)
                i++
            }
            // Continuation: a line indented deeper than the last item's own
            // indent (level*2) rides that item — level is indent/2.
            text.isNotBlank() && records.isNotEmpty() &&
                text.startsWith(" ".repeat(records.last().level * 2 + 2)) -> {
                records[records.size - 1].continuation += ContinuationLine(lines[i].offset, text.trim())
                i++
            }
            else -> break
        }
    }
    val blocks = mutableListOf<Block>()
    var r = 0
    while (r < records.size) {
        val (block, used) = buildList(records, r, records[r].level)
        blocks += block
        r += used
    }
    return Pair(blocks, i)
}

private fun buildList(records: List<ListRecord>, from: Int, level: Int): Pair<Block, Int> {
    val ordered = records[from].ordered
    val items = mutableListOf<ListItem>()
    var i = from
    while (i < records.size && records[i].level >= level) {
        if (records[i].level > level) { i++; continue } // orphan deeper record (defensive)
        if (records[i].ordered != ordered) break // kind switch → sibling list (caller loop continues)
        val record = records[i]
        var j = i + 1
        val childRecords = mutableListOf<ListRecord>()
        while (j < records.size && records[j].level > level) {
            childRecords += records[j]
            j++
        }
        val children = mutableListOf<Block>()
        for (c in record.continuation) children += Block.Paragraph(parseInline(c.text), c.offset)
        if (childRecords.isNotEmpty()) {
            children += buildList(childRecords, 0, childRecords.first().level).first
        }
        items += ListItem(
            number = if (record.ordered) record.number else null,
            spans = parseInline(record.text),
            children = children
        )
        i = j
    }
    // Real source offsets at EVERY nesting level — the frozen-prefix stream
    // parser and the renderer's block keys both depend on them. A fake 0 here
    // used to disable incrementality for the most common answer shape
    // (anything ending in a list) and split growing lists into duplicates.
    val block = if (ordered) {
        Block.OrderedList(start = records[from].number, items = items, sourceStart = records[from].offset)
    } else {
        Block.BulletList(items = items, sourceStart = records[from].offset)
    }
    return Pair(block, i - from)
}

private fun parseDetails(lines: List<Line>, start: Int): Pair<Block, Int> {
    val inlineSummary = detailsInlineRegex.matchEntire(lines[start].text.trimEnd())
    var summary = ""
    var i = start
    if (inlineSummary != null) {
        summary = inlineSummary.groupValues[1]
        i++
    } else {
        i++
        while (i < lines.size) {
            val m = summaryRegex.matchEntire(lines[i].text.trimEnd())
            if (m != null) { summary = m.groupValues[1]; i++; break }
            if (lines[i].text.isNotBlank()) break
            i++
        }
    }
    val inner = mutableListOf<Line>()
    while (i < lines.size) {
        if (detailsCloseRegex.matchEntire(lines[i].text.trimEnd()) != null) { i++; break }
        inner += lines[i]
        i++
    }
    return Pair(
        Block.CollapsibleBlock(
            summary = summary.ifBlank { "Details" },
            blocks = parseCore(inner),
            sourceStart = lines[start].offset
        ),
        i
    )
}

// ---------------------------------------------------------------------------
// Streaming: incremental frozen-prefix parse
// ---------------------------------------------------------------------------

/**
 * Appending a delta can only affect blocks in the LAST contiguous non-blank
 * line-run of the document — every line-run separated from the end by a blank
 * line is closed forever (every family in this grammar — paragraphs, lists,
 * quotes, tables, fences, math, details — absorbs only CONSECUTIVE lines).
 *
 * So a flush re-parses at most [lastLineRunStart] and freezes everything
 * before it: earlier block instances stay byte-identical across ~30 Hz
 * flushes, the renderer skips all completed blocks, and only the live tail
 * recomposes. A full parse happens only when genuinely necessary:
 *  - cache cold ([previousBlocks] empty), or
 *  - the append-only contract broke ([content] does not extend
 *    [previousContent] — content replaced, history jump, CRLF split).
 *
 * The tail boundary is the whole last line-RUN, not just the last block:
 * a flush can cut a line in a state that parses as a different family
 * (a bare "-" or "1." pending marker parses as prose today, a list item
 * tomorrow). Re-parsing from the last BLOCK alone then froze a still-open
 * list and spawned a duplicate sibling list on the next flush. The run
 * boundary makes that state impossible by construction.
 *
 * Streaming contract note: this path is metadata-free (StreamBlockCache only
 * ever feeds plain content), so every block here carries a real source
 * offset — never a fake 0.
 *
 * All arithmetic happens in the CRLF-normalized space ([parseBlocks]
 * normalizes internally, so offsets are normalized-space offsets); both
 * inputs are normalized up front to keep the two spaces identical. On an
 * LF-only stream Kotlin's replace returns the receiver — zero cost.
 */
fun parseStreamingBlocks(
    previousContent: String,
    previousBlocks: List<Block>,
    content: String
): List<Block> {
    val previous = previousContent.replace("\r\n", "\n")
    val current = content.replace("\r\n", "\n")
    if (previousBlocks.isEmpty() || !current.startsWith(previous)) {
        return parseBlocks(current)
    }
    val tailStart = lastLineRunStart(previous)
    if (tailStart <= 0) {
        // The live run starts at the document start (single growing run —
        // most commonly the first paragraph, legitimately at offset 0):
        // there is no frozen prefix, so the tail IS the document.
        return parseBlocks(current)
    }
    if (tailStart >= current.length) return parseBlocks(current) // defensive: nothing to append
    val head = previousBlocks.takeWhile { it.sourceStart < tailStart }
    if (head.isEmpty()) return parseBlocks(current) // defensive: boundary sanity
    val tail = parseBlocks(current.substring(tailStart)).map { block ->
        block.withShiftedSource(tailStart)
    }
    return head + tail
}

/**
 * Start offset of the last contiguous non-blank line-run of [text] (blank
 * trailing lines are skipped). Pure index arithmetic on the raw string —
 * O(length of that run), no allocations. Returns 0 when the document is one
 * run or empty. Internal so the incrementality tests can assert the exact
 * frozen-prefix contract.
 */
internal fun lastLineRunStart(text: String): Int {
    var end = text.length
    while (end > 0) {
        val c = text[end - 1]
        if (c == '\n' || c == ' ' || c == '\t' || c == '\r') end-- else break
    }
    if (end == 0) return 0
    var runStart = 0
    var searchEnd = end
    while (searchEnd > 0) {
        val lineStart = text.lastIndexOf('\n', searchEnd - 1) + 1
        var blank = true
        var k = lineStart
        while (k < searchEnd) {
            val c = text[k]
            if (c != ' ' && c != '\t' && c != '\r') { blank = false; break }
            k++
        }
        if (blank) break
        runStart = lineStart
        if (lineStart == 0) break
        searchEnd = lineStart - 1
    }
    return runStart
}

/**
 * Re-bases every source offset in the block (and its whole nested subtree —
 * list children, quote and collapsible innards) from tail-relative space into
 * absolute document space. The delta is the tail start ALONE: the previous
 * call passed `block.sourceStart + tailStart`, which double-counted the
 * block's own offset and corrupted every tail block not starting at zero.
 */
private fun Block.withShiftedSource(delta: Int): Block = when (this) {
    is Block.Paragraph -> copy(sourceStart = sourceStart + delta)
    is Block.Heading -> copy(sourceStart = sourceStart + delta)
    is Block.BulletList -> copy(
        sourceStart = sourceStart + delta,
        items = items.map { item -> item.copy(children = item.children.map { it.withShiftedSource(delta) }) }
    )
    is Block.OrderedList -> copy(
        sourceStart = sourceStart + delta,
        items = items.map { item -> item.copy(children = item.children.map { it.withShiftedSource(delta) }) }
    )
    is Block.BlockQuote -> copy(
        sourceStart = sourceStart + delta,
        blocks = blocks.map { it.withShiftedSource(delta) }
    )
    is Block.CodeBlock -> copy(sourceStart = sourceStart + delta)
    is Block.TableBlock -> copy(sourceStart = sourceStart + delta)
    is Block.Divider -> copy(sourceStart = sourceStart + delta)
    is Block.MathBlock -> copy(sourceStart = sourceStart + delta)
    is Block.MermaidBlock -> copy(sourceStart = sourceStart + delta)
    is Block.ImageBlock -> copy(sourceStart = sourceStart + delta)
    is Block.CollapsibleBlock -> copy(
        sourceStart = sourceStart + delta,
        blocks = blocks.map { it.withShiftedSource(delta) }
    )
    is Block.CitationsBlock -> copy(sourceStart = sourceStart + delta)
    is Block.ToolResultBlock -> copy(sourceStart = sourceStart + delta)
}
