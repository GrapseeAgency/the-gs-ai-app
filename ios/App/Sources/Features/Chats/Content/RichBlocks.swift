import SwiftUI
import UniformTypeIdentifiers

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

enum QuickCalcMode: String, CaseIterable {
    case discount = "-%"
    case markup = "+%"
    case percentOf = "Budget %"
    case compound = "Compound"
    case loan = "Loan / EMI"
    case macros = "Macros"
}

struct NexlyCalculatorSpec {
    let title: String
    let amountLabel: String
    let rateLabel: String
    let resultLabel: String
    let initialAmount: Double
    let initialRate: Double
    let initialYears: Int
    let mode: QuickCalcMode
    let currency: String
}

func parseQuickCalculatorSpec(titleRaw: String, bodyText: String) -> NexlyCalculatorSpec? {
    let (_, strippedTitle) = NexlyIcons.extractLeadingIcon(titleRaw)
    let cleanTitle = NexlyIcons.cleanPlainText(strippedTitle)
        .trimmingCharacters(in: CharacterSet(charactersIn: ":-—– "))
    let combined = "\(cleanTitle) \(bodyText)".lowercased()
    if ["calorie", "protein", "macro", "tdee", "bmi", "margin", "roi", "budget", "salary", "income"].contains(where: { combined.contains($0) }) { return nil }
    guard ["discount", "tax", "tip", "markup", "vat", "loan", "mortgage", "emi", "compound", "apy", "apr"].contains(where: { combined.contains($0) }) else { return nil }
    let amountPattern = try? NSRegularExpression(pattern: "(?<![\\dA-Za-z_(−-])[$€£¥]\\s*(\\d{1,3}(?:,\\d{3})+(?:\\.\\d+)?|\\d+(?:\\.\\d+)?)(?:\\s*([kKmMbB])(?![A-Za-z]))?(?![\\dA-Za-z.,])")
    let ratePattern = try? NSRegularExpression(pattern: "(?<![\\dA-Za-z_.,−-])(\\d+(?:\\.\\d+)?)\\s*(?:%|percent\\b)", options: .caseInsensitive)
    let sourceRange = NSRange(bodyText.startIndex..., in: bodyText)
    guard amountPattern?.numberOfMatches(in: bodyText, range: sourceRange) == 1,
          ratePattern?.numberOfMatches(in: bodyText, range: sourceRange) == 1 else { return nil }
    let needsTerm = ["loan", "mortgage", "emi", "amortiz", "compound", "invest", "apy", "apr"].contains(where: { combined.contains($0) })
    if needsTerm && bodyText.range(of: "\\b\\d{1,2}\\s*(?:-\\s*)?(?:years?|yrs?\\b)", options: [.regularExpression, .caseInsensitive]) == nil { return nil }


    var currAmount: Double? = nil
    if let crx = try? NSRegularExpression(pattern: "(?<![\\dA-Za-z_(−-])[$€£¥]\\s*(\\d{1,3}(?:,\\d{3})+(?:\\.\\d+)?|\\d+(?:\\.\\d+)?)(?:\\s*([kKmMbB])(?![A-Za-z]))?(?![\\dA-Za-z.,])"),
       let cm = crx.firstMatch(in: bodyText, range: NSRange(bodyText.startIndex..., in: bodyText)),
       let numRange = Range(cm.range(at: 1), in: bodyText),
       let base = Double(bodyText[numRange].replacingOccurrences(of: ",", with: "")) {
        var mult = 1.0
        if cm.range(at: 2).location != NSNotFound, let sr = Range(cm.range(at: 2), in: bodyText) {
            switch bodyText[sr].lowercased() {
            case "k": mult = 1_000.0
            case "m": mult = 1_000_000.0
            case "b": mult = 1_000_000_000.0
            default: break
            }
        }
        currAmount = base * mult
    }

    var pctVal: Double? = nil
    if let prx = try? NSRegularExpression(pattern: "(?<![\\dA-Za-z_.,−-])(\\d+(?:\\.\\d+)?)\\s*(?:%|percent\\b)", options: [.caseInsensitive]),
       let pm = prx.firstMatch(in: bodyText, range: NSRange(bodyText.startIndex..., in: bodyText)),
       let pr = Range(pm.range(at: 1), in: bodyText) {
        pctVal = Double(bodyText[pr])
    }

    var yearsVal: Int = 5
    if let yrx = try? NSRegularExpression(pattern: "(\\d{1,2})\\s*(?:-\\s*)?(?:years?|yrs?\\b)", options: [.caseInsensitive]),
       let ym = yrx.firstMatch(in: bodyText, range: NSRange(bodyText.startIndex..., in: bodyText)),
       let yr = Range(ym.range(at: 1), in: bodyText),
       let y = Int(bodyText[yr]) {
        // The compact horizon control supports 1...40 years. Never silently
        // turn a supplied 0- or 99-year scenario into a different loan.
        if needsTerm && !(1...40).contains(y) { return nil }
        yearsVal = y
    }

    guard let amt = currAmount, let rate = pctVal, amt.isFinite, rate.isFinite, amt >= 0, rate >= 0 else { return nil }

    let currency: String = {
        if bodyText.contains("€") { return "€" }
        if bodyText.contains("£") { return "£" }
        if bodyText.contains("¥") { return "¥" }
        if bodyText.contains("$") || combined.contains("price") || combined.contains("cost") || combined.contains("amount") || combined.contains("budget") || combined.contains("tax") || combined.contains("tip") || combined.contains("discount") || combined.contains("loan") || combined.contains("roi") {
            return "$"
        }
        return ""
    }()
    if combined.contains("loan") || combined.contains("mortgage") || combined.contains("emi") || combined.contains("amortiz") || combined.contains("apr") {
        return NexlyCalculatorSpec(
            title: cleanTitle.isEmpty ? "Loan & EMI Simulator" : cleanTitle,
            amountLabel: "Principal",
            rateLabel: "APR (%)",
            resultLabel: "Monthly EMI",
            initialAmount: amt,
            initialRate: rate,
            initialYears: yearsVal,
            mode: .loan,
            currency: currency.isEmpty ? "$" : currency
        )
    }
    if combined.contains("calorie") || combined.contains("protein") || combined.contains("macro") || combined.contains("tdee") || combined.contains("bmi") {
        return NexlyCalculatorSpec(
            title: cleanTitle.isEmpty ? "Macro & TDEE Estimator" : cleanTitle,
            amountLabel: "Weight (kg)",
            rateLabel: "Protein (g/kg)",
            resultLabel: "Daily Target",
            initialAmount: amt > 300 ? 75.0 : amt,
            initialRate: rate > 5 ? 2.0 : rate,
            initialYears: yearsVal,
            mode: .macros,
            currency: ""
        )
    }
    if combined.contains("compound") || combined.contains("roi") || combined.contains("invest") || combined.contains("annual") || combined.contains("portfolio") || combined.contains("apy") {
        return NexlyCalculatorSpec(
            title: cleanTitle.isEmpty ? "Quick Calculator" : cleanTitle,
            amountLabel: "Principal",
            rateLabel: "APY (%)",
            resultLabel: "\(yearsVal)-Yr Value",
            initialAmount: amt,
            initialRate: rate,
            initialYears: yearsVal,
            mode: .compound,
            currency: currency.isEmpty ? "$" : currency
        )
    }
    if combined.contains("budget") || combined.contains("50/30/20") || combined.contains("salary") || combined.contains("income") {
        return NexlyCalculatorSpec(
            title: cleanTitle.isEmpty ? "Budget & Savings Planner" : cleanTitle,
            amountLabel: "Income",
            rateLabel: "Save (%)",
            resultLabel: "Saved / Mo",
            initialAmount: amt,
            initialRate: rate,
            initialYears: yearsVal,
            mode: .percentOf,
            currency: currency.isEmpty ? "$" : currency
        )
    }
    if combined.contains("tax") || combined.contains("vat") || combined.contains("tip") || combined.contains("markup") || combined.contains("margin") || combined.contains("growth") || combined.contains("increase") {
        return NexlyCalculatorSpec(
            title: cleanTitle.isEmpty ? "Quick Calculator" : cleanTitle,
            amountLabel: "Base",
            rateLabel: combined.contains("tax") ? "Tax (%)" : (combined.contains("tip") ? "Tip (%)" : "Rate (%)"),
            resultLabel: "Total",
            initialAmount: amt,
            initialRate: rate,
            initialYears: yearsVal,
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
            initialYears: yearsVal,
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
        initialYears: yearsVal,
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
                return .heading(level: 3, spans: bch, sourceStart: id)
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
    @StateObject private var codeActions = ResponseCodeActionsIOS()

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
        var coalescedSources: ([CitationEntry], String)? = nil
    }

    private var entries: [SequenceEntry] {
        let sourceBlocks = normalizedProse
        var out: [SequenceEntry] = []
        var mode: SectionListMode = .normal
        var i = 0
        while i < sourceBlocks.count {
            let block = sourceBlocks[i]
            let nextBlock: Block? = (i + 1 < sourceBlocks.count) ? sourceBlocks[i + 1] : nil

            if let sourceCard = sourceCardForHeadingIOS(block, following: nextBlock) {
                mode = .sources
                out.append(SequenceEntry(index: i, consumedCount: 2, block: block, sectionMode: mode, coalescedCallout: nil, coalescedCalculator: nil, coalescedSources: (sourceCard.citations, sourceCard.title)))
                i += 2
                continue
            }

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
                    if !bodyText.isEmpty, let spec = parseQuickCalculatorSpec(titleRaw: hTitle, bodyText: bodyText) {
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
        sequenceBody.environment(\.responseCodeActions, codeActions)
    }

    @ViewBuilder
    private var sequenceBody: some View {
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
                if let (sources, title) = entry.coalescedSources {
                    CitationsBlockView(citations: sources, title: title)
                } else if let calc = entry.coalescedCalculator {
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

        if !isStreaming && !normalizedProse.isEmpty {
            let hasExplicitCalc = entries.contains { e in
                if e.coalescedCalculator != nil { return true }
                if case .widget(let k, _, _, _) = e.block, ["calc", "calculator", "converter"].contains(k) { return true }
                return false
            }
            let proseText = extractCopyableProseSummary(normalizedProse, citationsOnly: false)
            let lowProse = proseText.lowercased()
            let hasCurrency = proseText.range(of: "[$€£]\\s*\\d", options: .regularExpression) != nil
            let hasPercent = proseText.range(of: "\\b\\d+(?:\\.\\d+)?\\s*%", options: .regularExpression) != nil || lowProse.contains("percent")
            let hasNum = proseText.range(of: "\\b\\d{2,}\\b", options: .regularExpression) != nil
            let hasDomainSignal = hasNum && (lowProse.contains("mortgage") || lowProse.contains("amortiz") || lowProse.contains("compound interest") || (lowProse.contains("loan") && (lowProse.contains("apr") || lowProse.contains("monthly"))) || (lowProse.contains("budget") && lowProse.contains("50/30/20")) || (lowProse.contains("calorie") && lowProse.contains("protein")) || lowProse.contains("tdee") || ((hasCurrency || hasNum) && hasPercent && (lowProse.contains("discount") || lowProse.contains("tax") || lowProse.contains("tip") || lowProse.contains("margin") || lowProse.contains("roi") || lowProse.contains("interest") || lowProse.contains("salary"))))
            let spontaneousSpec = parseQuickCalculatorSpec(titleRaw: "Interactive Scenario Calculator", bodyText: proseText)
            let shouldShowSpontaneousCalc = spontaneousSpec != nil && !hasExplicitCalc && (activeDashboardTab == "Dashboard" || activeDashboardTab == "Calculator") && ((hasCurrency && hasPercent) || hasDomainSignal)
            if shouldShowSpontaneousCalc, let spec = spontaneousSpec {
                NexlyQuickCalculatorCardView(spec: spec)
            }

            let hasVisualDataBlock = normalizedProse.contains {
                if case .table = $0 { return true }
                if case .widget(let k, _, _, _) = $0, ["chart", "table", "stat", "progress", "gauge"].contains(k) { return true }
                return false
            }
            let spontaneousMetrics = (!hasVisualDataBlock && activeDashboardTab == "Dashboard")
                ? extractSpontaneousMetricsFromBlocksIOS(normalizedProse)
                : []
            let shouldShowQuantitativeExplorer = spontaneousMetrics.count >= 3
            if shouldShowQuantitativeExplorer {
                NexlyQuantitativeExplorerCardIOS(items: spontaneousMetrics)
            }

            let hasAnyWidgetOrHtml = normalizedProse.contains {
                if case .table = $0 { return true }
                if case .widget = $0 { return true }
                if case .html = $0 { return true }
                return false
            }
            if !hasExplicitCalc && !shouldShowSpontaneousCalc && !shouldShowQuantitativeExplorer && !hasAnyWidgetOrHtml && activeDashboardTab == "Dashboard" {
                let spontaneousConcepts = extractSpontaneousConceptsFromBlocksIOS(normalizedProse)
                if spontaneousConcepts.count >= 3 {
                    NexlyInteractiveConceptDeckCardIOS(concepts: spontaneousConcepts)
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
                NexlyContextualActionPillsRowView(prose: normalizedProse, citations: citations, onShowCode: {
                    activeDashboardTab = "Dashboard"
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

func extractCopyableProseSummary(_ prose: [Block], citationsOnly: Bool) -> String {
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
        case .widget(let kind, let json, _, _):
            if !citationsOnly { lines.append("\(kind) data\n\(json)") }
        case .html(_, let source, _, _):
            if !citationsOnly { lines.append(source) }
        case .table(let headers, _, let rows, _):
            if !citationsOnly {
                lines.append(headers.map { plainTextOfSpans($0) }.joined(separator: "\t"))
                lines += rows.map { row in row.map { plainTextOfSpans($0) }.joined(separator: "\t") }
            }
        case .blockQuote(let blocks, _):
            if !citationsOnly { lines.append(extractCopyableProseSummary(blocks, citationsOnly: false)) }
        case .mathBlock(let latex, _):
            if !citationsOnly { lines.append(latex) }
        case .mermaid(let source, _):
            if !citationsOnly { lines.append(source) }
        default:
            break
        }
    }
    let joined = lines.joined(separator: "\n").trimmingCharacters(in: .whitespacesAndNewlines)
    return joined
}

private struct NexlyContextualActionPillsRowView: View {
    @Environment(\.gsOpenInApp) private var inAppBrowser
    @Environment(\.gsWidgetAction) private var widgetAction
    @Environment(\.responseCodeActions) private var codeActions
    let prose: [Block]
    let citations: [Int: String]
    let onShowCode: () -> Void
    private enum PresentedSheet: Identifiable {
        case share, details
        var id: String {
            switch self {
            case .share: return "share"
            case .details: return "details"
            }
        }
    }
    @State private var presentedSheet: PresentedSheet? = nil
    @State private var sourcesOnly = false
    @State private var preparedExportURL: URL? = nil
    @State private var documentExportRequest: URL? = nil
    @State private var notice: String? = nil

    private var citationText: String {
        [extractCopyableProseSummary(prose, citationsOnly: true), citations.sorted { $0.key < $1.key }.map { "[\($0.key)] \($0.value)" }.joined(separator: "\n")]
            .filter { !$0.isEmpty }.joined(separator: "\n")
    }
    private var sourceURLs: [String] {
        let embedded = prose.flatMap { block -> [String] in
            if case .citations(let entries, _) = block { return entries.map(\.url) }
            return []
        }
        return Array(Set(Array(citations.values) + embedded)).sorted().filter { raw in
            citationSourceURLIOS(raw) != nil
        }
    }
    private var sourceText: String {
        let body = extractCopyableProseSummary(prose, citationsOnly: false)
        return body + (citations.isEmpty ? "" : "\n\nSources\n" + citations.sorted { $0.key < $1.key }.map { "[\($0.key)] \($0.value)" }.joined(separator: "\n"))
    }
    private func prepareExport(_ data: Data, filename: String) throws {
        cleanPreparedExport()
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent("GSResponse-" + UUID().uuidString, isDirectory: true)
        do {
            try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
            let file = directory.appendingPathComponent(filename)
            try data.write(to: file, options: .atomic)
            preparedExportURL = file
            documentExportRequest = file
        } catch {
            try? FileManager.default.removeItem(at: directory)
            throw error
        }
    }
    private func cleanPreparedExport() {
        if let file = preparedExportURL { try? FileManager.default.removeItem(at: file.deletingLastPathComponent()) }
        preparedExportURL = nil
    }
    private func perform(_ label: String) {
        notice = nil
        switch label {
        case "Share": presentedSheet = .share
        case "Copy Citations":
            if citationText.isEmpty { notice = "No citation URLs are available to copy." }
            else { UIPasteboard.general.string = citationText; notice = "Citations copied." }
        case "Download Report":
            do { try prepareExport(Data(sourceText.utf8), filename: "gs-ai-response.md") }
            catch { notice = "Could not prepare export: \(error.localizedDescription)" }
        case "Download Project":
            let sources: [(String?, String)] = prose.compactMap { block in
                guard case .codeBlock(let language, let source, let open, let id) = block, !open else { return nil }
                return codeActions?.source(AnyHashable(id)) ?? (language, source)
            }
            do {
                try prepareExport(try responseSourceArchiveIOS(sources), filename: "gs-ai-source-snippets.zip")
                notice = "Source snippets only, not a complete runnable project. See README.txt."
            } catch { notice = error.localizedDescription }
        case "Edit Code":
            onShowCode()
            if let codeActions { codeActions.editFirst() }
            else { notice = "No editable code block is available." }
        case "View All Sources": sourcesOnly = true; presentedSheet = .details
        case "View Details", "View Full Report": sourcesOnly = false; presentedSheet = .details
        default:
            if let widgetAction { widgetAction(label) }
            else { notice = "Follow-up requests are unavailable in this view." }
        }
    }

    private var actions: [(String, String)] {
        let hasChart = prose.contains { if case .widget(let k, _, _, _) = $0 { return k == "chart" }; return false }
        let hasTable = prose.contains {
            if case .table = $0 { return true }
            if case .widget(let k, _, _, _) = $0 { return k == "table" }
            return false
        }
        let hasCode = prose.contains { if case .codeBlock = $0 { return true }; return false }
        let hasSources = !citations.isEmpty || prose.contains {
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
            VStack(alignment: .leading, spacing: 6) {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(Array(list.enumerated()), id: \.offset) { _, pair in
                        Button {
                            GSHaptics.select()
                            perform(pair.1)
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
            if let notice { Text(notice).font(.caption).accessibilityIdentifier("response.action.notice") }
            }
            .background {
                ResponseDocumentPickerIOS(sourceURL: documentExportRequest) { destination in
                    notice = destination == nil ? "Export cancelled." : "Export saved."
                    documentExportRequest = nil
                    cleanPreparedExport()
                }
                .frame(width: 0, height: 0)
            }
            .sheet(item: $presentedSheet, onDismiss: { cleanPreparedExport() }) { sheet in
                switch sheet {
                case .share:
                    ResponseActivityPresenterIOS(items: [sourceText]) { presentedSheet = nil }
                case .details:
                NavigationStack {
                    ScrollView {
                        VStack(alignment: .leading, spacing: 14) {
                            Text(CodeDisplayIOS.escape(sourcesOnly ? (citationText.isEmpty ? "No source URLs were supplied." : citationText) : sourceText))
                                .textSelection(.enabled)
                            if sourcesOnly {
                                ForEach(sourceURLs, id: \.self) { raw in
                                    Button {
                                        if let url = citationSourceURLIOS(raw) { gsOpenURL(url, inApp: inAppBrowser) }
                                    } label: {
                                        HStack(alignment: .top, spacing: 8) {
                                            NexlyGlyph(name: "external-link", size: 14, color: .purple)
                                            Text(CodeDisplayIOS.escape(raw)).multilineTextAlignment(.leading)
                                        }
                                    }
                                    .accessibilityIdentifier("response.source.link")
                                }
                            }
                        }
                        .frame(maxWidth: .infinity, alignment: .leading).padding()
                    }
                    .navigationTitle(sourcesOnly ? "All sources" : "Response source and data")
                    .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Close details") { presentedSheet = nil } } }
                }
                }
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
    case .codeBlock(let language, let code, let open, let sourceStart):
        CodeBlockCardView(language: language, code: code, open: open, sourceStart: sourceStart)
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
            let cleanCode = CodeDisplayIOS.escape(code)
            var run = AttributedString(cleanCode)
            run.font = .system(size: theme.baseSize * 0.88, weight: .regular, design: .monospaced)
            run.backgroundColor = theme.monoBackground
            if !style.isEmpty { run.inlinePresentationIntent = style }
            result.append(run)
        case .link(let text, let url):
            let cleanText = NexlyIcons.cleanPlainText(text)
            let label = cleanText.isEmpty ? "Link" : cleanText
            if let url = URL(string: url) {
                var run = AttributedString(label)
                run.link = url
                run.underlineStyle = .single
                run.foregroundColor = theme.linkColor
                result.append(run)
            } else {
                result.append(AttributedString(label))
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
                    .fixedSize(horizontal: false, vertical: true)
                if !callout.bodySpans.isEmpty {
                    Text(attributed(from: callout.bodySpans, theme: theme))
                        .font(.system(size: compact ? 13 : 14, weight: .regular))
                        .foregroundStyle(bodyColor)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .textSelection(.enabled)
                        .fixedSize(horizontal: false, vertical: true)
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

func sourceScenarioNumberTextIOS(_ value: Double) -> String {
    guard value.isFinite else { return "" }
    if value == 0 { return "0" }
    let text = String(value).lowercased()
    let parts = text.split(separator: "e")
    guard parts.count == 2, let exponent = Int(parts[1]) else {
        return text.hasSuffix(".0") ? String(text.dropLast(2)) : text
    }
    let negative = parts[0].hasPrefix("-")
    let mantissa = negative ? String(parts[0].dropFirst()) : String(parts[0])
    let decimal = mantissa.split(separator: ".", omittingEmptySubsequences: false)
    let digits = decimal.joined()
    let position = decimal[0].count + exponent
    let plain: String
    if position <= 0 { plain = "0." + String(repeating: "0", count: -position) + digits }
    else if position >= digits.count { plain = digits + String(repeating: "0", count: position - digits.count) }
    else { plain = String(digits.prefix(position)) + "." + String(digits.dropFirst(position)) }
    return (negative ? "-" : "") + plain
}

func sourceLoanMonthlyPaymentIOS(principal: Double, apr: Double, years: Int) -> Double? {
    guard principal.isFinite, apr.isFinite, principal >= 0, apr >= 0, years > 0 else { return nil }
    let months = Double(years) * 12
    let rate = apr / 1200
    let payment = rate == 0 ? principal / months : principal * (rate / -expm1(-months * log1p(rate)))
    return payment.isFinite ? payment : nil
}

private struct NexlyQuickCalculatorCardView: View {
    let spec: NexlyCalculatorSpec

    @State private var amountText: String
    @State private var rateText: String
    @State private var years: Int
    @State private var mode: QuickCalcMode
    private let presets: [Double] = [0, 5, 10, 15, 20, 25, 30, 50]

    init(spec: NexlyCalculatorSpec) {
        self.spec = spec
        let amtFmt = sourceScenarioNumberTextIOS(spec.initialAmount)
        let rateFmt = sourceScenarioNumberTextIOS(spec.initialRate)
        _amountText = State(initialValue: amtFmt)
        _rateText = State(initialValue: rateFmt)
        _years = State(initialValue: spec.initialYears)
        _mode = State(initialValue: spec.mode)
    }

    private var amountVal: Double {
        guard let value = Double(amountText.replacingOccurrences(of: ",", with: "")), value.isFinite, value >= 0 else { return .nan }
        return value
    }

    private var rateVal: Double {
        guard let value = Double(rateText.replacingOccurrences(of: ",", with: "")), value.isFinite, value >= 0 else { return .nan }
        return value
    }

    private var monthlyLoanPayment: Double {
        sourceLoanMonthlyPaymentIOS(principal: amountVal, apr: rateVal, years: years) ?? .nan
    }

    private var totalLoanPaid: Double {
        monthlyLoanPayment * Double(max(1, years * 12))
    }

    private var totalLoanInterest: Double {
        max(0, totalLoanPaid - amountVal)
    }

    private var total: Double {
        switch mode {
        case .discount: return amountVal * (1.0 - rateVal / 100.0)
        case .markup: return amountVal * (1.0 + rateVal / 100.0)
        case .percentOf: return amountVal * (rateVal / 100.0)
        case .compound: return amountVal * pow(1.0 + rateVal / 100.0, Double(years))
        case .loan: return monthlyLoanPayment
        case .macros: return amountVal * 33.0
        }
    }

    private var delta: Double {
        abs(total - amountVal)
    }

    private var formattedResultText: String {
        guard total.isFinite else { return "Undefined" }
        switch mode {
        case .loan: return String(format: "%@%.2f/mo", spec.currency.isEmpty ? "$" : spec.currency, total)
        case .macros: return String(format: "%.0f kcal", total)
        default: return String(format: "%@%.2f", spec.currency, total)
        }
    }

    private var headerIconName: String {
        switch mode {
        case .loan: return "building-bank"
        case .compound: return "trending-up"
        case .macros: return "barbell"
        default: return "calculator"
        }
    }

    private var headerIconTone: NexlyGlassTone {
        switch mode {
        case .compound: return .green
        case .macros: return .peach
        default: return .lavender
        }
    }

    private var amountFieldLabel: String {
        switch mode {
        case .loan: return "Principal"
        case .macros: return "Weight (kg)"
        default: return spec.amountLabel
        }
    }

    private var rateFieldLabel: String {
        switch mode {
        case .loan: return "APR (%)"
        case .macros: return "Protein (g/kg)"
        default: return spec.rateLabel
        }
    }

    private var resultFieldLabel: String {
        switch mode {
        case .compound: return "\(years)-Yr Value"
        case .loan: return "Monthly EMI"
        case .macros: return "Daily Target"
        default: return spec.resultLabel
        }
    }

    private var horizonOptions: [Int] {
        Array(Set((mode == .loan ? [5, 10, 15, 20, 30] : [1, 3, 5, 10, 20]) + [spec.initialYears])).sorted()
    }

    private var sliderMax: Double {
        switch mode {
        case .macros: return 4.0
        case .loan: return 20.0
        default: return 100.0
        }
    }

    private var sliderStep: Double {
        switch mode {
        case .macros, .loan: return 0.5
        default: return 1.0
        }
    }

    private var summaryBadgeText: String {
        guard total.isFinite else { return "Enter valid amount and rate." }
        let curr = spec.currency.isEmpty ? "$" : spec.currency
        switch mode {
        case .loan:
            return String(format: "Interest %@%.2f · Total %@%.2f", curr, totalLoanInterest, curr, totalLoanPaid)
        case .macros:
            return String(format: "Protein %.0fg · Water %.1fL", amountVal * max(1.2, rateVal), amountVal * 0.035)
        case .discount:
            return String(format: "Saves %@%.2f", spec.currency, delta)
        case .markup:
            return String(format: "+%@%.2f", spec.currency, delta)
        default:
            return String(format: "Δ %@%.2f", spec.currency, delta)
        }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 9) {
            calcHeaderRow
            calcInputsRow
            if mode == .compound || mode == .loan {
                calcHorizonRow
            }
            calcSliderSection
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 10)
        .background(RoundedRectangle(cornerRadius: 14).fill(Color.white.opacity(0.76)))
        .overlay(RoundedRectangle(cornerRadius: 14).stroke(Color(red: 0.886, green: 0.855, blue: 0.980), lineWidth: 1))
    }

    @ViewBuilder
    private var calcHeaderRow: some View {
        HStack {
            HStack(spacing: 8) {
                NexlyGlassGlyphTile(
                    name: headerIconName,
                    tone: headerIconTone,
                    size: 26,
                    glyphSize: 15
                )
                Text(spec.title)
                    .font(.system(size: 13.5, weight: .bold))
                    .foregroundStyle(Color(red: 0.129, green: 0.094, blue: 0.271))
            }
            Spacer()
            HStack(spacing: 6) {
                Text(mode.rawValue)
                    .font(.system(size: 10.5, weight: .bold))
                    .padding(.horizontal, 7)

                Button("Reset") {
                    GSHaptics.select()
                    amountText = sourceScenarioNumberTextIOS(spec.initialAmount)
                    rateText = sourceScenarioNumberTextIOS(spec.initialRate)
                    years = spec.initialYears
                    mode = spec.mode
                }
                .font(.system(size: 11, weight: .semibold))
                .foregroundStyle(Color(red: 0.424, green: 0.298, blue: 0.965))
                .buttonStyle(.plain)
            }
        }
    }

    @ViewBuilder
    private var calcInputsRow: some View {
        HStack(spacing: 8) {
            VStack(alignment: .leading, spacing: 3) {
                Text(amountFieldLabel)
                    .font(.system(size: 10, weight: .medium))
                    .foregroundStyle(Aero.textSecondary)
                HStack(spacing: 2) {
                    if !spec.currency.isEmpty && mode != .macros {
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
                Text(rateFieldLabel)
                    .font(.system(size: 10, weight: .medium))
                    .foregroundStyle(Aero.textSecondary)
                HStack(spacing: 2) {
                    TextField("0", text: $rateText)
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(Aero.text)
                        .keyboardType(.decimalPad)
                    Text(mode == .macros ? "g" : "%")
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
                Text(resultFieldLabel)
                    .font(.system(size: 10, weight: .medium))
                    .foregroundStyle(Aero.textSecondary)
                Text(formattedResultText)
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
    }

    @ViewBuilder
    private var calcHorizonRow: some View {
        HStack {
            Text(mode == .loan ? "Loan Term" : "Compounding Horizon")
                .font(.system(size: 10, weight: .medium))
                .foregroundStyle(Aero.textSecondary)
            Spacer()
            HStack(spacing: 4) {
                ForEach(horizonOptions, id: \.self) { (yr: Int) in
                    let active = years == yr
                    Button {
                        GSHaptics.select()
                        years = yr
                    } label: {
                        Text("\(yr)Y")
                            .font(.system(size: 10, weight: .bold))
                            .foregroundStyle(active ? Color.white : Color(red: 0.357, green: 0.239, blue: 0.961))
                            .padding(.horizontal, 6)
                            .padding(.vertical, 2)
                            .background(
                                RoundedRectangle(cornerRadius: 6)
                                    .fill(active ? Color(red: 0.424, green: 0.298, blue: 0.965) : Color(red: 0.941, green: 0.918, blue: 1.0))
                            )
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }

    @ViewBuilder
    private var calcSliderSection: some View {
        let maxR = sliderMax
        let stepR = sliderStep
        let useDec = (mode == .macros || mode == .loan)
        VStack(alignment: .leading, spacing: 4) {
            Slider(
                value: Binding(
                    get: { rateVal.isFinite ? min(max(0.0, rateVal), maxR) : 0 },
                    set: { (newVal: Double) in
                        rateText = useDec ? String(format: "%.1f", newVal) : String(format: "%.0f", newVal)
                    }
                ),
                in: 0.0...maxR,
                step: stepR
            )
            .tint(Color(red: 0.424, green: 0.298, blue: 0.965))
            .disabled(!rateVal.isFinite)

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
                Text(summaryBadgeText)
                    .font(.system(size: 10.5, weight: .semibold))
                    .foregroundStyle(Color(red: 0.071, green: 0.604, blue: 0.353))
            }
        }
    }
}

private struct SpontaneousMetricItemIOS {
    let label: String
    let value: Double
    let unit: String
    let icon: String
    let tone: NexlyGlassTone
}

private func extractSpontaneousMetricsFromBlocksIOS(_ blocks: [Block]) -> [SpontaneousMetricItemIOS] {
    var out: [SpontaneousMetricItemIOS] = []
    var seen = Set<String>()
    guard let rx = try? NSRegularExpression(pattern: "^([A-Za-z][A-Za-z0-9\\s/+.#&-]{1,28})\\s*(?:—|–|-|:)\\s*([$€£¥])?\\s*(\\d{1,3}(?:,\\d{3})+(?:\\.\\d+)?|\\d+(?:\\.\\d+)?)\\s*([kKmMbB%])?\\s*$") else {
        return []
    }
    for b in blocks {
        let items: [ListItem]
        switch b {
        case .bulletList(let list, _):
            items = list.allSatisfy({ $0.checked == nil }) ? list : []
        case .orderedList(_, let list, _):
            items = list
        default:
            items = []
        }
        for li in items {
            let plain = NexlyIcons.cleanPlainText(plainTextOfSpans(li.spans))
            let ns = plain as NSString
            guard let m = rx.firstMatch(in: plain, range: NSRange(location: 0, length: ns.length)) else { continue }
            let label = ns.substring(with: m.range(at: 1)).trimmingCharacters(in: .whitespacesAndNewlines)
            let curr = m.range(at: 2).location != NSNotFound ? ns.substring(with: m.range(at: 2)) : ""
            let numStr = ns.substring(with: m.range(at: 3)).replacingOccurrences(of: ",", with: "")
            let suf = m.range(at: 4).location != NSNotFound ? ns.substring(with: m.range(at: 4)) : ""
            guard let num = Double(numStr), num > 0, !(num >= 1990 && num <= 2035) else { continue }
            let key = label.lowercased()
            if seen.contains(key) { continue }
            seen.insert(key)
            let mult: Double = {
                switch suf.lowercased() {
                case "k": return 1_000.0
                case "m": return 1_000_000.0
                case "b": return 1_000_000_000.0
                default: return 1.0
                }
            }()
            let unit = !curr.isEmpty ? curr : (suf == "%" ? "%" : "")
            let sem = NexlyIcons.semanticIcon(for: label, fallback: "chart-bar")
            out.append(SpontaneousMetricItemIOS(label: label, value: num * mult, unit: unit, icon: sem.icon, tone: sem.tone))
            if out.count >= 8 { break }
        }
    }
    if Set(out.map { $0.unit }).count != 1 || out.contains(where: { !["$", "€", "£", "¥"].contains($0.unit) }) { return [] }
    return out
}

private struct NexlyQuantitativeExplorerCardIOS: View {
    let items: [SpontaneousMetricItemIOS]
    @State private var scale: Double = 1.0
    @State private var sortDesc: Bool = true
    @State private var selectedIdx: Int = 0

    private var sorted: [SpontaneousMetricItemIOS] {
        sortDesc ? items.sorted(by: { $0.value > $1.value }) : items
    }

    private func fmtVal(_ v: Double, unit: String) -> String {
        let prefix = ["$", "€", "£", "¥"].contains(unit) ? unit : ""
        let suffix = unit == "%" ? "%" : ""
        if v >= 1_000_000 { return String(format: "%@%.1fM%@", prefix, v / 1_000_000.0, suffix) }
        if v >= 1_000 { return String(format: "%@%.1fK%@", prefix, v / 1_000.0, suffix) }
        if v.truncatingRemainder(dividingBy: 1.0) == 0 { return String(format: "%@%.0f%@", prefix, v, suffix) }
        return String(format: "%@%.1f%@", prefix, v, suffix)
    }

    var body: some View {
        let rows = sorted
        let maxVal = max(0.0001, (rows.map { $0.value * scale }.max() ?? 1.0))
        let totalVal = max(0.0001, rows.reduce(0.0) { $0 + $1.value * scale })
        let safeIdx = min(max(0, selectedIdx), max(0, rows.count - 1))
        let activeItem = rows[safeIdx]
        let unit = activeItem.unit

        VStack(alignment: .leading, spacing: 8) {
            HStack {
                HStack(spacing: 7) {
                    NexlyGlassGlyphTile(name: "chart-bar", tone: .lavender, size: 25, glyphSize: 14)
                    Text("Interactive Data Explorer")
                        .font(.system(size: 13, weight: .bold))
                        .foregroundStyle(Color(red: 0.129, green: 0.094, blue: 0.271))
                }
                Spacer()
                HStack(spacing: 4) {
                    ForEach([0.5, 1.0, 1.5, 2.0], id: \.self) { (mult: Double) in
                        let isSel = abs(scale - mult) < 0.05
                        Button {
                            GSHaptics.select()
                            scale = mult
                        } label: {
                            Text(mult == 1.0 ? "1×" : (mult == 2.0 ? "2×" : String(format: "%.1f×", mult)))
                                .font(.system(size: 10, weight: .bold))
                                .foregroundStyle(isSel ? Color.white : Color(red: 0.310, green: 0.208, blue: 0.910))
                                .padding(.horizontal, 6)
                                .padding(.vertical, 2)
                                .background(
                                    RoundedRectangle(cornerRadius: 6)
                                        .fill(isSel ? Color(red: 0.424, green: 0.298, blue: 0.965) : Color(red: 0.953, green: 0.933, blue: 1.0))
                                )
                        }
                        .buttonStyle(.plain)
                    }
                    Button {
                        GSHaptics.select()
                        sortDesc.toggle()
                    } label: {
                        Text(sortDesc ? "Rank ↓" : "Original")
                            .font(.system(size: 10, weight: .bold))
                            .foregroundStyle(Color(red: 0.357, green: 0.239, blue: 0.961))
                            .padding(.horizontal, 6)
                            .padding(.vertical, 2)
                            .background(RoundedRectangle(cornerRadius: 6).fill(Color(red: 0.929, green: 0.898, blue: 1.0)))
                    }
                    .buttonStyle(.plain)
                }
            }

            ForEach(Array(rows.enumerated()), id: \.offset) { idx, item in
                let scaled = item.value * scale
                let sharePct = (scaled / totalVal) * 100.0
                let isSel = idx == safeIdx
                Button {
                    GSHaptics.select()
                    selectedIdx = idx
                } label: {
                    VStack(alignment: .leading, spacing: 3) {
                        HStack {
                            NexlyGlassGlyphTile(name: item.icon, tone: item.tone, size: 19, glyphSize: 11)
                            Text(item.label)
                                .font(.system(size: 12, weight: isSel ? .bold : .semibold))
                                .foregroundStyle(Color(red: 0.129, green: 0.094, blue: 0.271))
                                .lineLimit(1)
                            Spacer()
                            Text(String(format: "%@ (%.0f%%)", fmtVal(scaled, unit: unit), sharePct))
                                .font(.system(size: 11.5, weight: .bold))
                                .foregroundStyle(isSel ? Color(red: 0.357, green: 0.239, blue: 0.961) : Aero.textSecondary)
                        }
                        GeometryReader { geo in
                            ZStack(alignment: .leading) {
                                Capsule().fill(Color(red: 0.918, green: 0.890, blue: 0.988))
                                Capsule()
                                    .fill(isSel ? Color(red: 0.357, green: 0.239, blue: 0.961) : Color(red: 0.549, green: 0.447, blue: 0.973))
                                    .frame(width: max(8, geo.size.width * CGFloat(min(1.0, scaled / maxVal))))
                            }
                        }
                        .frame(height: 6)
                    }
                    .padding(.horizontal, 6)
                    .padding(.vertical, 4)
                    .background(RoundedRectangle(cornerRadius: 9).fill(isSel ? Color(red: 0.953, green: 0.933, blue: 1.0) : Color.clear))
                }
                .buttonStyle(.plain)
            }

            HStack {
                Text("Selected: \(activeItem.label) (\(fmtVal(activeItem.value * scale, unit: unit)))")
                    .font(.system(size: 11, weight: .semibold))
                    .foregroundStyle(Color(red: 0.129, green: 0.094, blue: 0.271))
                Spacer()
                Text("Total: \(fmtVal(totalVal, unit: unit))")
                    .font(.system(size: 11, weight: .bold))
                    .foregroundStyle(Color(red: 0.357, green: 0.239, blue: 0.961))
            }
            .padding(.horizontal, 9)
            .padding(.vertical, 6)
            .background(RoundedRectangle(cornerRadius: 9).fill(Color(red: 0.965, green: 0.949, blue: 1.0)))
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 10)
        .background(RoundedRectangle(cornerRadius: 14).fill(Color.white.opacity(0.80)))
        .overlay(RoundedRectangle(cornerRadius: 14).stroke(Color(red: 0.886, green: 0.855, blue: 0.980), lineWidth: 1))
    }
}

struct SpontaneousConceptItemIOS {
    let term: String
    let definition: String
    let icon: String
    let tone: NexlyGlassTone
}

func extractSpontaneousConceptsFromBlocksIOS(_ blocks: [Block]) -> [SpontaneousConceptItemIOS] {
    var out: [SpontaneousConceptItemIOS] = []
    var seen = Set<String>()
    let sepRx = try? NSRegularExpression(pattern: "^([^:—–-]{3,42})\\s*(?::|—|–|-)\\s*(.{14,})$")

    func appendConcept(termRaw: String, defRaw: String) {
        let term = NexlyIcons.cleanPlainText(termRaw).trimmingCharacters(in: CharacterSet(charactersIn: ":-—– "))
        let def = NexlyIcons.cleanPlainText(defRaw).trimmingCharacters(in: CharacterSet(charactersIn: ":-—– "))
        guard term.count >= 3, term.count <= 44, (14...600).contains(def.count), !def.lowercased().contains("http") else { return }
        let key = term.lowercased()
        guard !seen.contains(key), !out.contains(where: { $0.definition.lowercased() == def.lowercased() }) else { return }
        seen.insert(key)
        let sem = NexlyIcons.semanticIcon(for: "\(term) \(def)", fallback: "bulb")
        out.append(SpontaneousConceptItemIOS(term: term, definition: def, icon: sem.icon, tone: sem.tone))
    }

    var excludedSection = false
    for (idx, b) in blocks.enumerated() {
        if case .heading(_, let spans, _) = b {
            let title = NexlyIcons.cleanPlainText(plainTextOfSpans(spans))
            let section = classifySectionListMode(title)
            excludedSection = section == .sources || section == .todo || title.range(of: "\\b(?:references|bibliography|steps?|plan|instructions|procedure)\\b", options: [.regularExpression, .caseInsensitive]) != nil
        }
        // A useful learning interaction needs actual definitions, not a quiz
        // fabricated from citation records or an action plan.
        if excludedSection { continue }
        let rawItems: [ListItem]
        switch b {
        case .bulletList(let items, _):
            rawItems = items.allSatisfy({ $0.checked == nil }) ? items : []
        case .orderedList(_, let items, _):
            rawItems = items
        default:
            rawItems = []
        }
        for li in rawItems {
            if let first = li.spans.first, case .bold(let bch) = first {
                let term = plainTextOfSpans(bch)
                let rest = plainTextOfSpans(Array(li.spans.dropFirst()))
                appendConcept(termRaw: term, defRaw: rest)
            } else {
                let plain = NexlyIcons.cleanPlainText(plainTextOfSpans(li.spans))
                if let rx = sepRx,
                   let m = rx.firstMatch(in: plain, range: NSRange(plain.startIndex..., in: plain)),
                   let r1 = Range(m.range(at: 1), in: plain),
                   let r2 = Range(m.range(at: 2), in: plain) {
                    appendConcept(termRaw: String(plain[r1]), defRaw: String(plain[r2]))
                }
            }
            if out.count >= 8 { return out }
        }
        if case .heading(let lvl, let hSpans, _) = b, (2...3).contains(lvl), idx + 1 < blocks.count {
            let hText = NexlyIcons.cleanPlainText(plainTextOfSpans(hSpans))
            if case .paragraph(let pSpans, _) = blocks[idx + 1],
               classifyCalloutHeaderTitle(hText) == nil,
               classifySectionListMode(hText) == .normal {
                appendConcept(termRaw: hText, defRaw: plainTextOfSpans(pSpans))
                if out.count >= 8 { return out }
            }
        }
    }
    return out
}

private struct NexlyInteractiveConceptDeckCardIOS: View {
    let concepts: [SpontaneousConceptItemIOS]
    @State private var quizMode: Bool = false
    @State private var cardIdx: Int = 0
    @State private var flipped: Bool = false
    @State private var masteredSet: Set<Int> = []
    @State private var quizIdx: Int = 0
    @State private var quizSelected: Int? = nil
    @State private var quizScore: Int = 0
    @State private var quizAnsweredCount: Int = 0

    private var safeCardIdx: Int {
        min(max(0, cardIdx), max(0, concepts.count - 1))
    }

    private var safeQuizIdx: Int {
        min(max(0, quizIdx), max(0, concepts.count - 1))
    }

    private var quizOptions: [Int] {
        let target = safeQuizIdx
        let distractors = Array(concepts.indices.filter { $0 != target }.prefix(3))
        return ([target] + distractors).sorted { (($0 * 7 + target * 3) % 11) < (($1 * 7 + target * 3) % 11) }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            conceptHeaderRow
            if !quizMode {
                conceptFlashcardBody
            } else {
                conceptQuizBody
            }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 10)
        .background(RoundedRectangle(cornerRadius: 14).fill(Color.white.opacity(0.82)))
        .overlay(RoundedRectangle(cornerRadius: 14).stroke(Color(red: 0.886, green: 0.855, blue: 0.980), lineWidth: 1))
    }

    @ViewBuilder
    private var conceptHeaderRow: some View {
        HStack {
            HStack(spacing: 7) {
                NexlyGlassGlyphTile(
                    name: quizMode ? "school" : "cards",
                    tone: .lavender,
                    size: 25,
                    glyphSize: 14
                )
                Text(quizMode ? "Interactive Self-Test" : "Interactive Concept Deck")
                    .font(.system(size: 13, weight: .bold))
                    .foregroundStyle(Color(red: 0.129, green: 0.094, blue: 0.271))
            }
            Spacer()
            HStack(spacing: 2) {
                ForEach([false, true], id: \.self) { (isQuiz: Bool) in
                    let active = quizMode == isQuiz
                    Button {
                        GSHaptics.select()
                        quizMode = isQuiz
                    } label: {
                        Text(isQuiz ? "Quick Quiz" : "Flashcards")
                            .font(.system(size: 10.5, weight: .bold))
                            .foregroundStyle(active ? Color.white : Color(red: 0.310, green: 0.208, blue: 0.910))
                            .padding(.horizontal, 8)
                            .padding(.vertical, 3)
                            .background(RoundedRectangle(cornerRadius: 6).fill(active ? Color(red: 0.424, green: 0.298, blue: 0.965) : Color.clear))
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(2)
            .background(RoundedRectangle(cornerRadius: 8).fill(Color(red: 0.929, green: 0.898, blue: 1.0)))
        }
    }

    @ViewBuilder
    private var conceptFlashcardBody: some View {
        let current = concepts[safeCardIdx]
        let isMastered = masteredSet.contains(safeCardIdx)
        Button {
            GSHaptics.select()
            flipped.toggle()
        } label: {
            VStack(alignment: .leading, spacing: 6) {
                HStack {
                    HStack(spacing: 6) {
                        NexlyGlassGlyphTile(
                            name: current.icon,
                            tone: current.tone,
                            size: 20,
                            glyphSize: 12
                        )
                        Text(flipped ? "DEFINITION · TAP TO FLIP" : "CONCEPT \(safeCardIdx + 1)/\(concepts.count) · TAP TO FLIP")
                            .font(.system(size: 10, weight: .bold))
                            .foregroundStyle(Color(red: 0.357, green: 0.239, blue: 0.961))
                    }
                    Spacer()
                    if isMastered {
                        HStack(spacing: 3) {
                            NexlyGlyph(name: "circle-check", size: 12, color: Color(red: 0.071, green: 0.604, blue: 0.353))
                            Text("Mastered")
                                .font(.system(size: 10, weight: .bold))
                                .foregroundStyle(Color(red: 0.071, green: 0.604, blue: 0.353))
                        }
                    }
                }
                Text(flipped ? current.definition : current.term)
                    .font(.system(size: flipped ? 12.5 : 14, weight: flipped ? .medium : .bold))
                    .foregroundStyle(Color(red: 0.129, green: 0.094, blue: 0.271))
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 11)
            .background(RoundedRectangle(cornerRadius: 11).fill(flipped ? Color(red: 0.929, green: 0.898, blue: 1.0) : Color(red: 0.965, green: 0.949, blue: 1.0)))
            .overlay(RoundedRectangle(cornerRadius: 11).stroke(Color(red: 0.851, green: 0.808, blue: 0.980), lineWidth: 1))
        }
        .buttonStyle(.plain)

        HStack {
            HStack(spacing: 6) {
                Button("Prev") {
                    GSHaptics.select()
                    cardIdx = (safeCardIdx - 1 + concepts.count) % concepts.count
                    flipped = false
                }
                .font(.system(size: 11, weight: .semibold))
                .foregroundStyle(Color(red: 0.310, green: 0.208, blue: 0.910))
                .padding(.horizontal, 9)
                .padding(.vertical, 4)
                .background(RoundedRectangle(cornerRadius: 7).fill(Color(red: 0.953, green: 0.933, blue: 1.0)))
                .buttonStyle(.plain)

                Button("Next") {
                    GSHaptics.select()
                    cardIdx = (safeCardIdx + 1) % concepts.count
                    flipped = false
                }
                .font(.system(size: 11, weight: .semibold))
                .foregroundStyle(Color(red: 0.310, green: 0.208, blue: 0.910))
                .padding(.horizontal, 9)
                .padding(.vertical, 4)
                .background(RoundedRectangle(cornerRadius: 7).fill(Color(red: 0.953, green: 0.933, blue: 1.0)))
                .buttonStyle(.plain)
            }
            Spacer()
            Button {
                GSHaptics.select()
                if isMastered { masteredSet.remove(safeCardIdx) } else { masteredSet.insert(safeCardIdx) }
            } label: {
                HStack(spacing: 4) {
                    NexlyGlyph(
                        name: isMastered ? "circle-check" : "sparkles",
                        size: 12,
                        color: isMastered ? Color(red: 0.071, green: 0.604, blue: 0.353) : Color(red: 0.357, green: 0.239, blue: 0.961)
                    )
                    Text("\(masteredSet.count)/\(concepts.count) Mastered")
                        .font(.system(size: 10.5, weight: .bold))
                        .foregroundStyle(isMastered ? Color(red: 0.071, green: 0.604, blue: 0.353) : Color(red: 0.357, green: 0.239, blue: 0.961))
                }
                .padding(.horizontal, 9)
                .padding(.vertical, 4)
                .background(Capsule().fill(isMastered ? Color(red: 0.902, green: 0.976, blue: 0.941) : Color(red: 0.929, green: 0.898, blue: 1.0)))
            }
            .buttonStyle(.plain)
        }
    }

    @ViewBuilder
    private var conceptQuizBody: some View {
        let target = concepts[safeQuizIdx]
        Text(target.definition)
            .font(.system(size: 12.5, weight: .medium))
            .foregroundStyle(Color(red: 0.129, green: 0.094, blue: 0.271))
            .padding(.horizontal, 10)
            .padding(.vertical, 8)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(RoundedRectangle(cornerRadius: 10).fill(Color(red: 0.965, green: 0.949, blue: 1.0)))

        VStack(alignment: .leading, spacing: 5) {
            ForEach(quizOptions, id: \.self) { (optIdx: Int) in
                conceptQuizOptionRow(optIdx: optIdx)
            }
        }

        HStack {
            Text("Score: \(quizScore) / \(max(1, quizAnsweredCount))")
                .font(.system(size: 11, weight: .bold))
                .foregroundStyle(Color(red: 0.357, green: 0.239, blue: 0.961))
            Spacer()
            if quizSelected != nil {
                Button("Next Question") {
                    GSHaptics.select()
                    quizSelected = nil
                    quizIdx = (safeQuizIdx + 1) % concepts.count
                }
                .font(.system(size: 11, weight: .bold))
                .foregroundStyle(.white)
                .padding(.horizontal, 10)
                .padding(.vertical, 4)
                .background(RoundedRectangle(cornerRadius: 7).fill(Color(red: 0.424, green: 0.298, blue: 0.965)))
                .buttonStyle(.plain)
            }
        }
    }

    @ViewBuilder
    private func conceptQuizOptionRow(optIdx: Int) -> some View {
        let optConcept = concepts[optIdx]
        let isChosen = quizSelected == optIdx
        let isCorrect = optIdx == safeQuizIdx
        let revealed = quizSelected != nil
        let bg: Color = {
            if revealed && isCorrect { return Color(red: 0.902, green: 0.976, blue: 0.941) }
            if revealed && isChosen { return Color(red: 0.992, green: 0.925, blue: 0.925) }
            return Color(red: 0.953, green: 0.933, blue: 1.0)
        }()
        let borderCol: Color = {
            if revealed && isCorrect { return Color(red: 0.071, green: 0.604, blue: 0.353) }
            if revealed && isChosen { return Color(red: 0.878, green: 0.310, blue: 0.373) }
            return Color(red: 0.851, green: 0.808, blue: 0.980)
        }()
        Button {
            guard !revealed else { return }
            GSHaptics.select()
            quizSelected = optIdx
            quizAnsweredCount += 1
            if isCorrect { quizScore += 1 }
        } label: {
            HStack {
                HStack(spacing: 6) {
                    NexlyGlassGlyphTile(
                        name: optConcept.icon,
                        tone: optConcept.tone,
                        size: 18,
                        glyphSize: 10
                    )
                    Text(optConcept.term)
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundStyle(Color(red: 0.129, green: 0.094, blue: 0.271))
                }
                Spacer()
                if revealed && (isCorrect || isChosen) {
                    NexlyGlyph(
                        name: isCorrect ? "circle-check" : "circle-x",
                        size: 14,
                        color: isCorrect ? Color(red: 0.071, green: 0.604, blue: 0.353) : Color(red: 0.878, green: 0.310, blue: 0.373)
                    )
                }
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 7)
            .background(RoundedRectangle(cornerRadius: 8).fill(bg))
            .overlay(RoundedRectangle(cornerRadius: 8).stroke(borderCol, lineWidth: 1))
        }
        .buttonStyle(.plain)
    }
}

func sourceCardForHeadingIOS(_ heading: Block, following: Block?) -> (citations: [CitationEntry], title: String)? {
    guard case .heading(_, let spans, _) = heading, let following else { return nil }
    let title = NexlyIcons.cleanPlainText(plainTextOfSpans(spans)).trimmingCharacters(in: .whitespacesAndNewlines)
    guard classifySectionListMode(title) == .sources else { return nil }
    if case .citations(let citations, _) = following { return (citations, title) }
    let items: [ListItem]
    let start: Int
    switch following {
    case .orderedList(let number, let list, _): items = list; start = number
    case .bulletList(let list, _): items = list; start = 1
    default: return nil
    }
    let entries = items.enumerated().compactMap { index, item in
        extractSourceEntryFromItem(number: item.number ?? (start + index), item: item)
    }
    guard !entries.isEmpty, entries.count == items.count else { return nil }
    return (entries, title)
}

private func extractSourceEntryFromItem(number: Int, item: ListItem) -> CitationEntry? {
    var combinedSpans = item.spans
    for child in item.children {
        if case .paragraph(let cSpans, _) = child {
            combinedSpans.append(.text(" "))
            combinedSpans.append(contentsOf: cSpans)
        }
    }
    return extractSourceEntryFromSpans(number: number, spans: combinedSpans)
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
    let urlPattern = "https?://[^\\s)\\]]+|www\\.[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}|\\b[a-zA-Z0-9-]+\\.(?:org|com|io|dev|edu|gov|net)\\b"
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
                let leadingIcon = NexlyIcons.extractLeadingIcon(plainTextOfSpans(spans)).0
                let displaySpans = leadingIcon != nil ? stripLeadingIconFromSpans(spans) : spans
                if let leadingIcon {
                    HStack(alignment: .top, spacing: 8) {
                        NexlyGlassGlyphTile(
                            name: leadingIcon,
                            tone: NexlyIcons.tone(for: leadingIcon),
                            size: 21,
                            glyphSize: 12
                        )
                        .padding(.top, 1)
                        Text(attributed(from: displaySpans, theme: theme))
                            .font(compact ? .system(size: 13, weight: .regular) : Aero.body())
                            .lineSpacing(compact ? 2 : 0)
                            .foregroundStyle(compact ? NexlyInk.prose : Aero.text)
                            .textSelection(.enabled)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                } else {
                    Text(attributed(from: displaySpans, theme: theme))
                        .font(compact ? .system(size: 13, weight: .regular) : Aero.body())
                        .lineSpacing(compact ? 2 : 0)
                        .foregroundStyle(compact ? NexlyInk.prose : Aero.text)
                        .textSelection(.enabled)
                            .fixedSize(horizontal: false, vertical: true)
                }
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
        let rawText = plainTextOfSpans(spans)
        let badge: (icon: String, tone: NexlyGlassTone)? = {
            if level >= 2 && level <= 3 {
                return sectionBadgeForHeadingText(rawText)
            }
            if let explicit = NexlyIcons.extractLeadingIcon(rawText).0 {
                return (explicit, .lavender)
            }
            return nil
        }()
        let cleanedSpans = stripLeadingIconFromSpans(spans)
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
            extractSourceEntryFromItem(number: idx + 1, item: item)
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
    @State private var completedSet: Set<Int> = []

    private var extractedSources: [CitationEntry] {
        let entries = items.enumerated().compactMap { idx, item in
            extractSourceEntryFromItem(
                number: item.number ?? (start + idx),
                item: item
            )
        }
        if !entries.isEmpty && entries.count == items.count {
            return entries
        }
        return []
    }

    var body: some View {
        let sources = extractedSources
        if !sources.isEmpty {
            CitationsBlockView(citations: sources)
        } else if sectionMode == .sources {
            VStack(alignment: .leading, spacing: 8) {
                ForEach(Array(items.enumerated()), id: \.offset) { index, item in
                    HStack(alignment: .top, spacing: 8) {
                        Text("\(item.number ?? (start + index)).").font(.system(size: 12, weight: .medium))
                        VStack(alignment: .leading, spacing: 4) {
                            ParagraphView(spans: item.spans, citations: citations)
                            ForEach(Array(item.children.enumerated()), id: \.offset) { _, child in
                                NestedChildView(block: child, citations: citations)
                            }
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                    }
                }
            }
        } else {
            VStack(alignment: .leading, spacing: 8) {
                if !completedSet.isEmpty && items.count >= 2 {
                    HStack {
                        Text("\(completedSet.count) of \(items.count) steps completed")
                            .font(.system(size: 11, weight: .semibold))
                            .foregroundStyle(Color(red: 0.357, green: 0.239, blue: 0.961))
                        Spacer()
                        Text("\(Int(round(Double(completedSet.count) / Double(max(1, items.count)) * 100)))%")
                            .font(.system(size: 11, weight: .bold))
                            .foregroundStyle(
                                completedSet.count == items.count
                                    ? Color(red: 0.071, green: 0.604, blue: 0.353)
                                    : Color(red: 0.357, green: 0.239, blue: 0.961)
                            )
                    }
                    .padding(.horizontal, 9)
                    .padding(.vertical, 5)
                    .background(RoundedRectangle(cornerRadius: 8).fill(Color(red: 0.953, green: 0.933, blue: 1.0)))
                }
                ForEach(Array(items.enumerated()), id: \.offset) { index, item in
                    let number = item.number ?? (start + index)
                    StepOrderedListItemRowView(
                        number: number,
                        item: item,
                        citations: citations,
                        isCompleted: completedSet.contains(index),
                        onToggle: {
                            if completedSet.contains(index) {
                                completedSet.remove(index)
                            } else {
                                completedSet.insert(index)
                            }
                        }
                    )
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
    var isCompleted: Bool = false
    var onToggle: (() -> Void)? = nil
    @State private var localCompleted: Bool = false

    private var completed: Bool {
        onToggle != nil ? isCompleted : localCompleted
    }

    private var splitStep: (icon: String?, title: String, detailSpans: [InlineSpan], consumedFirstChild: Bool)? {
        guard let first = item.spans.first, case .bold(let titleCh) = first else { return nil }
        let rawTitle = plainTextOfSpans(titleCh)
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .trimmingCharacters(in: CharacterSet(charactersIn: ":"))
        let (leadIcon, strippedTitle) = NexlyIcons.extractLeadingIcon(rawTitle)
        let cleanTitle = NexlyIcons.cleanPlainText(strippedTitle.isEmpty ? rawTitle : strippedTitle)
            .trimmingCharacters(in: .whitespacesAndNewlines)
        let title = cleanTitle
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
                return (leadIcon, title, rest, false)
            }
        }
        if let firstChild = item.children.first, case .paragraph(let childSpans, _) = firstChild, !childSpans.isEmpty {
            return (leadIcon, title, childSpans, true)
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
                if let onToggle { onToggle() } else { localCompleted.toggle() }
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
                            HStack(spacing: 5) {
                                if let stepIcon = split.icon {
                                    NexlyGlassGlyphTile(name: stepIcon, tone: .lavender, size: 18, glyphSize: 11)
                                }
                                Text(split.title)
                                    .font(.system(size: compact ? 13.5 : 15, weight: .bold))
                                    .strikethrough(completed)
                                    .foregroundStyle(completed ? Aero.textSecondary : (compact ? NexlyInk.prose : Aero.text))
                            }
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
        let semanticMatchIcon: String? = {
            guard extractedIcon == nil else { return nil }
            let sem = NexlyIcons.semanticIcon(for: rawText, fallback: "")
            return sem.icon.isEmpty || sem.icon == "sparkles" ? nil : sem.icon
        }()
        let leadingIcon = extractedIcon ?? semanticMatchIcon ?? (marker == "✓" ? "circle-check" : nil)
        let displaySpans = extractedIcon != nil ? stripLeadingIconFromSpans(item.spans) : item.spans
        VStack(alignment: .leading, spacing: 4) {
            HStack(alignment: leadingIcon != nil ? .top : .firstTextBaseline, spacing: 8) {
                if let leadingIcon {
                    NexlyGlassGlyphTile(
                        name: leadingIcon,
                        tone: marker == "✓" ? .green : NexlyIcons.tone(for: leadingIcon),
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

struct CodeBlockCardView: View {
    @Environment(\.nexlyCodeWrap) private var wrapCode
    @Environment(\.responseCodeActions) private var responseActions
    @Environment(\.reactPreviewScope) private var reactScope
    @State private var localSourceID = UUID()
    @FocusState private var sourceFocused: Bool
    let language: String?
    let code: String
    var open: Bool = false
    var sourceStart: Int? = nil
    private var sourceID: AnyHashable { sourceStart.map(AnyHashable.init) ?? AnyHashable(localSourceID) }

    @State private var workerDocument: String? = nil
    @State private var browserDocument: String? = nil
    @State private var editing = false
    @State private var editorDraft = ""
    @State private var editorEncoded = false
    @State private var editorError: String? = nil
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

    private var supportsExecution: Bool {
        ["html", "htm", "svg", "js", "javascript", "node", "json", "jsx", "tsx", "react"].contains(lang)
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
            if CodeDisplayIOS.requiresEncoding(currentCode) {
                Text("Unicode symbols are shown as escapes; Copy and Run keep original source.").font(.caption).foregroundStyle(Color.white.opacity(0.8)).padding(8)
            }
            if editing && editorEncoded {
                Text("Unicode-safe editor: edit one JSON-encoded source string.").font(.caption).foregroundStyle(Color.white.opacity(0.8)).padding(8)
            }
            if let editorError { Text(editorError).font(.caption).foregroundStyle(Color.red).padding(8) }
            codeEditorSurface
            codeExecutionBar
            codeOutputDrawer
            codeSeriesPreview
            browserPreviewSection
        }
        .background(RoundedRectangle(cornerRadius: 14).fill(Color(red: 0.133, green: 0.122, blue: 0.231)))
        .clipShape(RoundedRectangle(cornerRadius: 14))
        .overlay(RoundedRectangle(cornerRadius: 14).stroke(Color(red: 0.212, green: 0.192, blue: 0.361), lineWidth: 1))
        .onAppear {
            if !open {
                let restored = responseActions?.source(sourceID)?.1 ?? code
                if restored != code { editedCode = restored }
                registerResponseCode(source: restored)
            }
        }
        .onDisappear { responseActions?.remove(sourceID) }
        .onChange(of: currentCode) { source in responseActions?.update(sourceID, source: source) }
        .onChange(of: open) { value in if !value { registerResponseCode(source: currentCode) } }
        .onChange(of: code) { _ in
            resetCodeState()
            if !open { registerResponseCode(source: code) }
        }
        .onChange(of: language) { _ in
            resetCodeState()
            if !open { registerResponseCode(source: code) }
        }
    }

    private func registerResponseCode(source: String) {
        responseActions?.register(sourceID, entry: ResponseCodeActionsIOS.Entry(language: language, source: source, edit: {
            editorEncoded = CodeDisplayIOS.requiresEncoding(currentCode)
            editorDraft = editorEncoded ? CodeDisplayIOS.encodeEditor(currentCode) : currentCode
            editorError = nil; editing = true
            DispatchQueue.main.async { sourceFocused = true }
        }))
    }

    @ViewBuilder
    private var codeHeaderBar: some View {
        HStack(spacing: 8) {
            NexlyGlyph(
                name: "code",
                size: 14,
                color: Color(red: 0.620, green: 0.525, blue: 1.0)
            )
            Text(NexlyIcons.cleanPlainText((language ?? "code").capitalized))
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
                    if editing && editorError != nil { return }
                    if !editing {
                        editorEncoded = CodeDisplayIOS.requiresEncoding(currentCode)
                        editorDraft = editorEncoded ? CodeDisplayIOS.encodeEditor(currentCode) : currentCode
                        editorError = nil
                    }
                    editing.toggle()
                }
                .font(.system(size: 11, weight: .semibold))
                .foregroundStyle(Color(red: 0.773, green: 0.737, blue: 1.0))
                .padding(.horizontal, 7)
                .padding(.vertical, 3)
                .background(RoundedRectangle(cornerRadius: 6).fill(Color.white.opacity(0.08)))
                .buttonStyle(.plain)
            }
            if currentCode != code || editorError != nil {
                Button("Reset") { resetCodeState() }.buttonStyle(.plain).font(.caption)
            }
            CodeCopyButton(text: currentCode)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 7)
        .background(Color(red: 0.106, green: 0.094, blue: 0.188))
        .accessibilityElement(children: .contain)
        .accessibilityLabel("Code block\(language.map { ", " + $0 } ?? "")")
    }

    @ViewBuilder
    private var codeEditorSurface: some View {
        if currentCode.isEmpty && !editing {
            Text("…")
                .font(.system(size: 12, weight: .regular, design: .monospaced))
                .foregroundStyle(Color(red: 0.925, green: 0.910, blue: 1.0))
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(12)
                .textSelection(.enabled)
        } else if editing {
            TextEditor(text: Binding(
                get: { editorDraft },
                set: { draft in
                    if !editorEncoded && CodeDisplayIOS.requiresEncoding(draft) {
                        editorEncoded = true
                        editorDraft = CodeDisplayIOS.encodeEditor(draft)
                        editedCode = draft
                        editorError = nil
                    } else {
                        editorDraft = CodeDisplayIOS.escape(draft)
                        do {
                            if editorEncoded { editedCode = try CodeDisplayIOS.decodeEditor(draft) }
                            else { editedCode = draft }
                            editorError = nil
                        } catch { editorError = "Enter one valid JSON source string. Invalid drafts cannot run." }
                    }
                }
            ))
            .focused($sourceFocused)
            .font(.system(size: 12, weight: .regular, design: .monospaced))
            .foregroundStyle(Color(red: 0.925, green: 0.910, blue: 1.0))
            .frame(minHeight: 110, maxHeight: 260)
            .padding(.horizontal, 8)
            .padding(.bottom, 8)
        } else {
            if wrapCode {
                ScrollView(.vertical) {
                    codeSurfaceWithLineNumbers
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 10)
                        .textSelection(.enabled)
                }
                .frame(maxHeight: 320)
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
                        Text(!supportsExecution ? "Source only" : isBrowserLang ? "Run in Browser" : (lang == "json" ? "Validate JSON" : "Run Code"))
                            .font(.system(size: 11.5, weight: .bold))
                    }
                    .foregroundStyle(Color.white)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 5)
                    .background(RoundedRectangle(cornerRadius: 8).fill(Color(red: 0.424, green: 0.298, blue: 0.965)))
                }
                .buttonStyle(.plain)
                .disabled(!supportsExecution)
                .opacity(supportsExecution ? 1 : 0.65)

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
                    Text(CodeDisplayIOS.escape(out))
                        .font(.system(size: 11.5, weight: .regular, design: .monospaced))
                        .foregroundStyle(Color(red: 0.847, green: 0.953, blue: 0.863))
                        .frame(maxWidth: .infinity, alignment: .leading)
                }

                if let err = errorOutput ?? (["error", "stderr", "traceback", "exception"].contains(lang) ? currentCode : nil),
                   !err.isEmpty {
                    Text(CodeDisplayIOS.escape(err))
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
        if !open && ["html", "htm", "svg"].contains(lang), let browserDocument {
            HtmlBlockView(kind: lang == "svg" ? "svg" : "html", source: browserDocument, open: false)
                .id(runCount)
                .padding(8)
                .background(Color(red: 0.973, green: 0.961, blue: 1.0))
        } else if !open && ["js", "javascript", "node"].contains(lang), let workerDocument {
            HtmlBlockView(kind: "html", source: workerDocument, open: false)
                .id(runCount)
                .padding(8)
        } else if !open && ["jsx", "tsx", "react"].contains(lang), browserDocument != nil {
            ReactPreviewIOS(source: currentCode, scope: "\(reactScope ?? localSourceID.uuidString):\(sourceStart ?? 0)")
                .id(runCount)
                .frame(height: 420)
                .padding(8)
        } else if !open && !supportsExecution {
            Text("Source only: no \(NexlyIcons.cleanPlainText(lang)) runtime is available in this app. Copy this source into a compatible environment; no execution has occurred here.")
                .font(.system(size: 12))
                .foregroundStyle(Color.white.opacity(0.85))
                .fixedSize(horizontal: false, vertical: true)
                .padding(12)
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
        // A line number belongs to one original source line, even when that
        // line wraps. Separate number/code text columns drifted out of sync.
        VStack(alignment: .leading, spacing: 0) {
            ForEach(Array(displayedCode.components(separatedBy: "\n").enumerated()), id: \.offset) { index, line in
                HStack(alignment: .top, spacing: 8) {
                    Text("\(index + 1)")
                        .font(.system(size: 12, weight: .regular, design: .monospaced))
                        .foregroundStyle(Color(red: 0.431, green: 0.412, blue: 0.588))
                        .frame(width: 22, alignment: .trailing)
                    Group {
                        if open {
                            Text(CodeDisplayIOS.escape(line.isEmpty ? " " : line))
                                .font(.system(size: 12, weight: .regular, design: .monospaced))
                        } else {
                            highlightedCode(CodeDisplayIOS.escape(line.isEmpty ? " " : line), language: language, darkSurface: true)
                        }
                    }
                    .foregroundStyle(Color(red: 0.933, green: 0.922, blue: 1.0))
                    .fixedSize(horizontal: !wrapCode, vertical: true)
                }
            }
        }
    }

    private func resetCodeState() {
        editedCode = nil; editing = false; editorDraft = ""; editorError = nil
        editorEncoded = false; expanded = false; runOutput = nil; errorOutput = nil; runCount = 0; workerDocument = nil; browserDocument = nil
    }

    private func executeCode() {
        if editing && editorError != nil {
            workerDocument = nil; browserDocument = nil; runOutput = nil
            errorOutput = "Not executed: correct the JSON-encoded source draft first."
            return
        }
        runCount += 1
        runOutput = nil
        errorOutput = nil
        workerDocument = nil
        browserDocument = nil
        let src = currentCode.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !src.isEmpty else {
            errorOutput = "Not executed: empty snippet."
            return
        }
        if lang == "json" {
            guard let data = currentCode.data(using: .utf8) else { return }
            do {
                let obj = try JSONSerialization.jsonObject(with: data, options: [.fragmentsAllowed])
                let pretty = try JSONSerialization.data(withJSONObject: obj, options: [.prettyPrinted, .sortedKeys, .fragmentsAllowed])
                runOutput = String(data: pretty, encoding: .utf8)
            } catch {
                errorOutput = error.localizedDescription
            }
            return
        }
        if ["html", "htm", "svg", "jsx", "tsx", "react"].contains(lang) {
            browserDocument = currentCode
            return
        }
        if ["py", "python", "python3"].contains(lang) {
            errorOutput = "Not executed: no Python runtime is available here. Copy this source into a Python environment."
            return
        }
        if ["sql", "sqlite", "postgres", "postgresql", "mysql"].contains(lang) {
            errorOutput = "Not executed: no database is connected. Copy this SQL into your database client."
            return
        }
        if !["js", "javascript", "node"].contains(lang) {
            errorOutput = "Not executed: no \(lang.uppercased()) runtime is available here."
            return
        }
        workerDocument = JavaScriptWorkerSandboxIOS.document(source: currentCode)
        // Execution lives in a disposable Worker in WKWebView, never the app UI thread.
        // Its console, exception, completion and timeout are displayed by that runtime.

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

func parseSortableNumberIOS(_ raw: String) -> Double? {
    let text = raw.trimmingCharacters(in: .whitespacesAndNewlines).replacingOccurrences(of: "−", with: "-")
    guard let regex = try? NSRegularExpression(pattern: "^(?:([↑↓↗↘])\\s*)?([+-]?)\\s*[$€£¥₹৳#]?\\s*([+-]?)\\s*((?:\\d{1,3}(?:,\\d{3})+|\\d+)(?:\\.\\d+)?)\\s*([kKmMbBtT]?)\\s*[%xX]?$"),
          let match = regex.firstMatch(in: text, range: NSRange(text.startIndex..., in: text)) else { return nil }
    func group(_ index: Int) -> String {
        guard let range = Range(match.range(at: index), in: text) else { return "" }
        return String(text[range])
    }
    let arrow = group(1), outer = group(2), inner = group(3)
    guard outer.isEmpty || inner.isEmpty else { return nil }
    let sign = outer.isEmpty ? inner : outer
    let down = arrow == "↓" || arrow == "↘"
    guard arrow.isEmpty || !((down && sign == "+") || (!down && sign == "-")) else { return nil }
    let multipliers: [String: Double] = ["k": 1e3, "m": 1e6, "b": 1e9, "t": 1e12]
    let multiplier: Double = multipliers[group(5).lowercased()] ?? 1.0
    guard let base = Double(group(4).replacingOccurrences(of: ",", with: "")) else { return nil }
    let value = base * multiplier * (sign == "-" || down ? -1 : 1)
    return value.isFinite ? value : nil
}

func chartJsonFromTableIOS(headers: [[InlineSpan]], rows: [[[InlineSpan]]]) -> String? {
    guard rows.count >= 2, headers.count >= 2 else { return nil }
    let headerTexts = headers.map { NexlyIcons.cleanPlainText(plainTextOfSpans($0)).trimmingCharacters(in: .whitespacesAndNewlines) }
    let ranked = ["#", "rank", "no", "id"].contains(headerTexts[0].lowercased()) && headers.count >= 3
    let labelCol = ranked ? 1 : 0
    // Identifiers are categories, never the measure. Incomplete columns do not
    // become partial charts that quietly hide observations with missing data.
    for column in 0..<headers.count where column != labelCol && !(ranked && column == 0) {
        var dataItems: [[String: Any]] = []
        var units = Set<String>()
        for row in rows {
            guard labelCol < row.count, column < row.count else { break }
            let raw = plainTextOfSpans(row[column]).trimmingCharacters(in: .whitespacesAndNewlines)
            let label = NexlyIcons.cleanPlainText(plainTextOfSpans(row[labelCol])).trimmingCharacters(in: .whitespacesAndNewlines)
            guard let value = parseSortableNumberIOS(raw), !label.isEmpty else { break }
            let currency = raw.first(where: { "$€£¥₹৳".contains($0) }).map(String.init) ?? ""
            units.insert(raw.hasSuffix("%") ? "%" : (!currency.isEmpty ? currency : (raw.lowercased().hasSuffix("x") ? "x" : "")))
            dataItems.append(["label": label, "value": value])
        }
        guard dataItems.count == rows.count, units.count == 1 else { continue }
        let payload: [String: Any] = ["type": "bar", "title": headerTexts[column], "unit": units.first ?? "", "data": dataItems]
        guard let data = try? JSONSerialization.data(withJSONObject: payload), let json = String(data: data, encoding: .utf8) else { return nil }
        return json
    }
    return nil
}

private struct TableBlockView: View {
    let headers: [[InlineSpan]]
    let aligns: [TableAlign]
    let rows: [[[InlineSpan]]]
    var citations: [Int: String] = [:]

    @State private var measuredWidth: CGFloat = 0
    @State private var pageSize: Int
    @State private var page: Int = 0
    @State private var sortCol: Int = -1
    @State private var sortAsc: Bool = true
    @State private var showChart: Bool = false

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
        measuredWidth > 0 ? measuredWidth : min(UIScreen.main.bounds.width - 32, Aero.Layout.contentMaxWidth)
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

    private var effectivePageSize: Int {
        widgetTablePageSizeIOS(selection: pageSize, rowCount: sortedRows.count)
    }

    private var totalPages: Int {
        max(1, (sortedRows.count + effectivePageSize - 1) / effectivePageSize)
    }

    private var safePage: Int {
        min(max(0, page), totalPages - 1)
    }

    private var visibleRows: [[[InlineSpan]]] {
        let all = sortedRows
        let start = safePage * effectivePageSize
        guard start < all.count else { return [] }
        let end = min(all.count, start + effectivePageSize)
        return Array(all[start..<end])
    }

    var body: some View {
        let columns = max(headers.count, aligns.count, 1)
        let natural = naturalColumnWidths(columns: columns)
        let naturalTotal = natural.reduce(0, +)
        let extra = max(0, availableWidth - naturalTotal) / CGFloat(columns)
        let widths = natural.map { $0 + extra }
        let tableWidth = widths.reduce(0, +)
        let theme = SpanTheme(
            monoBackground: Aero.container,
            linkColor: Aero.accentDeep,
            textColor: Aero.text,
            baseSize: 13,
            citations: citations
        )
        let startRow = sortedRows.isEmpty ? 0 : safePage * effectivePageSize + 1
        let endRow = min(sortedRows.count, safePage * effectivePageSize + visibleRows.count)
        let chartJson = chartJsonFromTableIOS(headers: headers, rows: rows)
        VStack(alignment: .leading, spacing: 6) {
            if chartJson != nil {
                tableChartToggleBar
            }
            if showChart, let chartJson {
                WidgetBlockView(kind: "chart", json: chartJson, open: false)
            } else {
                VStack(alignment: .leading, spacing: 0) {
                    if tableWidth <= availableWidth {
                        table(theme: theme, widths: widths, columns: columns, scrollable: false)
                    } else {
                        ScrollView(.horizontal, showsIndicators: false) {
                            table(theme: theme, widths: widths, columns: columns, scrollable: true)
                        }
                    }

                    if rows.count >= 4 {
                        tablePaginationFooter(startRow: startRow, endRow: endRow)
                    }
                }
                .background(RoundedRectangle(cornerRadius: 12).fill(Color.white.opacity(0.75)))
                .clipShape(RoundedRectangle(cornerRadius: 12))
                .overlay(RoundedRectangle(cornerRadius: 12).stroke(Aero.outline.opacity(0.4), lineWidth: 1))
            }
        }
        .frame(minWidth: 0, maxWidth: .infinity, alignment: .leading)
        .onGeometryChange(for: CGFloat.self) { $0.size.width } action: { width in
            if width > 0 && abs(width - measuredWidth) > 0.5 { measuredWidth = width }
        }
    }

    private func naturalColumnWidths(columns: Int) -> [CGFloat] {
        let headerFont = UIFont.systemFont(ofSize: 12.5, weight: .bold)
        let firstHeader = headers.first.map { NexlyIcons.cleanPlainText(plainTextOfSpans($0)) } ?? ""
        return (0..<columns).map { column in
            let header = column < headers.count ? NexlyIcons.cleanPlainText(plainTextOfSpans(headers[column])) : ""
            var width = (header as NSString).size(withAttributes: [.font: headerFont]).width + 20
            let isName = column == 0 || (column == 1 && ["#", "Rank", "No", "ID"].contains(firstHeader))
            for row in rows where column < row.count {
                let raw = plainTextOfSpans(row[column]).trimmingCharacters(in: .whitespacesAndNewlines)
                let badge: CGFloat = isName && entityBadge(for: raw) != nil ? 26 : 0
                let trend = raw.hasSuffix("%") && ["+", "-", "↑", "↓", "↗", "↘"].contains(String(raw.prefix(1)))
                // Measure the actual displayed arrow plus value, not the raw
                // arrow AND an extra phantom 16pt icon. Rank cells do not need
                // the 44pt floor reserved for ordinary data columns either.
                let display = trend ? "\((raw.contains("-") || raw.hasPrefix("↓") || raw.hasPrefix("↘")) ? "↓" : "↑") \(raw.replacingOccurrences(of: "^[↑↓↗↘\\s]+", with: "", options: .regularExpression))" : NexlyIcons.cleanPlainText(raw)
                let font = UIFont.systemFont(ofSize: badge > 0 || trend ? 12.5 : 13, weight: trend ? .bold : (badge > 0 ? .semibold : .regular))
                width = max(width, (display as NSString).size(withAttributes: [.font: font]).width + 20 + badge)
            }
            return min(280, max(28, ceil(width)))
        }
    }

    @ViewBuilder
    private var tableChartToggleBar: some View {
        HStack(spacing: 4) {
            ForEach([false, true], id: \.self) { (isChart: Bool) in
                let active = showChart == isChart
                Button {
                    GSHaptics.select()
                    showChart = isChart
                } label: {
                    Text(isChart ? "Chart" : "Table")
                        .font(.system(size: 11, weight: .bold))
                        .foregroundStyle(active ? Color.white : Color(red: 0.357, green: 0.239, blue: 0.961))
                        .padding(.horizontal, 10)
                        .padding(.vertical, 4)
                        .background(
                            Capsule().fill(
                                active
                                    ? Color(red: 0.424, green: 0.298, blue: 0.965)
                                    : Color(red: 0.933, green: 0.910, blue: 1.0)
                            )
                        )
                }
                .buttonStyle(.plain)
            }
            Spacer()
        }
    }

    @ViewBuilder
    private func tablePaginationFooter(startRow: Int, endRow: Int) -> some View {
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
                case 10: pageSize = -1
                default: pageSize = 3
                }
                page = 0
            } label: {
                Text("Show: \(pageSize == -1 ? "All" : String(pageSize)) ▾")
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

    @ViewBuilder
    private func table(theme: SpanTheme, widths: [CGFloat], columns: Int, scrollable: Bool) -> some View {
        let firstHeaderRaw = headers.first.map { NexlyIcons.cleanPlainText(plainTextOfSpans($0)).trimmingCharacters(in: .whitespacesAndNewlines) } ?? ""
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 0) {
                ForEach(0..<columns, id: \.self) { (c: Int) in
                    let hRaw = c < headers.count ? NexlyIcons.cleanPlainText(plainTextOfSpans(headers[c])) : ""
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
                            width: widths[c],
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
                    ForEach(0..<columns, id: \.self) { (c: Int) in
                        let isNameCol = c == 0 || (c == 1 && ["#", "Rank", "No", "ID"].contains(firstHeaderRaw))
                        cell(
                            attributed(from: c < row.count ? row[c] : [], theme: theme),
                            rawString: c < row.count ? plainTextOfSpans(row[c]) : "",
                            width: widths[c],
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
        .frame(width: scrollable ? widths.reduce(0, +) : nil, alignment: .leading)
        .textSelection(.enabled)
    }

    private func entityBadge(for raw: String) -> (icon: String, tone: NexlyGlassTone)? {
        let clean = NexlyIcons.cleanPlainText(raw)
        guard !clean.isEmpty, clean.count <= 32, clean.contains(where: { $0.isLetter }) else { return nil }
        let lower = clean.lowercased()
        if lower.hasPrefix("python") { return ("brand-python", .blue) }
        if lower == "java" { return ("coffee", .amber) }
        if lower.hasPrefix("c++") || lower == "cpp" { return ("brand-cpp", .blue) }
        if lower == "c" || lower == "c#" { return ("code", .lavender) }
        if lower.hasPrefix("javascript") || lower == "js" { return ("brand-javascript", .amber) }
        if lower.hasPrefix("typescript") || lower == "ts" { return ("brand-typescript", .blue) }
        if lower.hasPrefix("rust") { return ("brand-rust", .peach) }
        if lower == "go" || lower == "golang" { return ("brand-golang", .mint) }
        if lower.hasPrefix("swift") { return ("brand-apple", .lavender) }
        if lower.hasPrefix("kotlin") || lower.hasPrefix("android") { return ("brand-android", .green) }
        return NexlyIcons.semanticIcon(for: raw, fallback: "sparkles")
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
                .padding(.horizontal, 10)
                .frame(width: width, alignment: frameAlignment(for: align))
                .padding(.vertical, 7)
        } else if !emphasize, isPrimaryNameCol, let badge = entityBadge(for: trimmed) {
            HStack(spacing: 6) {
                NexlyGlassGlyphTile(
                    name: badge.icon,
                    tone: badge.tone,
                    size: 20,
                    glyphSize: 11
                )
                Text(NexlyIcons.cleanPlainText(trimmed))
                    .font(.system(size: 12.5, weight: .semibold))
                    .foregroundStyle(Color(red: 0.133, green: 0.102, blue: 0.278))
                    .lineLimit(1)
            }
            .padding(.horizontal, 10)
            .frame(width: width, alignment: frameAlignment(for: align))
            .padding(.vertical, 7)
        } else {
            Text(text)
                .font((emphasize || (isPrimaryNameCol && Int(trimmed) == nil)) ? .system(size: 12.5, weight: .bold) : Aero.caption())
                .foregroundStyle((emphasize || (isPrimaryNameCol && Int(trimmed) == nil)) ? Color(red: 0.133, green: 0.102, blue: 0.278) : Aero.textSecondary)
                .padding(.horizontal, 10)
                .frame(width: width, alignment: frameAlignment(for: align))
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

func citationSourceURLIOS(_ raw: String) -> URL? {
    guard let url = URL(string: raw), let host = url.host, !host.isEmpty,
          ["https", "http"].contains(url.scheme?.lowercased() ?? ""), url.user == nil, url.password == nil else { return nil }
    return url
}

func isAcademicCitationDomain(_ domain: String, _ raw: String) -> Bool {
    guard let host = citationSourceURLIOS(raw)?.host?.lowercased() else { return false }
    let publishers = ["nature.com", "arxiv.org", "ieee.org", "acm.org", "springer.com", "springernature.com", "sciencedirect.com", "oup.com", "cambridge.org", "nih.gov"]
    return host.hasSuffix(".edu") || host.hasSuffix(".ac.uk") || publishers.contains { host == $0 || host.hasSuffix("." + $0) }
}

private struct CitationsBlockView: View {
    let citations: [CitationEntry]
    var title: String = "Key Sources"
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
                    Text(title)
                        .font(.system(size: 14, weight: .bold))
                        .foregroundStyle(Aero.text)
                }
                Spacer()
                Text("\(citations.count) \(citations.count == 1 ? "source" : "sources")")
                    .font(.system(size: 11.5, weight: .semibold))
                    .foregroundStyle(Color(red: 0.357, green: 0.239, blue: 0.961))
            }

            ForEach(Array(citations.enumerated()), id: \.offset) { _, citation in
                let link = citationSourceURLIOS(citation.url)
                let domain = link?.host?.replacingOccurrences(of: "^www\\.", with: "", options: .regularExpression) ?? "Invalid source URL"
                let academic = isAcademicCitationDomain(citation.domain, citation.url)
                Button {
                    if let link {
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
                                    Text(domain)
                                        .font(.system(size: 11.5, weight: .semibold))
                                        .foregroundStyle(Color(red: 0.357, green: 0.239, blue: 0.961))
                                        .lineLimit(1)
                                }
                                Spacer()
                                HStack(spacing: 4) {
                                    NexlyGlyph(
                                        name: academic ? "building-bank" : "external-link",
                                        size: 11,
                                        color: academic
                                            ? Color(red: 0.357, green: 0.239, blue: 0.961)
                                            : Color(red: 0.357, green: 0.239, blue: 0.961)
                                    )
                                    Text(link == nil ? "Invalid link" : academic ? "Academic domain" : "Source link")
                                        .font(.system(size: 10.5, weight: .bold))
                                        .foregroundStyle(
                                            academic
                                                ? Color(red: 0.357, green: 0.239, blue: 0.961)
                                                : Color(red: 0.357, green: 0.239, blue: 0.961)
                                        )
                                }
                                .padding(.horizontal, 8)
                                .padding(.vertical, 2)
                                .background(
                                    Capsule().fill(
                                        academic
                                            ? Color(red: 0.937, green: 0.918, blue: 1.0)
                                            : Color(red: 0.937, green: 0.918, blue: 1.0)
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
                .disabled(link == nil)
                .accessibilityElement(children: .combine)
                .accessibilityLabel("Source \(citation.number). \(citation.title), \(domain)")
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
