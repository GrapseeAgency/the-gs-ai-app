import SwiftUI

// MARK: - Stagger entrance (private per-file helper)

/// Fades + lifts a section into place, delayed by its index —
/// the Aeruo Kinetic section entrance.
private struct StaggerIn<Content: View>: View {
    let index: Int
    @ViewBuilder var content: () -> Content

    @State private var appeared = false

    var body: some View {
        content()
            .opacity(appeared ? 1 : 0)
            .offset(y: appeared ? 0 : 16)
            .onAppear {
                withAnimation(Aero.spring.delay(Aero.stagger(index))) {
                    appeared = true
                }
            }
    }
}

// MARK: - Search — one index over everything

/// Pushed via `.search`. One query over the real on-device corpus: SQLite
/// conversations and message bodies (FTS5-backed via `ConversationStore`).
/// The demo catalogues (library / assistants / projects) died with their
/// surfaces and the index follows them. Results arrive grouped with honest
/// per-group counts and every row routes to its real destination. Store
/// hiccups resolve to "no results", never an error. Kind chips gate the
/// groups; recent searches are the queries the reader actually acted on.
struct SearchView: View {

    // MARK: Result model

    private enum Kind: String, CaseIterable {
        case conversations = "Conversations"
        case messages = "Messages"

        var icon: String {
            switch self {
            case .conversations: return "bubble.left"
            case .messages: return "text.quote"
            }
        }
    }

    private struct SearchHit: Identifiable {
        let id: String
        let kind: Kind
        let title: String
        let detail: String
        let route: AeroRoute
    }

    /// Recent searches the reader acted on — local-first, hiccup-safe, newest first.
    private enum RecentSearches {
        private static let key = "gs_search_recent"

        static func load() -> [String] {
            guard let raw = UserDefaults.standard.string(forKey: key) else { return [] }
            return raw.split(separator: "\n").map(String.init).filter { !$0.isEmpty }
        }

        static func record(_ term: String) {
            let next = ([term] + load()).removingDuplicates().prefix(5)
            UserDefaults.standard.set(next.joined(separator: "\n"), forKey: key)
        }
    }

    // MARK: State

    @ObservedObject private var store = ConversationStore.shared
    @State private var query = ""
    @State private var activeKind: Kind?
    @State private var recents: [String] = RecentSearches.load()
    // Debounced, off-main results (Task 85-e I15) — the query no longer runs
    // synchronously in the body on every keystroke.
    @State private var results: [SearchHit] = []

    private var term: String {
        query.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    // MARK: Real search (debounced + off-main, Task 85-e I15)

    /// Same content, ordering and caps as the old synchronous pass — only the
    /// store's SQLite reads moved off the main actor
    /// (ConversationStore.searchAsync). @MainActor so the observed store is
    /// only ever touched from the main actor.
    @MainActor
    private func computeResults(_ term: String) async -> [SearchHit] {
        var hits: [SearchHit] = []

        let page = await store.searchAsync(term)
        for convo in page.titles.prefix(10) {
            hits.append(SearchHit(
                id: "convo-\(convo.id)",
                kind: .conversations,
                title: convo.title,
                detail: "Chat title match · " + Self.relative(convo.updatedAt),
                route: .chat(convo.id)))
        }
        for hit in page.messages.prefix(12) {
            hits.append(SearchHit(
                id: "msg-\(hit.messageID)",
                kind: .messages,
                title: hit.conversation.title,
                detail: Self.snippet(hit.content, term: term) + " · " + Self.relative(hit.createdAt),
                route: .chat(hit.conversation.id)))
        }
        return hits
    }

    // MARK: Body

    var body: some View {
        // Deep-perf pass 80-b: ONE search execution per body pass. Task 85-e
        // I15 goes further: the query no longer runs IN the body at all —
        // results are @State produced by the debounced off-main .task below,
        // so every keystroke paints instantly and the SQLite pair never
        // blocks the main thread.
        ScrollView {
            LazyVStack(alignment: .leading, spacing: Aero.Spacing.l) {
                StaggerIn(index: 0) { inputBar }
                if term.isEmpty {
                    StaggerIn(index: 1) { recentSection }
                    StaggerIn(index: 2) { filterSection }
                    StaggerIn(index: 3) {
                        EmptyStateView(
                            icon: "magnifyingglass",
                            title: "Search everything",
                            message: "Conversations and message bodies, one index over this device."
                        )
                    }
                } else if term.count < 2 {
                    StaggerIn(index: 1) {
                        EmptyStateView(
                            icon: "magnifyingglass",
                            title: "Keep typing",
                            message: "At least two characters to search everything on this device."
                        )
                    }
                } else if results.isEmpty {
                    StaggerIn(index: 1) {
                        EmptyStateView(
                            icon: "tray",
                            title: "No results",
                            message: "Nothing matches “\(term)” yet, try another term or clear a filter."
                        )
                    }
                } else {
                    StaggerIn(index: 1) { resultsSection(results) }
                }
            }
            .padding(.horizontal, Aero.Spacing.m)
            .padding(.top, Aero.Spacing.s)
            .padding(.bottom, Aero.Spacing.xl)
        }
        .background(Aero.background.ignoresSafeArea())
        .scrollDismissesKeyboard(.interactively)
        .navigationTitle("Search everything")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar(.visible, for: .navigationBar)
        .onAppear { recents = RecentSearches.load() }
        .task(id: term) {
            // Empty/short queries clear immediately; real ones settle for
            // 250 ms (keystroke batching), then read off-main. .task(id:)
            // cancels a superseded keystroke's task before it can land.
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

    // MARK: Input

    private var inputBar: some View {
        AeroInputBar(
            text: $query,
            placeholder: "Search conversations and messages…",
            action: {},
            searchField: true
        )
    }

    // MARK: Recent searches

    private var recentSection: some View {
        VStack(alignment: .leading, spacing: Aero.Spacing.m) {
            SectionHeader(title: "Recent searches")
            if recents.isEmpty {
                Text("Searches you act on land here.")
                    .font(Aero.caption())
                    .foregroundStyle(Aero.textMuted)
            } else {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: Aero.Spacing.s) {
                        ForEach(recents, id: \.self) { recent in
                            AeroChip(text: recent, action: { query = recent })
                        }
                    }
                    .padding(.vertical, 2)
                }
            }
        }
    }

    // MARK: Kind chips (honest group gates)

    private var filterSection: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: Aero.Spacing.s) {
                AeroChip(text: "All", selected: activeKind == nil) {
                    GSHaptics.select()
                    activeKind = nil
                }
                ForEach(Kind.allCases, id: \.self) { kind in
                    AeroChip(text: kind.rawValue, selected: activeKind == kind) {
                        GSHaptics.select()
                        withAnimation(Aero.motion(Aero.snappy)) {
                            activeKind = activeKind == kind ? nil : kind
                        }
                    }
                }
            }
            .padding(.vertical, 2)
        }
    }

    // MARK: Grouped results

    private func resultsSection(_ hits: [SearchHit]) -> some View {
        VStack(alignment: .leading, spacing: Aero.Spacing.l) {
            ForEach(Kind.allCases, id: \.self) { kind in
                let items = hits.filter { $0.kind == kind }
                if !items.isEmpty {
                    VStack(alignment: .leading, spacing: Aero.Spacing.m) {
                        SectionHeader(
                            title: kind.rawValue,
                            actionTitle: "\(items.count) found",
                            action: {}
                        )
                        VStack(spacing: Aero.Spacing.s) {
                            ForEach(items) { result in
                                NavigationLink(value: result.route) {
                                    AeroListRow(
                                        title: result.kind == .conversations ? gsConversationTitle(result.title) : result.title,
                                        subtitle: result.detail,
                                        leading: {
                                            Image(systemName: kind.icon)
                                                .font(.system(size: 14, weight: .medium))
                                                .foregroundStyle(Aero.text)
                                                .frame(width: 36, height: 36)
                                                .background(Circle().fill(Aero.containerHigh))
                                        },
                                        trailing: {
                                            Image(systemName: "chevron.right")
                                                .font(.system(size: 12, weight: .semibold))
                                                .foregroundStyle(Aero.textMuted)
                                        }
                                    )
                                }
                                .buttonStyle(KineticPressStyle())
                                .simultaneousGesture(TapGesture().onEnded {
                                    RecentSearches.record(term)
                                })
                            }
                        }
                    }
                }
            }
        }
    }

    // MARK: Helpers

    private static func snippet(_ content: String, term: String) -> String {
        let clean = content.replacingOccurrences(of: "\n", with: " ").trimmingCharacters(in: .whitespaces)
        guard let range = clean.range(of: term, options: .caseInsensitive) else {
            return String(clean.prefix(80))
        }
        let lowerStart = clean.distance(from: clean.startIndex, to: range.lowerBound)
        let start = max(0, lowerStart - 24)
        let end = min(clean.count, lowerStart + term.count + 48)
        let prefix = start > 0 ? "…" : ""
        let suffix = end < clean.count ? "…" : ""
        let slice = clean[clean.index(clean.startIndex, offsetBy: start)..<clean.index(clean.startIndex, offsetBy: end)]
        return prefix + slice.trimmingCharacters(in: .whitespaces) + suffix
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
}

private extension Array where Element == String {
    func removingDuplicates() -> [String] {
        var seen = Set<String>()
        return filter { seen.insert($0).inserted }
    }
}
