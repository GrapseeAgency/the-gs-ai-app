import SwiftUI

/**
 * PHASE 8.2 — the expandable "Research details" audit section (docs/
 * search-event-protocol.md §20). Composed ONLY from received events (`steps`)
 * and the turn's sources — queries run, engines with per-engine outcome,
 * sources found/read/failed, syndicated groups, citations used. Nothing is
 * invented: a section with no data behind it never renders, and the whole
 * card hides when there is nothing to audit.
 *
 * Memory discipline: the live step list is MEMORY-ONLY per the protocol.
 * It rides the turn until the conversation leaves memory; a reloaded thread
 * passes empty steps and this audit re-derives its source counts and
 * citations from the persisted `sources[]` alone (never the other way
 * around — no number is shown that neither an event nor a persisted row
 * reported).
 *
 * Motion/accessibility: expand/collapse uses the theme's gentle spring
 * (self-degrades under reduce motion), the same header/chevron treatment
 * and accessibility labels as the search trace card.
 */
struct ResearchDetailsView: View {

    let steps: [SearchTraceStep]
    let sources: [MessageSource]

    @State private var expanded = false

    @ViewBuilder
    var body: some View {
        if hasContent {
            VStack(alignment: .leading, spacing: 0) {
                header
                if expanded {
                    VStack(alignment: .leading, spacing: 10) {
                        if !queries.isEmpty {
                            sectionHeader("Queries")
                            VStack(alignment: .leading, spacing: 3) {
                                ForEach(Array(queries.enumerated()), id: \.offset) { _, query in
                                    Text("\u{201C}\(query)\u{201D}")
                                        .font(Aero.metadata())
                                        .foregroundStyle(Aero.textSecondary)
                                        .fixedSize(horizontal: false, vertical: true)
                                }
                            }
                        }
                        if !engineOutcomes.isEmpty {
                            sectionHeader("Engines")
                            VStack(alignment: .leading, spacing: 3) {
                                ForEach(Array(engineOutcomes.enumerated()), id: \.offset) { _, outcome in
                                    EngineOutcomeRow(outcome: outcome)
                                }
                            }
                        }
                        if !sourceLine.isEmpty {
                            sectionHeader("Sources")
                            Text(sourceLine)
                                .font(Aero.metadata())
                                .foregroundStyle(Aero.textSecondary)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                        if !citations.isEmpty {
                            sectionHeader("Citations used")
                            Text(citations.map { "[\($0)]" }.joined(separator: "  "))
                                .font(Aero.metadata())
                                .foregroundStyle(Aero.textSecondary)
                        }
                    }
                    .padding(.leading, 12)
                    .padding(.trailing, 12)
                    .padding(.bottom, 10)
                }
            }
            .background(RoundedRectangle(cornerRadius: Aero.Radius.md).fill(Aero.raisedSurface))
            .overlay(RoundedRectangle(cornerRadius: Aero.Radius.md).stroke(Aero.outline, lineWidth: 1))
        }
    }

    // MARK: Header

    private var header: some View {
        Button {
            withAnimation(Aero.gentle) { expanded.toggle() }
        } label: {
            HStack(spacing: 8) {
                Image(systemName: "info.circle")
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(Aero.textMuted)
                Text("Research details")
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
        .accessibilityLabel("Research details")
        .accessibilityValue(expanded ? "Expanded" : "Collapsed")
        .accessibilityHint("Shows the queries, engines and sources the research actually used")
    }

    private func sectionHeader(_ title: String) -> some View {
        Text(title)
            .font(Aero.metadata())
            .foregroundStyle(Aero.textMuted)
    }

    // MARK: Derived audit data (received events + persisted sources only)

    private var hasContent: Bool {
        !queries.isEmpty || !engineOutcomes.isEmpty || !sourceLine.isEmpty || !citations.isEmpty
    }

    /// The live step detail is the truth source while it is in memory; a
    /// reloaded thread (empty steps) re-derives from persisted sources.
    private var hasStepDetail: Bool { !steps.isEmpty }

    /// Distinct query texts in wire order (a literal repeat collapses).
    private var queries: [String] {
        var seen: Set<String> = []
        var out: [String] = []
        for step in steps {
            if case .query(_, let text, _) = step.kind, !text.isEmpty, seen.insert(text).inserted {
                out.append(text)
            }
        }
        return out
    }

    /// Last reported outcome per engine id (a later round supersedes an
    /// earlier one); first-seen order preserved. Anonymous engines share one
    /// slot — the wire always sends ids, this only bounds a broken payload.
    private var engineOutcomes: [SearchEngineOutcome] {
        var slots: [String: Int] = [:]
        var bySlot: [Int: SearchEngineOutcome] = [:]
        var next = 0
        for step in steps {
            if case .engines(_, let outcomes) = step.kind {
                for outcome in outcomes {
                    let slot: Int
                    if let known = slots[outcome.id] {
                        slot = known
                    } else {
                        slot = next
                        slots[outcome.id] = slot
                        next += 1
                    }
                    bySlot[slot] = outcome
                }
            }
        }
        return (0..<next).compactMap { bySlot[$0] }
    }

    private var foundCount: Int {
        if hasStepDetail {
            return steps.reduce(0) { if case .discovered = $1.kind { return $0 + 1 }; return $0 }
        }
        return sources.count
    }

    private var readCount: Int {
        if hasStepDetail {
            return steps.reduce(0) { if case .sourceCompleted = $1.kind { return $0 + 1 }; return $0 }
        }
        return sources.filter { $0.status == "retrieved" || $0.status == "used" }.count
    }

    private var failedCount: Int {
        if hasStepDetail {
            return steps.reduce(0) { if case .sourceFailed = $1.kind { return $0 + 1 }; return $0 }
        }
        return sources.filter { $0.status == "failed" }.count
    }

    private var skippedCount: Int {
        if hasStepDetail {
            return steps.reduce(0) { if case .sourceSkipped = $1.kind { return $0 + 1 }; return $0 }
        }
        return sources.filter { $0.status == "skipped" }.count
    }

    /// The highest syndicated-group count the wire reported for any single
    /// round — deliberately conservative: the wire reports per round, and a
    /// sum could double-count the same syndicated set across rounds.
    private var syndicatedCount: Int {
        var maxSeen = 0
        for step in steps {
            switch step.kind {
            case .roundSummary(_, _, _, let syndicated), .researchRound(_, _, _, _, let syndicated):
                if let syndicated, syndicated > maxSeen { maxSeen = syndicated }
            default:
                break
            }
        }
        return maxSeen
    }

    /// "12 found · 3 read · 2 failed · 1 skipped · 1 syndicated group" —
    /// zero counts stay invisible; the line (and section) only exists when
    /// at least one real number stands behind it.
    private var sourceLine: String {
        var parts: [String] = []
        if foundCount > 0 { parts.append("\(foundCount) found") }
        if readCount > 0 { parts.append("\(readCount) read") }
        if failedCount > 0 { parts.append("\(failedCount) failed") }
        if skippedCount > 0 { parts.append("\(skippedCount) skipped") }
        if syndicatedCount > 0 {
            parts.append(syndicatedCount == 1 ? "1 syndicated group" : "\(syndicatedCount) syndicated groups")
        }
        return parts.joined(separator: " \u{00B7} ")
    }

    /// Citations the answer actually bound: the wire's `usedCitations` when
    /// a completed event carried it, else the persisted rows' used flags.
    private var citations: [Int] {
        for step in steps.reversed() {
            if case .completedSummary(_, _, _, let used) = step.kind, let used, !used.isEmpty {
                return used
            }
        }
        return sources.filter { $0.used == true }.map { $0.ordinal }.sorted()
    }
}
