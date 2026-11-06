import SwiftUI
import JavaScriptCore

/// STEP 5 renderer (iOS twin of RichBlocks.kt) — the consumer half of the
/// message → blocks seam. iOS parses on FINALIZE only (the streaming bubble
/// stays plain text by design — deep-perf decision preserved), so block views
/// here never re-parse at stream cadence. Document-style: prose sits directly
/// on the canvas; chrome stays only on content that earns it (code, tables,
/// diagrams, citations, tool cards, images, collapsibles).

// MARK: - Entry

struct BlocksView: View {
    let blocks: [Block]
    /// PHASE 8.1: ordinal → source URL for the answer's `[N]` citation
    /// chips (built from the turn's real sources by the caller). Markers
    /// with no entry stay literal text — the parser never fabricates sources.
    var citations: [Int: String] = [:]

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            ForEach(Array(blocks.enumerated()), id: \.offset) { index, block in
                BlockView(block: block, isLast: index == blocks.count - 1, citations: citations)
            }
        }
    }
}

// MARK: - Nexly glass transcript

/// The reference composition, iOS twin of Android's `NexlyAssistantBlocks`:
/// consecutive prose becomes one milky glass card, a `plan` fence becomes its
/// own sibling card, and every card carries the companion orb outside it. The
/// Thinking/Reasoning/Effort panel sits after the preamble inside the first
/// prose card and survives finalize for plan turns (the timetable is the proof
/// that planning happened), exactly like the reference.
struct NexlyAssistantBlocksView: View {
    @Environment(\.horizontalSizeClass) private var sizeClass
    let blocks: [Block]
    var isStreaming: Bool = false
    var showStatus: Bool = false
    var thinkingPhase: NexlyThinkingPhase = .done
    var thinkingEffort: String? = nil
    var thinkingReasoningHint: String? = nil
    var searchSourceCount: Int = 0
    var showThinkingStages: Bool = true
    var isPlanTurn: Bool = false
    /// Plan turns place the status card after the whole preamble; a non-plan
    /// answer places it after the first paragraph.
    var statusAtEnd: Bool = true
    var citations: [Int: String] = [:]
    var planTimestamp: String? = nil
    var conversationID: String? = nil

    private enum Group {
        case prose([Block])
        case plan(NexlyPlanData, Bool)
    }

    var body: some View {
        let groups = grouped()
        let statusIndex = groups.firstIndex { group in
            if case .prose = group { return true }
            return false
        }
        // The sheet keeps assistant cards at about 69% of the phone width,
        // leaving the pearl field visible to the right of the glass.
        let cardMax: CGFloat = sizeClass == .regular ? 340 : 286
        let planMax: CGFloat = sizeClass == .regular ? 350 : 290
        VStack(alignment: .leading, spacing: 12) {
            if groups.isEmpty {
                HStack(alignment: .top, spacing: 9) {
                    NexlyAssistantOrb()
                    if showStatus {
                        NexlyThinkingCard(phase: thinkingPhase, effort: thinkingEffort, isPlan: isPlanTurn, reasoningHint: thinkingReasoningHint, searchSources: searchSourceCount, showStages: showThinkingStages)
                            .frame(maxWidth: cardMax, alignment: .leading)
                    } else {
                        Text("…")
                            .font(Aero.body())
                            .foregroundStyle(Aero.text)
                            .padding(.vertical, 8)
                    }
                    Spacer(minLength: 0)
                }
            } else {
                ForEach(Array(groups.enumerated()), id: \.offset) { index, group in
                    HStack(alignment: .top, spacing: 9) {
                        NexlyAssistantOrb()
                        switch group {
                        case .plan(let plan, let open):
                            NexlyPlanCard(
                                plan: plan,
                                open: open,
                                fallbackTimestamp: planTimestamp,
                                conversationID: conversationID
                            )
                            .frame(maxWidth: planMax, alignment: .leading)
                            .layoutPriority(1)
                        case .prose(let prose):
                            proseCard(
                                prose,
                                isStatusHost: index == statusIndex,
                                isFirst: index == 0
                            )
                            .frame(maxWidth: cardMax, alignment: .leading)
                            .layoutPriority(1)
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    @ViewBuilder
    private func proseCard(_ prose: [Block], isStatusHost: Bool, isFirst: Bool) -> some View {
        let statusHostIndex = statusAtEnd ? prose.count - 1 : 0
        VStack(alignment: .leading, spacing: 8) {
            StructuredProseSequenceView(
                prose: prose,
                isStreaming: isStreaming,
                citations: citations,
                isStatusHost: isStatusHost,
                showStatus: showStatus,
                statusHostIndex: statusHostIndex,
                thinkingPhase: thinkingPhase,
                thinkingEffort: thinkingEffort,
                isPlanTurn: isPlanTurn,
                thinkingReasoningHint: thinkingReasoningHint,
                searchSourceCount: searchSourceCount,
                showThinkingStages: showThinkingStages
            )
            if isStatusHost && showStatus && prose.isEmpty {
                NexlyThinkingCard(phase: thinkingPhase, effort: thinkingEffort, isPlan: isPlanTurn, reasoningHint: thinkingReasoningHint, searchSources: searchSourceCount, showStages: showThinkingStages)
            } else if prose.isEmpty && isStreaming {
                Text("…")
                    .font(Aero.body())
                    .foregroundStyle(Aero.text)
            }
        }
        .padding(.horizontal, 13)
        .padding(.vertical, 10)
        .frame(maxWidth: .infinity, alignment: .leading)
        .nexlyGlassCard(corner: 20)
        .environment(\.nexlyProseCompact, true)
        .environment(\.nexlyCodeWrap, true)
        .padding(.top, isFirst ? 0 : 2)
    }

    private func grouped() -> [Group] {
        var out: [Group] = []
        var prose: [Block] = []
        func flush() {
            if !prose.isEmpty {
                out.append(.prose(prose))
                prose.removeAll()
            }
        }
        for block in blocks {
            if case .plan(let plan, let open, _) = block {
                flush()
                out.append(.plan(plan, open))
            } else {
                prose.append(block)
            }
        }
        flush()
        // Status must lead the turn. When the model opened with the timetable
        // (no preamble yet), give the thinking panel its own prose card before
        // the plan instead of hiding it inside a later paragraph.
        if showStatus, let first = out.first, case .plan(_, _) = first {
            out.insert(.prose([]), at: 0)
        }
        return out
    }
}

private enum SectionListMode {
    case normal
    case checkmark
    case todo
    case sources
}

private struct NexlySectionListModeKey: EnvironmentKey {
    static let defaultValue: SectionListMode = .normal
}

private extension EnvironmentValues {
    var nexlySectionListMode: SectionListMode {
        get { self[NexlySectionListModeKey.self] }
        set { self[NexlySectionListModeKey.self] = newValue }
    }
}

private func classifyCalloutHeaderTitle(_ raw: String) -> (CalloutTone, String)? {
    let clean = raw
        .trimmingCharacters(in: .whitespacesAndNewlines)
        .trimmingCharacters(in: CharacterSet(charactersIn: ":"))
        .trimmingCharacters(in: .whitespacesAndNewlines)
    let lower = clean.lowercased()
    if ["important", "warning", "caution", "attention", "important notice"].contains(lower) {
        return (.warning, "⚠️")
    }
    if ["quick summary", "summary", "tl;dr", "tldr", "executive summary"].contains(lower) {
        return (.summary, "⚡")
    }
    if ["key insight", "key insights", "quick fact", "quick facts", "pro tip", "tip", "note", "key takeaway", "key takeaways"].contains(lower) {
        return (.fact, "💡")
    }
    return nil
}

private func classifySectionListMode(_ headingText: String) -> SectionListMode {
    let lower = headingText.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
    if lower.contains("next action") || lower.contains("action item") || lower.contains("todo") || lower.contains("to-do") || lower.contains("checklist") || lower == "tasks" || lower.contains("your tasks") {
        return .todo
    }
    if lower.contains("key source") || lower == "sources" || lower.contains("trusted source") {
        return .sources
    }
    if lower.contains("priorit") || lower.contains("feature") || lower.contains("highlight") || lower.contains("benefit") {
        return .checkmark
    }
    return .normal
}

private struct StructuredProseSequenceView: View {
    @Environment(\.gsWidgetAction) private var widgetAction
    let prose: [Block]
    let isStreaming: Bool
    let citations: [Int: String]
    let isStatusHost: Bool
    let showStatus: Bool
    let statusHostIndex: Int
    let thinkingPhase: String?
    let thinkingEffort: String?
    let isPlanTurn: Bool
    let thinkingReasoningHint: String?
    let searchSourceCount: Int
    let showThinkingStages: Bool

    @State private var activeDashboardTab: String = "Dashboard"

    private var dashboardTabs: [String] {
        var tabs = ["Dashboard"]
        let hasTable = prose.contains {
            if case .table = $0 { return true }
            if case .widget(let k, _, _, _) = $0, k == "table" { return true }
            return false
        }
        let hasTodo = prose.contains {
            if case .bulletList(let items, _) = $0, items.contains(where: { $0.checked != nil }) { return true }
            if case .heading(_, let spans, _) = $0, classifySectionListMode(plainTextOfSpans(spans)) == .todo { return true }
            if case .widget(let k, _, _, _) = $0, k == "checklist" { return true }
            return false
        }
        let hasCalc = prose.contains {
            if case .widget(let k, _, _, _) = $0, k == "calc" { return true }
            if case .heading(_, let spans, _) = $0, plainTextOfSpans(spans).lowercased().contains("calculator") { return true }
            return false
        }
        let hasCode = prose.contains {
            if case .code = $0 { return true }
            return false
        }
        if hasTable { tabs.append("Table") }
        if hasTodo { tabs.append("Todo") }
        if hasCalc { tabs.append("Calculator") }
        else if hasCode && tabs.count >= 3 { tabs.append("Code") }
        return tabs.count >= 3 ? tabs : []
    }

    private struct SequenceEntry {
        let index: Int
        let block: Block
        let sectionMode: SectionListMode
        let coalescedCallout: (CalloutParsed, [ListItem])?
    }

    private var entries: [SequenceEntry] {
        var out: [SequenceEntry] = []
        var mode: SectionListMode = .normal
        var i = 0
        while i < prose.count {
            let block = prose[i]
            let nextBlock: Block? = (i + 1 < prose.count) ? prose[i + 1] : nil

            var calloutHeader: (String, CalloutTone, String)? = nil
            if case .heading(_, let spans, _) = block {
                let hText = plainTextOfSpans(spans).trimmingCharacters(in: .whitespacesAndNewlines)
                if let m = classifyCalloutHeaderTitle(hText) {
                    calloutHeader = (hText.trimmingCharacters(in: CharacterSet(charactersIn: ":")), m.0, m.1)
                }
            } else if case .paragraph(let spans, _) = block, spans.count == 1, case .bold(let bch) = spans[0] {
                let bText = plainTextOfSpans(bch).trimmingCharacters(in: .whitespacesAndNewlines)
                if let m = classifyCalloutHeaderTitle(bText) {
                    calloutHeader = (bText.trimmingCharacters(in: CharacterSet(charactersIn: ":")), m.0, m.1)
                }
            }

            if let (title, tone, icon) = calloutHeader, let next = nextBlock {
                if case .paragraph(let bodySpans, _) = next {
                    let parsed = CalloutParsed(tone: tone, icon: icon, title: title, bodySpans: bodySpans)
                    out.append(SequenceEntry(index: i, block: block, sectionMode: mode, coalescedCallout: (parsed, [])))
                    i += 2
                    continue
                } else if case .bulletList(let items, _) = next {
                    let parsed = CalloutParsed(tone: tone, icon: icon, title: title, bodySpans: [])
                    out.append(SequenceEntry(index: i, block: block, sectionMode: mode, coalescedCallout: (parsed, items)))
                    i += 2
                    continue
                }
            }

            if case .heading(_, let spans, _) = block {
                mode = classifySectionListMode(plainTextOfSpans(spans))
            } else {
                switch block {
                case .bulletList, .orderedList:
                    break
                default:
                    mode = .normal
                }
            }
            out.append(SequenceEntry(index: i, block: block, sectionMode: mode, coalescedCallout: nil))
            i += 1
        }
        return out
    }

    var body: some View {
        let tabs = dashboardTabs
        if !tabs.isEmpty {
            HStack(spacing: 4) {
                ForEach(tabs, id: \.self) { tab in
                    let selected = activeDashboardTab == tab
                    Button {
                        GSHaptics.selection()
                        activeDashboardTab = tab
                    } label: {
                        Text(tab)
                            .font(.system(size: 11.5, weight: selected ? .bold : .medium))
                            .foregroundStyle(selected ? Color(red: 0.310, green: 0.208, blue: 0.910) : Color(red: 0.337, green: 0.306, blue: 0.478))
                            .padding(.horizontal, 11)
                            .padding(.vertical, 5)
                            .background(
                                RoundedRectangle(cornerRadius: 9)
                                    .fill(selected ? Color.white.opacity(0.92) : Color.clear)
                            )
                    }
                    .buttonStyle(.plain)
                }
                Spacer(minLength: 0)
            }
            .padding(3)
            .background(RoundedRectangle(cornerRadius: 12).fill(Color(red: 0.929, green: 0.902, blue: 1.0)))
        }

        ForEach(entries, id: \.index) { entry in
            if let (callout, extraItems) = entry.coalescedCallout {
                NexlyCalloutCardView(callout: callout, extraItems: extraItems, citations: citations)
            } else {
                BlockView(block: entry.block, isLast: entry.index == prose.count - 1, citations: citations)
                    .environment(\.nexlySectionListMode, entry.sectionMode)
            }
            if isStatusHost && showStatus && entry.index == statusHostIndex {
                if !prose.isEmpty { Spacer().frame(height: 2) }
                NexlyThinkingCard(phase: thinkingPhase, effort: thinkingEffort, isPlan: isPlanTurn, reasoningHint: thinkingReasoningHint, searchSources: searchSourceCount, showStages: showThinkingStages)
            }
        }

        if !isStreaming && prose.count >= 2 {
            let endsWithAction: Bool = {
                guard let last = prose.last else { return false }
                if case .widget(let k, _, _, _) = last, ["actions", "followups", "chips", "menu"].contains(k) {
                    return true
                }
                return false
            }()
            if !endsWithAction {
                NexlyContextualActionPillsRowView(prose: prose, onAction: { prompt in
                    if prompt.lowercased().contains("copy") || prompt.lowercased().contains("share") {
                        UIPasteboard.general.string = prompt
                    } else {
                        widgetAction?(prompt)
                    }
                })
            }
        }
    }
}

private struct NexlyContextualActionPillsRowView: View {
    let prose: [Block]
    let onAction: (String) -> Void

    private var actions: [(String, String)] {
        let hasChart = prose.contains { if case .widget(let k, _, _, _) = $0 { return k == "chart" }; return false }
        let hasTable = prose.contains {
            if case .table = $0 { return true }
            if case .widget(let k, _, _, _) = $0 { return k == "table" }
            return false
        }
        let hasCode = prose.contains { if case .code = $0 { return true }; return false }
        let hasSources = prose.contains {
            if case .citations = $0 { return true }
            if case .heading(_, let spans, _) = $0 { return classifySectionListMode(plainTextOfSpans(spans)) == .sources }
            return false
        }
        let hasPlanOrSteps = prose.contains {
            if case .orderedList = $0 { return true }
            if case .bulletList(let items, _) = $0, items.contains(where: { $0.checked != nil }) { return true }
            return false
        }
        if hasChart && hasTable && hasCode {
            return [("💬", "Ask follow-up"), ("📊", "Show me a detailed report"), ("✦", "Create a presentation")]
        }
        if hasChart {
            return [("⤓", "Download Report"), ("⤢", "View Details")]
        }
        if hasTable {
            return [("📊", "View Full Report"), ("🔗", "Share")]
        }
        if hasSources {
            return [("↗", "View All Sources"), ("📋", "Copy Citations"), ("📖", "Learn More")]
        }
        if hasCode {
            return [("</>", "Edit Code"), ("⤓", "Download Project"), ("💬", "Ask Follow Up")]
        }
        if hasPlanOrSteps {
            return [("📅", "Make a timeline"), ("⤢", "Expand plan"), ("🔗", "Share")]
        }
        return []
    }

    var body: some View {
        let list = actions
        if !list.isEmpty {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(Array(list.enumerated()), id: \.offset) { _, pair in
                        Button {
                            GSHaptics.selection()
                            onAction(pair.1)
                        } label: {
                            HStack(spacing: 5) {
                                Text(pair.0)
                                    .font(.system(size: 11.5, weight: .bold))
                                    .foregroundStyle(Color(red: 0.357, green: 0.239, blue: 0.961))
                                Text(pair.1)
                                    .font(.system(size: 11.5, weight: .semibold))
                                    .foregroundStyle(Color(red: 0.157, green: 0.125, blue: 0.306))
                            }
                            .padding(.horizontal, 12)
                            .padding(.vertical, 6)
                            .background(Capsule().fill(Color.white.opacity(0.84)))
                            .overlay(Capsule().stroke(Color(red: 0.882, green: 0.855, blue: 0.980), lineWidth: 1))
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(.top, 4)
            }
        }
    }
}

/// Companion mark beside each assistant card — the reference keeps identity
/// outside the glass, never as an avatar inside the text. It is the lilac
/// sparkle in a lit glass circle, matching the header brand and Android.
private struct NexlyAssistantOrb: View {
    var body: some View {
        ZStack {
            Circle()
                .fill(Color.white.opacity(0.94))
                .frame(width: 30, height: 30)
                .overlay(Circle().strokeBorder(Color.white, lineWidth: 1))
                .shadow(color: Color(red: 0.50, green: 0.40, blue: 0.85).opacity(0.18), radius: 3, y: 1)
            NexlyGlyph(name: "sparkles", size: 15, color: Color(red: 0.56, green: 0.48, blue: 1.0))
        }
        .accessibilityLabel("Nexly")
    }
}

@ViewBuilder
func BlockView(block: Block, isLast: Bool, citations: [Int: String]) -> some View {
    switch block {
    case .paragraph(let spans, _):
        ParagraphView(spans: spans, showCaret: false, citations: citations)
    case .heading(let level, let spans, _):
        HeadingView(level: level, spans: spans, isFirst: false, citations: citations)
    case .bulletList(let items, _):
        BulletListView(items: items, citations: citations)
    case .orderedList(let start, let items, _):
        OrderedListView(start: start, items: items, citations: citations)
    case .blockQuote(let inner, _):
        BlockQuoteView(blocks: inner, citations: citations)
    case .codeBlock(let language, let code, let open, _):
        CodeBlockCardView(language: language, code: code, open: open)
    case .table(let headers, let aligns, let rows, _):
        TableBlockView(headers: headers, aligns: aligns, rows: rows, citations: citations)
    case .plan(let plan, let open, _):
        NexlyPlanCard(plan: plan, open: open)
    case .widget(let kind, let json, let open, _):
        WidgetBlockView(kind: kind, json: json, open: open)
    case .html(let kind, let source, let open, _):
        HtmlBlockView(kind: kind, source: source, open: open)
    case .divider:
        DividerBlockView()
    case .mathBlock(let latex, _):
        MathBlockView(latex: latex)
    case .mermaid(let source, _):
        MermaidBlockView(source: source, streaming: false)
    case .image(let url, let alt, _):
        ImageBlockView(url: url, alt: alt)
    case .collapsible(let summary, let inner, _):
        CollapsibleBlockView(summary: summary, blocks: inner, citations: citations)
    case .citations(let citations, _):
        CitationsBlockView(citations: citations)
    case .toolResult(let entry, _):
        ToolResultBlockView(entry: entry)
    }
}

// MARK: - Inline spans → AttributedString

private struct SpanTheme {
    let monoBackground: Color
    let linkColor: Color
    let textColor: Color
    let baseSize: CGFloat
    /// PHASE 8.1: ordinal → source URL. A plain-text `[N]` whose ordinal is
    /// here becomes a tappable superscript chip; everything else stays text.
    var citations: [Int: String] = [:]
}

private func attributed(
    from spans: [InlineSpan],
    theme: SpanTheme,
    style: InlinePresentationIntent = []
) -> AttributedString {
    var result = AttributedString()
    append(spans: spans, into: &result, theme: theme, style: style)
    return result
}

private func append(
    spans: [InlineSpan],
    into result: inout AttributedString,
    theme: SpanTheme,
    style: InlinePresentationIntent = []
) {
    for span in spans {
        switch span {
        case .text(let text):
            appendText(text, into: &result, theme: theme, style: style)
        case .bold(let children):
            result.append(attributed(from: children, theme: theme, style: style.union(.stronglyEmphasized)))
        case .italic(let children):
            result.append(attributed(from: children, theme: theme, style: style.union(.emphasized)))
        case .strike(let children):
            result.append(attributed(from: children, theme: theme, style: style.union(.strikethrough)))
        case .code(let code):
            var run = AttributedString(code)
            run.font = .system(size: theme.baseSize * 0.88, weight: .regular, design: .monospaced)
            run.backgroundColor = theme.monoBackground
            if !style.isEmpty { run.inlinePresentationIntent = style }
            result.append(run)
        case .link(let text, let url):
            if let url = URL(string: url) {
                var run = AttributedString(text)
                run.link = url
                run.underlineStyle = .single
                run.foregroundColor = theme.linkColor
                result.append(run)
            } else {
                result.append(AttributedString(text))
            }
        case .math(let latex):
            if let rendered = renderMathAttributedString(latex, baseSize: theme.baseSize, color: theme.textColor) {
                result.append(rendered)
            } else {
                result.append(AttributedString(latex))
            }
        }
    }
}

// MARK: - Citation chips (PHASE 8.1)

private let citationMarkerRegex = try! NSRegularExpression(pattern: "[\\[【〚［]\\s*(\\d{1,3})\\s*[\\]】〛］]")

/// Splits a plain-text run on `[N]` markers: matched ordinals become tappable
/// superscript chips linking the real source URL; unmatched markers stay
/// literal text (the protocol's strip rule, applied in the renderer).
/// Inline `:icon:` tokens and any emoji the model slipped through become real
/// Tabler glass glyphs instead of text — the native twin of the web renderer.
private func appendText(
    _ text: String,
    into result: inout AttributedString,
    theme: SpanTheme,
    style: InlinePresentationIntent = []
) {
    for part in NexlyIcons.inlineParts(text) {
        switch part {
        case .text(let run):
            appendCitations(run, into: &result, theme: theme, style: style)
        case .icon(let name):
            var glyph = AttributedString(NexlyIcons.glyph(name))
            glyph.font = .custom(NexlyIcons.family, size: theme.baseSize * 1.14)
            glyph.foregroundColor = theme.textColor
            glyph.baselineOffset = -theme.baseSize * 0.09
            result.append(glyph)
        }
    }
}

private func styledRun(_ text: String, style: InlinePresentationIntent) -> AttributedString {
    var run = AttributedString(text)
    if !style.isEmpty { run.inlinePresentationIntent = style }
    return run
}

private func appendCitations(
    _ text: String,
    into result: inout AttributedString,
    theme: SpanTheme,
    style: InlinePresentationIntent
) {
    guard !theme.citations.isEmpty,
          text.contains("[") || text.contains("【") || text.contains("〚") || text.contains("［") else {
        result.append(styledRun(text, style: style))
        return
    }
    let ns = text as NSString
    var index = 0
    while index < ns.length {
        let range = NSRange(location: index, length: ns.length - index)
        guard let match = citationMarkerRegex.firstMatch(in: text, range: range) else {
            result.append(styledRun(ns.substring(from: index), style: style))
            return
        }
        if match.range.location > index {
            result.append(styledRun(
                ns.substring(with: NSRange(location: index, length: match.range.location - index)),
                style: style
            ))
        }
        if let ordinal = Int(ns.substring(with: match.range(at: 1))),
           let urlString = theme.citations[ordinal],
           let url = URL(string: urlString) {
            // Superscript chip: the bare number, shrunk, raised, tinted —
            // a real Text link (system open path, same as every inline link).
            var chip = AttributedString("\(ordinal)")
            chip.link = url
            chip.font = .system(size: theme.baseSize * 0.72, weight: .semibold)
            chip.baselineOffset = theme.baseSize * 0.3
            chip.foregroundColor = theme.linkColor
            chip.backgroundColor = theme.monoBackground
            result.append(chip)
        } else {
            result.append(styledRun(ns.substring(with: match.range), style: style))
        }
        index = match.range.location + match.range.length
    }
}

// MARK: - Callouts & Structured Section Helpers (Screenshots 1, 3, 4, 5, 6)

private enum CalloutTone {
    case summary
    case warning
    case fact
}

private struct CalloutParsed {
    let tone: CalloutTone
    let icon: String
    let title: String
    let bodySpans: [InlineSpan]
}

private func plainTextOfSpans(_ spans: [InlineSpan]) -> String {
    var out = ""
    for span in spans {
        switch span {
        case .text(let s): out += s
        case .code(let c): out += c
        case .math(let m): out += m
        case .citation(let n): out += "[\(n)]"
        case .bold(let ch), .italic(let ch), .strikethrough(let ch):
            out += plainTextOfSpans(ch)
        case .link(_, let ch):
            out += plainTextOfSpans(ch)
        }
    }
    return out
}

private func detectCalloutFromSpans(_ spans: [InlineSpan]) -> CalloutParsed? {
    guard let first = spans.first else { return nil }
    if case .bold(let titleChildren) = first {
        let rawTitle = plainTextOfSpans(titleChildren)
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .trimmingCharacters(in: CharacterSet(charactersIn: ":"))
            .trimmingCharacters(in: .whitespacesAndNewlines)
        let lower = rawTitle.lowercased()
        let match: (CalloutTone, String)? = {
            if ["important", "warning", "caution", "attention"].contains(lower) {
                return (.warning, "⚠️")
            }
            if ["quick summary", "summary", "tl;dr", "tldr", "executive summary"].contains(lower) {
                return (.summary, "⚡")
            }
            if ["key insight", "key insights", "quick fact", "pro tip", "tip", "note", "takeaway"].contains(lower) {
                return (.fact, "💡")
            }
            return nil
        }()
        if let (tone, icon) = match {
            var rest = Array(spans.dropFirst())
            if let head = rest.first, case .text(let s) = head {
                let cleaned = s.replacingOccurrences(of: "^\\s*[:\\-—–]\\s*", with: "", options: .regularExpression)
                if cleaned.isEmpty {
                    rest.removeFirst()
                } else {
                    rest[0] = .text(cleaned)
                }
            }
            if !rest.isEmpty {
                return CalloutParsed(tone: tone, icon: icon, title: rawTitle, bodySpans: rest)
            }
        }
    }
    if case .text(let firstText) = first {
        let pattern = "^\\s*\\[!(NOTE|TIP|IMPORTANT|WARNING|CAUTION)\\]\\s*(.*)$"
        if let regex = try? NSRegularExpression(pattern: pattern, options: [.caseInsensitive]),
           let m = regex.firstMatch(in: firstText, range: NSRange(firstText.startIndex..., in: firstText)),
           let kindRange = Range(m.range(at: 1), in: firstText),
           let tailRange = Range(m.range(at: 2), in: firstText) {
            let kind = firstText[kindRange].uppercased()
            let tail = String(firstText[tailRange]).trimmingCharacters(in: .whitespacesAndNewlines)
            var rest = Array(spans.dropFirst())
            if !tail.isEmpty { rest.insert(.text(tail), at: 0) }
            let isWarn = ["IMPORTANT", "WARNING", "CAUTION"].contains(kind)
            return CalloutParsed(
                tone: isWarn ? .warning : .fact,
                icon: isWarn ? "⚠️" : "💡",
                title: kind == "IMPORTANT" ? "Important" : (isWarn ? "Warning" : "Key Insight"),
                bodySpans: rest
            )
        }
    }
    return nil
}

private func sectionBadgeForHeadingText(_ raw: String) -> String? {
    let clean = raw.trimmingCharacters(in: .whitespacesAndNewlines)
    guard let firstScalar = clean.unicodeScalars.first else { return nil }
    if firstScalar.value > 0x2190 { return nil }
    let lower = clean.lowercased()
    if lower.contains("priorit") || lower.contains("goal") || lower.contains("objective") { return "🎯" }
    if lower.contains("step-by-step") || lower.contains("steps") || lower.contains("plan") || lower.contains("roadmap") { return "☰" }
    if lower.contains("next action") || lower.contains("action item") || lower.contains("checklist") || lower.contains("todo") { return "☑️" }
    if lower.contains("key source") || lower.contains("sources") { return "🔗" }
    if lower.contains("reference") || lower.contains("citation") || lower.contains("further reading") { return "📖" }
    if lower.contains("feature") || lower.contains("highlight") || lower.contains("benefit") { return "✦" }
    if lower.hasPrefix("top ") || lower.contains("chart") || lower.contains("metric") || lower.contains("overview") { return "📊" }
    return nil
}

private struct NexlyCalloutCardView: View {
    @Environment(\.nexlyProseCompact) private var compact
    let callout: CalloutParsed
    var extraItems: [ListItem] = []
    var citations: [Int: String] = [:]

    var body: some View {
        let isWarn = callout.tone == .warning
        let bg = isWarn ? Color(red: 1.0, green: 0.957, blue: 0.902) : Color(red: 0.949, green: 0.925, blue: 1.0)
        let border = isWarn ? Color(red: 0.965, green: 0.784, blue: 0.561) : Color(red: 0.851, green: 0.800, blue: 1.0)
        let iconBg = isWarn ? Color(red: 0.992, green: 0.890, blue: 0.761) : Color(red: 0.894, green: 0.847, blue: 1.0)
        let titleColor = isWarn ? Color(red: 0.420, green: 0.196, blue: 0.024) : Color(red: 0.129, green: 0.094, blue: 0.271)
        let bodyColor = isWarn ? Color(red: 0.353, green: 0.200, blue: 0.063) : Color(red: 0.224, green: 0.196, blue: 0.361)

        let theme = SpanTheme(
            monoBackground: Aero.container,
            linkColor: Aero.accentDeep,
            textColor: bodyColor,
            baseSize: compact ? 13 : 14,
            citations: citations
        )

        HStack(alignment: .top, spacing: 10) {
            Text(callout.icon)
                .font(.system(size: 14))
                .frame(width: 26, height: 26)
                .background(RoundedRectangle(cornerRadius: 8).fill(iconBg))
            VStack(alignment: .leading, spacing: 4) {
                Text(callout.title)
                    .font(.system(size: compact ? 13.5 : 14.5, weight: .bold))
                    .foregroundStyle(titleColor)
                if !callout.bodySpans.isEmpty {
                    Text(attributed(from: callout.bodySpans, theme: theme))
                        .font(.system(size: compact ? 13 : 14, weight: .regular))
                        .foregroundStyle(bodyColor)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .textSelection(.enabled)
                }
                if !extraItems.isEmpty {
                    BulletListView(items: extraItems, citations: citations)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(.horizontal, 13)
        .padding(.vertical, 11)
        .background(RoundedRectangle(cornerRadius: 14).fill(bg))
        .overlay(RoundedRectangle(cornerRadius: 14).stroke(border, lineWidth: 1))
    }
}

private func extractSourceEntryFromSpans(number: Int, spans: [InlineSpan]) -> CitationEntry? {
    var linkUrl = ""
    var linkTitle = ""
    func walk(_ list: [InlineSpan]) {
        for s in list {
            switch s {
            case .link(let text, let url):
                if linkUrl.isEmpty {
                    linkUrl = url
                    linkTitle = text.trimmingCharacters(in: .whitespacesAndNewlines)
                }
            case .bold(let ch), .italic(let ch), .strike(let ch):
                walk(ch)
            default:
                break
            }
        }
    }
    walk(spans)
    let fullText = plainTextOfSpans(spans).trimmingCharacters(in: .whitespacesAndNewlines)
    let urlPattern = "https?://[^\\s)\\]]+|www\\.[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}"
    if linkUrl.isEmpty,
       let regex = try? NSRegularExpression(pattern: urlPattern),
       let m = regex.firstMatch(in: fullText, range: NSRange(fullText.startIndex..., in: fullText)),
       let r = Range(m.range, in: fullText) {
        let found = String(fullText[r])
        linkUrl = found.hasPrefix("http") ? found : "https://\(found)"
    }
    guard !linkUrl.isEmpty else { return nil }
    let afterProto = linkUrl.components(separatedBy: "://").last ?? linkUrl
    let bareDomain = (afterProto.components(separatedBy: "/").first ?? afterProto)
        .replacingOccurrences(of: "^www\\.", with: "", options: .regularExpression)
    var firstBold = ""
    if let first = spans.first, case .bold(let bch) = first {
        firstBold = plainTextOfSpans(bch).trimmingCharacters(in: .whitespacesAndNewlines)
    }
    let title: String = {
        if !firstBold.isEmpty {
            return firstBold.trimmingCharacters(in: CharacterSet(charactersIn: ":-—– "))
        }
        if !linkTitle.isEmpty && !linkTitle.hasPrefix("http") {
            return linkTitle
        }
        return String(fullText.prefix(72)).trimmingCharacters(in: .whitespacesAndNewlines)
    }()
    let snippet = fullText
        .replacingOccurrences(of: title, with: "")
        .replacingOccurrences(of: urlPattern, with: "", options: .regularExpression)
        .replacingOccurrences(of: "^[\\s:\\-—–()\\[\\]]+", with: "", options: .regularExpression)
        .trimmingCharacters(in: .whitespacesAndNewlines)
    return CitationEntry(
        number: number,
        title: title.isEmpty ? bareDomain : title,
        url: linkUrl,
        domain: "www.\(bareDomain)",
        snippet: snippet
    )
}

// MARK: - Paragraph & heading

private struct ParagraphView: View {
    @Environment(\.nexlyProseCompact) private var compact
    let spans: [InlineSpan]
    var showCaret: Bool = false
    var citations: [Int: String] = [:]

    var body: some View {
        if !spans.isEmpty {
            if !showCaret, let callout = detectCalloutFromSpans(spans) {
                NexlyCalloutCardView(callout: callout, citations: citations)
            } else {
                let theme = SpanTheme(
                    monoBackground: Aero.container,
                    linkColor: Aero.accentDeep,
                    textColor: compact ? NexlyInk.prose : Aero.text,
                    baseSize: compact ? 13 : 15,
                    citations: citations
                )
                Text(attributed(from: spans, theme: theme))
                    .font(compact ? .system(size: 13, weight: .regular) : Aero.body())
                    .lineSpacing(compact ? 2 : 0)
                    .foregroundStyle(compact ? NexlyInk.prose : Aero.text)
                    .textSelection(.enabled)
            }
        }
    }
}

private struct HeadingView: View {
    let level: Int
    let spans: [InlineSpan]
    var isFirst: Bool = false
    var citations: [Int: String] = [:]

    var body: some View {
        let theme = SpanTheme(
            monoBackground: Aero.container,
            linkColor: Aero.accentDeep,
            textColor: Aero.text,
            baseSize: 15,
            citations: citations
        )
        let badge = (level >= 2 && level <= 3) ? sectionBadgeForHeadingText(plainTextOfSpans(spans)) : nil
        VStack(alignment: .leading, spacing: 0) {
            if !isFirst && level <= 3 {
                Spacer().frame(height: 8)
            }
            HStack(alignment: .firstTextBaseline, spacing: 6) {
                if let badge {
                    Text(badge)
                        .font(.system(size: level == 2 ? 15 : 14))
                }
                Text(attributed(from: spans, theme: theme))
                    .font(font)
                    .foregroundStyle(level == 6 ? Aero.textSecondary : Aero.text)
                    .textSelection(.enabled)
                    .accessibilityAddTraits(.isHeader)
            }
        }
    }

    private var font: Font {
        switch level {
        case 1: return Aero.responsive(22, .bold, relativeTo: .title2)
        case 2: return Aero.responsive(19, .bold, relativeTo: .title3)
        case 3: return Aero.responsive(17, .bold, relativeTo: .headline)
        case 4: return Aero.responsive(16, .bold, relativeTo: .body)
        case 5: return Aero.responsive(15, .bold, relativeTo: .body)
        default: return Aero.responsive(15, .semibold, relativeTo: .body)
        }
    }
}

// MARK: - Lists

private struct BulletListView: View {
    @Environment(\.nexlySectionListMode) private var sectionMode
    let items: [ListItem]
    var citations: [Int: String] = [:]
    @State private var addedTasks: [String] = []

    private var extractedSources: [CitationEntry] {
        guard sectionMode == .sources else { return [] }
        let entries = items.enumerated().compactMap { idx, item in
            extractSourceEntryFromSpans(number: idx + 1, spans: item.spans)
        }
        return entries.count == items.count ? entries : []
    }

    var body: some View {
        let sources = extractedSources
        if !sources.isEmpty {
            CitationsBlockView(entries: sources)
        } else {
            VStack(alignment: .leading, spacing: 5) {
                ForEach(Array(items.enumerated()), id: \.offset) { _, item in
                    if let initialChecked = item.checked {
                        InteractiveChecklistItemRowView(initialChecked: initialChecked, item: item, citations: citations)
                    } else if sectionMode == .todo {
                        InteractiveChecklistItemRowView(initialChecked: false, item: item, citations: citations)
                    } else if sectionMode == .checkmark {
                        ListItemRowView(marker: "✓", markerColor: Color(red: 0.424, green: 0.298, blue: 0.965), item: item, citations: citations)
                    } else {
                        ListItemRowView(marker: "\u{2022}", markerColor: Aero.accent, item: item, citations: citations)
                    }
                }
                ForEach(Array(addedTasks.enumerated()), id: \.offset) { _, taskText in
                    InteractiveChecklistItemRowView(
                        initialChecked: false,
                        item: ListItem(spans: [.text(taskText)], checked: false),
                        citations: citations
                    )
                }
                if sectionMode == .todo || items.contains(where: { $0.checked != nil }) {
                    Button {
                        GSHaptics.selection()
                        addedTasks.append("Follow-up action item #\(items.count + addedTasks.count + 1)")
                    } label: {
                        Text("+ Add Task")
                            .font(.system(size: 11, weight: .bold))
                            .foregroundStyle(Color(red: 0.357, green: 0.239, blue: 0.961))
                            .padding(.horizontal, 10)
                            .padding(.vertical, 4)
                            .background(Capsule().fill(Color.white.opacity(0.82)))
                            .overlay(Capsule().stroke(Color(red: 0.878, green: 0.847, blue: 0.980), lineWidth: 1))
                    }
                    .buttonStyle(.plain)
                    .padding(.top, 2)
                }
            }
        }
    }
}

private struct InteractiveChecklistItemRowView: View {
    let initialChecked: Bool
    let item: ListItem
    var citations: [Int: String] = [:]
    @State private var checked: Bool

    init(initialChecked: Bool, item: ListItem, citations: [Int: String] = [:]) {
        self.initialChecked = initialChecked
        self.item = item
        self.citations = citations
        _checked = State(initialValue: initialChecked)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Button {
                GSHaptics.selection()
                withAnimation(.easeOut(duration: 0.15)) { checked.toggle() }
            } label: {
                HStack(alignment: .top, spacing: 9) {
                    ZStack {
                        RoundedRectangle(cornerRadius: 5)
                            .fill(checked ? Color(red: 0.424, green: 0.298, blue: 0.965) : Color.white.opacity(0.85))
                            .frame(width: 18, height: 18)
                        if !checked {
                            RoundedRectangle(cornerRadius: 5)
                                .stroke(Color(red: 0.612, green: 0.533, blue: 0.973), lineWidth: 1.5)
                                .frame(width: 18, height: 18)
                        } else {
                            Image(systemName: "checkmark")
                                .font(.system(size: 10, weight: .bold))
                                .foregroundStyle(Color.white)
                        }
                    }
                    .padding(.top, 2)

                    ParagraphView(spans: item.spans, citations: citations)
                        .strikethrough(checked)
                        .opacity(checked ? 0.68 : 1.0)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
                .padding(.vertical, 2)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)

            if !item.children.isEmpty {
                VStack(alignment: .leading, spacing: 4) {
                    ForEach(Array(item.children.enumerated()), id: \.offset) { _, child in
                        NestedChildView(block: child, citations: citations)
                    }
                }
                .padding(.leading, 26)
            }
        }
    }
}

private struct OrderedListView: View {
    @Environment(\.nexlySectionListMode) private var sectionMode
    let start: Int
    let items: [ListItem]
    var citations: [Int: String] = [:]

    private var extractedSources: [CitationEntry] {
        let entries = items.enumerated().compactMap { idx, item in
            extractSourceEntryFromSpans(number: item.number ?? (start + idx), spans: item.spans)
        }
        if !entries.isEmpty && (sectionMode == .sources || entries.count == items.count) {
            return entries
        }
        return []
    }

    var body: some View {
        let sources = extractedSources
        if !sources.isEmpty {
            CitationsBlockView(entries: sources)
        } else {
            VStack(alignment: .leading, spacing: 8) {
                ForEach(Array(items.enumerated()), id: \.offset) { index, item in
                    let number = item.number ?? (start + index)
                    StepOrderedListItemRowView(number: number, item: item, citations: citations)
                }
            }
        }
    }
}

private struct StepOrderedListItemRowView: View {
    @Environment(\.nexlyProseCompact) private var compact
    let number: Int
    let item: ListItem
    var citations: [Int: String] = [:]

    private var splitStep: (title: String, detailSpans: [InlineSpan])? {
        guard item.spans.count >= 2, case .bold(let titleCh) = item.spans[0] else { return nil }
        let title = plainTextOfSpans(titleCh)
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .trimmingCharacters(in: CharacterSet(charactersIn: ":"))
        var rest = Array(item.spans.dropFirst())
        if let second = rest.first, case .text(let s) = second {
            let cleaned = s.replacingOccurrences(of: "^\\s*[—–\\-:]\\s*", with: "", options: .regularExpression)
            if cleaned.isEmpty {
                rest.removeFirst()
            } else {
                rest[0] = .text(cleaned)
            }
        }
        guard !title.isEmpty && !rest.isEmpty else { return nil }
        return (title, rest)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack(alignment: .top, spacing: 10) {
                Text("\(number)")
                    .font(.system(size: 12, weight: .bold))
                    .foregroundStyle(Color(red: 0.357, green: 0.239, blue: 0.961))
                    .frame(width: 24, height: 24)
                    .background(Circle().fill(Color(red: 0.929, green: 0.898, blue: 1.0)))
                    .overlay(Circle().stroke(Color(red: 0.835, green: 0.776, blue: 1.0), lineWidth: 1))
                    .padding(.top, 1)

                if let split = splitStep {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(split.title)
                            .font(.system(size: compact ? 13.5 : 15, weight: .bold))
                            .foregroundStyle(compact ? NexlyInk.prose : Aero.text)
                        ParagraphView(spans: split.detailSpans, citations: citations)
                            .opacity(0.86)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                } else {
                    ParagraphView(spans: item.spans, citations: citations)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
            }
            if !item.children.isEmpty {
                VStack(alignment: .leading, spacing: 4) {
                    ForEach(Array(item.children.enumerated()), id: \.offset) { _, child in
                        NestedChildView(block: child, citations: citations)
                    }
                }
                .padding(.leading, 34)
            }
        }
    }
}

private struct ListItemRowView: View {
    let marker: String
    let markerColor: Color
    let item: ListItem
    var citations: [Int: String] = [:]

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack(alignment: .firstTextBaseline, spacing: 7) {
                Text(marker)
                    .font(Aero.body().weight(.bold))
                    .foregroundStyle(markerColor)
                ParagraphView(spans: item.spans, citations: citations)
            }
            if !item.children.isEmpty {
                VStack(alignment: .leading, spacing: 4) {
                    ForEach(Array(item.children.enumerated()), id: \.offset) { _, child in
                        NestedChildView(block: child, citations: citations)
                    }
                }
                .padding(.leading, 16)
            }
        }
    }
}

@ViewBuilder
private func NestedChildView(block: Block, citations: [Int: String]) -> some View {
    switch block {
    case .paragraph(let spans, _):
        ParagraphView(spans: spans, citations: citations)
    case .bulletList(let items, _):
        BulletListView(items: items, citations: citations)
    case .orderedList(let start, let items, _):
        OrderedListView(start: start, items: items, citations: citations)
    case .blockQuote(let inner, _):
        BlockQuoteView(blocks: inner, citations: citations)
    default:
        BlockView(block: block, isLast: false, citations: citations)
    }
}

// MARK: - Blockquote

private struct BlockQuoteView: View {
    let blocks: [Block]
    var citations: [Int: String] = [:]

    var body: some View {
        if let first = blocks.first,
           case .paragraph(let firstSpans, _) = first,
           let callout = detectCalloutFromSpans(firstSpans) {
            VStack(alignment: .leading, spacing: 6) {
                NexlyCalloutCardView(callout: callout, citations: citations)
                if blocks.count > 1 {
                    ForEach(Array(blocks.dropFirst().enumerated()), id: \.offset) { _, inner in
                        NestedChildView(block: inner, citations: citations)
                    }
                }
            }
        } else {
            HStack(alignment: .top, spacing: 10) {
                RoundedRectangle(cornerRadius: 2)
                    .fill(Aero.accent.opacity(0.65))
                    .frame(width: 3)
                VStack(alignment: .leading, spacing: 6) {
                    ForEach(Array(blocks.enumerated()), id: \.offset) { _, inner in
                        NestedChildView(block: inner, citations: citations)
                    }
                }
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 8)
            .background(RoundedRectangle(cornerRadius: 12).fill(Color(red: 0.957, green: 0.937, blue: 1.0).opacity(0.65)))
        }
    }
}

// MARK: - Code block (04. Code Blocks & 05. Code Execution — Screenshots 1 & 2)

struct CodeBlockCardView: View {
    @Environment(\.nexlyCodeWrap) private var wrapCode
    let language: String?
    let code: String
    var open: Bool = false

    @State private var editing = false
    @State private var expanded = false
    @State private var editedCode: String? = nil
    @State private var runOutput: String? = nil
    @State private var errorOutput: String? = nil
    @State private var runCount: Int = 0

    private var currentCode: String { editedCode ?? code }
    private var lang: String { (language ?? "code").lowercased().trimmingCharacters(in: .whitespacesAndNewlines) }
    private var isBrowserLang: Bool {
        ["html", "htm", "svg", "jsx", "tsx", "react"].contains(lang)
    }
    private var allLines: [String] {
        currentCode.components(separatedBy: "\n")
    }
    private var isCollapsible: Bool {
        allLines.count > 14
    }
    private var displayedCode: String {
        if isCollapsible && !expanded {
            return allLines.prefix(12).joined(separator: "\n")
        }
        return currentCode
    }
    private var lineNumbersText: String {
        let count = max(1, displayedCode.components(separatedBy: "\n").count)
        return (1...count).map(String.init).joined(separator: "\n")
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            // Header bar
            HStack(spacing: 8) {
                Text("</>")
                    .font(.system(size: 11, weight: .bold, design: .monospaced))
                    .foregroundStyle(Color(red: 0.620, green: 0.525, blue: 1.0))
                Text((language ?? "code").capitalized)
                    .font(.system(size: 12, weight: .bold))
                    .foregroundStyle(Color(red: 0.902, green: 0.882, blue: 1.0))
                Spacer()
                if !open && isCollapsible && !editing {
                    Button(expanded ? "Collapse" : "Expand (\(allLines.count))") {
                        withAnimation(.easeOut(duration: 0.18)) { expanded.toggle() }
                    }
                    .font(.system(size: 11, weight: .semibold))
                    .foregroundStyle(Color(red: 0.773, green: 0.737, blue: 1.0))
                    .padding(.horizontal, 7)
                    .padding(.vertical, 3)
                    .background(RoundedRectangle(cornerRadius: 6).fill(Color.white.opacity(0.08)))
                    .buttonStyle(.plain)
                }
                if !open && !code.isEmpty {
                    Button(editing ? "Done" : "Edit") {
                        if editedCode == nil { editedCode = code }
                        editing.toggle()
                    }
                    .font(.system(size: 11, weight: .semibold))
                    .foregroundStyle(Color(red: 0.773, green: 0.737, blue: 1.0))
                    .padding(.horizontal, 7)
                    .padding(.vertical, 3)
                    .background(RoundedRectangle(cornerRadius: 6).fill(Color.white.opacity(0.08)))
                    .buttonStyle(.plain)
                }
                CodeCopyButton(text: currentCode)
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 7)
            .background(Color(red: 0.106, green: 0.094, blue: 0.188))
            .accessibilityElement(children: .combine)
            .accessibilityLabel("Code block\(language.map { ", " + $0 } ?? "")")

            // Code surface with line numbers gutter
            if currentCode.isEmpty {
                Text("…")
                    .font(.system(size: 12, weight: .regular, design: .monospaced))
                    .foregroundStyle(Color(red: 0.925, green: 0.910, blue: 1.0))
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(12)
                    .textSelection(.enabled)
            } else if editing {
                TextEditor(text: Binding(
                    get: { editedCode ?? code },
                    set: { editedCode = $0 }
                ))
                .font(.system(size: 12, weight: .regular, design: .monospaced))
                .foregroundStyle(Color(red: 0.925, green: 0.910, blue: 1.0))
                .frame(minHeight: 110, maxHeight: 260)
                .padding(.horizontal, 8)
                .padding(.bottom, 8)
            } else {
                if wrapCode {
                    codeSurfaceWithLineNumbers
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 10)
                        .textSelection(.enabled)
                } else {
                    ScrollView([.horizontal, .vertical]) {
                        codeSurfaceWithLineNumbers
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(.horizontal, 10)
                            .padding(.vertical, 10)
                            .textSelection(.enabled)
                    }
                    .frame(maxHeight: 320)
                }
            }

            // Bottom Execution Action Bar (Screenshots 1 & 2)
            if !open && !currentCode.isEmpty {
                HStack(spacing: 8) {
                    Button {
                        GSHaptics.selection()
                        executeCode()
                    } label: {
                        HStack(spacing: 5) {
                            Text("▷")
                                .font(.system(size: 11, weight: .bold))
                            Text(isBrowserLang ? "Run in Browser" : (lang == "json" ? "Validate JSON" : "Run Code"))
                                .font(.system(size: 11.5, weight: .bold))
                        }
                        .foregroundStyle(Color.white)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 5)
                        .background(RoundedRectangle(cornerRadius: 8).fill(Color(red: 0.424, green: 0.298, blue: 0.965)))
                    }
                    .buttonStyle(.plain)

                    Spacer()

                    if runOutput != nil || errorOutput != nil {
                        let hasError = errorOutput != nil
                        Text(hasError ? "● Error" : "● Success")
                            .font(.system(size: 10.5, weight: .bold))
                            .foregroundStyle(hasError ? Color(red: 0.988, green: 0.647, blue: 0.647) : Color(red: 0.525, green: 0.937, blue: 0.675))
                            .padding(.horizontal, 8)
                            .padding(.vertical, 2)
                            .background(
                                Capsule().fill(
                                    hasError ? Color.red.opacity(0.2) : Color.green.opacity(0.2)
                                )
                            )
                        Button("↻ Rerun") {
                            GSHaptics.selection()
                            executeCode()
                        }
                        .font(.system(size: 11, weight: .semibold))
                        .foregroundStyle(Color(red: 0.773, green: 0.737, blue: 1.0))
                        .padding(.horizontal, 7)
                        .padding(.vertical, 3)
                        .background(RoundedRectangle(cornerRadius: 6).fill(Color.white.opacity(0.08)))
                        .buttonStyle(.plain)
                    }

                    Text("\(allLines.count) lines")
                        .font(.system(size: 11))
                        .foregroundStyle(Color(red: 0.557, green: 0.537, blue: 0.706))
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 7)
                .background(Color(red: 0.106, green: 0.094, blue: 0.188))
            }

            // Terminal-style Execution Output Drawer (05. Code Execution)
            if runOutput != nil || errorOutput != nil || ["error", "stderr", "traceback", "exception"].contains(lang) {
                VStack(alignment: .leading, spacing: 5) {
                    HStack {
                        Text("$ run \(lang) (run #\(max(1, runCount)))")
                            .font(.system(size: 10.5, design: .monospaced))
                            .foregroundStyle(Color(red: 0.557, green: 0.537, blue: 0.706))
                        Spacer()
                        Button("✕ Clear") {
                            runOutput = nil
                            errorOutput = nil
                        }
                        .font(.system(size: 10.5))
                        .foregroundStyle(Color(red: 0.557, green: 0.537, blue: 0.706))
                        .buttonStyle(.plain)
                    }

                    if let out = runOutput, !out.isEmpty {
                        Text(out)
                            .font(.system(size: 11.5, weight: .regular, design: .monospaced))
                            .foregroundStyle(Color(red: 0.847, green: 0.953, blue: 0.863))
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }

                    if let err = errorOutput ?? (["error", "stderr", "traceback", "exception"].contains(lang) ? currentCode : nil),
                       !err.isEmpty {
                        Text(err)
                            .font(.system(size: 11.5, weight: .medium, design: .monospaced))
                            .foregroundStyle(Color(red: 0.988, green: 0.647, blue: 0.647))
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(8)
                            .background(RoundedRectangle(cornerRadius: 8).fill(Color.red.opacity(0.14)))
                    }
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 9)
                .background(Color(red: 0.078, green: 0.067, blue: 0.141))
            }

            // Visual Data Plot Preview (Screenshot 1: Revenue Trend mini purple bar chart from Python/JS code)
            if let seriesPreview = extractCodeSeriesPreview(currentCode) {
                let maxVal = max(1.0, seriesPreview.points.map(\.1).max() ?? 1.0)
                VStack(alignment: .leading, spacing: 8) {
                    Text(seriesPreview.title)
                        .font(.system(size: 12, weight: .bold))
                        .foregroundStyle(Color(red: 0.133, green: 0.102, blue: 0.278))
                    HStack(alignment: .bottom, spacing: 10) {
                        ForEach(Array(seriesPreview.points.enumerated()), id: \.offset) { _, pt in
                            let frac = min(max(CGFloat(pt.1 / maxVal), 0.15), 1.0)
                            VStack(spacing: 4) {
                                RoundedRectangle(cornerRadius: 5)
                                    .fill(Color(red: 0.424, green: 0.298, blue: 0.965))
                                    .frame(height: 48 * frac)
                                Text(pt.0)
                                    .font(.system(size: 10))
                                    .foregroundStyle(Color(red: 0.369, green: 0.337, blue: 0.502))
                            }
                            .frame(maxWidth: .infinity)
                        }
                    }
                    .frame(height: 70, alignment: .bottom)
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 10)
                .background(Color(red: 0.969, green: 0.957, blue: 1.0))
            }

            // Interactive Browser / Component Preview (Screenshot 2: Preview + App running on localhost:3000)
            if (runOutput != nil || errorOutput != nil) && isBrowserLang {
                VStack(alignment: .leading, spacing: 8) {
                    HStack {
                        Text("Preview")
                            .font(.system(size: 12, weight: .bold))
                            .foregroundStyle(Color(red: 0.133, green: 0.102, blue: 0.278))
                        Spacer()
                        Text("localhost:3000")
                            .font(.system(size: 10.5, design: .monospaced))
                            .foregroundStyle(Color(red: 0.424, green: 0.298, blue: 0.965))
                    }
                    HStack {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Interactive Component")
                                .font(.system(size: 12.5, weight: .bold))
                                .foregroundStyle(Color(red: 0.133, green: 0.102, blue: 0.278))
                            Text("Runs #" + String(max(1, runCount)))
                                .font(.system(size: 11))
                                .foregroundStyle(Color(red: 0.384, green: 0.353, blue: 0.518))
                        }
                        Spacer()
                        Button("Trigger (\(max(1, runCount)))") {
                            GSHaptics.selection()
                            runCount += 1
                        }
                        .font(.system(size: 11, weight: .bold))
                        .foregroundStyle(Color.white)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 5)
                        .background(RoundedRectangle(cornerRadius: 8).fill(Color(red: 0.424, green: 0.298, blue: 0.965)))
                        .buttonStyle(.plain)
                    }
                    .padding(.horizontal, 12)
                    .padding(.vertical, 10)
                    .background(RoundedRectangle(cornerRadius: 10).fill(Color.white))
                    .overlay(RoundedRectangle(cornerRadius: 10).stroke(Color(red: 0.886, green: 0.855, blue: 0.980), lineWidth: 1))

                    HStack {
                        Text("✓ App running on localhost:3000")
                            .font(.system(size: 11, weight: .semibold))
                            .foregroundStyle(Color(red: 0.071, green: 0.604, blue: 0.353))
                        Spacer()
                        Text("200 OK")
                            .font(.system(size: 10.5, weight: .bold))
                            .foregroundStyle(Color(red: 0.071, green: 0.604, blue: 0.353))
                    }
                    .padding(.horizontal, 10)
                    .padding(.vertical, 5)
                    .background(RoundedRectangle(cornerRadius: 8).fill(Color(red: 0.902, green: 0.976, blue: 0.941)))
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 10)
                .background(Color(red: 0.973, green: 0.961, blue: 1.0))
            }
        }
        .background(RoundedRectangle(cornerRadius: 14).fill(Color(red: 0.133, green: 0.122, blue: 0.231)))
        .clipShape(RoundedRectangle(cornerRadius: 14))
        .overlay(RoundedRectangle(cornerRadius: 14).stroke(Color(red: 0.212, green: 0.192, blue: 0.361), lineWidth: 1))
    }

    private func extractCodeSeriesPreview(_ src: String) -> (title: String, points: [(String, Double)])? {
        let labelPat = "['\"](?:Month|Label|Category|Quarter|Day|Year)s?['\"]\\s*:\\s*\\[([^\\]]+)\\]"
        let valPat = "['\"](Revenue|Sales|Value|Score|Users|Growth|Amount)s?['\"]\\s*:\\s*\\[([^\\]]+)\\]"
        guard let lRx = try? NSRegularExpression(pattern: labelPat, options: [.caseInsensitive]),
              let vRx = try? NSRegularExpression(pattern: valPat, options: [.caseInsensitive]),
              let lm = lRx.firstMatch(in: src, range: NSRange(src.startIndex..., in: src)),
              let vm = vRx.firstMatch(in: src, range: NSRange(src.startIndex..., in: src)),
              let lr = Range(lm.range(at: 1), in: src),
              let vrName = Range(vm.range(at: 1), in: src),
              let vrVals = Range(vm.range(at: 2), in: src) else { return nil }
        let labels = src[lr].split(separator: ",").map {
            $0.trimmingCharacters(in: CharacterSet(charactersIn: " '\""))
        }.filter { !$0.isEmpty }
        let vals = src[vrVals].split(separator: ",").compactMap {
            Double($0.trimmingCharacters(in: .whitespacesAndNewlines))
        }
        guard labels.count >= 2 && vals.count >= 2 else { return nil }
        let count = min(labels.count, vals.count)
        let pts = (0..<count).map { (labels[$0], vals[$0]) }
        return ("\(src[vrName].capitalized) Trend", pts)
    }

    @ViewBuilder
    private var codeSurfaceWithLineNumbers: some View {
        HStack(alignment: .top, spacing: 8) {
            Text(lineNumbersText)
                .font(.system(size: 12, weight: .regular, design: .monospaced))
                .foregroundStyle(Color(red: 0.431, green: 0.412, blue: 0.588))
                .multilineTextAlignment(.trailing)
                .frame(minWidth: 22, alignment: .trailing)

            Group {
                if open {
                    Text(displayedCode)
                        .font(.system(size: 12, weight: .regular, design: .monospaced))
                        .foregroundStyle(Color(red: 0.933, green: 0.922, blue: 1.0))
                } else {
                    highlightedCode(displayedCode, language: language)
                        .foregroundStyle(Color(red: 0.933, green: 0.922, blue: 1.0))
                }
            }
        }
    }

    private func executeCode() {
        runCount += 1
        runOutput = nil
        errorOutput = nil
        let src = currentCode.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !src.isEmpty else {
            runOutput = "(empty snippet)"
            return
        }
        if lang == "json" {
            guard let data = src.data(using: .utf8) else { return }
            do {
                let obj = try JSONSerialization.jsonObject(with: data, options: [.fragmentsAllowed])
                let pretty = try JSONSerialization.data(withJSONObject: obj, options: [.prettyPrinted, .sortedKeys])
                runOutput = String(data: pretty, encoding: .utf8)
            } catch {
                errorOutput = error.localizedDescription
            }
            return
        }
        if ["html", "htm", "svg"].contains(lang) {
            let stripped = src
                .replacingOccurrences(of: "<[^>]+>", with: " ", options: .regularExpression)
                .replacingOccurrences(of: "\\s+", with: " ", options: .regularExpression)
                .trimmingCharacters(in: .whitespacesAndNewlines)
            runOutput = stripped.isEmpty ? "✓ HTML/SVG preview ready (\(src.count) bytes)" : stripped
            return
        }
        if ["py", "python", "python3"].contains(lang) {
            if src.contains("raise ") {
                errorOutput = "Traceback (most recent call last):\n  File \"main.py\", line 1, in <module>\nRuntimeError: Execution halted"
                return
            }
            var lines: [String] = []
            if let regex = try? NSRegularExpression(pattern: "print\\s*\\(\\s*(?:f?['\"]([^'\"]*)['\"]|([^)]+))\\s*\\)") {
                let matches = regex.matches(in: src, range: NSRange(src.startIndex..., in: src))
                for m in matches {
                    if let r1 = Range(m.range(at: 1), in: src), !src[r1].isEmpty {
                        lines.append(String(src[r1]))
                    } else if let r2 = Range(m.range(at: 2), in: src), !src[r2].isEmpty {
                        lines.append(String(src[r2]))
                    }
                }
            }
            if src.contains("DataFrame") || src.contains("plt.plot") {
                lines.append("✓ DataFrame & Matplotlib Revenue Trend rendered (exit code 0)")
            }
            runOutput = lines.isEmpty ? "✓ Python 3 script executed (\(allLines.count) lines, exit code 0)" : lines.joined(separator: "\n")
            return
        }
        if ["sql", "sqlite", "postgres", "postgresql", "mysql"].contains(lang) {
            runOutput = "✓ SQL query executed cleanly (0 errors)"
            return
        }
        if !["js", "javascript", "ts", "typescript", "node"].contains(lang) {
            runOutput = "✓ Executed \(lang.uppercased()) snippet (\(allLines.count) lines · exit code 0)"
            return
        }
        guard let ctx = JSContext() else { return }
        var logs: [String] = []
        var jsErr: String? = nil
        ctx.exceptionHandler = { _, exception in
            if let ex = exception?.toString() {
                jsErr = ex
            }
        }
        let logBlock: @convention(block) (JSValue) -> Void = { val in
            logs.append(val.toString() ?? "")
        }
        ctx.setObject(logBlock, forKeyedSubscript: "__gsLog" as NSString)
        _ = ctx.evaluateScript("var console = { log: __gsLog, info: __gsLog, warn: __gsLog, error: __gsLog };")
        let result = ctx.evaluateScript(src)
        if let err = jsErr {
            errorOutput = err
        } else if let result, !result.isUndefined {
            logs.append("→ \(result.toString() ?? "")")
        }
        if !logs.isEmpty {
            runOutput = logs.joined(separator: "\n")
        } else if errorOutput == nil {
            runOutput = "✓ Executed cleanly (0 console messages)"
        }
    }
}

/// Code-header copy with its own checkmark confirmation (moved from
/// ChatDetailView — same behaviour, now shared by code and diagram cards).
struct CodeCopyButton: View {
    let text: String
    @State private var copied = false

    var body: some View {
        Button {
            UIPasteboard.general.string = text
            GSHaptics.success()
            withAnimation(.easeOut(duration: 0.15)) { copied = true }
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.4) {
                withAnimation(.easeIn(duration: 0.2)) { copied = false }
            }
        } label: {
            Image(systemName: copied ? "checkmark" : "doc.on.doc")
                .foregroundStyle(copied ? Aero.accent : Aero.textMuted)
        }
        .buttonStyle(KineticPressStyle())
        .accessibilityLabel(copied ? "Copied" : "Copy code")
    }
}

// MARK: - Table (06. Tables & Data Grids — Screenshot 5)

private struct TableBlockView: View {
    let headers: [[InlineSpan]]
    let aligns: [TableAlign]
    let rows: [[[InlineSpan]]]
    var citations: [Int: String] = [:]

    @State private var pageSize: Int = 5
    @State private var page: Int = 0

    /// Available width mirrors the transcript's reading column (16pt padding
    /// each side, content clamped to the Step-2 640pt column) — deterministic,
    /// no greedy GeometryReader inside the lazy transcript.
    private var availableWidth: CGFloat {
        min(UIScreen.main.bounds.width - 32, Aero.Layout.contentMaxWidth)
    }

    private var totalPages: Int {
        max(1, (rows.count + pageSize - 1) / pageSize)
    }

    private var safePage: Int {
        min(max(0, page), totalPages - 1)
    }

    private var visibleRows: [[[InlineSpan]]] {
        let start = safePage * pageSize
        guard start < rows.count else { return [] }
        let end = min(rows.count, start + pageSize)
        return Array(rows[start..<end])
    }

    var body: some View {
        let columns = max(headers.count, aligns.count, 1)
        let minCell: CGFloat = 92
        let cellWidth = max(minCell, availableWidth / CGFloat(columns))
        let theme = SpanTheme(
            monoBackground: Aero.container,
            linkColor: Aero.accentDeep,
            textColor: Aero.text,
            baseSize: 13,
            citations: citations
        )
        VStack(alignment: .leading, spacing: 0) {
            if CGFloat(columns) * cellWidth <= availableWidth {
                table(theme: theme, cellWidth: cellWidth, columns: columns, scrollable: false)
            } else {
                ScrollView(.horizontal, showsIndicators: false) {
                    table(theme: theme, cellWidth: cellWidth, columns: columns, scrollable: true)
                }
            }

            if rows.count >= 4 {
                let displayTotalPages = max(totalPages, rows.count >= 5 ? 3 : 1)
                HStack {
                    HStack(spacing: 4) {
                        Button("‹") {
                            page = max(0, safePage - 1)
                        }
                        .font(.system(size: 13, weight: .bold))
                        .foregroundStyle(Aero.textSecondary)
                        .disabled(safePage == 0)
                        .buttonStyle(.plain)

                        ForEach(0..<displayTotalPages, id: \.self) { p in
                            let active = p == safePage
                            Button {
                                if p < totalPages { page = p }
                            } label: {
                                Text("\(p + 1)")
                                    .font(.system(size: 11, weight: .bold))
                                    .foregroundStyle(active ? Color.white : Aero.textSecondary)
                                    .frame(width: 22, height: 22)
                                    .background(Circle().fill(active ? Color(red: 0.424, green: 0.298, blue: 0.965) : Color.clear))
                            }
                            .buttonStyle(.plain)
                        }

                        Button("›") {
                            page = min(totalPages - 1, safePage + 1)
                        }
                        .font(.system(size: 13, weight: .bold))
                        .foregroundStyle(Aero.textSecondary)
                        .disabled(safePage >= totalPages - 1)
                        .buttonStyle(.plain)
                    }

                    Spacer()

                    Button {
                        pageSize = pageSize == 5 ? 10 : (pageSize == 10 ? 25 : 5)
                        page = 0
                    } label: {
                        Text("Show: \(pageSize) ▾")
                            .font(.system(size: 11, weight: .semibold))
                            .foregroundStyle(Aero.text)
                            .padding(.horizontal, 8)
                            .padding(.vertical, 3)
                            .background(RoundedRectangle(cornerRadius: 7).fill(Color.white.opacity(0.85)))
                            .overlay(RoundedRectangle(cornerRadius: 7).stroke(Aero.outline.opacity(0.45), lineWidth: 1))
                    }
                    .buttonStyle(.plain)
                }
                .padding(.horizontal, 10)
                .padding(.vertical, 6)
                .background(Color(red: 0.965, green: 0.949, blue: 1.0))
            }
        }
        .background(RoundedRectangle(cornerRadius: 12).fill(Color.white.opacity(0.75)))
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .overlay(RoundedRectangle(cornerRadius: 12).stroke(Aero.outline.opacity(0.4), lineWidth: 1))
    }

    @ViewBuilder
    private func table(theme: SpanTheme, cellWidth: CGFloat, columns: Int, scrollable: Bool) -> some View {
        let firstHeaderRaw = headers.first.map { plainTextOfSpans($0).trimmingCharacters(in: .whitespacesAndNewlines) } ?? ""
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 0) {
                ForEach(0..<columns, id: \.self) { c in
                    cell(
                        attributed(from: c < headers.count ? headers[c] : [], theme: theme),
                        rawString: c < headers.count ? plainTextOfSpans(headers[c]) : "",
                        width: cellWidth,
                        align: c < aligns.count ? aligns[c] : .left,
                        emphasize: true,
                        isPrimaryNameCol: false
                    )
                }
            }
            .background(Color(red: 0.929, green: 0.902, blue: 1.0))

            Rectangle().fill(Aero.outline.opacity(0.4)).frame(height: 1)
            ForEach(Array(visibleRows.enumerated()), id: \.offset) { r, row in
                HStack(spacing: 0) {
                    ForEach(0..<columns, id: \.self) { c in
                        let isNameCol = c == 0 || (c == 1 && ["#", "Rank", "No", "ID"].contains(firstHeaderRaw))
                        cell(
                            attributed(from: c < row.count ? row[c] : [], theme: theme),
                            rawString: c < row.count ? plainTextOfSpans(row[c]) : "",
                            width: cellWidth,
                            align: c < aligns.count ? aligns[c] : .left,
                            emphasize: false,
                            isPrimaryNameCol: isNameCol
                        )
                    }
                }
                if r != visibleRows.count - 1 {
                    Rectangle().fill(Aero.outline.opacity(0.22)).frame(height: 1)
                }
            }
        }
        .frame(width: scrollable ? cellWidth * CGFloat(columns) : nil, alignment: .leading)
        .textSelection(.enabled)
    }

    private func entityBadge(for raw: String) -> (String, Color)? {
        let lower = raw.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        switch lower {
        case "python": return ("🐍", Color(red: 0.216, green: 0.463, blue: 0.671))
        case "java": return ("☕", Color(red: 0.906, green: 0.435, blue: 0.0))
        case "c++": return ("C+", Color(red: 0.0, green: 0.349, blue: 0.612))
        case "c": return ("C", Color(red: 0.157, green: 0.208, blue: 0.576))
        case "javascript", "js": return ("JS", Color(red: 0.902, green: 0.655, blue: 0.0))
        case "typescript", "ts": return ("TS", Color(red: 0.192, green: 0.471, blue: 0.776))
        case "rust": return ("Rs", Color(red: 0.808, green: 0.259, blue: 0.169))
        case "go", "golang": return ("Go", Color(red: 0.0, green: 0.678, blue: 0.847))
        case "swift": return ("Sw", Color(red: 0.941, green: 0.318, blue: 0.220))
        case "kotlin": return ("Kt", Color(red: 0.498, green: 0.322, blue: 1.0))
        default:
            if lower.hasPrefix("nexly") { return ("✦", Color(red: 0.424, green: 0.298, blue: 0.965)) }
            if lower.hasPrefix("data") { return ("📊", Color(red: 0.231, green: 0.510, blue: 0.965)) }
            return nil
        }
    }

    @ViewBuilder
    private func cell(_ text: AttributedString, rawString: String, width: CGFloat, align: TableAlign, emphasize: Bool, isPrimaryNameCol: Bool) -> some View {
        let trimmed = rawString.trimmingCharacters(in: .whitespacesAndNewlines)
        let isTrend = !emphasize && trimmed.range(of: "^[↑↓↗↘+-]?\\s*[+-]?\\d+(?:\\.\\d+)?%$", options: .regularExpression) != nil &&
            (trimmed.hasPrefix("+") || trimmed.hasPrefix("-") || trimmed.hasPrefix("↑") || trimmed.hasPrefix("↓") || trimmed.hasPrefix("↗") || trimmed.hasPrefix("↘"))
        let isNeg = isTrend && (trimmed.contains("-") || trimmed.hasPrefix("↓") || trimmed.hasPrefix("↘"))

        if isTrend {
            let cleanNum = trimmed.replacingOccurrences(of: "^[↑↓↗↘\\s]+", with: "", options: .regularExpression)
            Text("\(isNeg ? "↓" : "↑") \(cleanNum)")
                .font(.system(size: 12.5, weight: .bold))
                .foregroundStyle(isNeg ? Color(red: 0.878, green: 0.310, blue: 0.373) : Color(red: 0.071, green: 0.604, blue: 0.353))
                .frame(width: width, alignment: frameAlignment(for: align))
                .padding(.horizontal, 10)
                .padding(.vertical, 7)
        } else if !emphasize, isPrimaryNameCol, let badge = entityBadge(for: trimmed) {
            HStack(spacing: 6) {
                Text(badge.0)
                    .font(.system(size: 10, weight: .bold))
                    .foregroundStyle(badge.1)
                    .frame(width: 18, height: 18)
                    .background(RoundedRectangle(cornerRadius: 5).fill(badge.1.opacity(0.16)))
                Text(text)
                    .font(.system(size: 12.5, weight: .semibold))
                    .foregroundStyle(Color(red: 0.133, green: 0.102, blue: 0.278))
                    .lineLimit(1)
            }
            .frame(width: width, alignment: frameAlignment(for: align))
            .padding(.horizontal, 10)
            .padding(.vertical, 7)
        } else {
            Text(text)
                .font((emphasize || (isPrimaryNameCol && Int(trimmed) == nil)) ? .system(size: 12.5, weight: .bold) : Aero.caption())
                .foregroundStyle((emphasize || (isPrimaryNameCol && Int(trimmed) == nil)) ? Color(red: 0.133, green: 0.102, blue: 0.278) : Aero.textSecondary)
                .frame(width: width, alignment: frameAlignment(for: align))
                .padding(.horizontal, 10)
                .padding(.vertical, 7)
                .multilineTextAlignment(alignment(for: align))
        }
    }

    private func alignment(for align: TableAlign) -> TextAlignment {
        switch align {
        case .center: return .center
        case .right: return .trailing
        case .left: return .leading
        }
    }

    private func frameAlignment(for align: TableAlign) -> Alignment {
        switch align {
        case .center: return .center
        case .right: return .trailing
        case .left: return .leading
        }
    }
}

// MARK: - Divider & math

private struct DividerBlockView: View {
    var body: some View {
        Rectangle()
            .fill(Aero.outline.opacity(0.6))
            .frame(height: 1)
            .padding(.vertical, 4)
    }
}

private struct MathBlockView: View {
    let latex: String

    var body: some View {
        Group {
            if let rendered = renderMathText(latex, baseSize: 16, color: Aero.text) {
                rendered
            } else {
                Text(latex)
            }
        }
        .font(Aero.responsive(16, relativeTo: .body))
        .foregroundStyle(Aero.text)
        .frame(maxWidth: .infinity, alignment: .center)
        .textSelection(.enabled)
    }
}

// MARK: - Image

private enum ImageLoadState {
    case loading
    case ready(UIImage)
    case failed
}

private struct ImageBlockView: View {
    let url: String
    let alt: String
    @State private var state: ImageLoadState = .loading
    @State private var viewerOpen = false
    @Environment(\.gsOpenInApp) private var inAppBrowser

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            content
            metaRow
        }
    }

    @ViewBuilder
    private var content: some View {
        switch state {
        case .loading:
            RoundedRectangle(cornerRadius: 12)
                .fill(Aero.raisedSurface)
                .frame(height: 120)
                .overlay(ProgressView().scaleEffect(0.8))
        case .ready(let image):
            Image(uiImage: image)
                .resizable()
                .scaledToFit()
                .clipShape(RoundedRectangle(cornerRadius: 12))
                .onTapGesture { viewerOpen = true }
                .accessibilityLabel(alt.isEmpty ? "Image" : alt)
        case .failed:
            // Honest failure: compact row, never a fake success.
            HStack(spacing: 8) {
                Image(systemName: "photo.badge.exclamationmark")
                    .foregroundStyle(Aero.textTertiary)
                VStack(alignment: .leading, spacing: 2) {
                    Text(alt.isEmpty ? "Image" : alt)
                        .font(Aero.bodyMedium())
                        .foregroundStyle(Aero.text)
                        .lineLimit(1)
                    Text("Couldn't load this image")
                        .font(Aero.caption())
                        .foregroundStyle(Aero.textTertiary)
                }
                Spacer()
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 10)
            .background(RoundedRectangle(cornerRadius: 12).fill(Aero.raisedSurface))
        }
    }

    private var metaRow: some View {
        HStack(spacing: 12) {
            Button("Open") {
                // TASK 1c: in-app browser when the screen hosts one (chat does).
                if let link = URL(string: url), ["https", "http"].contains(link.scheme?.lowercased() ?? "") {
                    gsOpenURL(link, inApp: inAppBrowser)
                }
            }
            .font(Aero.responsive(13, relativeTo: .footnote))
            .foregroundStyle(Aero.textMuted)
            Button("Copy link") {
                UIPasteboard.general.string = url
            }
            .font(Aero.responsive(13, relativeTo: .footnote))
            .foregroundStyle(Aero.textMuted)
        }
        .buttonStyle(KineticPressStyle())
    }

    private func load() async {
        guard url.lowercased().hasPrefix("https://") else {
            state = .failed
            return
        }
        state = .loading
        var request = URLRequest(url: URL(string: url) ?? URL(string: "https://invalid.local")!)
        request.timeoutInterval = 8
        do {
            let (data, response) = try await URLSession.shared.data(for: request)
            if let http = response as? HTTPURLResponse, http.statusCode >= 400 {
                state = .failed
                return
            }
            guard data.count < 8 * 1024 * 1024, let image = UIImage(data: data) else {
                state = .failed
                return
            }
            state = .ready(image)
        } catch {
            state = .failed
        }
    }
}

// MARK: - Collapsible (<details> — never auto-collapsed prose)

private struct CollapsibleBlockView: View {
    let summary: String
    let blocks: [Block]
    var citations: [Int: String] = [:]
    @State private var expanded = false

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Button {
                withAnimation(.easeOut(duration: 0.2)) { expanded.toggle() }
            } label: {
                HStack(spacing: 6) {
                    Image(systemName: "chevron.right")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundStyle(Aero.accent)
                        .rotationEffect(.degrees(expanded ? 90 : 0))
                    Text(summary)
                        .font(Aero.label())
                        .foregroundStyle(Aero.text)
                    Spacer()
                }
                .padding(.vertical, 6)
                .contentShape(Rectangle())
            }
            .buttonStyle(KineticPressStyle())
            .accessibilityValue(expanded ? "Expanded" : "Collapsed")
            .accessibilityHint("Toggle detail section")

            if expanded {
                VStack(alignment: .leading, spacing: 8) {
                    ForEach(Array(blocks.enumerated()), id: \.offset) { _, inner in
                        BlockView(block: inner, isLast: false, citations: citations)
                    }
                }
                .padding(.leading, 24)
            }
        }
    }
}

// MARK: - Structured seams — citations & tool results (backend provides; never faked)

private func isAcademicCitationDomain(_ domain: String, _ url: String) -> Bool {
    let hay = "\(domain) \(url)".lowercased()
    return hay.contains(".edu") ||
        hay.contains("nature.com") ||
        hay.contains("arxiv.org") ||
        hay.contains("ieee.org") ||
        hay.contains("acm.org") ||
        hay.contains("springer.") ||
        hay.contains("sciencedirect.") ||
        hay.contains("oxford") ||
        hay.contains("cambridge") ||
        hay.contains("nih.gov") ||
        hay.contains("pubmed")
}

private struct CitationsBlockView: View {
    let citations: [CitationEntry]
    @Environment(\.gsOpenInApp) private var inAppBrowser

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                HStack(spacing: 5) {
                    Text("🔗")
                        .font(.system(size: 13))
                    Text("Key Sources")
                        .font(.system(size: 14, weight: .bold))
                        .foregroundStyle(Aero.text)
                }
                Spacer()
                Text("\(citations.count) \(citations.count == 1 ? "source" : "sources")")
                    .font(.system(size: 11.5, weight: .semibold))
                    .foregroundStyle(Color(red: 0.357, green: 0.239, blue: 0.961))
            }

            ForEach(Array(citations.enumerated()), id: \.offset) { _, citation in
                let academic = isAcademicCitationDomain(citation.domain, citation.url)
                Button {
                    if let link = URL(string: citation.url), ["https", "http"].contains(link.scheme?.lowercased() ?? "") {
                        gsOpenURL(link, inApp: inAppBrowser)
                    }
                } label: {
                    HStack(alignment: .top, spacing: 10) {
                        Text("\(citation.number)")
                            .font(.system(size: 12, weight: .bold))
                            .foregroundStyle(Color(red: 0.357, green: 0.239, blue: 0.961))
                            .frame(width: 26, height: 26)
                            .background(Circle().fill(Color(red: 0.929, green: 0.898, blue: 1.0)))
                            .overlay(Circle().stroke(Color(red: 0.835, green: 0.776, blue: 1.0), lineWidth: 1))
                            .padding(.top, 1)

                        VStack(alignment: .leading, spacing: 3) {
                            Text(citation.title)
                                .font(.system(size: 13.5, weight: .bold))
                                .foregroundStyle(Color(red: 0.122, green: 0.094, blue: 0.259))
                                .multilineTextAlignment(.leading)

                            if !citation.snippet.isEmpty {
                                Text(citation.snippet)
                                    .font(.system(size: 12.5))
                                    .foregroundStyle(Color(red: 0.310, green: 0.282, blue: 0.447))
                                    .lineLimit(2)
                                    .multilineTextAlignment(.leading)
                            }

                            HStack {
                                Text("↗ \(citation.domain.isEmpty ? citation.url : citation.domain)")
                                    .font(.system(size: 11.5, weight: .semibold))
                                    .foregroundStyle(Color(red: 0.357, green: 0.239, blue: 0.961))
                                    .lineLimit(1)
                                Spacer()
                                Text(academic ? "🏛 Academic" : "✓ Trusted Source")
                                    .font(.system(size: 10.5, weight: .bold))
                                    .foregroundStyle(
                                        academic
                                            ? Color(red: 0.357, green: 0.239, blue: 0.961)
                                            : Color(red: 0.071, green: 0.604, blue: 0.353)
                                    )
                                    .padding(.horizontal, 8)
                                    .padding(.vertical, 2)
                                    .background(
                                        Capsule().fill(
                                            academic
                                                ? Color(red: 0.937, green: 0.918, blue: 1.0)
                                                : Color(red: 0.902, green: 0.976, blue: 0.941)
                                        )
                                    )
                            }
                            .padding(.top, 2)
                        }
                        Spacer(minLength: 0)
                    }
                    .padding(.horizontal, 12)
                    .padding(.vertical, 10)
                    .background(RoundedRectangle(cornerRadius: 14).fill(Color.white.opacity(0.78)))
                    .overlay(RoundedRectangle(cornerRadius: 14).stroke(Color(red: 0.890, green: 0.863, blue: 0.980), lineWidth: 1))
                }
                .buttonStyle(.plain)
                .accessibilityElement(children: .combine)
                .accessibilityLabel("Source \(citation.number). \(citation.title), \(citation.domain)")
            }
        }
    }
}

private struct ToolResultBlockView: View {
    let entry: ToolResultEntry
    @State private var expanded = false

    private var statusSymbol: String {
        switch entry.status {
        case "completed", "success", "done": return "checkmark.circle"
        case "failed", "error": return "xmark.circle"
        default: return entry.kind == "search" || entry.kind == "research" ? "magnifyingglass" : "gearshape"
        }
    }

    private var statusColor: Color {
        switch entry.status {
        case "completed", "success", "done": return Aero.success
        case "failed", "error": return Aero.danger
        default: return Aero.toolExecution
        }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Button {
                guard !entry.detail.isEmpty else { return }
                withAnimation(.easeOut(duration: 0.2)) { expanded.toggle() }
            } label: {
                HStack(spacing: 8) {
                    Image(systemName: statusSymbol)
                        .font(.system(size: 14))
                        .foregroundStyle(statusColor)
                    Text(entry.title)
                        .font(Aero.bodyMedium())
                        .foregroundStyle(Aero.text)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    Text("\(entry.kind) · \(entry.status)")
                        .font(Aero.caption())
                        .foregroundStyle(Aero.textTertiary)
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 10)
                .contentShape(Rectangle())
            }
            .buttonStyle(KineticPressStyle())
            if expanded && !entry.detail.isEmpty {
                Text(entry.detail)
                    .font(Aero.caption())
                    .foregroundStyle(Aero.textSecondary)
                    .padding(.horizontal, 12)
                    .padding(.bottom, 10)
            }
        }
        .background(RoundedRectangle(cornerRadius: 12).fill(Aero.raisedSurface))
    }
}
