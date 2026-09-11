import SwiftUI

/// Chat search over the on-device SQLite store — FTS5 full-text over `ConversationStore`
/// (titles + message bodies) with honest edge states: a start-typing hint, a
/// no-matches empty state, and tap-through into the conversation. Store
/// hiccups resolve to "no matches", never an error.
struct ChatSearchView: View {

    private struct SearchHit: Identifiable {
        let id: String
        let routeID: String
        let title: String
        let snippet: String
        let when: String
    }

    @ObservedObject private var store = ConversationStore.shared
    @State private var query = ""
    @State private var activeFilters: Set<String> = []
    // Debounced, off-main results (Task 85-e I15) — the FTS/LIKE pair no
    // longer runs synchronously in the body on every keystroke.
    @State private var results: [SearchHit] = []

    // PHASE 2 honesty: the only filter chip is the one that actually filters.
    // "Has files" and "Model: GS Balanced" were visual toggles wired to
    // nothing — a dead filter and a hardcoded model name (raw model taxonomy
    // in a consumer surface) — both removed rather than left pretending.
    private static let filterChips = ["This week"]

    private var term: String {
        query.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    /// Same content, ordering and caps as the old synchronous pass — the
    /// SQLite reads just moved off the main actor
    /// (ConversationStore.searchAsync). @MainActor so the observed store is
    /// only ever touched from the main actor.
    @MainActor
    private func computeResults(_ term: String) async -> [SearchHit] {
        let withinWeek = activeFilters.contains("This week")

        var hits: [SearchHit] = []
        var seen = Set<String>()
        let page = await store.searchAsync(term)
        for convo in page.titles where !convo.archived {
            seen.insert(convo.id)
            hits.append(SearchHit(
                id: convo.id,
                routeID: convo.id,
                title: convo.title,
                snippet: "Chat title match",
                when: Self.relative(convo.updatedAt)))
        }
        for messageHit in page.messages {
            let convo = messageHit.conversation
            guard !convo.archived, !seen.contains(convo.id) else { continue }
            seen.insert(convo.id)
            hits.append(SearchHit(
                id: convo.id + "#" + messageHit.messageID,
                routeID: convo.id,
                title: convo.title,
                snippet: Self.snippet(messageHit.content, term: term),
                when: Self.relative(messageHit.createdAt)))
        }

        if withinWeek {
            hits = hits.filter { hit in
                Self.isRecent(hit.when)
            }
        }
        return Array(hits.prefix(24))
    }

    var body: some View {
        // Deep-perf pass 80-b: ONE results evaluation per body pass. Task 85-e
        // I15: the evaluation is now debounced + off-main via .task(id:) —
        // the body only renders the last landed page.
        VStack(alignment: .leading, spacing: Aero.Spacing.l) {
            Text("Search chats")
                .font(Aero.displayTitle())
                .foregroundStyle(Aero.text)

            AeroInputBar(
                text: $query,
                placeholder: "Search messages and chats",
                searchField: true
            )

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: Aero.Spacing.s) {
                    ForEach(Self.filterChips, id: \.self) { chip in
                        AeroChip(text: chip, selected: activeFilters.contains(chip)) {
                            GSHaptics.select()
                            withAnimation(Aero.snappy) {
                                if activeFilters.contains(chip) {
                                    activeFilters.remove(chip)
                                } else {
                                    activeFilters.insert(chip)
                                }
                            }
                        }
                    }
                }
            }

            ScrollView {
                if term.count < 2 {
                    EmptyStateView(
                        icon: "magnifyingglass",
                        title: "Search your chats",
                        message: "Start typing to find any conversation or message on this device."
                    )
                    .padding(.top, Aero.Spacing.xl)
                } else if results.isEmpty {
                    EmptyStateView(
                        icon: "magnifyingglass",
                        title: "No results",
                        message: "Nothing matched “\(term)”. Try different words or clear a filter."
                    )
                    .padding(.top, Aero.Spacing.xl)
                } else {
                    LazyVStack(spacing: Aero.Spacing.s) {
                        ForEach(results) { hit in
                            NavigationLink(value: AeroRoute.chat(hit.routeID)) {
                                AeroListRow(
                                    title: gsConversationTitle(hit.title),
                                    subtitle: "\(hit.snippet) · \(hit.when)",
                                    leading: {
                                        Image(systemName: "bubble.left")
                                            .font(.system(size: 14))
                                            .foregroundStyle(Aero.text)
                                            .frame(width: 36, height: 36)
                                            .background(Circle().fill(Aero.containerHigh))
                                    },
                                    trailing: {
                                        EmptyView()
                                    }
                                )
                            }
                            .buttonStyle(KineticPressStyle())
                        }

                        Text("Search runs across every conversation on this device.")
                            .font(Aero.caption())
                            .foregroundStyle(Aero.textMuted)
                            .padding(.top, Aero.Spacing.s)
                    }
                }
            }
            .scrollDismissesKeyboard(.interactively)
        }
        .padding(.horizontal, Aero.Spacing.m)
        .padding(.top, Aero.Spacing.s)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .background(Aero.background.ignoresSafeArea())
        .task(id: "\(term)|\(activeFilters.sorted().joined(separator: ","))") {
            // Empty/short queries clear immediately; real ones settle for
            // 250 ms, then read off-main. The id also folds the active
            // filters in, so a chip flip re-runs the same debounced read.
            guard term.count >= 2 else {
                results = []
                return
            }
            do {
                try await Task.sleep(nanoseconds: 250_000_000)
            } catch { return }
            guard !Task.isCancelled else { return }
            let computed = await computeResults(term)
            guard !Task.isCancelled else { return }
            results = computed
        }
    }

    // MARK: Helpers

    private static func snippet(_ content: String, term: String) -> String {
        let clean = content.replacingOccurrences(of: "\n", with: " ").trimmingCharacters(in: .whitespaces)
        guard let range = clean.range(of: term, options: .caseInsensitive) else {
            return String(clean.prefix(80))
        }
        let index = clean.distance(from: clean.startIndex, to: range.lowerBound)
        let start = max(0, index - 24)
        let end = min(clean.count, index + term.count + 48)
        let lower = clean.index(clean.startIndex, offsetBy: start)
        let upper = clean.index(clean.startIndex, offsetBy: end)
        let prefix = start > 0 ? "…" : ""
        let suffix = end < clean.count ? "…" : ""
        return prefix + clean[lower..<upper].trimmingCharacters(in: .whitespaces) + suffix
    }

    private static func relative(_ iso: String) -> String {
        guard let date = GSFormatters.date(from: iso) else { return "earlier" }
        let minutes = Int(Date().timeIntervalSince(date) / 60)
        switch minutes {
        case ..<1: return "just now"
        case ..<60: return "\(minutes)m ago"
        case ..<1440: return "\(minutes / 60)h ago"
        case ..<10080: return "\(minutes / 1440)d ago"
        default: return "earlier"
        }
    }

    /// "This week" filter — re-derives recency from the rendered moment label.
    private static func isRecent(_ when: String) -> Bool {
        when != "earlier"
    }
}
