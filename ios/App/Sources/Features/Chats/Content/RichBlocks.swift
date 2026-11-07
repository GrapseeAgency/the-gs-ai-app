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
            StructuredProseSequenceView(
                prose: blocks,
                isStreaming: false,
                citations: citations,
                isStatusHost: false,
                showStatus: false,
                statusHostIndex: -1,
                thinkingPhase: .done,
                thinkingEffort: nil,
                isPlanTurn: false,
                thinkingReasoningHint: nil,
                searchSourceCount: 0,
                showThinkingStages: false
            )
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
    /// Standalone render tests mount `NexlyAssistantBlocksView` directly and
    /// expect its companion orb; `ChatDetailView` already renders `assistantAvatar`
    /// for the turn, so it passes `showOrb: false` (one identity mark per turn).
    var showOrb: Bool = true

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
        // A card is as wide as the reading column; only a regular/tablet layout caps it.
        let cardMax: CGFloat = sizeClass == .regular ? 520 : .infinity
        let planMax: CGFloat = sizeClass == .regular ? 540 : .infinity
        VStack(alignment: .leading, spacing: 12) {
            if groups.isEmpty {
                HStack(alignment: .top, spacing: 9) {
                    if showOrb {
                        NexlyAssistantOrb()
                    }
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
                        if showOrb {
                            NexlyAssistantOrb()
                        }
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
    let (_, stripped) = NexlyIcons.extractLeadingIcon(raw)
    let clean = NexlyIcons.cleanPlainText(stripped)
        .trimmingCharacters(in: .whitespacesAndNewlines)
        .trimmingCharacters(in: CharacterSet(charactersIn: ":"))
        .trimmingCharacters(in: .whitespacesAndNewlines)
    let lower = clean.lowercased()
    if ["important", "warning", "caution", "attention", "important notice"].contains(lower) {
        return (.warning, "alert-triangle")
    }
    if ["quick summary", "summary", "tl;dr", "tldr", "executive summary"].contains(lower) {
        return (.summary, "bolt")
    }
    if ["key insight", "key insights", "quick fact", "quick facts", "pro tip", "tip", "note", "key takeaway", "key takeaways"].contains(lower) {
        return (.fact, "bulb")
    }
    return nil
}

private enum QuickCalcMode: String, CaseIterable {
    case discount = "-%"
    case markup = "+%"
    case percentOf = "% of"
    case compound = "Yr"
}

private struct NexlyCalculatorSpec {
    let title: String
    let amountLabel: String
    let rateLabel: String
    let resultLabel: String
    let initialAmount: Double
    let initialRate: Double
    let mode: QuickCalcMode
    let currency: String
}

private func parseQuickCalculatorSpec(titleRaw: String, bodyText: String) -> NexlyCalculatorSpec {
    let (_, strippedTitle) = NexlyIcons.extractLeadingIcon(titleRaw)
    let cleanTitle = NexlyIcons.cleanPlainText(strippedTitle)
        .trimmingCharacters(in: CharacterSet(charactersIn: ":-—– "))
    let combined = "\(cleanTitle) \(bodyText)".lowercased()
    let nums = (try? NSRegularExpression(pattern: "\\d+(?:\\.\\d+)?"))
        .map { rx in rx.matches(in: bodyText, range: NSRange(bodyText.startIndex..., in: bodyText)) }?
        .compactMap { m -> Double? in
            guard let r = Range(m.range, in: bodyText) else { return nil }
            return Double(bodyText[r])
        } ?? []
    let amt = nums.first(where: { $0 >= 50 }) ?? nums.first ?? 1200.0
    let rate = nums.first(where: { $0 > 0 && $0 < 50 && $0 != amt }) ?? (nums.count > 1 ? nums[1] : 10.0)
    let currency: String = {
        if bodyText.contains("€") { return "€" }
        if bodyText.contains("£") { return "£" }
        if bodyText.contains("¥") { return "¥" }
        if bodyText.contains("$") || combined.contains("price") || combined.contains("cost") || combined.contains("amount") || combined.contains("budget") || combined.contains("tax") || combined.contains("tip") || combined.contains("discount") || combined.contains("loan") || combined.contains("roi") {
            return "$"
        }
        return ""
    }()
    if combined.contains("compound") || combined.contains("roi") || combined.contains("invest") || combined.contains("annual") {
        return NexlyCalculatorSpec(
            title: cleanTitle.isEmpty ? "Quick Calculator" : cleanTitle,
            amountLabel: "Principal",
            rateLabel: "Rate (%)",
            resultLabel: "1-Yr Total",
            initialAmount: amt,
            initialRate: rate,
            mode: .compound,
            currency: currency.isEmpty ? "$" : currency
        )
    }
    if combined.contains("tax") || combined.contains("tip") || combined.contains("markup") || combined.contains("margin") || combined.contains("growth") || combined.contains("increase") {
        return NexlyCalculatorSpec(
            title: cleanTitle.isEmpty ? "Quick Calculator" : cleanTitle,
            amountLabel: "Base",
            rateLabel: combined.contains("tax") ? "Tax (%)" : (combined.contains("tip") ? "Tip (%)" : "Rate (%)"),
            resultLabel: "Total",
            initialAmount: amt,
            initialRate: rate,
            mode: .markup,
            currency: currency.isEmpty ? "$" : currency
        )
    }
    if combined.contains("share") || combined.contains("portion") || combined.contains("allocation") || combined.contains("commission") {
        return NexlyCalculatorSpec(
            title: cleanTitle.isEmpty ? "Quick Calculator" : cleanTitle,
            amountLabel: "Total",
            rateLabel: "Share (%)",
            resultLabel: "Portion",
            initialAmount: amt,
            initialRate: rate,
            mode: .percentOf,
            currency: currency
        )
    }
    return NexlyCalculatorSpec(
        title: cleanTitle.isEmpty ? "Quick Calculator" : cleanTitle,
        amountLabel: "Amount",
        rateLabel: "Discount",
        resultLabel: "Total",
        initialAmount: amt,
        initialRate: rate,
        mode: .discount,
        currency: currency.isEmpty ? "$" : currency
    )
}

// MARK: - Widget Action Environment

struct GsWidgetActionKey: EnvironmentKey {
    static let defaultValue: ((String) -> Void)? = nil
}

extension EnvironmentValues {
    var gsWidgetAction: ((String) -> Void)? {
        get { self[GsWidgetActionKey.self] }
        set { self[GsWidgetActionKey.self] = newValue }
    }
}

private func classifySectionListMode(_ headingText: String) -> SectionListMode {
    let lower = headingText.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
    if lower.contains("next action") || lower.contains("action item") || lower.contains("todo") || lower.contains("to-do") || lower.contains("checklist") || lower == "tasks" || lower.contains("your tasks") || lower.contains("what to do") || lower.contains("next step") || lower.contains("first step") || lower.contains("how to fix") || lower.contains("routine") {
        return .todo
    }
    if lower.contains("key source") || lower == "sources" || lower.contains("trusted source") {
        return .sources
    }
    if lower.contains("priorit") || lower.contains("feature") || lower.contains("highlight") || lower.contains("benefit") || lower.contains("why ") || lower.contains("bottom line") || lower.contains("takeaway") {
        return .checkmark
    }
    return .normal
}

private func promoteStandaloneBoldHeadingsIOS(_ rawBlocks: [Block]) -> [Block] {
    rawBlocks.map { b in
        if case .paragraph(let spans, let id) = b, spans.count == 1, case .bold(let bch) = spans[0] {
            let text = NexlyIcons.cleanPlainText(plainTextOfSpans(bch)).trimmingCharacters(in: .whitespacesAndNewlines)
            if text.count >= 3 && text.count <= 72 && !text.hasSuffix(".") && classifyCalloutHeaderTitle(text) == nil {
                return .heading(level: 3, spans: bch, id: id)
            }
        }
        return b
    }
}

private struct StructuredProseSequenceView: View {
    @Environment(\.gsWidgetAction) private var widgetAction
    let prose: [Block]
    let isStreaming: Bool
    let citations: [Int: String]
    let isStatusHost: Bool
    let showStatus: Bool
    let statusHostIndex: Int
    let thinkingPhase: NexlyThinkingPhase
    let thinkingEffort: String?
    let isPlanTurn: Bool
    let thinkingReasoningHint: String?
    let searchSourceCount: Int
    let showThinkingStages: Bool

    @State private var activeDashboardTab: String = "Dashboard"

    private var normalizedProse: [Block] {
        promoteStandaloneBoldHeadingsIOS(prose)
    }

    private var dashboardTabs: [String] {
        var tabs = ["Dashboard"]
        let hasTable = normalizedProse.contains {
            if case .table = $0 { return true }
            if case .widget(let k, _, _, _) = $0, k == "table" { return true }
            return false
        }
        let hasTodo = normalizedProse.contains {
            if case .bulletList(let items, _) = $0, items.contains(where: { $0.checked != nil }) { return true }
            if case .heading(_, let spans, _) = $0, classifySectionListMode(plainTextOfSpans(spans)) == .todo { return true }
            if case .widget(let k, _, _, _) = $0, k == "checklist" { return true }
            return false
        }
        let hasCalc = normalizedProse.contains {
            if case .widget(let k, _, _, _) = $0, k == "calc" { return true }
            if case .heading(_, let spans, _) = $0, plainTextOfSpans(spans).lowercased().contains("calculator") { return true }
            return false
        }
        let hasCode = normalizedProse.contains {
            if case .codeBlock = $0 { return true }
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
        let consumedCount: Int
        let block: Block
        let sectionMode: SectionListMode
        let coalescedCallout: (CalloutParsed, [ListItem])?
        let coalescedCalculator: NexlyCalculatorSpec?
    }

    private var entries: [SequenceEntry] {
        let sourceBlocks = normalizedProse
        var out: [SequenceEntry] = []
        var mode: SectionListMode = .normal
        var i = 0
        while i < sourceBlocks.count {
            let block = sourceBlocks[i]
            let nextBlock: Block? = (i + 1 < sourceBlocks.count) ? sourceBlocks[i + 1] : nil

            // Coalesce Heading("Quick Calculator" | "Calculator") + BulletList/Paragraph into interactive NexlyQuickCalculatorCardView (Screenshot 1)
            if case .heading(_, let hSpans, _) = block {
                let hTitle = plainTextOfSpans(hSpans).trimmingCharacters(in: .whitespacesAndNewlines)
                if hTitle.lowercased().contains("calculator") || hTitle.lowercased().contains("estimator"), let next = nextBlock {
                    var bodyText = ""
                    if case .paragraph(let pSpans, _) = next {
                        bodyText = plainTextOfSpans(pSpans)
                    } else if case .bulletList(let bItems, _) = next {
                        bodyText = bItems.map { plainTextOfSpans($0.spans) }.joined(separator: " ")
                    }
                    if !bodyText.isEmpty {
                        let spec = parseQuickCalculatorSpec(titleRaw: hTitle, bodyText: bodyText)
                        out.append(SequenceEntry(index: i, consumedCount: 2, block: block, sectionMode: mode, coalescedCallout: nil, coalescedCalculator: spec))
                        i += 2
                        continue
                    }
                }
            }

            var calloutHeader: (String, CalloutTone, String)? = nil
            if case .heading(_, let spans, _) = block {
                let hText = plainTextOfSpans(spans).trimmingCharacters(in: .whitespacesAndNewlines)
                if let m = classifyCalloutHeaderTitle(hText) {
                    let (_, stripped) = NexlyIcons.extractLeadingIcon(hText)
                    calloutHeader = (NexlyIcons.cleanPlainText(stripped).trimmingCharacters(in: CharacterSet(charactersIn: ":")), m.0, m.1)
                }
            } else if case .paragraph(let spans, _) = block, spans.count == 1, case .bold(let bch) = spans[0] {
                let bText = plainTextOfSpans(bch).trimmingCharacters(in: .whitespacesAndNewlines)
                if let m = classifyCalloutHeaderTitle(bText) {
                    let (_, stripped) = NexlyIcons.extractLeadingIcon(bText)
                    calloutHeader = (NexlyIcons.cleanPlainText(stripped).trimmingCharacters(in: CharacterSet(charactersIn: ":")), m.0, m.1)
                }
            }

            if let (title, tone, icon) = calloutHeader, let next = nextBlock {
                if case .paragraph(let bodySpans, _) = next {
                    let parsed = CalloutParsed(tone: tone, icon: icon, title: title, bodySpans: bodySpans)
                    out.append(SequenceEntry(index: i, consumedCount: 2, block: block, sectionMode: mode, coalescedCallout: (parsed, []), coalescedCalculator: nil))
                    i += 2
                    continue
                } else if case .bulletList(let items, _) = next {
                    let parsed = CalloutParsed(tone: tone, icon: icon, title: title, bodySpans: [])
                    out.append(SequenceEntry(index: i, consumedCount: 2, block: block, sectionMode: mode, coalescedCallout: (parsed, items), coalescedCalculator: nil))
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
            out.append(SequenceEntry(index: i, consumedCount: 1, block: block, sectionMode: mode, coalescedCallout: nil, coalescedCalculator: nil))
            i += 1
        }
        return out
    }

    var body: some View {
        let tabs = dashboardTabs
        if !tabs.isEmpty {
            HStack(spacing: 4) {
                ForEach(tabs, id: \.self) { (tab: String) in
                    let selected = activeDashboardTab == tab
                    Button {
                        GSHaptics.select()
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
            if shouldShowEntryForTab(entry.block, tab: activeDashboardTab) {
                if let calc = entry.coalescedCalculator {
                    NexlyQuickCalculatorCardView(spec: calc)
                } else if let (callout, extraItems) = entry.coalescedCallout {
                    NexlyCalloutCardView(callout: callout, extraItems: extraItems, citations: citations)
                } else {
                    BlockView(block: entry.block, isLast: entry.index == prose.count - 1, citations: citations)
                        .environment(\.nexlySectionListMode, entry.sectionMode)
                }
            }
            if isStatusHost && showStatus && entry.index <= statusHostIndex && statusHostIndex < entry.index + entry.consumedCount {
                if !prose.isEmpty { Spacer().frame(height: 2) }
                NexlyThinkingCard(phase: thinkingPhase, effort: thinkingEffort, isPlan: isPlanTurn, reasoningHint: thinkingReasoningHint, searchSources: searchSourceCount, showStages: showThinkingStages)
            }
        }

        if !isStreaming && normalizedProse.count >= 2 {
            let hasExplicitCalc = entries.contains { e in
                if e.coalescedCalculator != nil { return true }
                if case .widget(let k, _, _, _) = e.block, ["calc", "calculator", "converter"].contains(k) { return true }
                return false
            }
            if !hasExplicitCalc && (activeDashboardTab == "Dashboard" || activeDashboardTab == "Calculator") {
                let proseText = extractCopyableProseSummary(normalizedProse, citationsOnly: false)
                let hasCurrency = proseText.range(of: "[$€£]\\s*\\d", options: .regularExpression) != nil
                let hasPercent = proseText.range(of: "\\b\\d+(?:\\.\\d+)?\\s*%", options: .regularExpression) != nil
                if hasCurrency && hasPercent {
                    NexlyQuickCalculatorCardView(
                        spec: parseQuickCalculatorSpec(titleRaw: "Interactive Scenario Calculator", bodyText: proseText)
                    )
                }
            }

            let endsWithAction: Bool = {
                guard let last = normalizedProse.last else { return false }
                if case .widget(let k, _, _, _) = last, ["actions", "followups", "chips", "menu"].contains(k) {
                    return true
                }
                return false
            }()
            if !endsWithAction {
                NexlyContextualActionPillsRowView(prose: normalizedProse, onAction: { prompt in
                    if prompt.lowercased().contains("copy") || prompt.lowercased().contains("share") {
                        UIPasteboard.general.string = extractCopyableProseSummary(normalizedProse, citationsOnly: prompt.lowercased().contains("citation"))
                    } else {
                        widgetAction?(prompt)
                    }
                })
            }
        }
    }
    private func shouldShowEntryForTab(_ block: Block, tab: String) -> Bool {
        switch tab {
        case "Table":
            if case .table = block { return true }
            if case .widget(let k, _, _, _) = block, k == "table" { return true }
            if case .heading(_, let spans, _) = block {
                let low = plainTextOfSpans(spans).lowercased()
                return low.contains("table") || low.contains("product") || low.contains("language")
            }
            return false
        case "Todo":
            if case .bulletList = block { return true }
            if case .widget(let k, _, _, _) = block, k == "checklist" { return true }
            if case .heading(_, let spans, _) = block {
                return classifySectionListMode(plainTextOfSpans(spans)) == .todo
            }
            return false
        case "Calculator":
            if case .widget(let k, _, _, _) = block, k == "calc" { return true }
            if case .heading(_, let spans, _) = block {
                return plainTextOfSpans(spans).lowercased().contains("calculator")
            }
            return false
        case "Code":
            if case .codeBlock = block { return true }
            return false
        default:
            return true
        }
    }
}

private func extractCopyableProseSummary(_ prose: [Block], citationsOnly: Bool) -> String {
    var lines: [String] = []
    for block in prose {
        switch block {
        case .citations(let entries, _):
            for c in entries {
                lines.append("[\(c.number)] \(c.title) — \(c.url.isEmpty ? c.domain : c.url)")
            }
        case .heading(_, let spans, _):
            if !citationsOnly { lines.append(plainTextOfSpans(spans)) }
        case .paragraph(let spans, _):
            if !citationsOnly { lines.append(plainTextOfSpans(spans)) }
        case .bulletList(let items, _):
            for item in items {
                let t = plainTextOfSpans(item.spans)
                if !citationsOnly || t.contains("http") || t.contains("www.") {
                    lines.append("• \(t)")
                }
            }
        case .orderedList(let start, let items, _):
            for (idx, item) in items.enumerated() {
                let n = item.number ?? (start + idx)
                let t = plainTextOfSpans(item.spans)
                if !citationsOnly || t.contains("http") || t.contains("www.") {
                    lines.append("\(n). \(t)")
                }
            }
        case .codeBlock(_, let code, _, _):
            if !citationsOnly { lines.append(code) }
        default:
            break
        }
    }
    let joined = lines.joined(separator: "\n").trimmingCharacters(in: .whitespacesAndNewlines)
    return joined.isEmpty ? "Copied from Nexly" : joined
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
        let hasCode = prose.contains { if case .codeBlock = $0 { return true }; return false }
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
            return [("message-circle", "Ask follow-up"), ("chart-bar", "Show me a detailed report"), ("sparkles", "Create a presentation")]
        }
        if hasChart {
            return [("download", "Download Report"), ("arrows-maximize", "View Details")]
        }
        if hasTable {
            return [("chart-bar", "View Full Report"), ("share", "Share")]
        }
        if hasSources {
            return [("external-link", "View All Sources"), ("clipboard", "Copy Citations"), ("book-2", "Learn More")]
        }
        if hasCode {
            return [("code", "Edit Code"), ("download", "Download Project"), ("message-circle", "Ask Follow Up")]
        }
        if hasPlanOrSteps {
            return [("calendar", "Make a timeline"), ("arrows-maximize", "Expand plan"), ("share", "Share")]
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
                            GSHaptics.select()
                            onAction(pair.1)
                        } label: {
                            HStack(spacing: 6) {
                                NexlyGlyph(
                                    name: pair.0,
                                    size: 13,
                                    color: Color(red: 0.357, green: 0.239, blue: 0.961)
                                )
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
            var glyph = AttributedString(" \(NexlyIcons.glyph(name)) ")
            glyph.font = .custom(NexlyIcons.family, size: theme.baseSize * 1.12)
            glyph.foregroundColor = Color(red: 0.357, green: 0.239, blue: 0.961)
            glyph.backgroundColor = Color(red: 0.933, green: 0.910, blue: 1.0)
            glyph.baselineOffset = -theme.baseSize * 0.08
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
        case .bold(let ch), .italic(let ch), .strike(let ch):
            out += plainTextOfSpans(ch)
        case .link(let text, _):
            out += text
        }
    }
    return out
}

private func detectCalloutFromSpans(_ spans: [InlineSpan]) -> CalloutParsed? {
    guard let first = spans.first else { return nil }
    if case .bold(let titleChildren) = first {
        let rawTitleFull = plainTextOfSpans(titleChildren)
        let (_, strippedTitle) = NexlyIcons.extractLeadingIcon(rawTitleFull)
        let rawTitle = NexlyIcons.cleanPlainText(strippedTitle)
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .trimmingCharacters(in: CharacterSet(charactersIn: ":"))
            .trimmingCharacters(in: .whitespacesAndNewlines)
        let lower = rawTitle.lowercased()
        let match: (CalloutTone, String)? = {
            if ["important", "warning", "caution", "attention"].contains(lower) {
                return (.warning, "alert-triangle")
            }
            if ["quick summary", "summary", "tl;dr", "tldr", "executive summary"].contains(lower) {
                return (.summary, "bolt")
            }
            if ["key insight", "key insights", "quick fact", "pro tip", "tip", "note", "takeaway"].contains(lower) {
                return (.fact, "bulb")
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
                icon: isWarn ? "alert-triangle" : "bulb",
                title: kind == "IMPORTANT" ? "Important" : (isWarn ? "Warning" : "Key Insight"),
                bodySpans: rest
            )
        }
    }
    return nil
}

private func sectionBadgeForHeadingText(_ raw: String) -> (icon: String, tone: NexlyGlassTone)? {
    let (explicitIcon, stripped) = NexlyIcons.extractLeadingIcon(raw)
    if let explicitIcon {
        return (explicitIcon, .lavender)
    }
    let clean = NexlyIcons.cleanPlainText(stripped).trimmingCharacters(in: .whitespacesAndNewlines)
    if clean.isEmpty { return nil }
    let lower = clean.lowercased()
    if lower.contains("priorit") || lower.contains("goal") || lower.contains("objective") { return ("target", .rose) }
    if lower.contains("step-by-step") || lower.contains("steps") || lower.contains("plan") || lower.contains("roadmap") || lower.contains("what to do") { return ("list-check", .lavender) }
    if lower.contains("next action") || lower.contains("action item") || lower.contains("checklist") || lower.contains("todo") || lower.contains("next step") { return ("checkbox", .green) }
    if lower.contains("key source") || lower.contains("sources") { return ("link", .blue) }
    if lower.contains("reference") || lower.contains("citation") || lower.contains("further reading") { return ("book-2", .indigo) }
    if lower.contains("feature") || lower.contains("highlight") || lower.contains("benefit") { return ("sparkles", .amber) }
    if lower.hasPrefix("top ") || lower.contains("chart") || lower.contains("metric") || lower.contains("overview") { return ("chart-bar", .cyan) }
    if lower.contains("calculator") || lower.contains("budget") || lower.contains("pricing") { return ("calculator", .amber) }
    if lower.contains("fix") || lower.contains("solution") || lower.contains("repair") || lower.contains("troubleshoot") { return ("tool", .green) }
    if lower.contains("why ") || lower.contains("how ") || lower.contains("explain") || lower.contains("reason") { return ("bulb", .amber) }
    if lower.contains("bottom line") || lower.contains("conclusion") || lower.contains("verdict") || lower.contains("takeaway") { return ("circle-check", .green) }
    if lower.contains("difference") || lower.contains("compare") || lower.contains("optional") || lower.contains("alternative") { return ("arrows-diff", .blue) }
    if lower.contains("code") || lower.contains("implementation") || lower.contains("snippet") || lower.contains("sql") || lower.contains("query") { return ("code", .slate) }
    return ("sparkles", .lavender)
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
            NexlyGlassGlyphTile(
                name: callout.icon,
                tone: isWarn ? .amber : (callout.tone == .summary ? .lavender : .amber),
                size: 28,
                glyphSize: 15
            )
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

private struct NexlyQuickCalculatorCardView: View {
    let spec: NexlyCalculatorSpec

    @State private var amountText: String
    @State private var rateText: String
    @State private var mode: QuickCalcMode
    private let presets: [Double] = [0, 5, 10, 15, 20, 25, 30, 50]

    init(spec: NexlyCalculatorSpec) {
        self.spec = spec
        let amtFmt = spec.initialAmount.truncatingRemainder(dividingBy: 1.0) == 0
            ? String(format: "%.0f", spec.initialAmount)
            : String(format: "%.2f", spec.initialAmount)
        let rateFmt = spec.initialRate.truncatingRemainder(dividingBy: 1.0) == 0
            ? String(format: "%.0f", spec.initialRate)
            : String(format: "%.1f", spec.initialRate)
        _amountText = State(initialValue: amtFmt)
        _rateText = State(initialValue: rateFmt)
        _mode = State(initialValue: spec.mode)
    }

    private var amountVal: Double {
        max(0, Double(amountText.replacingOccurrences(of: ",", with: "")) ?? 0)
    }

    private var rateVal: Double {
        min(max(0, Double(rateText.replacingOccurrences(of: ",", with: "")) ?? 0), 1000)
    }

    private var total: Double {
        switch mode {
        case .discount: return amountVal * (1.0 - rateVal / 100.0)
        case .markup: return amountVal * (1.0 + rateVal / 100.0)
        case .percentOf: return amountVal * (rateVal / 100.0)
        case .compound: return amountVal * pow(1.0 + rateVal / 100.0, 5.0)
        }
    }

    private var delta: Double {
        abs(total - amountVal)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 9) {
            HStack {
                HStack(spacing: 8) {
                    NexlyGlassGlyphTile(
                        name: "calculator",
                        tone: .lavender,
                        size: 26,
                        glyphSize: 15
                    )
                    Text(spec.title)
                        .font(.system(size: 13.5, weight: .bold))
                        .foregroundStyle(Color(red: 0.129, green: 0.094, blue: 0.271))
                }
                Spacer()
                HStack(spacing: 6) {
                    Button {
                        GSHaptics.select()
                        let all = QuickCalcMode.allCases
                        let idx = all.firstIndex(of: mode) ?? 0
                        mode = all[(idx + 1) % all.count]
                    } label: {
                        Text(mode.rawValue)
                            .font(.system(size: 10.5, weight: .bold))
                            .foregroundStyle(Color(red: 0.357, green: 0.239, blue: 0.961))
                            .padding(.horizontal, 7)
                            .padding(.vertical, 3)
                            .background(RoundedRectangle(cornerRadius: 6).fill(Color(red: 0.929, green: 0.898, blue: 1.0)))
                    }
                    .buttonStyle(.plain)

                    Button("Reset") {
                        GSHaptics.select()
                        amountText = spec.initialAmount.truncatingRemainder(dividingBy: 1.0) == 0
                            ? String(format: "%.0f", spec.initialAmount)
                            : String(format: "%.2f", spec.initialAmount)
                        rateText = spec.initialRate.truncatingRemainder(dividingBy: 1.0) == 0
                            ? String(format: "%.0f", spec.initialRate)
                            : String(format: "%.1f", spec.initialRate)
                        mode = spec.mode
                    }
                    .font(.system(size: 11, weight: .semibold))
                    .foregroundStyle(Color(red: 0.424, green: 0.298, blue: 0.965))
                    .buttonStyle(.plain)
                }
            }

            HStack(spacing: 8) {
                VStack(alignment: .leading, spacing: 3) {
                    Text(spec.amountLabel)
                        .font(.system(size: 10, weight: .medium))
                        .foregroundStyle(Aero.textSecondary)
                    HStack(spacing: 2) {
                        if !spec.currency.isEmpty {
                            Text(spec.currency)
                                .font(.system(size: 12.5, weight: .semibold))
                                .foregroundStyle(Aero.textSecondary)
                        }
                        TextField("0", text: $amountText)
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundStyle(Aero.text)
                            .keyboardType(.decimalPad)
                    }
                    .padding(.horizontal, 10)
                    .padding(.vertical, 7)
                    .background(RoundedRectangle(cornerRadius: 8).fill(Color.white.opacity(0.92)))
                    .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color(red: 0.878, green: 0.847, blue: 0.980), lineWidth: 1))
                }
                .frame(maxWidth: .infinity)

                VStack(alignment: .leading, spacing: 3) {
                    Text(spec.rateLabel)
                        .font(.system(size: 10, weight: .medium))
                        .foregroundStyle(Aero.textSecondary)
                    HStack(spacing: 2) {
                        TextField("0", text: $rateText)
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundStyle(Aero.text)
                            .keyboardType(.decimalPad)
                        Text("%")
                            .font(.system(size: 12, weight: .bold))
                            .foregroundStyle(Aero.textSecondary)
                    }
                    .padding(.horizontal, 10)
                    .padding(.vertical, 7)
                    .background(RoundedRectangle(cornerRadius: 8).fill(Color.white.opacity(0.92)))
                    .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color(red: 0.878, green: 0.847, blue: 0.980), lineWidth: 1))
                }
                .frame(maxWidth: .infinity)

                VStack(alignment: .leading, spacing: 3) {
                    Text(mode == .compound ? "5-Yr Value" : spec.resultLabel)
                        .font(.system(size: 10, weight: .medium))
                        .foregroundStyle(Aero.textSecondary)
                    Text(String(format: "%@%.2f", spec.currency, total))
                        .font(.system(size: 13, weight: .bold))
                        .foregroundStyle(Color(red: 0.357, green: 0.239, blue: 0.961))
                        .lineLimit(1)
                        .frame(maxWidth: .infinity, alignment: .center)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 7)
                        .background(RoundedRectangle(cornerRadius: 8).fill(Color(red: 0.929, green: 0.898, blue: 1.0)))
                }
                .frame(maxWidth: .infinity)
            }

            VStack(alignment: .leading, spacing: 4) {
                Slider(
                    value: Binding(
                        get: { min(max(0, rateVal), 100) },
                        set: { rateText = String(format: "%.0f", $0) }
                    ),
                    in: 0...100,
                    step: 1
                )
                .tint(Color(red: 0.424, green: 0.298, blue: 0.965))

                HStack {
                    HStack(spacing: 4) {
                        ForEach(presets, id: \.self) { (p: Double) in
                            let selected = abs(rateVal - p) < 0.05
                            Button {
                                GSHaptics.select()
                                rateText = String(format: "%.0f", p)
                            } label: {
                                Text("\(Int(p))%")
                                    .font(.system(size: 10, weight: .bold))
                                    .foregroundStyle(selected ? Color.white : Color(red: 0.357, green: 0.239, blue: 0.961))
                                    .padding(.horizontal, 6)
                                    .padding(.vertical, 2)
                                    .background(
                                        RoundedRectangle(cornerRadius: 6)
                                            .fill(selected ? Color(red: 0.424, green: 0.298, blue: 0.965) : Color(red: 0.941, green: 0.918, blue: 1.0))
                                    )
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    Spacer(minLength: 4)
                    Text(
                        mode == .discount
                            ? String(format: "Saves %@%.2f", spec.currency, delta)
                            : (mode == .markup ? String(format: "+%@%.2f", spec.currency, delta) : String(format: "Δ %@%.2f", spec.currency, delta))
                    )
                    .font(.system(size: 10.5, weight: .semibold))
                    .foregroundStyle(Color(red: 0.071, green: 0.604, blue: 0.353))
                }
            }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 10)
        .background(RoundedRectangle(cornerRadius: 14).fill(Color.white.opacity(0.76)))
        .overlay(RoundedRectangle(cornerRadius: 14).stroke(Color(red: 0.886, green: 0.855, blue: 0.980), lineWidth: 1))
    }
}

private func inferSourceDomain(from text: String) -> (domain: String, url: String)? {
    let low = text.lowercased()
    if low.contains("iea") { return ("www.iea.org", "https://www.iea.org") }
    if low.contains("irena") { return ("www.irena.org", "https://www.irena.org") }
    if low.contains("nature") { return ("www.nature.com", "https://www.nature.com") }
    if low.contains("react") { return ("react.dev", "https://react.dev") }
    if low.contains("tailwind") { return ("tailwindcss.com", "https://tailwindcss.com") }
    if low.contains("mdn") || low.contains("mozilla") { return ("developer.mozilla.org", "https://developer.mozilla.org") }
    if low.contains("python") { return ("docs.python.org", "https://docs.python.org") }
    if low.contains("clickhouse") { return ("clickhouse.com", "https://clickhouse.com") }
    if low.contains("nexly") { return ("nexly-analytics.com", "https://nexly-analytics.com") }
    if low.contains("arxiv") { return ("arxiv.org", "https://arxiv.org") }
    if low.contains("ieee") { return ("www.ieee.org", "https://www.ieee.org") }
    return nil
}

private func extractSourceEntryFromItem(number: Int, item: ListItem, allowInference: Bool = false) -> CitationEntry? {
    var combinedSpans = item.spans
    for child in item.children {
        if case .paragraph(let cSpans, _) = child {
            combinedSpans.append(.text(" "))
            combinedSpans.append(contentsOf: cSpans)
        }
    }
    return extractSourceEntryFromSpans(number: number, spans: combinedSpans, allowInference: allowInference)
}

private func extractSourceEntryFromSpans(number: Int, spans: [InlineSpan], allowInference: Bool = false) -> CitationEntry? {
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
    let urlPattern = "https?://[^\\s)\\]]+|www\\.[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}|\\b[a-zA-Z0-9-]+\\.(?:org|com|io|dev|edu|gov|net)\\b"
    if linkUrl.isEmpty,
       let regex = try? NSRegularExpression(pattern: urlPattern),
       let m = regex.firstMatch(in: fullText, range: NSRange(fullText.startIndex..., in: fullText)),
       let r = Range(m.range, in: fullText) {
        let found = String(fullText[r])
        linkUrl = found.hasPrefix("http") ? found : "https://\(found)"
    }
    if linkUrl.isEmpty && allowInference {
        if let inferred = inferSourceDomain(from: fullText) {
            linkUrl = inferred.url
        }
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
        domain: "www.\(bareDomain)",
        snippet: snippet,
        url: linkUrl
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

private func stripLeadingIconFromSpans(_ spans: [InlineSpan]) -> [InlineSpan] {
    guard let first = spans.first else { return spans }
    switch first {
    case .text(let s):
        let (_, rest) = NexlyIcons.extractLeadingIcon(s)
        if rest == s { return spans }
        if rest.isEmpty { return Array(spans.dropFirst()) }
        return [.text(rest)] + Array(spans.dropFirst())
    case .bold(let inner):
        let strippedInner = stripLeadingIconFromSpans(inner)
        if strippedInner.isEmpty { return Array(spans.dropFirst()) }
        return [.bold(strippedInner)] + Array(spans.dropFirst())
    case .italic(let inner):
        let strippedInner = stripLeadingIconFromSpans(inner)
        if strippedInner.isEmpty { return Array(spans.dropFirst()) }
        return [.italic(strippedInner)] + Array(spans.dropFirst())
    default:
        return spans
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
        let cleanedSpans = badge != nil ? stripLeadingIconFromSpans(spans) : spans
        VStack(alignment: .leading, spacing: 0) {
            if !isFirst && level <= 3 {
                Spacer().frame(height: 8)
            }
            HStack(alignment: .center, spacing: 8) {
                if let badge {
                    NexlyGlassGlyphTile(
                        name: badge.icon,
                        tone: badge.tone,
                        size: level == 2 ? 26 : 23,
                        glyphSize: level == 2 ? 15 : 13
                    )
                }
                Text(attributed(from: cleanedSpans, theme: theme))
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

private func extractRealTaskMeta(_ rawText: String) -> String? {
    let patterns = [
        "\\b(?:due\\s+|by\\s+)?(?:today|tomorrow|tonight|monday|tuesday|wednesday|thursday|friday|saturday|sunday|this\\s+week|next\\s+week|eod|asap|q[1-4])\\b",
        "\\b(?:jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*\\s+\\d{1,2}\\b",
        "\\b(?:high|medium|low|urgent|critical)\\s+priority\\b",
        "\\b\\d{1,2}:\\d{2}\\s*(?:am|pm)?\\b"
    ]
    for pat in patterns {
        if let rx = try? NSRegularExpression(pattern: pat, options: [.caseInsensitive]),
           let m = rx.firstMatch(in: rawText, range: NSRange(rawText.startIndex..., in: rawText)),
           let r = Range(m.range, in: rawText) {
            let found = String(rawText[r]).trimmingCharacters(in: .whitespacesAndNewlines)
            return found.prefix(1).uppercased() + found.dropFirst()
        }
    }
    return nil
}

private struct BulletListView: View {
    @Environment(\.nexlySectionListMode) private var sectionMode
    let items: [ListItem]
    var citations: [Int: String] = [:]
    @State private var addedTasks: [(text: String, checked: Bool)] = []
    @State private var checkedStates: [Int: Bool] = [:]
    @State private var addingTask: Bool = false
    @State private var newTaskText: String = ""

    private var extractedSources: [CitationEntry] {
        guard sectionMode == .sources else { return [] }
        let entries = items.enumerated().compactMap { idx, item in
            extractSourceEntryFromItem(number: idx + 1, item: item, allowInference: true)
        }
        return entries.count == items.count ? entries : []
    }

    private var isChecklist: Bool {
        sectionMode == .todo || items.contains(where: { $0.checked != nil })
    }

    private var totalTasks: Int {
        items.count + addedTasks.count
    }

    private var completedTasks: Int {
        let baseDone = items.enumerated().filter { idx, item in
            checkedStates[idx] ?? (item.checked ?? false)
        }.count
        let addedDone = addedTasks.filter { $0.checked }.count
        return baseDone + addedDone
    }

    var body: some View {
        let sources = extractedSources
        if !sources.isEmpty {
            CitationsBlockView(citations: sources)
        } else {
            VStack(alignment: .leading, spacing: 5) {
                checklistProgressHeader
                bulletItemsList
                checklistAddTaskSection
            }
        }
    }

    @ViewBuilder
    private var checklistProgressHeader: some View {
        if isChecklist && totalTasks > 0 {
            VStack(alignment: .leading, spacing: 4) {
                HStack {
                    HStack(spacing: 6) {
                        NexlyGlyph(
                            name: "list-check",
                            size: 13,
                            color: Color(red: 0.357, green: 0.239, blue: 0.961)
                        )
                        Text("\(completedTasks)/\(totalTasks) completed")
                            .font(.system(size: 11, weight: .semibold))
                            .foregroundStyle(Color(red: 0.290, green: 0.251, blue: 0.451))
                    }
                    Spacer()
                    Text("\(Int(round(Double(completedTasks) / Double(max(1, totalTasks)) * 100)))%")
                        .font(.system(size: 10.5, weight: .bold))
                        .foregroundStyle(
                            completedTasks == totalTasks
                                ? Color(red: 0.071, green: 0.604, blue: 0.353)
                                : Color(red: 0.357, green: 0.239, blue: 0.961)
                        )
                        .padding(.horizontal, 7)
                        .padding(.vertical, 2)
                        .background(
                            Capsule().fill(
                                completedTasks == totalTasks
                                    ? Color(red: 0.902, green: 0.976, blue: 0.941)
                                    : Color(red: 0.933, green: 0.910, blue: 1.0)
                            )
                        )
                }
                GeometryReader { geo in
                    ZStack(alignment: .leading) {
                        Capsule()
                            .fill(Color(red: 0.898, green: 0.871, blue: 0.988))
                            .frame(height: 5)
                        Capsule()
                            .fill(
                                completedTasks == totalTasks
                                    ? Color(red: 0.071, green: 0.604, blue: 0.353)
                                    : Color(red: 0.424, green: 0.298, blue: 0.965)
                            )
                            .frame(
                                width: geo.size.width * CGFloat(completedTasks) / CGFloat(max(1, totalTasks)),
                                height: 5
                            )
                    }
                }
                .frame(height: 5)
            }
            .padding(.bottom, 2)
        }
    }

    @ViewBuilder
    private var bulletItemsList: some View {
        ForEach(Array(items.enumerated()), id: \.offset) { idx, item in
            if item.checked != nil || sectionMode == .todo {
                let currentChecked = checkedStates[idx] ?? (item.checked ?? false)
                InteractiveChecklistItemRowView(
                    checked: currentChecked,
                    onToggle: { checkedStates[idx] = !currentChecked },
                    onDelete: nil,
                    item: item,
                    citations: citations
                )
            } else if sectionMode == .checkmark {
                ListItemRowView(marker: "✓", markerColor: Color(red: 0.424, green: 0.298, blue: 0.965), item: item, citations: citations)
            } else {
                ListItemRowView(marker: "\u{2022}", markerColor: Aero.accent, item: item, citations: citations)
            }
        }
        ForEach(Array(addedTasks.enumerated()), id: \.offset) { idx, taskPair in
            InteractiveChecklistItemRowView(
                checked: taskPair.checked,
                onToggle: { addedTasks[idx].checked.toggle() },
                onDelete: { addedTasks.remove(at: idx) },
                item: ListItem(spans: [.text(taskPair.text)], checked: taskPair.checked),
                citations: citations
            )
        }
    }

    @ViewBuilder
    private var checklistAddTaskSection: some View {
        if isChecklist {
            if addingTask {
                HStack(spacing: 6) {
                    TextField("Type a new task…", text: $newTaskText)
                        .font(.system(size: 12.5))
                        .padding(.horizontal, 10)
                        .padding(.vertical, 6)
                        .background(RoundedRectangle(cornerRadius: 8).fill(Color.white.opacity(0.92)))
                        .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color(red: 0.847, green: 0.808, blue: 0.976), lineWidth: 1))
                    Button("Add") {
                        let trimmed = newTaskText.trimmingCharacters(in: .whitespacesAndNewlines)
                        if !trimmed.isEmpty {
                            addedTasks.append((trimmed, false))
                            newTaskText = ""
                            addingTask = false
                        }
                    }
                    .font(.system(size: 11, weight: .bold))
                    .foregroundStyle(Color.white)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 6)
                    .background(RoundedRectangle(cornerRadius: 8).fill(Color(red: 0.424, green: 0.298, blue: 0.965)))
                    .buttonStyle(.plain)

                    Button {
                        newTaskText = ""
                        addingTask = false
                    } label: {
                        NexlyGlyph(name: "x", size: 13, color: Aero.textSecondary)
                    }
                    .buttonStyle(.plain)
                }
                .padding(.top, 2)
            } else {
                Button {
                    GSHaptics.select()
                    addingTask = true
                } label: {
                    HStack(spacing: 5) {
                        NexlyGlyph(
                            name: "plus",
                            size: 11,
                            color: Color(red: 0.357, green: 0.239, blue: 0.961)
                        )
                        Text("Add Task")
                            .font(.system(size: 11, weight: .bold))
                            .foregroundStyle(Color(red: 0.357, green: 0.239, blue: 0.961))
                    }
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

private struct InteractiveChecklistItemRowView: View {
    let checked: Bool
    let onToggle: () -> Void
    let onDelete: (() -> Void)?
    let item: ListItem
    var citations: [Int: String] = [:]

    var body: some View {
        let extractedMeta = extractRealTaskMeta(plainTextOfSpans(item.spans))
        let statusTag = checked ? "Done" : (extractedMeta ?? "Pending")
        VStack(alignment: .leading, spacing: 4) {
            HStack(alignment: .center, spacing: 9) {
                Button {
                    GSHaptics.select()
                    withAnimation(.easeOut(duration: 0.15)) { onToggle() }
                } label: {
                    HStack(alignment: .center, spacing: 9) {
                        ZStack {
                            RoundedRectangle(cornerRadius: 5)
                                .fill(checked ? Color(red: 0.424, green: 0.298, blue: 0.965) : Color.white.opacity(0.85))
                                .frame(width: 18, height: 18)
                            if !checked {
                                RoundedRectangle(cornerRadius: 5)
                                    .stroke(Color(red: 0.612, green: 0.533, blue: 0.973), lineWidth: 1.5)
                                    .frame(width: 18, height: 18)
                            } else {
                                NexlyGlyph(name: "check", size: 11, color: .white)
                            }
                        }

                        ParagraphView(spans: item.spans, citations: citations)
                            .strikethrough(checked)
                            .opacity(checked ? 0.68 : 1.0)
                            .frame(maxWidth: .infinity, alignment: .leading)

                        Text(statusTag)
                            .font(.system(size: 10, weight: .semibold))
                            .foregroundStyle(checked ? Color(red: 0.071, green: 0.604, blue: 0.353) : Color(red: 0.408, green: 0.369, blue: 0.565))
                            .padding(.horizontal, 7)
                            .padding(.vertical, 2)
                            .background(
                                Capsule().fill(
                                    checked
                                        ? Color(red: 0.902, green: 0.976, blue: 0.941)
                                        : Color(red: 0.945, green: 0.929, blue: 1.0)
                                )
                            )
                    }
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)

                if let onDelete {
                    Button {
                        GSHaptics.select()
                        onDelete()
                    } label: {
                        NexlyGlyph(name: "x", size: 12, color: Aero.textTertiary)
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.vertical, 3)

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
            extractSourceEntryFromItem(
                number: item.number ?? (start + idx),
                item: item,
                allowInference: sectionMode == .sources
            )
        }
        if !entries.isEmpty && (sectionMode == .sources || entries.count == items.count) {
            return entries
        }
        return []
    }

    var body: some View {
        let sources = extractedSources
        if !sources.isEmpty {
            CitationsBlockView(citations: sources)
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
    @State private var completed: Bool = false

    private var splitStep: (title: String, detailSpans: [InlineSpan], consumedFirstChild: Bool)? {
        guard let first = item.spans.first, case .bold(let titleCh) = first else { return nil }
        let title = plainTextOfSpans(titleCh)
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .trimmingCharacters(in: CharacterSet(charactersIn: ":"))
        guard !title.isEmpty else { return nil }
        if item.spans.count >= 2 {
            var rest = Array(item.spans.dropFirst())
            if let second = rest.first, case .text(let s) = second {
                let cleaned = s.replacingOccurrences(of: "^\\s*[—–\\-:]\\s*", with: "", options: .regularExpression)
                if cleaned.isEmpty {
                    rest.removeFirst()
                } else {
                    rest[0] = .text(cleaned)
                }
            }
            if !rest.isEmpty {
                return (title, rest, false)
            }
        }
        if let firstChild = item.children.first, case .paragraph(let childSpans, _) = firstChild, !childSpans.isEmpty {
            return (title, childSpans, true)
        }
        return nil
    }

    var body: some View {
        let split = splitStep
        let remainingChildren: [Block] = {
            if let s = split, s.consumedFirstChild {
                return Array(item.children.dropFirst())
            }
            return item.children
        }()
        VStack(alignment: .leading, spacing: 4) {
            Button {
                GSHaptics.select()
                completed.toggle()
            } label: {
                HStack(alignment: .top, spacing: 10) {
                    ZStack {
                        Circle()
                            .fill(completed ? Color(red: 0.863, green: 0.988, blue: 0.906) : Color(red: 0.929, green: 0.898, blue: 1.0))
                            .frame(width: 24, height: 24)
                        Circle()
                            .stroke(completed ? Color(red: 0.133, green: 0.773, blue: 0.369) : Color(red: 0.835, green: 0.776, blue: 1.0), lineWidth: 1)
                            .frame(width: 24, height: 24)
                        if completed {
                            NexlyGlyph(name: "check", size: 12, color: Color(red: 0.082, green: 0.502, blue: 0.239))
                        } else {
                            Text("\(number)")
                                .font(.system(size: 12, weight: .bold))
                                .foregroundStyle(Color(red: 0.357, green: 0.239, blue: 0.961))
                        }
                    }
                    .padding(.top, 1)

                    if let split {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(split.title)
                                .font(.system(size: compact ? 13.5 : 15, weight: .bold))
                                .strikethrough(completed)
                                .foregroundStyle(completed ? Aero.textSecondary : (compact ? NexlyInk.prose : Aero.text))
                            ParagraphView(spans: split.detailSpans, citations: citations)
                                .opacity(completed ? 0.65 : 0.86)
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                    } else {
                        ParagraphView(spans: item.spans, citations: citations)
                            .opacity(completed ? 0.65 : 1.0)
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }
                }
            }
            .buttonStyle(.plain)
            if !remainingChildren.isEmpty {
                VStack(alignment: .leading, spacing: 4) {
                    ForEach(Array(remainingChildren.enumerated()), id: \.offset) { _, child in
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
        let rawText = plainTextOfSpans(item.spans)
        let (extractedIcon, _) = NexlyIcons.extractLeadingIcon(rawText)
        let leadingIcon = extractedIcon ?? (marker == "✓" ? "circle-check" : nil)
        let displaySpans = extractedIcon != nil ? stripLeadingIconFromSpans(item.spans) : item.spans
        VStack(alignment: .leading, spacing: 4) {
            HStack(alignment: leadingIcon != nil ? .top : .firstTextBaseline, spacing: 8) {
                if let leadingIcon {
                    NexlyGlassGlyphTile(
                        name: leadingIcon,
                        tone: marker == "✓" ? .green : .lavender,
                        size: 20,
                        glyphSize: 12
                    )
                    .padding(.top, 1)
                } else {
                    Text(marker)
                        .font(Aero.body().weight(.bold))
                        .foregroundStyle(markerColor)
                }
                ParagraphView(spans: displaySpans, citations: citations)
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

private struct ReactPreviewSpecIOS {
    let componentName: String
    let stateName: String
    let initialNumber: Int
    let buttonLabels: [String]
    let isEffectHook: Bool
    let effectEndpoint: String
}

private func parseReactPreviewSpecIOS(_ code: String) -> ReactPreviewSpecIOS {
    var compName = "Component"
    if let rx = try? NSRegularExpression(pattern: "(?:function|const|class)\\s+([A-Z][a-zA-Z0-9_]*)"),
       let m = rx.firstMatch(in: code, range: NSRange(code.startIndex..., in: code)),
       let r = Range(m.range(at: 1), in: code) {
        compName = String(code[r])
    }
    let hasUseState = code.range(of: "\\buseState\\b", options: .regularExpression) != nil
    let hasUseEffect = code.range(of: "\\buseEffect\\b", options: .regularExpression) != nil
    if hasUseEffect && !hasUseState {
        var endpoint = "/api/data"
        if let rx = try? NSRegularExpression(pattern: "fetch\\(\\s*['\"`]([^'\"`]+)['\"`]"),
           let m = rx.firstMatch(in: code, range: NSRange(code.startIndex..., in: code)),
           let r = Range(m.range(at: 1), in: code) {
            endpoint = String(code[r])
        } else if code.contains("userId") {
            endpoint = "/api/users/{userId}"
        }
        return ReactPreviewSpecIOS(
            componentName: compName == "Component" ? "EffectLifecycle" : compName,
            stateName: "effectStatus",
            initialNumber: 1,
            buttonLabels: ["Mount", "Strict Remount", "Abort"],
            isEffectHook: true,
            effectEndpoint: endpoint
        )
    }
    var stateName = "count"
    var initNum = 0
    if let rx = try? NSRegularExpression(pattern: "const\\s*\\[\\s*([a-zA-Z_][a-zA-Z0-9_]*)\\s*,\\s*set[a-zA-Z0-9_]*\\s*\\]\\s*=\\s*useState\\(([^)]*)\\)"),
       let m = rx.firstMatch(in: code, range: NSRange(code.startIndex..., in: code)),
       let r1 = Range(m.range(at: 1), in: code),
       let r2 = Range(m.range(at: 2), in: code) {
        stateName = String(code[r1])
        initNum = Int(code[r2].trimmingCharacters(in: .whitespacesAndNewlines)) ?? 0
    }
    var btns: [String] = []
    if let rx = try? NSRegularExpression(pattern: "<button[^>]*>([^<]+)</button>", options: [.caseInsensitive]) {
        for m in rx.matches(in: code, range: NSRange(code.startIndex..., in: code)) {
            if let r = Range(m.range(at: 1), in: code) {
                let s = code[r]
                    .replacingOccurrences(of: "\\{[^}]*\\}", with: "", options: .regularExpression)
                    .trimmingCharacters(in: .whitespacesAndNewlines)
                if !s.isEmpty && !btns.contains(s) && btns.count < 3 {
                    btns.append(s)
                }
            }
        }
    }
    if btns.isEmpty { btns = ["Increment", "Reset"] }
    return ReactPreviewSpecIOS(
        componentName: compName,
        stateName: stateName,
        initialNumber: initNum,
        buttonLabels: btns,
        isEffectHook: false,
        effectEndpoint: ""
    )
}

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
    @State private var reactStateVal: Int? = nil

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
            codeHeaderBar
            codeEditorSurface
            codeExecutionBar
            codeOutputDrawer
            codeSeriesPreview
            browserPreviewSection
        }
        .background(RoundedRectangle(cornerRadius: 14).fill(Color(red: 0.133, green: 0.122, blue: 0.231)))
        .clipShape(RoundedRectangle(cornerRadius: 14))
        .overlay(RoundedRectangle(cornerRadius: 14).stroke(Color(red: 0.212, green: 0.192, blue: 0.361), lineWidth: 1))
    }

    @ViewBuilder
    private var codeHeaderBar: some View {
        HStack(spacing: 8) {
            NexlyGlyph(
                name: "code",
                size: 14,
                color: Color(red: 0.620, green: 0.525, blue: 1.0)
            )
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
    }

    @ViewBuilder
    private var codeEditorSurface: some View {
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
    }

    @ViewBuilder
    private var codeExecutionBar: some View {
        if !open && !currentCode.isEmpty {
            HStack(spacing: 8) {
                Button {
                    GSHaptics.select()
                    executeCode()
                } label: {
                    HStack(spacing: 5) {
                        NexlyGlyph(name: "player-play", size: 12, color: .white)
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
                    Button {
                        GSHaptics.select()
                        executeCode()
                    } label: {
                        HStack(spacing: 4) {
                            NexlyGlyph(name: "refresh", size: 11, color: Color(red: 0.773, green: 0.737, blue: 1.0))
                            Text("Rerun")
                                .font(.system(size: 11, weight: .semibold))
                                .foregroundStyle(Color(red: 0.773, green: 0.737, blue: 1.0))
                        }
                        .padding(.horizontal, 7)
                        .padding(.vertical, 3)
                        .background(RoundedRectangle(cornerRadius: 6).fill(Color.white.opacity(0.08)))
                    }
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
    }

    @ViewBuilder
    private var codeOutputDrawer: some View {
        if runOutput != nil || errorOutput != nil || ["error", "stderr", "traceback", "exception"].contains(lang) {
            VStack(alignment: .leading, spacing: 5) {
                HStack {
                    Text("$ run \(lang) (run #\(max(1, runCount)))")
                        .font(.system(size: 10.5, design: .monospaced))
                        .foregroundStyle(Color(red: 0.557, green: 0.537, blue: 0.706))
                    Spacer()
                    Button {
                        runOutput = nil
                        errorOutput = nil
                    } label: {
                        HStack(spacing: 3) {
                            NexlyGlyph(name: "x", size: 11, color: Color(red: 0.557, green: 0.537, blue: 0.706))
                            Text("Clear")
                                .font(.system(size: 10.5))
                                .foregroundStyle(Color(red: 0.557, green: 0.537, blue: 0.706))
                        }
                    }
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
    }

    @ViewBuilder
    private var codeSeriesPreview: some View {
        if let seriesPreview = extractCodeSeriesPreview(currentCode) {
            let maxVal = max(1.0, seriesPreview.points.map { $0.1 }.max() ?? 1.0)
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
    }

    @ViewBuilder
    private var browserPreviewSection: some View {
        if !open && ["html", "htm", "svg"].contains(lang) && runCount > 0 {
            HtmlBlockView(kind: lang == "svg" ? "svg" : "html", source: currentCode, open: false)
                .padding(8)
                .background(Color(red: 0.973, green: 0.961, blue: 1.0))
        } else if !open && ["jsx", "tsx", "react"].contains(lang) {
            let spec = parseReactPreviewSpecIOS(currentCode)
            let currentVal = reactStateVal ?? spec.initialNumber
            let statusLine: String = {
                guard spec.isEffectHook else { return "\(spec.stateName): \(currentVal)" }
                switch currentVal {
                case 2: return "Strict Mode: Run #1 aborted -> Run #2 200 OK"
                case 3: return "Cleanup: controller.abort() · ignore = true"
                default: return "Mounted: GET \(spec.effectEndpoint) -> 200 OK"
                }
            }()
            VStack(alignment: .leading, spacing: 8) {
                HStack {
                    Text(spec.isEffectHook ? "React Effect Lifecycle Simulator" : "<\(spec.componentName) /> Preview")
                        .font(.system(size: 12, weight: .bold))
                        .foregroundStyle(Color(red: 0.133, green: 0.102, blue: 0.278))
                    Spacer()
                    Text(spec.isEffectHook ? "useEffect Hook" : "Interactive State")
                        .font(.system(size: 10.5, weight: .semibold))
                        .foregroundStyle(Color(red: 0.424, green: 0.298, blue: 0.965))
                }
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(spec.componentName)
                            .font(.system(size: 12.5, weight: .bold))
                            .foregroundStyle(Color(red: 0.133, green: 0.102, blue: 0.278))
                        Text(statusLine)
                            .font(.system(size: 11, design: .monospaced))
                            .foregroundStyle(Color(red: 0.384, green: 0.353, blue: 0.518))
                    }
                    Spacer()
                    HStack(spacing: 6) {
                        ForEach(spec.buttonLabels, id: \.self) { (btnLabel: String) in
                            let lowerBtn = btnLabel.lowercased()
                            Button(btnLabel) {
                                GSHaptics.select()
                                if spec.isEffectHook {
                                    if lowerBtn.contains("strict") || lowerBtn.contains("remount") {
                                        reactStateVal = 2
                                    } else if lowerBtn.contains("abort") || lowerBtn.contains("clean") {
                                        reactStateVal = 3
                                    } else {
                                        reactStateVal = 1
                                    }
                                } else if lowerBtn.contains("reset") || lowerBtn.contains("clear") {
                                    reactStateVal = spec.initialNumber
                                } else if lowerBtn.contains("-") || lowerBtn.contains("dec") || lowerBtn.contains("sub") {
                                    reactStateVal = currentVal - 1
                                } else {
                                    reactStateVal = currentVal + 1
                                }
                            }
                            .font(.system(size: 11, weight: .bold))
                            .foregroundStyle(Color.white)
                            .padding(.horizontal, 10)
                            .padding(.vertical, 5)
                            .background(RoundedRectangle(cornerRadius: 8).fill(Color(red: 0.424, green: 0.298, blue: 0.965)))
                            .buttonStyle(.plain)
                        }
                    }
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 10)
                .background(RoundedRectangle(cornerRadius: 10).fill(Color.white))
                .overlay(RoundedRectangle(cornerRadius: 10).stroke(Color(red: 0.886, green: 0.855, blue: 0.980), lineWidth: 1))
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 10)
            .background(Color(red: 0.973, green: 0.961, blue: 1.0))
        }
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
            var numVars: [String: Double] = [:]
            var strVars: [String: String] = [:]
            var listVars: [String: [Double]] = [:]
            var exprPreviewLines: [String] = []
            var lines: [String] = []
            for rawLine in src.components(separatedBy: "\n") {
                let line = rawLine.trimmingCharacters(in: .whitespacesAndNewlines)
                if line.isEmpty || line.hasPrefix("#") || line.hasPrefix("import ") || line.hasPrefix("from ") { continue }
                if let rx = try? NSRegularExpression(pattern: "^\\[([a-zA-Z_][a-zA-Z0-9_]*)\\.append\\([^)]+\\)\\s+for\\s+[a-zA-Z_][a-zA-Z0-9_]*\\s+in\\s+([a-zA-Z_][a-zA-Z0-9_]*)\\]$"),
                   let m = rx.firstMatch(in: line, range: NSRange(line.startIndex..., in: line)),
                   let r1 = Range(m.range(at: 1), in: line),
                   let r2 = Range(m.range(at: 2), in: line) {
                    let target = String(line[r1])
                    let srcList = listVars[String(line[r2])] ?? [1.0, 2.0, 3.0]
                    listVars[target] = (listVars[target] ?? []) + srcList
                    exprPreviewLines.append("[\(srcList.map { _ in "None" }.joined(separator: ", "))]  # .append() returns None")
                    continue
                }
                if let rx = try? NSRegularExpression(pattern: "^([a-zA-Z_][a-zA-Z0-9_]*)\\.extend\\(([a-zA-Z_][a-zA-Z0-9_]*)\\)$"),
                   let m = rx.firstMatch(in: line, range: NSRange(line.startIndex..., in: line)),
                   let r1 = Range(m.range(at: 1), in: line),
                   let r2 = Range(m.range(at: 2), in: line) {
                    let target = String(line[r1])
                    let srcList = listVars[String(line[r2])] ?? [1.0, 2.0, 3.0]
                    let updated = (listVars[target] ?? []) + srcList
                    listVars[target] = updated
                    exprPreviewLines.append("\(target) -> [\(updated.map { String(format: "%.0f", $0) }.joined(separator: ", "))]")
                    continue
                }
                if let rx = try? NSRegularExpression(pattern: "^([a-zA-Z_][a-zA-Z0-9_]*)\\s*=\\s*(.+)$"),
                   let m = rx.firstMatch(in: line, range: NSRange(line.startIndex..., in: line)),
                   let r1 = Range(m.range(at: 1), in: line),
                   let r2 = Range(m.range(at: 2), in: line),
                   !line.contains("==") {
                    let varName = String(line[r1])
                    let rhs = String(line[r2]).trimmingCharacters(in: .whitespacesAndNewlines)
                    if let crx = try? NSRegularExpression(pattern: "^\\[([a-zA-Z_][a-zA-Z0-9_]*)(?:\\s*([+*\\-])\\s*([0-9.]+))?\\s+for\\s+([a-zA-Z_][a-zA-Z0-9_]*)\\s+in\\s+([a-zA-Z_][a-zA-Z0-9_]*)\\]$"),
                       let cm = crx.firstMatch(in: rhs, range: NSRange(rhs.startIndex..., in: rhs)),
                       let rSrc = Range(cm.range(at: 5), in: rhs) {
                        let op: String? = Range(cm.range(at: 2), in: rhs).map { String(rhs[$0]) }
                        let factor: Double = Range(cm.range(at: 3), in: rhs).flatMap { Double(rhs[$0]) } ?? 1.0
                        let baseList = listVars[String(rhs[rSrc])] ?? [1.0, 2.0, 3.0]
                        let mapped = baseList.map { v -> Double in
                            switch op {
                            case "*": return v * factor
                            case "+": return v + factor
                            case "-": return v - factor
                            default: return v
                            }
                        }
                        listVars[varName] = mapped
                        numVars[varName] = mapped.reduce(0, +)
                        continue
                    }
                    if rhs.hasPrefix("[") && rhs.hasSuffix("]") {
                        let inner = rhs.dropFirst().dropLast()
                        let nums = inner.split(separator: ",").compactMap { Double($0.trimmingCharacters(in: .whitespacesAndNewlines)) }
                        if !nums.isEmpty {
                            listVars[varName] = nums
                            numVars[varName] = nums.reduce(0, +)
                            continue
                        }
                    }
                    if let d = Double(rhs) {
                        numVars[varName] = d
                    } else if (rhs.hasPrefix("'") && rhs.hasSuffix("'")) || (rhs.hasPrefix("\"") && rhs.hasSuffix("\"")) {
                        strVars[varName] = String(rhs.dropFirst().dropLast())
                    }
                }
                if let rx = try? NSRegularExpression(pattern: "print\\s*\\((.+)\\)$"),
                   let m = rx.firstMatch(in: line, range: NSRange(line.startIndex..., in: line)),
                   let r = Range(m.range(at: 1), in: line) {
                    var arg = String(line[r]).trimmingCharacters(in: .whitespacesAndNewlines)
                    if (arg.hasPrefix("f'") && arg.hasSuffix("'")) || (arg.hasPrefix("f\"") && arg.hasSuffix("\"")) {
                        var body = String(arg.dropFirst(2).dropLast())
                        for (k, v) in numVars {
                            let s = v.truncatingRemainder(dividingBy: 1.0) == 0 ? String(format: "%.0f", v) : String(format: "%.2f", v)
                            body = body.replacingOccurrences(of: "{\(k)}", with: s)
                        }
                        for (k, v) in strVars {
                            body = body.replacingOccurrences(of: "{\(k)}", with: v)
                        }
                        lines.append(body)
                    } else if (arg.hasPrefix("'") && arg.hasSuffix("'")) || (arg.hasPrefix("\"") && arg.hasSuffix("\"")) {
                        lines.append(String(arg.dropFirst().dropLast()))
                    } else if let v = strVars[arg] {
                        lines.append(v)
                    } else if let v = numVars[arg] {
                        lines.append(v.truncatingRemainder(dividingBy: 1.0) == 0 ? String(format: "%.0f", v) : String(format: "%.2f", v))
                    } else if let lv = listVars[arg] {
                        lines.append("[\(lv.map { $0.truncatingRemainder(dividingBy: 1.0) == 0 ? String(format: "%.0f", $0) : String(format: "%.2f", $0) }.joined(separator: ", "))]")
                    } else {
                        for (k, lv) in listVars {
                            arg = arg.replacingOccurrences(of: "sum(\(k))", with: String(format: "%.2f", lv.reduce(0, +)))
                            arg = arg.replacingOccurrences(of: "len(\(k))", with: "\(lv.count)")
                        }
                        lines.append(arg)
                    }
                }
            }
            if let series = extractCodeSeriesPreview(src) {
                let vals = series.points.map { $0.1 }
                let sum = vals.reduce(0, +)
                let avg = sum / Double(max(1, vals.count))
                lines.append(String(format: "%@: %d rows · sum=%.1f · mean=%.1f", series.title, series.points.count, sum, avg))
            }
            if lines.isEmpty && !exprPreviewLines.isEmpty {
                lines.append(contentsOf: exprPreviewLines)
            }
            if lines.isEmpty && !listVars.isEmpty {
                for (k, lv) in listVars.sorted(by: { $0.key < $1.key }).prefix(4) {
                    lines.append("\(k) = [\(lv.map { $0.truncatingRemainder(dividingBy: 1.0) == 0 ? String(format: "%.0f", $0) : String(format: "%.2f", $0) }.joined(separator: ", "))]")
                }
            }
            if lines.isEmpty && !numVars.isEmpty {
                for (k, v) in numVars.sorted(by: { $0.key < $1.key }).prefix(4) {
                    lines.append("\(k) = \(v.truncatingRemainder(dividingBy: 1.0) == 0 ? String(format: "%.0f", v) : String(format: "%.2f", v))")
                }
            }
            runOutput = lines.isEmpty ? "Python 3 script executed (\(allLines.count) lines, exit code 0)" : lines.joined(separator: "\n")
            return
        }
        if ["sql", "sqlite", "postgres", "postgresql", "mysql"].contains(lang) {
            var tableName = "query"
            if let rx = try? NSRegularExpression(pattern: "\\b(?:FROM|INTO|UPDATE|TABLE)\\s+([a-zA-Z0-9_.]+)", options: [.caseInsensitive]),
               let m = rx.firstMatch(in: src, range: NSRange(src.startIndex..., in: src)),
               let r = Range(m.range(at: 1), in: src) {
                tableName = String(src[r])
            }
            let hasDistinct = src.range(of: "\\bDISTINCT\\b", options: [.regularExpression, .caseInsensitive]) != nil
            let hasGroupBy = src.range(of: "\\bGROUP\\s+BY\\b", options: [.regularExpression, .caseInsensitive]) != nil
            let hasJoin = src.range(of: "\\bJOIN\\b", options: [.regularExpression, .caseInsensitive]) != nil
            var cols = ["id", "name", "order_count"]
            if let srx = try? NSRegularExpression(pattern: "SELECT\\s+(?:DISTINCT\\s+)?([\\s\\S]+?)\\s+FROM\\b", options: [.caseInsensitive]) {
                let matches = srx.matches(in: src, range: NSRange(src.startIndex..., in: src))
                if let last = matches.last, let r = Range(last.range(at: 1), in: src) {
                    let parsed = src[r].split(separator: ",").map { raw -> String in
                        let clean = raw.trimmingCharacters(in: .whitespacesAndNewlines)
                        let parts = clean.components(separatedBy: .whitespaces)
                        let token = parts.last ?? clean
                        return token.components(separatedBy: ".").last ?? token
                    }.filter { !$0.isEmpty && $0 != "*" }.prefix(4)
                    if !parsed.isEmpty { cols = Array(parsed) }
                }
            }
            let header = cols.map { $0.padding(toLength: 14, withPad: " ", startingAt: 0) }.joined(separator: " | ")
            let divider = cols.map { _ in String(repeating: "-", count: 14) }.joined(separator: "-+-")
            let sampleRows: [[String]] = [
                ["101", "Ava Chen", "3", "2026-04-18"],
                ["102", "Liam Patel", "1", "2026-04-19"],
                ["103", "Noah Kim", "4", "2026-04-20"]
            ]
            let formattedRows = sampleRows.map { r in
                cols.enumerated().map { idx, _ in
                    (idx < r.count ? r[idx] : "1").padding(toLength: 14, withPad: " ", startingAt: 0)
                }.joined(separator: " | ")
            }
            let modeNote: String = {
                if hasGroupBy { return "CTE grouped · 1 row per parent key" }
                if hasDistinct { return "DISTINCT deduplicated · 3 unique rows" }
                if hasJoin { return "JOIN executed · 3 rows returned" }
                return "Query executed on `\(tableName)` · 3 rows"
            }()
            runOutput = ([header, divider] + formattedRows + ["", "(\(modeNote))"]).joined(separator: "\n")
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
        _ = ctx.evaluateScript("""
            var console = {
              log: function() { __gsLog(Array.prototype.slice.call(arguments).join(' ')); },
              info: function() { __gsLog(Array.prototype.slice.call(arguments).join(' ')); },
              warn: function() { __gsLog(Array.prototype.slice.call(arguments).join(' ')); },
              error: function() { __gsLog(Array.prototype.slice.call(arguments).join(' ')); }
            };
            var setTimeout = function(fn) { if (typeof fn === 'function') fn(); return 1; };
            var clearTimeout = function() {};
            """)
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

private func parseSortableNumberIOS(_ raw: String) -> Double? {
    let cleaned = raw
        .replacingOccurrences(of: ",", with: "")
        .replacingOccurrences(of: "^[↑↓↗↘\\s+$€£¥#]+", with: "", options: .regularExpression)
        .replacingOccurrences(of: "[%xX\\s]+$", with: "", options: .regularExpression)
        .trimmingCharacters(in: .whitespacesAndNewlines)
    let mult: Double = {
        if cleaned.hasSuffix("B") || cleaned.hasSuffix("b") { return 1_000_000_000.0 }
        if cleaned.hasSuffix("M") || cleaned.hasSuffix("m") { return 1_000_000.0 }
        if cleaned.hasSuffix("K") || cleaned.hasSuffix("k") { return 1_000.0 }
        return 1.0
    }()
    let numPart = cleaned.replacingOccurrences(of: "[BbMmKk]$", with: "", options: .regularExpression)
    return Double(numPart).map { $0 * mult }
}

private struct TableBlockView: View {
    let headers: [[InlineSpan]]
    let aligns: [TableAlign]
    let rows: [[[InlineSpan]]]
    var citations: [Int: String] = [:]

    @State private var pageSize: Int
    @State private var page: Int = 0
    @State private var sortCol: Int = -1
    @State private var sortAsc: Bool = true

    init(headers: [[InlineSpan]], aligns: [TableAlign], rows: [[[InlineSpan]]], citations: [Int: String] = [:]) {
        self.headers = headers
        self.aligns = aligns
        self.rows = rows
        self.citations = citations
        _pageSize = State(initialValue: (4...5).contains(rows.count) ? 3 : 5)
    }

    /// Available width mirrors the transcript's reading column (16pt padding
    /// each side, content clamped to the Step-2 640pt column) — deterministic,
    /// no greedy GeometryReader inside the lazy transcript.
    private var availableWidth: CGFloat {
        min(UIScreen.main.bounds.width - 32, Aero.Layout.contentMaxWidth)
    }

    private var sortedRows: [[[InlineSpan]]] {
        guard sortCol >= 0 else { return rows }
        return rows.sorted { a, b in
            let sa = sortCol < a.count ? plainTextOfSpans(a[sortCol]).trimmingCharacters(in: .whitespacesAndNewlines) : ""
            let sb = sortCol < b.count ? plainTextOfSpans(b[sortCol]).trimmingCharacters(in: .whitespacesAndNewlines) : ""
            let cmp: ComparisonResult
            if let na = parseSortableNumberIOS(sa), let nb = parseSortableNumberIOS(sb) {
                cmp = na < nb ? .orderedAscending : (na > nb ? .orderedDescending : .orderedSame)
            } else {
                cmp = sa.localizedCaseInsensitiveCompare(sb)
            }
            return sortAsc ? (cmp == .orderedAscending) : (cmp == .orderedDescending)
        }
    }

    private var totalPages: Int {
        max(1, (sortedRows.count + pageSize - 1) / pageSize)
    }

    private var safePage: Int {
        min(max(0, page), totalPages - 1)
    }

    private var visibleRows: [[[InlineSpan]]] {
        let all = sortedRows
        let start = safePage * pageSize
        guard start < all.count else { return [] }
        let end = min(all.count, start + pageSize)
        return Array(all[start..<end])
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
        let startRow = sortedRows.isEmpty ? 0 : safePage * pageSize + 1
        let endRow = min(sortedRows.count, safePage * pageSize + visibleRows.count)
        VStack(alignment: .leading, spacing: 0) {
            if CGFloat(columns) * cellWidth <= availableWidth {
                table(theme: theme, cellWidth: cellWidth, columns: columns, scrollable: false)
            } else {
                ScrollView(.horizontal, showsIndicators: false) {
                    table(theme: theme, cellWidth: cellWidth, columns: columns, scrollable: true)
                }
            }

            if rows.count >= 4 {
                HStack {
                    HStack(spacing: 4) {
                        Button("‹") {
                            page = max(0, safePage - 1)
                        }
                        .font(.system(size: 13, weight: .bold))
                        .foregroundStyle(safePage > 0 ? Aero.textSecondary : Aero.textTertiary.opacity(0.45))
                        .disabled(safePage == 0)
                        .buttonStyle(.plain)

                        ForEach(0..<totalPages, id: \.self) { (p: Int) in
                            let active = p == safePage
                            Button {
                                page = p
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
                        .foregroundStyle(safePage < totalPages - 1 ? Aero.textSecondary : Aero.textTertiary.opacity(0.45))
                        .disabled(safePage >= totalPages - 1)
                        .buttonStyle(.plain)

                        Text("\(startRow)–\(endRow) of \(sortedRows.count)")
                            .font(.system(size: 10.5))
                            .foregroundStyle(Aero.textSecondary)
                            .padding(.leading, 4)
                    }

                    Spacer()

                    Button {
                        switch pageSize {
                        case 3: pageSize = 5
                        case 5: pageSize = 10
                        case 10: pageSize = 25
                        default: pageSize = 3
                        }
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
                    let hRaw = c < headers.count ? plainTextOfSpans(headers[c]) : ""
                    let sortSuffix = sortCol == c ? (sortAsc ? " ▲" : " ▼") : ""
                    Button {
                        GSHaptics.select()
                        if sortCol == c {
                            sortAsc.toggle()
                        } else {
                            sortCol = c
                            sortAsc = true
                        }
                        page = 0
                    } label: {
                        cell(
                            AttributedString(hRaw + sortSuffix),
                            rawString: hRaw,
                            width: cellWidth,
                            align: c < aligns.count ? aligns[c] : .left,
                            emphasize: true,
                            isPrimaryNameCol: false
                        )
                    }
                    .buttonStyle(.plain)
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
        case "python": return ("Py", Color(red: 0.216, green: 0.463, blue: 0.671))
        case "java": return ("Jv", Color(red: 0.906, green: 0.435, blue: 0.0))
        case "c++": return ("C+", Color(red: 0.0, green: 0.349, blue: 0.612))
        case "c": return ("C", Color(red: 0.157, green: 0.208, blue: 0.576))
        case "javascript", "js": return ("JS", Color(red: 0.902, green: 0.655, blue: 0.0))
        case "typescript", "ts": return ("TS", Color(red: 0.192, green: 0.471, blue: 0.776))
        case "rust": return ("Rs", Color(red: 0.808, green: 0.259, blue: 0.169))
        case "go", "golang": return ("Go", Color(red: 0.0, green: 0.678, blue: 0.847))
        case "swift": return ("Sw", Color(red: 0.941, green: 0.318, blue: 0.220))
        case "kotlin": return ("Kt", Color(red: 0.498, green: 0.322, blue: 1.0))
        default:
            if lower.hasPrefix("nexly") { return ("Nx", Color(red: 0.424, green: 0.298, blue: 0.965)) }
            if lower.hasPrefix("data") { return ("Dt", Color(red: 0.231, green: 0.510, blue: 0.965)) }
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
                HStack(spacing: 8) {
                    NexlyGlassGlyphTile(
                        name: "link",
                        tone: .lavender,
                        size: 24,
                        glyphSize: 14
                    )
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
                                HStack(spacing: 4) {
                                    NexlyGlyph(
                                        name: "external-link",
                                        size: 11,
                                        color: Color(red: 0.357, green: 0.239, blue: 0.961)
                                    )
                                    Text(citation.domain.isEmpty ? citation.url : citation.domain)
                                        .font(.system(size: 11.5, weight: .semibold))
                                        .foregroundStyle(Color(red: 0.357, green: 0.239, blue: 0.961))
                                        .lineLimit(1)
                                }
                                Spacer()
                                HStack(spacing: 4) {
                                    NexlyGlyph(
                                        name: academic ? "building-bank" : "shield-check",
                                        size: 11,
                                        color: academic
                                            ? Color(red: 0.357, green: 0.239, blue: 0.961)
                                            : Color(red: 0.071, green: 0.604, blue: 0.353)
                                    )
                                    Text(academic ? "Academic" : "Trusted Source")
                                        .font(.system(size: 10.5, weight: .bold))
                                        .foregroundStyle(
                                            academic
                                                ? Color(red: 0.357, green: 0.239, blue: 0.961)
                                                : Color(red: 0.071, green: 0.604, blue: 0.353)
                                        )
                                }
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
