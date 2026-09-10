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
    let number: Int?
    let spans: [InlineSpan]
    let children: [Block]
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
}

func parseBlocks(_ content: String, metadata: RichMetadata? = nil) -> [Block] {
    let normalized = content.replacingOccurrences(of: "\r\n", with: "\n")
    let lines = toLines(normalized)
    var blocks = parseCore(lines)
    if let metadata = metadata {
        let tools = metadata.toolResults.map { Block.toolResult($0, sourceStart: 0) }
        let citations = metadata.citations.isEmpty ? [] : [Block.citations(metadata.citations, sourceStart: 0)]
        blocks = tools + blocks + citations
    }
    return blocks
}

private func toLines(_ text: String) -> [GSLine] {
    var out: [GSLine] = []
    var offset = 0
    for raw in text.components(separatedBy: "\n") {
        out.append(GSLine(text: raw, offset: offset))
        offset += raw.count + 1
    }
    return out
}

private let headingLineRegex = try! NSRegularExpression(pattern: "^(#{1,6})\\s+(.+?)\\s*#*\\s*$")
private let hrLineRegex = try! NSRegularExpression(pattern: "^ {0,3}((\\* *){3,}|(- *){3,}|(_ *){3,})$")
private let bulletItemRegex = try! NSRegularExpression(pattern: "^(\\s*)([-*+])\\s+(.*)$")
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
        return (1..<m.numberOfRanges).map { range in
            range.location == NSNotFound ? "" : ns.substring(with: range)
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

        if lead.hasPrefix("```") {
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
                inner.append(GSLine(text: g[0], offset: lines[i].offset))
                i += 1
            }
            blocks.append(.blockQuote(parseCore(inner), sourceStart: line.offset))
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
    if lead.hasPrefix("```") { return false }
    if lead.hasPrefix("$$") { return false }
    if headingLineRegex.firstMatch(in: t, range: NSRange(location: 0, length: (t as NSString).length)) != nil { return false }
    if t.matchesEntire(hrLineRegex) { return false }
    if text.captureGroups(quoteLineRegex) != nil { return false }
    if text.captureGroups(bulletItemRegex) != nil || text.captureGroups(orderedItemRegex) != nil { return false }
    if t.matchesEntire(detailsOpenRegex) || t.matchesEntire(detailsInlineRegex) { return false }
    if t.contains("|"), index + 1 < lines.count, isTableSeparator(lines[index + 1].text) { return false }
    return true
}

private func parseFence(_ lines: [GSLine], start: Int) -> (Block, Int) {
    let opener = lines[start].text.trimmingCharacters(in: .whitespaces)
        .dropFirst(3)
        .trimmingCharacters(in: .whitespaces)
    let language = opener
        .components(separatedBy: .whitespacesAndNewlines)
        .first { !$0.isEmpty }
    var body = ""
    var i = start + 1
    while i < lines.count {
        let trimmed = lines[i].text.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.hasPrefix("```") && trimmed.dropFirst(3).trimmingCharacters(in: .whitespaces).isEmpty {
            return (.codeBlock(language: language, code: String(body.dropLast(body.hasSuffix("\n") ? 1 : 0)), open: false, sourceStart: lines[start].offset), i + 1)
        }
        if !body.isEmpty { body.append("\n") }
        body.append(lines[i].text)
        i += 1
    }
    // Unterminated trailing fence — mid-stream: render what arrived as open code.
    return (.codeBlock(language: language, code: String(body.dropLast(body.hasSuffix("\n") ? 1 : 0)), open: true, sourceStart: lines[start].offset), lines.count)
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
    var continuation: [String] = []
}

private func parseList(_ lines: [GSLine], start: Int) -> ([Block], Int) {
    var records: [GSListRecord] = []
    var i = start
    while i < lines.count {
        let text = lines[i].text
        if let g = text.captureGroups(bulletItemRegex) {
            let indent = g[0].filter { $0 == " " }.count + g[0].filter { $0 == "\t" }.count * 2
            records.append(GSListRecord(level: indent / 2, ordered: false, number: 0, text: g[2]))
            i += 1
        } else if let g = text.captureGroups(orderedItemRegex) {
            let indent = g[0].filter { $0 == " " }.count + g[0].filter { $0 == "\t" }.count * 2
            records.append(GSListRecord(level: indent / 2, ordered: true, number: Int(g[1]) ?? 0, text: g[2]))
            i += 1
        } else if !text.trimmed.isEmpty, let last = records.last {
            let required = String(repeating: " ", count: max(last.level * 2 + 2, 1))
            if text.hasPrefix(required) {
                records[records.count - 1].continuation.append(text.trimmed)
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
        var children: [Block] = record.continuation.map { .paragraph(spans: parseInline($0), sourceStart: 0) }
        if !childRecords.isEmpty {
            children.append(buildList(childRecords, from: 0, level: childRecords[0].level).0)
        }
        items.append(ListItem(
            number: record.ordered ? record.number : nil,
            spans: parseInline(record.text),
            children: children
        ))
        i = j
    }
    let block: Block = ordered
        ? .orderedList(start: records[from].number, items: items, sourceStart: 0)
        : .bulletList(items: items, sourceStart: 0)
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
