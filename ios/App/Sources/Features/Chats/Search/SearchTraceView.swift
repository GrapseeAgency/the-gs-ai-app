import SwiftUI

/**
 * Engine id → consumer display name. The wire carries internal engine ids
 * (bing-news-rss, z-ai, …); the trace UI renders a neutral product-neutral
 * label instead — raw ids are plumbing, not something a reader should have
 * to parse. Unknown ids fall back to the raw id (never blank, never fake),
 * so a future engine still surfaces honestly.
 */
enum EngineDisplayName {
    static func label(for engineID: String) -> String {
        switch engineID {
        case "bing-news-rss": return "Bing News"
        case "google-news-rss": return "Google News"
        case "bing-web": return "Bing Web"
        case "duckduckgo-lite": return "DuckDuckGo"
        case "wikipedia": return "Wikipedia"
        case "searxng": return "SearXNG"
        case "z-ai": return "GS Web"
        case "openlibrary": return "Open Library"
        case "gutenberg": return "Gutenberg"
        case "arxiv": return "arXiv"
        case "crossref": return "Crossref"
        default: return engineID // unknown id → raw id, never blank
        }
    }
}

/**
 * PHASE 8.1 — the search trace card (docs/search-event-protocol.md).
 * Renders ONLY steps that actually arrived as `search`/`source` events (plus
 * the synthesis start from `status`) — no timers, no fabricated steps, no
 * optimistic guesses; an event that never came simply never shows. PHASE
 * 8.2 (v2) adds the per-engine status rows, the evidence line and the
 * research-level event family to the same honest pipeline.
 *
 * Lifecycle: while the turn streams it sits ABOVE the live bubble as a
 * compact expandable card; after `done` the parent swaps it for the one-line
 * collapsed summary (`SearchTraceSummaryView`), per the protocol's "After
 * done, the trace collapses to a summary line; source cards stay."
 *
 * Motion: expand/collapse uses the theme's gentle spring, which self-degrades
 * under reduce motion — the same treatment the rest of the codebase uses.
 */
struct SearchTraceView: View {

    let steps: [SearchTraceStep]

    @State private var expanded = true

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            header
            if expanded {
                VStack(alignment: .leading, spacing: 7) {
                    ForEach(steps) { step in
                        SearchTraceRow(step: step)
                    }
                }
                .padding(.leading, 32)
                .padding(.trailing, 12)
                .padding(.bottom, 10)
            }
        }
        .background(RoundedRectangle(cornerRadius: Aero.Radius.md).fill(Aero.raisedSurface))
        .overlay(RoundedRectangle(cornerRadius: Aero.Radius.md).stroke(Aero.outline, lineWidth: 1))
    }

    private var header: some View {
        Button {
            withAnimation(Aero.gentle) { expanded.toggle() }
        } label: {
            HStack(spacing: 8) {
                Image(systemName: "magnifyingglass")
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(Aero.accent)
                Text("Search")
                    .font(Aero.label())
                    .foregroundStyle(Aero.textMuted)
                Spacer()
                Image(systemName: "chevron.right")
                    .font(.system(size: 11, weight: .semibold))
                    .foregroundStyle(Aero.textTertiary)
                    .rotationEffect(.degrees(expanded ? 90 : 0))
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 8)
            .contentShape(Rectangle())
        }
        .buttonStyle(KineticPressStyle())
        .accessibilityLabel("Search trace")
        .accessibilityValue(expanded ? "Expanded" : "Collapsed")
        .accessibilityHint("Shows the steps the search actually ran")
    }
}

/// One honest trace line — icon + title (+ detail). Copy states exactly what
/// the wire said: the query text, the found count, the real fetch failures.
private struct SearchTraceRow: View {

    let step: SearchTraceStep

    var body: some View {
        HStack(alignment: .top, spacing: 10) {
            Image(systemName: symbolName)
                .font(.system(size: 11))
                .foregroundStyle(iconColor)
                .frame(width: 14)
            VStack(alignment: .leading, spacing: 1) {
                Text(title)
                    .font(Aero.caption())
                    .foregroundStyle(Aero.text)
                    .fixedSize(horizontal: false, vertical: true)
                if case .engines(_, let outcomes) = step.kind {
                    // v2 per-engine truth — one sub-row per engine, ✓/✕ plus
                    // the count it contributed or the real error text.
                    ForEach(Array(outcomes.enumerated()), id: \.offset) { _, outcome in
                        EngineOutcomeRow(outcome: outcome)
                    }
                } else if !detail.isEmpty {
                    Text(detail)
                        .font(Aero.metadata())
                        .foregroundStyle(Aero.textTertiary)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
        }
        .accessibilityElement(children: .combine)
    }

    private func syndicatedGroupText(_ n: Int) -> String {
        n == 1 ? "1 syndicated group" : "\(n) syndicated groups"
    }

    private var symbolName: String {
        switch step.kind {
        case .started: return "magnifyingglass"
        case .query: return "magnifyingglass"
        case .engines: return "network"
        case .results: return "list.bullet"
        case .discovered: return "doc.text"
        case .reading: return "doc.text.magnifyingglass"
        case .sourceCompleted: return "checkmark.circle"
        case .evidence: return "text.quote"
        case .sourceFailed: return "xmark.circle"
        case .sourceSkipped: return "minus.circle"
        case .roundSummary: return "checkmark.seal"
        case .researchStarted: return "magnifyingglass"
        case .researchRound: return "checkmark.seal"
        case .researchFailed: return "exclamationmark.triangle"
        case .researchCancelled: return "stop.circle"
        case .searchFailed: return "exclamationmark.triangle"
        case .completedSummary: return "checkmark.seal"
        case .composing: return "pencil"
        }
    }

    private var iconColor: Color {
        switch step.kind {
        case .sourceFailed, .searchFailed, .researchFailed, .researchCancelled:
            return Aero.textMuted
        case .sourceCompleted, .roundSummary, .completedSummary, .researchRound:
            return Aero.accent
        default:
            return Aero.textTertiary
        }
    }

    private var title: String {
        switch step.kind {
        case .started(let label):
            return label?.isEmpty == false ? "Searching \u{201C}\(label!)\u{201D}" : "Search started"
        case .query(_, let text, _):
            return text.isEmpty ? "Running a query" : text
        case .engines(let round, _):
            return round > 0 ? "Round \(round) engines" : "Engine status"
        case .results(_, let found, _, _):
            return "\(found) \(found == 1 ? "result" : "results")"
        case .discovered(_, let title, let domain):
            let shown = title.isEmpty ? domain : title
            return shown.isEmpty ? "Found a source" : shown
        case .reading(let ordinal):
            return "Reading source \(ordinal)"
        case .sourceCompleted(let ordinal, _):
            return "Read source \(ordinal)"
        case .evidence(let ordinal, _, _):
            return "Extracted evidence from source \(ordinal)"
        case .sourceFailed(let ordinal, _):
            return "Couldn't read source \(ordinal)"
        case .sourceSkipped(let ordinal, _):
            return "Skipped source \(ordinal)"
        case .roundSummary(let round, let verified, let failed, let syndicated):
            var line = "Round \(round) \u{2014} \(verified) verified, \(failed) failed"
            if let syndicated, syndicated > 0 { line += " \u{00B7} \(syndicatedGroupText(syndicated))" }
            return line
        case .researchStarted:
            return "Research started"
        case .researchRound(let round, let found, let read, let failed, let syndicated):
            var line = "Round \(round) \u{2014} \(found) found, \(read) read, \(failed) failed"
            if let syndicated, syndicated > 0 { line += " \u{00B7} \(syndicatedGroupText(syndicated))" }
            return line
        case .researchFailed:
            return "Research failed"
        case .researchCancelled:
            return "Research cancelled"
        case .searchFailed:
            return "Search failed"
        case .completedSummary(_, let sources, let retrieved, _):
            return "Search complete \u{2014} \(sources) \(sources == 1 ? "source" : "sources"), \(retrieved) \(retrieved == 1 ? "page" : "pages") read"
        case .composing:
            return "Composing the answer"
        }
    }

    private var detail: String {
        switch step.kind {
        case .query(_, _, let engines):
            return engines.isEmpty ? "" : engines.map { EngineDisplayName.label(for: $0) }.joined(separator: " · ")
        case .results(let round, _, _, _):
            return "Round \(round)"
        case .discovered(_, _, let domain):
            return domain.isEmpty ? "" : domain
        case .sourceCompleted(_, let chars):
            guard let chars, chars > 0 else { return "" }
            return "\(chars) characters"
        case .evidence(_, let chars, let windowChars):
            var parts: [String] = []
            if let chars, chars > 0 { parts.append("\(chars) characters") }
            if let windowChars, windowChars > 0 { parts.append("\(windowChars)-character window") }
            return parts.joined(separator: " · ")
        case .sourceFailed(_, let reason):
            return reason.replacingOccurrences(of: "_", with: " ")
        case .sourceSkipped(_, let reason):
            return reason.replacingOccurrences(of: "_", with: " ")
        case .researchStarted(let depth, let roundsPlanned):
            var parts: [String] = []
            if let depth, !depth.isEmpty { parts.append(depth) }
            if let roundsPlanned, roundsPlanned > 0 {
                parts.append(roundsPlanned == 1 ? "1 round planned" : "\(roundsPlanned) rounds planned")
            }
            return parts.joined(separator: " · ")
        case .researchFailed(let reason):
            return reason.replacingOccurrences(of: "_", with: " ")
        case .researchCancelled(let by):
            guard let by, !by.isEmpty else { return "" }
            return "by \(by)"
        default:
            return ""
        }
    }
}

/// The collapsed one-line trace after `done` — computed only from the steps
/// that arrived ("Searched 2 queries · 5 sources · 4 pages read"). Stays on
/// the finalized turn; the source cards below carry the receipts.
struct SearchTraceSummaryView: View {

    let text: String

    var body: some View {
        HStack(spacing: 6) {
            Image(systemName: "magnifyingglass")
                .font(.system(size: 11, weight: .semibold))
                .foregroundStyle(Aero.textTertiary)
            Text(text)
                .font(Aero.metadata())
                .foregroundStyle(Aero.textMuted)
                .lineLimit(1)
        }
        .accessibilityElement(children: .combine)
    }
}

/// One per-engine outcome line (v2 `search:engines`) — ✓ with the result
/// count the engine contributed, or ✕ with the real error text. Shared by
/// the live trace rows and the research-details audit. Monochrome tokens
/// only: success/failure read through glyphs and copy, not hue.
struct EngineOutcomeRow: View {

    let outcome: SearchEngineOutcome

    var body: some View {
        HStack(spacing: 5) {
            Image(systemName: outcome.ok ? "checkmark" : "xmark")
                .font(.system(size: 9, weight: .semibold))
                .foregroundStyle(outcome.ok ? Aero.accent : Aero.textMuted)
            Text(outcome.id.isEmpty ? "engine" : EngineDisplayName.label(for: outcome.id))
                .font(Aero.metadata())
                .foregroundStyle(Aero.textSecondary)
                .lineLimit(1)
            if outcome.ok, let count = outcome.count {
                Text(count == 1 ? "1 result" : "\(count) results")
                    .font(Aero.metadata())
                    .foregroundStyle(Aero.textTertiary)
                    .lineLimit(1)
            } else if let error = outcome.error, !error.isEmpty {
                Text(error.replacingOccurrences(of: "_", with: " "))
                    .font(Aero.metadata())
                    .foregroundStyle(Aero.textTertiary)
                    .lineLimit(1)
            }
        }
    }
}
