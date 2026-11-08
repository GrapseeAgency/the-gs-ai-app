import Foundation

/// STEP 5 — the message → ordered-content-blocks model (iOS twin of the
/// Android `ui/chat/content/ContentBlocks.kt`; same families, same grammar).
///
/// A message is an ordered sequence of typed blocks, not one formatted string.
/// Stream safety contract: only COMPLETE constructs become rich blocks; a
/// trailing open construct stays its best stable representation (open fence →
/// open code block, incomplete table → rows so far, dangling emphasis →
/// literal text). iOS renders plain text during streaming and parses to blocks
/// on finalize — the Step-4 live-bubble decision is preserved.
///
/// Structured seams (citations, tool results) render ONLY when explicit
/// metadata is provided. The parser never fabricates sources.

// MARK: - Inline spans

indirect enum InlineSpan: Equatable {
    case text(String)
    case bold([InlineSpan])
    case italic([InlineSpan])
    case strike([InlineSpan])
    case code(String)
    case link(text: String, url: String)
    case math(latex: String)
}

enum TableAlign: Equatable {
    case left, center, right
}

struct ListItem: Equatable {
    var number: Int? = nil
    let spans: [InlineSpan]
    var children: [Block] = []
    var checked: Bool? = nil
}

// MARK: - Blocks

indirect enum Block: Equatable {
    case paragraph(spans: [InlineSpan], sourceStart: Int)
    case heading(level: Int, spans: [InlineSpan], sourceStart: Int)
    case bulletList(items: [ListItem], sourceStart: Int)
    case orderedList(start: Int, items: [ListItem], sourceStart: Int)
    case blockQuote(blocks: [Block], sourceStart: Int)
    /// `open` = the closing fence hasn't arrived yet (mid-stream representation).
    case codeBlock(language: String?, code: String, open: Bool, sourceStart: Int)
    case table(headers: [[InlineSpan]], aligns: [TableAlign], rows: [[[InlineSpan]]], sourceStart: Int)
    /// The Nexly timetable. A first-class block: it owns its own glass card,
    /// renders time + glass glyph + activity rows, and never falls through to
    /// a markdown table or raw JSON. `open` is the mid-stream skeleton.
    case plan(NexlyPlanData, open: Bool, sourceStart: Int)
    /// Interactive GENUI widget block across all 36 `WIDGET_KINDS`.
    case widget(kind: String, json: String, open: Bool, sourceStart: Int)
    /// Interactive HTML / SVG / map surface block.
    case html(kind: String, source: String, open: Bool, sourceStart: Int)
    case divider(sourceStart: Int)
    case mathBlock(latex: String, sourceStart: Int)
    case mermaid(source: String, sourceStart: Int)
    case image(url: String, alt: String, sourceStart: Int)
    case collapsible(summary: String, blocks: [Block], sourceStart: Int)
    // --- structured seams (rendered only when the backend really provides data) ---
    case citations([CitationEntry], sourceStart: Int)
    case toolResult(ToolResultEntry, sourceStart: Int)

    var sourceStart: Int {
        switch self {
        case .paragraph(_, let s): return s
        case .heading(_, _, let s): return s
        case .bulletList(_, let s): return s
        case .orderedList(_, _, let s): return s
        case .blockQuote(_, let s): return s
        case .codeBlock(_, _, _, let s): return s
        case .table(_, _, _, let s): return s
        case .plan(_, _, let s): return s
        case .widget(_, _, _, let s): return s
        case .html(_, _, _, let s): return s
        case .divider(let s): return s
        case .mathBlock(_, let s): return s
        case .mermaid(_, let s): return s
        case .image(_, _, let s): return s
        case .collapsible(_, _, let s): return s
        case .citations(_, let s): return s
        case .toolResult(_, let s): return s
        }
    }
}

struct CitationEntry: Equatable {
    let number: Int
    let title: String
    let domain: String
    let snippet: String
    var url: String = ""
}

struct ToolResultEntry: Equatable {
    let kind: String
    let status: String
    let title: String
    let detail: String
}

struct RichMetadata: Equatable {
    let citations: [CitationEntry]
    let toolResults: [ToolResultEntry]
}

// MARK: - Parser

struct GSLine {
    let text: String
    let offset: Int
    let ending: String

    init(text: String, offset: Int, ending: String = "\n") {
        self.text = text
        self.offset = offset
        self.ending = ending
    }
}

func parseBlocks(_ content: String, metadata: RichMetadata? = nil) -> [Block] {
    let lines = toLines(content)
    var blocks = parseCore(lines).map(promoteBarePayload)
    if let metadata = metadata {
        let tools = metadata.toolResults.map { Block.toolResult($0, sourceStart: 0) }
        let citations = metadata.citations.isEmpty ? [] : [Block.citations(metadata.citations, sourceStart: 0)]
        blocks = tools + blocks + citations
    }
    return blocks
}

private func promoteBarePayload(_ block: Block) -> Block {
    let candidate: String?
    switch block {
    case .paragraph(let spans, _):
        if spans.count == 1, case .text(let t) = spans[0] {
            candidate = t
        } else {
            candidate = nil
        }
    case .codeBlock(_, let code, let open, _):
        candidate = open ? nil : code
    default:
        candidate = nil
    }
    guard let raw = candidate?.trimmingCharacters(in: .whitespacesAndNewlines),
          raw.count >= 2, raw.count <= 65_536 else { return block }
    let isObj = raw.hasPrefix("{") && raw.hasSuffix("}")
    let isArr = raw.hasPrefix("[") && raw.hasSuffix("]")
    guard isObj || isArr else { return block }
    guard let kind = inferWidgetKind(raw) else { return block }
    if kind == "plan" {
        guard let plan = parseNexlyPlan(raw) else { return block }
        return .plan(plan, open: false, sourceStart: block.sourceStart)
    }
    return .widget(kind: kind, json: raw, open: false, sourceStart: block.sourceStart)
}

private func toLines(_ text: String) -> [GSLine] {
    var out: [GSLine] = []
    var offset = 0
    let rows = text.components(separatedBy: "\n")
    for (index, raw) in rows.enumerated() {
        let ending = index == rows.count - 1 ? "" : (raw.hasSuffix("\r") ? "\r\n" : "\n")
        let value = ending == "\r\n" ? String(raw.dropLast()) : raw
        out.append(GSLine(text: value, offset: offset, ending: ending))
        // Swift offsets count Characters: CRLF is one extended grapheme.
        offset += value.count + ending.count
    }
    return out
}

private let headingLineRegex = try! NSRegularExpression(pattern: "^(#{1,6})\\s+(.+?)\\s*#*\\s*$")
private let hrLineRegex = try! NSRegularExpression(pattern: "^ {0,3}((\\* *){3,}|(- *){3,}|(_ *){3,})$")
private let bulletItemRegex = try! NSRegularExpression(pattern: "^(\\s*)([-*+•·])\\s+(.*)$")
private let orderedItemRegex = try! NSRegularExpression(pattern: "^(\\s*)(\\d{1,9})[.)]\\s+(.*)$")
private let quoteLineRegex = try! NSRegularExpression(pattern: "^\\s{0,3}>\\s?(.*)$")
private let imageOnlyRegex = try! NSRegularExpression(pattern: "^!\\[([^\\]]*)\\]\\(([^)\\s]+)(?:\\s+\"[^\"]*\")?\\)\\s*$")
private let detailsOpenRegex = try! NSRegularExpression(pattern: "^\\s*<details[^>]*>\\s*$")
private let detailsInlineRegex = try! NSRegularExpression(pattern: "^\\s*<details[^>]*>\\s*<summary>(.*?)</summary>\\s*$")
private let summaryRegex = try! NSRegularExpression(pattern: "^\\s*<summary>(.*?)</summary>\\s*$")
private let detailsCloseRegex = try! NSRegularExpression(pattern: "^\\s*</details>\\s*$")

private extension String {
    var trimmedEnd: String { replacingOccurrences(of: "\\s+$", with: "", options: .regularExpression) }
    var trimmed: String { trimmingCharacters(in: .whitespacesAndNewlines) }

    func matchesEntire(_ regex: NSRegularExpression) -> Bool {
        let ns = self as NSString
        return regex.firstMatch(in: self, range: NSRange(location: 0, length: ns.length))?.range.length == ns.length
    }

    /// All capture groups of the FIRST match, without demanding the match
    /// covers the whole string — line classifiers use anchored patterns anyway.
    func captureGroups(_ regex: NSRegularExpression) -> [String]? {
        let ns = self as NSString
        guard let m = regex.firstMatch(in: self, range: NSRange(location: 0, length: ns.length)) else { return nil }
        return (1..<m.numberOfRanges).map { group in
            let range = m.range(at: group)
            return range.location == NSNotFound ? "" : ns.substring(with: range)
        }
    }
}

/// A table separator candidate: only pipes, dashes, colons and spaces.
private func isTableSeparator(_ text: String) -> Bool {
    let t = text.trimmed
    if t.isEmpty || !t.contains("|") || !t.contains("-") { return false }
    if !t.allSatisfy({ $0 == "|" || $0 == "-" || $0 == ":" || $0 == " " || $0 == "\t" }) { return false }
    return t.filter { $0 == "-" }.count >= 3
}

private func splitCells(_ text: String) -> [String] {
    var t = text.trimmed
    if t.hasPrefix("|") { t.removeFirst() }
    if t.hasSuffix("|") && !t.hasSuffix("\\|") { t.removeLast() }
    var cells: [String] = []
    var current = ""
    var escaped = false
    for ch in t {
        if escaped {
            if ch == "|" { current.append("|") } else { current.append("\\"); current.append(ch) }
            escaped = false
        } else if ch == "\\" {
            escaped = true
        } else if ch == "|" {
            cells.append(current.trimmed)
            current = ""
        } else {
            current.append(ch)
        }
    }
    if escaped { current.append("\\") }
    cells.append(current.trimmed)
    return cells
}

private func parseCore(_ lines: [GSLine]) -> [Block] {
    var blocks: [Block] = []
    var i = 0
    while i < lines.count {
        let line = lines[i]
        let text = line.text
        let trimmed = text.trimmedEnd
        let lead = trimmed.trimmingCharacters(in: .whitespaces)

        if text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            i += 1
            continue
        }

        if fenceOpening(lead) != nil {
            let (block, next) = parseFence(lines, start: i)
            blocks.append(block)
            i = next
            continue
        }

        if lead.hasPrefix("$$") {
            i = parseMathBlock(lines, start: i, into: &blocks)
            continue
        }

        if let groups = trimmed.captureGroups(headingLineRegex) {
            blocks.append(.heading(
                level: groups[0].count,
                spans: parseInline(groups[1]),
                sourceStart: line.offset
            ))
            i += 1
            continue
        }

        if trimmed.matchesEntire(hrLineRegex) {
            blocks.append(.divider(sourceStart: line.offset))
            i += 1
            continue
        }

        if text.contains("|"), i + 1 < lines.count, isTableSeparator(lines[i + 1].text) {
            let (block, next) = parseTable(lines, start: i)
            blocks.append(block)
            i = next
            continue
        }

        if text.captureGroups(quoteLineRegex) != nil {
            var inner: [GSLine] = []
            while i < lines.count, let g = lines[i].text.captureGroups(quoteLineRegex) {
                inner.append(GSLine(text: g[0], offset: lines[i].offset, ending: lines[i].ending))
                i += 1
            }
            blocks.append(.blockQuote(blocks: parseCore(inner), sourceStart: line.offset))
            continue
        }

        if trimmed.matchesEntire(detailsOpenRegex) || trimmed.matchesEntire(detailsInlineRegex) {
            let (block, next) = parseDetails(lines, start: i)
            blocks.append(block)
            i = next
            continue
        }

        if text.captureGroups(bulletItemRegex) != nil || text.captureGroups(orderedItemRegex) != nil {
            let (listBlocks, next) = parseList(lines, start: i)
            blocks.append(contentsOf: listBlocks)
            i = next
            continue
        }

        if let groups = text.trimmed.captureGroups(imageOnlyRegex) {
            blocks.append(.image(url: groups[1], alt: groups[0], sourceStart: line.offset))
            i += 1
            continue
        }

        // Paragraph: consume until blank/structural line.
        var paragraph = ""
        let start = line.offset
        while i < lines.count && isProse(lines, index: i) {
            if !paragraph.isEmpty { paragraph.append("\n") }
            paragraph.append(lines[i].text.trimmedEnd)
            i += 1
        }
        blocks.append(.paragraph(spans: parseInline(paragraph), sourceStart: start))
    }
    return blocks
}

/// A line continues the current paragraph unless it is blank or opens a block.
private func isProse(_ lines: [GSLine], index: Int) -> Bool {
    let text = lines[index].text
    if text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty { return false }
    let t = text.trimmedEnd
    let lead = t.trimmingCharacters(in: .whitespaces)
    if fenceOpening(lead) != nil { return false }
    if lead.hasPrefix("$$") { return false }
    if headingLineRegex.firstMatch(in: t, range: NSRange(location: 0, length: (t as NSString).length)) != nil { return false }
    if t.matchesEntire(hrLineRegex) { return false }
    if text.captureGroups(quoteLineRegex) != nil { return false }
    if text.captureGroups(bulletItemRegex) != nil || text.captureGroups(orderedItemRegex) != nil { return false }
    if t.matchesEntire(detailsOpenRegex) || t.matchesEntire(detailsInlineRegex) { return false }
    if t.contains("|"), index + 1 < lines.count, isTableSeparator(lines[index + 1].text) { return false }
    return true
}

/// Fence names models reach for when they mean the Nexly timetable. `schedule`
/// is also a real interactive app type, so it becomes a plan only when the
/// body actually has timed rows.
private let nexlyPlanFenceLanguages: Set<String> = [
    "plan", "schedule", "timetable", "itinerary", "routine", "day",
]

/// A shorter or different marker stays inside the source, never closes it.
private func fenceOpening(_ text: String) -> (marker: Character, width: Int)? {
    let lead = text.trimmingCharacters(in: .whitespaces)
    guard let marker = lead.first, marker == "`" || marker == "~" else { return nil }
    let width = lead.prefix { $0 == marker }.count
    guard width >= 3, marker != "`" || !lead.dropFirst(width).contains("`") else { return nil }
    return (marker, width)
}

private func parseFence(_ lines: [GSLine], start: Int) -> (Block, Int) {
    guard let opening = fenceOpening(lines[start].text) else { preconditionFailure("Expected fence") }
    let opener = lines[start].text.trimmingCharacters(in: .whitespaces)
        .dropFirst(opening.width).trimmingCharacters(in: .whitespaces)
    let language = opener.components(separatedBy: .whitespacesAndNewlines).first { !$0.isEmpty }
    var body = ""
    var previousEnding = ""
    var i = start + 1
    while i < lines.count {
        let trimmed = lines[i].text.trimmingCharacters(in: .whitespacesAndNewlines)
        let closingWidth = trimmed.prefix { $0 == opening.marker }.count
        if closingWidth >= opening.width && trimmed.dropFirst(closingWidth).trimmingCharacters(in: .whitespaces).isEmpty {
            return (fenceToBlock(language: language, code: body, open: false, offset: lines[start].offset), i + 1)
        }
        // Only the final newline before a closing fence is markup. Preserve
        // every actual source separator, including blank lines and CRLF.
        if i > start + 1 { body.append(previousEnding) }
        body.append(lines[i].text)
        previousEnding = lines[i].ending
        i += 1
    }
    return (fenceToBlock(language: language, code: body, open: true, offset: lines[start].offset), lines.count)
}

private let mermaidFenceAliases: Set<String> = [
    "mermaid", "flowchart", "sequence", "sequencediagram", "classdiagram",
    "statediagram", "erdiagram", "gitgraph", "c4", "architecture", "diagram",
    "mindmap", "gantt"
]

private let mapFenceAliases: Set<String> = [
    "map", "maps", "location", "locations", "places", "route", "directions", "geo", "geojson"
]

private func fenceToBlock(language: String?, code: String, open: Bool, offset: Int) -> Block {
    let lang = language?.lowercased()
    let kind = widgetKindOf(lang)
    let trimmedCode = code.trimmingCharacters(in: .whitespacesAndNewlines)
    let isJson = trimmedCode.hasPrefix("{") || trimmedCode.hasPrefix("[")

    if !open,
       (lang == "mermaid" || (lang != nil && mermaidFenceAliases.contains(lang!) && !isJson)) {
        return .mermaid(source: code, sourceStart: offset)
    }
    if let lang, mapFenceAliases.contains(lang), (isJson || open) {
        return .html(kind: "map", source: code, open: open, sourceStart: offset)
    }
    if !open, let lang, nexlyPlanFenceLanguages.contains(lang), let plan = parseNexlyPlan(code) {
        return .plan(plan, open: false, sourceStart: offset)
    }
    if !open, let lang, ["json", "jsonc", "widget", "genui", "data", "ui"].contains(lang),
       let plan = parseNexlyPlan(code) {
        return .plan(plan, open: false, sourceStart: offset)
    }
    if lang == "plan" {
        if open {
            return .plan(parseNexlyPlan(code) ?? .placeholder, open: true, sourceStart: offset)
        }
        return .codeBlock(language: language, code: code, open: false, sourceStart: offset)
    }
    if let kind, kind != "plan", (isJson || open) {
        let injected = (lang != nil && lang != kind && isJson)
            ? injectWidgetType(json: code, alias: lang!, kind: kind)
            : code
        return .widget(kind: kind, json: injected, open: open, sourceStart: offset)
    }
    if let lang, HTML_LANGUAGES.contains(lang) {
        return .html(kind: lang == "svg" ? "svg" : "html", source: code, open: open, sourceStart: offset)
    }
    return .codeBlock(language: language, code: code, open: open, sourceStart: offset)
}

private func parseMathBlock(_ lines: [GSLine], start: Int, into blocks: inout [Block]) -> Int {
    let first = lines[start].text.trimmingCharacters(in: .whitespaces)
    let rest = String(first.dropFirst(2))
    var body = ""
    var closed = false
    var i = start + 1
    if let closeIndex = rest.range(of: "$$") {
        body += String(rest[..<closeIndex.lowerBound])
        closed = true
    } else {
        if !rest.trimmed.isEmpty { body += rest.trimmed }
        while i < lines.count {
            let l = lines[i].text
            if let closeIndex = l.range(of: "$$") {
                let before = String(l[..<closeIndex.lowerBound])
                if !body.isEmpty && !before.trimmed.isEmpty { body.append("\n") }
                body += before.trimmed
                closed = true
                i += 1
                break
            }
            if !body.isEmpty { body.append("\n") }
            body += l.trimmed
            i += 1
        }
    }
    let latex = body.trimmingCharacters(in: .whitespacesAndNewlines)
    if !latex.isEmpty {
        blocks.append(.mathBlock(latex: latex, sourceStart: lines[start].offset))
    }
    return closed ? i : lines.count
}

private func parseTable(_ lines: [GSLine], start: Int) -> (Block, Int) {
    let headerCells = splitCells(lines[start].text)
    let alignCells = splitCells(lines[start + 1].text)
    let columnCount = max(headerCells.count, alignCells.count)
    let headers: [[InlineSpan]] = (0..<columnCount).map { c in
        parseInline(c < headerCells.count ? headerCells[c] : "")
    }
    let aligns: [TableAlign] = (0..<columnCount).map { c in
        let cell = c < alignCells.count ? alignCells[c].trimmed : ""
        let left = cell.hasPrefix(":")
        let right = cell.hasSuffix(":")
        if left && right { return .center }
        if right { return .right }
        return .left
    }
    var rows: [[[InlineSpan]]] = []
    var i = start + 2
    while i < lines.count {
        let t = lines[i].text
        if t.trimmed.isEmpty || !t.contains("|") { break }
        if isTableSeparator(t) { i += 1; continue }
        let cells = splitCells(t)
        rows.append((0..<columnCount).map { c in parseInline(c < cells.count ? cells[c] : "") })
        i += 1
    }
    return (.table(headers: headers, aligns: aligns, rows: rows, sourceStart: lines[start].offset), i)
}

private struct GSListRecord {
    let level: Int
    let ordered: Bool
    let number: Int
    let text: String
    let offset: Int
    var continuation: [(offset: Int, text: String)] = []
}

private func parseList(_ lines: [GSLine], start: Int) -> ([Block], Int) {
    var records: [GSListRecord] = []
    var i = start
    while i < lines.count {
        let text = lines[i].text
        if let g = text.captureGroups(bulletItemRegex) {
            let indent = g[0].filter { $0 == " " }.count + g[0].filter { $0 == "\t" }.count * 2
            records.append(GSListRecord(level: indent / 2, ordered: false, number: 0, text: g[2], offset: lines[i].offset))
            i += 1
        } else if let g = text.captureGroups(orderedItemRegex) {
            let indent = g[0].filter { $0 == " " }.count + g[0].filter { $0 == "\t" }.count * 2
            records.append(GSListRecord(level: indent / 2, ordered: true, number: Int(g[1]) ?? 0, text: g[2], offset: lines[i].offset))
            i += 1
        } else if !text.trimmed.isEmpty, let last = records.last {
            let required = String(repeating: " ", count: max(last.level * 2 + 2, 1))
            if text.hasPrefix(required) {
                records[records.count - 1].continuation.append((offset: lines[i].offset, text: text.trimmed))
                i += 1
            } else {
                break
            }
        } else {
            break
        }
    }
    var blocks: [Block] = []
    var r = 0
    while r < records.count {
        let (block, used) = buildList(records, from: r, level: records[r].level)
        blocks.append(block)
        r += used
    }
    return (blocks, i)
}

private let taskCheckboxRegex = try! NSRegularExpression(pattern: #"^\[([ xX✓☑])\]\s+(.*)$"#)

private func buildList(_ records: [GSListRecord], from: Int, level: Int) -> (Block, Int) {
    let ordered = records[from].ordered
    var items: [ListItem] = []
    var i = from
    while i < records.count && records[i].level >= level {
        if records[i].level > level { i += 1; continue } // orphan deeper record (defensive)
        if records[i].ordered != ordered { break } // kind switch → sibling list
        let record = records[i]
        var j = i + 1
        var childRecords: [GSListRecord] = []
        while j < records.count && records[j].level > level {
            childRecords.append(records[j])
            j += 1
        }
        var children: [Block] = record.continuation.map { .paragraph(spans: parseInline($0.text), sourceStart: $0.offset) }
        if !childRecords.isEmpty {
            children.append(buildList(childRecords, from: 0, level: childRecords[0].level).0)
        }
        var itemChecked: Bool? = nil
        var itemText = record.text
        if !record.ordered, let g = record.text.trimmed.captureGroups(taskCheckboxRegex), g.count >= 2 {
            let mark = g[0]
            itemChecked = (mark == "x" || mark == "X" || mark == "✓" || mark == "☑")
            itemText = g[1]
        }
        items.append(ListItem(
            number: record.ordered ? record.number : nil,
            spans: parseInline(itemText),
            children: children,
            checked: itemChecked
        ))
        i = j
    }
    let block: Block = ordered
        ? .orderedList(start: records[from].number, items: items, sourceStart: records[from].offset)
        : .bulletList(items: items, sourceStart: records[from].offset)
    return (block, i - from)
}

private func parseDetails(_ lines: [GSLine], start: Int) -> (Block, Int) {
    var summary = ""
    var i = start
    if let inline = lines[start].text.trimmedEnd.captureGroups(detailsInlineRegex) {
        summary = inline[0]
        i += 1
    } else {
        i += 1
        while i < lines.count {
            if let g = lines[i].text.trimmedEnd.captureGroups(summaryRegex) {
                summary = g[0]
                i += 1
                break
            }
            if !lines[i].text.trimmed.isEmpty { break }
            i += 1
        }
    }
    var inner: [GSLine] = []
    while i < lines.count {
        if lines[i].text.trimmedEnd.matchesEntire(detailsCloseRegex) { i += 1; break }
        inner.append(lines[i])
        i += 1
    }
    return (
        .collapsible(summary: summary.isEmpty ? "Details" : summary, blocks: parseCore(inner), sourceStart: lines[start].offset),
        i
    )
}

// MARK: - Streaming incremental parse (TASK 1b)

/**
 * Reparse the last line-run, snapped to its containing block's opener.
 * Fences, math, quotes and details may span blank lines. Earlier blocks keep
 * their instances; executable source and original line endings stay intact.
 */
func parseStreamingBlocks(
    previousContent: String,
    previousBlocks: [Block],
    content: String
) -> [Block] {
    let previous = previousContent
    let current = content
    if previousBlocks.isEmpty || !current.hasPrefix(previous) {
        return parseBlocks(current)
    }
    let tailStart = streamingTailStart(previous, blocks: previousBlocks)
    let head = previousBlocks.prefix { $0.sourceStart < tailStart }
    if head.isEmpty { return parseBlocks(current) }
    let tailText = String(current[current.index(current.startIndex, offsetBy: tailStart)...])
    let tail = parseBlocks(tailText).map { shiftSource($0, by: tailStart) }
    return Array(head) + tail
}

func streamingTailStart(_ text: String, blocks: [Block]) -> Int {
    let runStart = lastLineRunStart(text)
    return blocks.last(where: { $0.sourceStart <= runStart })?.sourceStart ?? 0
}

/**
 * Start offset of the last contiguous non-blank line-run (blank trailing
 * lines are skipped). Character-index arithmetic, O(length of the run).
 * Returns 0 when the document is one run or empty.
 */
private func lastLineRunStart(_ text: String) -> Int {
    let chars = Array(text)
    var end = chars.count
    while end > 0 {
        let c = chars[end - 1]
        if c == "\n" || c == "\r\n" || c == " " || c == "\t" || c == "\r" { end -= 1 } else { break }
    }
    if end == 0 { return 0 }
    var runStart = 0
    var searchEnd = end
    while searchEnd > 0 {
        var lineStart = 0
        if searchEnd > 0 {
            var idx = searchEnd - 1
            while idx >= 0, chars[idx] != "\n", chars[idx] != "\r\n" { idx -= 1 }
            lineStart = idx + 1
        }
        var blank = true
        var k = lineStart
        while k < searchEnd {
            let c = chars[k]
            if c != " " && c != "\t" && c != "\r" { blank = false; break }
            k += 1
        }
        if blank { break }
        runStart = lineStart
        if lineStart == 0 { break }
        searchEnd = lineStart - 1
    }
    return runStart
}

/// Re-bases every source offset in the block (and its whole nested subtree —
/// list children, quote and collapsible innards) from tail-relative space
/// into absolute document space.
private func shiftSource(_ block: Block, by delta: Int) -> Block {
    switch block {
    case .paragraph(let spans, let s):
        return .paragraph(spans: spans, sourceStart: s + delta)
    case .heading(let level, let spans, let s):
        return .heading(level: level, spans: spans, sourceStart: s + delta)
    case .bulletList(let items, let s):
        return .bulletList(items: items.map { shiftItem($0, by: delta) }, sourceStart: s + delta)
    case .orderedList(let start, let items, let s):
        return .orderedList(start: start, items: items.map { shiftItem($0, by: delta) }, sourceStart: s + delta)
    case .blockQuote(let blocks, let s):
        return .blockQuote(blocks: blocks.map { shiftSource($0, by: delta) }, sourceStart: s + delta)
    case .codeBlock(let language, let code, let open, let s):
        return .codeBlock(language: language, code: code, open: open, sourceStart: s + delta)
    case .table(let headers, let aligns, let rows, let s):
        return .table(headers: headers, aligns: aligns, rows: rows, sourceStart: s + delta)
    case .plan(let plan, let open, let s):
        return .plan(plan, open: open, sourceStart: s + delta)
    case .widget(let kind, let json, let open, let s):
        return .widget(kind: kind, json: json, open: open, sourceStart: s + delta)
    case .html(let kind, let source, let open, let s):
        return .html(kind: kind, source: source, open: open, sourceStart: s + delta)
    case .divider(let s):
        return .divider(sourceStart: s + delta)
    case .mathBlock(let latex, let s):
        return .mathBlock(latex: latex, sourceStart: s + delta)
    case .mermaid(let source, let s):
        return .mermaid(source: source, sourceStart: s + delta)
    case .image(let url, let alt, let s):
        return .image(url: url, alt: alt, sourceStart: s + delta)
    case .collapsible(let summary, let blocks, let s):
        return .collapsible(summary: summary, blocks: blocks.map { shiftSource($0, by: delta) }, sourceStart: s + delta)
    case .citations(let entries, let s):
        return .citations(entries, sourceStart: s + delta)
    case .toolResult(let entry, let s):
        return .toolResult(entry, sourceStart: s + delta)
    }
}

private func shiftItem(_ item: ListItem, by delta: Int) -> ListItem {
    ListItem(
        number: item.number,
        spans: item.spans,
        children: item.children.map { shiftSource($0, by: delta) },
        checked: item.checked
    )
}
