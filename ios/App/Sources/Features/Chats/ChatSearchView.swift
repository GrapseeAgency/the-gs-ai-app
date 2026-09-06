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

    private static let filterChips = ["This week", "Has files", "Model: GS Balanced"]

    private var term: String {
        query.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private var results: [SearchHit] {
        guard term.count >= 2 else { return [] }
        let withinWeek = activeFilters.contains("This week")

        var hits: [SearchHit] = []
        var seen = Set<String>()
        for convo in store.searchTitles(term) where !convo.archived {
            seen.insert(convo.id)
            hits.append(SearchHit(
                id: convo.id,
                routeID: convo.id,
                title: convo.title,
                snippet: "Chat title match",
                when: Self.relative(convo.updatedAt)))
        }
        for messageHit in store.searchMessages(term) {
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
        VStack(alignment: .leading, spacing: Aero.Spacing.l) {
            Text("Search chats")
                .font(Aero.displayTitle())
                .foregroundStyle(Aero.text)

            AeroInputBar(text: $query, placeholder: "Search messages and chats")

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: Aero.Spacing.s) {
                    ForEach(Self.filterChips, id: \.self) { chip in
                        AeroChip(text: chip, selected: activeFilters.contains(chip)) {
                            if activeFilters.contains(chip) {
                                activeFilters.remove(chip)
                            } else {
                                activeFilters.insert(chip)
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
                    VStack(spacing: Aero.Spacing.s) {
                        ForEach(results) { hit in
                            NavigationLink(value: AeroRoute.chat(hit.routeID)) {
                                AeroListRow(
                                    title: hit.title,
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
        }
        .padding(.horizontal, Aero.Spacing.m)
        .padding(.top, Aero.Spacing.s)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .background(Aero.background.ignoresSafeArea())
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
        guard let date = ISO8601DateFormatter().date(from: iso) else { return "earlier" }
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
