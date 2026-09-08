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
/// conversations and message bodies (FTS5-backed via `ConversationStore`),
/// Library saved items, the assistant catalogue (samples + the user's own)
/// and projects. Results arrive grouped with honest per-group counts and
/// every row routes to its real destination. Store hiccups resolve to "no
/// results", never an error. Kind chips gate the groups; recent searches are
/// the queries the reader actually acted on.
struct SearchView: View {

    // MARK: Result model

    private enum Kind: String, CaseIterable {
        case conversations = "Conversations"
        case messages = "Messages"
        case library = "Library"
        case assistants = "Assistants"
        case projects = "Projects"

        var icon: String {
            switch self {
            case .conversations: return "bubble.left"
            case .messages: return "text.quote"
            case .library: return "tray.full"
            case .assistants: return "smarttoy"
            case .projects: return "folder"
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
    @ObservedObject private var assistantsStore = AssistantsStore.shared
    @ObservedObject private var projectStore = ProjectStore.shared
    @State private var query = ""
    @State private var activeKind: Kind?
    @State private var recents: [String] = RecentSearches.load()

    private var term: String {
        query.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    // MARK: Real search

    private var matches: [SearchHit] {
        guard term.count >= 2 else { return [] }

        var hits: [SearchHit] = []

        for convo in store.searchTitles(term).prefix(10) {
            hits.append(SearchHit(
                id: "convo-\(convo.id)",
                kind: .conversations,
                title: convo.title,
                detail: "Chat title match · " + Self.relative(convo.updatedAt),
                route: .chat(convo.id)))
        }
        for hit in store.searchMessages(term).prefix(12) {
            hits.append(SearchHit(
                id: "msg-\(hit.messageID)",
                kind: .messages,
                title: hit.conversation.title,
                detail: Self.snippet(hit.content, term: term) + " · " + Self.relative(hit.createdAt),
                route: .chat(hit.conversation.id)))
        }
        for item in ConversationStore.shared.savedLibraryItems()
            .filter({ $0.title.localizedCaseInsensitiveContains(term) || $0.content.localizedCaseInsensitiveContains(term) })
            .prefix(10) {
            let contentMatched = !item.title.localizedCaseInsensitiveContains(term)
            let detail = (contentMatched ? Self.snippet(item.content, term: term) : "Saved \(item.kind)")
                + " · " + Self.relative(item.createdAt)
            hits.append(SearchHit(
                id: "lib-\(item.id)",
                kind: .library,
                title: item.title,
                detail: detail,
                route: .library))
        }
        for assistant in (AssistantSample.catalog + assistantsStore.userAssistants)
            .filter({
                $0.name.localizedCaseInsensitiveContains(term) ||
                $0.desc.localizedCaseInsensitiveContains(term) ||
                $0.category.localizedCaseInsensitiveContains(term)
            })
            .prefix(8) {
            hits.append(SearchHit(
                id: "asst-\(assistant.id)",
                kind: .assistants,
                title: assistant.name,
                detail: "\(assistant.category) · ★ \(assistant.ratingText)",
                route: .assistant(assistant.id)))
        }
        for project in projectStore.projects
            .filter({
                $0.name.localizedCaseInsensitiveContains(term) ||
                $0.blurb.localizedCaseInsensitiveContains(term)
            })
            .prefix(8) {
            let detail = project.blurb.isEmpty ? "\(project.chatIds.count) chats" : project.blurb
            hits.append(SearchHit(
                id: "proj-\(project.id)",
                kind: .projects,
                title: project.name,
                detail: detail,
                route: .project(project.id)))
        }
        return hits
    }

    // MARK: Body

    var body: some View {
        // Deep-perf pass 80-b: ONE search execution per body pass. resultsSection
        // re-filtered `matches` per kind (plus the isEmpty gate) — every
        // keystroke ran the full query 6× (12 SQL round trips + 6 UserDefaults
        // JSON decodes of the library).
        let hits = matches
        return ScrollView {
            LazyVStack(alignment: .leading, spacing: Aero.Spacing.l) {
                StaggerIn(index: 0) { inputBar }
                if term.isEmpty {
                    StaggerIn(index: 1) { recentSection }
                    StaggerIn(index: 2) { filterSection }
                    StaggerIn(index: 3) {
                        EmptyStateView(
                            icon: "magnifyingglass",
                            title: "Search everything",
                            message: "Conversations, messages, library items, assistants and projects — one index over this device."
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
                } else if hits.isEmpty {
                    StaggerIn(index: 1) {
                        EmptyStateView(
                            icon: "tray",
                            title: "No results",
                            message: "Nothing matches “\(term)” yet — try another term or clear a filter."
                        )
                    }
                } else {
                    StaggerIn(index: 1) { resultsSection(hits) }
                }
            }
            .padding(.horizontal, Aero.Spacing.m)
            .padding(.top, Aero.Spacing.s)
            .padding(.bottom, Aero.Spacing.xl)
        }
        .background(Aero.background.ignoresSafeArea())
        .navigationTitle("Search everything")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar(.visible, for: .navigationBar)
        .onAppear { recents = RecentSearches.load() }
    }

    // MARK: Input

    private var inputBar: some View {
        AeroInputBar(
            text: $query,
            placeholder: "Search conversations, library, assistants…",
            action: {}
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
                AeroChip(text: "All", selected: activeKind == nil) { activeKind = nil }
                ForEach(Kind.allCases, id: \.self) { kind in
                    AeroChip(text: kind.rawValue, selected: activeKind == kind) {
                        activeKind = activeKind == kind ? nil : kind
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
