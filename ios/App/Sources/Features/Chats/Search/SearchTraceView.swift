import SwiftUI

/**
 * PHASE 8.1 — the search trace card (docs/search-event-protocol.md).
 * Renders ONLY steps that actually arrived as `search`/`source` events (plus
 * the synthesis start from `status`) — no timers, no fabricated steps, no
 * optimistic guesses; an event that never came simply never shows.
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
                if !detail.isEmpty {
                    Text(detail)
                        .font(Aero.metadata())
                        .foregroundStyle(Aero.textTertiary)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
        }
        .accessibilityElement(children: .combine)
    }

    private var symbolName: String {
        switch step.kind {
        case .started: return "magnifyingglass"
        case .query: return "magnifyingglass"
        case .results: return "list.bullet"
        case .discovered: return "doc.text"
        case .reading: return "doc.text.magnifyingglass"
        case .sourceCompleted: return "checkmark.circle"
        case .sourceFailed: return "xmark.circle"
        case .sourceSkipped: return "minus.circle"
        case .roundSummary: return "checkmark.seal"
        case .searchFailed: return "exclamationmark.triangle"
        case .completedSummary: return "checkmark.seal"
        case .composing: return "pencil"
        }
    }

    private var iconColor: Color {
        switch step.kind {
        case .sourceFailed, .searchFailed: return Aero.textMuted
        case .sourceCompleted, .roundSummary, .completedSummary: return Aero.accent
        default: return Aero.textTertiary
        }
    }

    private var title: String {
        switch step.kind {
        case .started(let label):
            return label?.isEmpty == false ? "Searching \u{201C}\(label!)\u{201D}" : "Search started"
        case .query(_, let text, _):
            return text.isEmpty ? "Running a query" : text
        case .results(_, let found, _, _):
            return "\(found) \(found == 1 ? "result" : "results")"
        case .discovered(_, let title, let domain):
            let shown = title.isEmpty ? domain : title
            return shown.isEmpty ? "Found a source" : shown
        case .reading(let ordinal):
            return "Reading source \(ordinal)"
        case .sourceCompleted(let ordinal, _):
            return "Read source \(ordinal)"
        case .sourceFailed(let ordinal, _):
            return "Couldn't read source \(ordinal)"
        case .sourceSkipped(let ordinal, _):
            return "Skipped source \(ordinal)"
        case .roundSummary(let round, let verified, let failed):
            return "Round \(round) \u{2014} \(verified) verified, \(failed) failed"
        case .searchFailed:
            return "Search failed"
        case .completedSummary(_, let sources, let retrieved):
            return "Search complete \u{2014} \(sources) \(sources == 1 ? "source" : "sources"), \(retrieved) \(retrieved == 1 ? "page" : "pages") read"
        case .composing:
            return "Composing the answer"
        }
    }

    private var detail: String {
        switch step.kind {
        case .query(_, _, let engines):
            return engines.isEmpty ? "" : engines.joined(separator: " · ")
        case .results(let round, _, _, _):
            return "Round \(round)"
        case .discovered(_, _, let domain):
            return domain.isEmpty ? "" : domain
        case .sourceCompleted(_, let chars):
            guard let chars, chars > 0 else { return "" }
            return "\(chars) characters"
        case .sourceFailed(_, let reason):
            return reason.replacingOccurrences(of: "_", with: " ")
        case .sourceSkipped(_, let reason):
            return reason.replacingOccurrences(of: "_", with: " ")
        case .searchFailed(let reason):
            return reason.replacingOccurrences(of: "_", with: " ")
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
